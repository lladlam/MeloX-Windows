package melox.playback

import kotlin.math.*

/** Phrase/energy/spectral scoring from pinned Android 3f877ea's analyzer. */
object MeloXAutoMixTransitionScorer {
    fun plan(settings: MeloXAutoMixSettings, outgoing: MeloXAutoMixTrackAnalysis, incoming: MeloXAutoMixTrackAnalysis): MeloXAutoMixPlan? {
        if (min(outgoing.confidence, incoming.confidence) < settings.minimumConfidence || outgoing.bpm <= 0 || incoming.bpm <= 0) return null
        val aligned = listOf(incoming.bpm / 2, incoming.bpm, incoming.bpm * 2).minBy { abs(it - outgoing.bpm) }
        val canMatch = settings.tempoMatching && abs(outgoing.bpm / aligned - 1) <= settings.maxTempoAdjustment
        val outgoingRate = if (canMatch) (aligned / outgoing.bpm).coerceIn(1 - settings.maxTempoAdjustment, 1 + settings.maxTempoAdjustment).toFloat() else 1f
        val incomingRate = if (canMatch) (outgoing.bpm / aligned).coerceIn(1 - settings.maxTempoAdjustment, 1 + settings.maxTempoAdjustment).toFloat() else 1f
        val outgoingBeat = 60_000 / outgoing.bpm
        val incomingBeat = 60_000 / aligned
        val requested = (settings.transitionBars * 4 * (outgoingBeat + incomingBeat) / 2).toLong()
        val tailCut = (settings.tailCutBars * 4 * outgoingBeat).toLong()
        val earliest = outgoing.firstAudibleMs + (outgoing.lastAudibleMs - outgoing.firstAudibleMs).coerceAtLeast(1) * 75 / 100
        val latestIncoming = incoming.firstAudibleMs + (incoming.lastAudibleMs - incoming.firstAudibleMs).coerceAtLeast(1) * 25 / 100
        val desiredEnd = (outgoing.lastAudibleMs - tailCut).coerceAtLeast(earliest + requested).coerceAtMost(outgoing.lastAudibleMs)
        val maximum = min(32_000L, min(min((desiredEnd - earliest - 500).coerceAtLeast(0),
            (incoming.lastAudibleMs - incoming.firstAudibleMs - 500).coerceAtLeast(0)), (45_000 / max(incomingRate, 1f)).toLong()))
        val duration = requested.coerceAtLeast(3000).coerceAtMost(maximum)
        if (duration < MeloXAutoMixPlanner.MIN_DURATION_MS) return null
        val targetStart = desiredEnd - duration
        val outgoingBeats = outgoing.beatTimesMs.withIndex().filter { it.value in earliest..(targetStart + 80) }.takeLast(48)
        val incomingBeats = incoming.beatTimesMs.withIndex().filter { it.value in incoming.firstAudibleMs..(latestIncoming + 80) }.take(48)
            .ifEmpty { if (incoming.firstAudibleMs <= latestIncoming) listOf(IndexedValue(0, incoming.firstAudibleMs)) else emptyList() }
        if (outgoingBeats.isEmpty() || incomingBeats.isEmpty()) return null
        var best = Double.POSITIVE_INFINITY; var outgoingStart = 0L; var incomingStart = 0L
        outgoingBeats.forEach { out -> incomingBeats.forEach { into ->
            val timing = (abs(out.value - targetStart) / max(duration * 1.75, 12_000.0)).coerceIn(0.0, 1.0)
            val phrase = (phrasePenalty(out.index) + phrasePenalty(into.index)) / 2
            val boundary = 1 - (boundaryStrength(outgoing, out.value) + boundaryStrength(incoming, into.value)) / 2
            val contour = (contourPenalty(outgoing, out.value, false) + contourPenalty(incoming, into.value, true)) / 2
            val climax = (climaxPenalty(outgoing, out.value) + climaxPenalty(incoming, into.value)) / 2
            val quietIncoming = if (settings.skipQuietOpening) ((.20 - meanEnergy(incoming, into.value, 1500)) / .20).coerceIn(0.0, 1.0) else 0.0
            val quietOutgoing = ((.12 - meanEnergy(outgoing, out.value, 1500)) / .12).coerceIn(0.0, 1.0)
            val overlap = overlapPenalty(out.value, into.value, duration, outgoingRate, incomingRate, outgoing, incoming)
            val tempo = (abs(incomingRate - 1) / .10).coerceIn(0.0, 1.0)
            val score = timing * .25 + phrase * .08 + boundary * .19 + contour * .13 + quietIncoming * .09 +
                quietOutgoing * .05 + overlap * .17 + tempo * .04 + climax * .18
            if (score < best) { best = score; outgoingStart = out.value; incomingStart = into.value }
        } }
        return MeloXAutoMixPlan(duration, if (settings.skipQuietOpening) max(incomingStart, incoming.firstAudibleMs) else incomingStart,
            outgoingStartMs = outgoingStart, outgoingEndRate = outgoingRate, incomingStartRate = incomingRate, usedSmartAnalysis = true)
    }
    private fun overlapPenalty(outStart: Long, inStart: Long, duration: Long, outRate: Float, inRate: Float,
        outgoing: MeloXAutoMixTrackAnalysis, incoming: MeloXAutoMixTrackAnalysis): Double {
        val outgoingReference = meanEnergy(outgoing, outStart, 1000)
        val incomingEnd = inStart + contentOffset(duration, inRate, 1f, 1.0)
        val incomingReference = meanEnergy(incoming, max(incomingEnd - 1000, inStart), 1000)
        var continuity = 0.0; var bass = 0.0; var mids = 0.0; var spectral = 0.0
        for (sample in 0..16) {
            val p = sample / 16.0
            val out = outgoing.frameAt(outStart + contentOffset(duration, 1f, outRate, p)) ?: continue
            val into = incoming.frameAt(inStart + contentOffset(duration, inRate, 1f, p)) ?: continue
            val gain = MeloXAutoMixEnvelope.gains(p, MeloXAutoMixFadeCurve.EqualPower)
            continuity += abs(out.energy * gain.outgoing + into.energy * gain.incoming - (outgoingReference + (incomingReference - outgoingReference) * p)).coerceAtMost(1.0)
            bass += min(out.lowRatio * gain.outgoing, into.lowRatio * gain.incoming)
            mids += min(max(out.midRatio - .3f, 0f) * out.energy * gain.outgoing, max(into.midRatio - .3f, 0f) * into.energy * gain.incoming)
            spectral += (abs(out.lowRatio - into.lowRatio) + abs(out.midRatio - into.midRatio) + abs(out.highRatio - into.highRatio)) * min(gain.outgoing, gain.incoming)
        }
        return (continuity / 17 * .43 + bass / 17 * .24 + mids / 17 * .19 + spectral / 17 * .14).coerceIn(0.0, 1.0)
    }
    private fun contentOffset(duration: Long, start: Float, end: Float, p: Double) = (duration * p * (start + MeloXAutoMixEnvelope.rate(start, end, p)) / 2).toLong()
    private fun meanEnergy(analysis: MeloXAutoMixTrackAnalysis, start: Long, duration: Long): Double {
        val frames = analysis.frames.asSequence().filter { it.timeMs in start..(start + duration) }.map { it.energy.toDouble() }.toList()
        return if (frames.isEmpty()) analysis.frameAt(start)?.energy?.toDouble() ?: 0.0 else frames.average()
    }
    private fun phrasePenalty(index: Int) = when (index.mod(16)) { 0 -> 0.0; 4, 8, 12 -> .35; else -> .85 }
    private fun boundaryStrength(analysis: MeloXAutoMixTrackAnalysis, time: Long): Double = analysis.frameAt(time)?.let { (it.novelty * .58f + it.onset * .42f).coerceIn(0f, 1f).toDouble() } ?: 0.0
    private fun climaxPenalty(analysis: MeloXAutoMixTrackAnalysis, time: Long): Double = analysis.frameAt(time)?.let {
        ((it.energy - .72f) / .28f).coerceIn(0f, 1f) * .55 + ((it.novelty - .68f) / .32f).coerceIn(0f, 1f) * .25 + ((it.onset - .68f) / .32f).coerceIn(0f, 1f) * .20
    } ?: 0.0
    private fun contourPenalty(analysis: MeloXAutoMixTrackAnalysis, time: Long, rise: Boolean): Double {
        val slope = meanEnergy(analysis, time + 300, 1200) - meanEnergy(analysis, (time - 1500).coerceAtLeast(0), 1200)
        return ((if (rise) -slope else slope) * 2.5 + .35).coerceIn(0.0, 1.0)
    }
}
