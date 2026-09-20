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

private data class CloudFile(
    val id: String,
    val name: String,
    val artist: String,
    val fileSize: String,
    val uploadDate: String,
    val gradientColors: List<Color>,
)

private data class StorageQuota(
    val usedGB: Float,
    val totalGB: Float,
)

private val mockCloudFiles = listOf(
    CloudFile("c1", "夜曲 Op.9 No.2", "肖邦", "12.4 MB", "2026-09-18", listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))),
    CloudFile("c2", "致爱丽丝", "贝多芬", "8.7 MB", "2026-09-17", listOf(Color(0xFFFF2442), Color(0xFFFF6B81))),
    CloudFile("c3", "土耳其进行曲", "莫扎特", "10.2 MB", "2026-09-16", listOf(Color(0xFF03DAC5), Color(0xFF00BFA5))),
    CloudFile("c4", "卡农", "帕赫贝尔", "9.8 MB", "2026-09-15", listOf(Color(0xFFFF9800), Color(0xFFFFB74D))),
    CloudFile("c5", "月光", "德彪西", "14.6 MB", "2026-09-14", listOf(Color(0xFF2196F3), Color(0xFF64B5F6))),
    CloudFile("c6", "四季·春", "维瓦尔第", "11.3 MB", "2026-09-13", listOf(Color(0xFF4CAF50), Color(0xFF81C784))),
)

@Composable
fun CloudScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    val storageQuota = remember { StorageQuota(usedGB = 2.3f, totalGB = 10f) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf("日期") }
    var isUploading by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<CloudFile?>(null) }
    val cloudFiles = remember { mutableStateListOf<CloudFile>() }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cloudFiles.clear()
        cloudFiles.addAll(mockCloudFiles)
    }

    val filteredFiles = remember(searchQuery, cloudFiles) {
        if (searchQuery.isBlank()) cloudFiles
        else cloudFiles.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
        }
    }

    val sortedFiles = remember(sortOption, filteredFiles) {
        when (sortOption) {
            "名称" -> filteredFiles.sortedBy { it.name }
            "日期" -> filteredFiles.sortedByDescending { it.uploadDate }
            "大小" -> filteredFiles.sortedByDescending {
                it.fileSize.replace(" MB", "").replace(" KB", "").toFloatOrNull() ?: 0f
            }
            else -> filteredFiles
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        CloudTopBar(
            isRefreshing = isRefreshing,
            onRefresh = {
                isRefreshing = true
                isRefreshing = false
            },
            onUpload = {
                isUploading = true
                // Simulate upload completion
                isUploading = false
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            StorageQuotaBar(quota = storageQuota)
            Spacer(modifier = Modifier.height(16.dp))
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SortOptionsBar(
                selectedOption = sortOption,
                onOptionChange = { sortOption = it },
                fileCount = sortedFiles.size,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (isUploading) {
                UploadProgressIndicator()
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (sortedFiles.isEmpty()) {
                if (searchQuery.isNotBlank()) {
                    SearchEmptyState(query = searchQuery)
                } else {
                    CloudEmptyState()
                }
            } else {
                CloudFileList(
                    files = sortedFiles,
                    onDeleteFile = { showDeleteDialog = it },
                )
            }
        }
    }

    showDeleteDialog?.let { file ->
        DeleteConfirmDialog(
            fileName = file.name,
            onConfirm = {
                cloudFiles.removeAll { it.id == file.id }
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null },
        )
    }
}

@Composable
private fun CloudTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onUpload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MeloXColors.Background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "云盘",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onUpload) {
            Surface(
                shape = CircleShape,
                color = MeloXColors.Primary,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "↑",
                        color = MeloXColors.OnPrimary,
                        fontSize = 16.sp,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            Text(
                text = if (isRefreshing) "⟳" else "↻",
                color = MeloXColors.OnSurfaceVariant,
                fontSize = 20.sp,
            )
        }
    }
}

@Composable
private fun StorageQuotaBar(quota: StorageQuota) {
    val usedPercent = quota.usedGB / quota.totalGB

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "存储空间",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${quota.usedGB} GB / ${quota.totalGB} GB",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MeloXColors.SurfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = usedPercent)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(MeloXColors.Primary, MeloXColors.Secondary),
                        ),
                    ),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "已使用 ${(usedPercent * 100).toInt()}%",
            color = MeloXColors.TextTertiary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(12.dp),
        color = MeloXColors.SurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⌕",
                color = MeloXColors.TextTertiary,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                ),
                modifier = Modifier.weight(1f),
                singleLine = true,
                decorationBox = { innerTextField ->
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索云盘歌曲...",
                            color = MeloXColors.TextTertiary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(20.dp),
                ) {
                    Text(
                        text = "✕",
                        color = MeloXColors.TextTertiary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortOptionsBar(
    selectedOption: String,
    onOptionChange: (String) -> Unit,
    fileCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "共 $fileCount 首",
            color = MeloXColors.TextTertiary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        listOf("名称", "日期", "大小").forEach { option ->
            val isActive = selectedOption == option
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOptionChange(option) },
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
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun UploadProgressIndicator() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(12.dp),
        color = MeloXColors.CardBackground,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    color = MeloXColors.Primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "正在上传...",
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.65f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MeloXColors.Primary,
                trackColor = MeloXColors.PlayerProgressBackground,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "65% · 12.4 MB / 19.1 MB",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CloudFileList(
    files: List<CloudFile>,
    onDeleteFile: (CloudFile) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        files.forEach { file ->
            CloudFileItem(
                file = file,
                onDelete = { onDeleteFile(file) },
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CloudFileItem(
    file: CloudFile,
    onDelete: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .hoverable(interactionSource),
        shape = RoundedCornerShape(10.dp),
        color = if (isHovered) MeloXColors.CardBackgroundHover else MeloXColors.CardBackground,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.linearGradient(file.gradientColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "☁",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${file.artist} · ${file.uploadDate}",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = file.fileSize,
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
                modifier = Modifier.width(56.dp),
                textAlign = TextAlign.End,
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onDelete,
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

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        shape = RoundedCornerShape(16.dp),
        color = MeloXColors.Surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "确认删除",
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "确定要从云盘删除「$fileName」吗？",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onDismiss() },
                    shape = RoundedCornerShape(10.dp),
                    color = MeloXColors.ChipBackground,
                ) {
                    Text(
                        text = "取消",
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onConfirm() },
                    shape = RoundedCornerShape(10.dp),
                    color = MeloXColors.Error,
                ) {
                    Text(
                        text = "删除",
                        color = Color.White,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudEmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "☁",
                color = MeloXColors.TextTertiary,
                fontSize = 48.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "云盘为空",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "上传音乐文件到云盘，随时随地访问",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { },
                shape = RoundedCornerShape(20.dp),
                color = MeloXColors.Primary,
            ) {
                Text(
                    text = "上传文件",
                    color = MeloXColors.OnPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "⌕",
                color = MeloXColors.TextTertiary,
                fontSize = 36.sp,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "未找到「$query」",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "尝试其他关键词搜索",
                color = MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 12.sp,
            )
        }
    }
}
