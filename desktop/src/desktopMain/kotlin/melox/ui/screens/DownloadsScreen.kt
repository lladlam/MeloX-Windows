package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

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

private val sortOptions = listOf("名称", "日期", "大小")

@Composable
fun DownloadsScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
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
        MeloXIosTopBar(
            title = if (isMultiSelectMode) "已选择 ${selectedTrackIds.size} 项" else "下载",
            actions = {
                if (isMultiSelectMode) {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.XMark,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable {
                                isMultiSelectMode = false
                                selectedTrackIds.clear()
                            },
                        color = MeloXColors.OnSurfaceVariant,
                        size = 20,
                    )
                } else {
                    MeloXSymbolIcon(
                        symbol = MeloXSymbol.ListBullet,
                        modifier = Modifier
                            .size(44.dp)
                            .clickable { isMultiSelectMode = true },
                        color = MeloXColors.OnSurfaceVariant,
                        size = 20,
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (mockActiveDownloads.isNotEmpty()) {
                ActiveDownloadsSection()
                Spacer(modifier = Modifier.height(16.dp))
            }

            CompletedDownloadsHeader(
                sortOption = sortOption,
                onSortChange = { sortOption = it },
                trackCount = downloadedTracks.size,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (downloadedTracks.isEmpty()) {
                EmptyDownloadsState()
            } else {
                DownloadedListSection(
                    tracks = downloadedTracks,
                    isMultiSelectMode = isMultiSelectMode,
                    selectedTrackIds = selectedTrackIds,
                    onToggleSelect = { id ->
                        if (selectedTrackIds.contains(id)) selectedTrackIds.remove(id)
                        else selectedTrackIds.add(id)
                    },
                    onRemoveTrack = { id -> downloadedTracks.removeAll { it.id == id } },
                )
            }

            if (isMultiSelectMode && selectedTrackIds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                MultiSelectActions(
                    selectedCount = selectedTrackIds.size,
                    onDelete = {
                        downloadedTracks.removeAll { it.id in selectedTrackIds }
                        selectedTrackIds.clear()
                        isMultiSelectMode = false
                    },
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            DownloadStatsBar(trackCount = downloadedTracks.size)
        }
    }
}

@Composable
private fun ActiveDownloadsSection() {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "正在下载",
            style = MeloXTypography.headline,
            color = MeloXColors.OnBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MeloXGlass.largeCardShape)
                .background(MeloXColors.SurfaceVariant),
        ) {
            mockActiveDownloads.forEachIndexed { index, download ->
                ActiveDownloadItem(download)
                if (index < mockActiveDownloads.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(MeloXGlass.separatorColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveDownloadItem(download: ActiveDownload) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    style = MeloXTypography.body,
                    color = MeloXColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = download.artist,
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurfaceVariant,
                )
            }
            Text(
                text = download.speed,
                style = MeloXTypography.caption,
                color = MeloXColors.Success,
            )
            Spacer(modifier = Modifier.width(8.dp))
            MeloXSymbolIcon(
                symbol = MeloXSymbol.XMark,
                modifier = Modifier.size(28.dp).clickable { },
                color = MeloXColors.OnSurfaceVariant,
                size = 14,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { download.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(MeloXGlass.capsuleShape),
            color = MeloXColors.Primary,
            trackColor = MeloXColors.PlayerProgressBg,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = download.totalSize,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                text = "${(download.progress * 100).toInt()}%",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CompletedDownloadsHeader(
    sortOption: String,
    onSortChange: (String) -> Unit,
    trackCount: Int,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已完成 ($trackCount)",
                style = MeloXTypography.headline,
                color = MeloXColors.OnBackground,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sortOptions.forEach { option ->
                val isActive = sortOption == option
                Box(
                    modifier = Modifier
                        .clip(MeloXGlass.capsuleShape)
                        .background(if (isActive) MeloXColors.Primary else MeloXColors.SurfaceVariant)
                        .clickable { onSortChange(option) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option,
                        style = MeloXTypography.caption,
                        color = if (isActive) Color.White else MeloXColors.OnSurfaceVariant,
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
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(MeloXGlass.largeCardShape)
            .background(MeloXColors.SurfaceVariant),
    ) {
        tracks.forEachIndexed { index, track ->
            DownloadedFileItem(
                track = track,
                isMultiSelectMode = isMultiSelectMode,
                isSelected = selectedTrackIds.contains(track.id),
                onToggleSelect = { onToggleSelect(track.id) },
                onRemoveTrack = { onRemoveTrack(track.id) },
            )
            if (index < tracks.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(MeloXGlass.separatorColor),
                )
            }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMultiSelectMode) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(MeloXGlass.compactShape)
                    .background(if (isSelected) MeloXColors.Primary else Color.Transparent)
                    .then(
                        if (!isSelected) Modifier.background(Color.Transparent, MeloXGlass.compactShape)
                            .let { it }
                        else Modifier
                    )
                    .clickable { onToggleSelect() },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    MeloXSymbolIcon(symbol = MeloXSymbol.Checkmark, color = Color.White, size = 14)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MeloXGlass.compactShape)
                .background(Brush.linearGradient(track.gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(symbol = MeloXSymbol.MusicNote, color = Color.White.copy(alpha = 0.9f), size = 20)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(MeloXGlass.capsuleShape)
                .background(
                    if (track.format == "FLAC") MeloXColors.Success.copy(alpha = 0.15f)
                    else MeloXColors.SurfaceVariant
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = track.format,
                style = MeloXTypography.caption,
                color = if (track.format == "FLAC") MeloXColors.Success else MeloXColors.OnSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = track.fileSize,
            style = MeloXTypography.subheadline,
            color = MeloXColors.OnSurfaceVariant,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(4.dp))
        if (!isMultiSelectMode) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Trash,
                modifier = Modifier.size(28.dp).clickable { onRemoveTrack() },
                color = MeloXColors.Error.copy(alpha = 0.7f),
                size = 16,
            )
        }
    }
}

@Composable
private fun MultiSelectActions(selectedCount: Int, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(MeloXGlass.capsuleShape)
                .background(MeloXColors.Error.copy(alpha = 0.15f))
                .clickable { onDelete() }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeloXSymbolIcon(symbol = MeloXSymbol.Trash, color = MeloXColors.Error, size = 18)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "删除 ($selectedCount)",
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.Error,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(MeloXGlass.capsuleShape)
                .background(MeloXColors.Primary.copy(alpha = 0.15f))
                .clickable { }
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeloXSymbolIcon(symbol = MeloXSymbol.Upload, color = MeloXColors.Primary, size = 18)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "导出",
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.Primary,
                )
            }
        }
    }
}

@Composable
private fun DownloadStatsBar(trackCount: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MeloXGlass.cardShape)
            .background(MeloXColors.SurfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "共 $trackCount 首歌曲",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Text(
                text = "总计 111.4 MB",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyDownloadsState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Download,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.5f),
                size = 48,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无下载",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "从音乐库中下载你喜欢的歌曲",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
        }
    }
}
