package melox.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState

/** Desktop equivalent of the pinned status-bar owner. Preserve the user's original placement. */
internal class MeloXImmersiveWindowOwner(private val state: WindowState, private val window: ComposeWindow) : AutoCloseable {
    private var previous: WindowPlacement? = null
    private var closed = false

    fun setEnabled(enabled: Boolean) {
        if (closed) return
        if (enabled) {
            if (previous == null) {
                previous = state.placement
                applyPlacement(WindowPlacement.Fullscreen)
            }
        } else {
            val restore = previous ?: return
            previous = null
            // A manual placement change during immersion belongs to the user.
            if (state.placement == WindowPlacement.Fullscreen && window.placement == WindowPlacement.Fullscreen) applyPlacement(restore)
        }
    }

    private fun applyPlacement(placement: WindowPlacement) {
        // Compose 1.8.1's Maximized setter only sets MAXIMIZED_BOTH; it does not
        // clear the independent Skiko fullscreen flag. Leave fullscreen first.
        if (window.placement == WindowPlacement.Fullscreen && placement != WindowPlacement.Fullscreen) {
            window.placement = WindowPlacement.Floating
        }
        state.placement = placement
        window.placement = placement
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
