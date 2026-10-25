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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.network.MeloXAlbumDetail
import melox.network.NeteaseCollectionDetailsClient
import melox.playback.PlaybackCommands
import melox.ui.desktop.desktopString
import melox.ui.desktop.desktopFormat
import kotlinx.coroutines.launch
import melox.settings.MeloXSettingsRuntime
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun AlbumDetailScreen(
    albumId: String,
    albumName: String,
    onBack: () -> Unit,
) {
    val revision by NeteaseSessionStore.revision.collectAsState()
    key(albumId, revision) { AlbumDetailContent(albumId, albumName, onBack) }
}

@Composable
private fun AlbumDetailContent(albumId: String, albumName: String, onBack: () -> Unit) {
    val parsedId = albumId.toLongOrNull()
    val sessionCookie = remember { NeteaseSessionStore.readCookie() }
    val client = remember {
        NeteaseCollectionDetailsClient(cookieProvider = { sessionCookie })
    }
    var detail by remember(albumId) { mutableStateOf<MeloXAlbumDetail?>(null) }
    var loading by remember(albumId) { mutableStateOf(parsedId != null) }
    var error by remember(albumId) { mutableStateOf(if (parsedId == null) "无效的专辑" else null) }
    val queue by PlaybackCommands.queue.collectAsState()
    val playingId = queue.songs.getOrNull(queue.index)?.id ?: PlaybackCommands.currentSongId()
    val playbackState by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val scope = rememberCoroutineScope()
    val accountRevision by NeteaseSessionStore.revision.collectAsState()
    val loggedIn = remember(accountRevision) { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    var refreshRevision by remember(albumId) { mutableStateOf(0) }
    var subscribed by remember(albumId) { mutableStateOf(false) }
    var changingSubscription by remember(albumId) { mutableStateOf(false) }
    var operation by remember(albumId) { mutableStateOf<String?>(null) }
    var actionSong by remember(albumId) { mutableStateOf<SearchSong?>(null) }

    LaunchedEffect(parsedId, refreshRevision, accountRevision) {
        if (parsedId == null) return@LaunchedEffect
        loading = true
        error = null
        detail = null
        try {
            val loaded = client.albumDetail(parsedId)
            detail = loaded; subscribed = loaded.subscribed == true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: "专辑加载失败" }
        finally { loading = false }
    }

    val album = detail?.album
    val songs = detail?.songs.orEmpty()
    val title = album?.name?.ifBlank { albumName } ?: albumName
    val artistName = album?.artistText?.ifBlank { null } ?: "未知歌手"
    val totalMinutes = (songs.sumOf { it.durationMs } / 1000 / 60).toInt()
    val canSubscribe = loggedIn && detail?.subscribed != null && !loading && !changingSubscription && parsedId != null
    fun toggleSubscription() {
        val id = parsedId ?: return
        if (!canSubscribe) return
        val target = !subscribed
        val cookie = NeteaseSessionStore.readCookie()
        changingSubscription = true
        scope.launch {
            try {
                check(cookie == NeteaseSessionStore.readCookie()) { "账号已切换，请重试" }
                client.setAlbumSubscribed(id, target)
                check(cookie == NeteaseSessionStore.readCookie()) { "账号已切换，请刷新专辑" }
                subscribed = target; operation = if (target) "已收藏专辑" else "已取消收藏"
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { operation = failure.message ?: "收藏操作失败" }
            finally { changingSubscription = false }
        }
    }
    fun downloadAlbum() {
        if (loading || songs.isEmpty() || !MeloXSettingsRuntime.downloadsEnabled) return
        val store = melox.download.MeloXDownloadStore.instance()
        songs.forEach { store.start(it, melox.audio.MusicQualityPreferences.read()) }
        operation = "已添加 ${songs.size} 首歌曲到下载队列"
    }

    if (MeloXSettingsRuntime.desktopLayoutEnabled) {
        var menuAnchor by remember { mutableStateOf<androidx.compose.ui.unit.IntOffset?>(null) }
        melox.ui.desktop.MeloXDesktopCollectionDetail(
            kind = album?.type ?: desktopString("ui.common.album"),
            title = title,
            subtitle = album?.artistText?.takeIf(String::isNotBlank),
            metadata = buildList {
                album?.publishTime?.let { add(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).year.toString()) }
                album?.size?.let { add(desktopFormat("ui.common.song_count", it)) }
                add(melox.ui.desktop.desktopQualityTitle(melox.audio.MusicQualityPreferences.read()))
            }.joinToString(desktopString("ui.common.metadata_separator")),
            description = detail?.description,
            artworkUrl = album?.artworkUrl,
            songs = songs,
            loading = loading,
            error = error,
            playingId = playingId,
            isPlaying = playbackState.isPlaying,
            showsAlbumColumn = false,
            groupsByDisc = true,
            onRetry = { refreshRevision++ },
            onBack = onBack,
            onPlayAll = { songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id) } },
            onShuffle = { songs.shuffled().let { shuffled -> shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(shuffled, it.id) } } },
            onSong = { PlaybackCommands.playQueue(songs, it.id) },
            isFavorite = subscribed,
            favoriteEnabled = canSubscribe,
            onFavorite = ::toggleSubscription,
            onDownload = ::downloadAlbum,
            onMore = { actionSong = it },
            onMenuAnchor = { menuAnchor = it },
            status = operation,
            tag = "desktop-album-detail",
        )
        actionSong?.let { song -> MeloXSongActionsOverlay(song, songs, true, { actionSong = null }, desktopAnchor = menuAnchor) }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(MeloXColors.pageBackground()).testTag("album-detail")) {
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
                text = title,
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
                color = MeloXColors.pageOnSurfaceVariant(),
                modifier = Modifier.testTag("album-share").clickable(enabled = parsedId != null) {
                    runCatching {
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                            java.awt.datatransfer.StringSelection("https://music.163.com/#/album?id=$parsedId"), null)
                    }.onSuccess { operation = "已复制专辑链接" }.onFailure { operation = it.message ?: "复制链接失败" }
                }
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp).then(if (detail != null) Modifier.testTag("album-loaded") else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    MeloXArtworkImage(
                        url = album?.artworkUrl,
                        fallbackColor = MeloXColors.Primary,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = title,
                    )
                    if (album?.artworkUrl.isNullOrBlank()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "♫",
                                fontSize = 48.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                fontFamily = MeloXLanTingProFontFamily
                            )
                            Text(
                                text = artistName,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeloXColors.pageOnSurface(),
                        fontFamily = MeloXLanTingProFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = artistName,
                        fontSize = 14.sp,
                        color = MeloXColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${songs.size}首 · ${totalMinutes}分钟",
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
                        onClick = ::toggleSubscription,
                        enabled = canSubscribe,
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp).testTag("album-favorite")
                    ) {
                        Text(
                            text = if (changingSubscription) "正在更新…" else if (subscribed) "♥ 已收藏" else if (!loggedIn) "登录后收藏" else "♡ 收藏",
                            fontSize = 13.sp,
                            color = MeloXColors.pageOnSurface(),
                            fontFamily = MeloXLanTingProFontFamily
                        )
                    }
                    OutlinedButton(
                        onClick = ::downloadAlbum,
                        enabled = !loading && songs.isNotEmpty() && MeloXSettingsRuntime.downloadsEnabled,
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp).testTag("album-download")
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

            operation?.let { message -> item { Text(message, Modifier.padding(16.dp).testTag("album-operation-status")) } }

            item {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MeloXColors.Divider
                )
            }

            when {
                loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MeloXColors.Primary)
                    }
                }
                error != null -> item {
                    TextButton(onClick = { refreshRevision++ }, modifier = Modifier.testTag("album-retry")) { Text("重试") }
                    Text(
                        text = error.orEmpty(),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        color = MeloXColors.Error,
                        fontSize = 14.sp,
                    )
                }
                songs.isEmpty() -> item {
                    Text(
                        text = "专辑是空的",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        color = MeloXColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                else -> itemsIndexed(songs) { index, song ->
                    AlbumSongRow(
                        index = index + 1,
                        song = song,
                        isPlaying = playingId == song.id,
                        onClick = { PlaybackCommands.playQueue(songs, song.id) },
                    )
                }
            }

            if (!detail?.description.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        HorizontalDivider(color = MeloXColors.Divider)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "专辑信息",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MeloXColors.TextSecondary,
                            fontFamily = MeloXLanTingProFontFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = detail?.description.orEmpty(),
                            fontSize = 12.sp,
                            color = MeloXColors.TextTertiary
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AlbumSongRow(
    index: Int,
    song: SearchSong,
    isPlaying: Boolean,
    onClick: () -> Unit
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
        Text(
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
            modifier = Modifier.padding(start = 12.dp).clickable { }
        )
    }
}
