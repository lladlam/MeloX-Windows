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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import melox.ui.foundation.*
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.theme.MeloXTypography

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
        MeloXIosTopBar(
            title = "云盘",
            actions = {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Upload,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable {
                            isUploading = true
                            isUploading = false
                        },
                    color = MeloXColors.Primary,
                    size = 22,
                )
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Refresh,
                    modifier = Modifier.size(44.dp),
                    color = MeloXColors.OnSurfaceVariant,
                    size = 20,
                )
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
private fun StorageQuotaBar(quota: StorageQuota) {
    val usedPercent = quota.usedGB / quota.totalGB

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "存储空间",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${quota.usedGB} GB / ${quota.totalGB} GB",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MeloXGlass.capsuleShape)
                .background(MeloXColors.SurfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = usedPercent)
                    .clip(MeloXGlass.capsuleShape)
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
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MeloXGlass.capsuleShape)
            .background(MeloXColors.OnBackground.copy(alpha = 0.055f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeloXSymbolIcon(symbol = MeloXSymbol.Search, color = MeloXColors.OnSurfaceVariant, size = 18)
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = "搜索云盘歌曲...",
                        style = MeloXTypography.body,
                        color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    textStyle = TextStyle(
                        color = MeloXColors.OnSurface,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = MeloXTypography.body.fontSize,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            if (query.isNotEmpty()) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.XMark,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onQueryChange("") },
                    color = MeloXColors.OnSurfaceVariant,
                    size = 14,
                )
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "共 $fileCount 首",
            style = MeloXTypography.caption,
            color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
        )
        Spacer(modifier = Modifier.weight(1f))
        listOf("名称", "日期", "大小").forEach { option ->
            val isActive = selectedOption == option
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .background(if (isActive) MeloXColors.Primary else MeloXColors.SurfaceVariant)
                    .clickable { onOptionChange(option) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MeloXTypography.caption,
                    color = if (isActive) Color.White else MeloXColors.OnSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

@Composable
private fun UploadProgressIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MeloXGlass.cardShape)
            .background(MeloXColors.SurfaceVariant)
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MeloXSymbolIcon(symbol = MeloXSymbol.Upload, color = MeloXColors.Primary, size = 16)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "正在上传...",
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurface,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { 0.65f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(MeloXGlass.capsuleShape),
                color = MeloXColors.Primary,
                trackColor = MeloXColors.PlayerProgressBg,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "65% · 12.4 MB / 19.1 MB",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun CloudFileList(
    files: List<CloudFile>,
    onDeleteFile: (CloudFile) -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(MeloXGlass.largeCardShape)
            .background(MeloXColors.SurfaceVariant),
    ) {
        files.forEachIndexed { index, file ->
            CloudFileItem(
                file = file,
                onDelete = { onDeleteFile(file) },
            )
            if (index < files.lastIndex) {
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
private fun CloudFileItem(
    file: CloudFile,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(MeloXGlass.compactShape)
                .background(Brush.linearGradient(file.gradientColors)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(symbol = MeloXSymbol.Cloud, color = Color.White.copy(alpha = 0.9f), size = 20)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${file.artist} · ${file.uploadDate}",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = file.fileSize,
            style = MeloXTypography.subheadline,
            color = MeloXColors.OnSurfaceVariant,
            modifier = Modifier.width(56.dp),
            textAlign = TextAlign.End,
        )
        Spacer(modifier = Modifier.width(4.dp))
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Trash,
            modifier = Modifier.size(28.dp).clickable { onDelete() },
            color = MeloXColors.Error.copy(alpha = 0.7f),
            size = 16,
        )
    }
}

@Composable
private fun DeleteConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(48.dp)
                .clip(MeloXGlass.dialogShape)
                .background(MeloXColors.Surface)
                .clickable { },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "确认删除",
                    style = MeloXTypography.headline,
                    color = MeloXColors.OnBackground,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "确定要从云盘删除「$fileName」吗？",
                    style = MeloXTypography.body,
                    color = MeloXColors.OnSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MeloXGlass.capsuleShape)
                            .background(MeloXColors.SurfaceVariant)
                            .clickable { onDismiss() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消",
                            style = MeloXTypography.subheadline,
                            color = MeloXColors.OnSurface,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(MeloXGlass.capsuleShape)
                            .background(MeloXColors.Error)
                            .clickable { onConfirm() }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "删除",
                            style = MeloXTypography.subheadline,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Cloud,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.5f),
                size = 48,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "云盘为空",
                style = MeloXTypography.headline,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "上传音乐文件到云盘，随时随地访问",
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .clip(MeloXGlass.capsuleShape)
                    .background(MeloXColors.Primary)
                    .clickable { }
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MeloXSymbolIcon(symbol = MeloXSymbol.Upload, color = Color.White, size = 18)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "上传文件",
                        style = MeloXTypography.subheadline,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState(query: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.Search,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.5f),
                size = 36,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "未找到「$query」",
                style = MeloXTypography.body,
                color = MeloXColors.OnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "尝试其他关键词搜索",
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}
