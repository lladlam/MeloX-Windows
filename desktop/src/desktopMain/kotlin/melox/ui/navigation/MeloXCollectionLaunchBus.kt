package melox.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android App Shell collection overlays; retained request survives page changes. */
sealed interface MeloXCollectionTarget {
    data class Artist(val id: Long) : MeloXCollectionTarget
    data class Album(val id: Long, val name: String = "") : MeloXCollectionTarget
    data class Song(val id: Long) : MeloXCollectionTarget
}

object MeloXCollectionLaunchBus {
    private val pending = MutableStateFlow<MeloXCollectionTarget?>(null)
    private val history = ArrayDeque<MeloXCollectionTarget>()
    val target = pending.asStateFlow()
    @Synchronized
    fun open(target: MeloXCollectionTarget) {
        val current = pending.value
        if (current == target) return
        if (!melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) history.clear()
        else if (current != null) history.addLast(current)
        pending.value = target
    }
    @Synchronized
    fun dismiss() { pending.value = history.removeLastOrNull() }
    @Synchronized
    fun clear() { history.clear(); pending.value = null }
}
