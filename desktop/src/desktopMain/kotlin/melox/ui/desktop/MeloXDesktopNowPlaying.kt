package melox.ui.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.layout.MeloXDesktopPlayerGeometry
import melox.music.model.MusicTrack
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.glass.MeloXLiquidSlider
import melox.ui.screens.LyricsPage
import melox.ui.screens.MeloXArtworkImage

/** Desktop player geometry and concurrent page presentation from the pinned Swift tree. */
@Composable
internal fun MeloXDesktopNowPlaying(track: MusicTrack, onDismiss: () -> Unit, onActions: () -> Unit) {
    val state by AudioPlayer.state.collectAsState()
    val position by AudioPlayer.positionMs.collectAsState()
    val duration by AudioPlayer.durationMs.collectAsState()
    val volume by AudioPlayer.volumeLevel.collectAsState()
    var queuePage by remember { mutableStateOf(false) }
    val durationMs = if (MeloXSettingsRuntime.reduceMotion) 0 else 280
    BoxWithConstraints(Modifier.fillMaxSize().testTag("now-playing")) {
        val geometry = MeloXDesktopPlayerGeometry(maxWidth.value, maxHeight.value)
        val scale = geometry.scale
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            DesktopIconButton(MeloXSymbol.XMark, "player-collapse", desktopString("ui.common.close"), Color.White, onClick = onDismiss)
            Spacer(Modifier.weight(1f))
            MeloXLiquidSlider(volume, { PlaybackCommands.playerController()?.setVolume(it) }, valueRange = 0f..1f,
                modifier = Modifier.width(128.dp).height(36.dp).testTag("desktop-full-volume"), contentDescription = desktopString("ui.player.volume"))
        }
        Column(Modifier.offset(x = geometry.playerLeading.dp, y = geometry.chromeHeight.dp)
            .width(geometry.playerWidth.dp).height((maxHeight.value - geometry.chromeHeight).coerceAtLeast(0f).dp)
            .testTag("desktop-player-column")) {
            val artworkScale by animateFloatAsState(if (state.isPlaying || !MeloXSettingsRuntime.artworkMotionEnabled) 1f else .74f,
                tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 280), label = "desktop-artwork-playing")
            Box(Modifier.fillMaxWidth().padding(top = geometry.artworkTop.dp), contentAlignment = Alignment.Center) {
                MeloXArtworkImage(track.artworkUrl, Color.DarkGray, Modifier.size(geometry.artworkSize.dp)
                    .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                    .clip(RoundedCornerShape(12.dp)).testTag("desktop-player-artwork"))
            }
            Row(Modifier.fillMaxWidth().padding(top = geometry.metadataTop.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(track.artistText, track.album?.name.orEmpty()).filter(String::isNotBlank).joinToString(" — "),
                        Modifier.padding(top = (5 * scale).dp), color = Color.White.copy(alpha = .6f), fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DesktopIconButton(MeloXSymbol.Ellipsis, "player-actions", desktopString("ui.common.more"), Color.White, onClick = onActions)
            }
            Column(Modifier.fillMaxWidth().padding(top = (23 * scale).dp)) {
                DesktopSeekBar(Modifier.fillMaxWidth().height(20.dp), Color.White)
                Row(Modifier.fillMaxWidth()) {
                    Text(desktopTime(position), color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("−" + desktopTime((duration - position).coerceAtLeast(0)), color = Color.White.copy(alpha = .6f), fontSize = 12.sp)
                }
            }
            DesktopTransport(Modifier.fillMaxWidth().padding(top = (22 * scale).dp), Color.White, prominent = true)
        }
        Box(Modifier.offset(x = geometry.panelLeading.dp, y = geometry.lyricsTop.dp)
            .width((maxWidth.value - geometry.panelLeading - geometry.panelTrailing).coerceAtLeast(1f).dp)
            .height((maxHeight.value - geometry.lyricsTop).coerceAtLeast(1f).dp).testTag("desktop-player-panel")) {
            AnimatedContent(queuePage, transitionSpec = { fadeIn(tween(durationMs)) togetherWith fadeOut(tween(durationMs)) }, label = "desktop-player-page") { queue ->
                if (queue) DesktopQueueInspector(true, prominent = true) else DesktopLyrics(track, Color.White, focusOffset = geometry.lyricsFocusOffset)
            }
        }
        Row(Modifier.align(Alignment.BottomEnd).padding(18.dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))) {
            DesktopIconButton(MeloXSymbol.Quote, "desktop-full-lyrics", desktopString("ui.common.lyrics"), if (!queuePage) Color.White else Color.White.copy(alpha = .55f)) { queuePage = false }
            DesktopIconButton(MeloXSymbol.ListBullet, "desktop-full-queue", desktopString("ui.player.queue"), if (queuePage) Color.White else Color.White.copy(alpha = .55f)) { queuePage = true }
        }
    }
}

private fun desktopTime(ms: Long) = "${ms.coerceAtLeast(0) / 60000}:${(ms.coerceAtLeast(0) / 1000 % 60).toString().padStart(2, '0')}"
