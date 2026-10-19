package melox.playback

import melox.model.SearchSong
import kotlin.test.*

class SmartQueueBudgetTest {
    @AfterTest fun resetPreferences() { MeloXPlaybackModePreferences.reset() }

    private fun entries(count: Int) = (0 until count).map {
        QueueEntry(SearchSong(it.toLong(), "Generated $it", "Fixture", "Fixture", null), "base", it, "entry-$it")
    }
    private fun analysis(bpm: Double, confidence: Double) = MeloXAutoMixTrackAnalysis(
        bpm, confidence, 0, 10000, longArrayOf(), longArrayOf(), longArrayOf(), emptyList())

    @Test fun preferenceDefaultUnlimitedAndBoundsFollowRelease() {
        MeloXPlaybackModePreferences.reset()
        assertEquals(60, MeloXAutoMixSettings.read().smartQueueAnalysisLimit)
        for ((stored, expected) in listOf(-99 to -1, -1 to -1, 0 to 10, 9 to 10, 60 to 60, 999 to 500)) {
            MeloXPlaybackModePreferences.setAutoMixInt("automix_smart_queue_analysis_limit", stored)
            assertEquals(expected, MeloXAutoMixSettings.read().smartQueueAnalysisLimit)
        }
    }

    @Test fun futureBudgetExcludesHistoryAndFallbackDoesNotWaitForUnanalyzedTail() {
        val items = entries(8); val builder = MeloXSmartQueueBuilder()
        builder.begin(items, 2)
        val snapshot = builder.snapshot()
        assertEquals(items.drop(3), snapshot.futureItems)
        assertEquals(items[2].entryId, builder.tailEntryId())
        builder.setAnalysisTargets(snapshot.generation, listOf(items[2], items[3], items[4]))
        builder.record(snapshot.generation, items[2], analysis(90.0, .8))
        builder.record(snapshot.generation, items[0], analysis(90.0, 1.0)) // History never eligible.
        builder.record(snapshot.generation, items[7], analysis(90.0, 1.0)) // Outside quota never eligible.
        builder.record(snapshot.generation, items[3], analysis(140.0, .7))
        assertNull(builder.best(snapshot.generation, items[2]))
        builder.record(snapshot.generation, items[4], analysis(130.0, .9))
        assertEquals(items[4], builder.best(snapshot.generation, items[2]), "Highest-confidence fallback considers only budget targets")
        assertEquals(items[4], builder.consume(snapshot.generation, items[4]))
        assertEquals(items[4].entryId, builder.tailEntryId())
        assertNull(builder.consume(snapshot.generation, items[4]))
        assertEquals(items.filter { it != items[2] && it != items[4] }, builder.takeRemaining())
    }

    @Test fun staleGenerationCannotChangeNewTargetsAnalysisOrConsumption() {
        val items = entries(5); val builder = MeloXSmartQueueBuilder()
        builder.begin(items, 0); val old = builder.snapshot().generation
        builder.begin(items, 2); val current = builder.snapshot().generation
        builder.setAnalysisTargets(old, listOf(items[1]))
        builder.record(old, items[2], analysis(90.0, 1.0)); builder.failure(old, items[3])
        assertNull(builder.consume(old, items[3]))
        builder.setAnalysisTargets(current, listOf(items[2], items[3]))
        builder.record(current, items[2], analysis(90.0, .8)); builder.record(current, items[3], analysis(91.0, .8))
        assertEquals(items[3], builder.best(current, items[2]))
        assertNull(builder.best(current, items[2], setOf(items[4].entryId)), "A current/history item excluded by the timeline cannot be moved")
    }
}
