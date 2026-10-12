package melox.music

import kotlinx.coroutines.*
import melox.music.model.*
import melox.music.provider.*
import kotlin.test.*

/** Provider callback fixtures verify concurrency/cancellation and identity boundaries.
 * Production still calls its existing genuine providers and selected whitelist.
 */
class UnifiedSearchLifecycleTest {
    private class FixtureProvider(override val source: MusicSource, val search: suspend (String) -> List<MusicTrack>) : MusicProvider, SearchCapability {
        override val displayName = source.displayName
        override val capabilities = setOf(MusicCapability.Search)
        override suspend fun searchSongs(query: String, page: Int, pageSize: Int) = MusicPage(search(query), page, pageSize)
    }
    private fun track(source: MusicSource, title: String = "Music", id: String = "native-id") = MusicTrack(
        MusicResourceId(source, id), title, listOf(MusicArtistRef(name = "Artist")), durationMs = 120000,
    )

    @Test fun concurrentWhitelistSearchPreservesHealthyNativeIdentityWhenAnotherProviderFails() = runBlocking<Unit> {
        val ready = CompletableDeferred<Unit>()
        var forbiddenCalls = 0
        val healthy = FixtureProvider(MusicSource.Local) { ready.await(); listOf(track(MusicSource.Local)) }
        val failing = FixtureProvider(MusicSource.Netease) { ready.complete(Unit); error("actual provider failure") }
        val excluded = FixtureProvider(MusicSource.QQMusic) { forbiddenCalls++; emptyList() }
        val result = withTimeout(2000) {
            UnifiedMusicService(MusicProviderRegistry(listOf(healthy, failing, excluded))).searchSongs("query", setOf(MusicSource.Local, MusicSource.Netease))
        }
        assertEquals(0, forbiddenCalls)
        assertEquals(MusicSource.Local, result.tracks.single().id.source)
        assertEquals("native-id", result.aggregated.single().recommendation?.track?.id?.value)
        assertEquals(MusicSource.Netease, result.failures.single().source)
        assertEquals("actual provider failure", result.failures.single().message)
    }

    @Test fun timedOutSourceReportsItsOwnFailureWhileOtherApprovedResultsRemainUsable() = runBlocking<Unit> {
        var released = false
        val slow = FixtureProvider(MusicSource.Netease) { try { awaitCancellation() } finally { released = true } }
        val healthy = FixtureProvider(MusicSource.Local) { listOf(track(MusicSource.Local)) }
        val service = UnifiedMusicService(MusicProviderRegistry(listOf(slow, healthy)), providerTimeoutMs = 50)
        val result = withTimeout(2000) { service.searchSongs("query", setOf(MusicSource.Local, MusicSource.Netease)) }
        assertTrue(released)
        assertEquals(MusicSource.Local, result.tracks.single().id.source)
        assertEquals(MusicSource.Netease, result.failures.single().source)
        assertTrue(result.failures.single().message.contains("超时"))
    }

    @Test fun cancellingObsoleteSearchCancelsAllChildrenInsteadOfPublishingFailureResults() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        var released = false
        var published = false
        val provider = FixtureProvider(MusicSource.Local) { started.complete(Unit); try { awaitCancellation() } finally { released = true } }
        val request = launch {
            UnifiedMusicService(MusicProviderRegistry(listOf(provider))).searchSongs("obsolete", setOf(MusicSource.Local))
            published = true
        }
        withTimeout(2000) { started.await(); request.cancelAndJoin() }
        assertTrue(request.isCancelled)
        assertTrue(released)
        assertFalse(published)
    }

    @Test fun nativeDuplicatesAreRemovedAndForeignIdentityCannotMasqueradeAsTheQueriedProvider() = runBlocking<Unit> {
        val native = track(MusicSource.Netease)
        val wrong = track(MusicSource.QQMusic, "Foreign")
        val provider = FixtureProvider(MusicSource.Netease) { listOf(native, native, wrong) }
        val result = UnifiedMusicService(MusicProviderRegistry(listOf(provider))).searchSongs("query", setOf(MusicSource.Netease))
        assertEquals(listOf(native), result.tracks)
        assertEquals(MusicSource.Netease, result.failures.single().source)
        assertTrue(result.failures.single().message.contains("身份"))
    }

    @Test fun studioAndLiveResultsKeepTheirDistinctVersionsAndOriginalResourceIds() = runBlocking<Unit> {
        val studio = track(MusicSource.Local, "Music", "studio-key")
        val live = track(MusicSource.Netease, "Music (Live)", "live-key")
        val providers = listOf(FixtureProvider(MusicSource.Local) { listOf(studio) }, FixtureProvider(MusicSource.Netease) { listOf(live) })
        val result = UnifiedMusicService(MusicProviderRegistry(providers)).searchSongs("Music", setOf(MusicSource.Local, MusicSource.Netease))
        assertEquals(2, result.aggregated.size)
        assertEquals(setOf(studio.id, live.id), result.aggregated.mapNotNull { it.recommendation?.track?.id }.toSet())
    }

    @Test fun anUndeclaredSearchMethodIsUnavailableRatherThanAReportedEmptySuccess() = runBlocking<Unit> {
        var invoked = false
        val provider = object : MusicProvider, SearchCapability {
            override val source = MusicSource.Jellyfin
            override val displayName = source.displayName
            override val capabilities = setOf(MusicCapability.Playback)
            override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> {
                invoked = true
                return MusicPage(emptyList(), page, pageSize)
            }
        }
        val result = UnifiedMusicService(MusicProviderRegistry(listOf(provider))).searchSongs("query", setOf(MusicSource.Jellyfin))
        assertFalse(invoked)
        assertTrue(result.tracks.isEmpty())
        assertEquals(MusicSource.Jellyfin, result.failures.single().source)
    }
}
