package melox.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicRankingSummary
import melox.music.model.MusicResourceId
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.RankingCapability
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 0.6.5 `HomeSongListDetail`: quick actions and rankings open the canonical
 * song-list detail. Their content is already loaded from the source, so the
 * screen must not send the synthetic title to a playlist endpoint.
 */
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@Composable
internal fun MeloXProviderSongListDetail(
    songs: List<SearchSong>,
    title: String,
    onBack: () -> Unit,
) {
    val playlist = remember(title, songs) {
        val source = songs.firstOrNull()?.providerTrack?.id?.source
        NeteasePlaylistSummary(
            id = -(title.hashCode().toLong() and 0x7fff_ffffL) - 1L,
            name = title,
            coverUrl = songs.firstOrNull()?.artworkUrl,
            trackCount = songs.size,
            creatorName = "${source?.displayName ?: "网易云音乐"} · ${songs.size} 首歌曲",
        )
    }
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        val queue by melox.playback.PlaybackCommands.queue.collectAsState()
        val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
        var filter by remember(title, songs) { mutableStateOf("") }
        var selected by remember(title, songs) { mutableStateOf<SearchSong?>(null) }
        var anchor by remember { mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null) }
        melox.ui.desktop.MeloXDesktopCollectionDetail(
            kind = melox.ui.desktop.desktopString("ui.category.toplists"), title = title,
            subtitle = songs.firstOrNull()?.providerTrack?.id?.source?.displayName,
            metadata = melox.ui.desktop.desktopFormat("ui.common.song_count", songs.size),
            description = null, artworkUrl = playlist.coverUrl,
            songs = songs.filter { filter.isBlank() || it.name.contains(filter, true) || it.artists.contains(filter, true) || it.album.contains(filter, true) },
            hasSongs = songs.isNotEmpty(), loading = false, error = null,
            playingId = queue.songs.getOrNull(queue.index)?.id, isPlaying = playback.isPlaying,
            onBack = onBack,
            onPlayAll = { songs.firstOrNull()?.let { melox.playback.PlaybackCommands.playQueue(songs, it.id) } },
            onShuffle = { songs.shuffled().takeIf { it.isNotEmpty() }?.let { melox.playback.PlaybackCommands.playQueue(it, it.first().id) } },
            favoriteEnabled = false, filter = filter, onFilterChange = { filter = it },
            onRetry = {}, onSong = { melox.playback.PlaybackCommands.playQueue(songs, it.id) },
            onMore = { selected = it }, onMenuAnchor = { anchor = it }, tag = "desktop-provider-ranking-detail",
        )
        selected?.let { melox.ui.desktop.MeloXDesktopSongMenu(it, { selected = null }, anchor) }
        return
    }
    val client = remember { NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    androidx.compose.animation.SharedTransitionLayout(Modifier.fillMaxSize().testTag("home-song-list-detail")) {
        androidx.compose.animation.AnimatedVisibility(visible = true) {
            MeloXLibraryPlaylistDetailScreen(
                playlist, client, onBack, this@SharedTransitionLayout, this,
                providedSongs = songs,
            )
        }
    }
}

/** Load every page of a provider ranking and de-duplicate by source + real ID. */
internal suspend fun loadProviderRankingSongs(
    source: melox.music.model.MusicSource,
    ranking: MusicRankingSummary,
): List<SearchSong> = withContext(Dispatchers.IO) {
    val capability = MeloXMusicProviders.create().require(source) as? RankingCapability
        ?: error("${source.displayName} 当前不提供排行榜详情")
    val tracks = linkedMapOf<MusicResourceId, melox.music.model.MusicTrack>()
    var page = 1
    while (true) {
        val result = capability.rankingTracks(ranking, page = page, pageSize = 200)
        val before = tracks.size
        result.items.forEach { tracks[it.id] = it }
        if (!result.hasMore || tracks.size == before || result.items.isEmpty()) break
        page++
    }
    tracks.values.map(MeloXLegacyUiBridge::track)
}
