package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.provider.youtubemusic.YouTubeChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.provider.youtubemusic.YouTubeSessionStore

/** Shared account controls on both Windows layouts; channels always come from InnerTube. */
@Composable
internal fun YouTubeAccountControls(onRelogin: () -> Unit) {
    val revision by YouTubeSessionStore.revision.collectAsState()
    val expired by YouTubeSessionStore.expired.collectAsState()
    val session = remember(revision) { YouTubeSessionStore.read() }
    val scope = rememberCoroutineScope()
    var showChannels by remember(revision) { mutableStateOf(false) }
    var channels by remember(revision) { mutableStateOf<List<YouTubeChannel>>(emptyList()) }
    var busy by remember(revision) { mutableStateOf(false) }
    var error by remember(revision) { mutableStateOf<String?>(null) }

    LaunchedEffect(session.cookie, session.authUser, session.pageId, session.dataSyncId) {
        if (session.isLoggedIn) {
            YouTubeSessionStore.revalidate().onFailure { error = it.message ?: "无法验证 YouTube Music 登录状态" }
        }
    }
    if (!session.isLoggedIn) return
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 8.dp, bottom = 8.dp).testTag("youtube-account-controls")) {
        Text(if (expired) "登录已过期，请重新登录" else session.accountName.ifBlank { "已导入 YouTube Music 账号" },
            color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(enabled = !busy, modifier = Modifier.testTag("youtube-channel-open"), onClick = {
                showChannels = true
                channels = emptyList()
                error = null
                busy = true
                scope.launch {
                    try { channels = YouTubeSessionStore.channels() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "读取频道失败" }
                    finally { busy = false }
                }
            }) { Text("切换频道") }
            TextButton(enabled = !busy, modifier = Modifier.testTag("youtube-revalidate"), onClick = {
                busy = true
                error = null
                scope.launch {
                    try { YouTubeSessionStore.revalidate().onFailure { error = it.message ?: "验证失败" } }
                    finally { busy = false }
                }
            }) { Text("验证状态") }
            TextButton(onClick = onRelogin, enabled = !busy, modifier = Modifier.testTag("youtube-relogin")) { Text("重新登录") }
        }
        if (!showChannels) error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
    if (showChannels) AlertDialog(
        onDismissRequest = { if (!busy) showChannels = false },
        title = { Text("YouTube Music 频道") },
        text = {
            Column(Modifier.widthIn(max = 480.dp).heightIn(max = 320.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("youtube-channel-error")) }
                if (!busy && error == null && channels.isEmpty()) Text("没有返回可切换的频道")
                LazyColumn {
                    itemsIndexed(channels) { index, channel ->
                        val selected = if (session.pageId.isNotBlank()) channel.pageId == session.pageId else channel.selected
                        Row(Modifier.fillMaxWidth().testTag("youtube-channel:$index").clickable(enabled = !busy) {
                            busy = true
                            error = null
                            scope.launch {
                                try { YouTubeSessionStore.useChannel(channel); showChannels = false }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "频道切换失败" }
                                finally { busy = false }
                            }
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(channel.name)
                                channel.handle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (selected) Text("✓")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { showChannels = false }) { Text("完成") } },
    )
}
