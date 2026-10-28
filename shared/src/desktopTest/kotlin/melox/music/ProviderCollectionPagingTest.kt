package melox.music

import kotlinx.coroutines.runBlocking
import melox.music.model.MusicPlaylistDetail
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.music.provider.PlaylistCapability
import melox.music.provider.containsNativeTrack
import melox.music.provider.loadAllPlaylistTracks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProviderCollectionPagingTest {
    private val playlist = MusicPlaylistSummary(MusicResourceId(MusicSource.YouTubeMusic, "list"), "List")
    private fun track(entry: String?) = MusicTrack(MusicResourceId(MusicSource.YouTubeMusic, "video"), "Video", emptyList(),
        providerMetadata = ProviderTrackMetadata.YouTube("video", entry))

    @Test fun youtubePlaylistKeepsRepeatedVideoMembershipsAcrossPages() = runBlocking {
        val pages = listOf(listOf(track("first"), track("second")), listOf(track(null)))
        val provider = object : PlaylistCapability {
            override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) =
                MusicPlaylistDetail(playlist, pages[page - 1], if (page == 2) 3 else null, page < 2)
        }
        val loaded = provider.loadAllPlaylistTracks(playlist, pageSize = 2)
        assertEquals(pages.flatten(), loaded.tracks)
        assertEquals(3, loaded.summary.trackCount)
        assertEquals(false, loaded.hasMore)
    }

    @Test fun youtubeEmptyIntermediatePageIsNotPresentedAsACompletePlaylist() = runBlocking<Unit> {
        val provider = object : PlaylistCapability {
            override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) =
                MusicPlaylistDetail(playlist, if (page == 1) listOf(track("first")) else emptyList(), null, true)
        }
        assertFailsWith<IllegalStateException> { provider.loadAllPlaylistTracks(playlist, pageSize = 2) }
    }

    @Test fun favoriteLookupFollowsExplicitContinuationEvenForAnUnderfilledPage() = runBlocking {
        val target = track("target").copy(id = MusicResourceId(MusicSource.YouTubeMusic, "target"))
        val provider = object : PlaylistCapability {
            override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) =
                MusicPlaylistDetail(playlist, if (page == 1) listOf(track("first")) else listOf(target), null, page == 1)
        }
        assertEquals(true, provider.containsNativeTrack(playlist, target))
    }

    @Test fun favoriteLookupRejectsEmptyPageThatStillHasAContinuation() = runBlocking<Unit> {
        val target = track("target").copy(id = MusicResourceId(MusicSource.YouTubeMusic, "target"))
        val provider = object : PlaylistCapability {
            override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) =
                MusicPlaylistDetail(playlist, emptyList(), null, true)
        }
        assertFailsWith<IllegalStateException> { provider.containsNativeTrack(playlist, target) }
    }
}
