package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicRankingSummary
import melox.music.model.MusicSource
import melox.music.provider.HomeFeedCapability
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.network.NeteaseCollectionDetailsClient
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsRuntime
import melox.ui.navigation.MeloXCollectionLaunchBus
import melox.ui.navigation.MeloXCollectionTarget
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.floor

/** DesktopDiscoveryView: new songs, toplists and circular popular artists. */
@Composable
internal fun MeloXDesktopExplore(source: MusicSource, onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onProviderPlaylist: (MusicPlaylistSummary) -> Unit) {
    var songs by remember(source) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var playlists by remember(source) { mutableStateOf<List<DesktopExploreCard>>(emptyList()) }
    var artists by remember(source) { mutableStateOf<List<DesktopExploreArtist>>(emptyList()) }
    var loading by remember(source) { mutableStateOf(true) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = key(source) { rememberCoroutineScope() }
    var rankingRequest by remember(source) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var rankingSongs by remember(source) { mutableStateOf<List<SearchSong>?>(null) }
    var rankingTitle by remember(source) { mutableStateOf("") }
    val revision by NeteaseSessionStore.revision.collectAsState()
    LaunchedEffect(source, revision, retry) {
        loading = true; error = null
        try {
            if (source == MusicSource.Netease) {
                val cookie = NeteaseSessionStore.readCookie()
                val client = NeteaseLibraryClient(cookieProvider = { cookie })
                val details = NeteaseCollectionDetailsClient({ cookie })
                coroutineScope {
                    // Mac sections are independently optional; one failing route
                    // must not erase the other sections' real content.
                    val content = async { runCatching { client.homeContent(podcastsEnabled = false, refresh = retry > 0) } }
                    val charts = async { runCatching { details.toplists() } }
                    val top = async { runCatching { details.topArtists(18) } }
                    val feed = content.await()
                    val chartResult = charts.await()
                    val topResult = top.await()
                    songs = feed.getOrNull()?.newSongs.orEmpty().take(12)
                    playlists = chartResult.getOrNull().orEmpty().map { playlist ->
                        DesktopExploreCard(playlist.name, playlist.copywriter.orEmpty(), playlist.coverUrl, playlist.playCount,
                            "desktop-explore-playlist:${playlist.id}") { onPlaylist(playlist) }
                    }
                    artists = topResult.getOrNull().orEmpty().map { artist ->
                        DesktopExploreArtist(artist.id, artist.name, artist.aliases.firstOrNull().orEmpty(), artist.artworkUrl)
                    }
                    if (songs.isEmpty() && playlists.isEmpty() && artists.isEmpty()) {
                        error = (feed.exceptionOrNull() ?: chartResult.exceptionOrNull() ?: topResult.exceptionOrNull())
                            ?.message ?: desktopString("ui.error.home.load_failed")
                    }
                }
            } else {
                val provider = MeloXMusicProviders.create().require(source)
                val feed = (provider as? HomeFeedCapability)?.homeFeed(playlistLimit = 40, newSongLimit = 12, rankingLimit = 30)
                if (feed == null) error = desktopString("ui.error.home.load_failed")
                else {
                    songs = feed.newSongs.map(MeloXLegacyUiBridge::track)
                    playlists = buildList {
                        feed.recommendedPlaylists.forEach { value ->
                            add(DesktopExploreCard(value.title, value.creatorName.orEmpty(), value.artworkUrl, value.playCount ?: 0L,
                                "desktop-explore-provider:${value.id.value}") { onProviderPlaylist(value) })
                        }
                        feed.rankings.forEach { value: MusicRankingSummary ->
                            add(DesktopExploreCard(value.title, value.subtitle.orEmpty(), value.artworkUrl, 0L,
                                "desktop-explore-ranking:${value.id.value}") {
                                rankingTitle = value.title
                                rankingRequest?.cancel()
                                rankingRequest = scope.launch {
                                    try { rankingSongs = melox.ui.screens.loadProviderRankingSongs(source, value) }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
                                }
                            })
                        }
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        val scroll = rememberLazyListState()
        val queue by PlaybackCommands.queue.collectAsState()
        val playback by melox.player.AudioPlayer.state.collectAsState()
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-explore")) {
            LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll), state = scroll,
                contentPadding = PaddingValues(horizontal = 34.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(34.dp)) {
                item("title") { Text(desktopString("ui.navigation.explore"), fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                if (songs.isNotEmpty()) item("new-songs") {
                    DesktopExploreSection(desktopString("ui.desktop.explore.new_songs")) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            songs.forEachIndexed { index, song ->
                                DesktopCollectionTrackRow(index + 1, song, queue.songs.getOrNull(queue.index)?.id == song.id,
                                    playback.isPlaying, false, { PlaybackCommands.playQueue(songs, song.id) }, onMore = null, showsArtwork = true)
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) item("toplists") {
                    DesktopExploreSection(desktopString("ui.category.toplists")) {
                        DesktopExploreGrid(playlists)
                    }
                }
                if (artists.isNotEmpty()) item("artists") {
                    DesktopExploreSection(desktopString("ui.desktop.explore.popular_artists")) {
                        DesktopExploreArtistGrid(artists)
                    }
                }
                if (loading && songs.isEmpty() && playlists.isEmpty() && artists.isEmpty()) item("loading") {
                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                if (error != null) item("error") {
                    Column(Modifier.fillMaxWidth().heightIn(min = 220.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Text(desktopString("ui.error.home.load_failed"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(error!!, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        DesktopDialogButton("ui.common.retry", onClick = { retry++ })
                    }
                }
                if (!loading && error == null && songs.isEmpty() && playlists.isEmpty() && artists.isEmpty()) item("empty") {
                    Box(Modifier.fillMaxWidth().height(360.dp), contentAlignment = Alignment.Center) {
                        Text(desktopString("ui.desktop.library.empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item("clearance") { Spacer(Modifier.height(80.dp)) }
            }
            melox.ui.navigation.MeloXDetailOverlay(rankingSongs, { rankingSongs = null }) {
                melox.ui.screens.MeloXProviderSongListDetail(it, rankingTitle) { rankingSongs = null }
            }
        }
    }
}

private data class DesktopExploreCard(val title: String, val subtitle: String, val artwork: String?, val playCount: Long,
    val tag: String, val action: () -> Unit)
private data class DesktopExploreArtist(val id: Long, val name: String, val subtitle: String, val artwork: String?)

@Composable
private fun DesktopExploreSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun DesktopExploreGrid(cards: List<DesktopExploreCard>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = floor((maxWidth.value + 20) / 145).toInt().coerceAtLeast(1)
        val width = ((maxWidth.value - (count - 1) * 20) / count).coerceAtMost(205f).dp
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            cards.chunked(count).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { card ->
                        DesktopHomeMediaCard(card.title, card.subtitle, card.artwork, width, card.tag, card.playCount,
                            onOpen = card.action)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopExploreArtistGrid(values: List<DesktopExploreArtist>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val count = floor((maxWidth.value + 20) / 145).toInt().coerceAtLeast(1)
        val width = ((maxWidth.value - (count - 1) * 20) / count).coerceAtMost(205f).dp
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            values.chunked(count).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { artist ->
                        DesktopHomeMediaCard(artist.name, artist.subtitle, artist.artwork, width, "desktop-explore-artist:${artist.id}",
                            onOpen = { MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Artist(artist.id)) }, isCircular = true)
                    }
                }
            }
        }
    }
}
