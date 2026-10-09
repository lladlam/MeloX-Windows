package melox.player

import melox.music.model.MusicTrack
import melox.platform.logDebug
import melox.platform.logError
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

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
    private val httpClient = OkHttpClient.Builder().followRedirects(true).build()

    private var currentJob: Job? = null
    private var currentLine: SourceDataLine? = null
    private var currentProcess: Process? = null
    private var volumeLinear: Float = 1f
    private val playbackLock = Any()
    private val paused = AtomicBoolean(false)
    private val ready = AtomicBoolean(false)
    private val writtenFrames = AtomicLong(0L)
    private val pendingSeekMs = AtomicLong(-1L)
    private val session = AtomicReference<PlayRequest?>(null)

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

    @Volatile
    var onTrackEnded: (() -> Unit)? = null

    @Volatile
    var decoderCommand: List<String> = emptyList() // Bundled extraction belongs to startDecoder's IO worker.

    internal val playbackOffsetMs = AtomicLong(0L)

    /** Publish the selected item before URL resolution; never leave old audio running. */
    fun selectTrack(track: MusicTrack, startPositionMs: Long = 0L, knownDurationMs: Long = 0L) {
        stop(clearTrack = false)
        _currentTrack.value = track
        _durationMs.value = (knownDurationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L).coerceAtLeast(0L)
        _positionMs.value = startPositionMs.coerceAtLeast(0L)
        _progress.value = if (_durationMs.value > 0L) (_positionMs.value.toFloat() / _durationMs.value).coerceIn(0f, 1f) else 0f
        _state.value = PlayerState(isLoading = true, trackTitle = track.title, trackArtist = track.artistText)
    }

    fun reportFailure(error: Throwable) {
        _state.value = _state.value.copy(isLoading = false, isPlaying = false, isPaused = false,
            error = error.message?.takeIf { it.isNotBlank() } ?: "播放失败")
    }

    fun play(
        url: String,
        track: MusicTrack,
        headers: Map<String, String> = emptyMap(),
        startPositionMs: Long = 0L,
        knownDurationMs: Long = 0L,
        playWhenReady: Boolean = true,
    ) {
        selectTrack(track, startPositionMs, knownDurationMs)
        paused.set(!playWhenReady)
        _state.value = _state.value.copy(isPaused = !playWhenReady)
        pendingSeekMs.set(startPositionMs.coerceAtLeast(0L))
        val request = PlayRequest(url, headers, knownDurationMs.coerceAtLeast(0L))
        session.set(request)
        currentJob = scope.launch {
            try {
                runSession(request)
            } catch (e: Exception) {
                synchronized(playbackLock) {
                    if (session.get() !== request) return@launch
                    logError("AudioPlayer", "播放失败: ${e.message}")
                    reportFailure(e)
                    currentProcess?.let { process ->
                        runCatching { process.destroyForcibly() }
                        if (currentProcess === process) currentProcess = null
                    }
                }
            }
        }
    }

    private fun runSession(request: PlayRequest) {
        var startAt = pendingSeekMs.get().coerceAtLeast(0L)
        while (session.get() === request) {
            synchronized(playbackLock) {
                if (session.get() !== request) return
                writtenFrames.set(0L)
                playbackOffsetMs.set(startAt)
                ready.set(false)
            }
            val launched = startAt
            val process = startDecoder(request, launched)
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
            val stdout = process.inputStream
            val line = try {
                openLine()
            } catch (error: Exception) {
                process.destroyForcibly()
                stderrJob.cancel()
                throw error
            }
            synchronized(playbackLock) {
                if (session.get() !== request) {
                    closeDecoder(process, line)
                    stderrJob.cancel()
                    return
                }
                currentLine = line
            }
            applyVolume(line, volumeLinear)
            val buffer = ByteArray(PCM_SAMPLE_RATE * PCM_BYTES_PER_FRAME / 5)
            var started = false
            var endedNaturally = false
            var restartingForSeek = false
            var progressJob: Job? = null
            try {
                while (session.get() === request) {
                    val requested = pendingSeekMs.get()
                    if (requested >= 0L && requested != launched) {
                        startAt = requested
                        restartingForSeek = true
                        break
                    }
                    if (paused.get()) {
                        if (line.isRunning) line.stop()
                        Thread.sleep(40)
                        continue
                    } else if (!line.isRunning && started) {
                        line.start()
                    }
                    val read = stdout.read(buffer)
                    if (read < 0) {
                        endedNaturally = started
                        break
                    }
                    if (read == 0) continue
                    if (!started) {
                        val raced = pendingSeekMs.get()
                        if (raced >= 0L && raced != launched) {
                            startAt = raced
                            restartingForSeek = true
                            break
                        }
                        synchronized(playbackLock) {
                            if (session.get() !== request) break
                            started = true
                            ready.set(true)
                            pendingSeekMs.compareAndSet(raced, -1L)
                            if (!paused.get()) line.start()
                            _state.value = _state.value.copy(isPlaying = !paused.get(), isPaused = paused.get(), isLoading = false, isFinished = false, error = null)
                        }
                        progressJob = startProgress(request)
                    }
                    line.write(buffer, 0, read)
                    synchronized(playbackLock) {
                        if (session.get() === request) {
                            writtenFrames.addAndGet(read / PCM_BYTES_PER_FRAME.toLong())
                            publishPcmEnergy(buffer, read)
                        }
                    }
                }
            } finally {
                progressJob?.cancel()
                closeDecoder(process, line)
                stderrJob.cancel()
                synchronized(playbackLock) {
                    if (currentProcess === process) currentProcess = null
                    if (currentLine === line) currentLine = null
                }
            }
            if (session.get() !== request) return
            if (restartingForSeek) continue
            synchronized(playbackLock) {
                if (session.get() !== request) return
                if (!started) {
                    ready.set(false)
                    val code = runCatching { process.exitValue() }.getOrDefault(-1)
                    _state.value = _state.value.copy(
                        isPlaying = false,
                        isFinished = false,
                        isLoading = false,
                        error = stderr.toString().ifBlank { "ffmpeg exited $code" }.trim(),
                    )
                    return
                }
                if (endedNaturally) {
                    ready.set(false)
                    _state.value = _state.value.copy(isPlaying = false, isFinished = true)
                    onTrackEnded?.invoke()
                    return
                }
            }
        }
    }

    internal fun decoderArgs(request: PlayRequest, startAtMs: Long): List<String> = buildList {
        addAll(decoderCommand)
        addAll(listOf("-hide_banner", "-loglevel", "error", "-nostdin"))
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
        logDebug("AudioPlayer", "decode ${request.url} at ${startAtMs}ms")
        return ProcessBuilder(command).redirectErrorStream(false).start()
    }

    private fun openLine(): SourceDataLine {
        val format = AudioFormat(PCM_SAMPLE_RATE.toFloat(), 16, PCM_CHANNELS, true, false)
        val line = AudioSystem.getSourceDataLine(format)
        line.open(format, PCM_SAMPLE_RATE * PCM_BYTES_PER_FRAME)
        return line
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
        currentLine?.let { applyVolume(it, volumeLinear) }
    }

    private fun applyVolume(line: SourceDataLine, linear: Float) {
        runCatching {
            val control = line.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            control.value = if (linear <= 0f) {
                control.minimum
            } else {
                (20.0 * kotlin.math.log10(linear.toDouble())).toFloat().coerceIn(control.minimum, control.maximum)
            }
        }
    }

    fun pause(): Unit = synchronized(playbackLock) {
        if (_currentTrack.value == null) return
        paused.set(true)
        _state.value = _state.value.copy(isPlaying = false, isPaused = true)
    }

    fun resume(): Unit = synchronized(playbackLock) {
        if (_currentTrack.value == null || _state.value.error != null || _state.value.isFinished) return
        paused.set(false)
        _state.value = _state.value.copy(isPlaying = ready.get(), isPaused = false, isFinished = false)
    }

    fun stop(clearTrack: Boolean = true) {
        val decoder = synchronized(playbackLock) {
            session.set(null)
            currentJob?.cancel()
            val old = currentProcess to currentLine
            currentProcess = null
            currentLine = null
            ready.set(false)
            paused.set(false)
            writtenFrames.set(0L)
            pendingSeekMs.set(-1L)
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
        pendingSeekMs.set(target)
        _positionMs.value = target
        val duration = _durationMs.value
        _progress.value = if (duration > 0L) (target.toFloat() / duration).coerceIn(0f, 1f) else 0f
        if (!ready.get()) return
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

    internal fun extract(name: String, loader: ClassLoader): File? {
        val input = loader.getResourceAsStream(name) ?: return null
        val target = File(System.getProperty("java.io.tmpdir"), "melox-" + name.replace('/', '-'))
        val part = File(target.path + ".part")
        input.use { stream -> part.outputStream().use { stream.copyTo(it) } }
        val replace = !target.exists() || !target.canExecute() || target.length() != part.length()
        if (replace) {
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
        } else {
            part.delete()
        }
        if (!target.setExecutable(true) || !target.canExecute()) {
            target.delete()
            return null
        }
        return target
    }
}

// Identity distinguishes two consecutive plays of the same URI from each other.
internal class PlayRequest(
    val url: String,
    val headers: Map<String, String>,
    val knownDurationMs: Long,
)

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val isLoading: Boolean = false,
    val trackTitle: String = "",
    val trackArtist: String = "",
    val error: String? = null,
)
