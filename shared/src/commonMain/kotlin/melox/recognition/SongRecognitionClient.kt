package melox.recognition

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import melox.model.SearchSong
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android's real 8kHz PCM → pinned AFP WASM → NetEase match pipeline on desktop. */
class SongRecognitionClient(
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : AutoCloseable {
    data class SongRecognitionResult(val song: SearchSong, val startTimeMs: Long)
    private val closed = AtomicBoolean(false)
    private val operation = Mutex()
    private val microphone = AtomicReference<TargetDataLine?>()
    private val request = AtomicReference<Call?>()

    suspend fun recognize(durationSeconds: Int): List<SongRecognitionResult> = operation.withLock {
        check(!closed.get()) { "听歌识曲已关闭" }
        require(durationSeconds in 3..15)
        val samples = capture(durationSeconds)
        recognizeSamples(samples)
    }

    suspend fun recognizeLocal(uri: String, durationMs: Long, segmentStartMs: Long? = null): List<SongRecognitionResult> = operation.withLock {
        check(!closed.get()) { "听歌识曲已关闭" }
        val sourceMs = durationMs.takeIf { it > 0 } ?: LocalAudioSegmentExtractor.SegmentLengthMs
        val lengthMs = LocalAudioSegmentExtractor.SegmentLengthMs.coerceAtMost(sourceMs.coerceAtLeast(1_000L))
        val maximumStart = (sourceMs - lengthMs).coerceAtLeast(0L)
        val start = (segmentStartMs ?: maximumStart / 2).coerceIn(0L, maximumStart)
        val samples = cancellableWork {
            if (closed.get() || Thread.currentThread().isInterrupted) throw IOException("听歌识曲已取消")
            LocalAudioSegmentExtractor.extractBlocking(uri, start, lengthMs)
        }
        recognizeSamples(samples)
    }

    internal suspend fun recognizeSamples(samples: FloatArray): List<SongRecognitionResult> {
        check(!closed.get()) { "听歌识曲已关闭" }
        require(samples.size * 1_000L / SampleRate >= 1_000L) { "本地音频片段不足 1 秒，无法生成音频指纹" }
        val fingerprint = withContext(Dispatchers.IO) { PinnedAudioFingerprint.generate(samples) }
        currentCoroutineContext().ensureActive()
        check(!closed.get()) { "听歌识曲已关闭" }
        // Segment duration is rounded from the real decoded sample count (never padded to a claim).
        val duration = ((samples.size + SampleRate / 2) / SampleRate).coerceAtLeast(1)
        val url = "https://interface.music.163.com/api/music/audio/match".toHttpUrl().newBuilder()
            .addQueryParameter("sessionId", "0123456789abcdef")
            .addQueryParameter("algorithmCode", "shazam_v2")
            .addQueryParameter("duration", duration.toString())
            .addQueryParameter("rawdata", fingerprint)
            .addQueryParameter("times", "1").addQueryParameter("decrypt", "1").build()
        return cancellableWork {
            val call = httpClient.newCall(Request.Builder().url(url).header("User-Agent", "MeloX-Desktop/1.0").get().build())
            request.set(call)
            try {
                check(!closed.get() && !Thread.currentThread().isInterrupted) { "听歌识曲已取消" }
                call.execute().use { response ->
                    if (!response.isSuccessful) throw IOException("听歌识曲请求失败：HTTP ${response.code}")
                    parseMatches(JSONObject(response.body.string()))
                }
            } finally { request.compareAndSet(call, null) }
        }
    }

    private suspend fun capture(seconds: Int): FloatArray = cancellableWork {
        val format = AudioFormat(SampleRate.toFloat(), 16, 1, true, false)
        val line = AudioSystem.getTargetDataLine(format)
        microphone.set(line)
        try {
            if (closed.get() || Thread.currentThread().isInterrupted) throw IOException("听歌识曲已取消")
            line.open(format, 4_096)
            line.start()
            val bytes = ByteArray(seconds * SampleRate * 2)
            var offset = 0
            while (offset < bytes.size) {
                if (closed.get() || Thread.currentThread().isInterrupted) throw IOException("录音已取消")
                val read = line.read(bytes, offset, minOf(4_096, bytes.size - offset))
                if (read <= 0) throw IOException("没有收到麦克风音频，请检查系统录音设备与权限")
                offset += read
            }
            val pcm = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            FloatArray(pcm.remaining()) { pcm.get().toFloat() / 32768f }
        } finally { microphone.compareAndSet(line, null); line.close() }
    }

    private suspend fun <T> cancellableWork(block: () -> T): T = suspendCancellableCoroutine { continuation ->
        val future = worker.submit {
            if (!continuation.isActive) return@submit
            try { val result = block(); if (continuation.isActive) continuation.resume(result) }
            catch (failure: Throwable) { if (continuation.isActive) continuation.resumeWithException(failure) }
        }
        continuation.invokeOnCancellation { abortActive(); future.cancel(true) }
    }

    private fun abortActive() {
        runCatching { microphone.getAndSet(null)?.close() }
        LocalAudioSegmentExtractor.abortActive()
        request.getAndSet(null)?.cancel()
    }
    override fun close() { closed.set(true); abortActive() }

    companion object {
        const val SampleRate = 8_000
        private val worker = Executors.newCachedThreadPool { task -> Thread(task, "melox-recognition").apply { isDaemon = true } }
        internal fun resample(input: FloatArray, inputRate: Int): FloatArray {
            require(inputRate > 0)
            if (input.isEmpty()) return FloatArray(0)
            val count = (input.size.toLong() * SampleRate / inputRate).toInt()
            return FloatArray(count) { index ->
                val position = index * inputRate.toDouble() / SampleRate
                val first = position.toInt().coerceAtMost(input.lastIndex)
                val next = (first + 1).coerceAtMost(input.lastIndex)
                input[first] + (input[next] - input[first]) * (position - first).toFloat()
            }
        }
        internal fun parseMatches(root: JSONObject): List<SongRecognitionResult> {
            if (root.optInt("code", 200) != 200) throw IOException(root.optString("message").ifBlank { "网易云听歌识曲服务返回错误" })
            val candidates = root.optJSONObject("data")?.optJSONArray("result") ?: JSONArray()
            val seen = mutableSetOf<Long>()
            return buildList {
                for (index in 0 until candidates.length()) {
                    val candidate = candidates.optJSONObject(index) ?: continue
                    val value = candidate.optJSONObject("song") ?: continue
                    val id = value.optLong("id").takeIf { it > 0 && seen.add(it) } ?: continue
                    val album = value.optJSONObject("al") ?: value.optJSONObject("album")
                    val artists = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
                    val names = (0 until artists.length()).mapNotNull { artists.optJSONObject(it)?.optString("name")?.takeIf(String::isNotBlank) }
                    val artwork = (album?.optString("picUrl")?.takeIf(String::isNotBlank)
                        ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank) ?: value.optString("picUrl").takeIf(String::isNotBlank))?.replace("http://", "https://")
                    add(SongRecognitionResult(SearchSong(id, value.optString("name").ifBlank { "未知歌曲" }, names.joinToString(" / ").ifBlank { "未知歌手" },
                        album?.optString("name").orEmpty(), artwork, value.optLong("dt", value.optLong("duration")).coerceAtLeast(0L),
                        albumId = album?.optLong("id")?.takeIf { it > 0 }, artistId = artists.optJSONObject(0)?.optLong("id")?.takeIf { it > 0 }),
                        candidate.optLong("startTime").coerceAtLeast(0L)))
                }
            }
        }
    }
}
