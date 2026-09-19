package melox.player

import melox.music.model.MusicTrack
import melox.platform.logDebug
import melox.platform.logError
import melox.platform.logInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
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

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    fun play(url: String, track: MusicTrack) {
        stop()
        _currentTrack.value = track
        _state.value = PlayerState(isPlaying = true, trackTitle = track.title, trackArtist = track.artistText)
        currentSource = url

        currentJob = scope.launch {
            try {
                if (url.startsWith("http")) {
                    playFromUrl(url)
                } else {
                    playFromFile(File(url))
                }
            } catch (e: Exception) {
                logError("AudioPlayer", "播放失败: ${e.message}")
                _state.value = PlayerState(error = e.message)
            }
        }
    }

    private suspend fun playFromUrl(url: String) = withContext(Dispatchers.IO) {
        logDebug("AudioPlayer", "从URL播放: $url")
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()

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
        clip.addLineListener { event ->
            if (event.type == LineEvent.Type.STOP) {
                if (clip.microsecondPosition >= clip.microsecondLength) {
                    _state.value = PlayerState(isFinished = true)
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
                delay(100)
            }
        }
    }

    fun pause() {
        currentClip?.let { clip ->
            if (clip.isActive) {
                clip.stop()
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun resume() {
        currentClip?.let { clip ->
            if (!clip.isActive && clip.isOpen) {
                clip.start()
                _state.value = _state.value.copy(isPlaying = true)
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentClip?.let { clip ->
            try {
                clip.stop()
                clip.close()
            } catch (_: Exception) {}
        }
        currentClip = null
        currentSource = null
        _progress.value = 0f
        _state.value = PlayerState()
    }

    fun seekTo(positionMs: Long) {
        currentClip?.let { clip ->
            clip.microsecondPosition = positionMs * 1000
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
