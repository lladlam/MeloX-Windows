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

private val albumTracks: List<MusicTrack> = listOf(
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a1"),
        title = "晴天",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 269_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a2"),
        title = "七里香",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 299_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a3"),
        title = "夜曲",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 226_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a4"),
        title = "稻香",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 223_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a5"),
        title = "告白气球",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 215_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a6"),
        title = "青花瓷",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 239_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a7"),
        title = "简单爱",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 270_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a8"),
        title = "东风破",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 313_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a9"),
        title = "双截棍",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 193_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a10"),
        title = "以父之名",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 342_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a11"),
        title = "止战之殇",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 280_000,
    ),
    MusicTrack(
        id = MusicResourceId(MusicSource.Local, "a12"),
        title = "回到过去",
        artists = listOf(MusicArtistRef(name = "周杰伦")),
        album = MusicAlbumRef(name = "叶惠美"),
        durationMs = 250_000,
    ),
)

private fun formatDuration(ms: Long?): String {
    if (ms == null) return "--:--"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun AlbumDetailScreen(navState: MeloXNavState, albumId: String, albumName: String) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val artistName = "周杰伦"
    val totalDurationMs = albumTracks.sumOf { it.durationMs ?: 0L }
    val totalMinutes = (totalDurationMs / 1000 / 60).toInt()

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
                text = albumName,
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "♫",
                                fontSize = 48.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = albumName,
                                fontSize = 16.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                fontFamily = MeloXLanTingProFontFamily
                            )
                            Text(
                                text = "$artistName · 2024",
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
                        text = albumName,
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
                        text = "2024 · ${albumTracks.size}首 · ${totalMinutes}分钟",
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

            item {
                Text(
                    text = "Disc 1",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MeloXColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    fontFamily = MeloXLanTingProFontFamily
                )
            }

            itemsIndexed(albumTracks) { index, track ->
                AlbumTrackItem(
                    index = index + 1,
                    track = track,
                    isPlaying = currentTrack?.id == track.id,
                    onClick = { AudioPlayer.play("", track) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

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
                        text = "© 2024 杰威尔音乐",
                        fontSize = 12.sp,
                        color = MeloXColors.TextTertiary
                    )
                    Text(
                        text = "℗ 2024 杰威尔音乐有限公司",
                        fontSize = 12.sp,
                        color = MeloXColors.TextTertiary
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun AlbumTrackItem(
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
