package melox.validation

import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import javax.sound.sampled.SourceDataLine
import org.json.JSONObject

/** Validation-only, explicitly labelled software output. It consumes actual written PCM in real time
 * and saves it for spectral verification. This is not a speaker or an audible acceptance result.
 */
internal class MeloXPcmCaptureOutput(private val directory: File) {
    private val outputs = CopyOnWriteArrayList<Endpoint>()
    fun open(): SourceDataLine = Endpoint(File(directory, "pcm-output-${outputs.size}.s16le")).also(outputs::add).line
    fun evidence() = outputs.map { it.evidence() }
    private class Endpoint(private val file: File) {
        private val stream = file.outputStream()
        private var written = 0L; private var played = 0L; private var running = false; private var closed = false
        private var startedAt = 0L; private var startFrame = 0L
        @Synchronized private fun update(): Long {
            if (running) played = minOf(written, startFrame + (System.nanoTime() - startedAt) * 44100 / 1_000_000_000)
            return played
        }
        private fun write(bytes: ByteArray, offset: Int, count: Int): Int {
            while (true) {
                synchronized(this) {
                    check(!closed) { "Capture PCM output released" }
                    val available = 8820 - (written - update())
                    if (running && available > 0) {
                        val accepted = minOf(count, (available * 4).toInt())
                        stream.write(bytes, offset, accepted); written += accepted / 4
                        return accepted
                    }
                }
                Thread.sleep(2)
            }
        }
        @Synchronized fun evidence() = JSONObject().put("file", file.name).put("written_frames", written).put("consumed_frames", update())
            .put("closed", closed).put("hardware_output", false)
        val line: SourceDataLine = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> write(args!![0] as ByteArray, args[1] as Int, args[2] as Int)
                "getLongFramePosition" -> update()
                "getFramePosition" -> update().toInt()
                "start" -> synchronized(this) { if (!running) { startFrame = played; startedAt = System.nanoTime(); running = true }; null }
                "stop" -> synchronized(this) { update(); running = false; null }
                "close" -> synchronized(this) { if (!closed) { update(); running = false; closed = true; stream.close() }; null }
                "isRunning", "isActive" -> synchronized(this) { running }
                "isOpen" -> synchronized(this) { !closed }
                "getControl" -> throw IllegalArgumentException("Software validation output has no MASTER_GAIN")
                "toString" -> "ValidationPcmOutput"
                else -> error("Unsupported capture output ${method.name}")
            }
        } as SourceDataLine
    }
}
