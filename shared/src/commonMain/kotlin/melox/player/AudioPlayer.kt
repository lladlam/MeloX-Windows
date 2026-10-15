package melox.player

import melox.music.model.MusicTrack
import melox.playback.MeloXAutoMixPlan
import melox.playback.MeloXAutoMixFadeCurve
import melox.playback.MeloXAutoMixTransitionRuntime
import melox.platform.logDebug
import melox.platform.logError
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Desktop playback. HTTP and local files are decoded by ffmpeg to PCM and
 * written to a SourceDataLine. Playback starts once ffmpeg emits audio,
 * without downloading the whole response first.
 */
object AudioPlayer {
    const val PCM_SAMPLE_RATE = 44100
    const val PCM_CHANNELS = 2
    const val PCM_BYTES_PER_FRAME = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentJob: Job? = null
    private var currentLine: SourceDataLine? = null
    private var currentProcess: Process? = null
    @Volatile private var volumeLinear: Float = 1f
    private val playbackLock = Any()
    private val paused = AtomicBoolean(false)
    private val pauseVersion = AtomicLong()
    private val ready = AtomicBoolean(false)
    private val writtenFrames = AtomicLong(0L)
    private val pendingSeekMs = AtomicLong(-1L)
    private val seekRevision = AtomicLong(0L)
    private val session = AtomicReference<PlayRequest?>(null)
    private val dualSession = AtomicReference<MeloXDualDecodeSession?>(null)

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    // Tests replace only the physical device; production always opens Java Sound.
    @Volatile internal var outputLineFactory: () -> SourceDataLine = MeloXPcmOutput::open

    @Volatile
    var decoderCommand: List<String> = emptyList() // Bundled extraction belongs to startDecoder's IO worker.

    internal val playbackOffsetMs = AtomicLong(0L)

    /** Publish the selected item before URL resolution; never leave old audio running. */
    fun selectTrack(track: MusicTrack, startPositionMs: Long = 0L, knownDurationMs: Long = 0L, loading: Boolean = true) {
        stop(clearTrack = false)
        _currentTrack.value = track
        _durationMs.value = (knownDurationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L).coerceAtLeast(0L)
        _positionMs.value = startPositionMs.coerceAtLeast(0L)
        _progress.value = if (_durationMs.value > 0L) (_positionMs.value.toFloat() / _durationMs.value).coerceIn(0f, 1f) else 0f
        _state.value = PlayerState(isLoading = loading, isPaused = !loading, trackTitle = track.title, trackArtist = track.artistText)
    }

    fun reportFailure(error: Throwable): Unit = synchronized(playbackLock) {
        ready.set(false)
        _state.value = _state.value.copy(isLoading = false, isPlaying = false, isPaused = false,
            isFinished = false,
            errorKind = (error as? AudioPlaybackFailure)?.kind,
            error = error.message?.takeIf { it.isNotBlank() } ?: "播放失败")
    }

    fun play(
        url: String,
        track: MusicTrack,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        knownDurationMs: Long = 0L,
        playWhenReady: Boolean = true,
        onEnded: () -> Unit = {},
        onFailure: (AudioPlaybackFailure) -> Unit = {},
        autoMix: Boolean = melox.playback.MeloXPlaybackModeRuntime.autoMixEnabled,
    ) {
        selectTrack(track, startPositionMs, knownDurationMs)
        paused.set(!playWhenReady)
        _state.value = _state.value.copy(isPaused = !playWhenReady)
        pendingSeekMs.set(startPositionMs.coerceAtLeast(0L))
        val request = PlayRequest(url, headers, knownDurationMs.coerceAtLeast(0L), onEnded, onFailure)
        session.set(request)
        currentJob = scope.launch {
            try {
                if (autoMix) runDualSession(request, track, startPositionMs) else runSession(request)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val failure = e as? AudioPlaybackFailure ?: AudioPlaybackFailure(AudioFailureKind.Content, e)
                val notify = synchronized(playbackLock) {
                    if (session.get() !== request) return@launch
                    logError("AudioPlayer", "播放失败 (${failure.kind}): ${melox.diagnostics.MeloXProcessLogs.describe(e)}")
                    reportFailure(failure)
                    currentProcess?.let { process ->
                        runCatching { process.destroyForcibly() }
                        if (currentProcess === process) currentProcess = null
                    }
                    true
                }
                if (notify) request.onFailure(failure)
            }
        }
    }

    /** Attach real, bounded PCM preload to the current streaming output without restarting it. */
    internal suspend fun prepareAutoMix(
        outgoing: MusicTrack, incoming: MusicTrack, uri: String, headers: Map<String, String>,
        plan: MeloXAutoMixPlan, curve: MeloXAutoMixFadeCurve, onHandoff: (Long) -> Unit,
    ): Boolean {
        val owner = session.get() ?: return false
        val engine = dualSession.get() ?: return false
        if (_currentTrack.value?.id != outgoing.id || engine.hasMix()) return false
        owner.tracks[trackKey(incoming)] = incoming
        owner.handoff = { onHandoff(_positionMs.value) }
        val prepared = engine.prepareIncoming(MeloXDeckSource(uri, headers, identity = trackKey(incoming), durationMs = incoming.durationMs ?: 0), plan, curve)
        if (prepared && session.get() === owner && dualSession.get() === engine && engine.hasMix()) {
            MeloXAutoMixTransitionRuntime.status("prepared")
            return true
        }
        return false
    }

    internal fun supportsAutoMix() = dualSession.get() != null
    internal fun hasAutoMix() = dualSession.get()?.hasMix() == true
    internal fun incomingMixPosition(): Long? = dualSession.get()?.clock()?.takeIf { it.incoming != null && it.mixProgress > 0 }?.incomingPositionMs
    internal fun cancelAutoMix() {
        val engine = dualSession.get() ?: return
        if (engine.hasMix()) engine.seek(_positionMs.value)
        MeloXAutoMixTransitionRuntime.cancel()
    }

    private suspend fun runDualSession(request: PlayRequest, track: MusicTrack, startAtMs: Long) {
        request.tracks[trackKey(track)] = track
        val engine = MeloXDualDecodeSession(
            primarySource = MeloXDeckSource(request.url, request.headers, startAtMs, trackKey(track), _durationMs.value),
            outputFactory = { outputLineFactory() },
            bassSwap = true,
            onClock = { clock ->
                synchronized(playbackLock) {
                    if (session.get() === request) {
                        val selected = clock.source?.identity?.let { request.tracks[it] } ?: track
                        _currentTrack.value = selected
                        _durationMs.value = selected.durationMs ?: request.knownDurationMs
                        _positionMs.value = clock.positionMs
                        _progress.value = if (_durationMs.value > 0) (clock.positionMs.toFloat() / _durationMs.value).coerceIn(0f, 1f) else 0f
                        _state.value = _state.value.copy(trackTitle = selected.title, trackArtist = selected.artistText)
                        val incoming = clock.incoming?.identity?.let { request.tracks[it] }
                        if (incoming != null && clock.mixProgress > 0) MeloXAutoMixTransitionRuntime.update(selected, incoming, clock.mixProgress.toFloat(), clock.incomingPositionMs)
                        clock.source?.let { request.url = it.uri; request.headers = it.headers }
                    }
                }
            },
            onHandoff = {
                synchronized(playbackLock) {
                    if (session.get() === request) {
                        MeloXAutoMixTransitionRuntime.handoff(); request.handoff?.invoke()
                        val handedOff = MeloXAutoMixTransitionRuntime.state.value
                        scope.launch { delay(650); if (session.get() === request && MeloXAutoMixTransitionRuntime.state.value === handedOff) MeloXAutoMixTransitionRuntime.cancel() }
                    }
                }
            },
            onFallback = { failure -> if (session.get() === request) MeloXAutoMixTransitionRuntime.cancel(failure.message ?: "AutoMix unavailable") },
            onReady = {
                synchronized(playbackLock) {
                    if (session.get() === request) {
                        ready.set(true)
                        _state.value = _state.value.copy(isLoading = false, isPlaying = !paused.get(), isPaused = paused.get(), error = null, errorKind = null)
                    }
                }
            },
        )
        engine.volume = volumeLinear
        if (paused.get()) engine.pause()
        synchronized(playbackLock) {
            if (session.get() !== request) { engine.close(); return }
            dualSession.set(engine)
        }
        try {
            engine.run()
            synchronized(playbackLock) {
                if (session.get() !== request) return
                _positionMs.value = engine.clock().positionMs
                ready.set(false)
                _state.value = _state.value.copy(isLoading = false, isPlaying = false, isPaused = false, isFinished = true)
            }
            if (session.get() === request) request.onEnded()
        } finally { dualSession.compareAndSet(engine, null); engine.close() }
    }

    private fun trackKey(track: MusicTrack) = "${track.id.source.storageValue}:${track.id.value}"

    private suspend fun runSession(request: PlayRequest) {
        while (session.get() === request) {
            val (launched, launchedRevision) = synchronized(playbackLock) {
                if (session.get() !== request) return
                writtenFrames.set(0L)
                val startAt = pendingSeekMs.get().coerceAtLeast(0L)
                playbackOffsetMs.set(startAt)
                ready.set(false)
                startAt to seekRevision.get()
            }
            val process = try { startDecoder(request, launched) }
            catch (error: Exception) {
                if (session.get() !== request) return
                if (seekRevision.get() != launchedRevision) continue
                throw AudioPlaybackFailure(AudioFailureKind.Configuration, error)
            }
            synchronized(playbackLock) {
                if (session.get() !== request) {
                    process.destroyForcibly()
                    return
                }
                currentProcess = process
            }
            val stderr = StringBuilder()
            val stderrJob = scope.launch {
                process.errorStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.isNotBlank()) stderr.append(line).append('\n')
                    }
                }
            }
            val stdout = BufferedInputStream(process.inputStream)
            val line = try {
                outputLineFactory()
            } catch (error: Exception) {
                process.destroyForcibly()
                stderrJob.cancel()
                if (session.get() !== request) return
                if (seekRevision.get() != launchedRevision) continue
                throw AudioPlaybackFailure(AudioFailureKind.Output, error)
            }
            synchronized(playbackLock) {
                if (session.get() !== request) {
                    closeDecoder(process, line)
                    stderrJob.cancel()
                    return
                }
                currentLine = line
            }
            val buffer = ByteArray(PCM_SAMPLE_RATE * PCM_BYTES_PER_FRAME / 5)
            val equalizer = melox.playback.MeloXEqualizerController()
            var started = false
            var endedNaturally = false
            var requestedEnd = false
            var restartingForSeek = false
            var progressJob: Job? = null
            try {
                while (session.get() === request) {
                    if (seekRevision.get() != launchedRevision) {
                        restartingForSeek = true
                        break
                    }
                    if (paused.get()) {
                        Thread.sleep(40)
                        continue
                    }
                    // Pipe reads may split PCM frames. readNBytes preserves alignment
                    // across short reads and exposes a malformed final partial frame.
                    val read = stdout.readNBytes(buffer, 0, buffer.size)
                    if (session.get() !== request) return
                    if (seekRevision.get() != launchedRevision) {
                        restartingForSeek = true
                        break
                    }
                    if (read == 0) {
                        val exitCode = process.waitFor()
                        stderrJob.join()
                        if (session.get() !== request) return
                        if (seekRevision.get() != launchedRevision) {
                            restartingForSeek = true
                            break
                        }
                        val duration = _durationMs.value
                        requestedEnd = !started && exitCode == 0 && duration > 0L && launched >= duration
                        if (exitCode != 0 || !started && !requestedEnd) {
                            throw AudioPlaybackFailure(AudioFailureKind.Content,
                                IOException(stderr.toString().trim().ifBlank { "ffmpeg exited $exitCode without playable PCM" }))
                        }
                        // Media3 ENDED is after audible output, not decoder EOF. Keep
                        // the tail queued, and allow pause/seek/stop while it plays.
                        while (session.get() === request && seekRevision.get() == launchedRevision &&
                            line.longFramePosition < writtenFrames.get()) {
                            synchronized(playbackLock) {
                                if (session.get() === request && seekRevision.get() == launchedRevision) {
                                    if (paused.get()) { if (line.isRunning) line.stop() }
                                    else if (!line.isRunning) line.start()
                                }
                            }
                            delay(10)
                        }
                        if (session.get() !== request) return
                        restartingForSeek = seekRevision.get() != launchedRevision
                        endedNaturally = !restartingForSeek
                        break
                    }
                    if (read % PCM_BYTES_PER_FRAME != 0) {
                        throw AudioPlaybackFailure(AudioFailureKind.Content, IOException("ffmpeg emitted an incomplete PCM frame"))
                    }
                    if (!started) {
                        synchronized(playbackLock) {
                            if (session.get() !== request) break
                            if (seekRevision.get() != launchedRevision) {
                                restartingForSeek = true
                                break
                            }
                            started = true
                            ready.set(true)
                            pendingSeekMs.set(-1L)
                            if (!paused.get()) line.start()
                            _state.value = _state.value.copy(isPlaying = !paused.get(), isPaused = paused.get(), isLoading = false, isFinished = false, error = null, errorKind = null)
                        }
                        progressJob = startProgress(request)
                    }
                    equalizer.process(buffer, read, volumeLinear)
                    val written = writePcmBlock(line, buffer, read,
                        isCurrent = { session.get() === request && seekRevision.get() == launchedRevision },
                        isPaused = paused::get, pauseVersion = pauseVersion::get,
                        startIfPlaying = { synchronized(playbackLock) {
                            if (session.get() !== request || seekRevision.get() != launchedRevision || paused.get()) false
                            else { if (!line.isRunning) line.start(); true }
                        } }) { accepted ->
                        synchronized(playbackLock) {
                            if (session.get() === request && seekRevision.get() == launchedRevision) {
                                writtenFrames.addAndGet(accepted / PCM_BYTES_PER_FRAME.toLong())
                            }
                        }
                    }
                    if (session.get() === request && seekRevision.get() == launchedRevision) publishPcmEnergy(buffer, written)
                }
            } catch (error: Exception) {
                if (session.get() !== request) return
                if (seekRevision.get() != launchedRevision) restartingForSeek = true
                else if (error is AudioPlaybackFailure || error is CancellationException) throw error
                else throw AudioPlaybackFailure(if (error is IOException) AudioFailureKind.Content else AudioFailureKind.Output, error)
            } finally {
                equalizer.release()
                progressJob?.cancel()
                closeDecoder(process, line)
                stderrJob.cancel()
                synchronized(playbackLock) {
                    if (currentProcess === process) currentProcess = null
                    if (currentLine === line) currentLine = null
                }
            }
            if (session.get() !== request) return
            if (restartingForSeek || seekRevision.get() != launchedRevision) continue
            synchronized(playbackLock) {
                if (session.get() !== request) return
                if (seekRevision.get() != launchedRevision) continue
                if (endedNaturally) {
                    ready.set(false)
                    val finalPosition = if (requestedEnd) _durationMs.value else positionMs(launched, writtenFrames.get())
                    _positionMs.value = finalPosition
                    _progress.value = if (_durationMs.value > 0L) (finalPosition.toFloat() / _durationMs.value).coerceIn(0f, 1f) else 0f
                    _state.value = _state.value.copy(isPlaying = false, isPaused = false, isLoading = false, isFinished = true)
                }
            }
            if (endedNaturally) {
                request.onEnded()
                return
            }
        }
    }

    internal fun decoderArgs(request: PlayRequest, startAtMs: Long): List<String> = buildList {
        addAll(decoderCommand)
        addAll(listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-xerror"))
        add("-ss")
        add("%.3f".format(java.util.Locale.ROOT, startAtMs / 1000.0))
        if (request.url.startsWith("http")) {
            val headerBlob = buildString {
                append("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36\r\n")
                request.headers.forEach { (name, value) ->
                    if (!name.equals("User-Agent", ignoreCase = true)) append(name).append(": ").append(value).append("\r\n")
                }
            }
            addAll(listOf("-headers", headerBlob, "-i", request.url))
        } else {
            addAll(listOf("-i", request.url))
        }
        addAll(listOf("-f", "s16le", "-acodec", "pcm_s16le", "-ar", PCM_SAMPLE_RATE.toString(), "-ac", PCM_CHANNELS.toString(), "pipe:1"))
    }

    private fun startDecoder(request: PlayRequest, startAtMs: Long): Process {
        decoderCommand = FfmpegLocator.command()
        if (decoderCommand.isEmpty()) throw IllegalStateException("bundled ffmpeg missing")
        val command = decoderArgs(request, startAtMs)
        logDebug("AudioPlayer", "FFmpeg binary=${decoderCommand.first()} os=${System.getProperty("os.name")} sourceHost=${if (request.url.startsWith("http")) runCatching { java.net.URI(request.url).host }.getOrNull() else "local"}")
        logDebug("AudioPlayer", "decode ${if (request.url.startsWith("http")) "HTTP source" else "local source"} at ${startAtMs}ms")
        return ProcessBuilder(command).redirectErrorStream(false).start()
    }

    private fun closeDecoder(process: Process, line: SourceDataLine) {
        runCatching { line.stop() }
        runCatching { line.close() }
        runCatching { process.destroyForcibly() }
    }

    private fun startProgress(request: PlayRequest): Job =
        scope.launch {
            while (isActive && session.get() === request && ready.get()) {
                synchronized(playbackLock) {
                    if (session.get() !== request || !ready.get()) return@launch
                    val written = writtenFrames.get()
                    val buffered = currentLine?.let { unplayedFrames(it, written) } ?: 0L
                    val position = displayedPosition(pendingSeekMs.get(), playbackOffsetMs.get(), written, buffered)
                    val duration = _durationMs.value
                    _positionMs.value = position
                    _progress.value = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                }
                delay(100)
            }
        }

    fun setVolume(volume: Float) {
        volumeLinear = volume.coerceIn(0f, 1f)
        dualSession.get()?.volume = volumeLinear
    }


    fun pause() {
        synchronized(playbackLock) {
            if (_currentTrack.value == null) return
            paused.set(true)
            pauseVersion.incrementAndGet()
            _state.value = _state.value.copy(isPlaying = false, isPaused = true)
            currentLine?.let { runCatching { it.stop() } }
        }
        dualSession.get()?.pause()
    }

    fun resume() {
        synchronized(playbackLock) {
            if (_currentTrack.value == null || _state.value.error != null || _state.value.isFinished) return
            paused.set(false)
            _state.value = _state.value.copy(isPlaying = ready.get(), isPaused = false, isFinished = false)
            currentLine.takeIf { ready.get() }?.let { runCatching { it.start() } }
        }
        dualSession.get()?.resume()
    }

    fun stop(clearTrack: Boolean = true) {
        val decoder = synchronized(playbackLock) {
            session.set(null)
            dualSession.getAndSet(null)?.close()
            MeloXAutoMixTransitionRuntime.cancel()
            currentJob?.cancel()
            val old = currentProcess to currentLine
            currentProcess = null
            currentLine = null
            ready.set(false)
            paused.set(false)
            writtenFrames.set(0L)
            pendingSeekMs.set(-1L)
            seekRevision.incrementAndGet()
            _progress.value = 0f
            _positionMs.value = 0L
            _durationMs.value = 0L
            _state.value = PlayerState()
            if (clearTrack) _currentTrack.value = null
            old
        }
        decoder.second?.let { line -> runCatching { line.stop(); line.close() } }
        decoder.first?.let { process -> runCatching { process.destroyForcibly() } }
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        dualSession.get()?.let { engine ->
            engine.seek(target)
            _positionMs.value = target
            _progress.value = if (_durationMs.value > 0) (target.toFloat() / _durationMs.value).coerceIn(0f, 1f) else 0f
            MeloXAutoMixTransitionRuntime.cancel()
            return
        }
        val decoder = synchronized(playbackLock) {
            pendingSeekMs.set(target)
            seekRevision.incrementAndGet()
            _positionMs.value = target
            val duration = _durationMs.value
            _progress.value = if (duration > 0L) (target.toFloat() / duration).coerceIn(0f, 1f) else 0f
            currentProcess to currentLine
        }
        // A stalled network read/full stopped line must not prevent a new seek.
        decoder.second?.let { runCatching { it.close() } }
        decoder.first?.let { runCatching { it.destroyForcibly() } }
    }

    fun isReady(): Boolean = ready.get()

    fun getDurationMs(): Long = _durationMs.value

    fun getCurrentPositionMs(): Long = _positionMs.value

    internal fun decoderArgsFor(url: String, headers: Map<String, String>, startAtMs: Long): List<String> =
        decoderArgs(PlayRequest(url, headers, 0L), startAtMs)

    internal fun positionMs(offsetMs: Long, frames: Long): Long = offsetMs + frames * 1000L / PCM_SAMPLE_RATE

    private fun publishPcmEnergy(buffer: ByteArray, count: Int) {
        val samples = count / 2
        if (samples <= 0) return
        val shorts = ShortArray(samples)
        var index = 0
        while (index < samples) {
            val lo = buffer[index * 2].toInt() and 0xFF
            val hi = buffer[index * 2 + 1].toInt()
            shorts[index] = ((hi shl 8) or lo).toShort()
            index += 1
        }
        melox.playback.MeloXAudioReactiveRuntime.publishPcm(
            _currentTrack.value?.id?.let { "${it.source.storageValue}:${it.value}" },
            shorts,
            samples,
            volumeLinear,
        )
    }

    internal fun unplayedFrames(line: SourceDataLine, writtenFrames: Long): Long {
        val played = runCatching { line.longFramePosition }.getOrDefault(writtenFrames)
        return (writtenFrames - played).coerceAtLeast(0L)
    }

    internal fun displayedPosition(
        pendingSeekMs: Long,
        offsetMs: Long,
        frames: Long,
        bufferedFrames: Long = 0L,
    ): Long {
        if (pendingSeekMs >= 0L) return pendingSeekMs
        val heard = (frames - bufferedFrames.coerceAtLeast(0L)).coerceAtLeast(0L)
        return positionMs(offsetMs, heard)
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun openLocal(file: File): InputStream = BufferedInputStream(file.inputStream())
}

internal sealed class FfmpegLocation {
    data class Bundled(val file: File) : FfmpegLocation()
    data object Missing : FfmpegLocation()
}

internal object FfmpegLocator {
    private data class VerifiedDecoder(val file: File, val bytes: Long, val modified: Long)
    private val verified = java.util.WeakHashMap<ClassLoader, MutableMap<String, VerifiedDecoder>>()
    internal fun resourceName(): String {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        return if (os.contains("win")) "decoder/ffmpeg.exe" else "decoder/ffmpeg"
    }

    fun command(name: String = resourceName()): List<String> = when (val located = locate(name)) {
        is FfmpegLocation.Bundled -> listOf(located.file.absolutePath)
        FfmpegLocation.Missing -> emptyList()
    }

    internal fun locate(name: String = resourceName()): FfmpegLocation {
        val loader = Thread.currentThread().contextClassLoader ?: javaClass.classLoader ?: return FfmpegLocation.Missing
        val file = extract(name, loader) ?: return FfmpegLocation.Missing
        return FfmpegLocation.Bundled(file)
    }

    @Synchronized
    internal fun extract(name: String, loader: ClassLoader): File? {
        val input = loader.getResourceAsStream(name) ?: return null
        val root = File(System.getProperty("java.io.tmpdir")).canonicalFile
        val key = "${root.path}:$name"
        val cache = verified.getOrPut(loader) { mutableMapOf() }
        cache[key]?.let { previous ->
            if (previous.file.isFile && previous.file.canExecute() && previous.file.length() == previous.bytes &&
                previous.file.lastModified() == previous.modified) {
                input.close()
                return previous.file
            }
        }
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        input.use { stream -> java.security.DigestInputStream(stream, digest).use { it.copyTo(java.io.OutputStream.nullOutputStream()) } }
        val hash = digest.digest()
        fun matches(file: File): Boolean = file.isFile && file.inputStream().use { stream ->
            val check = java.security.MessageDigest.getInstance("SHA-256")
            java.security.DigestInputStream(stream, check).use { it.copyTo(java.io.OutputStream.nullOutputStream()) }
            java.security.MessageDigest.isEqual(hash, check.digest())
        }
        // Accept an existing legacy extraction only after checking its actual content,
        // never merely its length. No second 80+ MB file is required for a valid cache.
        val legacy = File(root, "melox-" + name.replace('/', '-'))
        val extension = if (name.endsWith(".exe")) ".exe" else ""
        val hex = hash.joinToString("") { "%02x".format(it) }
        val target = if (legacy.canExecute() && matches(legacy)) legacy else
            File(root, "melox-${name.replace('/', '-').removeSuffix(extension)}-${hex.take(16)}$extension")
        if (!matches(target)) {
            val part = File.createTempFile(target.name + ".", ".part", root)
            try {
                checkNotNull(loader.getResourceAsStream(name)).use { stream -> part.outputStream().use { stream.copyTo(it) } }
                check(matches(part)) { "Bundled FFmpeg changed during extraction" }
                try {
                    java.nio.file.Files.move(part.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                } catch (error: java.io.IOException) {
                    // Another process may have installed and started this same version on Windows.
                    if (!matches(target)) throw error
                }
            } finally { part.delete() }
        }
        // Windows uses executable file ACLs, not POSIX chmod; a redundant chmod
        // must not delete a valid executable that Windows can already launch.
        if (!target.canExecute() && !System.getProperty("os.name").orEmpty().lowercase().contains("win")) target.setExecutable(true)
        if (!target.canExecute()) return null
        cache[key] = VerifiedDecoder(target, target.length(), target.lastModified())
        logDebug("AudioPlayer", "Bundled FFmpeg verified: sha256=$hex bytes=${target.length()}")
        return target
    }
}

// Identity distinguishes two consecutive plays of the same URI from each other.
internal class PlayRequest(
    @Volatile var url: String,
    @Volatile var headers: Map<String, String>,
    val knownDurationMs: Long,
    val onEnded: () -> Unit = {},
    val onFailure: (AudioPlaybackFailure) -> Unit = {},
) {
    val tracks = java.util.concurrent.ConcurrentHashMap<String, MusicTrack>()
    @Volatile var handoff: (() -> Unit)? = null
}

enum class AudioFailureKind { Content, Output, Configuration }

class AudioPlaybackFailure(val kind: AudioFailureKind, cause: Throwable) :
    IOException(cause.message?.takeIf(String::isNotBlank) ?: "播放失败", cause)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = false,
    val trackTitle: String = "",
    val trackArtist: String = "",
    val error: String? = null,
    val errorKind: AudioFailureKind? = null,
)
