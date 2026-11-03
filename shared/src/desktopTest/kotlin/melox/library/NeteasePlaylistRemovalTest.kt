package melox.library

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NeteasePlaylistRemovalTest {
    private fun playlist(id: Long, creator: Long?) = NeteasePlaylistSummary(id, "fixture", null, 0, "", creator)

    @Test fun determinesRemovalFromFreshAccountList() {
        val values = listOf(playlist(1, 10), playlist(2, 10), playlist(3, 20))
        assertEquals(NeteasePlaylistRemoval.Delete, neteasePlaylistRemoval(values, 10, 2))
        assertEquals(NeteasePlaylistRemoval.Unsubscribe, neteasePlaylistRemoval(values, 10, 3))
        assertFailsWith<IllegalArgumentException> { neteasePlaylistRemoval(values, 10, 1) }
    }

    @Test fun refusesUnknownOrAmbiguousState() {
        assertFailsWith<IllegalStateException> { neteasePlaylistRemoval(emptyList(), 10, 2) }
        assertFailsWith<IllegalStateException> { neteasePlaylistRemoval(listOf(playlist(1, 10)), 10, 2) }
        assertFailsWith<IllegalStateException> { neteasePlaylistRemoval(listOf(playlist(1, 10), playlist(2, null)), 10, 2) }
        assertFailsWith<IllegalStateException> { neteasePlaylistRemoval(listOf(playlist(1, 10), playlist(2, 10), playlist(2, 20)), 10, 2) }
    }

    @Test fun readsFreshListAndNeverWritesForProtectedOrChangedSession() = runBlocking {
        val values = listOf(playlist(1, 10), playlist(2, 10), playlist(3, 20))
        val events = mutableListOf<String>()
        suspend fun remove(id: Long, current: Boolean = true) = removeNeteaseLibraryPlaylist(
            10, id,
            loadPlaylists = { events += "read:$it"; values },
            checkSession = { check(current); events += "session" },
            delete = { events += "delete:$it" },
            unsubscribe = { events += "unsubscribe:$it" },
        )
        remove(2)
        remove(3)
        assertEquals(listOf("session", "read:10", "session", "delete:2", "session", "read:10", "session", "unsubscribe:3"), events)
        events.clear()
        assertFailsWith<IllegalArgumentException> { remove(1) }
        assertEquals(listOf("session", "read:10"), events)
        events.clear()
        assertFailsWith<IllegalStateException> { remove(2, current = false) }
        assertEquals(emptyList(), events)
        var checks = 0
        assertFailsWith<IllegalStateException> {
            removeNeteaseLibraryPlaylist(10, 2,
                loadPlaylists = { events += "read:$it"; values },
                checkSession = { checks++; check(checks == 1) },
                delete = { events += "delete:$it" },
                unsubscribe = { events += "unsubscribe:$it" })
        }
        assertEquals(listOf("read:10"), events)
    }
}
