package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicPage
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.provider.netease.NeteaseProvider
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

// ── Category data ──

private data class CategoryChip(
    val label: String,
    val searchQuery: String,
)

private val categories = listOf(
    CategoryChip("推荐歌单", "推荐歌单"),
    CategoryChip("排行榜", "排行榜"),
    CategoryChip("精品歌单", "精品歌单"),
    CategoryChip("华语", "华语"),
    CategoryChip("欧美", "欧美"),
    CategoryChip("流行", "流行"),
    CategoryChip("摇滚", "摇滚"),
    CategoryChip("民谣", "民谣"),
    CategoryChip("电子", "电子"),
)

// ── Playlist card color gradients ──

private val cardGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFFE91E63), Color(0xFFFF5722))),
    Brush.linearGradient(listOf(Color(0xFF2196F3), Color(0xFF00BCD4))),
    Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF8BC34A))),
    Brush.linearGradient(listOf(Color(0xFFFF9800), Color(0xFFFF5722))),
    Brush.linearGradient(listOf(Color(0xFF9C27B0), Color(0xFFE91E63))),
    Brush.linearGradient(listOf(Color(0xFF009688), Color(0xFF4CAF50))),
    Brush.linearGradient(listOf(Color(0xFF3F51B5), Color(0xFF2196F3))),
    Brush.linearGradient(listOf(Color(0xFFFF5722), Color(0xFFFF9800))),
    Brush.linearGradient(listOf(Color(0xFF795548), Color(0xFF9E9E9E))),
    Brush.linearGradient(listOf(Color(0xFF607D8B), Color(0xFF455A64))),
)

// ── Format helpers ──

private fun formatPlayCount(count: Long?): String {
    if (count == null) return ""
    return when {
        count >= 1_0000_0000 -> "${count / 1_0000_0000}亿"
        count >= 1_0000 -> "${count / 1_0000}万"
        else -> count.toString()
    }
}

// ── Playlist grid card ──

@Composable
private fun ExplorePlaylistCard(
    playlist: MusicPlaylistSummary,
    gradient: Brush,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(MeloXGlass.cardShape, MeloXColors.SurfaceVariant)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(gradient),
            contentAlignment = Alignment.Center,
        ) {
            if (playlist.playCount != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MeloXSymbolIcon(symbol = MeloXSymbol.Play, color = Color.White, size = 10)
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        style = MeloXTypography.caption,
                        color = Color.White,
                        fontSize = 10.sp,
                    )
                }
            }

            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = Color.White.copy(alpha = 0.15f),
                size = 48,
            )

            if (playlist.trackCount != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "${playlist.trackCount}首",
                        style = MeloXTypography.caption,
                        color = Color.White,
                        fontSize = 10.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            style = MeloXTypography.caption.copy(fontWeight = FontWeight.Medium),
            color = MeloXColors.OnSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val creatorName = playlist.creatorName
        if (creatorName != null) {
            Text(
                text = creatorName,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                text = "探索中...",
                style = MeloXTypography.body,
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
                symbol = MeloXSymbol.Search,
                color = MeloXColors.OnSurfaceVariant,
                size = 48,
            )
            Text(
                text = message,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

// ── Generate playlist cards from search results ──

private fun tracksToPlaylists(tracks: List<MusicTrack>, source: MusicSource): List<MusicPlaylistSummary> {
    if (tracks.isEmpty()) return emptyList()
    return tracks.chunked(6).mapIndexed { chunkIndex, chunk ->
        MusicPlaylistSummary(
            id = MusicResourceId(source, "explore_${chunkIndex}_${chunk.firstOrNull()?.id?.value}"),
            title = chunk.firstOrNull()?.let { "${it.title} & 更多" } ?: "推荐歌单",
            creatorName = chunk.firstOrNull()?.artistText,
            trackCount = chunk.size,
            playCount = (chunk.size.toLong() * 10000 * (3 + chunkIndex)),
        )
    }
}

// ── Main ExploreScreen composable ──

@Composable
fun ExploreScreen(
    navState: MeloXNavState,
) {
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var tracks by remember { mutableStateOf(emptyList<MusicTrack>()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedCategory) {
        isLoading = true
        error = null
        tracks = emptyList()
        try {
            val result: MusicPage<MusicTrack> = NeteaseProvider().searchSongs(
                query = selectedCategory.searchQuery,
                page = 1,
                pageSize = 30,
            )
            tracks = result.items
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            isLoading = false
        }
    }

    val playlists = remember(tracks) {
        tracksToPlaylists(tracks, MusicSource.Netease)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        MeloXIosTopBar(title = "探索")

        Spacer(modifier = Modifier.height(4.dp))

        // Category chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // First row
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.take(4).forEach { chip ->
                    val isSelected = chip == selectedCategory
                    val interactionSource = remember { MutableInteractionSource() }
                    val isHovered by interactionSource.collectIsHoveredAsState()

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(MeloXGlass.capsuleShape)
                            .background(
                                when {
                                    isSelected -> MeloXColors.Primary
                                    isHovered -> MeloXColors.SurfaceVariant
                                    else -> MeloXColors.OnBackground.copy(alpha = 0.055f)
                                },
                            )
                            .hoverable(interactionSource)
                            .clickable(interactionSource = interactionSource, indication = null) {
                                selectedCategory = chip
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = chip.label,
                            style = MeloXTypography.subheadline.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            ),
                            color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            categories.drop(4).forEach { chip ->
                val isSelected = chip == selectedCategory
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(MeloXGlass.capsuleShape)
                        .background(
                            when {
                                isSelected -> MeloXColors.Primary
                                isHovered -> MeloXColors.SurfaceVariant
                                else -> MeloXColors.OnBackground.copy(alpha = 0.055f)
                            },
                        )
                        .hoverable(interactionSource)
                        .clickable(interactionSource = interactionSource, indication = null) {
                            selectedCategory = chip
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chip.label,
                        style = MeloXTypography.subheadline.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> LoadingState()
            error != null -> EmptyState(error ?: "未知错误")
            playlists.isEmpty() -> EmptyState("暂无歌单数据")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    state = rememberLazyGridState(),
                ) {
                    itemsIndexed(playlists) { index, playlist ->
                        ExplorePlaylistCard(
                            playlist = playlist,
                            gradient = cardGradients[index % cardGradients.size],
                            onClick = {
                                navState.navigateTo(
                                    Route.PlaylistDetail(
                                        id = playlist.id.value,
                                        name = playlist.title,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
