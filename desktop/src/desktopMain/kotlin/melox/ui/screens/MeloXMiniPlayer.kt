package melox.ui.screens

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import melox.playback.MeloXDesktopPlayer
import melox.ui.foundation.MeloXMotion
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.smoothStep
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.meloXLiquidContentTransform
import melox.ui.glass.rememberMeloXLiquidInteraction
import melox.ui.glass.meloXLiquidBottomBar
import melox.ui.glass.bottomGlassSurfaceColor
import melox.ui.glass.bottomLiquidGlassTint
import melox.ui.glass.bottomBarTone
import melox.ui.foundation.MeloXChromeTokens
import melox.settings.MeloXSettingsRuntime
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import kotlin.math.abs

private fun livePlayer(): MeloXDesktopPlayer? =
    PlaybackCommands.playerController()

/** Android main 94e17c9: fixed 48dp, persistent artist, content-sized control row. */
@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun MeloXMiniPlayer(
    compactProgress: Float = 0f,
    onExpand: () -> Unit = {},
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val player = livePlayer()
    val playerState by AudioPlayer.state.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val queue by PlaybackCommands.queue.collectAsState()
    val repeatMode by player?.repeatMode?.collectAsState() ?: remember { mutableIntStateOf(PlaybackCommands.REPEAT_OFF) }
    val track = currentTrack ?: return

    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var swipeWidth by remember { mutableFloatStateOf(with(density) { 360.dp.toPx() }) }
    val contentOffset = remember { Animatable(0f) }
    val liquidInteraction = rememberMeloXLiquidInteraction()
    var accumulatedDrag by remember { mutableStateOf(0f) }
    var pendingDirection by remember { mutableStateOf(0) }
    var pendingOutgoingMediaId by remember { mutableStateOf<String?>(null) }
    var pendingIncoming by remember { mutableStateOf<melox.model.SearchSong?>(null) }
    val mediaId = "${track.id.source.storageValue}:${track.id.value}"

    LaunchedEffect(track.id, pendingDirection) {
        if (pendingDirection != 0 && mediaId != pendingOutgoingMediaId) {
            contentOffset.snapTo(0f)
            pendingDirection = 0
            pendingOutgoingMediaId = null
            pendingIncoming = null
        } else if (pendingDirection == 0) {
            contentOffset.snapTo(0f)
            pendingIncoming = null
        }
    }

    LaunchedEffect(pendingOutgoingMediaId) {
        if (pendingOutgoingMediaId == null) return@LaunchedEffect
        kotlinx.coroutines.delay(1_500L)
        contentOffset.animateTo(0f, spring(dampingRatio = .72f, stiffness = 430f))
        pendingDirection = 0
        pendingOutgoingMediaId = null
        pendingIncoming = null
    }

    val expansionProgress = if (animatedVisibilityScope != null) {
        val value by animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { androidx.compose.animation.core.tween(
                MeloXSettingsRuntime.playerTransitionDurationMs,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ) }, label = "mini-player-expansion-progress",
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        value
    } else 0f
    val miniChromeAlpha = 1f - smoothStep(expansionProgress, 0.10f, 0.58f)
    val miniSurfaceAlpha = 1f - smoothStep(expansionProgress, 0.02f, 0.48f)
    val playerArtworkScale = if (MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || playerState.isPlaying) 1f else 0.74f
    val sharedArtworkScale = 1f + (playerArtworkScale - 1f) * smoothStep(expansionProgress, 0.30f, 0.88f)

    val compact = compactProgress.coerceIn(0f, 1f)
    val artworkSize = lerpDpF(MeloXChromeTokens.MiniArtworkExpanded, MeloXChromeTokens.MiniArtworkCompact, compact)
    val compactNextAlpha = 1f - smoothStep(compact, 0.04f, 0.50f)
    val controlMetric = smoothStep(compact, 0.20f, 0.80f)
    val controlSize = lerpDpF(MeloXChromeTokens.MiniControlExpanded, MeloXChromeTokens.MiniControlCompact, controlMetric)
    val controlIconSize = lerpDpF(MeloXChromeTokens.MiniControlIconExpanded, MeloXChromeTokens.MiniControlIconCompact, controlMetric)
    val titleFontSize = with(density) { MeloXChromeTokens.MiniTitleFontDp.dp.toSp() }
    val artistFontSize = with(density) { MeloXChromeTokens.MiniArtistFontDp.dp.toSp() }
    val lineHeight = with(density) { MeloXChromeTokens.MiniLineHeightDp.dp.toSp() }
    val holdIncoming = pendingIncoming != null && pendingDirection != 0 && mediaId == pendingOutgoingMediaId && abs(contentOffset.value) > swipeWidth * 0.82f
    val shown = if (holdIncoming) pendingIncoming else null
    val visualOffset = if (holdIncoming) 0f else contentOffset.value
    val dragDirection = when {
        visualOffset < 0f -> -1
        visualOffset > 0f -> 1
        else -> 0
    }
    val dragProgress = (abs(visualOffset) / swipeWidth.coerceAtLeast(1f)).coerceIn(0f, 1f)
    val adjacentAlpha = smoothStep(dragProgress, 0.15f, 0.85f)

    val hasNext = queue.index < queue.songs.lastIndex || repeatMode == PlaybackCommands.REPEAT_ALL
    val hasPrevious = queue.index > 0 || repeatMode == PlaybackCommands.REPEAT_ALL
    fun adjacentPreview(direction: Int = dragDirection): melox.model.SearchSong? {
        val target = when {
            direction < 0 && hasNext -> queue.songs.getOrNull(if (queue.index + 1 < queue.songs.size) queue.index + 1 else 0)
            direction > 0 && hasPrevious -> queue.songs.getOrNull(if (queue.index > 0) queue.index - 1 else queue.songs.lastIndex)
            else -> null
        } ?: return null
        return target
    }

    val sharedShell = sharedShellModifier(sharedTransitionScope, animatedVisibilityScope)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeloXChromeTokens.MiniHeight)
            .onSizeChanged { swipeWidth = it.width.toFloat().coerceAtLeast(1f) }
            .then(sharedShell),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MeloXChromeTokens.MiniHeight),
        ) {
            // Glass surface (kept out of shared-bounds node on Android; here
            // simply the under-layer).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = miniSurfaceAlpha }
                    .meloXLiquidBottomBar(
                        shape = RoundedCornerShape(50),
                        tint = bottomLiquidGlassTint(),
                        refractionHeight = 8.dp,
                        surfaceColor = bottomGlassSurfaceColor(),
                        pressProgress = liquidInteraction.highlight.pressProgress,
                        dark = bottomBarTone() > 0.5f,
                    ),
            )
        }

        // Chrome rides the same press/drag transform.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(MeloXChromeTokens.MiniHeight)
                .padding(vertical = 3.dp)
                .meloXLiquidContentTransform(liquidInteraction),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MeloXChromeTokens.MiniHeight)
                    .clip(RoundedCornerShape(50))
                    .padding(
                        horizontal = lerpDpF(12.dp, 8.dp, compact),
                        vertical = lerpDpF(4.dp, 3.dp, compact),
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mini-player-expand")
                        .fillMaxHeight()
                        .pointerInput(track.id) {
                            detectHorizontalDragGestures(
                                 onDragStart = {
                                    pendingDirection = 0
                                    pendingOutgoingMediaId = null
                                    pendingIncoming = null
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
                                    if (direction != 0 && (direction < 0 && hasNext || direction > 0 && hasPrevious)) {
                                        scope.launch {
                                            contentOffset.animateTo(direction * swipeWidth, spring(dampingRatio = .68f, stiffness = 360f))
                                            pendingDirection = direction
                                            pendingIncoming = adjacentPreview(direction)
                                            pendingOutgoingMediaId = mediaId
                                            if (direction < 0) player?.next() else player?.previous()
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
                        modifier = Modifier.fillMaxSize().graphicsLayer { translationX = visualOffset },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(lerpDpF(10.dp, 8.dp, compact)),
                    ) {
                        MiniArtwork(
                            artworkSize,
                            shown?.artworkUrl ?: track.artworkUrl,
                            sharedElementModifier(sharedTransitionScope, animatedVisibilityScope, mediaId),
                            scale = sharedArtworkScale,
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .wrapContentHeight(Alignment.CenterVertically)
                                .graphicsLayer { alpha = miniChromeAlpha },
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = (shown?.name ?: track.title).ifBlank { "正在播放" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = titleFontSize,
                                lineHeight = lineHeight,
                                softWrap = false,
                                fontWeight = FontWeight.SemiBold,
                                color = MeloXColors.pageOnSurface(),
                            )
                            Text(
                                text = shown?.artists ?: track.artistText,
                                modifier = Modifier.height(15.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = artistFontSize,
                                lineHeight = lineHeight,
                                softWrap = false,
                                color = MeloXColors.pageOnSurface().copy(alpha = 0.64f),
                            )
                        }
                    }
                adjacentPreview()?.let { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = adjacentAlpha
                                translationX = visualOffset - dragDirection * swipeWidth
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(lerpDpF(10.dp, 8.dp, compact)),
                    ) {
                        MiniArtwork(artworkSize, entry.artworkUrl)
                        Column(Modifier.weight(1f).fillMaxHeight().wrapContentHeight(Alignment.CenterVertically)) {
                            Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = titleFontSize, lineHeight = lineHeight, fontWeight = FontWeight.SemiBold, color = MeloXColors.pageOnSurface())
                            Text(entry.artists, modifier = Modifier.height(15.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = artistFontSize, lineHeight = lineHeight, color = MeloXColors.pageOnSurface().copy(alpha = 0.64f))
                        }
                    }
                }
                }

                MiniDancingBars(
                    isPlaying = playerState.isPlaying,
                    sample = rememberMiniReactiveSample(mediaId, playerState.isPlaying, expansionProgress > 0.01f),
                    color = MeloXColors.pageOnSurface().copy(alpha = miniChromeAlpha * 0.72f),
                    modifier = Modifier
                        .width(15.dp)
                        .height(18.dp)
                        .graphicsLayer { alpha = miniChromeAlpha },
                )

                Row(
                    modifier = Modifier
                        .height(controlSize)
                        .zIndex(8f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    MiniVectorButton(
                        kind = if (playerState.isPlaying) MiniGlyph.Pause else MiniGlyph.Play,
                        enabled = true,
                        onClick = { player?.togglePlay() },
                        size = controlSize,
                        iconSize = controlIconSize,
                        modifier = Modifier
                            .testTag("mini-player-play-pause")
                            .offset(x = MeloXChromeTokens.MiniControlStartPull)
                            .zIndex(10f),
                        visualAlpha = miniChromeAlpha,
                    )
                    if (compactNextAlpha > 0.05f) {
                        MiniVectorButton(
                            kind = MiniGlyph.Forward,
                            enabled = hasNext,
                            onClick = { player?.next() },
                            size = controlSize,
                            iconSize = controlIconSize,
                            modifier = Modifier
                                .testTag("mini-player-next")
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
private fun rememberMiniReactiveSample(mediaId: String, isPlaying: Boolean, expanding: Boolean): melox.playback.MeloXAudioReactiveSample {
    var sample by remember(mediaId) { mutableStateOf(melox.playback.MeloXAudioReactiveRuntime.sample(mediaId)) }
    LaunchedEffect(mediaId, isPlaying, expanding) {
        if (!isPlaying || expanding) {
            melox.playback.MeloXAudioReactiveRuntime.publish(mediaId, AudioPlayer.positionMs.value, false)
            sample = melox.playback.MeloXAudioReactiveSample.Idle
            return@LaunchedEffect
        }
        while (true) {
            melox.playback.MeloXAudioReactiveRuntime.publishPosition(mediaId, AudioPlayer.positionMs.value)
            sample = melox.playback.MeloXAudioReactiveRuntime.sample(mediaId)
            kotlinx.coroutines.delay(50)
        }
    }
    return sample
}

@Composable
private fun MiniDancingBars(
    isPlaying: Boolean,
    sample: melox.playback.MeloXAudioReactiveSample,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val bars = melox.playback.miniBarFractions(isPlaying, sample)
    Canvas(modifier) {
        val gap = size.width * .12f
        val barWidth = (size.width - gap * 3f) / 4f
        bars.forEachIndexed { index, heightFraction ->
            val barHeight = size.height * heightFraction.coerceIn(.12f, 1f)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), (size.height - barHeight) / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

private enum class MiniGlyph { Play, Pause, Forward }

@Composable
private fun MiniVectorButton(
    kind: MiniGlyph,
    enabled: Boolean,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    visualAlpha: Float = 1f,
) {
    val baseAlpha = if (enabled) 0.94f else 0.26f
    val drawAlpha = visualAlpha.coerceIn(0f, 1f)
    val color = MeloXColors.pageOnSurface().copy(alpha = baseAlpha)
    Box(
        modifier = modifier
            .graphicsLayer { alpha = drawAlpha }
            .size(size)
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
            size = iconSize.value.toInt(),
            color = color,
        )
    }
}

// Artwork: size animated 40→30dp, corner 6dp, source-tinted gradient
@Composable
private fun MiniArtwork(
    size: androidx.compose.ui.unit.Dp,
    artworkUrl: String?,
    sharedModifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val sourceColor = MeloXColors.sourceColors[currentTrack?.id?.source?.storageValue] ?: MeloXColors.Primary
    MeloXArtworkImage(
        artworkUrl,
        sourceColor,
        Modifier
            .requiredSize(size)
            .then(sharedModifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(6.dp)),
    )
}

private fun lerpDpF(start: androidx.compose.ui.unit.Dp, end: androidx.compose.ui.unit.Dp, t: Float): androidx.compose.ui.unit.Dp =
    androidx.compose.ui.unit.Dp(start.value + (end.value - start.value) * t)


@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
private fun sharedShellModifier(
    scope: androidx.compose.animation.SharedTransitionScope?,
    visibility: androidx.compose.animation.AnimatedVisibilityScope?,
): Modifier {
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(key = MeloXPlayerShellKey),
            animatedVisibilityScope = visibility,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = androidx.compose.animation.ExitTransition.None,
            boundsTransform = MeloXPlayerShellBoundsTransform,
            resizeMode = androidx.compose.animation.SharedTransitionScope.ResizeMode.RemeasureToBounds,
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
private fun sharedElementModifier(
    scope: androidx.compose.animation.SharedTransitionScope?,
    visibility: androidx.compose.animation.AnimatedVisibilityScope?,
    mediaId: String?,
): Modifier {
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(key = MeloXPlayerArtworkKey(mediaId)),
            animatedVisibilityScope = visibility,
            boundsTransform = MeloXArtworkBoundsTransform,
        )
    }
}
