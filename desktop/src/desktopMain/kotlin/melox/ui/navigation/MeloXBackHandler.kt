package melox.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

/** Each root tab owns its back stack, including during a cross-fade. */
class MeloXBackDispatcher {
    private data class Entry(val priority: Int, val action: () -> Unit)
    private val actions = linkedMapOf<Any, Entry>()

    internal fun register(token: Any, priority: Int, action: () -> Unit) {
        actions[token] = Entry(priority, action)
    }

    internal fun unregister(token: Any) { actions.remove(token) }

    fun dispatch(): Boolean {
        val priority = actions.values.maxOfOrNull(Entry::priority) ?: return false
        val entry = actions.values.lastOrNull { it.priority == priority }
            ?: return false
        entry.action()
        return true
    }
}

val LocalMeloXBackDispatcher = staticCompositionLocalOf<MeloXBackDispatcher?> { null }

/** Desktop Escape invokes the topmost destination's ordinary Back action. */
@Composable
fun MeloXBackHandler(enabled: Boolean, priority: Int = 0, onBack: () -> Unit) {
    val dispatcher = LocalMeloXBackDispatcher.current ?: return
    val token = remember { Any() }
    val currentAction = rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, token, enabled, priority) {
        if (enabled) dispatcher.register(token, priority) { currentAction.value() }
        onDispose { dispatcher.unregister(token) }
    }
}
