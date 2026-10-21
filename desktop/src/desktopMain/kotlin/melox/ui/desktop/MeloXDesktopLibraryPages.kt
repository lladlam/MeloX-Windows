package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.download.MeloXDownloadStore
import melox.download.MeloXProviderDownloadStore
import melox.model.SearchSong
import melox.music.provider.MeloXLegacyUiBridge
import melox.network.MeloXCloudSong
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.ui.theme.MeloXDesktopTextFontFamily

/** DesktopLibraryView song/cloud/download sections. Every row is the Mac track row. */
@Composable
internal fun DesktopLibrarySongs(titleKey: String, songs: List<SearchSong>, loading: Boolean = false, error: String? = null,
    onRetry: () -> Unit = {}, onLogin: (() -> Unit)? = null, headerActions: @Composable RowScope.() -> Unit = {},
    preamble: LazyListScope.() -> Unit = {}, rowActions: (@Composable (SearchSong) -> Unit)? = null, tag: String) {
    val scroll = rememberLazyListState()
    val queue by PlaybackCommands.queue.collectAsState()
    val playback by melox.player.AudioPlayer.state.collectAsState()
    val current = queue.songs.getOrNull(queue.index)?.id
    var selected by remember { mutableStateOf<SearchSong?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).desktopPromotedDragScroll(scroll).testTag(tag), state = scroll,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = 28.dp)) {
            item("title") {
                Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(desktopString(titleKey), Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    headerActions()
                }
            }
            preamble()
            when {
                loading && songs.isEmpty() -> item("loading") { Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                songs.isEmpty() -> item("empty") {
                    Column(Modifier.fillMaxWidth().heightIn(min = 340.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
                        Text(desktopString(if (onLogin == null) "ui.desktop.library.empty" else "ui.desktop.library.sign_in_title"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(error ?: desktopString(if (onLogin == null) "ui.desktop.library.empty.message" else "ui.desktop.library.sign_in_message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (onLogin != null) DesktopDialogButton("ui.common.login", prominent = true, onClick = onLogin)
                        else if (error != null) DesktopDialogButton("ui.common.retry", onClick = onRetry)
                    }
                }
                else -> {
                    if (error != null) item("error") { Text(error, color = MaterialTheme.colorScheme.error); DesktopDialogButton("ui.common.retry", onClick = onRetry) }
                    itemsIndexed(songs, key = { _, song -> song.providerTrack?.id?.toString() ?: song.id.toString() }) { index, song ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { DesktopCollectionTrackRow(index + 1, song, song.id == current, playback.isPlaying, false,
                                { PlaybackCommands.playQueue(songs, song.id) }, { anchor = it; selected = song }, showsArtwork = true) }
                            rowActions?.invoke(song)
                        }
                        Spacer(Modifier.height(2.dp))
                    }
                }
            }
            item("clearance") { Spacer(Modifier.height(70.dp)) }
        }
    }
    selected?.let { MeloXDesktopSongMenu(it, { selected = null }, anchor) }
}

@Composable
internal fun MeloXDesktopDownloads() {
    val store = remember { MeloXDownloadStore.instance() }
    val providers = remember { MeloXProviderDownloadStore.get() }
    val songs = store.downloadedSongs + providers.downloads.map { MeloXLegacyUiBridge.track(it.track) }
    var deleting by remember { mutableStateOf<SearchSong?>(null) }
    DesktopLibrarySongs("ui.navigation.downloads", songs, tag = "desktop-downloads", headerActions = {
        DesktopDialogButton("ui.common.play_all", songs.isNotEmpty(), "desktop-downloads-play") { songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id) } }
    }, preamble = {
        if (store.activeDownloads.isNotEmpty()) {
            item("active-title") { Text(desktopString("ui.common.download"), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            store.activeDownloads.values.toList().forEach { download -> item("active:${download.song.id}") {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(download.song.name, Modifier.weight(1f))
                    download.fractionCompleted?.let { Text("${(it * 100).toInt()}%") }
                    DesktopDialogButton("ui.common.cancel", tag = "desktop-download-cancel:${download.song.id}") { store.cancel(download.song.id) }
                }
            } }
        }
    }, rowActions = { song -> DesktopSettingsButton(desktopString("ui.downloads.delete"), Modifier.testTag("desktop-download-remove:${song.id}"), destructive = true) { deleting = song } })
    deleting?.let { song -> DesktopMessageDialog("ui.downloads.delete", song.name, { deleting = null }, confirmKey = "ui.downloads.delete", onConfirm = {
        song.providerTrack?.let { providers.remove(it.id) } ?: store.remove(song.id)
        deleting = null
    }) }
    store.errorMessage?.let { DesktopMessageDialog("ui.library.error.title", it, store::clearError) }
}

@Composable
internal fun MeloXDesktopCloud(onLogin: () -> Unit) {
    val revision by NeteaseSessionStore.revision.collectAsState()
    val loggedIn = NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())
    val cookie = remember(revision) { NeteaseSessionStore.readCookie() }
    val client = remember(cookie) { NeteaseUniversalSearchClient(cookieProvider = { cookie }) }
    val scope = rememberCoroutineScope()
    var values by remember(cookie) { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var loading by remember(cookie) { mutableStateOf(loggedIn) }
    var uploading by remember { mutableStateOf(false) }
    var uploadJob by remember { mutableStateOf<Job?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember(cookie) { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MeloXCloudSong?>(null) }
    suspend fun refresh() {
        loading = true; error = null
        try {
            val all = mutableListOf<MeloXCloudSong>()
            do {
                val page = client.cloudSongs(offset = all.size)
                all += page.values
            } while (page.hasMore && page.values.isNotEmpty())
            values = all.distinctBy { it.id }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    LaunchedEffect(client) { if (loggedIn) refresh() }
    DisposableEffect(cookie) { onDispose { uploadJob?.cancel() } }
    DesktopLibrarySongs("ui.navigation.cloud", values.map { it.song }, loading, error, { scope.launch { refresh() } }, if (loggedIn) null else onLogin,
        headerActions = {
            DesktopDialogButton("ui.cloud.upload_music", loggedIn && !uploading, "cloud-upload") {
                uploading = true; error = null
                uploadJob = scope.launch {
                    try {
                        val selected = melox.ui.screens.chooseCloudAudioFile() ?: return@launch
                        client.uploadCloudSong(selected) { progress -> scope.launch { status = progress.stage + if (progress.totalBytes > 0) " · ${progress.uploadedBytes * 100 / progress.totalBytes}%" else "" } }
                        refresh()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
                    finally { uploading = false; status = null }
                }
            }
            DesktopDialogButton("ui.common.refresh", loggedIn && !loading && !uploading, "cloud-refresh") { scope.launch { refresh() } }
        }, preamble = {
            if (uploading) item("uploading") { Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(status ?: desktopString("ui.cloud.upload_music"), Modifier.weight(1f))
                DesktopDialogButton("ui.common.cancel", tag = "cloud-upload-cancel") { uploadJob?.cancel() }
            } }
        }, rowActions = { song -> DesktopSettingsButton(desktopString("ui.cloud.delete"), Modifier.testTag("desktop-cloud-remove:${song.id}"), destructive = true) { deleting = values.firstOrNull { it.song == song } } }, tag = "desktop-cloud")
    deleting?.let { item -> DesktopMessageDialog("ui.cloud.delete", item.song.name, { deleting = null }, confirmKey = "ui.cloud.delete", onConfirm = {
        deleting = null
        scope.launch {
            try { client.deleteCloudSong(item.id); values = values.filterNot { it.id == item.id } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        }
    }) }
}
