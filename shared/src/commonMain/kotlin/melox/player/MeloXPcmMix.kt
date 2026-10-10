package melox.player

import melox.playback.MeloXAutoMixEnvelope
import melox.playback.MeloXAutoMixFadeCurve
import java.util.Locale
import kotlin.math.*

/** Signed stereo s16le mixer. Accumulate in floating point, saturate once, never wrap shorts. */
internal class MeloXPcmMix(private val bassSwap: Boolean = true) {
    private val outgoing = BassShelf()
    private val incoming = BassShelf()
    private val equalizer = melox.playback.MeloXAutoMixEqualizerEnvelope()
    var clippedSamples = 0L
        private set

    fun mix(a: ByteArray, b: ByteArray, count: Int, frameOffset: Long, totalFrames: Long, curve: MeloXAutoMixFadeCurve) {
        require(count % 4 == 0 && count <= min(a.size, b.size) && totalFrames > 0)
        val blockProgress = (frameOffset.toDouble() / totalFrames).coerceIn(0.0, 1.0)
        if (bassSwap) {
            equalizer.apply(blockProgress)
            outgoing.cut(equalizer.outgoingCutDb)
            incoming.cut(equalizer.incomingCutDb)
        }
        for (frame in 0 until count / 4) {
            val p = ((frameOffset + frame).toDouble() / totalFrames).coerceIn(0.0, 1.0)
            val gain = MeloXAutoMixEnvelope.gains(p, curve)
            repeat(2) { channel ->
                val index = frame * 4 + channel * 2
                val av = sample(a, index).toDouble()
                val bv = sample(b, index).toDouble()
                val sum = (if (bassSwap) outgoing.filter(av, channel) else av) * gain.outgoing +
                    (if (bassSwap) incoming.filter(bv, channel) else bv) * gain.incoming
                if (sum > Short.MAX_VALUE || sum < Short.MIN_VALUE) clippedSamples++
                val value = sum.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                a[index] = value.toByte(); a[index + 1] = (value shr 8).toByte()
            }
        }
    }
    /** RBJ low shelf, 300 Hz: software platform equivalent of Android's <=300 Hz deck bands. */
    private class BassShelf {
        private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
        private val z1 = DoubleArray(2); private val z2 = DoubleArray(2)
        private var lastCut = Double.NaN
        fun cut(db: Double) {
            if (db == lastCut) return
            lastCut = db
            val a = 10.0.pow(db / 40)
            val w = 2 * PI * 300 / AudioPlayer.PCM_SAMPLE_RATE
            val c = cos(w); val alpha = sin(w) * sqrt(2.0) / 2
            val beta = 2 * sqrt(a) * alpha
            val divisor = (a + 1) + (a - 1) * c + beta
            b0 = a * ((a + 1) - (a - 1) * c + beta) / divisor
            b1 = 2 * a * ((a - 1) - (a + 1) * c) / divisor
            b2 = a * ((a + 1) - (a - 1) * c - beta) / divisor
            a1 = -2 * ((a - 1) + (a + 1) * c) / divisor
            a2 = ((a + 1) + (a - 1) * c - beta) / divisor
        }
        fun filter(value: Double, channel: Int): Double {
            val output = value * b0 + z1[channel]
            z1[channel] = value * b1 - output * a1 + z2[channel]
            z2[channel] = value * b2 - output * a2
            return output
        }
    }
    companion object {
        fun scale(bytes: ByteArray, count: Int, volume: Float) {
            if (volume == 1f) return
            val gain = volume.coerceIn(0f, 1f)
            for (index in 0 until count step 2) {
                val value = (sample(bytes, index) * gain).roundToInt().coerceIn(-32768, 32767)
                bytes[index] = value.toByte(); bytes[index + 1] = (value shr 8).toByte()
            }
        }
        fun sample(bytes: ByteArray, index: Int): Int = ((bytes[index].toInt() and 255) or (bytes[index + 1].toInt() shl 8)).toShort().toInt()
        private fun smooth(value: Double): Double = value.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    }
}

/** Pitch-preserving atempo command ramp; command timestamps are input-content time. */
internal object MeloXPcmTempo {
    fun contentMs(outputMs: Double, start: Float, end: Float, durationMs: Long): Double {
        if (durationMs <= 0) return outputMs
        val p = (outputMs / durationMs).coerceIn(0.0, 1.0)
        // Integral of source smoothstep rate: 3p²-2p³ -> p³-.5p⁴.
        val during = durationMs * (start * p + (end - start) * (p.pow(3) - .5 * p.pow(4)))
        return during + max(outputMs - durationMs, 0.0) * end
    }
    fun filter(start: Float, end: Float, durationMs: Long, leadMs: Long = 0): String? {
        require(start in .5f..2f && end in .5f..2f && durationMs > 0 && leadMs >= 0)
        if (start == 1f && end == 1f) return null
        val steps = (durationMs / 100).coerceAtLeast(1).toInt()
        val commands = (0..steps).joinToString(";") { index ->
            val p = index.toDouble() / steps
            val at = (leadMs + contentMs(durationMs * p, start, end, durationMs)) / 1000
            "%.6f atempo@mix tempo %.6f".format(Locale.ROOT, at, MeloXAutoMixEnvelope.rate(start, end, p))
        }
        val initial = if (leadMs > 0) 1f else start
        return "asetpts=PTS-STARTPTS,asendcmd=c='$commands',atempo@mix=%.6f".format(Locale.ROOT, initial)
    }
}
