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
import melox.music.model.*
import melox.player.AudioPlayer
import melox.ui.navigation.MeloXNavState
import melox.ui.navigation.Route
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private val mockTracks: List<MusicTrack> = listOf(
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "1"),
        title = "晴天",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 269_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "2"),
        title = "七里香",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "七里香"),
        durationMs = 299_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "3"),
        title = "夜曲",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "十一月的萧邦"),
        durationMs = 226_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "4"),
        title = "稻香",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "魔杰座"),
        durationMs = 223_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "5"),
        title = "告白气球",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "周杰伦的床边故事"),
        durationMs = 215_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "6"),
        title = "青花瓷",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "我很忙"),
        durationMs = 239_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "7"),
        title = "江南",
        artists = listOf(MusicArtistRef(name = "林俊杰")),
        album = MusicAlbumRef(name = "第二天堂"),
        durationMs = 286_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "8"),
        title = "修炼爱情",
        artists = listOf(MusicArtistRef(name = "林俊杰")),
        album = MusicAlbumRef(name = "因你而在"),
        durationMs = 330_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "9"),
        title = "可惜没如果",
        artists = listOf(MusicArtistRef(name = "林俊杰")),
        album = MusicAlbumRef(name = "新地球"),
        durationMs = 352_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "10"),
        title = "光年之外",
        artists = listOf(MusicArtistRef(name = "邓紫棋")),
        album = MusicAlbumRef(name = "光年之外"),
        durationMs = 235_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "11"),
        title = "泡沫",
        artists = listOf(MusicArtistRef(name = "邓紫棋")),
        album = MusicAlbumRef(name = "Xposed"),
        durationMs = 270_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "12"),
        title = "匆匆那年",
        artists = listOf(MusicArtistRef(name = "王菲")),
        album = MusicAlbumRef(name = "匆匆那年"),
        durationMs = 312_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "13"),
        title = "红豆",
        artists = listOf(MusicArtistRef(name = "王菲")),
        album = MusicAlbumRef(name = "唱游"),
        durationMs = 302_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "14"),
        title = "平凡之路",
        artists = listOf(MusicArtistRef(name = "朴树")),
        album = MusicAlbumRef(name = "猎户星座"),
        durationMs = 282_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "15"),
        title = "岁月神偷",
        artists = listOf(MusicArtistRef(name = "金玟岐")),
        album = MusicAlbumRef(name = "岁月神偷"),
        durationMs = 247_000,
    ),
)

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "--:--"
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
fun PlaylistDetailScreen(navState: MeloXNavState, playlistId: String, playlistName: String) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val totalDurationMs = mockTracks.sumOf { it.durationMs ?: 0L }

    Column(modifier = Modifier.fillMaxSize().background(MeloXColors.Background)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←",
                fontSize = 20.sp,
                color = MeloXColors.OnSurface,
                modifier = Modifier.clickable { navState.goBack() }
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = playlistName,
                fontSize = 16.sp,
                color = MeloXColors.TextPrimary,
                fontWeight = FontWeight.Medium,
                fontFamily = MeloXLanTingProFontFamily
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
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.linearGradient(
                                colors = listOf(MeloXColors.Primary, MeloXColors.SurfaceVariant)
                            )
                        ),
                        contentAlignment = Alignment.Center
                    ) {
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
                        text = playlistName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "创建者: MeloX",
                        fontSize = 14.sp,
                        color = MeloXColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${mockTracks.size}首 · ${formatTotalDuration(totalDurationMs)}",
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
                        onClick = { },
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

            itemsIndexed(mockTracks) { index, track ->
                TrackItem(
                    index = index + 1,
                    track = track,
                    isPlaying = currentTrack?.id == track.id,
                    onClick = { AudioPlayer.play("", track) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TrackItem(
    index: Int,
    track: MusicTrack,
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
                text = track.title,
                fontSize = 15.sp,
                color = if (isPlaying) MeloXColors.Primary else MeloXColors.TextPrimary,
                fontWeight = if (isPlaying) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = MeloXLanTingProFontFamily
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artistText,
                fontSize = 12.sp,
                color = MeloXColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = formatDuration(track.durationMs),
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
