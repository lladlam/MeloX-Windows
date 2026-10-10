package melox.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import melox.music.model.MusicTrack

data class MeloXAutoMixTransitionSnapshot(
    val status: String = "idle",
    val active: Boolean = false,
    val progress: Float = 0f,
    val handedOff: Boolean = false,
    val outgoing: MusicTrack? = null,
    val incoming: MusicTrack? = null,
    val incomingPositionMs: Long = 0,
    val message: String? = null,
)

/** PCM output clock bridge. UI never synthesizes transition progress from wall time. */
object MeloXAutoMixTransitionRuntime {
    private val mutable = MutableStateFlow(MeloXAutoMixTransitionSnapshot())
    val state = mutable.asStateFlow()
    fun status(status: String, message: String? = null) { mutable.value = mutable.value.copy(status = status, message = message) }
    fun update(outgoing: MusicTrack?, incoming: MusicTrack?, progress: Float, positionMs: Long) {
        mutable.value = MeloXAutoMixTransitionSnapshot("mixing", true, progress.coerceIn(0f, 1f), false, outgoing, incoming, positionMs)
    }
    fun handoff() { mutable.value = mutable.value.copy(status = "handoff", active = false, progress = 1f, handedOff = true) }
    fun cancel(message: String? = null) { mutable.value = MeloXAutoMixTransitionSnapshot(if (message == null) "idle" else "fallback", message = message) }
}
