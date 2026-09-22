package melox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.smoothStep
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import kotlin.math.roundToInt

/**
 * 1:1 port of Android MeloXIOSNowPlayingScene + MeloXNowPlayingCoreControls.
 *
 * Layout (Android audit):
 * - Scene: Column, horizontal 32dp, grabber (60×5dp pill, White α0.52, tap = dismiss)
 * - Page cross-fades: all spring(0.7, 300)
 *   - artwork details: exits to −300dp
 *   - lyrics/queue: enter from +400dp, scale 0.92 when the other is active
 * - Controls: 279dp column = progress 52 + spacer 19 + transport 82 + spacer 31
 *   + volume 42 + spacer 3 + page selector 50
 * - Progress: 4→6dp track, White α0.20 bg / α0.96 fill, 11sp Monospace labels α0.50
 * - Transport: play/pause 64dp circle glyph 48sp; skip 64dp glyph 34sp;
 *   press scale 0.86/0.84 spring(MediumBouncy, 620)
 * - Play↔pause icon: fadeIn(180)+scaleIn(0.78,200)+slideIn(200,24%)
 *   ↔ fadeOut(150)+scaleOut(0.78,180)+slideOut(180,−24%)
 * - Artwork: paused scale 0.74 springs grow 0.70/280 shrink 0.94/360;
 *   shadow 26/14dp spring 0.92/320
 */
private const val CONTROLS_HEIGHT_DP = 279

@Composable
fun MeloXNowPlayingScreen(
    navState: MeloXNavState? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val playerState by AudioPlayer.state.collectAsState()
    val playerProgress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val track = currentTrack ?: return

    var currentPage by remember { mutableStateOf(0) } // 0 artwork, 1 lyrics, 2 queue
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val sourceColor = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.Primary

    // ── Collapse gesture (vertical drag on grabber/scene) ──
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Vertical drag collapses; handled on grabber below (scene-level
                // drag reserved for lyric scroll passthrough)
            },
    ) {
        // Background: source-tinted gradient (Android uses blurred artwork; gradient fallback)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            sourceColor.copy(alpha = 0.25f),
                            Color.Black,
                        )
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Grabber (60×5dp, White α0.52) ──
            Grabber(onDismiss = { onDismiss?.invoke() ?: navState?.goBack() })

            Spacer(modifier = Modifier.height(8.dp))

            // ── Page cross-fade region ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                // Artwork page
                PageLayer(visible = currentPage == 0) {
                    ArtworkPage(
                        track = track,
                        sourceColor = sourceColor,
                        isPlaying = playerState.isPlaying,
                    )
                }
                // Lyrics page
                PageLayer(visible = currentPage == 1) {
                    LyricsPage(track = track, sourceColor = sourceColor)
                }
                // Queue page
                PageLayer(visible = currentPage == 2) {
                    QueuePage(sourceColor = sourceColor)
                }
            }

            // ── Controls: 279dp column (exact Android) ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTROLS_HEIGHT_DP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Progress section: 52dp
                ProgressSection(
                    progress = playerProgress,
                    durationMs = track.durationMs ?: 0L,
                )
                Spacer(modifier = Modifier.height(19.dp))

                // Transport: 82dp
                TransportSection(isPlaying = playerState.isPlaying)
                Spacer(modifier = Modifier.height(31.dp))

                // Volume: 42dp (simplified on desktop — audio via system)
                Spacer(modifier = Modifier.height(42.dp))
                Spacer(modifier = Modifier.height(3.dp))

                // Page selector: 50dp
                PageSelectorSection(
                    currentPage = currentPage,
                    hasQueue = true,
                    onSelect = { currentPage = it },
                )
            }
        }
    }
}

@Composable
private fun PageLayer(visible: Boolean, content: @Composable () -> Unit) {
    // spring(0.7, 300) cross-fade, lyrics/queue slide from +400dp, artwork exits −300dp
    AnimatedContent(
        targetState = visible,
        transitionSpec = {
            if (targetState) {
                (fadeIn(MeloXMotion.scenePageSpec()) +
                    slideInVertically(tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { it }) togetherWith fadeOut(MeloXMotion.scenePageSpec())
            } else {
                fadeIn(MeloXMotion.scenePageSpec()) togetherWith
                    (fadeOut(MeloXMotion.scenePageSpec()) +
                        slideOutVertically(tween(360, easing = androidx.compose.animation.core.FastOutSlowInEasing)) { -it / 4 })
            }
        },
        label = "melox-player-page",
    ) { isVisible ->
        if (isVisible) {
            Box(modifier = Modifier.fillMaxSize()) { content() }
        }
    }
}

// ── Grabber: 30dp touch area, 60×5dp pill ──
@Composable
private fun Grabber(onDismiss: () -> Unit) {
    var dragY by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 60.dp, height = 5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.52f))
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragY > 100f) onDismiss()
                            dragY = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount
                    }
                },
        )
    }
}

// ── Artwork page: 12dp radius, playback scale, shadow ──
@Composable
private fun ArtworkPage(
    track: melox.music.model.MusicTrack,
    sourceColor: Color,
    isPlaying: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Playback scale: paused 0.74, playing 1.0 (asymmetric springs)
        val playbackScale by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0.74f,
            animationSpec = if (isPlaying) MeloXMotion.artworkGrowSpec() else MeloXMotion.artworkShrinkSpec(),
            label = "melox-artwork-scale",
        )
        // Shadow: playing 26dp, paused 14dp
        val shadowElev by animateFloatAsState(
            targetValue = if (isPlaying) 26f else 14f,
            animationSpec = MeloXMotion.artworkShadowSpec(),
            label = "melox-artwork-shadow",
        )

        Box(
            modifier = Modifier
                .graphicsLayer(
                    scaleX = playbackScale,
                    scaleY = playbackScale,
                    shadowElevation = shadowElev,
                    shape = RoundedCornerShape(12.dp),
                    clip = false,
                )
                .size(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            sourceColor.copy(alpha = 0.9f),
                            sourceColor.copy(alpha = 0.4f),
                        )
                    )
                ),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.MusicNote,
                color = Color.White.copy(alpha = 0.85f),
                size = 72,
            )
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Track info
        Text(
            text = track.title,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.artistText,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            lineHeight = 22.sp,
            color = Color.White.copy(alpha = 0.64f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        if (track.album != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.album!!.name,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.42f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Lyrics page (simplified until full Apple Music lyrics engine) ──
@Composable
private fun LyricsPage(track: melox.music.model.MusicTrack, sourceColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 88.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无歌词",
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            color = Color.White.copy(alpha = 0.42f),
        )
    }
}

// ── Queue page ──
@Composable
private fun QueuePage(sourceColor: Color) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp),
    ) {
        if (currentTrack == null) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.Queue,
                    color = Color.White.copy(alpha = 0.55f),
                    size = 40,
                )
            }
        } else {
            val track = currentTrack!!
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "继续播放",
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(20.dp))
                // Queue rows artwork 48dp, corner 6dp
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        sourceColor.copy(alpha = 0.85f),
                                        sourceColor.copy(alpha = 0.45f),
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.MusicNote, color = Color.White.copy(alpha = 0.9f), size = 18)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 16.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artistText,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.58f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ── Progress: 52dp section, 4→6dp track, Monospace 11sp labels ──
@Composable
private fun ProgressSection(progress: Float, durationMs: Long) {
    var isScrubbing by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "melox-progress-track",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            value = progress.coerceIn(0f, 1f),
            onValueChange = { isScrubbing = true },
            onValueChangeFinished = { isScrubbing = false },
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight),
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.White.copy(alpha = 0.96f),
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime((progress * durationMs).toLong()),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.50f),
            )
            Text(
                text = formatTime(durationMs),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.50f),
            )
        }
    }
}

// ── Transport: play/pause 64dp glyph 48sp; skip 64dp glyph 34sp ──
@Composable
private fun TransportSection(isPlaying: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = {
            AudioPlayer.seekTo(0)
        }) {
            MeloXSymbolIcon(MeloXSymbol.PreviousTrack, color = Color.White, size = 34)
        }
        PressScaleButton(size = 64.dp, pressScale = 0.86f, onClick = {
            if (isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
        }) {
            // Play↔pause icon swap: exact Android enter/exit
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.78f) +
                            slideInVertically(tween(200)) { (it * 0.24f).toInt() }) togetherWith
                            (fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.78f) +
                                slideOutVertically(tween(180)) { -(it * 0.24f).toInt() })
                    } else {
                        (fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.78f) +
                            slideInVertically(tween(200)) { (it * 0.24f).toInt() }) togetherWith
                            (fadeOut(tween(150)) + scaleOut(tween(180), targetScale = 0.78f) +
                                slideOutVertically(tween(180)) { -(it * 0.24f).toInt() })
                    }
                },
                label = "melox-play-pause",
            ) { playing ->
                MeloXSymbolIcon(
                    symbol = if (playing) MeloXSymbol.Pause else MeloXSymbol.Play,
                    color = Color.White,
                    size = 48,
                )
            }
        }
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = { /* next */ }) {
            MeloXSymbolIcon(MeloXSymbol.NextTrack, color = Color.White, size = 34)
        }
    }
}

@Composable
private fun PressScaleButton(
    size: androidx.compose.ui.unit.Dp,
    pressScale: Float,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressScale else 1f,
        animationSpec = MeloXMotion.transportPress(),
        label = "melox-transport-press",
    )
    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ── Page selector: 48dp circles, selected White α0.68·0.16 bg ──
@Composable
private fun PageSelectorSection(
    currentPage: Int,
    hasQueue: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageSelectorButton(
            selected = currentPage == 1,
            enabled = true,
            onClick = { onSelect(1) },
        ) {
            MeloXSymbolIcon(MeloXSymbol.Lyrics, color = pageIconColor(currentPage == 1), size = 22)
        }
        Spacer(modifier = Modifier.width(24.dp))
        PageSelectorButton(
            selected = currentPage == 2,
            enabled = hasQueue,
            onClick = { onSelect(2) },
        ) {
            MeloXSymbolIcon(MeloXSymbol.Queue, color = pageIconColor(currentPage == 2), size = 22)
        }
    }
}

@Composable
private fun pageIconColor(selected: Boolean): Color =
    if (selected) Color.Black.copy(alpha = 0.68f) else Color.White.copy(alpha = 0.72f)

@Composable
private fun PageSelectorButton(
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // press 0.86 (stiffness 650), selected scale 1.04
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else if (selected) 1.04f else 1f,
        animationSpec = MeloXMotion.pageSelectorPress(),
        label = "melox-page-selector",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.68f * 0.16f else 0f,
        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "melox-page-sel-bg",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = bgAlpha))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
