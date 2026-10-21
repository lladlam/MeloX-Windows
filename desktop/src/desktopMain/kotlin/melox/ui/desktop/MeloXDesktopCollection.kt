package melox.ui.desktop

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.model.SearchSong
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.MeloXGlassTextField
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopDisplayFontFamily
import melox.ui.theme.MeloXDesktopSubtitleFontFamily
import melox.ui.theme.MeloXDesktopTextFontFamily
import com.kyant.capsule.ContinuousRoundedRectangle
import melox.ui.navigation.MeloXPointerMode

/** DesktopRouteView / DesktopTrackRow at a41ea0e. Native toolbar is a fixed Windows adapter. */
@Composable
internal fun MeloXDesktopCollectionDetail(
    kind: String,
    title: String,
    subtitle: String?,
    metadata: String?,
    description: String?,
    artworkUrl: String?,
    songs: List<SearchSong>,
    hasSongs: Boolean = songs.isNotEmpty(),
    loading: Boolean,
    error: String?,
    playingId: Long?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    isFavorite: Boolean = false,
    favoriteEnabled: Boolean = true,
    onFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    filter: String = "",
    onFilterChange: ((String) -> Unit)? = null,
    showsAlbumColumn: Boolean = true,
    groupsByDisc: Boolean = false,
    onRetry: () -> Unit,
    onSong: (SearchSong) -> Unit,
    onMore: ((SearchSong) -> Unit)? = null,
    onMenuAnchor: (IntOffset) -> Unit = {},
    status: String? = null,
    extraContent: (androidx.compose.foundation.lazy.LazyListScope.() -> Unit)? = null,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    var searchOpen by remember(tag) { mutableStateOf(false) }
    var downloadMenu by remember(tag) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Do not merge Material bodyLarge's 24sp line height into 11–13.5pt SF text.
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = MeloXDesktopTextFontFamily, color = colors.onSurface)) {
        Column(Modifier.fillMaxSize().background(colors.background).testTag(tag)) {
            // SwiftUI navigationTitle/back live outside ScrollView, not inside its header.
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).background(colors.onSurface.copy(alpha = .025f)).border(.75.dp, colors.onSurface.copy(alpha = .12f), CircleShape)) {
                    DesktopCollectionIconButton(MeloXSymbol.ChevronLeft, "desktop-collection-back", desktopString("ui.common.back"), onBack, size = 36)
                }
                Text(title, Modifier.weight(1f).padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (onFilterChange != null) {
                    if (searchOpen) MeloXGlassTextField(
                        value = filter, onValueChange = onFilterChange,
                        modifier = Modifier.width(240.dp).testTag("desktop-collection-search"),
                        placeholder = { Text(desktopString("ui.navigation.search"), fontSize = 13.sp) },
                        textStyle = TextStyle(fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp, color = colors.onSurface),
                    )
                    DesktopCollectionIconButton(MeloXSymbol.Search, "desktop-collection-search-toggle", desktopString("ui.navigation.search"), {
                        searchOpen = !searchOpen
                        if (!searchOpen) onFilterChange("")
                    })
                }
            }
            when {
                loading -> Box(Modifier.fillMaxSize().testTag("desktop-collection-loading"), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                error != null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(desktopString("ui.error.music_content_load_failed"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(error, Modifier.padding(12.dp), fontSize = 13.sp, color = colors.onSurface.copy(alpha = .5f))
                    DesktopCollectionAction(desktopString("ui.common.retry"), MeloXSymbol.Refresh, "desktop-collection-retry", onClick = onRetry)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().desktopPromotedDragScroll(listState).testTag("desktop-collection-scroll")
                        .semantics { stateDescription = "${listState.firstVisibleItemIndex}-${listState.firstVisibleItemScrollOffset}" },
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 42.dp, vertical = 34.dp),
                ) {
                    item("header") {
                        Row(Modifier.fillMaxWidth().testTag("desktop-collection-header"), verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                            DesktopCollectionArtwork(artworkUrl, title)
                            Box(Modifier.weight(1f)) {
                            DesktopCollectionDetails(kind, title, subtitle, metadata, description, hasSongs,
                                onPlayAll, onShuffle, isFavorite, favoriteEnabled, onFavorite,
                                if (onDownload == null) null else {
                                    {
                                        Box {
                                            DesktopCollectionIconButton(MeloXSymbol.Ellipsis, "desktop-collection-actions", desktopString("ui.common.more"), { downloadMenu = true }, size = 22)
                                            DropdownMenu(downloadMenu, { downloadMenu = false }) {
                                                DropdownMenuItem(text = { Text(desktopString("ui.desktop.collection.download_all")) },
                                                    enabled = hasSongs, leadingIcon = { MeloXSymbolIcon(MeloXSymbol.Download, size = 13) },
                                                    modifier = Modifier.testTag("desktop-collection-download"),
                                                    onClick = { downloadMenu = false; onDownload() })
                                            }
                                        }
                                    }
                                }, Modifier.widthIn(max = 620.dp).fillMaxWidth().padding(bottom = 6.dp))
                            }
                        }
                    }
                    status?.let { message -> item("status") { Text(message, Modifier.padding(top = 12.dp), color = colors.onSurfaceVariant, fontSize = 12.sp) } }
                    if (extraContent != null) {
                        extraContent()
                    } else {
                    item("divider") {
                        HorizontalDivider(Modifier.padding(vertical = 34.dp), color = colors.onSurface.copy(alpha = .10f), thickness = .5.dp)
                    }
                    val discGroups = songs.groupBy { song ->
                        val raw = song.disc.orEmpty()
                        raw.filter(Char::isDigit).toIntOrNull()?.toString() ?: raw.trim().ifEmpty { "1" }
                    }
                    val grouped = groupsByDisc && discGroups.size > 1
                    var indexOffset = 0
                    for ((disc, group) in if (grouped) discGroups else mapOf("" to songs)) {
                        if (grouped) item("disc:$disc") {
                            Text(desktopString("ui.desktop.collection.disc").replace("%ld", disc),
                                Modifier.padding(start = 12.dp, end = 12.dp, top = if (disc == discGroups.keys.first()) 0.dp else 18.dp, bottom = 5.dp),
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(alpha = .5f))
                        }
                        val offset = indexOffset
                        itemsIndexed(group, key = { _, song -> song.providerTrack?.id?.toString() ?: song.id.toString() }) { index, song ->
                            DesktopCollectionTrackRow(if (groupsByDisc) song.trackNumber ?: (offset + index + 1) else offset + index + 1,
                                song, playingId == song.id, isPlaying, showsAlbumColumn, { onSong(song) }, onMore?.let { action -> { anchor -> onMenuAnchor(anchor); action(song) } })
                            Spacer(Modifier.height(1.dp))
                        }
                        indexOffset += group.size
                    }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopCollectionArtwork(url: String?, title: String) {
    val artworkShape = remember { ContinuousRoundedRectangle(11.dp) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.012f else 1f, tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 250), label = "collection-artwork-hover")
    MeloXArtworkImage(url, MaterialTheme.colorScheme.surfaceVariant,
        Modifier.size(278.dp).testTag("desktop-collection-artwork").hoverable(interaction).graphicsLayer {
            scaleX = scale; scaleY = scale
            shadowElevation = (if (hovered) 22.dp else 18.dp).toPx()
            shape = artworkShape
            ambientShadowColor = Color.Black.copy(alpha = if (hovered) .24f else .20f)
            spotShadowColor = ambientShadowColor
        }.clip(artworkShape), contentDescription = title)
}

@Composable
private fun DesktopCollectionDetails(
    kind: String, title: String, subtitle: String?, metadata: String?, description: String?, hasSongs: Boolean,
    onPlayAll: () -> Unit, onShuffle: () -> Unit, isFavorite: Boolean, favoriteEnabled: Boolean, onFavorite: (() -> Unit)?,
    more: (@Composable () -> Unit)?, modifier: Modifier,
) {
    val secondary = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
    var expanded by remember(title, description) { mutableStateOf(false) }
    Column(modifier.testTag("desktop-collection-details"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(kind.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = secondary)
        Text(title, fontFamily = MeloXDesktopDisplayFontFamily, fontSize = 32.sp,
            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        subtitle?.takeIf(String::isNotBlank)?.let {
            Text(it, fontFamily = MeloXDesktopSubtitleFontFamily, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, color = Color(0xFFFF3B45), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        metadata?.takeIf(String::isNotBlank)?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondary) }
        description?.takeIf(String::isNotBlank)?.let {
            val expandable = remember(it) { it.contains('\n') || Regex("\\X").findAll(it).take(121).count() > 120 }
            Column(Modifier.animateContentSize(tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 240)), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                SelectionContainer {
                    Text(it, Modifier.testTag("desktop-collection-description").semantics { stateDescription = if (expanded) "expanded" else "collapsed" }, fontSize = 12.5.sp,
                        color = secondary, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                }
                if (expandable) Text(
                    desktopString(if (expanded) "ui.common.collapse" else "ui.common.more"),
                    Modifier.testTag("desktop-collection-description-toggle").clickable(role = Role.Button) { expanded = !expanded },
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.Red)
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopCollectionAction(desktopString("ui.common.play"), MeloXSymbol.Play, "desktop-collection-play", hasSongs, prominent = true, onClick = onPlayAll)
            DesktopCollectionAction(desktopString("ui.player.shuffle"), MeloXSymbol.Shuffle, "desktop-collection-shuffle", hasSongs, onClick = onShuffle)
            if (onFavorite != null) DesktopCollectionAction(desktopString(if (isFavorite) "ui.desktop.collection.unfavorite" else "ui.desktop.collection.favorite"),
                if (isFavorite) MeloXSymbol.StarFill else MeloXSymbol.Star, "desktop-collection-favorite", favoriteEnabled, iconOnly = true, onClick = onFavorite)
            more?.invoke()
        }
    }
}

/** Compact macOS bordered-control adapter, without Material's 40/48dp minimums. */
@Composable
private fun DesktopCollectionAction(label: String, symbol: MeloXSymbol, tag: String, enabled: Boolean = true,
    prominent: Boolean = false, iconOnly: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(5.dp)
    val accent = Color(0xFFFF3B45)
    Row(Modifier.height(24.dp).clip(shape).testTag(tag).semantics { contentDescription = label }
        .background(if (prominent) accent.copy(alpha = if (enabled) 1f else .35f) else accent.copy(alpha = if (hovered && enabled) .14f else .065f))
        .hoverable(interaction)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (iconOnly) 7.dp else 9.dp)
        .alpha(if (enabled) 1f else .4f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        MeloXSymbolIcon(symbol, size = 12, color = if (prominent) Color.White else accent)
        if (!iconOnly) Text(label, fontSize = 13.sp, color = if (prominent) Color.White else accent)
    }
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
internal fun DesktopCollectionTrackRow(index: Int, song: SearchSong, current: Boolean, isPlaying: Boolean,
    showsAlbumColumn: Boolean, onClick: () -> Unit, onMore: ((IntOffset) -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var rowOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    var menuAnchor by remember { mutableStateOf(IntOffset.Zero) }
    val opacity by animateFloatAsState(if (hovered) .045f else 0f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 160, easing = CubicBezierEasing(0f, 0f, .58f, 1f)), label = "collection-row-hover")
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("desktop-collection-track:${song.id}")
        .onGloballyPositioned { rowOrigin = it.boundsInWindow().topLeft }
        .onPointerEvent(PointerEventType.Press) { event ->
            if (event.buttons.isSecondaryPressed) {
                val point = rowOrigin + event.changes.first().position
                onMore?.invoke(IntOffset(point.x.toInt(), point.y.toInt()))
                event.changes.forEach { it.consume() }
            }
        }
        .clip(RoundedCornerShape(8.dp)).hoverable(interaction).background(colors.onSurface.copy(alpha = opacity))
        .combinedClickable(onClick = { if (MeloXPointerMode.touch) onClick() }, onDoubleClick = onClick, onLongClick = onMore?.let { { it(menuAnchor) } })
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(24.dp).height(28.dp).testTag("desktop-collection-track-play:${song.id}")
            .semantics { contentDescription = desktopFormat("ui.common.play_named", song.name) }
            .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
            if (hovered || current || MeloXPointerMode.touch) MeloXSymbolIcon(if (current && isPlaying) MeloXSymbol.Waveform else MeloXSymbol.Play,
                size = 12, weight = FontWeight.SemiBold, color = if (current) Color.Red else colors.onSurface)
            else Text(index.toString(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(alpha = .5f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(song.name, fontSize = 13.5.sp, fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                color = if (current) Color.Red else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = .5f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showsAlbumColumn) Text(song.album, Modifier.width(180.dp), fontSize = 12.sp, color = colors.onSurface.copy(alpha = .5f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(desktopDuration(song.durationMs), Modifier.width(42.dp), fontSize = 12.sp, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            textAlign = androidx.compose.ui.text.style.TextAlign.End, color = colors.onSurface.copy(alpha = .5f))
        Box(Modifier.alpha(if (hovered || current || MeloXPointerMode.touch) 1f else .16f)
            .onGloballyPositioned { val bounds = it.boundsInWindow(); menuAnchor = IntOffset(bounds.right.toInt(), bounds.bottom.toInt()) }) {
            if (onMore != null) DesktopCollectionIconButton(MeloXSymbol.Ellipsis, "desktop-collection-more:${song.id}", desktopString("ui.common.more"), { onMore(menuAnchor) }, size = 28)
            else Spacer(Modifier.size(28.dp))
        }
    }
}

@Composable
private fun DesktopCollectionIconButton(symbol: MeloXSymbol, tag: String, label: String, onClick: () -> Unit, size: Int = 34) {
    Box(Modifier.size(size.dp).clip(CircleShape).testTag(tag).semantics { contentDescription = label }
        .clickable(role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = 13, color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun desktopDuration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
