package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.*
import melox.settings.MeloXSettingsRuntime
import melox.ui.navigation.MeloXDetailOverlay

@Composable
internal fun UnifiedFavoritesSettings() {
    val revision by MeloXUnifiedFavorites.revision.collectAsState()
    val enabled = remember(revision) { runCatching { MeloXUnifiedFavorites.enabled() }.getOrDefault(false) }
    var setup by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<MusicSource>()) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val candidates = remember { MeloXMusicProviders.create().providers.filter {
        it.source == MusicSource.Local || it.source == MusicSource.Netease || it is UserLibraryCapability && it is PlaylistCapability
    }.map { it.source } }
    SettingsExternalToggleRow("弥乐「我喜欢」", enabled, "unified-favorites-enabled", enabled = !busy,
        subtitle = "仅在弥乐内保存；可收藏任意音源，不写入音源账号。") { desired ->
        try {
            if (!desired || MeloXUnifiedFavorites.initialized()) MeloXUnifiedFavorites.setEnabled(desired)
            else { selected = emptySet(); notice = null; setup = true }
        } catch (failure: Exception) { notice = failure.message }
    }
    if (enabled) Text("已启用 · ${runCatching { MeloXUnifiedFavorites.tracks().size }.getOrNull()?.let { "$it 首" } ?: "读取失败"}",
        Modifier.padding(12.dp).testTag("unified-favorites-status"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    notice?.let { Text(it, Modifier.padding(12.dp).testTag("unified-favorites-notice")) }
    if (setup) AlertDialog(
        onDismissRequest = { if (!busy) setup = false },
        title = { Text("创建弥乐「我喜欢」") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
            Text("可选：从已登录音源读取原有收藏。不会修改原音源收藏；不选来源可创建空歌单。读取失败会按来源报告。")
            candidates.forEach { source ->
                Row(Modifier.fillMaxWidth().testTag("unified-favorites-import:${source.name}")
                    .clickable(enabled = !busy) { selected = if (source in selected) selected - source else selected + source },
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(source in selected, onCheckedChange = null)
                    Text(source.displayName, Modifier.padding(8.dp))
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(24.dp))
        } },
        confirmButton = { TextButton(enabled = !busy, modifier = Modifier.testTag("unified-favorites-create"), onClick = {
            busy = true
            scope.launch {
                try {
                    val report = MeloXUnifiedFavorites.initialize(selected)
                    notice = "已创建 · ${report.total} 首" + report.failures.joinToString("", prefix = if (report.failures.isEmpty()) "" else "\n") {
                        "${it.source.displayName}：${it.message}\n"
                    }
                    setup = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { notice = failure.message ?: "本地收藏创建失败"; setup = false }
                finally { busy = false }
            }
        }) { Text(if (busy) "正在读取…" else "创建") } },
        dismissButton = { TextButton({ setup = false }, enabled = !busy) { Text("取消") } },
    )
}

/** Account-independent library entry, above the source-specific login gate in both layouts. */
@Composable
internal fun UnifiedFavoritesLibraryHost(content: @Composable () -> Unit) {
    val revision by MeloXUnifiedFavorites.revision.collectAsState()
    val enabled = remember(revision) { runCatching { MeloXUnifiedFavorites.enabled() }.getOrDefault(false) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) open = false }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (enabled) {
                val count = remember(revision) { runCatching { MeloXUnifiedFavorites.tracks().size }.getOrNull() }
                Row(Modifier.fillMaxWidth().testTag("unified-favorites-library")
                    .clickable { open = true }.padding(horizontal = if (MeloXSettingsRuntime.desktopLayoutEnabled) 34.dp else 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("♥", color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("弥乐「我喜欢」", fontWeight = FontWeight.SemiBold)
                        Text("本地收藏 · ${count?.let { "$it 首" } ?: "读取失败"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("›")
                }
                HorizontalDivider()
            }
            Box(Modifier.weight(1f)) { content() }
        }
        MeloXDetailOverlay(if (open && enabled) Unit else null, { open = false }) {
            UnifiedFavoritesDetail { open = false }
        }
    }
}

@Composable
private fun UnifiedFavoritesDetail(onBack: () -> Unit) {
    val revision by MeloXUnifiedFavorites.revision.collectAsState()
    val tracks = remember(revision) { runCatching { MeloXUnifiedFavorites.tracks() } }
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    var menu by remember { mutableStateOf<MusicTrack?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun mutate(action: () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { withContext(Dispatchers.IO) { action() } }
            catch (failure: Exception) { if (failure is CancellationException) throw failure; error = failure.message }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("unified-favorites-detail")
        .semantics { stateDescription = "count=${tracks.getOrNull()?.size ?: 0}" }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = if (desktop) 34.dp else 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onBack, Modifier.testTag("unified-favorites-back")) { Text("返回") }
            Text("我喜欢", Modifier.weight(1f), fontSize = if (desktop) 28.sp else 22.sp, fontWeight = FontWeight.Bold)
            val values = tracks.getOrDefault(emptyList())
            TextButton({ values.firstOrNull()?.let { melox.playback.ProviderPlaybackCommands.playQueue(values, it.id) } }, enabled = values.isNotEmpty(),
                modifier = Modifier.testTag("unified-favorites-play")) { Text("播放全部") }
        }
        Text("弥乐本地收藏，不依赖任何音源账号", Modifier.padding(horizontal = if (desktop) 34.dp else 18.dp), fontSize = 12.sp)
        tracks.exceptionOrNull()?.let { Text(it.message ?: "收藏读取失败", color = MaterialTheme.colorScheme.error) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("unified-favorites-error")) }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 156.dp, top = 12.dp)) {
            if (tracks.getOrDefault(emptyList()).isEmpty() && tracks.isSuccess) item { Text("暂无收藏，可在歌曲菜单中添加", Modifier.padding(18.dp)) }
            itemsIndexed(tracks.getOrDefault(emptyList()), key = { _, track -> "${track.id.source.storageValue}:${track.id.value}" }) { index, track ->
                val song = MeloXLegacyUiBridge.track(track)
                Column(Modifier.testTag("unified-favorites-track:$index").semantics { stateDescription = "${track.id.source.storageValue}|${track.id.value}" }) {
                    if (desktop) Box(Modifier.padding(horizontal = 34.dp)) {
                        melox.ui.desktop.DesktopCollectionTrackRow(index + 1, song, false, false, true,
                            { melox.playback.ProviderPlaybackCommands.playQueue(tracks.getOrThrow(), track.id) }, { anchor = it; menu = track }, showsArtwork = true)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { MeloXLibraryTrackRow(song) { melox.playback.ProviderPlaybackCommands.playQueue(tracks.getOrThrow(), track.id) } }
                            IconButton({ anchor = null; menu = track }, Modifier.testTag("unified-favorites-menu:$index")) {
                                melox.ui.foundation.MeloXSymbolIcon(melox.ui.foundation.MeloXSymbol.Ellipsis, size = 20)
                            }
                        }
                    }
                    Row(Modifier.padding(horizontal = if (desktop) 34.dp else 18.dp)) {
                        TextButton({ mutate { MeloXUnifiedFavorites.reorder(track, index - 1) } }, enabled = !busy && index > 0,
                            modifier = Modifier.testTag("unified-favorites-up:$index")) { Text("上移") }
                        TextButton({ mutate { MeloXUnifiedFavorites.reorder(track, index + 1) } }, enabled = !busy && index < tracks.getOrThrow().lastIndex,
                            modifier = Modifier.testTag("unified-favorites-down:$index")) { Text("下移") }
                        Spacer(Modifier.weight(1f))
                        TextButton({ mutate { MeloXUnifiedFavorites.set(track, false) } }, enabled = !busy,
                            modifier = Modifier.testTag("unified-favorites-remove:$index")) { Text("移除收藏") }
                    }
                }
            }
        }
    }
    menu?.let { track -> MeloXSongActionsOverlay(MeloXLegacyUiBridge.track(track), tracks.getOrDefault(emptyList()).map(MeloXLegacyUiBridge::track),
        true, { menu = null }, desktopAnchor = anchor) }
}
