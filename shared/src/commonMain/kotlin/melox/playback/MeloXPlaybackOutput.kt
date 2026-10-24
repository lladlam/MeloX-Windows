package melox.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import melox.dlna.DlnaSession
import melox.player.AudioPlayer

/** Display state for the actual output, separate from the paused local decoder during DLNA. */
@OptIn(ExperimentalCoroutinesApi::class)
object MeloXPlaybackOutput {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val remotePosition = DlnaSession.state.flatMapLatest { snapshot ->
        if (snapshot.device == null) flowOf<Long?>(null)
        else flow { while (currentCoroutineContext().isActive) { emit(snapshot.displayedPosition()); delay(200) } }
    }.stateIn(scope, SharingStarted.Eagerly, null)
    val state = combine(AudioPlayer.state, DlnaSession.state) { local, remote ->
        if (remote.device == null) local else local.copy(isPlaying = remote.playing, isPaused = !remote.playing,
            isLoading = remote.busy, isFinished = false, error = remote.error)
    }.stateIn(scope, SharingStarted.Eagerly, AudioPlayer.state.value)
    val positionMs = combine(AudioPlayer.positionMs, remotePosition) { local, remote -> remote ?: local }
        .stateIn(scope, SharingStarted.Eagerly, AudioPlayer.positionMs.value)
    val durationMs = combine(AudioPlayer.durationMs, DlnaSession.state) { local, remote -> if (remote.device == null || remote.durationMs <= 0) local else remote.durationMs }
        .stateIn(scope, SharingStarted.Eagerly, AudioPlayer.durationMs.value)
    val progress = combine(AudioPlayer.progress, remotePosition, durationMs) { local, remote, duration ->
        if (remote == null) local else if (duration > 0) (remote.toFloat() / duration).coerceIn(0f, 1f) else 0f
    }.stateIn(scope, SharingStarted.Eagerly, AudioPlayer.progress.value)
    val volumeLevel = combine(AudioPlayer.volumeLevel, DlnaSession.state) { local, remote -> if (remote.device == null) local else remote.volume ?: local }
        .stateIn(scope, SharingStarted.Eagerly, AudioPlayer.volumeLevel.value)
}
