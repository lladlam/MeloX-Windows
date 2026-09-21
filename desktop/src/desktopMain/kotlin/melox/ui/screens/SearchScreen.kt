package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.*
import melox.player.AudioPlayer
import melox.ui.foundation.*
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

private enum class SearchScope(val label: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Albums("专辑"),
    Artists("歌手"),
}

private val trendingSearches = listOf(
    "周杰伦", "陈奕迅", "邓紫棋", "林俊杰",
    "薛之谦", "李荣浩", "华晨宇", "毛不易",
)

private val searchHistory = listOf(
    "起风了", "孤勇者", "稻香", "平凡之路",
    "光辉岁月", "错位时空",
)

private val recommendedPlaylists = listOf(
    "每日推荐" to MeloXColors.Primary,
    "华语流行" to MeloXColors.Secondary,
    "经典老歌" to MeloXColors.Warning,
    "轻音乐" to MeloXColors.Success,
    "说唱精选" to Color(0xFF7C4DFF),
    "电子舞曲" to Color(0xFFFF6E40),
)

private fun mockSearchAll(query: String): List<MusicTrack> {
    val allTracks = listOf(
        MusicTrack(
            id = MusicResourceId(MusicSource.Netease, "1001"),
            title = "起风了",
            artists = listOf(MusicArtistRef(name = "买辣椒也用券")),
            album = MusicAlbumRef(name = "起风了"),
            durationMs = 325_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, "2001"),
            title = "孤勇者",
            artists = listOf(MusicArtistRef(name = "陈奕迅")),
            album = MusicAlbumRef(name = "孤勇者"),
            durationMs = 262_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Kugou, "3001"),
            title = "漠河舞厅",
            artists = listOf(MusicArtistRef(name = "柳爽")),
            album = MusicAlbumRef(name = "漠河舞厅"),
            durationMs = 288_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Kuwo, "4001"),
            title = "错位时空",
            artists = listOf(MusicArtistRef(name = "艾辰")),
            album = MusicAlbumRef(name = "错位时空"),
            durationMs = 252_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Netease, "1002"),
            title = "半生雪",
            artists = listOf(MusicArtistRef(name = "是七叔呢")),
            album = MusicAlbumRef(name = "半生雪"),
            durationMs = 216_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, "2002"),
            title = "稻香",
            artists = listOf(MusicArtistRef(name = "周杰伦")),
            album = MusicAlbumRef(name = "魔杰座"),
            durationMs = 223_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Netease, "1003"),
            title = "光辉岁月",
            artists = listOf(MusicArtistRef(name = "Beyond")),
            album = MusicAlbumRef(name = "命运派对"),
            durationMs = 295_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Kugou, "3002"),
            title = "平凡之路",
            artists = listOf(MusicArtistRef(name = "朴树")),
            album = MusicAlbumRef(name = "猎户星座"),
            durationMs = 300_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, "2003"),
            title = "晴天",
            artists = listOf(MusicArtistRef(name = "周杰伦")),
            album = MusicAlbumRef(name = "叶惠美"),
            durationMs = 269_000,
        ),
        MusicTrack(
            id = MusicResourceId(MusicSource.Kuwo, "4002"),
            title = "一路生花",
            artists = listOf(MusicArtistRef(name = "温奕心")),
            album = MusicAlbumRef(name = "一路生花"),
            durationMs = 241_000,
        ),
    )
    val q = query.lowercase()
    return allTracks.filter {
        it.title.lowercase().contains(q) ||
            it.artistText.lowercase().contains(q) ||
            (it.album?.name?.lowercase()?.contains(q) == true)
    }
}

@Composable
fun SearchScreen() {
    var query by remember { mutableStateOf("") }
    var selectedScope by remember { mutableStateOf(SearchScope.Songs) }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val playerState by AudioPlayer.state.collectAsState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun performSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        hasSearched = true
        isSearching = true
        searchError = null
        searchResults = emptyList()

        searchResults = mockSearchAll(trimmed)
        isSearching = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        MeloXIosTopBar(title = "搜索")

        MeloXGlassTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = "搜索歌曲、歌手、专辑...",
            leadingIcon = {
                MeloXSymbolIcon(symbol = MeloXSymbol.Search, color = MeloXColors.OnSurfaceVariant, size = 18)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MeloXColors.OnBackground.copy(alpha = 0.055f))
                            .clickable {
                                query = ""
                                searchResults = emptyList()
                                hasSearched = false
                                searchError = null
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(symbol = MeloXSymbol.XMark, color = MeloXColors.OnSurfaceVariant, size = 14)
                    }
                }
            },
        )

        // Scope pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchScope.entries.forEach { scope ->
                val isSelected = scope == selectedScope
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
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
                            selectedScope = scope
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = scope.label,
                        style = MeloXTypography.caption.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !hasSearched -> DiscoveryView(
                    onQuerySelected = { q ->
                        query = q
                        performSearch(q)
                    },
                )
                isSearching -> LoadingView()
                searchError != null -> ErrorView(
                    message = searchError!!,
                    onRetry = { performSearch(query) },
                )
                searchResults.isEmpty() -> EmptySearchView()
                else -> ResultsView(
                    results = searchResults,
                    currentTrack = currentTrack,
                    isPlaying = playerState.isPlaying,
                    onPlayTrack = { track ->
                        AudioPlayer.play(
                            "https://music.163.com/song/media/outer/url?id=${track.id.value}",
                            track,
                        )
                    },
                )
            }
        }
    }
}

// ── Discovery View ──

@Composable
private fun DiscoveryView(onQuerySelected: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            Text(
                text = "推荐歌单",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            RecommendedPlaylistsRow(onQuerySelected = onQuerySelected)
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text(
                text = "热门搜索",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(trendingSearches.chunked(4)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { term ->
                    TrendingChip(
                        term = term,
                        modifier = Modifier.weight(1f),
                        onClick = { onQuerySelected(term) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Text(
                text = "搜索历史",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(searchHistory.chunked(4)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { term ->
                    HistoryChip(
                        term = term,
                        modifier = Modifier.weight(1f),
                        onClick = { onQuerySelected(term) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RecommendedPlaylistsRow(onQuerySelected: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(recommendedPlaylists) { (name, color) ->
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Column(
                modifier = Modifier
                    .width(100.dp)
                    .glassSurface(MeloXGlass.cardShape, MeloXColors.SurfaceVariant)
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onQuerySelected(name)
                    }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(symbol = MeloXSymbol.MusicNote, color = color, size = 28)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = name,
                    style = MeloXTypography.caption.copy(fontWeight = FontWeight.Medium),
                    color = MeloXColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrendingChip(
    term: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .clip(MeloXGlass.capsuleShape)
            .background(
                if (isHovered) MeloXColors.SurfaceVariant
                else MeloXColors.OnBackground.copy(alpha = 0.055f),
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = term,
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistoryChip(
    term: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .clip(MeloXGlass.capsuleShape)
            .background(
                if (isHovered) MeloXColors.SurfaceVariant
                else MeloXColors.OnBackground.copy(alpha = 0.055f),
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeloXSymbolIcon(symbol = MeloXSymbol.Clock, color = MeloXColors.OnSurfaceVariant, size = 12)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = term,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Results View ──

@Composable
private fun ResultsView(
    results: List<MusicTrack>,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onPlayTrack: (MusicTrack) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        item {
            Text(
                text = "找到 ${results.size} 首歌曲",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        itemsIndexed(results) { _, track ->
            val isCurrentTrack = currentTrack?.id?.value == track.id.value
            SearchResultItem(
                track = track,
                isCurrentTrack = isCurrentTrack,
                isPlaying = isCurrentTrack && isPlaying,
                onPlay = { onPlayTrack(track) },
            )
        }
    }
}

@Composable
private fun SearchResultItem(
    track: MusicTrack,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                if (isHovered) MeloXColors.SurfaceVariant else MeloXColors.Background,
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    MeloXColors.sourceColors[track.id.source.storageValue]?.copy(alpha = 0.2f)
                        ?: MeloXColors.SurfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = if (isCurrentTrack && isPlaying) MeloXSymbol.MusicNote else MeloXSymbol.Play,
                color = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.OnSurfaceVariant,
                size = 18,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MeloXTypography.body.copy(
                    fontWeight = if (isCurrentTrack) FontWeight.Medium else FontWeight.Normal,
                ),
                color = if (isCurrentTrack) MeloXColors.Primary else MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artistText,
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val albumName = track.album?.name
                if (albumName?.isNotBlank() == true) {
                    Text(
                        text = " · $albumName",
                        style = MeloXTypography.subheadline,
                        color = MeloXColors.OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        SearchSourceBadge(source = track.id.source)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formatSearchDuration(track.durationMs),
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant,
        )

        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(MeloXColors.SurfaceVariant)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = if (isCurrentTrack && isPlaying) MeloXSymbol.Pause else MeloXSymbol.Play,
                color = MeloXColors.OnSurface,
                size = 14,
            )
        }
    }
}

@Composable
private fun SearchSourceBadge(source: MusicSource) {
    val badgeColor = MeloXColors.sourceColors[source.storageValue] ?: MeloXColors.OnSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = source.displayName,
            style = TextStyle(fontSize = 10.sp),
            color = badgeColor,
        )
    }
}

// ── States ──

@Composable
private fun LoadingView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = MeloXColors.Primary,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "搜索中...",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptySearchView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(symbol = MeloXSymbol.Search, color = MeloXColors.OnSurfaceVariant, size = 48)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "没有找到相关结果",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "换个关键词试试",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(symbol = MeloXSymbol.Refresh, color = MeloXColors.Warning, size = 48)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "搜索出错",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .glassButton(MeloXColors.Primary)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "重试",
                    style = MeloXTypography.caption,
                    color = MeloXColors.OnPrimary,
                )
            }
        }
    }
}

private fun formatSearchDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
