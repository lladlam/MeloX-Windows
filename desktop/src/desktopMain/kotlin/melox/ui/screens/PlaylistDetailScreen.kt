package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import melox.settings.MeloXSettingsRuntime
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistDetail
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.PlaylistCapability
import melox.playback.PlaybackCommands
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun formatTotalDuration(ms: Long): String {
    val totalMinutes = (ms / 1000 / 60).toInt()
    return "${totalMinutes}分钟"
}

@Composable
fun PlaylistDetailScreen(
    playlist: NeteasePlaylistSummary,
    onBack: () -> Unit,
) {
    val client = remember { NeteaseLibraryClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var detail by remember(playlist.id, playlist.providerPlaylist?.id) { mutableStateOf<NeteasePlaylistDetail?>(null) }
    var loading by remember(playlist.id, playlist.providerPlaylist?.id) { mutableStateOf(true) }
    var error by remember(playlist.id, playlist.providerPlaylist?.id) { mutableStateOf<String?>(null) }
    val currentSongId by remember { derivedStateOf { PlaybackCommands.currentSongId() } }
    val queue by PlaybackCommands.queue.collectAsState()
    val playingId = queue.songs.getOrNull(queue.index)?.id ?: currentSongId
    val scope = rememberCoroutineScope()
    var filter by remember(playlist.id) { mutableStateOf("") }
    var showSearch by remember(playlist.id, MeloXSettingsRuntime.playlistSearchBarHidden) { mutableStateOf(!MeloXSettingsRuntime.playlistSearchBarHidden) }
    var actionSong by remember(playlist.id) { mutableStateOf<SearchSong?>(null) }
    var refreshRevision by remember(playlist.id) { mutableStateOf(0) }
    var accountUserId by remember { mutableStateOf<Long?>(null) }
    var subscribed by remember(playlist.id) { mutableStateOf<Boolean?>(null) }
    var operation by remember(playlist.id) { mutableStateOf<String?>(null) }
    var busy by remember(playlist.id) { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val searchScroll = remember(listState) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0f && !listState.canScrollBackward) showSearch = true
            return Offset.Zero
        }
    } }

    LaunchedEffect(Unit) {
        if (NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())) {
            runCatching { melox.network.NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }).accountProfile().userId }
                .onSuccess { accountUserId = it }
        }
    }
    LaunchedEffect(playlist.id, playlist.providerPlaylist?.id, refreshRevision) {
        loading = true
        error = null
        val backing = playlist.providerPlaylist
        val result = runCatching {
            if (backing != null) {
                val source = backing.id.source
                val registry = MeloXMusicProviders.create()
                val provider = registry.require(source)
                val capability = provider as? PlaylistCapability
                    ?: error("${source.displayName} 当前不提供歌单详情")
                val providerDetail = withContext(Dispatchers.IO) {
                    capability.playlistDetail(backing)
                }
                MeloXLegacyUiBridge.playlistDetail(providerDetail)
            } else {
                client.playlistDetail(playlist.id)
            }
        }
        result.onSuccess { detail = it }.onFailure { error = it.message ?: "歌单加载失败" }
        loading = false
    }

    val shown = detail?.summary ?: playlist
    val songs = detail?.songs.orEmpty()
    val filtered = songs.filter { filter.isBlank() || it.name.contains(filter, true) || it.artists.contains(filter, true) || it.album.contains(filter, true) }
    val totalDurationMs = songs.sumOf { it.durationMs }

    Column(modifier = Modifier.fillMaxSize().testTag("playlist-detail").background(MeloXColors.pageBackground())) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 20.sp,
                color = MeloXColors.pageOnSurface(),
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = shown.name,
                fontSize = 16.sp,
                color = MeloXColors.pageOnSurface(),
                fontWeight = FontWeight.Medium,
                fontFamily = MeloXLanTingProFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "⤴",
                fontSize = 20.sp,
                color = MeloXColors.pageOnSurfaceVariant()
            )
        }

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(searchScroll), contentPadding = PaddingValues(bottom = 140.dp)) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    MeloXArtworkImage(
                        url = shown.coverUrl,
                        fallbackColor = MeloXColors.Primary,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = shown.name,
                    )
                    if (shown.coverUrl.isNullOrBlank()) {
                        Text(
                            text = "♫",
                            fontSize = 64.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = shown.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeloXColors.pageOnSurface(),
                        fontFamily = MeloXLanTingProFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "创建者: ${shown.creatorName.ifBlank { "未知" }}",
                        fontSize = 14.sp,
                        color = MeloXColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${if (detail != null) songs.size else shown.trackCount}首 · ${formatTotalDuration(totalDurationMs)}",
                        fontSize = 13.sp,
                        color = MeloXColors.TextTertiary
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id) } },
                        enabled = songs.isNotEmpty(),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeloXColors.Primary),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "▶ 播放全部",
                            fontSize = 13.sp,
                            color = Color.White,
                            fontFamily = MeloXLanTingProFontFamily
                        )
                    }
                    OutlinedButton(
                        enabled = songs.isNotEmpty() && !busy && playlist.providerPlaylist == null,
                        onClick = {
                            busy = true
                            scope.launch {
                                try {
                                    melox.network.NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }).setPlaylistSubscribed(shown.id, subscribed != true)
                                    subscribed = subscribed != true
                                    operation = if (subscribed == true) "已收藏" else "已取消收藏"
                                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                                catch (failure: Exception) { operation = failure.message ?: "收藏失败" }
                                finally { busy = false }
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = if (subscribed == true) "♡ 已收藏" else "♡ 收藏",
                            fontSize = 13.sp,
                            color = MeloXColors.pageOnSurface(),
                            fontFamily = MeloXLanTingProFontFamily
                        )
                    }
                    OutlinedButton(
                        enabled = songs.isNotEmpty() && playlist.providerPlaylist == null,
                        onClick = {
                            val store = melox.download.MeloXDownloadStore.instance()
                            val ref = melox.download.MeloXDownloadPlaylistRef(shown.id, shown.name, shown.coverUrl)
                            songs.forEach { store.start(it, melox.audio.MusicQualityPreferences.read(), ref) }
                            operation = "已加入下载队列"
                        },
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "↓ 下载",
                            fontSize = 13.sp,
                            color = MeloXColors.pageOnSurface(),
                            fontFamily = MeloXLanTingProFontFamily
                        )
                    }
                }
            }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MeloXColors.Divider
                )
            }
            operation?.let { text -> item { Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            if (showSearch) item("search") {
                melox.ui.glass.MeloXGlassTextField(value = filter, onValueChange = { filter = it },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp).testTag("playlist-search"),
                    placeholder = { Text("搜索歌单") })
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeloXColors.Primary)
                    }
                }
                error != null -> item {
                    Text(
                        text = error.orEmpty(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        color = MeloXColors.Error,
                        fontSize = 14.sp,
                    )
                }
                songs.isEmpty() -> item {
                    Text(
                        text = "歌单是空的",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        color = MeloXColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                else -> itemsIndexed(filtered, key = { _, song -> song.providerTrack?.id?.toString() ?: song.id.toString() }) { index, song ->
                    SongRow(
                        index = index + 1,
                        song = song,
                        isPlaying = playingId == song.id,
                        onClick = { PlaybackCommands.playQueue(songs, song.id) },
                        onMore = { actionSong = song },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
    actionSong?.let { song ->
        MeloXSongActionsOverlay(song, songs, true, { actionSong = null },
            sourcePlaylist = if (playlist.providerPlaylist == null) melox.download.MeloXDownloadPlaylistRef(shown.id, shown.name, shown.coverUrl) else null,
            sourceOwnedPlaylistId = shown.id.takeIf { playlist.providerPlaylist == null && accountUserId != null && shown.creatorUserId == accountUserId },
            sourceProviderPlaylist = playlist.providerPlaylist,
            onSourcePlaylistChanged = { refreshRevision++ })
    }
}

@Composable
private fun SongRow(
    index: Int,
    song: SearchSong,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .hoverable(interactionSource)
            .background(
                if (isHovered) MeloXColors.CardBackgroundHover else Color.Transparent
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (MeloXSettingsRuntime.playlistShowArtwork) {
            MeloXArtworkImage(song.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(40.dp).clip(RoundedCornerShape(5.dp)))
            Spacer(Modifier.width(12.dp))
        } else Text(
            text = index.toString(),
            fontSize = 14.sp,
            color = MeloXColors.TextTertiary,
            modifier = Modifier.width(30.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.name,
                fontSize = 15.sp,
                color = if (isPlaying) MeloXColors.Primary else MeloXColors.pageOnSurface(),
                fontWeight = if (isPlaying) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = MeloXLanTingProFontFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = song.artists,
                fontSize = 12.sp,
                color = MeloXColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatDuration(song.durationMs),
            fontSize = 13.sp,
            color = MeloXColors.TextTertiary,
            modifier = Modifier.padding(start = 8.dp)
        )

        Text(
            text = "⋯",
            fontSize = 18.sp,
            color = MeloXColors.pageOnSurfaceVariant(),
            modifier = Modifier.padding(start = 12.dp).clickable(onClick = onMore).testTag("playlist-song-more-${song.id}")
        )
    }
}
