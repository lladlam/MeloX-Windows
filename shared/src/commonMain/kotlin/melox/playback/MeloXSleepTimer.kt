package melox.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import melox.settings.MeloXSettingsPreferences

/** Process-owned timer: closing the action sheet or changing tracks cannot cancel it. */
internal class PlaybackSleepTimer(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val persist: (Long) -> Unit,
    private val pause: () -> Unit,
) {
    private val lock = Any()
    private var generation = 0L
    private var job: Job? = null
    private val deadline = MutableStateFlow(0L)
    val endEpochMs = deadline.asStateFlow()

    fun restore(end: Long) = synchronized(lock) { replace(end.coerceAtLeast(0L)) }

    fun set(minutes: Int) = synchronized(lock) {
        require(minutes > 0) { "定时时长必须大于零" }
        replace(Math.addExact(now(), minutes * 60_000L))
    }

    fun cancel() = synchronized(lock) { replace(0L) }

    /** Process disposal stops the worker without cancelling the persisted deadline. */
    fun stop() = synchronized(lock) {
        generation++
        job?.cancel()
        job = null
    }

    private fun replace(end: Long) {
        val token = ++generation
        job?.cancel()
        job = null
        persist(end)
        deadline.value = end
        if (end == 0L) return
        job = scope.launch {
            while (true) {
                val remaining = end - now()
                if (remaining <= 0L) break
                // Recheck wall-clock time after suspend/resume or clock adjustments.
                delay(remaining.coerceAtMost(1_000L))
            }
            synchronized(lock) {
                if (token != generation) return@synchronized
                persist(0L)
                deadline.value = 0L
                pause()
            }
        }
    }
}

object MeloXSleepTimer {
    private const val KEY = "playback_sleep_timer_end_epoch_ms"
    private val timer = PlaybackSleepTimer(
        CoroutineScope(SupervisorJob() + Dispatchers.Main), System::currentTimeMillis,
        { MeloXSettingsPreferences.setLong(KEY, it) },
        { PlaybackCommands.playerController()?.pause() ?: melox.player.AudioPlayer.pause() },
    )
    val endEpochMs = timer.endEpochMs
    private var initialized = false

    @Synchronized fun initialize() {
        if (initialized) return
        initialized = true
        timer.restore(MeloXSettingsPreferences.long(KEY, 0L))
    }

    fun set(minutes: Int) { initialize(); timer.set(minutes) }
    fun cancel() { initialize(); timer.cancel() }
    @Synchronized fun shutdown() { timer.stop(); initialized = false }
}
