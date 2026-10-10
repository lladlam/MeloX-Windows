package melox.ui.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import melox.music.model.MusicSource
import melox.network.MeloXSearchKind

data class MeloXSearchLaunch(val query: String, val kind: MeloXSearchKind, val source: MusicSource? = null, val nonce: Long = System.nanoTime())

/** Same retained request/conditional consumption as Android SearchScreen's launch bus. */
object MeloXSearchLaunchBus {
    var request by mutableStateOf<MeloXSearchLaunch?>(null)
        private set
    fun post(query: String, kind: MeloXSearchKind, source: MusicSource? = null) { request = MeloXSearchLaunch(query, kind, source) }
    fun consume(request: MeloXSearchLaunch) { if (this.request == request) this.request = null }
}
