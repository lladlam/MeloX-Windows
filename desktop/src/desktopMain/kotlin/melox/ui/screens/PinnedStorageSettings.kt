package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.audio.MusicQuality
import melox.download.MeloXDownloadStore
import melox.platform.*
import melox.settings.MeloXSettingsPreferences
import melox.ui.foundation.*
import melox.ui.legal.pinnedString
import java.io.File

private data class StorageUsage(val downloads: Long = 0, val network: Long = 0, val playback: Long = 0,
    val temporary: Long = 0, val local: Long = 0, val total: Long = 0, val available: Long = 0) {
    val managed get() = downloads + network + playback + temporary + local
    val reclaimable get() = network + playback + temporary
}

internal fun settingsFormatBytes(bytes: Long) = when {
    bytes >= 1024L * 1024 * 1024 -> "%.2f GB".format(bytes / 1073741824.0)
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / 1048576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun File.treeBytes(): Long = if (!exists()) 0 else java.nio.file.Files.walk(toPath()).use { paths ->
    paths.filter { java.nio.file.Files.isRegularFile(it, java.nio.file.LinkOption.NOFOLLOW_LINKS) }.mapToLong { java.nio.file.Files.size(it) }.sum()
}

@Composable
internal fun PinnedStorageSettings() {
    val downloads = remember { MeloXDownloadStore.instance() }
    val data = remember { File(meloXDataDir()) }
    val cache = remember { File(meloXCacheDir()) }
    var usage by remember { mutableStateOf(StorageUsage()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var autoCache by remember { mutableStateOf(getPreferences("melox_downloads").getBoolean("downloads_auto_cache", false)) }
    var threshold by remember { mutableIntStateOf(getPreferences("melox_downloads").getInt("downloads_auto_cache_threshold", 3)) }
    var quality by remember { mutableStateOf(getPreferences("melox_downloads").getString("downloads_auto_cache_quality", "Standard") ?: "Standard") }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true
        runCatching {
            val downloadBytes = downloads.totalByteCount
            withContext(Dispatchers.IO) {
                StorageUsage(downloadBytes, listOf("melox_http", "image_cache", "coil3_disk_cache").sumOf { File(cache, it).treeBytes() },
                    File(cache, "melox_media").treeBytes(), File(cache, "automix_analysis").treeBytes(),
                    listOf("automix_analysis_index.json", "automix_analysis", "netease_library_cache").sumOf { File(data, it).treeBytes() }, data.totalSpace, data.usableSpace)
            }
        }.onSuccess { usage = it }.onFailure { message = it.message }
        loading = false
    }
    LaunchedEffect(downloads.downloads.size) { refresh() }
    SettingsGlassGroup {
        Column(Modifier.padding(20.dp).testTag("storage-summary"), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            MeloXSymbolIcon(MeloXSymbol.Drive, size = 30)
            Text(pinnedString("settings_storage_managed"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
            Text(if (loading) pinnedString("settings_storage_calculating") else settingsFormatBytes(usage.managed), fontSize = 34.sp, fontWeight = FontWeight.Bold)
            val used = (usage.total - usage.available).coerceAtLeast(0)
            LinearProgressIndicator(progress = { if (usage.total > 0) (used.toFloat() / usage.total).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            Text(pinnedString("settings_storage_device", settingsFormatBytes(used), settingsFormatBytes(usage.available)), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .50f))
            Text(pinnedString("settings_storage_reclaimable", settingsFormatBytes(usage.reclaimable)), fontSize = 12.sp)
        }
    }
    Spacer(Modifier.height(18.dp)); StorageSectionTitle("settings_storage_items"); Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf("downloads" to usage.downloads, "network" to usage.network, "playback" to usage.playback, "temporary" to usage.temporary, "local" to usage.local)
            .forEachIndexed { index, (title, bytes) -> MeloXIosListRow(title = pinnedString("settings_storage_$title"), detail = settingsFormatBytes(bytes),
                leadingIcon = if (title == "downloads") MeloXSymbol.Download else MeloXSymbol.Drive, showTopSeparator = index > 0, showChevron = false) }
    }
    Spacer(Modifier.height(18.dp)); StorageSectionTitle("settings_storage_cleanup"); Spacer(Modifier.height(8.dp))
    SettingsGlassGroup {
        listOf("all", "network", "playback").forEachIndexed { index, key ->
            MeloXIosListRow(title = pinnedString("settings_storage_clear_$key"), leadingIcon = MeloXSymbol.Drive, showTopSeparator = index > 0,
                modifier = Modifier.testTag("storage-clear-$key"), onClick = { confirmation = key })
        }
    }
    Spacer(Modifier.height(18.dp))
    SettingsGlassGroup {
        SettingsToggleRow("settings_storage_download_lyrics", "download_lyrics", true)
        SettingsExternalToggleRow(pinnedString("settings_storage_auto_cache"), autoCache, "downloads_auto_cache") {
            autoCache = it; getPreferences("melox_downloads").putBoolean("downloads_auto_cache", it)
            MeloXSettingsPreferences.setBoolean("downloads_auto_cache", it)
        }
    }
    if (autoCache) {
        SettingsGlassGroup {
            SettingsChoice("settings_storage_threshold", "downloads_auto_cache_threshold", threshold,
                listOf(2, 3, 5, 8, 10).map { it to pinnedString("settings_storage_plays", it) }) {
                threshold = it; getPreferences("melox_downloads").putInt("downloads_auto_cache_threshold", it)
                MeloXSettingsPreferences.setInt("downloads_auto_cache_threshold", it)
            }
        }
        StorageSectionTitle("settings_storage_auto_quality"); Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            MusicQuality.entries.forEach { entry -> SettingsChoiceRow(qualityTitle(entry), quality == entry.name, "auto-cache-quality-${entry.name}") {
                quality = entry.name; getPreferences("melox_downloads").putString("downloads_auto_cache_quality", entry.name)
                MeloXSettingsPreferences.setString("downloads_auto_cache_quality", entry.name)
            } }
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_storage_reset_stats")) { downloads.resetAutomaticCacheHistory() }
    }
    Spacer(Modifier.height(10.dp))
    if (downloads.activeDownloads.isNotEmpty()) {
        StorageSectionTitle("settings_storage_downloading")
        SettingsGlassGroup { downloads.activeDownloads.values.forEach { active ->
            MeloXIosListRow(title = active.song.name, subtitle = active.fractionCompleted?.let {
                pinnedString("settings_storage_download_progress", (it * 100).toInt(), qualityTitle(active.quality))
            } ?: qualityTitle(active.quality), showChevron = false, trailingContent = {
                Text(pinnedString("settings_storage_cancel"), Modifier.clickable { downloads.cancel(active.song.id) }.padding(8.dp), color = MaterialTheme.colorScheme.error)
            })
        } }
    }
    if (downloads.downloads.isNotEmpty()) {
        Spacer(Modifier.height(12.dp)); SettingsDangerButton(pinnedString("settings_storage_delete_all")) { downloads.removeAll() }
    }
    (downloads.errorMessage ?: message)?.let { Text(it, Modifier.padding(top = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(pinnedString("settings_storage_repair"), Modifier.testTag("storage-repair")) {
        downloads.repairStorage { result -> message = result.fold(onSuccess = {
            pinnedString("settings_storage_repair_done", it.missingRecordsRemoved, it.orphanFilesRemoved, settingsFormatBytes(it.recoveredBytes))
        }, onFailure = { it.message ?: pinnedString("settings_storage_repair_failed") }); scope.launch { refresh() } }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(pinnedString("settings_storage_clear_temp"), Modifier.testTag("storage-clear-temp")) { confirmation = "temp" }
    confirmation?.let { action ->
        SettingsConfirmation(pinnedString("settings_storage_confirm_${action}_title"), pinnedString("settings_storage_confirm_${action}_body"), onDismiss = { confirmation = null }) {
            confirmation = null
            scope.launch {
                runCatching { withContext(Dispatchers.IO) {
                    if (action in listOf("all", "network")) {
                        melox.network.MeloXHttpClient.clearCache()
                        melox.ui.artwork.MeloXArtworkMemoryCache.clear()
                        melox.ui.player.ArtworkDynamicPaletteProvider.clearMemoryCache()
                    }
                    if (action in listOf("all", "playback")) melox.playback.MeloXMediaCache.clear()
                    if (action == "temp") File(cache, "automix_analysis").deleteRecursively()
                } }.onSuccess { message = pinnedString("settings_storage_cleared") }.onFailure { message = it.message }
                refresh()
            }
        }
    }
}

@Composable
private fun StorageSectionTitle(key: String) {
    Text(pinnedString(key), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
}
