package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.*
import melox.music.provider.MeloXLegacyUiBridge
import melox.playback.PlaybackCommands

/** Provider-native feed with Desktop geometry and complete actionable ranking routes. */
@Composable
internal fun MeloXDesktopProviderHome(source: MusicSource, feed: MusicHomeFeed?, loading: Boolean, error: String?,
    onRetry: () -> Unit, onPlaylist: (MusicPlaylistSummary) -> Unit, onRanking: (MusicRankingSummary) -> Unit) {
    val scroll = rememberLazyListState()
    val queue by PlaybackCommands.queue.collectAsState()
    val state by melox.player.AudioPlayer.state.collectAsState()
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(
        fontFamily = melox.ui.theme.MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).desktopPromotedDragScroll(scroll)
        .testTag("desktop-provider-home:${source.name}"), state = scroll,
        contentPadding = PaddingValues(start = 34.dp, end = 34.dp, top = 28.dp, bottom = 150.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)) {
        item("title") { Column {
            Text(desktopString("ui.navigation.home"), fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(source.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        } }
        if (loading && feed == null) item("loading") { CircularProgressIndicator(Modifier.size(22.dp)) }
        if (feed?.recommendedPlaylists?.isNotEmpty() == true) item("playlists") {
            Text(desktopString("ui.home.section.recommended_playlists"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            DesktopHomePagingShelf(feed.recommendedPlaylists, { it.id.value }, 175.dp, 20.dp, 0, "provider-home-playlists:${source.name}") { playlist ->
                DesktopHomeMediaCard(playlist.title, playlist.creatorName.orEmpty(), playlist.artworkUrl, 175.dp,
                    "desktop-provider-home-playlist:${playlist.id.value}", playlist.playCount ?: 0, onOpen = { onPlaylist(playlist) })
            }
        }
        if (feed?.rankings?.isNotEmpty() == true) item("rankings") {
            Text(desktopString("ui.category.toplists"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            DesktopHomePagingShelf(feed.rankings, { it.id.value }, 175.dp, 20.dp, 0, "provider-home-rankings:${source.name}") { ranking ->
                DesktopHomeMediaCard(ranking.title, ranking.subtitle.orEmpty(), ranking.artworkUrl, 175.dp,
                    "desktop-provider-home-ranking:${ranking.id.value}", onOpen = { onRanking(ranking) })
            }
        }
        if (feed?.newSongs?.isNotEmpty() == true) item("songs") {
            val songs = feed.newSongs.map(MeloXLegacyUiBridge::track)
            Text(desktopString("ui.desktop.explore.new_songs"), fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Column {
                songs.forEachIndexed { index, song -> DesktopCollectionTrackRow(index + 1, song,
                    queue.songs.getOrNull(queue.index)?.id == song.id, state.isPlaying, false,
                    { PlaybackCommands.playQueue(songs, song.id) }, onMore = null, showsArtwork = true) }
            }
        }
        error?.let { message -> item("error") {
            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("provider-home-error"))
            TextButton(onRetry, Modifier.testTag("provider-home-retry")) { Text(desktopString("ui.common.retry")) }
        } }
        if (!loading && error == null && feed?.recommendedPlaylists.isNullOrEmpty() && feed?.newSongs.isNullOrEmpty() && feed?.rankings.isNullOrEmpty()) item("empty") {
            Text(desktopString("ui.desktop.library.empty.message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onRetry) { Text(desktopString("ui.common.retry")) }
        }
    }
    }
}
