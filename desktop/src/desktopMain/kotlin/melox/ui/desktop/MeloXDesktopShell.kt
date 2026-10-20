package melox.ui.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.account.rememberNeteaseSessionStore
import melox.music.model.MusicSource
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXContentLayout
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.*
import melox.ui.layout.MeloXListDetailLayout
import melox.ui.navigation.AppTab
import melox.ui.screens.LyricsPage
import melox.ui.screens.MeloXArtworkImage

internal enum class DesktopInspector { Lyrics, Queue }

/** Exact source metrics. Native window growth/SwiftUI material remain separately tracked. */
internal object DesktopMetrics {
    const val Sidebar = 199
    const val Inspector = 258
    const val PlayerWidth = 700
    const val PlayerHeight = 54
    const val PlayerBottom = 10
}

/** The page composition stays in the same keyed slot when the UI toggle changes. */
@Composable
internal fun MeloXInterfaceLayout(
    hasMedia: Boolean,
    selectedTab: AppTab,
    source: MusicSource,
    libraryPage: String?,
    onSelect: (AppTab, String?) -> Unit,
    onAccount: () -> Unit,
    onOpenMessages: () -> Unit,
    onLogin: () -> Unit,
    onExpandPlayer: () -> Unit,
    playerOpen: Boolean,
    backdrop: MeloXLayerBackdrop,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    var inspector by remember { mutableStateOf<DesktopInspector?>(null) }
    var accountSheet by remember { mutableStateOf(false) }
    LaunchedEffect(desktop, hasMedia) { if (!desktop || !hasMedia) inspector = null }
    val duration = if (MeloXSettingsRuntime.reduceMotion) 0 else 280
    Row(Modifier.fillMaxSize().testTag(if (desktop) "interface-desktop" else "interface-current")) {
        if (desktop) DesktopSidebar(selectedTab, source, libraryPage, onSelect) { accountSheet = true }
        key("retained-page") {
            Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
                CompositionLocalProvider(LocalDesktopInspectorWidth provides if (desktop && inspector != null && !playerOpen) DesktopMetrics.Inspector else 0) {
                    MeloXListDetailLayout(hasMedia, modifier) { content() }
                }
                if (desktop) {
                    androidx.compose.animation.AnimatedVisibility(inspector != null && !playerOpen, Modifier.align(Alignment.CenterEnd).width(DesktopMetrics.Inspector.dp).fillMaxHeight(),
                        enter = slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)),
                        exit = slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration))) {
                        var retained by remember { mutableStateOf(inspector ?: DesktopInspector.Lyrics) }
                        if (inspector != null) retained = inspector!!
                        DesktopInspectorPanel(retained, inspector != null)
                    }
                    if (hasMedia && !playerOpen) {
                        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp + if (inspector != null) DesktopMetrics.Inspector.dp else 0.dp,
                                bottom = DesktopMetrics.PlayerBottom.dp), contentAlignment = Alignment.Center) {
                            CompositionLocalProvider(LocalMeloXLayerBackdrop provides backdrop) {
                                DesktopBottomPlayer(inspector, { inspector = if (inspector == it) null else it }, onExpandPlayer)
                            }
                        }
                    }
                }
            }
        }
    }
    if (desktop && accountSheet) {
        MeloXDesktopAccountSheet(
            onDismiss = { accountSheet = false },
            onAccountSettings = { accountSheet = false; onAccount() },
            onMessages = { accountSheet = false; onOpenMessages() },
            onLogin = { accountSheet = false; onLogin() },
            onSelect = { tab, page -> accountSheet = false; onSelect(tab, page) },
        )
    }
}

@Composable
private fun DesktopSidebar(selectedTab: AppTab, source: MusicSource, libraryPage: String?,
    onSelect: (AppTab, String?) -> Unit, onAccount: () -> Unit) {
    val session = rememberNeteaseSessionStore()
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(8.dp).width(DesktopMetrics.Sidebar.dp).fillMaxHeight()
        .clip(RoundedCornerShape(18.dp)).background(colors.surface.copy(alpha = .72f))
        .border(.65.dp, colors.outlineVariant.copy(alpha = .5f), RoundedCornerShape(18.dp))
        .testTag("desktop-sidebar")) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 9.dp, vertical = 38.dp)) {
            fun selected(tab: AppTab, page: String? = null) = tab == selectedTab && (page == null || libraryPage == page)
            DesktopNavigationRow("Search", "ui.navigation.search", MeloXSymbol.Search, selected(AppTab.Search)) { onSelect(AppTab.Search, null) }
            if (MeloXSettingsRuntime.homeTabEnabled)
                DesktopNavigationRow("Home", "ui.navigation.home", MeloXSymbol.Home, selected(AppTab.Home)) { onSelect(AppTab.Home, null) }
            if (MeloXSettingsRuntime.exploreTabEnabled)
                DesktopNavigationRow("Explore", "ui.navigation.explore", MeloXSymbol.Grid, selected(AppTab.Explore)) { onSelect(AppTab.Explore, null) }
            if (MeloXSettingsRuntime.podcastsEnabled && source == MusicSource.Netease)
                DesktopNavigationRow("Radio", "ui.navigation.podcasts", MeloXSymbol.Podcast, selected(AppTab.Podcasts)) { onSelect(AppTab.Podcasts, null) }
            Text(desktopString("ui.navigation.library"), Modifier.padding(top = 22.dp, start = 4.dp, bottom = 6.dp),
                color = colors.onSurface.copy(alpha = .4f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            val pages = MeloXContentLayout.libraryPages(source)
            for ((page, label, symbol) in listOf(
                Triple("Songs", "ui.navigation.library.liked_songs", MeloXSymbol.Heart),
                Triple("Playlists", "ui.navigation.library.liked_playlists", MeloXSymbol.Library),
                Triple("Albums", "ui.navigation.library.liked_albums", MeloXSymbol.MusicNote),
                Triple("Artists", "ui.library.artists.title", MeloXSymbol.Mic),
                Triple("Podcasts", "ui.navigation.library.podcasts", MeloXSymbol.Mic),
                Triple("Downloads", "ui.navigation.downloads", MeloXSymbol.Download),
                Triple("Cloud", "ui.navigation.cloud", MeloXSymbol.Cloud),
                Triple("History", "ui.navigation.library.history", MeloXSymbol.Clock),
            )) {
                val enabled = page in pages
                // The desktop source exposes Albums/Artists. Until their real
                // Windows provider consumers exist, retain explicit disabled rows.
                if (enabled || page in setOf("Albums", "Artists"))
                    DesktopNavigationRow(page, label, symbol, selected(AppTab.Library, page), enabled) { onSelect(AppTab.Library, page) }
            }
        }
        Box(Modifier.fillMaxWidth().height(.5.dp).background(colors.outlineVariant))
        Row(Modifier.fillMaxWidth().height(50.dp).testTag("desktop-account").clickable(role = Role.Button, onClick = onAccount)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            val profile = session.profile
            if (profile?.avatarUrl != null) MeloXArtworkImage(profile.avatarUrl, colors.primary, Modifier.size(30.dp).clip(CircleShape))
            else Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0xFF7AA1E0)), contentAlignment = Alignment.Center) {
                Text(desktopString("ui.desktop.sidebar.fallback_avatar_initial"), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Text(profile?.nickname ?: desktopString("ui.desktop.sidebar.not_signed_in"), fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().height(36.dp).testTag("desktop-settings").clickable(role = Role.Button) { onSelect(AppTab.Settings, null) }
            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MeloXSymbolIcon(MeloXSymbol.Settings, size = 15, nativeGlyph = true)
            Text(desktopPlatformString("settings"), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DesktopNavigationRow(id: String, key: String, symbol: MeloXSymbol, isSelected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .34f)
    Row(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(8.dp))
        .background(if (isSelected) Color(0xFFFF303C) else if (hovered && enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = .06f) else Color.Transparent)
        .testTag("desktop-nav:$id").semantics {
            selected = isSelected
            contentDescription = desktopString(key) + if (enabled) "" else ": " + desktopPlatformString("unavailable")
        }.hoverable(interaction).clickable(enabled = enabled, role = Role.Tab, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        MeloXSymbolIcon(symbol, size = 16, color = color, nativeGlyph = true)
        Text(desktopString(key), color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DesktopBottomPlayer(inspector: DesktopInspector?, onInspector: (DesktopInspector) -> Unit, onExpand: () -> Unit) {
    val track by AudioPlayer.currentTrack.collectAsState()
    val song = track ?: return
    val color = MaterialTheme.colorScheme.onSurface
    val player = PlaybackCommands.playerController()
    val volume by AudioPlayer.volumeLevel.collectAsState()
    var volumeExpanded by remember { mutableStateOf(false) }
    Row(Modifier.widthIn(max = DesktopMetrics.PlayerWidth.dp).fillMaxWidth().height(DesktopMetrics.PlayerHeight.dp)
        .testTag("desktop-bottom-player").shadow(14.dp, RoundedCornerShape(27.dp))
        .meloXLiquidBottomBar(RoundedCornerShape(27.dp), bottomLiquidGlassTint(), surfaceColor = bottomGlassSurfaceColor(), dark = bottomBarTone() > .5f)
        .padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        DesktopTransport(Modifier.width(148.dp), color)
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            Row(Modifier.weight(1f).fillMaxWidth().testTag("mini-player-expand").clickable(role = Role.Button, onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MeloXArtworkImage(song.artworkUrl, color.copy(alpha = .2f), Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = color, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(song.artistText, song.album?.name.orEmpty()).filter { it.isNotBlank() }.joinToString(" — "),
                        color = color.copy(alpha = .6f), fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            DesktopSeekBar(Modifier.fillMaxWidth().height(8.dp), color)
        }
        Spacer(Modifier.width(9.dp))
        if (volumeExpanded) {
            MeloXLiquidSlider(volume, { player?.setVolume(it) }, valueRange = 0f..1f,
                modifier = Modifier.width(100.dp).height(36.dp).testTag("desktop-volume-slider"), contentDescription = desktopString("ui.player.volume"))
        } else {
            DesktopIconButton(MeloXSymbol.Quote, "desktop-show-lyrics", desktopString("ui.desktop.commands.show_lyrics"),
                if (inspector == DesktopInspector.Lyrics) Color.Red else color) { onInspector(DesktopInspector.Lyrics) }
            DesktopIconButton(MeloXSymbol.ListBullet, "desktop-show-queue", desktopString("ui.desktop.commands.show_queue"),
                if (inspector == DesktopInspector.Queue) Color.Red else color) { onInspector(DesktopInspector.Queue) }
        }
        DesktopIconButton(MeloXSymbol.Volume, "desktop-volume", desktopString("ui.player.volume"), color) { volumeExpanded = !volumeExpanded }
    }
}

@Composable
internal fun DesktopTransport(modifier: Modifier, tint: Color, prominent: Boolean = false) {
    val player = PlaybackCommands.playerController()
    val state by AudioPlayer.state.collectAsState()
    val queue by PlaybackCommands.queue.collectAsState()
    val shuffle by player?.shuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val repeat by player?.repeatMode?.collectAsState() ?: remember { mutableIntStateOf(0) }
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        DesktopIconButton(MeloXSymbol.Shuffle, "desktop-shuffle", desktopString(if (shuffle) "ui.player.shuffle_off" else "ui.player.shuffle_on"),
            if (shuffle) Color.Red else tint.copy(alpha = .58f), width = if (prominent) 40 else 28, iconSize = if (prominent) 20 else 11) { player?.setShuffleEnabled(!shuffle) }
        DesktopIconButton(MeloXSymbol.PreviousTrack, "desktop-previous", desktopString("ui.player.previous"), tint,
            width = if (prominent) 44 else 28, iconSize = if (prominent) 26 else 13) { player?.previous() }
        DesktopIconButton(if (state.isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play, "mini-player-play-pause", desktopString(if (state.isPlaying) "ui.player.pause" else "ui.common.play"), tint,
            width = if (prominent) 52 else 36, iconSize = if (prominent) 32 else 19) { player?.togglePlay() }
        DesktopIconButton(MeloXSymbol.NextTrack, "mini-player-next", desktopString("ui.player.next"), tint,
            enabled = queue.index < queue.songs.lastIndex || repeat == PlaybackCommands.REPEAT_ALL,
            width = if (prominent) 44 else 28, iconSize = if (prominent) 26 else 13) { player?.next() }
        DesktopIconButton(if (repeat == PlaybackCommands.REPEAT_ONE) MeloXSymbol.RepeatOne else MeloXSymbol.Repeat,
            "desktop-repeat", desktopString("ui.player.repeat"), if (repeat != 0) Color.Red else tint.copy(alpha = .58f),
            width = if (prominent) 40 else 28, iconSize = if (prominent) 20 else 11) { player?.cycleRepeatMode() }
    }
}

@Composable
internal fun DesktopIconButton(symbol: MeloXSymbol, tag: String, label: String, tint: Color,
    enabled: Boolean = true, width: Int = 36, iconSize: Int = 15, onClick: () -> Unit) {
    Box(Modifier.size(width.dp, 36.dp).clip(CircleShape).testTag(tag).semantics { contentDescription = label }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = iconSize, color = tint.copy(alpha = tint.alpha * if (enabled) 1f else .3f))
    }
}

@Composable
internal fun DesktopSeekBar(modifier: Modifier, tint: Color) {
    val progress by AudioPlayer.progress.collectAsState()
    val duration by AudioPlayer.durationMs.collectAsState()
    var drag by remember { mutableStateOf<Float?>(null) }
    val latestDuration by rememberUpdatedState(duration)
    fun seek(fraction: Float) { PlaybackCommands.playerController()?.seekTo((latestDuration * fraction.coerceIn(0f, 1f)).toLong()) }
    Canvas(modifier.testTag("desktop-seek").semantics {
        contentDescription = desktopString("ui.player.progress")
        progressBarRangeInfo = ProgressBarRangeInfo(drag ?: progress, 0f..1f)
        if (duration <= 0) disabled()
        setProgress { if (latestDuration > 0) { seek(it); true } else false }
    }.pointerInput(duration > 0) {
        if (latestDuration <= 0) return@pointerInput
        detectTapGestures { seek(it.x / size.width.coerceAtLeast(1)) }
    }.pointerInput(duration > 0) {
        if (latestDuration <= 0) return@pointerInput
        detectDragGestures(onDragStart = { drag = (it.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) },
            onDragCancel = { drag = null }, onDragEnd = { drag?.let(::seek); drag = null }) { change, _ ->
            change.consume(); drag = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }) {
        val inset = size.width * .015f
        val width = (size.width - inset * 2).coerceAtLeast(0f)
        val height = 2.dp.toPx()
        val top = Offset(inset, (size.height - height) / 2)
        drawRoundRect(tint.copy(alpha = .18f), top, Size(width, height), CornerRadius(height))
        drawRoundRect(tint.copy(alpha = .55f), top, Size(width * (drag ?: progress).coerceIn(0f, 1f), height), CornerRadius(height))
    }
}

@Composable
private fun DesktopInspectorPanel(kind: DesktopInspector, active: Boolean) {
    val track by AudioPlayer.currentTrack.collectAsState()
    val color = MaterialTheme.colorScheme.onSurface
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)
        .testTag("desktop-inspector:${kind.name}")) {
        if (kind == DesktopInspector.Lyrics) track?.let { DesktopLyrics(it, color, active = active, compact = true) }
        else DesktopQueueInspector(active)
    }
}

@Composable
internal fun DesktopQueueInspector(active: Boolean, prominent: Boolean = false) {
    val queue by PlaybackCommands.queue.collectAsState()
    val player = PlaybackCommands.playerController()
    val color = if (prominent) Color.White else MaterialTheme.colorScheme.onSurface
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(queue.entryIds.getOrNull(queue.index)) {
        list.scrollToItem(if (queue.index >= 0) queue.index + 2 else 0)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = if (prominent) 38.dp else 7.dp),
            horizontalArrangement = Arrangement.spacedBy(if (prominent) 11.dp else 9.dp)) {
            val autoplay = melox.playback.MeloXPlaybackModeRuntime.autoplayEnabled
            val automix = melox.playback.MeloXPlaybackModeRuntime.autoMixEnabled
            for ((id, title, checked) in listOf(Triple("autoplay", "ui.desktop.player.autoplay", autoplay),
                Triple("automix", "ui.settings.automix.title", automix))) {
                Row(Modifier.weight(1f).height(if (prominent) 40.dp else 38.dp).clip(CircleShape).testTag("desktop-queue-$id")
                    .background(if (checked) Color.Red else color.copy(alpha = .10f))
                    .semantics { selected = checked; contentDescription = desktopString(title) }
                    .clickable(enabled = active, role = Role.Button) {
                        if (id == "autoplay") player?.setAutoplayEnabled(!autoplay) else player?.setAutoMixEnabled(!automix)
                    }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    MeloXSymbolIcon(MeloXSymbol.Infinity, size = 16, color = if (checked) Color.White else color.copy(alpha = .88f))
                    if (prominent) {
                        Spacer(Modifier.width(8.dp))
                        Text(desktopString(title), color = if (checked) Color.White else color.copy(alpha = .88f),
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, userScrollEnabled = active,
            contentPadding = PaddingValues(bottom = if (prominent) 64.dp else 0.dp)) {
        fun header(title: String, tag: String, enabled: Boolean, onClear: () -> Unit) {
            item(key = tag) {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = if (prominent) 15.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopString(title), Modifier.weight(1f), color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(desktopString("ui.common.clear"), Modifier.testTag(tag).clickable(enabled = active && enabled, role = Role.Button, onClick = onClear),
                        color = Color.Red.copy(alpha = if (enabled) 1f else .3f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        fun rows(indices: List<Int>) {
        items(indices.size, key = { queue.entryIds.getOrNull(indices[it]) ?: indices[it] }) { row ->
            val index = indices[row]
            val song = queue.songs[index]
            val entryId = queue.entryIds.getOrNull(index)
            fun playEntry() { PlaybackCommands.queue.value.entryIds.indexOf(entryId).takeIf { it >= 0 }?.let { player?.seekToIndex(it); player?.play() } }
            var menu by remember(entryId) { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().height(48.dp).padding(start = if (prominent) 15.dp else 16.dp, end = if (prominent) 9.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f).fillMaxHeight().testTag("desktop-queue-track:$index").clickable(enabled = active, onClick = ::playEntry),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                MeloXArtworkImage(song.artworkUrl, color.copy(alpha = .2f), Modifier.size(34.dp).clip(RoundedCornerShape(5.dp)))
                Column(Modifier.weight(1f)) {
                    Text(song.name, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = if (index == queue.index) Color.Red else color)
                    Text(listOf(song.artists, song.album).filter(String::isNotBlank).joinToString(" — "), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = color.copy(alpha = .55f))
                }
                }
                Box {
                    DesktopIconButton(MeloXSymbol.Ellipsis, "desktop-queue-menu:$index", desktopString("ui.common.more"), color, width = 28, iconSize = 14) { menu = true }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(desktopString("ui.common.play")) }, onClick = { menu = false; playEntry() })
                        if (index != queue.index) androidx.compose.material3.DropdownMenuItem(text = { Text(desktopString("ui.desktop.player.remove_from_queue")) }, onClick = {
                            menu = false
                            PlaybackCommands.queue.value.entryIds.indexOf(entryId).takeIf { it >= 0 }?.let { player?.removeAt(it) }
                        })
                    }
                }
            }
            if (row < indices.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(.5.dp).background(color.copy(alpha = .13f)))
        }
        }
        if (queue.index >= 0) {
            header("ui.common.history", "desktop-queue-clear-history", queue.index > 0) {
                for (index in PlaybackCommands.queue.value.index - 1 downTo 0) player?.removeAt(index)
            }
            rows((0..queue.index.coerceAtMost(queue.songs.lastIndex)).toList())
        }
        header("ui.desktop.player.continue_playing", "desktop-queue-clear-upcoming", queue.index < queue.songs.lastIndex) {
            val current = PlaybackCommands.queue.value
            for (index in current.songs.lastIndex downTo current.index + 1) player?.removeAt(index)
        }
        val upcoming = (queue.index + 1 until queue.songs.size).toList()
        if (upcoming.isEmpty()) item { Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text(desktopString("ui.player.queue_empty"), color = color.copy(alpha = .55f), fontSize = 15.sp)
        } } else rows(upcoming)
        }
    }
}
