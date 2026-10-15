package melox.playback

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Explicit controller commands, distinct from transient buffering/decoder state. */
internal object MeloXPlaybackEvents {
    enum class Kind { Queue, Play, Pause, Seek }
    data class Event(val kind: Kind, val songId: Long?, val positionMs: Long, val playWhenReady: Boolean)
    private val events = MutableSharedFlow<Event>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val flow = events.asSharedFlow()
    fun publish(player: MeloXDesktopPlayer, kind: Kind) {
        if (PlaybackCommands.playerController() !== player) return
        val song = player.snapshot().getOrNull(player.currentMediaItemIndex)
        events.tryEmit(Event(kind, song?.neteaseSongId, player.currentPosition.coerceAtLeast(0L), player.playWhenReady))
    }
}
