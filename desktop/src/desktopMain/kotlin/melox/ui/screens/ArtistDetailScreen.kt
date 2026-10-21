package melox.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.network.MeloXAlbumSummary
import melox.network.MeloXArtistDetail
import melox.network.NeteaseCollectionDetailsClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassIconButton
import melox.ui.navigation.MeloXBackHandler

/** Port of 3f877ea ui/collection/MeloXArtistDetailScreen; requests use the real Netease client. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(id: Long, onBack: () -> Unit) {
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.MeloXDesktopArtistDetail(id, onBack)
        return
    }
    val client = remember { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    var detail by remember(id) { mutableStateOf<MeloXArtistDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var followed by remember(id) { mutableStateOf<Boolean?>(null) }
    var followBusy by remember(id) { mutableStateOf(false) }
    var selectedSong by remember(id) { mutableStateOf<SearchSong?>(null) }
    var allSongs by remember(id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var showInfo by remember(id) { mutableStateOf(false) }
    var expanded by remember(id) { mutableStateOf<String?>(null) }
    var album by remember(id) { mutableStateOf<MeloXAlbumSummary?>(null) }
    val sectionBack = remember(id) { Animatable(0f) }
    val albumBack = remember(id) { Animatable(0f) }
    MeloXBackHandler(enabled = true, priority = 10, onBack = onBack)
    fun closeSection() { scope.launch { sectionBack.animateTo(1f, tween(160)); expanded = null; sectionBack.snapTo(0f) } }
    fun closeAlbum() { scope.launch { albumBack.animateTo(1f, tween(160)); album = null; albumBack.snapTo(0f) } }
    MeloXBackHandler(enabled = album != null || expanded != null || showInfo, priority = 20) {
        when { showInfo -> showInfo = false; album != null -> closeAlbum(); else -> closeSection() }
    }
    LaunchedEffect(id) {
        try { detail = client.artistDetail(id); followed = detail?.followed }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "歌手加载失败" }
        loading = false
    }
    LaunchedEffect(id, detail) {
        if (detail == null || allSongs.isNotEmpty()) return@LaunchedEffect
        val collected = mutableListOf<SearchSong>()
        while (collected.size < 500) {
            val page = try { client.artistSongs(id, 100, collected.size) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { break }
            collected += page
            if (page.size < 100) break
        }
        allSongs = collected
    }
    val groups = detail?.albums.orEmpty().groupBy(::artistAlbumSection)
    val pageFocus = remember { FocusRequester() }
    LaunchedEffect(showInfo) { if (!showInfo) pageFocus.requestFocus() }
    Box(Modifier.fillMaxSize().focusRequester(pageFocus).focusable().background(MaterialTheme.colorScheme.background).testTag("artist-detail").semantics {
        stateDescription = when { detail != null -> "loaded"; loading -> "loading"; else -> "error" }
    }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 236.dp)) {
            detail?.let { artist ->
                item("hero") { ArtistHero(artist) }
                if (artist.hotSongs.isNotEmpty()) {
                    item("songs-title") { ArtistSectionTitle("歌曲排行") }
                    item("hot-songs") {
                        val chunks = artist.hotSongs.take(20).chunked(5)
                        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(chunks.size) { index ->
                                Column(Modifier.fillParentMaxWidth(.9f)) {
                                    chunks[index].forEach { song -> ArtistTopSongRow(song,
                                        onPlay = { PlaybackCommands.playQueue(artist.hotSongs, song.id) }, onMore = { selectedSong = song }) }
                                }
                            }
                        }
                    }
                }
                listOf("专辑", "单曲与 EP", "多人合作专辑", "现场与精选集").forEach { title ->
                    val albums = groups[title].orEmpty()
                    if (albums.isNotEmpty()) {
                        item("title-$title") { ArtistSectionTitle(title) { expanded = title } }
                        item("albums-$title") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(albums.take(7), key = { it.id }) { value -> ArtistAlbumCard(value) { album = value } }
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
            if (loading) item { Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            error?.let { message -> item { Text(message, Modifier.padding(20.dp), color = MaterialTheme.colorScheme.error) } }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            MeloXGlassIconButton(MeloXSymbol.ChevronLeft, onBack, Modifier.size(44.dp).testTag("artist-back"), contentDescription = "返回")
            MeloXGlassIconButton(MeloXSymbol.Ellipsis, { showInfo = true }, Modifier.size(44.dp).testTag("artist-info"), contentDescription = "歌手资料")
        }
        expanded?.let { title ->
            Box(Modifier.fillMaxSize().zIndex(10f).background(MaterialTheme.colorScheme.background).graphicsLayer {
                translationX = size.width * sectionBack.value
                scaleX = 1f - .08f * sectionBack.value; scaleY = scaleX; transformOrigin = TransformOrigin(0f, .5f)
            }.testTag("artist-albums-expanded")) {
                Column {
                    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        MeloXGlassIconButton(MeloXSymbol.ChevronLeft, ::closeSection, contentDescription = "返回")
                        Spacer(Modifier.width(12.dp)); Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 236.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(groups[title].orEmpty(), key = { it.id }) { value -> ArtistAlbumCard(value, true) { album = value } }
                    }
                }
            }
        }
        album?.let { value ->
            Box(Modifier.fillMaxSize().zIndex(20f).graphicsLayer {
                translationX = size.width * albumBack.value
                scaleX = 1f - .08f * albumBack.value; scaleY = scaleX; transformOrigin = TransformOrigin(0f, .5f)
            }) { AlbumDetailScreen(value.id.toString(), value.name, ::closeAlbum) }
        }
        selectedSong?.let { song -> MeloXSongActionsOverlay(song, allSongs.ifEmpty { detail?.hotSongs.orEmpty() }, true, { selectedSong = null }) }
    }
    if (showInfo && detail != null) {
        val artist = detail!!
        val sheetFocus = remember { FocusRequester() }
        ModalBottomSheet(modifier = Modifier.testTag("artist-info-sheet"), onDismissRequest = { showInfo = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface, dragHandle = null) {
            LaunchedEffect(Unit) { sheetFocus.requestFocus() }
            Column(Modifier.fillMaxWidth().onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) { showInfo = false; true } else false
            }.focusRequester(sheetFocus).focusable().padding(horizontal = 24.dp).padding(top = 24.dp, bottom = 48.dp).verticalScroll(rememberScrollState())) {
                Text(artist.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    ArtistMetric(artist.musicSize, "单曲"); ArtistMetric(artist.albumSize, "专辑"); ArtistMetric(artist.mvSize, "MV")
                }
                Spacer(Modifier.height(24.dp))
                followed?.let { value ->
                    MeloXGlassButton(onClick = {
                        followBusy = true
                        scope.launch {
                            try { client.setArtistFollowed(id, !value); followed = !value }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { error = failure.message ?: "关注操作失败" }
                            finally { followBusy = false }
                        }
                    }, modifier = Modifier.fillMaxWidth(), enabled = !followBusy,
                        style = if (value) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered) {
                        Text(if (value) "已关注" else "关注", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(24.dp))
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                artist.description?.let { Text(it, fontSize = 15.sp, lineHeight = 22.sp) }
            }
        }
    }
}

private fun artistAlbumSection(album: MeloXAlbumSummary): String {
    val live = album.type == "Live" || album.type == "专辑" && (album.name.contains("Live", true) || album.name.contains("演唱会") || album.name.contains("巡回"))
    val collab = album.artistText.contains(" / ")
    return when {
        album.type == "专辑" && !live && !collab -> "专辑"
        (album.type == "EP" || album.type == "Single") && !live && !collab -> "单曲与 EP"
        album.type == "专辑" && collab && !live -> "多人合作专辑"
        else -> "现场与精选集"
    }
}

@Composable private fun ArtistHero(artist: MeloXArtistDetail) {
    val background = MaterialTheme.colorScheme.background
    Box(Modifier.fillMaxWidth().height(320.dp).testTag("artist-loaded")) {
        MeloXArtworkImage(artist.coverUrl ?: artist.artworkUrl, background, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to background.copy(alpha = .16f), .42f to Color.Transparent, .78f to background.copy(alpha = .86f), 1f to background)))
        Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.Bottom) {
            MeloXArtworkImage(optimizedArtistArtwork(artist.artworkUrl), background, Modifier.size(64.dp).clip(CircleShape))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(artist.name, fontSize = 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (artist.aliases.isNotEmpty()) Text(artist.aliases.joinToString(" · "), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .54f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
@Composable private fun ArtistMetric(value: Int, label: String) {
    Column { Text(value.toString(), fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f)) }
}
@Composable private fun ArtistSectionTitle(title: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(enabled = onClick != null) { onClick?.invoke() }.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        if (onClick != null) MeloXSymbolIcon(MeloXSymbol.ChevronRight, Modifier.padding(start = 6.dp).size(20.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = .5f))
    }
}
@Composable private fun ArtistTopSongRow(song: SearchSong, onPlay: () -> Unit, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        MeloXArtworkImage(song.artworkUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(50.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(song.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        MeloXSymbolIcon(MeloXSymbol.Ellipsis, Modifier.size(32.dp).clickable(onClick = onMore).padding(4.dp).testTag("artist-song-more-${song.id}"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
private fun optimizedArtistArtwork(url: String?): String? = url?.let {
    if (it.contains(".music.126.net") && !it.contains("param=")) "$it${if ('?' in it) '&' else '?'}param=500y500" else it
}
@Composable private fun ArtistAlbumCard(album: MeloXAlbumSummary, fillWidth: Boolean = false, onClick: () -> Unit) {
    Column((if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(116.dp)).clickable(onClick = onClick).testTag("artist-album-${album.id}")) {
        MeloXArtworkImage(optimizedArtistArtwork(album.artworkUrl), MaterialTheme.colorScheme.surfaceVariant,
            (if (fillWidth) Modifier.fillMaxWidth().aspectRatio(1f) else Modifier.size(116.dp)).clip(RoundedCornerShape(10.dp)), contentDescription = album.name)
        Spacer(Modifier.height(7.dp))
        Text(album.name, fontWeight = FontWeight.Medium, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
        Text(album.artistText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
