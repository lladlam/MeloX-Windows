package melox.playback

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSleepTimerTest {
    @Test fun replacementAndCancellationNeverPauseAtOldDeadline() = runTest {
        var pauses = 0
        val persisted = mutableListOf<Long>()
        val timer = PlaybackSleepTimer(backgroundScope, { testScheduler.currentTime }, { persisted += it }, { pauses++ })
        timer.set(15)
        advanceTimeBy(60_000)
        timer.set(30)
        advanceTimeBy(14 * 60_000); runCurrent()
        assertEquals(0, pauses)
        timer.cancel()
        advanceTimeBy(31 * 60_000); runCurrent()
        assertEquals(0, pauses)
        assertEquals(0L, timer.endEpochMs.value)
        assertEquals(listOf(900_000L, 1_860_000L, 0L), persisted)
    }

    @Test fun restoredDeadlineSurvivesPanelLifetimeAndExpiresExactlyOnce() = runTest {
        var pauses = 0
        var persisted = -1L
        val timer = PlaybackSleepTimer(backgroundScope, { testScheduler.currentTime }, { persisted = it }, { pauses++ })
        timer.restore(2_500L)
        advanceTimeBy(2_499); runCurrent()
        assertEquals(0, pauses)
        advanceTimeBy(1); runCurrent()
        assertEquals(1, pauses)
        assertEquals(0L, persisted)
        advanceTimeBy(10_000); runCurrent()
        assertEquals(1, pauses)
    }

    @Test fun processDisposalStopsWorkerButPreservesDeadlineForNextLaunch() = runTest {
        var persisted = 0L
        var pauses = 0
        val timer = PlaybackSleepTimer(backgroundScope, { testScheduler.currentTime }, { persisted = it }, { pauses++ })
        timer.restore(2_500L)
        runCurrent()
        timer.stop()
        advanceTimeBy(3_000); runCurrent()
        assertEquals(0, pauses)
        assertEquals(2_500L, persisted)
        timer.restore(persisted)
        runCurrent()
        assertEquals(1, pauses)
        assertEquals(0L, persisted)
    }
}
