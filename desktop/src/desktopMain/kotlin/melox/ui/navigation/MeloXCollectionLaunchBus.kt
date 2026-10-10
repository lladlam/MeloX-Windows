package melox.ui.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Android App Shell collection overlays; retained request survives page changes. */
sealed interface MeloXCollectionTarget {
    data class Artist(val id: Long) : MeloXCollectionTarget
    data class Album(val id: Long, val name: String = "") : MeloXCollectionTarget
}

object MeloXCollectionLaunchBus {
    private val pending = MutableStateFlow<MeloXCollectionTarget?>(null)
    val target = pending.asStateFlow()
    fun open(target: MeloXCollectionTarget) { pending.value = target }
    fun dismiss() { pending.value = null }
}
