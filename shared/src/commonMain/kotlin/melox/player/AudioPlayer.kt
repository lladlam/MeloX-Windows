package melox.player

import melox.music.model.MusicTrack
import melox.platform.logDebug
import melox.platform.logError
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.sound.sampled.*
import javax.sound.sampled.DataLine.Info
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 桌面音频播放器，基于 javax.sound.sampled。
 * 支持 HTTP 流式播放和本地文件播放。
 */
object AudioPlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentClip: Clip? = null
    private var currentJob: Job? = null
    private var currentSource: String? = null
    private var volumeLinear: Float = 1f
    @Volatile
    private var suppressEnd = false

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

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun play(url: String, track: MusicTrack, headers: Map<String, String> = emptyMap()) {
        stop()
        _currentTrack.value = track
        _state.value = PlayerState(isPlaying = true, trackTitle = track.title, trackArtist = track.artistText)
        currentSource = url

        currentJob = scope.launch {
            try {
                if (url.startsWith("http")) {
                    playFromUrl(url, headers)
                } else {
                    playFromFile(File(url))
                }
            } catch (e: Exception) {
                logError("AudioPlayer", "播放失败: ${e.message}")
                _state.value = PlayerState(error = e.message)
            }
        }
    }

    private suspend fun playFromUrl(url: String, headers: Map<String, String>) = withContext(Dispatchers.IO) {
        logDebug("AudioPlayer", "从URL播放: $url")
        val builder = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        headers.forEach { (name, value) ->
            if (name.equals("User-Agent", ignoreCase = true)) {
                builder.header(name, value)
            } else {
                builder.addHeader(name, value)
            }
        }
        val request = builder.build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")

        val tempFile = File.createTempFile("melox_audio", ".tmp")
        tempFile.deleteOnExit()

        response.body?.byteStream()?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }

        playFromFile(tempFile)
    }

    private suspend fun playFromFile(file: File) = withContext(Dispatchers.IO) {
        logDebug("AudioPlayer", "从文件播放: ${file.name}")
        val audioStream = AudioSystem.getAudioInputStream(file)

        val format = audioStream.format
        val info = Info(Clip::class.java, format)
        val clip = AudioSystem.getLine(info) as Clip
        clip.open(audioStream)

        currentClip = clip
        _durationMs.value = clip.microsecondLength / 1000L
        applyVolume(clip, volumeLinear)
        clip.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP && !suppressEnd) {
                if (clip.microsecondPosition >= clip.microsecondLength) {
                    _state.value = _state.value.copy(isPlaying = false, isFinished = true)
                    onTrackEnded?.invoke()
                }
            }
        }

        clip.start()
        startProgressTracking(clip)
    }

    private fun startProgressTracking(clip: Clip) {
        scope.launch {
            while (isActive && clip.isOpen) {
                val total = clip.microsecondLength.toFloat()
                val current = clip.microsecondPosition.toFloat()
                _progress.value = if (total > 0) (current / total).coerceIn(0f, 1f) else 0f
                _positionMs.value = clip.microsecondPosition / 1000L
                _durationMs.value = clip.microsecondLength / 1000L
                delay(100)
            }
        }
    }

    fun setVolume(volume: Float) {
        volumeLinear = volume.coerceIn(0f, 1f)
        currentClip?.let { applyVolume(it, volumeLinear) }
    }

    private fun applyVolume(clip: Clip, linear: Float) {
        runCatching {
            val control = clip.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl
            val min = control.minimum
            val max = control.maximum
            val gain = if (linear <= 0f) {
                min
            } else {
                (20.0 * kotlin.math.log10(linear.toDouble())).toFloat().coerceIn(min, max)
            }
            control.value = gain
        }
    }

    fun pause() {
        currentClip?.let { clip ->
            if (clip.isActive) {
                suppressEnd = true
                try {
                    clip.stop()
                } finally {
                    suppressEnd = false
                }
                _state.value = _state.value.copy(isPlaying = false, isPaused = true)
            }
        }
    }

    fun resume() {
        currentClip?.let { clip ->
            if (!clip.isActive && clip.isOpen) {
                clip.start()
                _state.value = _state.value.copy(isPlaying = true, isPaused = false, isFinished = false)
            }
        }
    }

    fun stop() {
        suppressEnd = true
        try {
            currentJob?.cancel()
            currentClip?.let { clip ->
                try {
                    clip.stop()
                    clip.close()
                } catch (_: Exception) {}
            }
        } finally {
            suppressEnd = false
        }
        currentClip = null
        currentSource = null
        _progress.value = 0f
        _positionMs.value = 0L
        _durationMs.value = 0L
        _state.value = PlayerState()
    }

    fun seekTo(positionMs: Long) {
        currentClip?.let { clip ->
            clip.microsecondPosition = positionMs * 1000
            _positionMs.value = positionMs
            _progress.value = if (clip.microsecondLength > 0) {
                (positionMs * 1000f / clip.microsecondLength).coerceIn(0f, 1f)
            } else 0f
        }
    }

    fun getDurationMs(): Long {
        return currentClip?.let { it.microsecondLength / 1000 } ?: 0L
    }

    fun getCurrentPositionMs(): Long {
        return currentClip?.let { it.microsecondPosition / 1000 } ?: 0L
    }
}

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isFinished: Boolean = false,
    val trackTitle: String = "",
    val trackArtist: String = "",
    val error: String? = null,
)
