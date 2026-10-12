package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.remoteconfig.*
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassDialog

/** Only the verified, applicable, opted-in static compatibility notice is displayed. */
@Composable
internal fun RemoteCompatibilityNotice() {
    val status by MeloXRemoteConfigRuntime.status.collectAsState()
    val enabled by MeloXRemoteConfigRuntime.enabled.collectAsState()
    var pending by remember { mutableStateOf<MeloXRemoteNotice?>(null) }
    LaunchedEffect(status, enabled) {
        pending = status.config.notice?.takeIf {
            enabled && status.source == MeloXRemoteConfigSource.VerifiedRemote && MeloXRemoteNoticeStore.shouldShow(it)
        }
    }
    pending?.let { notice ->
        LaunchedEffect(notice.id, notice.frequency) { MeloXRemoteNoticeStore.markShown(notice) }
        MeloXGlassDialog(true, onDismiss = { pending = null }, modifier = Modifier.testTag("remote-notice")) {
            Text(notice.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
            Text(notice.message, Modifier.padding(top = 10.dp, bottom = 16.dp), fontSize = 14.sp, lineHeight = 21.sp)
            MeloXGlassButton(onClick = { pending = null }, modifier = Modifier.fillMaxWidth().testTag("remote-notice-acknowledge")) { Text("知道了") }
        }
    }
}
