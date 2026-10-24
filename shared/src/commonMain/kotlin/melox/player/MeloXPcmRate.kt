package melox.player

/** Small bounded drift correction on decoded stereo S16LE; no decoder restarts. */
internal class MeloXPcmRate {
    private var phase = 0.0
    fun process(input: ByteArray, count: Int, rate: Float): ByteArray {
        val frames = count / 4
        val speed = rate.coerceIn(.97f, 1.03f).toDouble()
        if (speed == 1.0 && phase == 0.0) return if (count == input.size) input else input.copyOf(count)
        val out = ByteArray(((frames / speed).toInt() + 3) * 4)
        var written = 0
        while (phase < frames) {
            val index = phase.toInt(); val next = (index + 1).coerceAtMost(frames - 1); val fraction = phase - index
            for (channel in 0..1) {
                fun sample(frame: Int): Int {
                    val offset = frame * 4 + channel * 2
                    return ((input[offset].toInt() and 255) or (input[offset + 1].toInt() shl 8)).toShort().toInt()
                }
                val value = (sample(index) * (1 - fraction) + sample(next) * fraction).toInt().coerceIn(-32768, 32767)
                out[written + channel * 2] = value.toByte(); out[written + channel * 2 + 1] = (value shr 8).toByte()
            }
            written += 4; phase += speed
        }
        phase -= frames
        return out.copyOf(written)
    }
}
