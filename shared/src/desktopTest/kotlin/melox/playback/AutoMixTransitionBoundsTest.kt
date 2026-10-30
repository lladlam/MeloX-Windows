package melox.playback

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import melox.player.MeloXPcmTempo

class AutoMixTransitionBoundsTest {
    private fun analysis(bpm: Double, end: Long): MeloXAutoMixTrackAnalysis {
        val beats = generateSequence(0L) { it + 500 }.takeWhile { it <= end }.toList().toLongArray()
        val frames = (0L..end step 100).map { time ->
            MeloXAutoMixFrame(time, 0.6f, 0.3f, 0.5f, 0.2f, 0.1f, 0.1f)
        }
        return MeloXAutoMixTrackAnalysis(bpm, .95, 0, end, beats,
            beats.filterIndexed { index, _ -> index % 4 == 0 }.toLongArray(), longArrayOf(0, end), frames)
    }

    @Test fun smartTransitionNeverConsumesPastEitherAudibleBoundary() {
        val outgoing = analysis(120.0, 60_000)
        // Its only detected beat is too late to fit a full overlap. A valid
        // fallback must start at the first audible frame instead of selecting
        // that beat and running the decoder out before handoff.
        val incoming = analysis(116.0, 6_000).copy(beatTimesMs = longArrayOf(1_500))
        val settings = MeloXAutoMixSettings(transitionBars = 4, tailCutBars = 0, maxTempoAdjustment = .08)
        val plan = assertNotNull(MeloXAutoMixTransitionScorer.plan(settings, outgoing, incoming))
        val outgoingEnd = plan.outgoingStartMs + MeloXPcmTempo.contentMs(plan.durationMs.toDouble(), plan.outgoingStartRate, plan.outgoingEndRate, plan.durationMs)
        val incomingEnd = plan.incomingStartMs + MeloXPcmTempo.contentMs(plan.durationMs.toDouble(), plan.incomingStartRate, plan.incomingEndRate, plan.durationMs)
        assertTrue(plan.incomingStartMs == incoming.firstAudibleMs, "late incoming beat selected: $plan")
        assertTrue(outgoingEnd <= outgoing.lastAudibleMs, "outgoing ends at $outgoingEnd")
        assertTrue(incomingEnd <= incoming.lastAudibleMs, "incoming ends at $incomingEnd")
    }

    @Test fun tempoIntegralAgreesWithNumericalRateEnvelope() {
        val duration = 12_000L
        for ((start, end) in listOf(1f to .92f, 1.08f to 1f)) {
            val sum = (0 until 10_000).sumOf { index -> MeloXAutoMixEnvelope.rate(start, end, (index + .5) / 10_000).toDouble() } * duration / 10_000
            val actual = MeloXPcmTempo.contentMs(duration.toDouble(), start, end, duration)
            assertTrue(abs(actual - sum) < .01, "$start -> $end: $actual vs $sum")
        }
    }

    @Test fun detectedPhraseBoundaryBeatsArbitraryArrayIndexPhase() {
        val outgoing = analysis(120.0, 60_000).copy(phraseBoundariesMs = longArrayOf(0, 51_500, 60_000))
        val incoming = analysis(120.0, 20_000)
        val settings = MeloXAutoMixSettings(transitionBars = 4, tailCutBars = 0, tempoMatching = false)
        val plan = assertNotNull(MeloXAutoMixTransitionScorer.plan(settings, outgoing, incoming))
        assertTrue(abs(plan.outgoingStartMs - 51_500) <= 250, "phrase boundary ignored: $plan")
    }
}
