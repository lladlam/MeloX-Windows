package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import melox.account.NeteaseSessionStore
import melox.network.MeloXCloudSong
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXGlassDialog
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassTextField
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors

@Composable
fun CloudScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    val client = remember { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<MeloXCloudSong?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf<melox.network.MeloXCloudUploadProgress?>(null) }
    var uploadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        try { runCatching {
            val first = client.cloudSongs()
            if (!first.hasMore) first else {
                var page = first
                val all = first.values.toMutableList()
                var offset = all.size
                while (page.hasMore && offset < first.totalCount) {
                    page = client.cloudSongs(offset = offset)
                    if (page.values.isEmpty()) break
                    all += page.values
                    offset += page.values.size
                }
                first.copy(values = all.distinctBy(MeloXCloudSong::id), hasMore = page.hasMore)
            }
        }.onSuccess { page ->
            values = page.values
            quota = if (page.maxBytes > 0L) "${formatBytes(page.usedBytes)} / ${formatBytes(page.maxBytes)} · ${page.totalCount} 首" else "${page.totalCount} 首"
        }.onFailure {
            if (it is CancellationException) throw it
            error = it.message ?: "云盘加载失败"
        } } finally { loading = false }
    }
    LaunchedEffect(Unit) { refresh() }
    val displayed = values.filter { song ->
        val keyword = query.trim()
        keyword.isBlank() || listOf(song.song.name, song.song.artists, song.song.album).any { it.contains(keyword, ignoreCase = true) }
    }
    pendingDelete?.let { target ->
        MeloXGlassDialog(visible = true, onDismiss = { pendingDelete = null }, title = "删除云盘歌曲") {
            Text("确定从云盘删除「${target.song.name}」？", color = MeloXColors.pageOnSurfaceVariant())
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXGlassButton(onClick = { pendingDelete = null }, modifier = Modifier.weight(1f), style = MeloXGlassButtonStyle.Plain) { Text("取消") }
                MeloXGlassButton(
                    onClick = {
                        pendingDelete = null
                        scope.launch {
                            runCatching { client.deleteCloudSong(target.id) }
                                .onSuccess { values = values.filterNot { it.id == target.id } }
                                .onFailure { error = it.message ?: "删除失败" }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    style = MeloXGlassButtonStyle.Destructive,
                ) { Text("删除") }
            }
        }
    }

    val actions: @Composable () -> Unit = {
            Text(if (uploading) "上传中" else "上传", color = MeloXColors.Primary,
                modifier = Modifier.testTag("cloud-upload").clickable(enabled = !uploading) {
                    uploadJob = scope.launch {
                        uploading = true; error = null; uploadProgress = null
                        try {
                            val selected = chooseCloudAudioFile() ?: return@launch
                            val owner = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]
                            client.uploadCloudSong(selected) { stage -> scope.launch { if (owner?.isActive == true) uploadProgress = stage } }
                            uploadProgress = melox.network.MeloXCloudUploadProgress("上传完成", selected.length(), selected.length())
                            refresh()
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { error = failure.message ?: "云盘上传失败" }
                        finally { uploading = false }
                    }
                }.padding(10.dp))
            Text("刷新", color = MeloXColors.Primary, modifier = Modifier.testTag("cloud-refresh")
                .clickable(enabled = !loading && !uploading) { scope.launch { refresh() } }.padding(10.dp))
    }
    Column(modifier.fillMaxSize().background(MeloXColors.pageBackground()).padding(top = if (embedded) 0.dp else 18.dp)) {
        if (embedded) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) { actions() }
        else MeloXIosTopBar(title = "音乐云盘", actions = { actions() })
        MeloXGlassTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索云盘", color = MeloXColors.pageOnSurfaceVariant()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(quota, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
        if (uploading || uploadProgress?.stage == "上传完成") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).testTag("cloud-upload-status"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (uploading) CircularProgressIndicator(Modifier.size(18.dp), color = MeloXColors.Primary, strokeWidth = 2.dp)
                val stage = uploadProgress
                Text(if (stage == null) "请选择音频文件" else stage.stage + if (stage.stage == "上传音频" && stage.totalBytes > 0)
                    " · ${stage.uploadedBytes * 100 / stage.totalBytes}%" else "", modifier = Modifier.weight(1f),
                    color = MeloXColors.pageOnSurfaceVariant(), fontSize = 12.sp)
                if (uploading) Text("取消", color = MeloXColors.Primary, modifier = Modifier.testTag("cloud-upload-cancel")
                    .clickable { uploadJob?.cancel(); uploadProgress = null }.padding(10.dp))
            }
        }
        if (error != null && values.isNotEmpty()) Text(error.orEmpty(), color = MeloXColors.Error,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("cloud-error"))
        when {
            loading && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MeloXColors.Primary) }
            error != null && values.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error.orEmpty(), color = MeloXColors.Error, modifier = Modifier.testTag("cloud-error"))
                    Text("重试", color = MeloXColors.Primary, modifier = Modifier.padding(top = 12.dp).testTag("cloud-retry")
                        .clickable { scope.launch { refresh() } })
                }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(displayed, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            PlaybackCommands.playQueue(displayed.map { it.song }, item.song.id)
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXArtworkImage(item.song.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Medium)
                            Text(item.song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
                        }
                        Text("删除", color = MeloXColors.Error, modifier = Modifier.clickable { pendingDelete = item })
                    }
                }
            }
        }
    }
}

/** Desktop file chooser on the EDT; disposing/cancelling the screen closes the dialog. */
private suspend fun chooseCloudAudioFile(): java.io.File? = suspendCancellableCoroutine { continuation ->
    val chooser = java.util.concurrent.atomic.AtomicReference<JFileChooser?>()
    continuation.invokeOnCancellation { SwingUtilities.invokeLater { chooser.get()?.cancelSelection() } }
    SwingUtilities.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val picker = JFileChooser().apply {
                dialogTitle = "选择要上传到网易云盘的音频"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("音频文件", "mp3", "flac", "m4a", "aac", "wav", "ogg", "opus", "wma")
                isMultiSelectionEnabled = false
            }
            chooser.set(picker)
            if (!continuation.isActive) return@invokeLater
            val result = picker.showOpenDialog(null)
            if (continuation.isActive) continuation.resume(if (result == JFileChooser.APPROVE_OPTION) picker.selectedFile else null)
        } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
