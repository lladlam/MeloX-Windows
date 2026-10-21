package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.lyrics.LyricLine
import melox.model.SearchSong
import melox.network.*
import melox.playback.PlaybackCommands
import melox.ui.screens.MeloXArtworkImage

/** DesktopSongDetailView: collection header, 420pt picker and three desktop content panes. */
@Composable
internal fun MeloXDesktopSongDetail(id: Long, onBack: () -> Unit) {
    val cookie = remember { { NeteaseSessionStore.readCookie() } }
    var song by remember(id) { mutableStateOf<SearchSong?>(null) }
    var similar by remember(id) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var lyrics by remember(id) { mutableStateOf<List<LyricLine>>(emptyList()) }
    var comments by remember(id) { mutableStateOf<List<MeloXMusicComment>>(emptyList()) }
    var loading by remember(id) { mutableStateOf(true) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var lyricsError by remember(id) { mutableStateOf<String?>(null) }
    var similarError by remember(id) { mutableStateOf<String?>(null) }
    var commentsError by remember(id) { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var tab by remember(id) { mutableIntStateOf(0) }
    var selectedSong by remember { mutableStateOf<SearchSong?>(null) }
    var menuAnchor by remember { mutableStateOf<IntOffset?>(null) }
    var liked by remember(id) { mutableStateOf<Boolean?>(null) }
    var favoriteBusy by remember(id) { mutableStateOf(false) }
    var operationError by remember(id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val session by NeteaseSessionStore.revision.collectAsState()
    suspend fun readFavorite(): Boolean {
        check(NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())) { desktopString("ui.desktop.sidebar.not_signed_in") }
        val user = NeteaseSearchClient(cookieProvider = cookie).accountProfile().userId
        return withContext(Dispatchers.IO) { id in NeteaseLibraryClient(cookie).likedSongIdsBlocking(user) }
    }
    LaunchedEffect(id, session) {
        liked = null
        try { liked = readFavorite() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* Keep unknown until a real read succeeds. */ }
    }
    val playback by melox.player.AudioPlayer.state.collectAsState()
    LaunchedEffect(id, revision) {
        loading = true
        error = null
        lyricsError = null; similarError = null; commentsError = null
        try {
            song = requireNotNull(NeteaseUniversalSearchClient(cookieProvider = cookie).songDetail(id)) { desktopString("ui.error.unknown") }
            coroutineScope {
                val l = async { runCatching { NeteaseSearchClient(cookieProvider = cookie).lyrics(id).lines }.getOrElse {
                    if (it is CancellationException) throw it
                    lyricsError = it.message ?: desktopString("ui.error.unknown"); emptyList()
                } }
                val s = async { runCatching { withContext(Dispatchers.IO) { NeteaseLibraryClient(cookie).similarSongsBlocking(id, 20) } }.getOrElse {
                    if (it is CancellationException) throw it
                    similarError = it.message ?: desktopString("ui.error.unknown"); emptyList()
                } }
                val c = async { runCatching { NeteaseSocialExtrasClient(cookie).songComments(id, limit = 40).let { it.hotComments + it.comments }.distinctBy { it.id } }.getOrElse {
                    if (it is CancellationException) throw it
                    commentsError = it.message ?: desktopString("ui.error.unknown"); emptyList()
                } }
                lyrics = l.await(); similar = s.await(); comments = c.await()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    MeloXDesktopCollectionDetail(
        kind = desktopString("ui.common.songs"), title = song?.name ?: desktopString("ui.common.songs"),
        subtitle = song?.artists,
        metadata = song?.let { listOf(it.album, "${it.durationMs / 60000}:${(it.durationMs / 1000 % 60).toString().padStart(2, '0')}").filter(String::isNotBlank).joinToString(desktopString("ui.common.metadata_separator")) },
        description = null, artworkUrl = song?.artworkUrl, songs = emptyList(), hasSongs = song != null,
        loading = loading && song == null, error = error, playingId = null, isPlaying = playback.isPlaying,
        onBack = onBack, onPlayAll = { song?.let { PlaybackCommands.playQueue(listOf(it), it.id) } },
        onShuffle = { song?.let { PlaybackCommands.playQueue(listOf(it), it.id) } },
        isFavorite = liked == true, favoriteEnabled = !favoriteBusy,
        onFavorite = {
            if (!favoriteBusy) {
                favoriteBusy = true
                scope.launch {
                    try {
                        val desired = !(liked ?: readFavorite())
                        NeteaseMusicOperationsClient(cookie).setSongLiked(id, desired)
                        liked = desired
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { operationError = failure.message ?: desktopString("ui.error.favorite_failed") }
                    finally { favoriteBusy = false }
                }
            }
        },
        onDownload = { song?.let { melox.download.MeloXDownloadStore.instance().start(it, melox.audio.MusicQualityPreferences.read()) } },
        onRetry = { revision++ }, onSong = { PlaybackCommands.playQueue(listOf(it), it.id) }, tag = "desktop-song-detail",
        extraContent = {
            item("picker") {
                Row(Modifier.padding(top = 28.dp, bottom = 28.dp).widthIn(max = 420.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f))
                    .padding(2.dp).testTag("desktop-song-tabs")) {
                    listOf("ui.song.detail.title", "ui.common.lyrics", "ui.comments.title").forEachIndexed { index, key ->
                        Box(Modifier.weight(1f).height(25.dp).clip(RoundedCornerShape(4.dp))
                            .background(if (tab == index) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
                            .testTag("desktop-song-tab:$index").clickable { tab = index }, contentAlignment = Alignment.Center) {
                            Text(desktopString(key), fontSize = 12.sp)
                        }
                    }
                }
            }
            val paneError = when (tab) { 0 -> similarError; 1 -> lyricsError; else -> commentsError }
            when {
                loading -> item("content-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
                }
                paneError != null -> item("content-error") {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(paneError, color = MaterialTheme.colorScheme.error)
                        DesktopDialogButton("ui.common.retry", onClick = { revision++ })
                    }
                }
                else -> when (tab) {
                0 -> {
                    item("similar-title") { Text(desktopString("ui.home.action.similar_songs"), Modifier.padding(bottom = 18.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    itemsIndexed(similar, key = { _, value -> value.id }) { index, value ->
                        DesktopCollectionTrackRow(index + 1, value, false, false, true,
                            { PlaybackCommands.playQueue(similar, value.id) }, { menuAnchor = it; selectedSong = value })
                    }
                }
                1 -> if (lyrics.isEmpty()) {
                    item("no-lyrics") { DesktopSongEmpty("ui.desktop.lyrics.unavailable") }
                } else itemsIndexed(lyrics) { index, line ->
                    Column(Modifier.fillMaxWidth().testTag("desktop-song-lyric:$index").clickable {
                        song?.let { value -> PlaybackCommands.playQueue(listOf(value), value.id, startPositionMs = line.timeMs) }
                    }.padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        line.translation?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                2 -> if (comments.isEmpty()) {
                    item("no-comments") { DesktopSongEmpty("ui.comments.empty") }
                } else itemsIndexed(comments, key = { _, value -> value.id }) { _, comment ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp).testTag("desktop-song-comment:${comment.id}"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MeloXArtworkImage(comment.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(36.dp).clip(CircleShape))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row { Text(comment.user, Modifier.weight(1f), fontWeight = FontWeight.SemiBold); Text(comment.timeText, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                            SelectionContainer { Text(comment.content) }
                            if (comment.likedCount > 0) Text(comment.likedCount.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(thickness = .5.dp)
                }
            }
            }
        },
    )
    selectedSong?.let { value -> MeloXDesktopSongMenu(value, { selectedSong = null }, menuAnchor) }
    operationError?.let { message -> DesktopMessageDialog("ui.error.favorite_failed", message, { operationError = null }) }
}

@Composable
private fun DesktopSongEmpty(key: String) {
    Box(Modifier.fillMaxWidth().heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
        Text(desktopString(key), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
