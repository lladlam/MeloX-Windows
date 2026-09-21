package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.*
import melox.player.AudioPlayer
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

private enum class LibraryTab(val label: String) {
    Songs("歌曲"),
    Playlists("歌单"),
    Podcasts("播客"),
    Downloads("下载"),
    History("历史"),
}

private val mockTracks = listOf(
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
)

private val mockPlaylists = listOf(
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.Netease, "pl_001"),
        title = "我喜欢的音乐",
        trackCount = 128,
        creatorName = "用户",
    ),
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.QQMusic, "pl_002"),
        title = "华语经典",
        trackCount = 56,
        creatorName = "官方",
    ),
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.Kugou, "pl_003"),
        title = "助眠轻音乐",
        trackCount = 34,
        creatorName = "用户",
    ),
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.Netease, "pl_004"),
        title = "跑步运动",
        trackCount = 42,
        creatorName = "用户",
    ),
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.Kuwo, "pl_005"),
        title = "深夜电台",
        trackCount = 20,
        creatorName = "电台",
    ),
    MusicPlaylistSummary(
        id = MusicResourceId(MusicSource.QQMusic, "pl_006"),
        title = "民谣合集",
        trackCount = 67,
        creatorName = "官方",
    ),
)

private val mockPodcasts = listOf(
    Pair("故事FM", "用你的声音，讲述你的故事"),
    Pair("忽左忽右", "人文社科深度播客"),
    Pair("日谈公园", "闲聊是一种生产力"),
    Pair("声东击西", "从不同角度探讨科技与社会"),
    Pair("小宇宙精选", "每日精选播客内容"),
)

internal data class DownloadedTrack(
    val track: MusicTrack,
    val fileSize: String,
)

private val mockDownloads = listOf(
    DownloadedTrack(mockTracks[0], "8.2 MB"),
    DownloadedTrack(mockTracks[1], "6.8 MB"),
    DownloadedTrack(mockTracks[5], "5.5 MB"),
    DownloadedTrack(mockTracks[6], "7.3 MB"),
)

private data class HistoryEntry(
    val track: MusicTrack,
    val timestamp: String,
)

private val mockHistory = listOf(
    HistoryEntry(mockTracks[0], "今天 14:32"),
    HistoryEntry(mockTracks[2], "今天 12:15"),
    HistoryEntry(mockTracks[5], "昨天 22:47"),
    HistoryEntry(mockTracks[3], "昨天 20:03"),
    HistoryEntry(mockTracks[7], "9月19日 18:30"),
    HistoryEntry(mockTracks[1], "9月18日 21:12"),
)

private val playlistCardColors = listOf(
    MeloXColors.Primary,
    MeloXColors.Secondary,
    MeloXColors.Warning,
    MeloXColors.Success,
    MeloXColors.GlassBlue,
    MeloXColors.GlassRed,
)

@Composable
fun LibraryScreen(
    navState: MeloXNavState? = null,
) {
    var selectedTab by remember { mutableStateOf(LibraryTab.Songs) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        MeloXIosTopBar(title = "音乐库")

        MeloXSegmentedControl(
            selectedIndex = selectedTab.ordinal,
            options = LibraryTab.entries.map { it.label },
            onSelected = { selectedTab = LibraryTab.entries[it] },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                LibraryTab.Songs -> SongsTab(navState = navState)
                LibraryTab.Playlists -> PlaylistsTab(navState = navState)
                LibraryTab.Podcasts -> PodcastsTab()
                LibraryTab.Downloads -> DownloadsTab()
                LibraryTab.History -> HistoryTab()
            }
        }
    }
}

// ── Songs Tab ──

@Composable
private fun SongsTab(navState: MeloXNavState?) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val isPlaying by AudioPlayer.state.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${mockTracks.size} 首",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            PlayAllButton(
                onClick = {
                    if (mockTracks.isNotEmpty()) {
                        AudioPlayer.play("https://music.163.com/song/media/outer/url?id=1001", mockTracks[0])
                    }
                },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            itemsIndexed(mockTracks) { index, track ->
                val isCurrentTrack = currentTrack?.id?.value == track.id.value
                SongListItem(
                    track = track,
                    number = index + 1,
                    isCurrentTrack = isCurrentTrack,
                    isPlaying = isCurrentTrack && isPlaying.isPlaying,
                    onClick = {
                        AudioPlayer.play("https://music.163.com/song/media/outer/url?id=${track.id.value}", track)
                    },
                )
            }
        }
    }
}

@Composable
private fun PlayAllButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .glassButton(MeloXColors.Primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MeloXSymbolIcon(symbol = MeloXSymbol.Play, color = MeloXColors.OnPrimary, size = 14)
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "播放全部",
            style = MeloXTypography.caption,
            color = MeloXColors.OnPrimary,
        )
    }
}

@Composable
private fun SongListItem(
    track: MusicTrack,
    number: Int,
    isCurrentTrack: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isHovered) MeloXColors.SurfaceVariant else MeloXColors.Background)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrentTrack) {
            MeloXSymbolIcon(
                symbol = if (isPlaying) MeloXSymbol.MusicNote else MeloXSymbol.Play,
                color = MeloXColors.Primary,
                size = 18,
                modifier = Modifier.width(28.dp),
            )
        } else {
            Text(
                text = "%02d".format(number),
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
                modifier = Modifier.width(28.dp),
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
            Text(
                text = track.artistText,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        SourceBadge(source = track.id.source)

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = formatDuration(track.durationMs),
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant,
        )
    }
}

@Composable
private fun SourceBadge(source: MusicSource) {
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

// ── Playlists Tab ──

@Composable
private fun PlaylistsTab(navState: MeloXNavState?) {
    if (mockPlaylists.isEmpty()) {
        EmptyState(
            symbol = MeloXSymbol.MusicNote,
            title = "暂无歌单",
            subtitle = "创建或收藏你的第一个歌单",
        )
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        gridItemsIndexed(mockPlaylists) { index, playlist ->
            PlaylistCard(
                playlist = playlist,
                onClick = {
                    navState?.navigateTo(Route.PlaylistDetail(playlist.id.value, playlist.title))
                },
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: MusicPlaylistSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val colorIndex = playlist.id.value.hashCode().mod(playlistCardColors.size).let {
        if (it < 0) it + playlistCardColors.size else it
    }

    Column(
        modifier = modifier
            .glassSurface(MeloXGlass.cardShape, MeloXColors.SurfaceVariant)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(playlistCardColors[colorIndex].copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = playlistCardColors[colorIndex],
                size = 32,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.title,
            style = MeloXTypography.caption.copy(fontWeight = FontWeight.Medium),
            color = MeloXColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.trackCount ?: 0} 首",
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant,
        )
    }
}

// ── Podcasts Tab ──

@Composable
private fun PodcastsTab() {
    if (mockPodcasts.isEmpty()) {
        EmptyState(
            symbol = MeloXSymbol.Podcast,
            title = "暂无播客",
            subtitle = "订阅你喜欢的播客节目",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        mockPodcasts.forEach { (name, description) ->
            item {
                MeloXIosListRow(
                    title = name,
                    subtitle = description,
                    leadingIcon = MeloXSymbol.Podcast,
                    leadingIconColor = MeloXColors.Primary,
                    showChevron = false,
                    onClick = { },
                )
            }
        }
    }
}

// ── Downloads Tab ──

@Composable
private fun DownloadsTab() {
    var multiSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    if (mockDownloads.isEmpty()) {
        EmptyState(
            symbol = MeloXSymbol.Download,
            title = "暂无下载",
            subtitle = "下载歌曲以便离线收听",
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (multiSelectMode) "已选 ${selectedIds.size} 项" else "共 ${mockDownloads.size} 首",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .background(
                        if (multiSelectMode) MeloXColors.Primary.copy(alpha = 0.15f)
                        else MeloXColors.OnBackground.copy(alpha = 0.055f),
                    )
                    .clickable {
                        multiSelectMode = !multiSelectMode
                        if (!multiSelectMode) selectedIds = emptySet()
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (multiSelectMode) "取消" else "多选",
                    style = MeloXTypography.caption,
                    color = if (multiSelectMode) MeloXColors.Primary else MeloXColors.OnSurfaceVariant,
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            mockDownloads.forEach { download ->
                item {
                    DownloadItem(
                        download = download,
                        isMultiSelectMode = multiSelectMode,
                        isSelected = download.track.id.value in selectedIds,
                        onToggleSelect = {
                            selectedIds = if (download.track.id.value in selectedIds) {
                                selectedIds - download.track.id.value
                            } else {
                                selectedIds + download.track.id.value
                            }
                         },
                        onClick = {
                            if (!multiSelectMode) {
                                AudioPlayer.play(
                                    "https://music.163.com/song/media/outer/url?id=${download.track.id.value}",
                                    download.track,
                                )
                            } else {
                                selectedIds = if (download.track.id.value in selectedIds) {
                                    selectedIds - download.track.id.value
                                } else {
                                    selectedIds + download.track.id.value
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItem(
    download: DownloadedTrack,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(
                when {
                    isSelected -> MeloXColors.Primary.copy(alpha = 0.08f)
                    isHovered -> MeloXColors.SurfaceVariant
                    else -> MeloXColors.Background
                },
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isSelected) MeloXColors.Primary else MeloXColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    MeloXSymbolIcon(symbol = MeloXSymbol.Checkmark, color = MeloXColors.OnPrimary, size = 14)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = download.track.title,
                style = MeloXTypography.body.copy(fontWeight = FontWeight.Medium),
                color = if (isSelected) MeloXColors.Primary else MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = download.track.artistText,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        SourceBadge(source = download.track.id.source)

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = download.fileSize,
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant,
        )
    }
}

// ── History Tab ──

@Composable
private fun HistoryTab() {
    if (mockHistory.isEmpty()) {
        EmptyState(
            symbol = MeloXSymbol.Clock,
            title = "暂无播放记录",
            subtitle = "听过的歌曲会出现在这里",
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        mockHistory.forEach { entry ->
            item {
                HistoryItem(entry = entry)
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: HistoryEntry) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(if (isHovered) MeloXColors.SurfaceVariant else MeloXColors.Background)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) {
                AudioPlayer.play(
                    "https://music.163.com/song/media/outer/url?id=${entry.track.id.value}",
                    entry.track,
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.track.title,
                style = MeloXTypography.body.copy(fontWeight = FontWeight.Medium),
                color = MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = entry.track.artistText,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        SourceBadge(source = entry.track.id.source)

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(entry.track.durationMs),
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
            )
            Text(
                text = entry.timestamp,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

// ── Shared ──

@Composable
private fun EmptyState(
    symbol: MeloXSymbol,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(symbol = symbol, color = MeloXColors.OnSurfaceVariant, size = 48)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

private fun formatDuration(durationMs: Long?): String {
    if (durationMs == null || durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
