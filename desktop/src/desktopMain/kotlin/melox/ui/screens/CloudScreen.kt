package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
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
) {
    val client = remember { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<MeloXCloudSong?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching {
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
        }.onFailure { error = it.message ?: "云盘加载失败" }
        loading = false
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

    Column(modifier.fillMaxSize().background(MeloXColors.pageBackground()).padding(top = 18.dp)) {
        MeloXIosTopBar(title = "音乐云盘", onBack = { navState.goBack() })
        MeloXGlassTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索云盘", color = MeloXColors.pageOnSurfaceVariant()) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        )
        Text(quota, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MeloXColors.Primary) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MeloXColors.Error) }
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

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
