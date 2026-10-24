package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import melox.ui.input.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.model.SearchSong
import melox.music.model.MusicTrack
import melox.music.provider.MeloXLegacyUiBridge
import melox.network.MeloXSearchKind
import melox.network.MeloXSearchMediaItem
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.floor

/**
 * Presentation for DesktopSearchView. Data loading and detail routes stay in
 * SearchScreen; this file owns only the Mac toolbar, scope/kind pickers and
 * the track-row/media-grid bodies.
 */
@Composable
internal fun MeloXDesktopSearchScaffold(
    query: String,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    suggestions: List<String>,
    onSuggestion: (String) -> Unit,
    libraryScope: Boolean,
    onScope: (Boolean) -> Unit,
    kinds: List<MeloXSearchKind>,
    kind: MeloXSearchKind,
    onKind: (MeloXSearchKind) -> Unit,
    loading: Boolean,
    error: String?,
    searched: Boolean,
    songs: List<SearchSong>,
    providerSongs: List<MusicTrack>,
    media: List<MeloXSearchMediaItem>,
    onPlaySong: (SearchSong) -> Unit,
    onPlayProvider: (MusicTrack) -> Unit,
    onOpenMedia: (MeloXSearchMediaItem) -> Unit,
    onSongMenu: (SearchSong, IntOffset?) -> Unit,
    onRetry: () -> Unit,
) {
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-search")) {
            Row(Modifier.fillMaxWidth().testTag("desktop-search-toolbar").padding(horizontal = 34.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                DesktopSearchField(query, onQuery, onSubmit, suggestions, onSuggestion)
                Spacer(Modifier.weight(1f))
                DesktopSearchScope(libraryScope, onScope)
            }
            HorizontalDivider(thickness = .5.dp)
            if (query.isNotEmpty()) {
                DesktopSearchKindPicker(kinds.filter { it != MeloXSearchKind.Users }, kind, onKind)
                HorizontalDivider(thickness = .5.dp)
            }
            Box(Modifier.weight(1f)) {
                when {
                    error != null -> DesktopSearchUnavailable(desktopString("ui.desktop.search.failed"), error, onRetry)
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                    !searched -> DesktopSearchUnavailable(desktopString("ui.desktop.search.empty.title"),
                        desktopString("ui.desktop.search.empty.message"))
                    kind == MeloXSearchKind.Songs && (songs.isNotEmpty() || providerSongs.isNotEmpty()) -> {
                        if (songs.isNotEmpty()) DesktopSearchSongs(songs, onPlaySong, onSongMenu)
                        else DesktopSearchProviderSongs(providerSongs, onPlayProvider)
                    }
                    songs.isEmpty() && providerSongs.isEmpty() && media.isEmpty() -> DesktopSearchUnavailable(
                        desktopString("ui.desktop.search.empty.title"), desktopString("ui.desktop.search.empty.message"))
                    else -> DesktopSearchMediaGrid(media, kind, onOpenMedia)
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchField(value: String, onChange: (String) -> Unit, onSubmit: () -> Unit,
    suggestions: List<String>, onSuggestion: (String) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    var suggestionsDismissed by remember(value) { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box {
        Row(Modifier.width(332.dp).height(36.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MeloXSymbolIcon(MeloXSymbol.Search, size = 13, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            BasicTextField(value, onChange, singleLine = true,
                modifier = Modifier.weight(1f).testTag("search-query").onFocusChanged { focused = it.isFocused },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = LocalTextStyle.current,
                decorationBox = { inner -> Box { if (value.isEmpty()) Text(desktopString("ui.search.prompt"),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 13.sp); inner() } })
        }
        if (focused && !suggestionsDismissed && suggestions.isNotEmpty()) {
            androidx.compose.ui.window.Popup(alignment = Alignment.TopStart,
                offset = IntOffset(0, with(density) { 40.dp.roundToPx() }),
                onDismissRequest = { suggestionsDismissed = true },
                properties = androidx.compose.ui.window.PopupProperties(focusable = false)) {
                Surface(Modifier.width(332.dp).testTag("desktop-search-suggestions"), shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp, border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .18f))) {
                    Column(Modifier.heightIn(max = 260.dp).desktopVerticalScroll().padding(vertical = 4.dp)) {
                        suggestions.forEach { suggestion ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 26.dp).clickable { suggestionsDismissed = true; onSuggestion(suggestion) }
                                .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MeloXSymbolIcon(MeloXSymbol.Search, size = 11, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                                Text(suggestion, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchScope(library: Boolean, onScope: (Boolean) -> Unit) {
    Row(Modifier.width(236.dp).height(26.dp).clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(2.dp)) {
        listOf(false to "ui.common.netease_cloud_music", true to "ui.navigation.library").forEach { (value, key) ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                .background(if (value == library) MaterialTheme.colorScheme.surface else Color.Transparent)
                .clickable { onScope(value) }.testTag("desktop-search-scope:${if (value) "library" else "catalog"}"),
                contentAlignment = Alignment.Center) {
                Text(desktopString(key), fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DesktopSearchKindPicker(kinds: List<MeloXSearchKind>, selected: MeloXSearchKind, onKind: (MeloXSearchKind) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 34.dp, vertical = 16.dp)) {
        Row(Modifier.widthIn(max = 520.dp).fillMaxWidth().height(26.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f)).padding(2.dp)) {
            kinds.forEach { kind ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                    .background(if (kind == selected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onKind(kind) }.testTag("search-kind-${kind.name.lowercase()}"), contentAlignment = Alignment.Center) {
                    Text(desktopString("ui.search.kind.${kind.name.lowercase()}"), fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchSongs(songs: List<SearchSong>, onPlay: (SearchSong) -> Unit, onMenu: (SearchSong, IntOffset?) -> Unit) {
    val scroll = rememberLazyListState()
    val queue by PlaybackCommands.queue.collectAsState()
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll), state = scroll,
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(songs, key = { _, it -> it.id }) { index, song ->
            DesktopCollectionTrackRow(index + 1, song, queue.songs.getOrNull(queue.index)?.id == song.id,
                playback.isPlaying, false, { onPlay(song) }, { onMenu(song, it) }, showsArtwork = true)
        }
    }
}

@Composable
private fun DesktopSearchProviderSongs(tracks: List<MusicTrack>, onPlay: (MusicTrack) -> Unit) {
    val scroll = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll), state = scroll,
        contentPadding = PaddingValues(horizontal = 34.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(tracks, key = { _, it -> "${it.id.source.storageValue}:${it.id.value}" }) { index, track ->
            DesktopCollectionTrackRow(index + 1, MeloXLegacyUiBridge.track(track), false, false, false,
                { onPlay(track) }, onMore = null, showsArtwork = true)
        }
    }
}

@Composable
private fun DesktopSearchMediaGrid(items: List<MeloXSearchMediaItem>, kind: MeloXSearchKind, onOpen: (MeloXSearchMediaItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 34.dp, vertical = 24.dp)) {
        val count = floor((maxWidth.value + 20) / 150).toInt().coerceAtLeast(1)
        val width = ((maxWidth.value - (count - 1) * 20) / count).coerceAtMost(210f).dp
        val scroll = rememberLazyListState()
        LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll).testTag("desktop-search-media-scroll"),
            state = scroll, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            itemsIndexed(items.chunked(count), key = { _, row -> "${row.first().kind}:${row.first().id}" }) { _, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { item ->
                        DesktopHomeMediaCard(item.title, item.subtitle, item.artworkUrl, width,
                            "desktop-search-result:${item.kind.name}:${item.id}", item.trackCount.toLong(),
                            onOpen = { onOpen(item) }, isCircular = kind == MeloXSearchKind.Artists)
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopSearchUnavailable(title: String, message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        MeloXSymbolIcon(MeloXSymbol.Search, size = 42, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) DesktopDialogButton("ui.common.retry", onClick = onRetry)
    }
}
