package melox.ui.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.library.NeteaseHomeContent
import melox.library.NeteasePlaylistSummary
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.model.SearchSong
import melox.layout.meloXDesktopShelfWidth

internal val LocalDesktopInspectorWidth = staticCompositionLocalOf { 0 }

private data class DesktopQuickAction(val original: String, val key: String, val symbol: MeloXSymbol, val colors: List<Color>)
private val quickActions = listOf(
    DesktopQuickAction("每日推荐", "daily_songs", MeloXSymbol.Calendar, listOf(Color(0xFF5856D6), Color(0xFF007AFF), Color(0xFF00C7BE))),
    DesktopQuickAction("心动模式", "heart_mode", MeloXSymbol.Heart, listOf(Color(0xFFFF9500), Color(0xFFFF3B30))),
    DesktopQuickAction("私人漫游", "private_roaming", MeloXSymbol.Walk, listOf(Color.Black, Color(0xFF5856D6), Color.Gray)),
    DesktopQuickAction("热歌榜", "hot_songs", MeloXSymbol.Flame, listOf(Color(0xFFFF3B30), Color(0xFFFF9500))),
    DesktopQuickAction("私人雷达", "private_radar", MeloXSymbol.Podcast, listOf(Color(0xFFAF52DE), Color(0xFF007AFF))),
    DesktopQuickAction("相似歌曲", "similar_songs", MeloXSymbol.Library, listOf(Color(0xFF30B0C7), Color(0xFF5856D6))),
)

@Composable
internal fun MeloXDesktopHome(content: NeteaseHomeContent?, activeAction: String?, loading: Boolean, error: String?,
    onAction: (String) -> Unit, onPlaylist: (NeteasePlaylistSummary) -> Unit) {
    val reserved = LocalDesktopInspectorWidth.current
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-home")) {
        val heroWidth = meloXDesktopShelfWidth(maxWidth.value - reserved, 200, 20).dp
        val mediaWidth = meloXDesktopShelfWidth(maxWidth.value - reserved, 145, 24).dp
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 52.dp, end = 52.dp,
            top = 22.dp, bottom = 104.dp), verticalArrangement = Arrangement.spacedBy(32.dp)) {
            item { Text(desktopString("ui.navigation.home"), fontSize = 46.sp, fontWeight = FontWeight.Bold) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DesktopSectionHeader(desktopString("ui.home.section.quick_actions"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(end = reserved.dp)) {
                        items(quickActions, key = { it.key }) { action ->
                            DesktopHeroSurface(heroWidth, activeAction != null, "desktop-home-action:${action.key}", { onAction(action.original) }) {
                                Box(Modifier.fillMaxSize().background(Brush.linearGradient(action.colors)), contentAlignment = Alignment.BottomEnd) {
                                    MeloXSymbolIcon(action.symbol, Modifier.offset(x = heroWidth * .1f, y = heroWidth * 4f / 3f * .08f),
                                        size = (heroWidth.value * 4f / 3f * .34f).coerceAtMost(150f).toInt(), color = Color.White.copy(alpha = .22f))
                                }
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .15f), Color.Black.copy(alpha = .78f)))))
                                Column(Modifier.fillMaxSize().padding(24.dp)) {
                                    Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MeloXSymbolIcon(MeloXSymbol.MusicNote, size = 15, color = Color.White)
                                        Text("MeloX", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    Text(desktopString("ui.home.action.${action.key}.eyebrow"), color = Color.White.copy(alpha = .68f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                    Text(desktopString("ui.home.action.${action.key}"), Modifier.padding(top = 5.dp), color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                                    Text(desktopString("ui.home.action.${action.key}.subtitle"), Modifier.padding(top = 5.dp), color = Color.White.copy(alpha = .92f), fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                                }
                                if (activeAction == action.original) CircularProgressIndicator(Modifier.align(Alignment.Center).size(24.dp), color = Color.White)
                            }
                        }
                    }
                }
            }
            if (content?.playlists?.isNotEmpty() == true) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DesktopSectionHeader(desktopString("ui.home.section.recommended_playlists"))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(end = reserved.dp)) {
                            items(content.playlists.take(7), key = { it.id }) { playlist ->
                                DesktopHeroSurface(heroWidth, false, "desktop-home-playlist:${playlist.id}", { onPlaylist(playlist) }) {
                                    MeloXArtworkImage(playlist.coverUrl, Color.DarkGray, Modifier.fillMaxSize())
                                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
                                    Text(playlist.name, Modifier.align(Alignment.BottomStart).padding(24.dp), fontSize = 25.sp,
                                        fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            if (content?.newSongs?.isNotEmpty() == true) item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DesktopSectionHeader(desktopString("ui.navigation.library.history"))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        items(content.newSongs.take(8), key = { it.id }) { song ->
                            Column(Modifier.width(mediaWidth).testTag("desktop-home-track:${song.id}").clickable(role = Role.Button) {
                                PlaybackCommands.playQueue(content.newSongs, song.id)
                            }) {
                                MeloXArtworkImage(song.artworkUrl, Color.DarkGray, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(9.dp)))
                                Text(song.name, Modifier.padding(top = 8.dp), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artists, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (loading) item { CircularProgressIndicator(Modifier.size(24.dp)) }
            if (error != null) item { Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun DesktopSectionHeader(title: String) {
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun DesktopHeroSurface(width: androidx.compose.ui.unit.Dp, disabled: Boolean, tag: String, onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hover by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hover && !disabled) 1.012f else 1f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 160), label = "desktop-hero-hover")
    Box(Modifier.width(width).height(width * 4f / 3f).graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(RoundedCornerShape(15.dp)).testTag(tag).hoverable(interaction)
        .clickable(enabled = !disabled, role = Role.Button, onClick = onClick), content = content)
}
