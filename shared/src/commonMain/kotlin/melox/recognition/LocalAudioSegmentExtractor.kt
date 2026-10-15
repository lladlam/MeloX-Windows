package melox.recognition

import melox.player.FfmpegPcmDecoder
import java.io.File
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

/**
 * Real short-segment decode for local recognition.
 *
 * Pinned Android uses MediaExtractor + MediaCodec and writes mono 8 kHz float
 * PCM; desktop uses the same contract through the bundled FFmpeg PCM decoder.
 * Decoding is blocking by design — the caller owns the worker thread, deadline
 * guard and interruption so cancelling a recognition closes this decoder too.
 */
internal object LocalAudioSegmentExtractor {
    /** Pinned recognition requests 9 seconds of local audio, never a longer GET fingerprint. */
    const val SegmentLengthMs = 9_000L
    private const val InputRate = 44_100
    private const val Channels = 2
    private val active = AtomicReference<FfmpegPcmDecoder?>()

    fun resolveFile(uri: String): File = if (uri.startsWith("file:")) File(URI(uri)) else File(uri)

    /** Closes a decode that is still running when the recognition job is cancelled. */
    fun abortActive() { active.getAndSet(null)?.let { session -> runCatching { session.close() } } }

    /**
     * @param segmentStartMs byte position offset in the source; [Long.MAX_VALUE] is not accepted.
     * @return mono float PCM at [SongRecognitionClient.SampleRate]; the sample count reflects
     * only audio actually decoded, without fabricated padding.
     */
    fun extractBlocking(
        uri: String,
        segmentStartMs: Long,
        segmentLengthMs: Long = SegmentLengthMs,
        onProgress: (Float) -> Unit = {},
    ): FloatArray {
        val file = resolveFile(uri)
        require(file.isFile) { "本地音频文件不存在" }
        val lengthMs = segmentLengthMs.coerceIn(1_000L, SegmentLengthMs)
        val startMs = segmentStartMs.coerceAtLeast(0L)
        onProgress(0f)
        val session = FfmpegPcmDecoder(file.canonicalPath, startMs = startMs, durationMs = lengthMs)
        active.set(session)
        try {
            if (Thread.currentThread().isInterrupted) throw java.io.IOException("本地音频识别已取消")
            val bytes = ByteArray((lengthMs * InputRate / 1_000L).toInt() * Channels * 2)
            val count = session.read(bytes)
            session.verifyExit()
            if (count <= 0) throw java.io.IOException("本地音频没有解码出任何 PCM")
            val pcm = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val frames = count / (Channels * 2)
            val mono = FloatArray(frames) { index ->
                var sum = 0
                repeat(Channels) { sum += pcm.get(index * Channels + it).toInt() }
                sum.toFloat() / (Channels * 32_768f)
            }
            onProgress(1f)
            return SongRecognitionClient.resample(mono, InputRate)
        } finally {
            active.compareAndSet(session, null)
            runCatching { session.close() }
        }
    }
}
