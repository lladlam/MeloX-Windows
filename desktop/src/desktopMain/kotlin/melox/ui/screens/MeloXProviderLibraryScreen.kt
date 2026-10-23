package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.music.model.*
import melox.music.provider.*
import melox.settings.MeloXContentLayout
import melox.settings.MeloXSettingsRuntime
import melox.ui.desktop.DesktopHomeMediaCard
import melox.ui.desktop.desktopPromotedDragScroll
import melox.ui.navigation.MeloXDetailOverlay
import melox.ui.foundation.MeloXIosTopBar

private data class ProviderLibraryEntry(
    val key: String, val title: String, val subtitle: String, val artwork: String?,
    val playlist: MusicPlaylistSummary? = null, val album: MusicAlbumSummary? = null, val artist: MusicArtistSummary? = null,
)

/** Source-native collections; data is shared, phone and Desktop presentation stay independent. */
@Composable
internal fun MeloXProviderLibraryScreen(source: MusicSource, requestedPage: String?, onLogin: () -> Unit) {
    val revision = when (source) {
        MusicSource.Spotify -> melox.provider.spotify.SpotifySessionStore.revision.collectAsState().value
        MusicSource.YouTubeMusic -> melox.provider.youtubemusic.YouTubeSessionStore.revision.collectAsState().value
        else -> 0L
    }
    key(source, revision) {
        val textStyle = if (MeloXSettingsRuntime.desktopLayoutEnabled) androidx.compose.ui.text.TextStyle(
            fontFamily = melox.ui.theme.MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            else LocalTextStyle.current
        CompositionLocalProvider(LocalTextStyle provides textStyle) { ProviderLibraryContent(source, requestedPage, onLogin) }
    }
}

@Composable
private fun ProviderLibraryContent(source: MusicSource, requestedPage: String?, onLogin: () -> Unit) {
    val provider = remember(source) { MeloXMusicProviders.create().require(source) }
    val pages = MeloXContentLayout.libraryPages(source)
    var section by remember(source) { mutableStateOf(MeloXContentLayout.initialLibraryPage(source)) }
    LaunchedEffect(source, requestedPage) { requestedPage?.takeIf { it in pages }?.let { section = it } }
    LaunchedEffect(source, section) { MeloXContentLayout.rememberLibraryPage(source, section) }
    var entries by remember(source, section) { mutableStateOf<List<ProviderLibraryEntry>>(emptyList()) }
    var tracks by remember(source, section) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember(source, section) { mutableStateOf(true) }
    var error by remember(source, section) { mutableStateOf<String?>(null) }
    var loggedIn by remember(source) { mutableStateOf(source == MusicSource.Local) }
    var page by remember(source, section) { mutableIntStateOf(1) }
    var more by remember(source, section) { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var detail by remember(source, section) { mutableStateOf<ProviderLibraryEntry?>(null) }
    var menuSong by remember(source, section) { mutableStateOf<melox.model.SearchSong?>(null) }
    var menuAnchor by remember(source, section) { mutableStateOf<IntOffset?>(null) }
    val queue by melox.playback.PlaybackCommands.queue.collectAsState()
    val playback by melox.player.AudioPlayer.state.collectAsState()
    val scope = key(source, section) { rememberCoroutineScope() }
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    val title = when (section) { "Songs" -> "歌曲"; "Albums" -> "收藏专辑"; "Artists" -> "关注歌手"; else -> "歌单" }
    suspend fun load(next: Int) {
        loading = true; error = null
        try {
                if (source != MusicSource.Local) loggedIn = withContext(Dispatchers.IO) { (provider as? UserLibraryCapability)?.accountSummary() } != null
                if (!loggedIn) { entries = emptyList(); tracks = emptyList(); more = false; return }
                val size = 50
                when (section) {
                    "Songs" -> {
                        val result = withContext(Dispatchers.IO) { (provider as LocalAggregationCapability).aggregationTracks(next, size) }
                        tracks = (if (next == 1) result.items else tracks + result.items).distinctBy { it.id }
                        more = result.hasMore
                    }
                    "Albums" -> {
                        val result = withContext(Dispatchers.IO) { (provider as LibraryCollectionCapability).savedAlbums(next, size) }
                        val values = result.items.map { ProviderLibraryEntry(it.id.value, it.title, it.artists.joinToString { a -> a.name }, it.artworkUrl, album = it) }
                        entries = (if (next == 1) values else entries + values).distinctBy { it.key }; more = result.hasMore
                    }
                    "Artists" -> {
                        val result = withContext(Dispatchers.IO) { (provider as LibraryCollectionCapability).followedArtists(next, size) }
                        val values = result.items.map { ProviderLibraryEntry(it.id.value, it.name, source.displayName, it.artworkUrl, artist = it) }
                        entries = (if (next == 1) values else entries + values).distinctBy { it.key }; more = result.hasMore
                    }
                    else -> {
                        val result = withContext(Dispatchers.IO) {
                            if (source == MusicSource.Local) (provider as PlaylistWriteCapability).writablePlaylists(next, size)
                            else (provider as UserLibraryCapability).userPlaylists(next, size)
                        }
                        val values = result.items.map { ProviderLibraryEntry(it.id.value, it.title, it.creatorName.orEmpty(), it.artworkUrl, playlist = it) }
                        entries = (if (next == 1) values else entries + values).distinctBy { it.key }; more = result.hasMore
                    }
                }
                page = next
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "音乐库读取失败" }
        finally { loading = false }
    }
    LaunchedEffect(source, section, reload) { load(1) }
    var creating by remember(source) { mutableStateOf(false) }
    var createName by remember(source) { mutableStateOf("") }
    var createBusy by remember(source) { mutableStateOf(false) }
    var createError by remember(source) { mutableStateOf<String?>(null) }
    val writer = provider as? PlaylistSyncCapability
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .testTag(if (desktop) "desktop-provider-library:${source.name}" else "mobile-provider-library:${source.name}")
        .semantics { stateDescription = "$section;count=${if (section == "Songs") tracks.size else entries.size};more=$more" }) {
        val scroll = key(source, section) { rememberLazyListState() }
        Column(Modifier.fillMaxSize()) {
        if (!desktop) {
            MeloXIosTopBar("音乐库", actions = {
                if (section == "Playlists" && writer != null && loggedIn) TextButton(
                    { createError = null; creating = true }, Modifier.testTag("provider-library-create")) { Text("新建歌单") }
            })
            MeloXProviderLibraryPicker(section, { section = it }, pages.map { value ->
                value to when (value) { "Albums" -> "专辑"; "Artists" -> "歌手"; "Songs" -> "歌曲"; else -> "歌单" }
            }, Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().then(if (desktop) Modifier.desktopPromotedDragScroll(scroll) else Modifier), state = scroll,
            contentPadding = PaddingValues(horizontal = if (desktop) 34.dp else 0.dp, vertical = if (desktop) 28.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (desktop && section != "Songs") 18.dp else if (desktop) 2.dp else 0.dp)) {
            if (desktop) item("title") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = if (desktop) 32.sp else 28.sp)
                        Text(source.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    if (section == "Playlists" && writer != null && loggedIn) TextButton({ createError = null; creating = true }, Modifier.testTag("provider-library-create")) { Text("新建歌单") }
                }
            }
            if (loading && entries.isEmpty() && tracks.isEmpty()) item { CircularProgressIndicator(Modifier.size(22.dp)) }
            error?.let { message -> item("error") {
                Column { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("provider-library-error"))
                    TextButton({ reload++ }, Modifier.testTag("provider-library-retry")) { Text("重试") } }
            } }
            if (!loading && !loggedIn && error == null) item("login") {
                Text("请先登录 ${source.displayName}")
                TextButton(onLogin, Modifier.testTag("provider-library-login")) { Text("登录") }
            }
            if (!loading && loggedIn && entries.isEmpty() && tracks.isEmpty() && error == null) item("empty") {
                Text("${source.displayName} 暂无$title", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("provider-library-empty"))
            }
            if (desktop && entries.isNotEmpty()) item("grid") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = ((maxWidth.value + 20) / 165).toInt().coerceAtLeast(1)
                    val width = ((maxWidth.value - (columns - 1) * 20) / columns).coerceAtMost(205f).dp
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        entries.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { entry -> DesktopHomeMediaCard(entry.title, entry.subtitle, entry.artwork, width,
                                "provider-library-card:${source.name}:${entry.key}", onOpen = { detail = entry }, isCircular = section == "Artists") }
                        } }
                    }
                }
            } else items(entries, key = { it.key }) { entry ->
                Row(Modifier.fillMaxWidth().height(66.dp).clickable { detail = entry }
                    .testTag("provider-library-card:${source.name}:${entry.key}").padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    MeloXArtworkImage(entry.artwork, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(44.dp).clip(
                        if (section == "Artists") androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(6.dp)))
                    Column(Modifier.padding(start = 14.dp)) { Text(entry.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(entry.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                }
            }
            if (tracks.isNotEmpty()) item("play-all") {
                val playAll = { melox.playback.ProviderPlaybackCommands.playQueue(tracks, tracks.first().id); Unit }
                if (desktop) TextButton(playAll, Modifier.testTag("provider-library-play-all")) { Text("播放全部") }
                else Box(Modifier.testTag("provider-library-play-all")) { MeloXPlayAllRow(playAll) }
            }
            itemsIndexed(tracks, key = { _, track -> track.id.value }) { index, track ->
                val song = MeloXLegacyUiBridge.track(track)
                Box(Modifier.fillMaxWidth().testTag("provider-library-track:${track.id.value}")) {
                    if (desktop) melox.ui.desktop.DesktopCollectionTrackRow(index + 1, song,
                        queue.songs.getOrNull(queue.index)?.providerTrack?.id == track.id, playback.isPlaying, false,
                        { melox.playback.ProviderPlaybackCommands.playQueue(tracks, track.id) },
                        { menuAnchor = it; menuSong = song }, showsArtwork = true)
                    else Column {
                        MeloXLibraryTrackRow(song) { melox.playback.ProviderPlaybackCommands.playQueue(tracks, track.id) }
                        HorizontalDivider(Modifier.padding(start = 68.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                    }
                }
            }
            if (more) item("more") { TextButton({ if (!loading) scope.launch { load(page + 1) } }, Modifier.testTag("provider-library-more"), enabled = !loading) { Text(if (loading) "加载中…" else "加载更多") } }
            item("clearance") { Spacer(Modifier.height(150.dp)) }
        }
        }
        MeloXDetailOverlay(detail, { detail = null }) { entry ->
            key(source, entry.key, section) {
                if (entry.playlist != null) PlaylistDetailScreen(MeloXLegacyUiBridge.playlist(entry.playlist), onBack = { detail = null })
                else ProviderCollectionDetail(entry.title, entry.subtitle, entry.artwork, load = {
                    withContext(Dispatchers.IO) {
                        if (entry.album != null) (provider as AlbumCapability).albumDetail(entry.album, pageSize = 100).tracks
                        else (provider as ArtistCapability).artistDetail(requireNotNull(entry.artist), pageSize = 100).tracks
                    }
                }, onBack = { detail = null })
            }
        }
    }
    menuSong?.let { melox.ui.desktop.MeloXDesktopSongMenu(it, { menuSong = null }, menuAnchor) }
    if (creating && writer != null) AlertDialog(onDismissRequest = { if (!createBusy) creating = false },
        shape = RoundedCornerShape(if (desktop) 10.dp else 28.dp),
        title = { Text("新建歌单") }, text = { Column {
            OutlinedTextField(createName, { createName = it }, label = { Text("名称") }, modifier = Modifier.testTag("provider-playlist-name"))
            createError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton({
            createBusy = true
            createError = null
            scope.launch {
                try { withContext(Dispatchers.IO) { writer.createPlaylist(createName.trim()) }; creating = false; createName = ""; reload++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { createError = failure.message ?: "歌单创建失败" }
                finally { createBusy = false }
            }
        }, enabled = !createBusy && createName.isNotBlank(), modifier = Modifier.testTag("provider-playlist-create-confirm")) { Text("创建") } },
        dismissButton = { TextButton({ creating = false }, enabled = !createBusy) { Text("取消") } })
}
