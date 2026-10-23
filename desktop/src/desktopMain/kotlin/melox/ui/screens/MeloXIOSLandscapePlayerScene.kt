package melox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicTrack
import melox.playback.MeloXDesktopPlayer
import melox.playback.MeloXPlaybackModeRuntime
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon

/** Source landscape scene: 43/57 panes, 24dp gap, persistent cover owned by the host. */
@Composable
internal fun MeloXIOSLandscapePlayerScene(
    track: MusicTrack, sourceColor: Color, isPlaying: Boolean, progress: Float, durationMs: Long,
    page: Int, player: MeloXDesktopPlayer?, showsLyricsControls: Boolean,
    onPage: (Int) -> Unit, onDismiss: () -> Unit, onShowActions: () -> Unit,
    onShowQuality: () -> Unit, onShowOutput: () -> Unit, onInterfaceInteraction: () -> Unit,
    onInterfaceVisibilityChange: (Boolean) -> Unit, grabberDragModifier: Modifier,
    showSkyline: Boolean = false, onShowSkyline: () -> Unit = {}, onHideSkyline: () -> Unit = {},
) {
    val mix by melox.playback.MeloXAutoMixTransitionRuntime.state.collectAsState()
    val displayedTrack = if (mix.active || mix.handedOff) mix.incoming ?: track else track
    if (showSkyline && page == 1) {
        Box(Modifier.fillMaxSize()) {
            MeloXSkylineLyricsPanel(track, Modifier.fillMaxSize(), onInterfaceInteraction)
            Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 20.dp).testTag("player-skyline-exit")
                .clip(CircleShape).background(Color.Black.copy(alpha = .24f)).clickable(onClick = onHideSkyline)
                .padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("退出天际歌词", color = Color.White.copy(alpha = .86f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize().testTag("player-landscape").padding(horizontal = 20.dp, vertical = 2.dp)) {
        val contentWidth = when {
            maxWidth >= 840.dp -> 1180.dp
            maxWidth >= 600.dp -> 920.dp
            else -> 600.dp
        }
        Column(Modifier.fillMaxSize()) {
            Grabber(onDismiss, grabberDragModifier)
            Row(Modifier.fillMaxWidth().widthIn(max = contentWidth).weight(1f)) {
                Spacer(Modifier.weight(.43f).fillMaxHeight())
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(.57f).fillMaxHeight()) {
                    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            if (MeloXPlaybackModeRuntime.heartModeActive) {
                                Text("心动模式", color = Color(0xFFFF7BA5), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(displayedTrack.title, color = Color.White, fontSize = 19.sp, lineHeight = 23.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(displayedTrack.artistText, color = Color.White.copy(alpha = .58f), fontSize = 15.sp,
                                lineHeight = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (page == 1 && MeloXSettingsRuntime.skylineEnabled) {
                            Box(Modifier.size(40.dp).testTag("player-skyline-open").clip(CircleShape).clickable(onClick = onShowSkyline), Alignment.Center) {
                                Text("↗", fontSize = 19.sp, color = Color.White.copy(alpha = .84f))
                            }
                        }
                        Box(Modifier.size(40.dp).testTag("player-actions").clip(CircleShape).clickable(onClick = onShowActions), Alignment.Center) {
                            MeloXSymbolIcon(MeloXSymbol.Ellipsis, size = 22, color = Color.White)
                        }
                    }
                    AnimatedContent(page, modifier = Modifier.weight(1f).fillMaxWidth(),
                        transitionSpec = { fadeIn(MeloXMotion.contentEnterTween()) togetherWith fadeOut(MeloXMotion.contentExitTween()) },
                        label = "landscape-player-page") { destination ->
                        when (destination) {
                            0 -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                                MeloXPlayerCoreControls(track, isPlaying, progress, durationMs, page, player,
                                    onPage, onShowQuality, onShowOutput, onInterfaceInteraction)
                            }
                            1 -> Box(Modifier.fillMaxSize()) {
                                LyricsPage(track, sourceColor, interactive = page == 1,
                                    modifier = Modifier.padding(bottom = 50.dp),
                                    interfaceHidden = !showsLyricsControls,
                                    onInterfaceInteraction = onInterfaceInteraction,
                                    onInterfaceVisibilityChange = onInterfaceVisibilityChange)
                                androidx.compose.animation.AnimatedVisibility(showsLyricsControls, modifier = Modifier.align(Alignment.BottomCenter),
                                    enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                                    Box(Modifier.testTag("player-controls")) { PageSelectorSection(player, page, onShowOutput, onPage) }
                                }
                            }
                            2 -> Box(Modifier.fillMaxSize()) {
                                QueuePage(sourceColor, interactive = page == 2, modifier = Modifier.padding(bottom = 50.dp))
                                Box(Modifier.align(Alignment.BottomCenter).testTag("player-controls")) {
                                    PageSelectorSection(player, page, onShowOutput, onPage)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeloXPlayerCoreControls(
    track: MusicTrack, isPlaying: Boolean, progress: Float, durationMs: Long, page: Int,
    player: MeloXDesktopPlayer?, onPage: (Int) -> Unit, onShowQuality: () -> Unit,
    onShowOutput: () -> Unit, onInterfaceInteraction: () -> Unit, modifier: Modifier = Modifier,
) {
    val mix by melox.playback.MeloXAutoMixTransitionRuntime.state.collectAsState()
    val incoming = mix.incoming.takeIf { mix.active }
    val displayedDuration = incoming?.durationMs?.takeIf { it > 0 } ?: durationMs
    val displayedProgress = if (incoming != null && displayedDuration > 0) {
        (mix.incomingPositionMs.toFloat() / displayedDuration).coerceIn(0f, 1f)
    } else progress
    Column(modifier.fillMaxWidth().height(279.dp).testTag("player-controls")) {
        key(track.id) {
            ProgressSection(displayedProgress, displayedDuration,
                qualityChip = if (MeloXSettingsRuntime.showPlayerQualityTip) ({ MeloXPlayerQualityChip(track, onShowQuality) }) else null,
                seekEnabled = !mix.active,
            ) { player?.seekTo((it * durationMs).toLong()) }
        }
        Spacer(Modifier.height(19.dp))
        TransportSection(isPlaying, { player?.previous() }, { player?.togglePlay(); onInterfaceInteraction() }, { player?.next() })
        Spacer(Modifier.height(31.dp))
        VolumeSection(player)
        Spacer(Modifier.height(3.dp))
        PageSelectorSection(player, page, onShowOutput) { onInterfaceInteraction(); onPage(it) }
    }
}
