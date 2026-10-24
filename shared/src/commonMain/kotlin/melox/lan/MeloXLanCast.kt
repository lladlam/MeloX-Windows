package melox.lan

import java.io.*
import java.net.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.TimeUnit
import javax.sound.sampled.*
import org.json.JSONObject

data class LanPcmFormat(val sampleRate: Int = 48000, val channels: Int = 2) {
    init { require(sampleRate in 8000..192000 && channels in 1..2) { "unsupported_format" } }
    val frameBytes get() = channels * 2
    fun json() = JSONObject().put("codec", "pcm_s16le").put("sampleRate", sampleRate).put("channels", channels)
    companion object {
        fun parse(j: JSONObject?): LanPcmFormat {
            require(j?.optString("codec", "pcm_s16le") in listOf(null, "pcm_s16le")) { "unsupported_codec" }
            return LanPcmFormat(j?.optInt("sampleRate", 48000) ?: 48000, j?.optInt("channels", 2) ?: 2)
        }
    }
}

/** Receives only the bytes actually accepted by MeloX's production output line. */
object LanPcmTap {
    @Volatile var muteLocalOutput = false
    private val listeners = CopyOnWriteArraySet<(ByteArray) -> Unit>()
    fun subscribe(listener: (ByteArray) -> Unit) { listeners += listener }
    fun unsubscribe(listener: (ByteArray) -> Unit) { listeners -= listener }
    fun accepted(bytes: ByteArray, offset: Int, count: Int) {
        if (listeners.isEmpty()) return
        val copy = bytes.copyOfRange(offset, offset + count)
        listeners.forEach { it(copy) }
    }
}

class LanCastSender(private val address: InetAddress, private val port: Int, private val onStop: () -> Unit = {}) : AutoCloseable {
    private val packets = ArrayBlockingQueue<ByteArray>(64)
    @Volatile private var running = false
    val isRunning get() = running
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    private val sentBytes = java.util.concurrent.atomic.AtomicLong()
    private var socket: Socket? = null
    private val tap: (ByteArray) -> Unit = { bytes ->
        if (running) {
            val packetBytes = 3528 // 20ms, 44.1kHz stereo signed16LE
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + packetBytes, bytes.size)
                val part = bytes.copyOfRange(offset, end)
                if (!packets.offer(part)) { packets.poll(); packets.offer(part) }
                offset = end
            }
        }
    }
    fun start() {
        require(isLanAddress(address))
        val s = Socket(); socket = s
        try { s.connect(InetSocketAddress(address, port), 5000); s.tcpNoDelay = true; running = true }
        catch (error: Exception) { close(); throw error }
        LanPcmTap.subscribe(tap)
        Thread({
            try {
                val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
                while (running) {
                    val bytes = packets.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    out.writeInt(bytes.size); out.write(bytes); out.flush()
                    if (sentBytes.getAndAdd(bytes.size.toLong()) == 0L) melox.platform.logInfo("MeloXCast", "First PCM sent: bytes=${bytes.size}")
                }
            } catch (_: Exception) {} finally { close(); onStop() }
        }, "MeloX-LAN-PCM-send").apply { isDaemon = true; start() }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running = false; LanPcmTap.unsubscribe(tap); runCatching { socket?.close() }; packets.clear()
        melox.platform.logInfo("MeloXCast", "Sender stopped: sent_bytes=${sentBytes.get()}")
    }
}

class LanCastReceiver(private val bind: InetAddress, private val peer: InetAddress, val format: LanPcmFormat,
    private val openLine: (AudioFormat) -> SourceDataLine = { f ->
        (AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, f)) as SourceDataLine).apply {
            open(f, (f.sampleRate * f.frameSize * .2f).toInt())
        }
    }, private val onError: (String) -> Unit = {}, private val onStopped: () -> Unit = {}) : AutoCloseable {
    private val closed = java.util.concurrent.atomic.AtomicBoolean()
    private val receivedBytes = java.util.concurrent.atomic.AtomicLong()
    private val server = ServerSocket(0, 1, bind).apply { soTimeout = 15000 }
    private val packets = ArrayBlockingQueue<ByteArray>(12)
    @Volatile private var running = false
    private var socket: Socket? = null
    private var line: SourceDataLine? = null
    val port get() = server.localPort
    fun start() {
        val f = AudioFormat(format.sampleRate.toFloat(), 16, format.channels, true, false)
        // Device allocation errors are reported before cast.start succeeds.
        line = try { openLine(f) } catch (error: Exception) { close(); throw error }
        running = true
        Thread({
            try {
                val deadline = System.nanoTime() + 15_000_000_000L
                var accepted: Socket? = null
                while (running && System.nanoTime() < deadline) {
                    val candidate = server.accept()
                    if (candidate.inetAddress == peer) { accepted = candidate; break } else candidate.close()
                }
                val s = accepted ?: throw IOException("cast_connect_timeout")
                socket = s; server.close(); s.soTimeout = 0
                val input = DataInputStream(BufferedInputStream(s.getInputStream()))
                while (running) {
                    val count = input.readInt()
                    require(count in 1..65536 && count % format.frameBytes == 0) { "invalid_pcm_packet" }
                    val bytes = ByteArray(count); input.readFully(bytes)
                    if (receivedBytes.getAndAdd(count.toLong()) == 0L) melox.platform.logInfo("MeloXCast", "First PCM received: bytes=$count")
                    if (!packets.offer(bytes)) { packets.poll(); packets.offer(bytes) }
                }
            } catch (error: Exception) { if (running && error !is EOFException) onError(error.message ?: "cast_failed") }
            finally { close() }
        }, "MeloX-LAN-PCM-read").apply { isDaemon = true; start() }
        Thread({
            try {
                val output = checkNotNull(line); output.start()
                while (running) {
                    val bytes = packets.poll(200, TimeUnit.MILLISECONDS) ?: continue
                    val gain = melox.player.AudioPlayer.volumeLevel.value
                    if (gain < .999f) for (i in bytes.indices step 2) {
                        val sample = ((bytes[i].toInt() and 255) or (bytes[i + 1].toInt() shl 8)).toShort().toInt()
                        val scaled = (sample * gain).toInt().coerceIn(-32768, 32767)
                        bytes[i] = scaled.toByte(); bytes[i + 1] = (scaled shr 8).toByte()
                    }
                    var offset = 0
                    while (running && offset < bytes.size) {
                        val count = output.write(bytes, offset, bytes.size - offset)
                        if (count <= 0) throw IOException("cast_output_stopped")
                        offset += count
                    }
                }
            } catch (error: Exception) { if (running) onError(error.message ?: "cast_output_failed") }
            finally { close() }
        }, "MeloX-LAN-PCM-play").apply { isDaemon = true; start() }
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        running = false; runCatching { server.close() }; runCatching { socket?.close() }
        runCatching { line?.stop() }; runCatching { line?.close() }; packets.clear()
        onStopped()
        melox.platform.logInfo("MeloXCast", "Receiver stopped: received_bytes=${receivedBytes.get()}")
    }
}
