package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicPage
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.SearchCapability
import melox.player.AudioPlayer
import melox.provider.kugou.KugouProvider
import melox.provider.kuwo.KuwoProvider
import melox.provider.netease.NeteaseProvider
import melox.provider.qqmusic.QQMusicProvider
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import java.awt.Cursor

// ── Provider registry ──

private enum class SourceChip(
    val label: String,
    val color: Color,
    val provider: SearchCapability,
    val source: MusicSource,
) {
    Netease("网易云", MeloXColors.sourceColors["netease"]!!, NeteaseProvider(), MusicSource.Netease),
    QQ("QQ", MeloXColors.sourceColors["qq"]!!, QQMusicProvider(), MusicSource.QQMusic),
    Kugou("酷狗", MeloXColors.sourceColors["kugou"]!!, KugouProvider(), MusicSource.Kugou),
    Kuwo("酷我", MeloXColors.sourceColors["kuwo"]!!, KuwoProvider(), MusicSource.Kuwo),
}

// ── Quick action gradient definitions ──

private data class QuickAction(
    val title: String,
    val subtitle: String,
    val gradient: Brush,
    val icon: String,
)

private val quickActions = listOf(
    QuickAction(
        title = "每日推荐",
        subtitle = "为你精选",
        gradient = Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5722))),
        icon = "🎵",
    ),
    QuickAction(
        title = "热歌榜",
        subtitle = "实时更新",
        gradient = Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722))),
        icon = "🔥",
    ),
    QuickAction(
        title = "私人FM",
        subtitle = "专属电台",
        gradient = Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF00BCD4))),
        icon = "📻",
    ),
    QuickAction(
        title = "心动模式",
        subtitle = "智能推荐",
        gradient = Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))),
        icon = "💓",
    ),
)

// ── Placeholder playlist data ──

private fun placeholderPlaylists(source: MusicSource): List<MusicPlaylistSummary> {
    val accent = MeloXColors.sourceColors[source.storageValue] ?: MeloXColors.Primary
    return listOf(
        MusicPlaylistSummary(
            id = MusicResourceId(source, "pl_1"),
            title = "华语经典情歌",
            playCount = 1280_0000,
            trackCount = 30,
        ),
        MusicPlaylistSummary(
            id = MusicResourceId(source, "pl_2"),
            title = "深夜治愈歌单",
            playCount = 860_0000,
            trackCount = 25,
        ),
        MusicPlaylistSummary(
            id = MusicResourceId(source, "pl_3"),
            title = "工作学习BGM",
            playCount = 540_0000,
            trackCount = 40,
        ),
        MusicPlaylistSummary(
            id = MusicResourceId(source, "pl_4"),
            title = "跑步运动节奏",
            playCount = 320_0000,
            trackCount = 35,
        ),
        MusicPlaylistSummary(
            id = MusicResourceId(source, "pl_5"),
            title = "新歌速递",
            playCount = 210_0000,
            trackCount = 20,
        ),
    )
}

// ── Formatting helpers ──

private fun formatPlayCount(count: Long?): String {
    if (count == null) return ""
    return when {
        count >= 1_0000_0000 -> "${count / 1_0000_0000}亿"
        count >= 1_0000 -> "${count / 1_0000}万"
        else -> count.toString()
    }
}

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

// ── Top bar ──

@Composable
private fun HomeTopBar(
    onSettingsClick: () -> Unit,
    onSearchClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "发现",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            color = MeloXColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MeloXColors.SurfaceVariant)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
            ) {
                Text(
                    text = "⌕",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeloXColors.TextPrimary,
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MeloXColors.SurfaceVariant)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
            ) {
                Text(
                    text = "⚙",
                    style = MaterialTheme.typography.titleMedium,
                    color = MeloXColors.TextPrimary,
                )
            }
        }
    }
}

// ── Source selector chips ──

@Composable
private fun SourceChips(
    selectedSource: SourceChip,
    onSourceSelected: (SourceChip) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SourceChip.entries.forEach { chip ->
            val isSelected = chip == selectedSource
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onSourceSelected(chip) }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) chip.color else MeloXColors.ChipBackground,
            ) {
                Text(
                    text = chip.label,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (isSelected) Color.White else MeloXColors.TextSecondary,
                )
            }
        }
    }
}

// ── Quick action cards ──

@Composable
private fun QuickActionCards(
    onActionClick: (QuickAction) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        quickActions.forEach { action ->
            Surface(
                modifier = Modifier
                    .width(150.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onActionClick(action) }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(action.gradient),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                    ) {
                        Text(
                            text = action.icon,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = action.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = MeloXLanTingProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            ),
                            color = Color.White,
                        )
                        Text(
                            text = action.subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = MeloXLanTingProFontFamily,
                                fontSize = 11.sp,
                            ),
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }
                }
            }
        }
    }
}

// ── Section header ──

@Composable
private fun SectionHeader(
    title: String,
    onSeeMore: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            ),
            color = MeloXColors.TextPrimary,
            modifier = Modifier.weight(1f),
        )
        if (onSeeMore != null) {
            Text(
                text = "查看更多 >",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                ),
                color = MeloXColors.TextSecondary,
                modifier = Modifier
                    .clickable { onSeeMore() }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
                    .padding(start = 8.dp),
            )
        }
    }
}

// ── Playlist horizontal scrolling section ──

@Composable
private fun PlaylistCarousel(
    playlists: List<MusicPlaylistSummary>,
    sourceColor: Color,
    onPlaylistClick: (MusicPlaylistSummary) -> Unit,
    onSeeMore: (() -> Unit)?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = "推荐歌单", onSeeMore = onSeeMore)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            playlists.forEachIndexed { index, playlist ->
                PlaylistCard(
                    playlist = playlist,
                    placeholderColor = sourceColor.copy(alpha = 0.3f + (index % 3) * 0.15f),
                    onClick = { onPlaylistClick(playlist) },
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: MusicPlaylistSummary,
    placeholderColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            placeholderColor,
                            placeholderColor.copy(alpha = 0.5f),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                    ),
                ),
        ) {
            // Play count overlay
            if (playlist.playCount != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("▶", fontSize = 8.sp, color = Color.White)
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }
            // Playlist icon
            Text(
                text = "♪",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
            ),
            color = MeloXColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Song list item ──

@Composable
private fun SongListItem(
    index: Int,
    track: MusicTrack,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR)))
            .then(
                if (isPlaying) Modifier.background(MeloXColors.SurfaceVariant.copy(alpha = 0.5f))
                else Modifier
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Track number
        Text(
            text = "%02d".format(index + 1),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
            ),
            color = if (isPlaying) MeloXColors.Primary else MeloXColors.TextTertiary,
            modifier = Modifier.width(30.dp),
        )

        // Track artwork placeholder
        val sourceColor = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.Primary
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(sourceColor.copy(alpha = 0.4f), sourceColor.copy(alpha = 0.15f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("♪", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                ),
                color = if (isPlaying) MeloXColors.Primary else MeloXColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
                color = MeloXColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Duration
        val duration = formatDuration(track.durationMs)
        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
                color = MeloXColors.TextTertiary,
            )
        }
    }
}

// ── Loading state ──

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(
                color = MeloXColors.Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = "加载中...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
                color = MeloXColors.TextSecondary,
            )
        }
    }
}

// ── Empty state ──

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "🎵",
                style = MaterialTheme.typography.displayMedium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
                color = MeloXColors.TextSecondary,
            )
        }
    }
}

// ── Main HomeScreen composable ──

@Composable
fun HomeScreen(
    navState: MeloXNavState,
) {
    var selectedSource by remember { mutableStateOf(SourceChip.Netease) }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val playerState by AudioPlayer.state.collectAsState()

    // Search on source change
    LaunchedEffect(selectedSource) {
        isLoading = true
        error = null
        tracks = emptyList()
        try {
            val result: MusicPage<MusicTrack> = selectedSource.provider.searchSongs(
                query = "热门",
                page = 1,
                pageSize = 20,
            )
            tracks = result.items
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        // Top bar
        HomeTopBar(
            onSettingsClick = { navState.navigateTo(Route.Settings) },
            onSearchClick = { navState.navigateTo(Route.Search) },
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Source chips
        SourceChips(
            selectedSource = selectedSource,
            onSourceSelected = { source -> selectedSource = source },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
            isLoading -> LoadingState()
            error != null -> EmptyState(error ?: "未知错误")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Quick action cards
                    item {
                        QuickActionCards(onActionClick = { /* TODO */ })
                    }

                    // Playlist carousel
                    item {
                        PlaylistCarousel(
                            playlists = placeholderPlaylists(selectedSource.source),
                            sourceColor = selectedSource.color,
                            onPlaylistClick = { /* TODO */ },
                            onSeeMore = { /* TODO */ },
                        )
                    }

                    // Hot songs list
                    item {
                        SectionHeader(
                            title = "热门歌曲",
                            onSeeMore = { /* TODO */ },
                        )
                    }

                    itemsIndexed(tracks) { index, track ->
                        SongListItem(
                            index = index,
                            track = track,
                            isPlaying = currentTrack?.id == track.id && playerState.isPlaying,
                            onClick = {
                                // Build a NetEase playback URL for the track
                                val neteaseId = track.id.value.toLongOrNull()
                                if (neteaseId != null && track.id.source == MusicSource.Netease) {
                                    val url = "https://music.163.com/song/media/outer/url?id=$neteaseId"
                                    AudioPlayer.play(url, track)
                                } else {
                                    // For non-Netease, still try play with a generic approach
                                    AudioPlayer.play(track.id.value, track)
                                }
                            },
                        )
                    }

                    if (tracks.isEmpty() && !isLoading) {
                        item { EmptyState("暂无数据，换个搜索词试试") }
                    }
                }
            }
        }
    }
}
