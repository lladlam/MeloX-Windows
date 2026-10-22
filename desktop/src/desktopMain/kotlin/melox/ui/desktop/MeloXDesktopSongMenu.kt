package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import melox.ui.input.BasicTextField
import androidx.compose.foundation.focusable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.input.key.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import melox.account.NeteaseSessionStore
import melox.audio.MusicQualityPreferences
import melox.download.MeloXDownloadPlaylistRef
import melox.download.MeloXDownloadStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.music.provider.*
import melox.network.NeteaseMusicOperationsClient
import melox.network.NeteaseSearchClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.MeloXCollectionLaunchBus
import melox.ui.navigation.MeloXCollectionTarget
import melox.ui.navigation.MeloXSearchLaunchBus
import melox.ui.navigation.songShareUrl
import melox.network.MeloXSearchKind
import melox.ui.theme.MeloXDesktopTextFontFamily

/** DesktopTrackRow.songMenuContent, independent of the Android song-action sheet. */
@Composable
internal fun MeloXDesktopSongMenu(
    song: SearchSong,
    onDismiss: () -> Unit,
    anchor: IntOffset? = null,
    sourcePlaylist: MeloXDownloadPlaylistRef? = null,
    sourceOwnedPlaylistId: Long? = null,
    sourceProviderPlaylist: MusicPlaylistSummary? = null,
    onSourcePlaylistChanged: (() -> Unit)? = null,
) = key(song) {
    val scope = rememberCoroutineScope()
    val cookie = remember { { NeteaseSessionStore.readCookie() } }
    val operations = remember { NeteaseMusicOperationsClient(cookie) }
    val track = song.providerTrack
    val id = song.neteaseSongId
    val provider = remember(track?.id?.source) { track?.let { MeloXMusicProviders.create()[it.id.source] } }
    val writer = provider as? PlaylistWriteCapability
    val sync = provider as? PlaylistSyncCapability
    val favoriteReader = provider as? FavoriteStateCapability
    val favoriteWriter = provider as? FavoriteCapability
    val loggedIn = NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())
    var owned by remember { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var writable by remember { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var busy by remember { mutableStateOf(false) }
    var loadingPlaylists by remember { mutableStateOf(true) }
    var playlistError by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(true) }
    var addOpen by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    val submenuFocus = remember { FocusRequester() }
    LaunchedEffect(menuOpen, addOpen) {
        if (menuOpen) {
            withFrameNanos { }
            if (addOpen) submenuFocus.requestFocus() else menuFocus.requestFocus()
        }
    }

    fun operation(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
            finally { busy = false }
        }
    }
    suspend fun readFavorite(): Boolean = if (id != null) {
        check(loggedIn) { desktopString("ui.desktop.sidebar.not_signed_in") }
        val user = NeteaseSearchClient(cookieProvider = cookie).accountProfile().userId
        withContext(Dispatchers.IO) { id in NeteaseLibraryClient(cookie).likedSongIdsBlocking(user) }
    } else requireNotNull(favoriteReader).isFavorite(requireNotNull(track))

    LaunchedEffect(song) {
        if (id != null && loggedIn || favoriteReader != null) {
            try { liked = readFavorite() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Unknown is not an invented unliked state. Click retries the real reader. */ }
        }
    }
    suspend fun loadPlaylists() {
        loadingPlaylists = true
        playlistError = null
        try {
            if (id != null && loggedIn) {
                val user = NeteaseSearchClient(cookieProvider = cookie).accountProfile().userId
                owned = withContext(Dispatchers.IO) { NeteaseLibraryClient(cookie).userPlaylistsBlocking(user).filter { it.creatorUserId == user } }
            } else if (writer != null) {
                // Read every actual writable page; do not silently hide playlists after the first 50.
                val playlists = mutableListOf<MusicPlaylistSummary>()
                var page = 1
                do {
                    val result = writer.writablePlaylists(page++, 50)
                    playlists += result.items
                } while (result.hasMore)
                writable = playlists.distinctBy { it.id }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { playlistError = failure.message ?: desktopString("ui.error.unknown") }
        finally { loadingPlaylists = false }
    }
    LaunchedEffect(addOpen) { if (addOpen) loadPlaylists() }

    if (menuOpen) Popup(
        popupPositionProvider = remember(anchor) { DesktopSongMenuPosition(anchor) },
        onDismissRequest = { if (!busy) onDismiss() },
        properties = PopupProperties(focusable = true),
    ) {
        DesktopMenuSurface(Modifier.width(266.dp).testTag("desktop-song-menu")
            .focusRequester(menuFocus).onPreviewKeyEvent {
                if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyUp) onDismiss(); true } else false
            }.focusable()) {
            DesktopSongMenuItem("ui.player.play_next", MeloXSymbol.NextTrack, "next", !busy) { PlaybackCommands.playNext(song); onDismiss() }
            DesktopSongMenuItem("ui.player.add_to_queue", MeloXSymbol.Badge, "queue", !busy) { PlaybackCommands.addToQueue(song); onDismiss() }
            Box {
                DesktopSongMenuItem("ui.playlists.add_to", MeloXSymbol.Badge, "add-to", !busy, submenu = true, onHover = { addOpen = true }) { addOpen = true }
                DropdownMenu(addOpen, { addOpen = false }, modifier = Modifier.width(264.dp).testTag("desktop-song-add-menu")
                    .focusRequester(submenuFocus).onPreviewKeyEvent {
                        if (it.key == Key.Escape) { if (it.type == KeyEventType.KeyUp) addOpen = false; true } else false
                    }.focusable()) {
                    DesktopSongMenuItem("ui.playlists.create.title", MeloXSymbol.Plus, "create", if (id != null) loggedIn else sync != null) {
                        addOpen = false; menuOpen = false; creating = true
                    }
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), thickness = .5.dp)
                    when {
                        loadingPlaylists -> Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp) }
                        playlistError != null -> {
                            Text(requireNotNull(playlistError), Modifier.padding(horizontal = 12.dp, vertical = 5.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            DesktopSongMenuItem("ui.common.retry", MeloXSymbol.Refresh, "retry-playlists") { scope.launch { loadPlaylists() } }
                        }
                        owned.isEmpty() && writable.isEmpty() -> Text(desktopString("ui.playlists.none_available"), Modifier.padding(12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    owned.forEach { playlist -> DesktopSongMenuItem(null, null, "playlist:${playlist.id}", !busy, label = playlist.name) {
                        operation { operations.addSongToPlaylist(requireNotNull(id), playlist.id); onSourcePlaylistChanged?.invoke(); onDismiss() }
                    } }
                    writable.forEach { playlist -> DesktopSongMenuItem(null, null, "provider-playlist:${playlist.id}", !busy, label = playlist.title) {
                        operation { requireNotNull(writer).addTrackToPlaylist(requireNotNull(track), playlist); onSourcePlaylistChanged?.invoke(); onDismiss() }
                    } }
                }
            }
            if (sourceOwnedPlaylistId != null && id != null || sourceProviderPlaylist != null && sync != null) {
                DesktopSongMenuItem("ui.playlists.remove_song", MeloXSymbol.Trash, "remove", !busy) { menuOpen = false; removing = true }
            }
            DesktopMenuDivider()
            DesktopSongMenuItem(if (liked == true) "ui.song.unlike" else "ui.song.like", if (liked == true) MeloXSymbol.StarFill else MeloXSymbol.Star,
                "favorite", !busy && (id != null || favoriteReader != null && favoriteWriter != null)) {
                operation {
                    val desired = !(liked ?: readFavorite())
                    if (id != null) operations.setSongLiked(id, desired) else requireNotNull(favoriteWriter).setFavorite(requireNotNull(track), desired)
                    liked = desired; onDismiss()
                }
            }
            DesktopSongMenuItem("ui.common.download", MeloXSymbol.Download, "download", !busy && id != null) {
                MeloXDownloadStore.instance().start(song, MusicQualityPreferences.read(), sourcePlaylist); onDismiss()
            }
            DesktopMenuDivider()
            DesktopSongMenuItem("ui.song.view_information", MeloXSymbol.Info, "information") {
                song.neteaseSongId?.let { MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Song(it)) }
                    ?: MeloXSearchLaunchBus.post(song.name, MeloXSearchKind.Songs, track?.id?.source ?: MusicSource.Netease)
                onDismiss()
            }
            if (song.artists.isNotBlank()) DesktopSongMenuItem("ui.artist.go_to_generic", MeloXSymbol.Person, "artist") {
                val artistId = song.artistId ?: track?.artists?.firstOrNull()?.id?.value?.toLongOrNull()
                if (id != null && artistId != null) MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Artist(artistId))
                else MeloXSearchLaunchBus.post(song.artists, MeloXSearchKind.Artists, track?.id?.source ?: MusicSource.Netease)
                onDismiss()
            }
            if (song.album.isNotBlank()) DesktopSongMenuItem("ui.common.album", MeloXSymbol.Library, "album") {
                val albumId = song.albumId ?: track?.album?.id?.value?.toLongOrNull()
                if (id != null && albumId != null) MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Album(albumId, song.album))
                else MeloXSearchLaunchBus.post(song.album, MeloXSearchKind.Albums, track?.id?.source ?: MusicSource.Netease)
                onDismiss()
            }
            DesktopSongMenuItem("ui.common.share", MeloXSymbol.Share, "share", songShareUrl(song) != null) {
                operation {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(requireNotNull(songShareUrl(song))), null)
                    onDismiss()
                }
            }
        }
    }
    if (creating) DesktopPlaylistCreation(
        privateSupported = id != null, busy = busy, error = error,
        onDismiss = { if (!busy) onDismiss() },
    ) { name, private ->
        operation {
            // Mac's create action creates a playlist; adding to it is a separate menu action.
            if (id != null) operations.createPlaylist(name, private) else requireNotNull(sync).createPlaylist(name)
            onSourcePlaylistChanged?.invoke(); onDismiss()
        }
    }
    if (removing) DesktopMessageDialog("ui.playlists.remove_song", desktopFormat("ui.playlists.remove_song.message", song.name),
        onDismiss = { if (!busy) onDismiss() }, confirmKey = "ui.playlists.remove_song", enabled = !busy, onConfirm = {
            operation {
                if (id != null && sourceOwnedPlaylistId != null) operations.removeSongFromPlaylist(id, sourceOwnedPlaylistId)
                else requireNotNull(sync).removeTrackFromPlaylist(requireNotNull(track), requireNotNull(sourceProviderPlaylist))
                onSourcePlaylistChanged?.invoke(); onDismiss()
            }
        })
    if (error != null && !creating) DesktopMessageDialog("ui.library.error.title", requireNotNull(error), onDismiss = { error = null })
}

private class DesktopSongMenuPosition(private val position: IntOffset?) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val point = position ?: IntOffset(anchorBounds.right - popupContentSize.width - 16, anchorBounds.top + 64)
        return IntOffset(point.x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            point.y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
private fun DesktopMenuSurface(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    val focus = LocalFocusManager.current
    Surface(modifier, shape = shape, color = MaterialTheme.colorScheme.surface, shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .16f))) {
        CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily)) {
            Column(Modifier.heightIn(max = 600.dp).desktopVerticalScroll().padding(5.dp).onPreviewKeyEvent {
                when (it.key) {
                    Key.DirectionDown, Key.DirectionUp -> {
                        if (it.type == KeyEventType.KeyDown) focus.moveFocus(if (it.key == Key.DirectionDown) FocusDirection.Next else FocusDirection.Previous)
                        true
                    }
                    else -> false
                }
            }, content = content)
        }
    }
}

@Composable
private fun DesktopMenuDivider() = HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 6.dp), thickness = .5.dp)

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
private fun DesktopSongMenuItem(key: String?, symbol: MeloXSymbol?, tag: String, enabled: Boolean = true,
    label: String = key?.let(::desktopString).orEmpty(), submenu: Boolean = false, onHover: (() -> Unit)? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (hovered && enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f)
    Row(Modifier.fillMaxWidth().heightIn(min = 26.dp).clip(RoundedCornerShape(4.dp))
        .background(if (hovered && enabled) Color(0xFFDC0011) else Color.Transparent)
        .testTag("desktop-song-$tag").hoverable(interaction)
        // Open on real pointer motion, not recomposition-induced hover re-entry
        // after Escape closes the child popup under a stationary pointer.
        .onPointerEvent(PointerEventType.Move) { if (enabled) onHover?.invoke() }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.width(16.dp), contentAlignment = Alignment.Center) { if (symbol != null) MeloXSymbolIcon(symbol, size = 13, color = color) }
        Text(label, Modifier.weight(1f), color = color, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (submenu) MeloXSymbolIcon(MeloXSymbol.ChevronRight, size = 10, color = color)
    }
}

@Composable
internal fun DesktopPlaylistCreation(privateSupported: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var private by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.size(420.dp, 280.dp).testTag("desktop-playlist-create"), shape = RoundedCornerShape(10.dp)) {
            CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
                color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)) {
                Column {
                    Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DesktopDialogButton("ui.common.cancel", !busy, onClick = onDismiss)
                        Text(desktopString("ui.playlists.create.title"), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                        DesktopDialogButton("ui.playlists.create.action", !busy && name.isNotBlank(), "desktop-playlist-create-confirm", prominent = true) { onCreate(name.trim(), private) }
                    }
                    HorizontalDivider(thickness = .5.dp)
                    Column(Modifier.padding(20.dp).clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)).padding(horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(desktopString("ui.playlists.create.name"))
                            BasicTextField(name, { name = it }, enabled = !busy, singleLine = true,
                                textStyle = LocalTextStyle.current, modifier = Modifier.weight(1f).testTag("desktop-playlist-create-name"),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface))
                        }
                        HorizontalDivider(thickness = .5.dp)
                        Row(Modifier.fillMaxWidth().height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(desktopString("ui.playlists.create.private"), Modifier.weight(1f))
                            Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(if (private) Color(0xFFDC0011) else Color.Transparent)
                                .border(.75.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .35f), RoundedCornerShape(3.dp))
                                .testTag("desktop-playlist-create-private").toggleable(value = private, enabled = !busy && privateSupported, role = Role.Checkbox, onValueChange = { private = it }), contentAlignment = Alignment.Center) {
                                if (private) MeloXSymbolIcon(MeloXSymbol.Checkmark, size = 9, color = Color.White)
                            }
                        }
                    }
                    error?.let { Text(it, Modifier.padding(horizontal = 20.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.error, maxLines = 3) }
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).size(16.dp), strokeWidth = 1.5.dp)
                }
            }
        }
    }
}

@Composable
internal fun DesktopDialogButton(key: String, enabled: Boolean = true, tag: String = "desktop-dialog:$key", prominent: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.height(23.dp).widthIn(min = 64.dp).clip(shape)
        .background(if (prominent && enabled) Color(0xFFDC0011) else MaterialTheme.colorScheme.onSurface.copy(alpha = .065f))
        .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .13f), shape).testTag(tag)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 9.dp), contentAlignment = Alignment.Center) {
        Text(desktopString(key), fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp,
            color = if (prominent && enabled) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
    }
}

@Composable
internal fun DesktopMessageDialog(titleKey: String, message: String, onDismiss: () -> Unit,
    confirmKey: String = "ui.common.ok", enabled: Boolean = true, onConfirm: (() -> Unit)? = null) {
    DesktopTextDialog(desktopString(titleKey), message, onDismiss, confirmKey, enabled, onConfirm)
}

/** Same independent Desktop alert for a server-provided literal title. */
@Composable
internal fun DesktopTextDialog(title: String, message: String, onDismiss: () -> Unit,
    confirmKey: String = "ui.common.ok", enabled: Boolean = true, onConfirm: (() -> Unit)? = null) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.width(360.dp).testTag("desktop-message-dialog"), shape = RoundedCornerShape(10.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(title, fontFamily = MeloXDesktopTextFontFamily, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(message, fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (onConfirm != null) DesktopDialogButton("ui.common.cancel", enabled, onClick = onDismiss)
                    DesktopDialogButton(confirmKey, enabled, prominent = true, onClick = onConfirm ?: onDismiss)
                }
            }
        }
    }
}
