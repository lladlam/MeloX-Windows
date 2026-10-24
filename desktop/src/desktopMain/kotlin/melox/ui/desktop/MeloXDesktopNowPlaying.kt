package melox.ui.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.capsule.ContinuousRoundedRectangle
import melox.layout.MeloXDesktopPlayerGeometry
import melox.music.model.MusicTrack
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopTextFontFamily
import melox.ui.navigation.meloXPlayerPullDown

private enum class DesktopPlayerPage { Artwork, Lyrics, Queue }

/** Source geometry over the existing player, artwork loader, timed lyrics and live queue. */
@Composable
internal fun MeloXDesktopNowPlaying(track: MusicTrack, onDismiss: () -> Unit, onActions: () -> Unit, actionsVisible: Boolean = false) {
    val state by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val volume by melox.playback.MeloXPlaybackOutput.volumeLevel.collectAsState()
    val rememberPage = MeloXSettingsPreferences.boolean("playback_remember_page", true)
    var page by remember(rememberPage) {
        mutableStateOf(if (rememberPage) DesktopPlayerPage.entries.firstOrNull {
            it.name == MeloXSettingsPreferences.string("desktop_playback_last_page", "Lyrics")
        } ?: DesktopPlayerPage.Lyrics else DesktopPlayerPage.Lyrics)
    }
    fun select(destination: DesktopPlayerPage) {
        page = if (page == destination) DesktopPlayerPage.Artwork else destination
        if (rememberPage) MeloXSettingsPreferences.setString("desktop_playback_last_page", page.name)
    }
    val motionMs = if (MeloXSettingsRuntime.reduceMotion) 0 else 300
    CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = MeloXDesktopTextFontFamily, color = Color.White)) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("now-playing")
        .meloXPlayerPullDown(page == DesktopPlayerPage.Artwork, onDismiss)) {
        val geometry = MeloXDesktopPlayerGeometry(maxWidth.value, maxHeight.value)
        val scale = geometry.scale
        val playerX by animateFloatAsState(if (page == DesktopPlayerPage.Artwork) (maxWidth.value - geometry.playerWidth) / 2 else geometry.playerLeading,
            tween(motionMs), label = "desktop-player-column-x")
        Column(Modifier.offset(x = playerX.dp, y = geometry.chromeHeight.dp)
            .width(geometry.playerWidth.dp).height((maxHeight.value - geometry.chromeHeight).coerceAtLeast(0f).dp)
            .testTag("desktop-player-column")) {
            // artworkSize is the PAUSED layout slot; SwiftUI expands it around
            // its centre by 1/.74 during playback without moving the metadata.
            val expanded = state.isPlaying || !MeloXSettingsRuntime.artworkMotionEnabled
            val artworkScale by animateFloatAsState(if (expanded) 1f / .74f else 1f,
                tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 480), label = "desktop-artwork-playing")
            val shape = remember { ContinuousRoundedRectangle(11.dp) }
            Box(Modifier.fillMaxWidth().padding(top = geometry.artworkTop.dp), contentAlignment = Alignment.Center) {
                MeloXArtworkImage(track.artworkUrl, Color.DarkGray, Modifier.size(geometry.artworkSize.dp)
                    .graphicsLayer { scaleX = artworkScale; scaleY = artworkScale }
                    .shadow(if (state.isPlaying) 26.dp else 14.dp, shape,
                        ambientColor = Color.Black.copy(alpha = if (state.isPlaying) .34f else .18f),
                        spotColor = Color.Black.copy(alpha = if (state.isPlaying) .34f else .18f))
                    .clip(shape).testTag("desktop-player-artwork"), contentDescription = track.title)
            }
            Row(Modifier.fillMaxWidth().padding(top = geometry.metadataTop.dp).testTag("desktop-player-metadata"),
                verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy((10 * scale).dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy((5 * scale).dp)) {
                    Text(track.title, fontSize = (18 * scale).sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOf(track.artistText, track.album?.name.orEmpty()).filter(String::isNotBlank)
                        .joinToString(desktopString("ui.common.title_detail_separator")),
                        color = Color.White.copy(alpha = .6f), fontSize = (14 * scale).sp,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width((6 * scale).dp))
                DesktopPlayerFavorite(track, scale, actionsVisible)
                DesktopPlayerCircleButton(MeloXSymbol.Ellipsis, "player-actions", desktopString("ui.common.more"), scale, onClick = onActions)
            }
            Box(Modifier.fillMaxWidth().padding(top = (23 * scale).dp)) { DesktopPlayerProgress(scale) }
            DesktopTransport(Modifier.fillMaxWidth().padding(top = (22 * scale).dp).testTag("desktop-player-transport"),
                Color.White, prominent = true, scale = scale)
        }
        Box(Modifier.offset(x = geometry.panelLeading.dp, y = geometry.chromeHeight.dp)
            .width((maxWidth.value - geometry.panelLeading - geometry.panelTrailing).coerceAtLeast(1f).dp)
            .height((maxHeight.value - geometry.chromeHeight).coerceAtLeast(1f).dp).testTag("desktop-player-panel")) {
            AnimatedContent(page, transitionSpec = { fadeIn(tween(motionMs)) togetherWith fadeOut(tween(motionMs)) }, label = "desktop-player-page") { destination ->
                when (destination) {
                    DesktopPlayerPage.Queue -> DesktopQueueInspector(true, prominent = true)
                    DesktopPlayerPage.Lyrics -> Box(Modifier.padding(top = 40.dp)) {
                        DesktopLyrics(track, Color.White, focusOffset = geometry.lyricsFocusOffset)
                    }
                    DesktopPlayerPage.Artwork -> Unit
                }
            }
        }
        // Window accessories remain above expanded artwork and content hit targets.
        Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(Color.White.copy(alpha = .08f)).border(.75.dp, Color.White.copy(alpha = .18f), CircleShape)) {
                DesktopIconButton(MeloXSymbol.XMark, "player-collapse", desktopString("ui.common.close"), Color.White, onClick = onDismiss)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.height(36.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f))
                .border(.75.dp, Color.White.copy(alpha = .18f), CircleShape).padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                DesktopPlayerVolume(volume)
                DesktopVolumeMute(volume)
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).size(92.dp, 56.dp).testTag("desktop-player-page-switcher"), contentAlignment = Alignment.Center) {
            Box(Modifier.size(72.dp, 36.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f))
                .border(.75.dp, Color.White.copy(alpha = .32f), CircleShape))
            val selectionX by animateFloatAsState(if (page == DesktopPlayerPage.Queue) 18f else -18f, tween(motionMs), label = "desktop-page-selection")
            if (page != DesktopPlayerPage.Artwork) Box(Modifier.offset(x = selectionX.dp).size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = .88f)))
            for ((destination, symbol, tag, label, x) in listOf(
                PageButton(DesktopPlayerPage.Lyrics, MeloXSymbol.Quote, "desktop-full-lyrics", "ui.desktop.commands.show_lyrics", -18),
                PageButton(DesktopPlayerPage.Queue, MeloXSymbol.ListBullet, "desktop-full-queue", "ui.desktop.commands.show_queue", 18))) {
                Box(Modifier.offset(x = x.dp).size(36.dp, 56.dp).testTag(tag).semantics {
                    selected = page == destination; contentDescription = desktopString(label)
                }.clickable(interactionSource = null, indication = null, role = Role.Button) { select(destination) }, contentAlignment = Alignment.Center) {
                    MeloXSymbolIcon(symbol, size = 15, weight = FontWeight.SemiBold,
                        color = if (page == destination) Color.Black.copy(alpha = .78f) else Color.White)
                }
            }
        }
    }
    }
}

private data class PageButton(val page: DesktopPlayerPage, val symbol: MeloXSymbol, val tag: String, val label: String, val x: Int)

@Composable
internal fun DesktopPlayerCircleButton(symbol: MeloXSymbol, tag: String, label: String, scale: Float,
    enabled: Boolean = true, state: String? = null, onClick: () -> Unit) {
    Box(Modifier.size((28 * scale).dp).clip(CircleShape).background(Color.White.copy(alpha = .12f))
        .border(.5.dp, Color.White.copy(alpha = .10f), CircleShape).testTag(tag).semantics {
            contentDescription = label
            state?.let { stateDescription = it }
        }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = (13 * scale).toInt(), weight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = if (enabled) 1f else .3f))
    }
}

@Composable
private fun DesktopVolumeMute(volume: Float) {
    val dlna by melox.dlna.DlnaSession.state.collectAsState()
    val enabled = dlna.device == null || dlna.device?.rendering != null
    var unmuted by remember { mutableFloatStateOf(volume.takeIf { it > .001f } ?: 1f) }
    LaunchedEffect(volume) { if (volume > .001f) unmuted = volume }
    Box(Modifier.size(30.dp).testTag("desktop-full-mute").semantics { contentDescription = desktopString("ui.desktop.player.toggle_mute") }
        .clickable(enabled = enabled, role = Role.Button) { PlaybackCommands.playerController()?.setVolume(if (volume <= .001f) unmuted.coerceAtLeast(.2f) else 0f) }, contentAlignment = Alignment.Center) {
        val symbol = when { volume <= .001f -> MeloXSymbol.VolumeMuted; volume < .35f -> MeloXSymbol.VolumeLow
            volume < .7f -> MeloXSymbol.Volume; else -> MeloXSymbol.VolumeHigh }
        MeloXSymbolIcon(symbol, size = 15, weight = FontWeight.SemiBold, color = Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}
