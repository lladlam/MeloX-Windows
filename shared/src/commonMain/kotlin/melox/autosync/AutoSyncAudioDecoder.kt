package melox.autosync

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import melox.platform.meloXCacheDir
import melox.player.FfmpegPcmDecoder

/** A bounded, app-owned PCM file; it never overwrites or deletes the source audio. */
internal class AutoSyncPcmFile(private val file: File, val startMs: Long, val sampleCount: Long) : Closeable {
    fun window(start: Long, end: Long): FloatArray {
        val first = ((start - startMs).coerceAtLeast(0) * SAMPLE_RATE / 1000).coerceAtMost(sampleCount)
        val last = ((end - startMs).coerceAtLeast(0) * SAMPLE_RATE / 1000).coerceIn(first, sampleCount)
        require(last - first <= SAMPLE_RATE * 15L) { "AutoSync window too long" }
        val bytes = ByteArray(((last - first) * 2).toInt())
        RandomAccessFile(file, "r").use { input -> input.seek(first * 2); input.readFully(bytes) }
        return FloatArray(bytes.size / 2) { i ->
            ((bytes[i * 2].toInt() and 255) or (bytes[i * 2 + 1].toInt() shl 8)).toShort().toInt() / 32768f
        }
    }

    override fun close() { file.delete() }

    companion object { const val SAMPLE_RATE = 16_000 }
}

/** Decode the selected recording using the same packaged FFmpeg as playback. */
internal object AutoSyncAudioDecoder {
    private const val MAX_RANGE_MS = 30 * 60_000L

    @OptIn(InternalCoroutinesApi::class)
    suspend fun <T> withPcm(uri: String, headers: Map<String, String>, startMs: Long, endMs: Long,
                            block: suspend (AutoSyncPcmFile) -> T): T =
        withContext(Dispatchers.IO) {
            require(startMs >= 0 && endMs > startMs && endMs - startMs <= MAX_RANGE_MS)
            val root = File(meloXCacheDir(), "autosync-pcm")
            check(root.isDirectory || root.mkdirs()) { "Cannot create AutoSync PCM directory" }
            val file = File.createTempFile("alignment-", ".pcm", root)
            try {
                val samples = withTimeout((30_000L + (endMs - startMs) / 2).coerceAtMost(180_000L)) {
                    FfmpegPcmDecoder(uri, headers, startMs, endMs - startMs,
                        outputRate = AutoSyncPcmFile.SAMPLE_RATE, outputChannels = 1).use { decoder ->
                        // Closing the process interrupts a blocked pipe on track change or timeout.
                        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { decoder.close() }
                        try {
                            var bytesWritten = 0L
                            file.outputStream().buffered().use { output ->
                                val buffer = ByteArray(16_384)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val count = decoder.read(buffer)
                                    if (count == 0) break
                                    bytesWritten += count
                                    check(bytesWritten <= MAX_RANGE_MS * AutoSyncPcmFile.SAMPLE_RATE * 2 / 1000) { "AutoSync PCM exceeds limit" }
                                    output.write(buffer, 0, count)
                                }
                            }
                            decoder.verifyExit()
                            currentCoroutineContext().ensureActive()
                            bytesWritten / 2
                        } finally { cancellation.dispose() }
                    }
                }
                check(samples > 0) { "AutoSync audio decoded no PCM" }
                AutoSyncPcmFile(file, startMs, samples).use { pcm -> block(pcm) }
            } catch (error: Throwable) {
                file.delete() // Only this invocation's temporary file.
                throw error
            }
        }
}
