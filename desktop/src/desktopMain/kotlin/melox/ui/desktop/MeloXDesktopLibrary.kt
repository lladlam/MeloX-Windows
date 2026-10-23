package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import melox.account.NeteaseSessionStore
import melox.account.rememberNeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.network.*
import melox.playback.PlaybackCommands
import melox.ui.navigation.MeloXCollectionLaunchBus
import melox.ui.navigation.MeloXCollectionTarget
import melox.ui.screens.PlaylistDetailScreen
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.floor

private data class DesktopLibraryCard(val id: Long, val title: String, val subtitle: String, val artwork: String?, val open: () -> Unit)

/** Non-NetEase providers keep the Mac grid/list structure with their real playlists. */
@Composable
internal fun MeloXDesktopProviderLibrary(source: MusicSource, playlists: List<NeteasePlaylistSummary>,
    loading: Boolean, error: String?, accountName: String?, onLogin: () -> Unit, onRetry: () -> Unit,
    onOpen: (NeteasePlaylistSummary) -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).desktopVerticalScroll()
            .padding(horizontal = 34.dp, vertical = 28.dp).testTag("desktop-provider-library")) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(desktopString("ui.navigation.library"), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text(source.displayName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                accountName?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(22.dp))
            when {
                loading && playlists.isEmpty() -> Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                playlists.isEmpty() -> Column(Modifier.fillMaxWidth().heightIn(min = 340.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    Text(desktopString(if (accountName == null) "ui.desktop.library.sign_in_title" else "ui.desktop.library.empty"),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(error ?: desktopString("ui.desktop.library.empty.message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (accountName == null) DesktopDialogButton("ui.common.login", prominent = true, tag = "provider-library-login", onClick = onLogin)
                    if (error != null) DesktopDialogButton("ui.common.retry", tag = "provider-library-retry", onClick = onRetry)
                }
                else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val count = floor((maxWidth.value + 20) / 165).toInt().coerceAtLeast(1)
                    val width = ((maxWidth.value - (count - 1) * 20) / count).coerceAtMost(205f).dp
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        playlists.chunked(count).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                row.forEach { playlist ->
                                    DesktopHomeMediaCard(playlist.name, playlist.creatorName, playlist.coverUrl, width,
                                        "desktop-provider-library-card:${playlist.id}", playlist.playCount,
                                        onOpen = { onOpen(playlist) })
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

/** DesktopLibraryView uses sidebar destinations, never the phone segmented picker. */
@Composable
internal fun MeloXDesktopLibrary(page: String?, onLogin: () -> Unit) {
    val session = rememberNeteaseSessionStore()
    val revision by NeteaseSessionStore.revision.collectAsState()
    val section = page ?: "Songs"
    val cookie = remember(revision) { NeteaseSessionStore.readCookie() }
    val client = remember(cookie) { NeteaseLibraryClient(cookieProvider = { cookie }) }
    val details = remember(cookie) { NeteaseCollectionDetailsClient({ cookie }) }
    val universal = remember(cookie) { NeteaseUniversalSearchClient(cookieProvider = { cookie }) }
    val operations = remember(cookie) { NeteaseMusicOperationsClient(cookieProvider = { cookie }) }
    var songs by remember(section, cookie, revision) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var cards by remember(section, cookie, revision) { mutableStateOf<List<DesktopLibraryCard>>(emptyList()) }
    var playlist by remember(section, cookie, revision) { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var podcast by remember(section, cookie, revision) { mutableStateOf<MeloXPodcast?>(null) }
    var loading by remember(section, cookie, revision) { mutableStateOf(session.isLoggedIn) }
    var error by remember(section, cookie, revision) { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var creating by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val title = when (section) {
        "Playlists" -> "ui.navigation.library.liked_playlists"; "Albums" -> "ui.navigation.library.liked_albums"
        "Artists" -> "ui.library.artists.title"; "Podcasts" -> "ui.navigation.library.podcasts"
        "History" -> "ui.navigation.library.history"; else -> "ui.navigation.library.liked_songs"
    }
    LaunchedEffect(section, cookie, revision, refresh) {
        session.reloadFromStorage()
        if (!NeteaseSessionStore.containsMusicU(cookie) || section in setOf("Cloud", "Downloads")) {
            loading = false
            return@LaunchedEffect
        }
        loading = true; error = null
        try {
            when (section) {
                "Albums" -> cards = details.subscribedAlbums().map { album -> DesktopLibraryCard(album.id, album.name, album.artistText, album.artworkUrl) { MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Album(album.id, album.name)) } }
                "Artists" -> cards = details.subscribedArtists().map { artist -> DesktopLibraryCard(artist.id, artist.name, artist.aliases.joinToString(" / "), artist.artworkUrl) { MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Artist(artist.id)) } }
                "Podcasts" -> cards = universal.subscribedPodcasts().map { value -> DesktopLibraryCard(value.id, value.name, value.host?.nickname.orEmpty(), value.artworkUrl) { podcast = value } }
                else -> {
                    if (section == "History") {
                        songs = client.recentSongs()
                    } else {
                        session.refreshProfile()
                        currentCoroutineContext().ensureActive()
                        if (NeteaseSessionStore.revision.value != revision || NeteaseSessionStore.readCookie() != cookie) {
                            throw CancellationException("Library account changed")
                        }
                        val user = session.profile?.userId
                            ?: throw java.io.IOException(session.errorMessage ?: desktopString("ui.desktop.library.sign_in_message"))
                        if (section == "Playlists") {
                            cards = client.userPlaylists(user).map { value -> DesktopLibraryCard(value.id, value.name, value.creatorName, value.coverUrl) { playlist = value } }
                        } else songs = client.likedSongs(user)
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    when {
        section == "Cloud" -> MeloXDesktopCloud(onLogin)
        section == "Downloads" -> MeloXDesktopDownloads()
        playlist != null -> PlaylistDetailScreen(requireNotNull(playlist), onBack = { playlist = null })
        podcast != null -> MeloXDesktopPodcastDetail(universal, requireNotNull(podcast)) { podcast = null }
        section in setOf("Songs", "History") -> DesktopLibrarySongs(title, songs, loading, error, { refresh++ }, if (session.isLoggedIn) null else onLogin, tag = "desktop-library:$section")
        else -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).desktopVerticalScroll().padding(horizontal = 34.dp, vertical = 28.dp).testTag("desktop-library:$section"), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(desktopString(title), Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                if (section == "Playlists") DesktopDialogButton("ui.playlists.create.title", session.isLoggedIn, "desktop-library-create") { creating = true }
            }
            when {
                loading -> Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                cards.isEmpty() -> Column(Modifier.fillMaxWidth().heightIn(min = 340.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                    Text(desktopString(if (session.isLoggedIn) "ui.desktop.library.empty" else "ui.desktop.library.sign_in_title"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(error ?: desktopString(if (session.isLoggedIn) "ui.desktop.library.empty.message" else "ui.desktop.library.sign_in_message"))
                    if (!session.isLoggedIn) DesktopDialogButton("ui.common.login", onClick = onLogin)
                    else if (error != null) DesktopDialogButton("ui.common.retry") { refresh++ }
                }
                else -> BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val count = floor((maxWidth.value + 20) / 165).toInt().coerceAtLeast(1)
                    val width = ((maxWidth.value - (count - 1) * 20) / count).coerceAtMost(205f).dp
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { cards.chunked(count).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { card -> DesktopHomeMediaCard(card.title, card.subtitle, card.artwork, width, "desktop-library-card:${card.id}", onOpen = card.open, isCircular = section == "Artists") }
                    } } }
                }
            }
            Spacer(Modifier.height(70.dp))
        }
    }
    if (creating) DesktopPlaylistCreation(true, busy, error, { if (!busy) creating = false }) { name, private ->
        busy = true
        scope.launch {
            try { operations.createPlaylist(name, private); creating = false; refresh++ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
            finally { busy = false }
        }
    }
}
