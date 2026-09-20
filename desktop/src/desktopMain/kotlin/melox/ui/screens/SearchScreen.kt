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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.*
import melox.player.AudioPlayer
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

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
        SearchBar(
            query = query,
            onQueryChange = { query = it },
            onSearch = { performSearch(query) },
            onClear = {
                query = ""
                searchResults = emptyList()
                hasSearched = false
                searchError = null
            },
            focusRequester = focusRequester,
        )

        ScopeSelector(
            selectedScope = selectedScope,
            onScopeSelected = { selectedScope = it },
        )

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

@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MeloXColors.SurfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⌕",
                color = MeloXColors.TextTertiary,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索歌曲、歌手、专辑...",
                        color = MeloXColors.TextTertiary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                onSearch()
                                true
                            } else {
                                false
                            }
                        },
                    textStyle = TextStyle(
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MeloXColors.Primary),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(MeloXColors.SurfaceHigh)
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "✕",
                        color = MeloXColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScopeSelector(
    selectedScope: SearchScope,
    onScopeSelected: (SearchScope) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchScope.entries.forEach { scope ->
            val isSelected = scope == selectedScope
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            isSelected -> MeloXColors.ChipBackgroundSelected
                            isHovered -> MeloXColors.SurfaceHigh
                            else -> MeloXColors.ChipBackground
                        },
                    )
                    .hoverable(interactionSource)
                    .clickable(interactionSource = interactionSource, indication = null) {
                        onScopeSelected(scope)
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    text = scope.label,
                    color = if (isSelected) MeloXColors.OnPrimary else MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
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
            SectionTitle("推荐歌单")
            Spacer(modifier = Modifier.height(8.dp))
        }
        item {
            RecommendedPlaylistsRow(onQuerySelected = onQuerySelected)
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            SectionTitle("热门搜索")
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(trendingSearches.chunked(4)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
            SectionTitle("搜索历史")
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(searchHistory.chunked(4)) { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
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
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = MeloXColors.TextPrimary,
        fontFamily = MeloXLanTingProFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
    )
}

@Composable
private fun RecommendedPlaylistsRow(onQuerySelected: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recommendedPlaylists) { (name, color) ->
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Column(
                modifier = Modifier
                    .width(100.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isHovered) MeloXColors.CardBackgroundHover else MeloXColors.CardBackground,
                    )
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
                    Text("♫", color = color, fontSize = 28.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) MeloXColors.SurfaceHigh else MeloXColors.ChipBackground)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = term,
            color = MeloXColors.TextSecondary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 12.sp,
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isHovered) MeloXColors.SurfaceHigh else MeloXColors.ChipBackground)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "◷",
                color = MeloXColors.TextTertiary,
                fontSize = 11.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = term,
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
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
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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
                when {
                    isHovered -> MeloXColors.SurfaceVariant
                    else -> MeloXColors.Background
                },
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlay)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Artwork placeholder
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
            Text(
                text = if (isCurrentTrack && isPlaying) "♫" else "♪",
                color = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.TextTertiary,
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = if (isCurrentTrack) MeloXColors.Primary else MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
                fontWeight = if (isCurrentTrack) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artistText,
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val albumName = track.album?.name
                if (albumName?.isNotBlank() == true) {
                    Text(
                        text = " · $albumName",
                        color = MeloXColors.TextTertiary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 12.sp,
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
            color = MeloXColors.TextTertiary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 12.sp,
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Play button
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MeloXColors.SurfaceVariant)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isCurrentTrack && isPlaying) "⏸" else "▶",
                color = MeloXColors.OnSurface,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SearchSourceBadge(source: MusicSource) {
    val badgeColor = MeloXColors.sourceColors[source.storageValue] ?: MeloXColors.TextTertiary

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = source.displayName,
            color = badgeColor,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 10.sp,
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
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
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
            Text(
                text = "⌕",
                fontSize = 48.sp,
                color = MeloXColors.TextTertiary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "没有找到相关结果",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "换个关键词试试",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
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
            Text(
                text = "⚠",
                fontSize = 48.sp,
                color = MeloXColors.Warning,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "搜索出错",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MeloXColors.Primary)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "重试",
                    color = MeloXColors.OnPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
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
