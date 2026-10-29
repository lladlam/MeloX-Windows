package melox.autosync

/** Android's Viterbi alignment contract, matching the AutoSync Python reference. */
internal object CtcForcedAligner {
    const val NEG_INF = -1e30

    data class Span(val token: Int, val startFrame: Int, val endFrame: Int, val score: Double) {
        val numFrames: Int get() = endFrame - startFrame + 1
    }
    data class Alignment(
        val spans: List<Span?>,
        val totalLogProb: Double,
        val feasible: Boolean,
        val requiredFrames: Int,
        val reasons: List<String>,
        val meanScore: Double,
    )

    fun minimumFrames(targets: List<Int>): Int = targets.size + targets.zipWithNext().count { it.first == it.second }

    fun align(logProbs: Array<DoubleArray>, targets: List<Int>, blank: Int = 0): Alignment {
        val t = logProbs.size
        val c = logProbs.firstOrNull()?.size ?: 0
        val n = targets.size
        val required = minimumFrames(targets)
        if (n == 0) return Alignment(emptyList(), 0.0, true, required, emptyList(), 0.0)
        if (c > 0) {
            require(blank in 0 until c && targets.all { it in 0 until c }) { "CTC token out of range" }
            require(logProbs.all { it.size == c }) { "Ragged CTC logits" }
        }
        if (t == 0) return Alignment(List(n) { null }, NEG_INF, false, required, listOf("no frames (T=0)"), 0.0)
        if (t < required) return Alignment(List(n) { null }, NEG_INF, false, required,
            listOf("too few frames: T=$t < required=$required for $n tokens"), 0.0)

        val states = 2 * n + 1
        val ext = IntArray(states) { blank }
        targets.forEachIndexed { i, token -> ext[2 * i + 1] = token }
        var dp = DoubleArray(states) { NEG_INF }
        var next = DoubleArray(states)
        dp[0] = logProbs[0][blank]
        dp[1] = logProbs[0][ext[1]]
        val back = Array(t - 1) { ByteArray(states) }
        for (frame in 1 until t) {
            for (state in 0 until states) {
                var best = dp[state]
                var step = 0
                if (state > 0 && dp[state - 1] > best) { best = dp[state - 1]; step = 1 }
                if (state > 1 && ext[state] != ext[state - 2] && dp[state - 2] > best) {
                    best = dp[state - 2]; step = 2
                }
                back[frame - 1][state] = step.toByte()
                next[state] = best + logProbs[frame][ext[state]]
            }
            val recycled = dp
            dp = next
            next = recycled
        }
        val total = dp.max()
        var state = if (dp[states - 1] >= dp[states - 2]) states - 1 else states - 2
        val path = IntArray(t)
        for (frame in t - 1 downTo 0) {
            path[frame] = state
            if (frame > 0) state -= back[frame - 1][state].toInt()
        }
        val missing = mutableListOf<Int>()
        val spans = targets.mapIndexed { i, token ->
            val expected = 2 * i + 1
            val first = path.indexOf(expected)
            if (first < 0) { missing.add(i); null } else {
                val last = path.lastIndexOf(expected)
                var sum = 0.0
                for (frame in first..last) sum += logProbs[frame][token]
                Span(token, first, last, sum / (last - first + 1))
            }
        }
        val reasons = mutableListOf<String>()
        if (!total.isFinite() || total <= NEG_INF / 2) reasons += "all paths have -inf probability"
        if (missing.isNotEmpty()) reasons += "${missing.size} token(s) received zero frames: ${missing.take(8)}"
        var previous = -1
        if (spans.filterNotNull().any { span ->
                val invalid = span.startFrame <= previous || span.endFrame < span.startFrame
                previous = span.endFrame
                invalid
            }) reasons += "spans are not monotonic"
        val valid = spans.filterNotNull()
        if (valid.isEmpty()) reasons += "no usable spans"
        else if (valid.size >= 4 && valid.maxOf { it.numFrames }.toDouble() / t > 0.5) {
            reasons += "collapsed: one token spans ${(valid.maxOf { it.numFrames }.toDouble() / t * 100).toInt()}% of the audio"
        }
        return Alignment(spans, total, reasons.isEmpty(), required, reasons,
            if (valid.isEmpty()) 0.0 else valid.map { it.score }.average())
    }

    fun logSoftmax(logits: FloatArray): DoubleArray {
        val max = logits.max()
        val sum = logits.sumOf { kotlin.math.exp((it - max).toDouble()) }
        val logSum = kotlin.math.ln(sum)
        return DoubleArray(logits.size) { logits[it] - max - logSum }
    }
}
