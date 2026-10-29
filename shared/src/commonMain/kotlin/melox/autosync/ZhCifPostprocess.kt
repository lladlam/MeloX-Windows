package melox.autosync

/** fa-zh's CIF timestamps, not CTC; constants match the published model contract. */
internal object ZhCifPostprocess {
    private const val TIME_RATE = 0.02
    private const val THRESHOLD = 1.0 - 1e-4
    data class TokenSpan(val startMs: Long, val endMs: Long)

    fun tokens(alphas: FloatArray, peaksIn: FloatArray, tokenCount: Int): List<TokenSpan> {
        if (tokenCount <= 0 || alphas.isEmpty() || peaksIn.isEmpty()) return emptyList()
        var peaks = peaksIn
        var fires = firePlaces(peaks)
        if (fires.size != tokenCount + 1) {
            val mass = alphas.sum().toDouble()
            if (mass <= 0.0) return emptyList()
            val scale = mass / (tokenCount + 1)
            var integral = 0.0
            peaks = FloatArray(alphas.size) { i ->
                integral += (alphas[i] / scale).toFloat()
                val value = integral.toFloat()
                if (integral >= THRESHOLD) integral -= THRESHOLD
                value
            }
            fires = firePlaces(peaks)
        }
        if (fires.isEmpty()) return emptyList()
        val starts = ArrayList<Double>()
        val ends = ArrayList<Double>()
        val silence = ArrayList<Boolean>()
        if (fires[0] > 5.0) { starts.add(0.0); ends.add(fires[0] * TIME_RATE); silence.add(true) }
        for (i in 0 until fires.size - 1) {
            starts.add(fires[i] * TIME_RATE); ends.add(fires[i + 1] * TIME_RATE); silence.add(false)
            if (fires[i + 1] - fires[i] > 12.0) {
                val split = fires[i] + 12.0
                ends[ends.lastIndex] = split * TIME_RATE
                starts.add(split * TIME_RATE); ends.add(fires[i + 1] * TIME_RATE); silence.add(true)
            }
        }
        if (starts.isEmpty()) return emptyList()
        val frames = peaks.size.toDouble()
        if (frames - fires.last() > 5.0) {
            val tail = (frames + fires.last()) * 0.5
            ends[ends.lastIndex] = tail * TIME_RATE
            starts.add(tail * TIME_RATE); ends.add(frames * TIME_RATE); silence.add(true)
        } else ends[ends.lastIndex] = frames * TIME_RATE
        return starts.indices.filterNot { silence[it] }.map {
            TokenSpan((starts[it] * 1000.0).toLong(), (ends[it] * 1000.0).toLong())
        }
    }

    private fun firePlaces(peaks: FloatArray): DoubleArray = peaks.indices
        .filter { peaks[it] >= THRESHOLD }.map { it - 1.5 }.toDoubleArray()
}
