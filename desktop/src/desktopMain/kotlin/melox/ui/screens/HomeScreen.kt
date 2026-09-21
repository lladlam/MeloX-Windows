package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

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

// ── Quick action definitions ──

private data class QuickAction(
    val title: String,
    val subtitle: String,
    val gradient: Brush,
    val icon: MeloXSymbol,
)

private val quickActions = listOf(
    QuickAction(
        title = "每日推荐",
        subtitle = "为你精选",
        gradient = Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5722))),
        icon = MeloXSymbol.MusicNote,
    ),
    QuickAction(
        title = "热歌榜",
        subtitle = "实时更新",
        gradient = Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722))),
        icon = MeloXSymbol.Star,
    ),
    QuickAction(
        title = "私人FM",
        subtitle = "专属电台",
        gradient = Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF00BCD4))),
        icon = MeloXSymbol.Podcast,
    ),
    QuickAction(
        title = "心动模式",
        subtitle = "智能推荐",
        gradient = Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))),
        icon = MeloXSymbol.Heart,
    ),
)

// ── Placeholder playlist data ──

private fun placeholderPlaylists(source: MusicSource): List<MusicPlaylistSummary> {
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

// ── Source selector chips ──

@Composable
private fun SourceChips(
    selectedSource: SourceChip,
    onSourceSelected: (SourceChip) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceChip.entries.forEach { chip ->
            val isSelected = chip == selectedSource
            val interactionSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .then(
                        if (isSelected) {
                            Modifier.glassSurface(
                                shape = MeloXGlass.capsuleShape,
                                backgroundColor = chip.color,
                            )
                        } else {
                            Modifier.glassSurface(
                                shape = MeloXGlass.capsuleShape,
                                backgroundColor = MeloXColors.SurfaceVariant,
                            )
                        }
                    )
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onSourceSelected(chip)
                    }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = chip.label,
                    style = MeloXTypography.subheadline.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold
                        else FontWeight.Normal,
                    ),
                    color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
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
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        quickActions.forEach { action ->
            Box(
                modifier = Modifier
                    .width(150.dp)
                    .height(90.dp)
                    .clip(MeloXGlass.compactShape)
                    .background(action.gradient)
                    .clickable { onActionClick(action) },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                ) {
                    MeloXSymbolIcon(
                        symbol = action.icon,
                        color = Color.White,
                        size = 24,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = action.title,
                        style = MeloXTypography.subheadline.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = Color.White,
                    )
                    Text(
                        text = action.subtitle,
                        style = MeloXTypography.caption,
                        color = Color.White.copy(alpha = 0.75f),
                    )
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
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MeloXTypography.headline,
            color = MeloXColors.OnSurface,
            modifier = Modifier.weight(1f),
        )
        if (onSeeMore != null) {
            Row(
                modifier = Modifier.clickable { onSeeMore() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "查看更多",
                    style = MeloXTypography.caption,
                    color = MeloXColors.OnSurfaceVariant,
                )
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronRight,
                    color = MeloXColors.OnSurfaceVariant,
                    size = 14,
                )
            }
        }
    }
}

// ── Playlist card ──

@Composable
private fun PlaylistCard(
    playlist: MusicPlaylistSummary,
    placeholderColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onClick() },
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(MeloXGlass.compactShape)
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
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Play,
                        color = Color.White,
                        size = 8,
                    )
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        style = MeloXTypography.caption,
                        color = Color.White,
                    )
                }
            }
            // Playlist icon
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                modifier = Modifier.align(Alignment.Center),
                color = Color.White.copy(alpha = 0.20f),
                size = 36,
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playlist.title,
            style = MeloXTypography.subheadline,
            color = MeloXColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
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
            style = MeloXTypography.subheadline.copy(
                fontWeight = if (isPlaying) FontWeight.Bold
                else FontWeight.Medium,
            ),
            color = if (isPlaying) MeloXColors.Primary else MeloXColors.OnSurfaceVariant,
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
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = Color.White.copy(alpha = 0.6f),
                size = 16,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Track info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MeloXTypography.body.copy(
                    fontWeight = if (isPlaying) FontWeight.Bold
                    else FontWeight.Normal,
                ),
                color = if (isPlaying) MeloXColors.Primary else MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Duration
        val duration = formatDuration(track.durationMs)
        if (duration.isNotEmpty()) {
            Text(
                text = duration,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
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
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
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
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = MeloXColors.OnSurfaceVariant,
                size = 36,
            )
            Text(
                text = message,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
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
        // iOS-style top bar
        MeloXIosTopBar(
            title = "发现",
            actions = {
                IconButton(
                    onClick = { navState.navigateTo(Route.Search) },
                    modifier = Modifier.size(36.dp),
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Search,
                        color = MeloXColors.OnSurface,
                        size = 22,
                    )
                }
                IconButton(
                    onClick = { navState.navigateTo(Route.Settings) },
                    modifier = Modifier.size(36.dp),
                ) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.Settings,
                        color = MeloXColors.OnSurface,
                        size = 22,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Source chips
        SourceChips(
            selectedSource = selectedSource,
            onSourceSelected = { source -> selectedSource = source },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Content
        when {
            isLoading -> LoadingState()
            error != null -> EmptyState(error ?: "未知错误")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                val neteaseId = track.id.value.toLongOrNull()
                                if (neteaseId != null && track.id.source == MusicSource.Netease) {
                                    val url = "https://music.163.com/song/media/outer/url?id=$neteaseId"
                                    AudioPlayer.play(url, track)
                                } else {
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
