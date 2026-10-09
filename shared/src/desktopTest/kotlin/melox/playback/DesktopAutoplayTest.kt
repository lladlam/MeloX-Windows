package melox.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import melox.model.SearchSong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopAutoplayTest {
    private fun song(id: Long, title: String = "title", artist: String = "artist", duration: Long = 180_000L) =
        SearchSong(id, title, artist, "album", null, durationMs = duration)

    private fun entry(id: Long) = QueueEntry(song(id), PlaybackCommands.QUEUE_ORIGIN_BASE, 0, "entry-$id")

    @Test fun disabledAutoplayCannotCommitAnUncancellableLateRecommendation() = runTest {
        var enabled = true
        val pending = CompletableDeferred<List<SearchSong>>()
        val appended = mutableListOf<SearchSong>()
        val coordinator = DesktopAutoplayCoordinator(backgroundScope, object : DesktopAutoplaySource() {
            override suspend fun recommendations(song: SearchSong) = withContext(NonCancellable) { pending.await() }
        }, { enabled }, { entry(1) }, { appended += it }, {})
        coordinator.selectionChanged()
        coordinator.ensure()
        runCurrent()
        enabled = false
        coordinator.cancel()
        pending.complete(listOf(song(2)))
        runCurrent()
        assertTrue(appended.isEmpty())
    }

    @Test fun changingTracksDiscardsTheOldResultAndAllowsTheNewSeed() = runTest {
        var current = entry(1)
        val old = CompletableDeferred<List<SearchSong>>()
        val appended = mutableListOf<SearchSong>()
        val coordinator = DesktopAutoplayCoordinator(backgroundScope, object : DesktopAutoplaySource() {
            override suspend fun recommendations(song: SearchSong): List<SearchSong> =
                if (song.id == 1L) withContext(NonCancellable) { old.await() } else listOf(this@DesktopAutoplayTest.song(3))
        }, { true }, { current }, { appended += it }, {})
        coordinator.selectionChanged()
        coordinator.ensure()
        runCurrent()
        current = entry(2)
        coordinator.selectionChanged()
        coordinator.ensure()
        runCurrent()
        old.complete(listOf(song(99)))
        runCurrent()
        assertEquals(listOf(3L), appended.map { it.id })
        coordinator.cancel()
    }

    @Test fun failureWaitsFifteenSecondsAndConcurrentRequestsAreDeduplicated() = runTest {
        var now = 0L
        var calls = 0
        val appended = mutableListOf<SearchSong>()
        val coordinator = DesktopAutoplayCoordinator(backgroundScope, object : DesktopAutoplaySource() {
            override suspend fun recommendations(song: SearchSong): List<SearchSong> {
                if (++calls == 1) throw java.io.IOException("network down")
                return listOf(this@DesktopAutoplayTest.song(2))
            }
        }, { true }, { entry(1) }, { appended += it }, {}, { now })
        coordinator.selectionChanged()
        coordinator.ensure()
        coordinator.ensure()
        runCurrent()
        assertEquals(1, calls)
        now = 14_999
        coordinator.ensure()
        runCurrent()
        assertEquals(1, calls)
        now = 15_000
        coordinator.ensure()
        runCurrent()
        assertEquals(listOf(2L), appended.map { it.id })
        coordinator.ensure()
        runCurrent()
        assertEquals(2, calls)
        coordinator.cancel()
    }

    @Test fun crossProviderSeedsRequireTheSourceTitleArtistAndDurationMatch() {
        val wrongArtist = song(1, "Title!", "another artist", 180_000)
        val wrongDuration = song(2, "Title", "artist", 200_000)
        val partialArtist = song(3, "Title", "artist / guest", 180_000)
        val exact = song(4, "TITLE", "artist", 181_000)
        assertEquals(4L, selectNeteaseAutoplaySeed("Title", "artist", 180_000, listOf(wrongArtist, wrongDuration, partialArtist, exact)))
        assertNull(selectNeteaseAutoplaySeed("Title", "artist", 180_000, listOf(wrongArtist, wrongDuration)))
    }
}
