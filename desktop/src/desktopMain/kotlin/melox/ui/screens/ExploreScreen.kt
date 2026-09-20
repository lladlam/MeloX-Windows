package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import melox.provider.netease.NeteaseProvider
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import java.awt.Cursor

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

// ── Top bar ──

@Composable
private fun ExploreTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "探索",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            ),
            color = MeloXColors.TextPrimary,
        )
    }
}

// ── Category chips ──

@Composable
private fun CategoryChips(
    selectedCategory: CategoryChip,
    onCategorySelected: (CategoryChip) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.forEach { chip ->
            val isSelected = chip == selectedCategory
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onCategorySelected(chip) }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) MeloXColors.ChipBackgroundSelected else MeloXColors.ChipBackground,
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

// ── Playlist grid card ──

@Composable
private fun ExplorePlaylistCard(
    playlist: MusicPlaylistSummary,
    gradient: Brush,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
    ) {
        // Artwork placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(gradient),
            contentAlignment = Alignment.Center,
        ) {
            // Play count overlay
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
                    Text("▶", fontSize = 8.sp, color = Color.White)
                    Text(
                        text = formatPlayCount(playlist.playCount),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }

            // Center icon
            Text(
                text = "♪",
                style = MaterialTheme.typography.displayLarge,
                color = Color.White.copy(alpha = 0.15f),
            )

            // Track count badge
            if (playlist.trackCount != null) {
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.45f),
                ) {
                    Text(
                        text = "${playlist.trackCount}首",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Title
        Text(
            text = playlist.title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
            color = MeloXColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        // Creator
        val creatorName = playlist.creatorName
        if (creatorName != null) {
            Text(
                text = creatorName,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 11.sp,
                ),
                color = MeloXColors.TextSecondary,
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
                text = "🔍",
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

    // Search when category changes
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
        // Top bar
        ExploreTopBar()

        Spacer(modifier = Modifier.height(4.dp))

        // Category chips
        CategoryChips(
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it },
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Content
        when {
            isLoading -> LoadingState()
            error != null -> EmptyState(error ?: "未知错误")
            playlists.isEmpty() -> EmptyState("暂无歌单数据")
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
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
