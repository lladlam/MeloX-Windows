package melox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.network.MeloXListenTogetherInvitation
import melox.network.NeteaseClipboardTarget
import melox.network.NeteaseMusicOperationsClient
import melox.playback.MeloXListenTogetherCoordinator
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassDialog
import melox.ui.legal.pinnedString

/** MeloXApp.kt:1048, including the source glass button styles and spacing. */
@Composable
internal fun MeloXClipboardLinkDialog(target: NeteaseClipboardTarget, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MeloXGlassDialog(true, onDismiss, Modifier.testTag("clipboard-link-dialog")) {
        Text(pinnedString("app_clipboard_title"), style = MaterialTheme.typography.titleLarge)
        Text(
            pinnedString(if (target is NeteaseClipboardTarget.Song) "app_clipboard_song" else "app_clipboard_playlist"),
            Modifier.padding(top = 8.dp).testTag("clipboard-link-message"),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .64f),
        )
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton(onClick = onDismiss, modifier = Modifier.weight(1f).testTag("clipboard-cancel"),
                style = MeloXGlassButtonStyle.Plain) { Text(pinnedString("action_cancel")) }
            MeloXGlassButton(onClick = onConfirm, modifier = Modifier.weight(1f).testTag("clipboard-confirm"),
                style = MeloXGlassButtonStyle.BorderedProminent) { Text(pinnedString("app_confirm")) }
        }
    }
}

/** Pinned MeloXListenTogetherInviteActivity: explicit confirmation, real join, adopt only on success. */
@Composable
internal fun MeloXListenTogetherInviteDialog(invitation: MeloXListenTogetherInvitation, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember(invitation) { mutableStateOf(false) }
    var error by remember(invitation) { mutableStateOf<String?>(null) }
    AlertDialog(
        modifier = Modifier.testTag("clipboard-together-dialog"),
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("发现一起听邀请") },
        text = {
            if (busy) CircularProgressIndicator(Modifier.testTag("clipboard-together-busy"))
            else Text(error ?: "房间 ${invitation.roomId}\n是否加入？", Modifier.testTag("clipboard-together-message"))
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.testTag("clipboard-together-cancel")) { Text("取消") }
        },
        confirmButton = {
            TextButton(enabled = !busy, modifier = Modifier.testTag("clipboard-together-join"), onClick = {
                busy = true
                error = null
                val revision = NeteaseSessionStore.revision.value
                val cookie = NeteaseSessionStore.readCookie()
                scope.launch {
                    try {
                        val room = NeteaseMusicOperationsClient(cookieProvider = { cookie })
                            .joinListenTogetherRoom(invitation.roomId, invitation.inviterId)
                        check(revision == NeteaseSessionStore.revision.value && cookie == NeteaseSessionStore.readCookie()) {
                            "账号已切换，请重新连接"
                        }
                        MeloXListenTogetherCoordinator.adoptRoom(room)
                        onDismiss()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: "加入房间失败" }
                    finally { busy = false }
                }
            }) { Text("加入") }
        },
    )
}
