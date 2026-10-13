package melox.player

import melox.platform.logInfo
import melox.platform.logWarn
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

/** Real Java Sound outputs. Keep decoded/EQ PCM fixed at stereo 44100Hz S16LE. */
internal object MeloXPcmOutput {
    val format = AudioFormat(44100f, 16, 2, true, false)
    private const val BufferBytes = 44100 * 4

    fun open(): SourceDataLine {
        val candidates = sequence {
            yield("system default" to { AudioSystem.getSourceDataLine(format) })
            val info = DataLine.Info(SourceDataLine::class.java, format)
            AudioSystem.getMixerInfo().forEach { mixerInfo ->
                val mixer = AudioSystem.getMixer(mixerInfo)
                if (mixer.isLineSupported(info)) yield(mixerInfo.name to { mixer.getLine(info) as SourceDataLine })
            }
        }
        return openCandidates(candidates.asIterable())
    }

    internal fun openCandidates(candidates: Iterable<Pair<String, () -> SourceDataLine>>): SourceDataLine {
        val failures = mutableListOf<Exception>()
        candidates.forEach { (name, create) ->
            var line: SourceDataLine? = null
            try {
                line = create()
                line.open(format, BufferBytes)
                logInfo("AudioPlayer", "PCM output opened: $name; 44100Hz stereo S16LE; buffer=${line.bufferSize}")
                return line
            } catch (error: Exception) {
                runCatching { line?.close() }
                failures += error
                logWarn("AudioPlayer", "PCM output rejected: $name; ${error.javaClass.simpleName}: ${error.message}")
            }
        }
        throw java.io.IOException("无法打开声音输出设备（44100 Hz、双声道）。${failures.firstOrNull()?.message.orEmpty()}", failures.firstOrNull()).apply {
            failures.drop(1).forEach(::addSuppressed)
        }
    }
}
