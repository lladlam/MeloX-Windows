package melox.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import kotlinx.coroutines.launch
import melox.layout.meloXDesktopShelfWidth
import melox.library.NeteaseHomeContent
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopDisplayFontFamily
import melox.ui.theme.MeloXDesktopTextFontFamily

internal val LocalDesktopHomeVerticalWheel = staticCompositionLocalOf<((Float) -> Unit)?> { null }

private data class DesktopQuickAction(val original: String, val key: String, val symbol: MeloXSymbol,
    val colors: List<Color>, val tint: Color)
// SwiftUI semantic colours in the supplied dark Home frame. Artwork, when present, replaces the gradient.
private val quickActions = listOf(
    DesktopQuickAction("每日推荐", "daily_songs", MeloXSymbol.Calendar, listOf(Color(0xFF7D7DFF), Color(0xFF2692FF), Color(0xFF40DCC9)), Color(0xFFFF453A)),
    DesktopQuickAction("心动模式", "heart_mode", MeloXSymbol.HeartFill, listOf(Color(0xFFFF9230), Color(0xFFFF4948)), Color(0xFFFF6482)),
    DesktopQuickAction("私人漫游", "private_roaming", MeloXSymbol.Walk, listOf(Color.Black, Color(0xFF7D7AFF), Color(0xFF8E8E93)), Color(0xFF0A84FF)),
    DesktopQuickAction("热歌榜", "hot_songs", MeloXSymbol.Flame, listOf(Color(0xFFFF453A), Color(0xFFFF9F0A)), Color(0xFFFF9F0A)),
    DesktopQuickAction("私人雷达", "private_radar", MeloXSymbol.Podcast, listOf(Color(0xFFBF5AF2), Color(0xFF0A84FF)), Color(0xFFBF5AF2)),
    DesktopQuickAction("相似歌曲", "similar_songs", MeloXSymbol.Library, listOf(Color(0xFF40C8E0), Color(0xFF7D7AFF)), Color(0xFF40C8E0)),
)

@Composable
internal fun MeloXDesktopHome(content: NeteaseHomeContent?, recentSongs: List<SearchSong>, activeAction: String?, loading: Boolean, error: String?,
    onAction: (String) -> Unit, onPlaylist: (NeteasePlaylistSummary) -> Unit, onPlayPlaylist: (NeteasePlaylistSummary) -> Unit,
    onAlbum: (Long, String) -> Unit, onPlayAlbum: (Long) -> Unit, onPodcast: (Long) -> Unit, onTool: (String) -> Unit) {
    val reserved = 0
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val wheelScale = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        color = MaterialTheme.colorScheme.onBackground), LocalDesktopHomeVerticalWheel provides { delta ->
        scope.launch { listState.scrollBy(delta * wheelScale) }; Unit
    }) {
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-home")) {
        val heroWidth = meloXDesktopShelfWidth(maxWidth.value - reserved, if (reserved > 0) 145 else 200, 20).dp
        val mediaWidth = meloXDesktopShelfWidth(maxWidth.value - reserved, 145, 24).dp
        // The native reference has a toolbar safe-area above its 22pt scroll padding.
        LazyColumn(Modifier.fillMaxSize().padding(top = 52.dp).desktopPromotedDragScroll(listState).testTag("desktop-home-scroll"), state = listState,
            contentPadding = PaddingValues(start = 52.dp, end = 52.dp, top = 22.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp)) {
            item("title") { Text(desktopString("ui.navigation.home"), Modifier.height(55.dp).wrapContentHeight(unbounded = true),
                fontFamily = MeloXDesktopDisplayFontFamily, fontSize = 46.sp, lineHeight = 55.sp, fontWeight = FontWeight.Bold) }
            item("quick-actions") {
                HomeSection(desktopString("ui.home.section.quick_actions")) {
                    DesktopHomePagingShelf(quickActions, { it.key }, heroWidth, 20.dp, reserved, "actions") { action ->
                        val artwork = when (action.key) {
                            "private_roaming" -> content?.radarPlaylists?.firstOrNull()?.coverUrl ?: content?.playlists?.firstOrNull()?.coverUrl
                            "hot_songs" -> content?.chartPlaylists?.firstOrNull { it.id == 3_778_678L || it.name.contains("热歌榜") }?.coverUrl
                            "private_radar" -> content?.radarPlaylists?.firstOrNull()?.coverUrl
                            "similar_songs" -> currentTrack?.artworkUrl
                            else -> null
                        }
                        DesktopQuickActionCard(action, artwork, heroWidth, activeAction, onAction)
                    }
                }
            }
            item("hero-playlists") {
                HomeSection(desktopString("ui.home.section.recommended_playlists")) {
                    if (content?.playlists?.isNotEmpty() == true) {
                        DesktopHomePagingShelf(content.playlists.take(7), { it.id }, heroWidth, 20.dp, reserved, "hero") { playlist ->
                            DesktopHomeHeroPlaylist(playlist, heroWidth) { onPlaylist(playlist) }
                        }
                    } else if (loading) Spacer(Modifier.height(270.dp))
                }
            }
            val history = recentSongs.ifEmpty { content?.newSongs.orEmpty() }.take(8)
            if (MeloXSettingsRuntime.listeningHistoryEnabled && history.isNotEmpty()) item("history") {
                HomeSection(desktopString("ui.navigation.library.history")) {
                    DesktopHomePagingShelf(history, { it.id }, mediaWidth, 24.dp, reserved, "history") { song ->
                        DesktopHomeMediaCard(song.name, song.artists, song.artworkUrl, mediaWidth, "desktop-home-track:${song.id}",
                            onOpen = { melox.ui.navigation.MeloXCollectionLaunchBus.open(
                                melox.ui.navigation.MeloXCollectionTarget.Song(song.id)) },
                            onPlay = { melox.playback.PlaybackCommands.playQueue(history, song.id) })
                    }
                }
            }
            if (content?.playlists?.isNotEmpty() == true) item("tailored") {
                HomeSection(desktopString("ui.home.section.tailored"), desktopString("ui.common.view_all"), { onTool("Playlists") }) {
                    DesktopHomePagingShelf(content.playlists.take(12), { it.id }, mediaWidth, 24.dp, reserved, "tailored") { playlist ->
                        DesktopHomeMediaCard(playlist.name, playlist.creatorName.ifBlank { playlist.copywriter.orEmpty() }, playlist.coverUrl,
                            mediaWidth, "desktop-home-media-playlist:${playlist.id}", playlist.playCount,
                            onOpen = { onPlaylist(playlist) }, onPlay = { onPlayPlaylist(playlist) })
                    }
                }
            }
            if (content?.newAlbums?.isNotEmpty() == true) item("albums") {
                HomeSection(desktopString("ui.albums.new.title"), desktopString("ui.common.view_all"), { onTool("Explore") }) {
                    DesktopHomePagingShelf(content.newAlbums.take(12), { it.id }, mediaWidth, 24.dp, reserved, "albums") { album ->
                        DesktopHomeMediaCard(album.name, album.artistText, album.artworkUrl, mediaWidth, "desktop-home-album:${album.id}",
                            onOpen = { onAlbum(album.id, album.name) }, onPlay = { onPlayAlbum(album.id) })
                    }
                }
            }
            if (MeloXSettingsRuntime.podcastsEnabled && content?.podcasts?.isNotEmpty() == true) item("podcasts") {
                HomeSection(desktopString("ui.home.section.podcast_recommendations"), desktopString("ui.navigation.podcasts"), { onTool("Podcasts") }) {
                    DesktopHomePagingShelf(content.podcasts.take(8), { it.id }, mediaWidth, 24.dp, reserved, "podcasts") { program ->
                        DesktopHomeMediaCard(program.name, program.playbackSong?.album.orEmpty(), program.artworkUrl, mediaWidth,
                            "desktop-home-podcast:${program.id}", onOpen = { onPodcast(program.id) },
                            onPlay = program.playbackSong?.let { song -> { melox.playback.PlaybackCommands.playQueue(listOf(song), song.id) } })
                    }
                }
            }
            if (error != null) item("error") {
                Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).testTag("desktop-home-error"), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(desktopString("ui.error.home.load_failed"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(error, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), fontSize = 13.sp)
                }
            }
        }
    }
    }
}

@Composable
private fun HomeSection(title: String, trailing: String? = null, onTrailing: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f).wrapContentHeight(unbounded = true), fontSize = 23.sp, lineHeight = 28.sp, fontFamily = MeloXDesktopDisplayFontFamily, fontWeight = FontWeight.Bold)
            if (trailing != null && onTrailing != null) Text(trailing, Modifier.testTag("desktop-home-section:$title")
                .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onTrailing), color = Color.Red, fontSize = 13.sp)
        }
        content()
    }
}

@Composable
private fun DesktopQuickActionCard(action: DesktopQuickAction, artwork: String?, width: Dp, activeAction: String?, onAction: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && activeAction == null) 1.012f else 1f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 160), label = "home-action-hover")
    val shape = ContinuousRoundedRectangle(15.dp)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val colors = if (dark) action.colors else when (action.key) {
        "daily_songs" -> listOf(Color(0xFF5856D6), Color(0xFF007AFF), Color(0xFF00C7BE))
        "heart_mode" -> listOf(Color(0xFFFF9500), Color(0xFFFF3B30))
        "private_roaming" -> listOf(Color.Black, Color(0xFF5856D6), Color.Gray)
        "hot_songs" -> listOf(Color(0xFFFF3B30), Color(0xFFFF9500))
        "private_radar" -> listOf(Color(0xFFAF52DE), Color(0xFF007AFF))
        else -> listOf(Color(0xFF30B0C7), Color(0xFF5856D6))
    }
    Box(Modifier.width(width).height(width * 4f / 3f).graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(if (hovered) 18.dp else 6.dp, shape, ambientColor = Color.Black.copy(alpha = if (hovered) .22f else .10f))
        .clip(shape).testTag("desktop-home-action:${action.key}").hoverable(interaction)
        .clickable(enabled = activeAction == null, interactionSource = interaction, indication = null, role = Role.Button) { onAction(action.original) }) {
        if (artwork != null) MeloXArtworkImage(artwork, Color.DarkGray, Modifier.fillMaxSize())
        else Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
            MeloXSymbolIcon(action.symbol, Modifier.offset(y = -width * 4f / 3f * .08f),
                size = (width.value * 4f / 3f * .34f).coerceAtMost(150f).toInt(), color = Color.White.copy(alpha = .22f))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(action.tint.copy(alpha = if (artwork == null) .06f else .24f),
            Color.Transparent, Color.Black.copy(alpha = .78f)))))
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                MeloXSymbolIcon(MeloXSymbol.MusicNote, size = 15, weight = FontWeight.Bold, color = Color.White)
                Text("MeloX", color = Color.White, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(desktopString("ui.home.action.${action.key}.eyebrow"), color = Color.White.copy(alpha = .68f), fontSize = 14.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(desktopString("ui.home.action.${action.key}"), color = Color.White, fontSize = 25.sp, lineHeight = 30.sp, fontFamily = MeloXDesktopDisplayFontFamily, fontWeight = FontWeight.Bold)
                Text(desktopString("ui.home.action.${action.key}.subtitle"), color = Color.White.copy(alpha = .92f), fontSize = 14.sp,
                    lineHeight = 17.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (activeAction == action.original) Box(Modifier.align(Alignment.Center).size(58.dp).clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .84f)), contentAlignment = Alignment.Center) {
            MeloXSymbolIcon(MeloXSymbol.Waveform, size = 22, weight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DesktopHomeHeroPlaylist(playlist: NeteasePlaylistSummary, width: Dp, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.025f else 1f, tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 280), label = "home-hero-artwork")
    Box(Modifier.size(width).clip(ContinuousRoundedRectangle(10.dp)).testTag("desktop-home-playlist:${playlist.id}")
        .hoverable(interaction).clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onOpen)) {
        MeloXArtworkImage(playlist.coverUrl, Color.DarkGray, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale })
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .08f), Color.Black.copy(alpha = .78f)))))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(playlist.copywriter ?: desktopString("ui.home.section.tailored"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = .66f))
            Text(playlist.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (playlist.creatorName.isNotBlank()) Text(playlist.creatorName, Modifier.weight(1f, fill = false), fontSize = 13.sp,
                    fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = .92f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (MeloXSettingsRuntime.showPlaylistPlayCount && playlist.playCount > 0) DesktopHomePlayCount(playlist.playCount)
            }
        }
    }
}
