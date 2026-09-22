package melox.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.smoothStep
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.rememberMeloXLiquidInteraction
import melox.ui.glass.meloXLiquidContentTransform
import melox.ui.glass.rememberMeloXLiquidInteraction
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import kotlin.math.abs

/**
 * 1:1 port of Android MeloXIOSMiniPlayer (ui/player/MeloXIOSMiniPlayer.kt).
 *
 * Specs:
 * - Outer padding vertical 3dp; pill height 52dp, capsule
 * - Glass: liquid button blur 2dp, lens 28dp, refraction 16dp,
 *   surfaceColor White@0.06
 * - Artwork lerp(40→30dp) by compactProgress, corner 6dp
 * - Inner Row padding h: lerp(12,8,compact), v: lerp(6,3,compact),
 *   spacing lerp(10,8,compact)
 * - Title 14sp/17 SemiBold; artist 12sp/15 α0.64, height 15→0dp (ss .04/.72)
 * - compactArtistAlpha 1−ss(.04,.52); compactNextAlpha 1−ss(.04,.50)
 * - controlStageWidth lerp(72→36, ss(.08,.84))
 * - chrome alpha 1−ss(expansion .10,.58); surface alpha 1−ss(.02,.48)
 * - Swipe: ±28px threshold, animates full width spring(.68,360) then
 *   next/previousFromMiniPlayer, bounce back spring(.72,430), 1.5s hold
 * - DancingBars 15×18dp, 4 bars from reactive energy/beat/downbeat
 * - Play/pause button 36dp circle, icon 22sp, baseAlpha .94/0.26
 */
@Composable
fun MeloXMiniPlayer(
    compactProgress: Float = 0f,
    onExpand: () -> Unit = {},
) {
    val playerState by AudioPlayer.state.collectAsState()
    val playerProgress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val track = currentTrack ?: return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeWidth = with(density) { 360.dp.toPx() }
    val contentOffset = remember { Animatable(0f) }
    val liquidInteraction = rememberMeloXLiquidInteraction()
    var accumulatedDrag by remember { mutableStateOf(0f) }
    var pendingDirection by remember { mutableStateOf(0) }
    var pendingOutgoingMediaId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(track.id, pendingDirection) {
        if (pendingDirection != 0 && track.id.source.storageValue != pendingOutgoingMediaId) {
            contentOffset.snapTo(0f)
            pendingDirection = 0
            pendingOutgoingMediaId = null
        } else if (pendingDirection == 0) {
            contentOffset.snapTo(0f)
        }
    }

    LaunchedEffect(pendingOutgoingMediaId) {
        if (pendingOutgoingMediaId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
        pendingDirection = 0
        pendingOutgoingMediaId = null
    }

    // Desktop has no player-transition animatedVisibilityScope here; the
    // expansion fades are driven by compactProgress only.
    val miniChromeAlpha = 1f - smoothStep(compactProgress, 0.10f, 0.58f)
    val miniSurfaceAlpha = 1f - smoothStep(compactProgress, 0.02f, 0.48f)

    val compact = compactProgress.coerceIn(0f, 1f)
    val artworkSize = lerpDpF(40.dp, 30.dp, compact)
    val artworkRadius = 6.dp
    val compactArtistAlpha = 1f - smoothStep(compact, 0.04f, 0.52f)
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    val controlStageWidth = lerpDpF(72.dp, 36.dp, smoothStep(compact, 0.08f, 0.84f))
    val artistHeight = lerpDpF(15.dp, 0.dp, smoothStep(compact, 0.04f, 0.72f))
    val dragDirection = when {
        contentOffset.value < 0f -> -1
        contentOffset.value > 0f -> 1
        else -> 0
    }
    val dragProgress = (abs(contentOffset.value) / swipeWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val adjacentAlpha = smoothStep(dragProgress, 0.15f, 0.85f)

    val hasNext = false // desktop AudioPlayer has no queue API yet

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            // Glass surface (kept out of shared-bounds node on Android; here
            // simply the under-layer).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniSurfaceAlpha }
                    .meloXLiquidButton(
                        shape = RoundedCornerShape(50),
                        blurRadius = 2.dp,
                        lensRadius = 28.dp,
                        refractionHeight = 16.dp,
                        surfaceColor = Color.White.copy(alpha = 0.06f),
                        interaction = liquidInteraction,
                    ),
            )
        }

        // Chrome rides the same press/drag transform.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .meloXLiquidContentTransform(liquidInteraction),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(50))
                    .padding(
                        horizontal = lerpDpF(12.dp, 8.dp, compact),
                        vertical = lerpDpF(6.dp, 3.dp, compact),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(track.id) {
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.stop() }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                    scope.launch {
                                        contentOffset.snapTo(
                                            (contentOffset.value + dragAmount).coerceIn(-swipeWidth * .86f, swipeWidth * .86f),
                                        )
                                    }
                                },
                                onDragEnd = {
                                    val direction = when {
                                        accumulatedDrag <= -28f -> -1
                                        accumulatedDrag >= 28f -> 1
                                        else -> 0
                                    }
                                    if (direction != 0) {
                                        scope.launch {
                                            contentOffset.animateTo(direction * swipeWidth, spring(dampingRatio = .68f, stiffness = 360f))
                                            pendingDirection = direction
                                            pendingOutgoingMediaId = track.id.source.storageValue
                                            // Desktop AudioPlayer has no queue; bounce back after swipe animation
                                        }
                                    } else {
                                        scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                    }
                                    accumulatedDrag = 0f
                                },
                                onDragCancel = {
                                    accumulatedDrag = 0f
                                    scope.launch { contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f)) }
                                },
                            )
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExpand,
                        ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().graphicsLayer { translationX = contentOffset.value },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(lerpDpF(10.dp, 8.dp, compact)),
                    ) {
                        MiniArtwork(artworkSize)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = miniChromeAlpha },
                        ) {
                            Text(
                                text = track.title.ifBlank { "正在播放" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                lineHeight = 17.sp,
                                softWrap = false,
                                fontWeight = FontWeight.SemiBold,
                                color = MeloXColors.OnSurface,
                            )
                            Box(
                                modifier = Modifier
                                    .height(artistHeight)
                                    .graphicsLayer { alpha = compactArtistAlpha },
                            ) {
                                if (artistHeight > 0.dp) {
                                    Text(
                                        text = track.artistText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        softWrap = false,
                                        color = MeloXColors.OnSurface.copy(alpha = 0.64f),
                                    )
                                }
                            }
                        }
                    }
                }

                MiniDancingBars(
                    isPlaying = playerState.isPlaying,
                    color = MeloXColors.OnSurface.copy(alpha = miniChromeAlpha * 0.72f),
                    modifier = Modifier
                        .width(15.dp)
                        .height(18.dp)
                        .graphicsLayer { alpha = miniChromeAlpha },
                )

                Box(
                    modifier = Modifier
                        .width(controlStageWidth)
                        .height(36.dp)
                        .zIndex(8f),
                ) {
                    MiniVectorButton(
                        kind = if (playerState.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                        enabled = true,
                        onClick = {
                            if (playerState.isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
                        },
                        modifier = Modifier
                            .align(if (compact > 0.55f) Alignment.Center else Alignment.CenterStart)
                            .zIndex(10f),
                        visualAlpha = miniChromeAlpha,
                    )
                    if (compactNextAlpha > 0.05f) {
                        MiniVectorButton(
                            kind = MiniGlyph.Forward,
                            enabled = hasNext,
                            onClick = { },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .zIndex(9f),
                            visualAlpha = miniChromeAlpha * compactNextAlpha,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // Desktop audio-reactive sampling is unavailable; use the static
    // energy pattern the Android fallback uses when sample is Idle.
    val energy = if (isPlaying) 0.6f else 0.10f
    val bars = listOf(
        energy * 0.58f,
        energy * 0.86f,
        energy * 0.46f,
        energy * 0.72f,
    )
    // Animate a subtle phase so bars dance while playing.
    val phase = rememberInfinitePhase(isPlaying)
    Canvas(modifier) {
        val gap = size.width * .12f
        val barWidth = (size.width - gap * 3f) / 4f
        bars.forEachIndexed { index, heightFraction ->
            val wobble = if (isPlaying) {
                0.85f + 0.3f * kotlin.math.sin(phase + index * 1.1f)
            } else 1f
            val barHeight = size.height * (heightFraction * wobble).coerceIn(.12f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun rememberInfinitePhase(isPlaying: Boolean): Float {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "mini-bars")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * Math.PI).toFloat(),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "mini-bars-phase",
    )
    return if (isPlaying) phase else 0f
}

private enum class MiniGlyph { Play, Pause, Forward }

@Composable
private fun MiniVectorButton(
    kind: MiniGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualAlpha: Float = 1f,
) {
    val baseAlpha = if (enabled) 0.94f else 0.26f
    val drawAlpha = visualAlpha.coerceIn(0f, 1f)
    val color = MeloXColors.OnSurface.copy(alpha = baseAlpha)
    Box(
        modifier = modifier
            .graphicsLayer { alpha = drawAlpha }
            .size(36.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled && drawAlpha > 0.05f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = when (kind) {
                MiniGlyph.Play -> MeloXSymbol.Play
                MiniGlyph.Pause -> MeloXSymbol.Pause
                MiniGlyph.Forward -> MeloXSymbol.NextTrack
            },
            size = 22,
            color = color,
        )
    }
}

// Artwork: size animated 40→30dp, corner 6dp, source-tinted gradient
@Composable
private fun MiniArtwork(size: androidx.compose.ui.unit.Dp) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val sourceColor = MeloXColors.sourceColors[currentTrack?.id?.source?.storageValue] ?: MeloXColors.Primary
    Box(
        modifier = Modifier
            .size(size)
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
        MeloXSymbolIcon(
            symbol = MeloXSymbol.MusicNote,
            color = Color.White.copy(alpha = 0.9f),
            size = (size.value * 0.45f).toInt().coerceAtLeast(10),
        )
    }
}

private fun lerpDpF(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, t: Float): androidx.compose.ui.unit.Dp =
    androidx.compose.ui.unit.Dp(start.value + (end.value - start.value) * t)
