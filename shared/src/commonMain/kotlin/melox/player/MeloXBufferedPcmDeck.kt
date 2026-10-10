package melox.player

import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Bounded one-second decoded preload. Pipe EOF/error is distinct from PCM availability. */
internal class MeloXBufferedPcmDeck(source: MeloXDeckSource, filters: String? = null) : Closeable {
    private data class Packet(val pcm: ByteArray? = null, val failure: Throwable? = null)
    private val decoder = try { FfmpegPcmDecoder(source.uri, source.headers, source.startMs, filters = filters) }
        catch (failure: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Configuration, failure) }
    private val closed = AtomicBoolean(false)
    private val packets = ArrayBlockingQueue<Packet>(50)
    private var current = ByteArray(0)
    private var offset = 0
    private var ended = false
    private val worker = thread(name = "melox-pcm-preload", isDaemon = true) {
        try {
            while (!closed.get()) {
                val bytes = ByteArray(3528)
                val count = decoder.read(bytes)
                if (count == 0) { decoder.verifyExit(); packets.put(Packet()); break }
                packets.put(Packet(bytes.copyOf(count)))
            }
        } catch (interrupted: InterruptedException) { Thread.currentThread().interrupt() }
        catch (failure: Throwable) { if (!closed.get()) runCatching { packets.put(Packet(failure = failure)) } }
    }
    fun read(bytes: ByteArray, length: Int = bytes.size, wait: Boolean = true): Int {
        require(length in 0..bytes.size && length % 4 == 0)
        var copied = 0
        while (copied < length && !ended) {
            if (closed.get()) throw IOException("PCM deck released")
            if (offset == current.size) {
                val packet = if (wait) packets.take() else packets.poll() ?: throw IOException("Incoming PCM preload underrun")
                packet.failure?.let { throw IOException(it.message ?: "Native PCM decode failed", it) }
                if (packet.pcm == null) { ended = true; break }
                current = packet.pcm; offset = 0
            }
            val count = minOf(length - copied, current.size - offset)
            current.copyInto(bytes, copied, offset, offset + count)
            copied += count; offset += count
        }
        return copied
    }
    fun awaitReady() {
        val packet = packets.take()
        packet.failure?.let { throw IOException(it.message ?: "Native PCM preload failed", it) }
        current = packet.pcm ?: throw IOException("Incoming source has no playable PCM")
        offset = 0
    }
    fun isClosed(): Boolean = closed.get()
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        worker.interrupt()
        decoder.close()
        packets.clear()
        packets.offer(Packet(failure = IOException("PCM deck released")))
        if (Thread.currentThread() !== worker) worker.join(2000)
    }
}
