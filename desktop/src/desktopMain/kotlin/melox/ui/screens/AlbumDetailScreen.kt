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
    val parsedId = albumId.toLongOrNull()
    val client = remember {
        NeteaseCollectionDetailsClient(cookieProvider = { NeteaseSessionStore.readCookie() })
    }
    var detail by remember(albumId) { mutableStateOf<MeloXAlbumDetail?>(null) }
    var loading by remember(albumId) { mutableStateOf(parsedId != null) }
    var error by remember(albumId) { mutableStateOf(if (parsedId == null) "无效的专辑" else null) }
    val queue by PlaybackCommands.queue.collectAsState()
    val playingId = queue.songs.getOrNull(queue.index)?.id ?: PlaybackCommands.currentSongId()

    LaunchedEffect(parsedId) {
        if (parsedId == null) return@LaunchedEffect
        loading = true
        error = null
        runCatching { client.albumDetail(parsedId) }
            .onSuccess { detail = it }
            .onFailure { error = it.message ?: "专辑加载失败" }
        loading = false
    }

    val album = detail?.album
    val songs = detail?.songs.orEmpty()
    val title = album?.name?.ifBlank { albumName } ?: albumName
    val artistName = album?.artistText?.ifBlank { null } ?: "未知歌手"
    val totalMinutes = (songs.sumOf { it.durationMs } / 1000 / 60).toInt()

    Column(modifier = Modifier.fillMaxSize().background(MeloXColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 20.sp,
                color = MeloXColors.OnSurface,
                modifier = Modifier.clickable { onBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = title,
                fontSize = 16.sp,
                color = MeloXColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontFamily = MeloXLanTingProFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "⤴",
                fontSize = 20.sp,
                color = MeloXColors.OnSurfaceVariant
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
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
                        color = MeloXColors.TextPrimary,
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
                        onClick = { },
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "♡ 收藏",
                            fontSize = 13.sp,
                            color = MeloXColors.OnSurface,
                            fontFamily = MeloXLanTingProFontFamily
                        )
                    }
                    OutlinedButton(
                        onClick = { },
                        shape = RoundedCornerShape(24.dp),
                        border = ButtonDefaults.outlinedButtonBorder,
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(
                            text = "↓ 下载",
                            fontSize = 13.sp,
                            color = MeloXColors.OnSurface,
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
                color = if (isPlaying) MeloXColors.Primary else MeloXColors.TextPrimary,
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
            color = MeloXColors.OnSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp).clickable { }
        )
    }
}
