package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.network.MeloXArtistDetail
import melox.network.NeteaseCollectionDetailsClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.MeloXCollectionLaunchBus
import melox.ui.navigation.MeloXCollectionTarget
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopDisplayFontFamily
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.floor

/** Mac DesktopArtistDetailView; not the Android artist page or its full-screen sheets. */
@Composable
internal fun MeloXDesktopArtistDetail(id: Long, onBack: () -> Unit) {
    val client = remember { NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var detail by remember(id) { mutableStateOf<MeloXArtistDetail?>(null) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var operationError by remember(id) { mutableStateOf<String?>(null) }
    var followed by remember(id) { mutableStateOf(false) }
    var busy by remember(id) { mutableStateOf(false) }
    var revision by remember(id) { mutableIntStateOf(0) }
    var selected by remember(id) { mutableStateOf<SearchSong?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val queue by PlaybackCommands.queue.collectAsState()
    val currentId = queue.songs.getOrNull(queue.index)?.id
    LaunchedEffect(id, revision) {
        loading = true; error = null
        try { detail = client.artistDetail(id); followed = detail?.followed == true }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily, color = MaterialTheme.colorScheme.onBackground)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-artist-detail")) {
            val artist = detail
            when {
                artist != null -> LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(list).testTag("desktop-artist-scroll"), state = list,
                    verticalArrangement = Arrangement.spacedBy(30.dp)) {
                    item("hero") {
                        Box(Modifier.fillMaxWidth().height(380.dp).clip(RoundedCornerShape(0.dp)).testTag("desktop-artist-hero")) {
                            MeloXArtworkImage(artist.artworkUrl, Color.DarkGray, Modifier.matchParentSize().graphicsLayer {
                                scaleX = 1.38f; scaleY = 1.38f
                                renderEffect = BlurEffect(48.dp.toPx(), 48.dp.toPx(), TileMode.Clamp)
                                alpha = .58f
                            })
                            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .18f), Color.Black.copy(alpha = .62f)))))
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(34.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                MeloXArtworkImage(artist.artworkUrl, Color.DarkGray, Modifier.align(Alignment.CenterHorizontally).size(190.dp).shadow(18.dp, CircleShape).clip(CircleShape).testTag("desktop-artist-avatar"))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(48.dp).clip(CircleShape).background(Color(0xFFFF3B30)).testTag("desktop-artist-play")
                                        .clickable(enabled = artist.hotSongs.isNotEmpty()) { artist.hotSongs.firstOrNull()?.let { PlaybackCommands.playQueue(artist.hotSongs, it.id) } }, contentAlignment = Alignment.Center) {
                                        MeloXSymbolIcon(MeloXSymbol.Play, size = 19, weight = FontWeight.Bold, color = Color.White)
                                    }
                                    DesktopArtistAction(if (followed) MeloXSymbol.Checkmark else MeloXSymbol.Plus,
                                        desktopString(if (followed) "ui.desktop.artist.following" else "ui.desktop.artist.follow"),
                                        "desktop-artist-follow", enabled = !busy, onClick = {
                                        val previous = followed
                                        followed = !previous; busy = true
                                        scope.launch {
                                            try { client.setArtistFollowed(id, !previous) }
                                            catch (cancelled: CancellationException) { followed = previous; throw cancelled }
                                            catch (failure: Exception) { followed = previous; operationError = failure.message ?: desktopString("ui.error.unknown") }
                                            finally { busy = false }
                                        }
                                    })
                                    DesktopArtistAction(MeloXSymbol.Share, null, "desktop-artist-share", onClick = {
                                        runCatching { java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection("https://music.163.com/#/artist?id=$id"), null) }
                                            .onFailure { operationError = it.message ?: desktopString("ui.error.unknown") }
                                    })
                                    Text(artist.name, Modifier.padding(start = 4.dp).weight(1f), fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = MeloXDesktopDisplayFontFamily, color = Color.White)
                                }
                            }
                        }
                    }
                    item("content") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp).padding(bottom = 30.dp), horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(desktopString("ui.desktop.artist.latest_releases"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                BoxWithConstraints(Modifier.fillMaxWidth()) {
                                    val columns = floor((maxWidth.value + 20) / 165).toInt().coerceAtLeast(1)
                                    val width = ((maxWidth.value - (columns - 1) * 20) / columns).coerceAtMost(205f).dp
                                    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
                                        for (row in artist.albums.take(8).chunked(columns)) Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                            row.forEach { album -> DesktopHomeMediaCard(album.name, album.artistText, album.artworkUrl, width,
                                                "desktop-artist-album:${album.id}", onOpen = { MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Album(album.id, album.name)) },
                                                onPlay = { scope.launch {
                                                    try { val songs = client.albumDetail(album.id).songs; songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id) } }
                                                    catch (cancelled: CancellationException) { throw cancelled }
                                                    catch (failure: Exception) { operationError = failure.message ?: desktopString("ui.error.unknown") }
                                                } }) }
                                        }
                                    }
                                }
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(desktopString("ui.artist.popular_songs"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Column(Modifier.fillMaxWidth()) {
                                    artist.hotSongs.take(12).forEachIndexed { index, song ->
                                        DesktopCollectionTrackRow(index + 1, song, currentId == song.id, playback.isPlaying, true,
                                            { PlaybackCommands.playQueue(artist.hotSongs, song.id) }, { anchor = it; selected = song })
                                        Spacer(Modifier.height(1.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                else -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error ?: desktopString("ui.error.unknown"))
                    TextButton(onClick = { revision++ }) { Text(desktopString("ui.common.retry")) }
                }
            }
            Box(Modifier.padding(8.dp).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .7f))
                .testTag("desktop-artist-back").clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                MeloXSymbolIcon(MeloXSymbol.ChevronLeft, size = 13)
            }
        }
    }
    selected?.let { song -> MeloXDesktopSongMenu(song, { selected = null }, anchor) }
    operationError?.let { message -> DesktopMessageDialog("ui.desktop.artist.follow_failed", message, { operationError = null }) }
}

@Composable
private fun DesktopArtistAction(symbol: MeloXSymbol, label: String?, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Row(Modifier.height(24.dp).clip(shape).background(Color.White.copy(alpha = .12f)).border(.5.dp, Color.White.copy(alpha = .3f), shape)
        .testTag(tag).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        MeloXSymbolIcon(symbol, size = 12, color = Color.White.copy(alpha = if (enabled) 1f else .4f))
        label?.let { Text(it, fontSize = 13.sp, color = Color.White.copy(alpha = if (enabled) 1f else .4f)) }
    }
}
