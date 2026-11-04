package melox.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.library.removeNeteaseLibraryPlaylist
import melox.network.NeteaseMusicOperationsClient

@Composable
internal fun NeteasePlaylistRemovalDialog(
    target: NeteasePlaylistSummary,
    userId: Long?,
    expectedCookie: String,
    onDismiss: () -> Unit,
    onRemoved: () -> Unit,
) {
    var busy by remember(target.id) { mutableStateOf(false) }
    var error by remember(target.id) { mutableStateOf<String?>(null) }
    val cookie = remember(target.id) { expectedCookie }
    val revision = remember(target.id) { NeteaseSessionStore.revision.value }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("移除歌单") },
        text = { Text(error ?: "自建歌单将被删除，收藏的歌单将取消收藏：${target.name}") },
        confirmButton = {
            TextButton(enabled = !busy, modifier = Modifier.testTag("library-remove-confirm"), onClick = {
                if (busy) return@TextButton
                busy = true
                error = null
                scope.launch {
                    try {
                        val uid = requireNotNull(userId) { "无法确认当前账号，请刷新后重试" }
                        val operations = NeteaseMusicOperationsClient({ cookie })
                        removeNeteaseLibraryPlaylist(uid, target.id,
                            loadPlaylists = { NeteaseLibraryClient({ cookie }).userPlaylists(it) },
                            checkSession = { check(NeteaseSessionStore.revision.value == revision && NeteaseSessionStore.readCookie() == cookie) { "登录账号已变更，请重试" } },
                            delete = { operations.deletePlaylist(it) },
                            unsubscribe = { operations.setPlaylistSubscribed(it, false) })
                        check(NeteaseSessionStore.revision.value == revision && NeteaseSessionStore.readCookie() == cookie) { "登录账号已变更，请刷新资料库" }
                        onRemoved()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        error = failure.message ?: "移除歌单失败"
                    } finally {
                        busy = false
                    }
                }
            }) { Text(if (busy) "处理中…" else "移除") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
