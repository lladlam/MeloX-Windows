package melox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.download.MeloXDownloadStore
import melox.download.MeloXProviderDownloadStore
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.meloXContentSurface
import melox.ui.glass.MeloXShapes
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography
import java.io.File

/**
 * 1:1 port of Android MeloXLibraryDownloadsPage (LibraryScreen.kt 543-943):
 * Root / Active / Playlists / PlaylistDetail subpages with multi-select,
 * browse-mode grouping, provider downloads and export.
 */
private enum class MeloXDownloadsPage { Root, Active, Playlists, PlaylistDetail }
private enum class MeloXLocalBrowseMode(val title: String) {
    Songs("歌曲"), Artists("艺术家"), Albums("专辑"), Folders("文件夹")
}

@Composable
internal fun MeloXLibraryDownloadsPage() {
    val downloads = remember { MeloXDownloadStore.instance() }
    val providerDownloads = remember { MeloXProviderDownloadStore.get() }
    var page by rememberSaveable { mutableStateOf(MeloXDownloadsPage.Root) }
    var selectedPlaylistId by remember { mutableStateOf<Long?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var browseMode by remember { mutableStateOf(MeloXLocalBrowseMode.Songs) }
    var browseGroup by remember { mutableStateOf<String?>(null) }
    var exportMessage by remember { mutableStateOf<String?>(null) }

    val active = downloads.activeDownloads.values.toList()
    val completed = downloads.downloads.toList()
    val providerCompleted = providerDownloads.downloads.toList()
    val groups = downloads.downloadedPlaylists
    val browseGroups = remember(completed, browseMode) {
        when (browseMode) {
            MeloXLocalBrowseMode.Songs -> emptyMap()
            MeloXLocalBrowseMode.Artists -> completed.groupBy { it.song.artists.ifBlank { "未知艺术家" } }
            MeloXLocalBrowseMode.Albums -> completed.groupBy { it.song.album.ifBlank { "未知专辑" } }
            MeloXLocalBrowseMode.Folders -> mapOf("Music/MeloX" to completed)
        }.toSortedMap()
    }
    val visibleCompleted = remember(completed, browseMode, browseGroup, browseGroups) {
        if (browseMode == MeloXLocalBrowseMode.Songs) completed
        else browseGroup?.let { browseGroups[it].orEmpty() }.orEmpty()
    }

    fun exportSelected() {
        if (selectedIds.isEmpty()) return
        downloads.exportToMusicLibrary(selectedIds) { result ->
            exportMessage = result.fold(
                onSuccess = { "已导出 $it 首到 Music/MeloX" },
                onFailure = { it.message ?: "导出失败" },
            )
        }
    }

    when (page) {
        MeloXDownloadsPage.Root -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = MeloXBottomContentClearanceDp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (active.isNotEmpty()) {
                item {
                    DownloadNavigationCard(
                        title = "正在下载",
                        subtitle = "${meloXFormatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
                        onClick = { page = MeloXDownloadsPage.Active },
                    )
                }
            }
            if (completed.isNotEmpty()) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("已下载", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (selecting) {
                                Text(
                                    if (selectedIds.size == completed.size) "取消全选" else "全选",
                                    color = MeloXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        selectedIds = if (selectedIds.size == completed.size) emptySet()
                                        else completed.map { it.song.id }.toSet()
                                    },
                                )
                                Text(
                                    "取消",
                                    color = MeloXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        selecting = false
                                        selectedIds = emptySet()
                                    },
                                )
                            } else {
                                Text(
                                    "多选",
                                    color = MeloXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { selecting = true },
                                )
                                Text(
                                    "播放全部",
                                    color = MeloXColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable {
                                        downloads.downloadedSongs.firstOrNull()?.let {
                                            melox.playback.PlaybackCommands.playQueue(
                                                songs = downloads.downloadedSongs,
                                                selectedSongId = it.id,
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MeloXLocalBrowseMode.entries.forEach { mode ->
                            Text(
                                mode.title,
                                color = if (browseMode == mode) MeloXColors.Primary else MeloXColors.OnBackground.copy(alpha = .55f),
                                fontWeight = if (browseMode == mode) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MeloXColors.OnBackground.copy(alpha = if (browseMode == mode) .10f else .04f))
                                    .clickable {
                                        browseMode = mode
                                        browseGroup = null
                                        selectedIds = emptySet()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                            )
                        }
                    }
                }
                exportMessage?.let { value ->
                    item { Text(value, color = MeloXColors.Primary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)) }
                }
                if (groups.isNotEmpty()) {
                    item {
                        DownloadNavigationCard(
                            title = "已下载歌单",
                            subtitle = "${groups.size} 个歌单",
                            onClick = { page = MeloXDownloadsPage.Playlists },
                        )
                    }
                }
                if (browseMode != MeloXLocalBrowseMode.Songs && browseGroup == null) {
                    items(browseGroups.entries.toList()) { group ->
                        DownloadNavigationCard(
                            title = group.key,
                            subtitle = "${group.value.size} 首歌曲",
                            onClick = { browseGroup = group.key },
                        )
                    }
                } else if (browseMode != MeloXLocalBrowseMode.Songs) {
                    item { DownloadsSubpageHeader(browseGroup.orEmpty()) { browseGroup = null } }
                }
                items(visibleCompleted) { item ->
                    val checked = item.song.id in selectedIds
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(62.dp)
                            .clickable {
                                if (selecting) {
                                    selectedIds = if (checked) selectedIds - item.song.id else selectedIds + item.song.id
                                } else {
                                    melox.playback.PlaybackCommands.playQueue(
                                        songs = downloads.downloadedSongs,
                                        selectedSongId = item.song.id,
                                    )
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXLocalArtwork(
                            path = downloads.localArtworkString(item.song.id) ?: item.song.artworkUrl,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.song.artists} · ${item.quality.title}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MeloXColors.OnBackground.copy(alpha = .48f),
                                fontSize = 12.sp,
                            )
                        }
                        if (selecting) {
                            Text(if (checked) "✓" else "○", color = MeloXColors.Primary, fontSize = 20.sp, modifier = Modifier.padding(10.dp))
                        } else {
                            Text(
                                "删除",
                                color = MeloXColors.Error,
                                modifier = Modifier.clickable { downloads.remove(item.song.id) }.padding(10.dp),
                            )
                        }
                    }
                }
                if (selecting) {
                    item {
                        val canDelete = selectedIds.isNotEmpty()
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                                    .background(MeloXColors.Primary.copy(alpha = if (canDelete) .14f else .05f))
                                    .clickable(enabled = canDelete) { exportSelected() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "导出已选",
                                    color = MeloXColors.Primary.copy(alpha = if (canDelete) 1f else .4f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Box(
                                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(18.dp))
                                    .background(MeloXColors.Error.copy(alpha = if (canDelete) .14f else .05f))
                                    .clickable(enabled = canDelete) {
                                        downloads.removeMany(selectedIds)
                                        selectedIds = emptySet()
                                        selecting = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (canDelete) "删除 ${selectedIds.size} 首" else "请选择歌曲",
                                    color = MeloXColors.Error.copy(alpha = if (canDelete) 1f else .4f),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            if (providerCompleted.isNotEmpty()) {
                item {
                    Text(
                        "Spotify / YouTube Music",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                    )
                }
                items(providerCompleted) { item ->
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).clickable {
                            melox.playback.ProviderPlaybackCommands.playQueue(
                                providerCompleted.map { it.track },
                                item.track.id,
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXArtworkImage(
                            url = item.track.artworkUrl,
                            fallbackColor = MeloXColors.Surface,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.track.artistText} · ${item.track.id.source.displayName}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MeloXColors.OnBackground.copy(alpha = .48f),
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            "删除",
                            color = MeloXColors.Error,
                            modifier = Modifier.clickable { providerDownloads.remove(item.track.id) }.padding(10.dp),
                        )
                    }
                }
            }

            if (active.isEmpty() && completed.isEmpty() && providerCompleted.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("还没有下载歌曲", fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "在歌曲的更多操作菜单中选择“下载歌曲”。",
                                modifier = Modifier.padding(top = 7.dp),
                                color = MeloXColors.OnBackground.copy(alpha = .48f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        MeloXDownloadsPage.Active -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = MeloXBottomContentClearanceDp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { DownloadsSubpageHeader("正在下载") { page = MeloXDownloadsPage.Root } }
            item {
                Text(
                    "${meloXFormatDownloadSpeed(downloads.aggregateDownloadBytesPerSecond)} · 剩余 ${active.size} 首未完成",
                    color = MeloXColors.OnBackground.copy(alpha = .52f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            items(active) { item ->
                Row(Modifier.fillMaxWidth().height(66.dp), verticalAlignment = Alignment.CenterVertically) {
                    MeloXArtworkImage(
                        url = item.song.artworkUrl,
                        fallbackColor = MeloXColors.Surface,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        val progress = item.fractionCompleted?.let { "${(it * 100).toInt()}%" } ?: "准备中"
                        Text(
                            "$progress · ${meloXFormatDownloadSpeed(item.bytesPerSecond)} · ${item.quality.title}",
                            color = MeloXColors.OnBackground.copy(alpha = .48f),
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        "取消",
                        color = MeloXColors.Error,
                        modifier = Modifier.clickable { downloads.cancel(item.song.id) }.padding(10.dp),
                    )
                }
            }
            if (active.isEmpty()) {
                item {
                    Text(
                        "当前没有正在下载的歌曲",
                        color = MeloXColors.OnBackground.copy(alpha = .5f),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }

        MeloXDownloadsPage.Playlists -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = MeloXBottomContentClearanceDp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item { DownloadsSubpageHeader("已下载歌单") { page = MeloXDownloadsPage.Root } }
            items(groups) { group ->
                Row(
                    Modifier.fillMaxWidth().height(68.dp).clickable {
                        selectedPlaylistId = group.playlist.id
                        page = MeloXDownloadsPage.PlaylistDetail
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXLocalArtwork(
                        path = downloads.localPlaylistArtworkString(group.playlist.id) ?: group.playlist.artworkUrl,
                        modifier = Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(group.playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text("已下载 ${group.songs.size} 首", color = MeloXColors.OnBackground.copy(alpha = .48f), fontSize = 12.sp)
                    }
                    MeloXActionIcon("›", Modifier.size(18.dp), MeloXColors.OnBackground.copy(alpha = .4f))
                }
            }
        }

        MeloXDownloadsPage.PlaylistDetail -> {
            val group = groups.firstOrNull { it.playlist.id == selectedPlaylistId }
            val songs = group?.songs?.map { it.song }.orEmpty()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = MeloXBottomContentClearanceDp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item {
                    DownloadsSubpageHeader(group?.playlist?.name ?: "已下载歌单") {
                        page = MeloXDownloadsPage.Playlists
                    }
                }
                group?.let { existing ->
                    item {
                        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            MeloXLocalArtwork(
                                path = downloads.localPlaylistArtworkString(existing.playlist.id) ?: existing.playlist.artworkUrl,
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(
                                    existing.playlist.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${songs.size} 首已下载歌曲",
                                    color = MeloXColors.OnBackground.copy(alpha = .5f),
                                    fontSize = 12.sp,
                                )
                            }
                            Text(
                                "播放全部",
                                color = MeloXColors.Primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    songs.firstOrNull()?.let {
                                        melox.playback.PlaybackCommands.playQueue(
                                            songs = songs,
                                            selectedSongId = it.id,
                                        )
                                    }
                                }.padding(8.dp),
                            )
                        }
                    }
                }
                items(group?.songs.orEmpty()) { item ->
                    Row(
                        Modifier.fillMaxWidth().height(62.dp).clickable {
                            melox.playback.PlaybackCommands.playQueue(
                                songs = songs,
                                selectedSongId = item.song.id,
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXLocalArtwork(
                            path = downloads.localArtworkString(item.song.id) ?: item.song.artworkUrl,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(9.dp)),
                        )
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(
                                item.song.artists,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MeloXColors.OnBackground.copy(alpha = .48f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Local-file artwork with URL fallback (Android AsyncImage local uri branch). */
@Composable
private fun MeloXLocalArtwork(path: String?, modifier: Modifier = Modifier) {
    if (path != null && !path.startsWith("http")) {
        val file = remember(path) { File(path) }
        if (file.isFile) {
            Image(
                bitmap = remember(path) {
                    file.inputStream().use { androidx.compose.ui.res.loadImageBitmap(it) }
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = modifier,
            )
            return
        }
    }
    MeloXArtworkImage(
        url = path,
        fallbackColor = MeloXColors.Surface,
        modifier = modifier,
    )
}

@Composable
private fun DownloadNavigationCard(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp)
            .height(66.dp)
            .meloXContentSurface(
                shape = MeloXShapes.card,
                surfaceColor = MeloXColors.OnBackground.copy(alpha = .055f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(
                subtitle,
                color = MeloXColors.OnBackground.copy(alpha = .48f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        MeloXActionIcon("›", Modifier.size(18.dp), MeloXColors.OnBackground.copy(alpha = .42f))
    }
}

@Composable
private fun DownloadsSubpageHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(58.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            MeloXActionIcon("‹", Modifier.size(20.dp), MeloXColors.OnBackground)
        }
        Text(title, style = MeloXTypography.title2)
    }
}

private fun meloXFormatDownloadSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / (1024.0 * 1024.0))
    bytesPerSecond >= 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    bytesPerSecond > 0L -> "$bytesPerSecond B/s"
    else -> "0 KB/s"
}
