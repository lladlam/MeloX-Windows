package melox.provider.youtubemusic

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YouTubeCursorPagerTest {
    @Test fun fillsNumberedPagesFromMultipleCursorBatchesWithoutFalseTotals() = runBlocking {
        val pager = YouTubeCursorPager<String> { it }
        var requests = 0
        val first = suspend { YouTubeCursorPager.Batch(listOf("a", "b"), "c1") }
        val next: suspend (String) -> YouTubeCursorPager.Batch<String> = { cursor ->
            requests++
            when (cursor) {
                "c1" -> YouTubeCursorPager.Batch(listOf("b", "c"), "c2")
                "c2" -> YouTubeCursorPager.Batch(listOf("d", "e"), null)
                else -> error("unexpected cursor")
            }
        }
        val one = pager.page("account-a", 1, 2, first, next)
        assertEquals(listOf("a", "b"), one.items)
        assertNull(one.total)
        assertTrue(one.hasMore)
        val two = pager.page("account-a", 2, 2, first, next)
        assertEquals(listOf("c", "d"), two.items)
        assertEquals(5L, two.total)
        assertTrue(two.hasMore)
        val three = pager.page("account-a", 3, 2, first, next)
        assertEquals(listOf("e"), three.items)
        assertFalse(three.hasMore)
        assertEquals(2, requests)
    }

    @Test fun repeatedCursorFailsInsteadOfReportingAnIncompleteLibraryAsComplete() = runBlocking<Unit> {
        val pager = YouTubeCursorPager<String> { it }
        assertFailsWith<IllegalStateException> {
            pager.page("account", 1, 4,
                first = { YouTubeCursorPager.Batch(listOf("a"), "same") },
                next = { YouTubeCursorPager.Batch(listOf("a"), "same") })
        }
    }

    @Test fun changingAccountKeyDoesNotReuseAnotherAccountsCachedCollection() = runBlocking {
        val pager = YouTubeCursorPager<String> { it }
        val firstA = suspend { YouTubeCursorPager.Batch(listOf("private-a"), null) }
        val firstB = suspend { YouTubeCursorPager.Batch(listOf("private-b"), null) }
        val noNext: suspend (String) -> YouTubeCursorPager.Batch<String> = { error("unexpected continuation") }
        assertEquals(listOf("private-a"), pager.page("a", 1, 10, firstA, noNext).items)
        assertEquals(listOf("private-b"), pager.page("b", 1, 10, firstB, noNext).items)
    }

    @Test fun playlistPreservesRepeatedVideosWithoutMembershipIds() = runBlocking {
        data class Song(val video: String, val membership: String?)
        val pager = YouTubeCursorPager<Song> { it.membership }
        val page = pager.page("playlist", 1, 10,
            first = { YouTubeCursorPager.Batch(listOf(Song("video", null), Song("video", null), Song("other", "m1")), "next") },
            next = { YouTubeCursorPager.Batch(listOf(Song("other", "m1"), Song("video", null)), null) })
        assertEquals(listOf(Song("video", null), Song("video", null), Song("other", "m1"), Song("video", null)), page.items)
        assertEquals(4L, page.total)
    }
}
