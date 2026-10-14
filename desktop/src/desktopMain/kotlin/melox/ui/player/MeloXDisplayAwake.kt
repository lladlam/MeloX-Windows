package melox.ui.player

import androidx.compose.runtime.*
import melox.platform.MeloXDisplayAwakeController

// An offscreen validation scene has no foreground OS window and cannot hold display power.
internal val LocalMeloXDisplayAwakeOwner = staticCompositionLocalOf<MeloXDisplayAwakeController?> { null }
internal val LocalMeloXWindowForeground = staticCompositionLocalOf { false }

@Composable
internal fun MeloXDisplayAwakeEffect(required: Boolean) {
    val owner = LocalMeloXDisplayAwakeOwner.current
    val foreground = LocalMeloXWindowForeground.current
    LaunchedEffect(owner, foreground, required) { owner?.setRequired(foreground && required) }
    DisposableEffect(owner) { onDispose { owner?.setRequired(false) } }
}
