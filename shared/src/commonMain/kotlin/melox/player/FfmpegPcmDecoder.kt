package melox.player

import java.io.Closeable
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/** Independent native decode session; has no UI, output device or global request ownership. */
internal class FfmpegPcmDecoder(
    uri: String,
    headers: Map<String, String> = emptyMap(),
    startMs: Long = 0,
    durationMs: Long = Long.MAX_VALUE,
    filters: String? = null,
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val stderr = StringBuilder()
    private val process = ProcessBuilder(buildList {
        addAll(FfmpegLocator.command().ifEmpty { throw IOException("bundled ffmpeg missing") })
        addAll(listOf("-hide_banner", "-loglevel", "error", "-nostdin", "-xerror", "-threads", "1"))
        if (startMs > 0) addAll(listOf("-ss", "%.3f".format(Locale.ROOT, startMs / 1000.0)))
        if (uri.startsWith("http")) {
            val combined = linkedMapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)") + headers
            require(combined.all { '\r' !in it.key && '\n' !in it.key && '\r' !in it.value && '\n' !in it.value })
            addAll(listOf("-rw_timeout", "15000000", "-headers", combined.entries.joinToString("") { "${it.key}: ${it.value}\r\n" }))
        }
        addAll(listOf("-i", uri))
        if (durationMs != Long.MAX_VALUE) addAll(listOf("-t", "%.3f".format(Locale.ROOT, durationMs / 1000.0)))
        if (filters != null) addAll(listOf("-af", filters))
        addAll(listOf("-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2", "pipe:1"))
    }).start()
    init { processes.add(process) }
    private val errorReader = thread(name = "melox-pcm-stderr", isDaemon = true) {
        runCatching { process.errorStream.bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                synchronized(stderr) { if (stderr.length < 8192) stderr.append(line.take(1024)).append('\n') }
            }
        } }
    }
    fun read(bytes: ByteArray, length: Int = bytes.size): Int {
        check(!closed.get()) { "Decoder session released" }
        require(length in 0..bytes.size && length % 4 == 0)
        val count = process.inputStream.readNBytes(bytes, 0, length)
        if (count % 4 != 0) throw IOException("Decoder emitted incomplete stereo PCM frame")
        return count
    }
    fun verifyExit() {
        val code = process.waitFor()
        errorReader.join(1000)
        if (code != 0 && !closed.get()) throw IOException(synchronized(stderr) { stderr.toString().trim() }.ifBlank { "ffmpeg exited $code" })
    }
    internal fun isAlive() = process.isAlive
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        process.destroyForcibly()
        runCatching { process.inputStream.close() }
        runCatching { process.errorStream.close() }
        runCatching { process.outputStream.close() }
        process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
        errorReader.join(1000)
        if (!process.isAlive) processes.remove(process)
    }
    companion object {
        private val processes = java.util.concurrent.ConcurrentHashMap.newKeySet<Process>()
        internal fun activeProcesses(): Int = processes.count { it.isAlive }
    }
}
