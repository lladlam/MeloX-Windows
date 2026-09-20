package melox.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private data class ActiveDownload(
    val id: String,
    val title: String,
    val artist: String,
    val progress: Float,
    val speed: String,
    val totalSize: String,
)

private data class DownloadedFile(
    val id: String,
    val title: String,
    val artist: String,
    val fileSize: String,
    val format: String,
    val date: String,
    val gradientColors: List<Color>,
    var isSelected: Boolean = false,
)

private data class DownloadPlaylist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val totalSize: String,
    val gradientColors: List<Color>,
)

private val mockActiveDownloads = listOf(
    ActiveDownload("a1", "月光奏鸣曲", "贝多芬", 0.65f, "2.3 MB/s", "45.2 MB"),
    ActiveDownload("a2", "蓝色多瑙河", "约翰·施特劳斯", 0.32f, "1.8 MB/s", "38.7 MB"),
)

private val mockDownloadedFiles = listOf(
    DownloadedFile("d1", "夜曲 Op.9 No.2", "肖邦", "12.4 MB", "FLAC", "2026-09-18", listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
    DownloadedFile("d2", "致爱丽丝", "贝多芬", "8.7 MB", "MP3", "2026-09-17", listOf(Color(0xFFFF2442), Color(0xFFFF6B81))),
    DownloadedFile("d3", "土耳其进行曲", "莫扎特", "10.2 MB", "FLAC", "2026-09-16", listOf(Color(0xFF03DAC5), Color(0xFF00BFA5))),
    DownloadedFile("d4", "卡农", "帕赫贝尔", "9.8 MB", "MP3", "2026-09-15", listOf(Color(0xFFFF9800), Color(0xFFFFB74D))),
    DownloadedFile("d5", "月光", "德彪西", "14.6 MB", "FLAC", "2026-09-14", listOf(Color(0xFF2196F3), Color(0xFF64B5F6))),
    DownloadedFile("d6", "四季·春", "维瓦尔第", "11.3 MB", "MP3", "2026-09-13", listOf(Color(0xFF4CAF50), Color(0xFF81C784))),
    DownloadedFile("d7", "天鹅湖", "柴可夫斯基", "16.8 MB", "FLAC", "2026-09-12", listOf(Color(0xFF7C4DFF), Color(0xFFB388FF))),
    DownloadedFile("d8", "幻想即兴曲", "肖邦", "7.9 MB", "MP3", "2026-09-11", listOf(Color(0xFFE91E63), Color(0xFFF48FB1))),
    DownloadedFile("d9", "小星星变奏曲", "莫扎特", "6.5 MB", "MP3", "2026-09-10", listOf(Color(0xFF00BCD4), Color(0xFF4DD0E1))),
    DownloadedFile("d10", "胡桃夹子组曲", "柴可夫斯基", "13.2 MB", "FLAC", "2026-09-09", listOf(Color(0xFF8D6E63), Color(0xFFBCAAA4))),
)

private val mockDownloadedPlaylists = listOf(
    DownloadPlaylist("p1", "古典音乐精选", 12, "156.3 MB", listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
    DownloadPlaylist("p2", "工作学习背景", 8, "98.7 MB", listOf(Color(0xFF03DAC5), Color(0xFF00BFA5))),
    DownloadPlaylist("p3", "深夜钢琴曲", 15, "187.2 MB", listOf(Color(0xFF2196F3), Color(0xFF64B5F6))),
)

@Composable
fun DownloadsScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    var viewMode by remember { mutableStateOf("list") }
    var sortOption by remember { mutableStateOf("日期") }
    var isMultiSelectMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateSetOf<String>() }
    val downloadedTracks = remember { mutableStateListOf<DownloadedFile>() }
    LaunchedEffect(Unit) {
        downloadedTracks.clear()
        downloadedTracks.addAll(mockDownloadedFiles)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        DownloadsTopBar(
            isMultiSelectMode = isMultiSelectMode,
            selectedCount = selectedTrackIds.size,
            onMultiSelectToggle = {
                isMultiSelectMode = !isMultiSelectMode
                if (!isMultiSelectMode) selectedTrackIds.clear()
            },
            onDeleteSelected = {
                downloadedTracks.removeAll { it.id in selectedTrackIds }
                selectedTrackIds.clear()
                isMultiSelectMode = false
            },
            onCancelMultiSelect = {
                isMultiSelectMode = false
                selectedTrackIds.clear()
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (mockActiveDownloads.isNotEmpty()) {
                ActiveDownloadsSection()
                Spacer(modifier = Modifier.height(20.dp))
            }

            CompletedDownloadsHeader(
                viewMode = viewMode,
                onViewModeChange = { viewMode = it },
                sortOption = sortOption,
                onSortChange = { sortOption = it },
                trackCount = downloadedTracks.size,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (downloadedTracks.isEmpty()) {
                EmptyDownloadsState()
            } else {
                when (viewMode) {
                    "list" -> DownloadedListSection(
                        tracks = downloadedTracks,
                        isMultiSelectMode = isMultiSelectMode,
                        selectedTrackIds = selectedTrackIds,
                        onToggleSelect = { id ->
                            if (selectedTrackIds.contains(id)) selectedTrackIds.remove(id)
                            else selectedTrackIds.add(id)
                        },
                        onRemoveTrack = { id ->
                            downloadedTracks.removeAll { it.id == id }
                        },
                    )
                    "folder" -> DownloadedFolderSection(downloadedTracks)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            DownloadedPlaylistsSection()
            Spacer(modifier = Modifier.height(16.dp))
            DownloadStatsBar(trackCount = downloadedTracks.size)
        }
    }
}

@Composable
private fun DownloadsTopBar(
    isMultiSelectMode: Boolean,
    selectedCount: Int,
    onMultiSelectToggle: () -> Unit,
    onDeleteSelected: () -> Unit,
    onCancelMultiSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeloXColors.Background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelectMode) {
            IconButton(onClick = onCancelMultiSelect) {
                Text(
                    text = "✕",
                    color = MeloXColors.OnSurfaceVariant,
                    fontSize = 18.sp,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "已选择 $selectedCount 项",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedCount > 0) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDeleteSelected() },
                    shape = RoundedCornerShape(8.dp),
                    color = MeloXColors.Error.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "删除 ($selectedCount)",
                        color = MeloXColors.Error,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { },
                    shape = RoundedCornerShape(8.dp),
                    color = MeloXColors.Primary.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = "导出",
                        color = MeloXColors.Primary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        } else {
            Text(
                text = "下载",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onMultiSelectToggle() },
                shape = RoundedCornerShape(8.dp),
                color = MeloXColors.ChipBackground,
            ) {
                Text(
                    text = "多选",
                    color = MeloXColors.OnSurfaceVariant,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActiveDownloadsSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = "正在下载",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        mockActiveDownloads.forEach { download ->
            ActiveDownloadItem(download)
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ActiveDownloadItem(download: ActiveDownload) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MeloXColors.CardBackground,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = download.artist,
                        color = MeloXColors.TextSecondary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = download.speed,
                    color = MeloXColors.Success,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { },
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = "✕",
                        color = MeloXColors.OnSurfaceVariant,
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LinearProgressIndicator(
                    progress = { download.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MeloXColors.Primary,
                    trackColor = MeloXColors.PlayerProgressBackground,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(download.progress * 100).toInt()}%",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 11.sp,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = download.totalSize,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CompletedDownloadsHeader(
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    sortOption: String,
    onSortChange: (String) -> Unit,
    trackCount: Int,
) {
    val sortOptions = listOf("名称", "日期", "大小")

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已完成 ($trackCount)",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            // View mode toggle
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MeloXColors.ChipBackground)
                    .padding(2.dp),
            ) {
                listOf("list" to "列表", "folder" to "文件夹").forEach { (mode, label) ->
                    val isActive = viewMode == mode
                    val bgColor by animateColorAsState(
                        if (isActive) MeloXColors.SurfaceHigh else Color.Transparent,
                    )
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onViewModeChange(mode) },
                        shape = RoundedCornerShape(6.dp),
                        color = bgColor,
                    ) {
                        Text(
                            text = label,
                            color = if (isActive) MeloXColors.TextPrimary else MeloXColors.TextTertiary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Sort options
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sortOptions.forEach { option ->
                val isActive = sortOption == option
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSortChange(option) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isActive) MeloXColors.ChipBackgroundSelected else MeloXColors.ChipBackground,
                ) {
                    Text(
                        text = option,
                        color = if (isActive) MeloXColors.OnPrimary else MeloXColors.OnSurfaceVariant,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedListSection(
    tracks: List<DownloadedFile>,
    isMultiSelectMode: Boolean,
    selectedTrackIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onRemoveTrack: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        tracks.forEach { track ->
            DownloadedFileItem(
                track = track,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = selectedTrackIds.contains(track.id),
                onToggleSelect = { onToggleSelect(track.id) },
                onRemoveTrack = { onRemoveTrack(track.id) },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DownloadedFileItem(
    track: DownloadedFile,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onRemoveTrack: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(10.dp),
        color = if (isHovered || isSelected) MeloXColors.CardBackgroundHover else MeloXColors.CardBackground,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMultiSelectMode) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isSelected) MeloXColors.Primary else MeloXColors.SurfaceVariant)
                        .clickable { onToggleSelect() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Text(
                            text = "✓",
                            color = MeloXColors.OnPrimary,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(track.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "♫",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (track.format == "FLAC") MeloXColors.Success.copy(alpha = 0.15f) else MeloXColors.ChipBackground,
            ) {
                Text(
                    text = track.format,
                    color = if (track.format == "FLAC") MeloXColors.Success else MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = track.fileSize,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.width(4.dp))
            if (!isMultiSelectMode) {
                IconButton(
                    onClick = onRemoveTrack,
                    modifier = Modifier.size(28.dp),
                ) {
                    Text(
                        text = "🗑",
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedFolderSection(tracks: List<DownloadedFile>) {
    val formatGroups = tracks.groupBy { it.format }

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        formatGroups.forEach { (format, formatTracks) ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MeloXColors.CardBackground,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MeloXColors.SurfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (format == "FLAC") "◆" else "◇",
                                color = if (format == "FLAC") MeloXColors.Success else MeloXColors.TextTertiary,
                                fontSize = 16.sp,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = format,
                                color = MeloXColors.TextPrimary,
                                fontFamily = MeloXLanTingProFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "${formatTracks.size} 首歌曲",
                                color = MeloXColors.TextSecondary,
                                fontFamily = MeloXLanTingProFontFamily,
                                fontSize = 12.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    formatTracks.take(3).forEach { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "♫",
                                color = MeloXColors.OnSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.width(20.dp),
                            )
                            Text(
                                text = track.title,
                                color = MeloXColors.TextPrimary,
                                fontFamily = MeloXLanTingProFontFamily,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = track.fileSize,
                                color = MeloXColors.TextTertiary,
                                fontFamily = MeloXLanTingProFontFamily,
                                fontSize = 11.sp,
                            )
                        }
                    }
                    if (formatTracks.size > 3) {
                        Text(
                            text = "查看全部 ${formatTracks.size} 首",
                            color = MeloXColors.Primary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clickable { },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DownloadedPlaylistsSection() {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已下载歌单",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "查看全部",
                color = MeloXColors.Primary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                modifier = Modifier.clickable { },
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            mockDownloadedPlaylists.forEach { playlist ->
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = MeloXColors.CardBackground,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(playlist.gradientColors)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "♫",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 24.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = playlist.name,
                            color = MeloXColors.TextPrimary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${playlist.trackCount} 首 · ${playlist.totalSize}",
                            color = MeloXColors.TextTertiary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadStatsBar(trackCount: Int) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MeloXColors.SurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "共 $trackCount 首歌曲",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
            Text(
                text = "总计 111.4 MB",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun EmptyDownloadsState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "↓",
                color = MeloXColors.TextTertiary,
                fontSize = 48.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无下载",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "从音乐库中下载你喜欢的歌曲",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
