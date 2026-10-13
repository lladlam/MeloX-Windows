package melox.playback

import kotlin.math.*
import melox.player.MeloXPcmMix

/** Real stereo s16le output DSP. One instance per output/seek lifecycle, after deck mixing.
 * Android's device-specific audiofx bands are mapped to ten octave-spaced RBJ peaking filters.
 * Preamp is an actual input gain; output is saturated once after filtering and software volume.
 */
class MeloXEqualizerController(private val sampleRate: Int = 44_100,
    private val settingsProvider: () -> MeloXEqualizerSettings = { MeloXEqualizerSettings.current.value }) {
    private var settings: MeloXEqualizerSettings? = null
    private var bank: Bank? = null
    private var previous: Bank? = null
    private var morphFrames = 0
    private val morphLength = (sampleRate / 50).coerceAtLeast(1) // 20ms preset/enable transition
    var clippedSamples = 0L
        private set

    init { require(sampleRate > 32_000) }

    fun process(bytes: ByteArray, count: Int, volume: Float = 1f) {
        require(count >= 0 && count <= bytes.size && count % 4 == 0)
        val requested = settingsProvider()
        require(requested.gainsDb.size == TARGET_FREQUENCIES_HZ.size)
        if (requested != settings) {
            previous = bank
            bank = Bank(requested, sampleRate)
            morphFrames = if (previous == null) 0 else morphLength
            settings = requested
        }
        val current = checkNotNull(bank)
        if (current.bypass && morphFrames == 0) { MeloXPcmMix.scale(bytes, count, volume); return }
        val gain = if (volume.isFinite()) volume.coerceIn(0f, 1f).toDouble() else 0.0
        for (frame in 0 until count / 4) {
            val fraction = if (morphFrames > 0) 1.0 - morphFrames.toDouble() / morphLength else 1.0
            for (channel in 0..1) {
                val offset = frame * 4 + channel * 2
                val input = MeloXPcmMix.sample(bytes, offset).toDouble()
                val next = current.filter(input, channel)
                val output = (if (morphFrames > 0) checkNotNull(previous).filter(input, channel) * (1 - fraction) + next * fraction else next) * gain
                if (output > 32767 || output < -32768) clippedSamples++
                val value = output.roundToInt().coerceIn(-32768, 32767)
                bytes[offset] = value.toByte(); bytes[offset + 1] = (value shr 8).toByte()
            }
            if (morphFrames > 0 && --morphFrames == 0) previous = null
        }
    }

    fun release() { settings = null; bank = null; previous = null; morphFrames = 0 }

    private class Bank(settings: MeloXEqualizerSettings, sampleRate: Int) {
        val bypass = !settings.enabled || settings.preampDb == 0f && settings.gainsDb.all { it == 0f }
        private val gain = if (settings.enabled) 10.0.pow(settings.preampDb.toDouble().coerceIn(-12.0, 12.0) / 20) else 1.0
        private val filters = if (!settings.enabled) emptyList() else settings.gainsDb.mapIndexedNotNull { index, db ->
            if (db == 0f) null else Peak(TARGET_FREQUENCIES_HZ[index].toDouble(), db.toDouble().coerceIn(-12.0, 12.0), sampleRate)
        }
        fun filter(input: Double, channel: Int): Double {
            if (bypass) return input
            var output = input * gain
            filters.forEach { output = it.filter(output, channel) }
            return output
        }
    }

    private class Peak(hz: Double, db: Double, rate: Int) {
        private val a = 10.0.pow(db / 40)
        private val omega = 2 * PI * hz / rate
        private val alpha = sin(omega) / (2 * sqrt(2.0))
        private val divisor = 1 + alpha / a
        private val b0 = (1 + alpha * a) / divisor
        private val b1 = -2 * cos(omega) / divisor
        private val b2 = (1 - alpha * a) / divisor
        private val a1 = b1
        private val a2 = (1 - alpha / a) / divisor
        private val z1 = DoubleArray(2)
        private val z2 = DoubleArray(2)
        fun filter(input: Double, channel: Int): Double {
            val output = input * b0 + z1[channel]
            z1[channel] = input * b1 - output * a1 + z2[channel]
            z2[channel] = input * b2 - output * a2
            return output
        }
    }

    companion object {
        val TARGET_FREQUENCIES_HZ = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f)

        val PRESETS = linkedMapOf(
            "Flat" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Bass" to floatArrayOf(6f, 5f, 4f, 2f, 1f, 0f, -1f, -1f, 0f, 0f),
            "Vocal" to floatArrayOf(-2f, -1f, 0f, 1f, 3f, 4f, 4f, 2f, 0f, -1f),
            "Treble" to floatArrayOf(-2f, -2f, -1f, 0f, 0f, 1f, 2f, 4f, 5f, 6f),
            "Electronic" to floatArrayOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 5f),
            "Rock" to floatArrayOf(4f, 3f, 2f, 0f, -1f, 0f, 2f, 3f, 4f, 4f),
            "Classical" to floatArrayOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, 1f, 2f, 3f),
            "Custom" to floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        )

        internal fun interpolatedGain(centerHz: Float, gains: List<Float>): Float {
            if (centerHz <= TARGET_FREQUENCIES_HZ.first()) return gains.first()
            if (centerHz >= TARGET_FREQUENCIES_HZ.last()) return gains.last()
            val upper = TARGET_FREQUENCIES_HZ.indexOfFirst { it >= centerHz }.coerceAtLeast(1)
            val lower = upper - 1
            val lowerLog = ln(TARGET_FREQUENCIES_HZ[lower])
            val upperLog = ln(TARGET_FREQUENCIES_HZ[upper])
            val fraction = ((ln(centerHz) - lowerLog) / (upperLog - lowerLog)).coerceIn(0f, 1f)
            return gains[lower] + (gains[upper] - gains[lower]) * fraction
        }
    }
}
