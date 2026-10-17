package melox.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** Desktop equivalent of the pinned status-bar owner. Preserve the user's original placement. */
internal class MeloXImmersiveWindowOwner(private val state: WindowState) : AutoCloseable {
    private var previous: WindowPlacement? = null
    private var closed = false

    fun setEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled) {
            if (previous == null) {
                previous = state.placement
                state.placement = WindowPlacement.Fullscreen
            }
        } else {
            val restore = previous ?: return
            previous = null
            // A manual placement change during immersion belongs to the user.
            if (state.placement == WindowPlacement.Fullscreen) state.placement = restore
        }
    }

    override fun close() {
        setEnabled(false)
        closed = true
    }
}

internal val LocalMeloXImmersiveWindowOwner = staticCompositionLocalOf<MeloXImmersiveWindowOwner?> { null }

@Composable
internal fun MeloXImmersivePlaybackEffect(enabled: Boolean) {
    val owner = LocalMeloXImmersiveWindowOwner.current
    DisposableEffect(owner, enabled) {
        owner?.setEnabled(enabled)
        onDispose { owner?.setEnabled(false) }
    }
}
