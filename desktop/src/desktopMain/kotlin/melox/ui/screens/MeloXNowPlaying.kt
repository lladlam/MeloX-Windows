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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.playback.MeloXDesktopPlayer
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.smoothStep
import melox.ui.navigation.MeloXNavState
import melox.ui.player.MeloXFlowingLightBackdrop
import melox.ui.player.MeloXLyricsArtworkBackdrop
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

private fun livePlayer(): MeloXDesktopPlayer? =
    PlaybackCommands::class.java.getMethod("getActiveController${'$'}shared").invoke(PlaybackCommands) as? MeloXDesktopPlayer

@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun MeloXNowPlayingScreen(
    navState: MeloXNavState? = null,
    onDismiss: (() -> Unit)? = null,
    onSeekCollapse: (suspend (Float) -> Unit)? = null,
    onSettleCollapse: (suspend (Boolean) -> Unit)? = null,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val player = livePlayer()
    val playerState by AudioPlayer.state.collectAsState()
    val playerProgress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val durationMs by AudioPlayer.durationMs.collectAsState()
    val track = currentTrack ?: return

    var currentPage by remember { mutableStateOf(0) } // 0 artwork, 1 lyrics, 2 queue
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }

    val sourceColor = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.Primary

    // ── Collapse gesture (vertical drag on grabber/scene) ──
    val sharedShell = playerSharedShell(sharedTransitionScope, animatedVisibilityScope)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(sharedShell)
            .background(Color.Black)
            .pointerInput(Unit) {
                // Vertical drag collapses; handled on grabber below (scene-level
                // drag reserved for lyric scroll passthrough)
            },
    ) {
        MeloXFlowingLightBackdrop(
            artworkUrl = track.artworkUrl,
            isPlaying = playerState.isPlaying,
            mediaId = track.id.value,
            modifier = Modifier.fillMaxSize(),
        )
        if (currentPage == 1) {
            MeloXLyricsArtworkBackdrop(
                artworkUrl = track.artworkUrl,
                isPlaying = playerState.isPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.38f)),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── Grabber (60×5dp, White α0.52) ──
            Grabber(onDismiss = { onDismiss?.invoke() ?: navState?.goBack() }, onSeekCollapse = onSeekCollapse, onSettleCollapse = onSettleCollapse)

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
                        sharedModifier = playerSharedElement(sharedTransitionScope, animatedVisibilityScope),
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
                    durationMs = durationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L,
                    onSeekFinished = { value ->
                        player?.seekTo((value * AudioPlayer.durationMs.value).toLong())
                    },
                )
                Spacer(modifier = Modifier.height(19.dp))

                // Transport: 82dp
                TransportSection(
                    isPlaying = playerState.isPlaying,
                    onPrevious = { player?.previous() },
                    onTogglePlay = { player?.togglePlay() },
                    onNext = { player?.next() },
                )
                Spacer(modifier = Modifier.height(31.dp))

                VolumeSection(player = player)
                Spacer(modifier = Modifier.height(3.dp))

                RepeatShuffleSection(player = player)

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
private fun Grabber(
    onDismiss: () -> Unit,
    onSeekCollapse: (suspend (Float) -> Unit)? = null,
    onSettleCollapse: (suspend (Boolean) -> Unit)? = null,
) {
    var dragY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
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
                .pointerInput(onSeekCollapse, onSettleCollapse) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .pointerInput(onSeekCollapse, onSettleCollapse) {
                    val range = size.height.coerceAtLeast(1).toFloat() * 18f
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val progress = (dragY / range).coerceIn(0f, 1f)
                            val collapse = progress >= 0.42f
                            scope.launch {
                                if (onSettleCollapse != null) onSettleCollapse(collapse)
                                else if (collapse) onDismiss()
                            }
                            dragY = 0f
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 0f) {
                            dragY += dragAmount
                            val progress = (dragY / range).coerceIn(0f, 0.999f)
                            if (onSeekCollapse != null) scope.launch { onSeekCollapse(progress) }
                        }
                    }
                },
        )
    }
}

@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
@androidx.compose.runtime.Composable
private fun playerSharedShell(
    scope: androidx.compose.animation.SharedTransitionScope?,
    visibility: androidx.compose.animation.AnimatedVisibilityScope?,
): Modifier {
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedBounds(
            sharedContentState = rememberSharedContentState(MeloXPlayerShellKey),
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
private fun playerSharedElement(
    scope: androidx.compose.animation.SharedTransitionScope?,
    visibility: androidx.compose.animation.AnimatedVisibilityScope?,
): Modifier {
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(MeloXPlayerArtworkKey),
            animatedVisibilityScope = visibility,
            boundsTransform = MeloXArtworkBoundsTransform,
        )
    }
}

// ── Artwork page: 12dp radius, playback scale, shadow ──
@Composable
private fun ArtworkPage(
    track: melox.music.model.MusicTrack,
    sourceColor: Color,
    isPlaying: Boolean,
    sharedModifier: Modifier = Modifier,
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
                .then(sharedModifier)
                .graphicsLayer(
                    scaleX = playbackScale,
                    scaleY = playbackScale,
                    shadowElevation = shadowElev,
                    shape = RoundedCornerShape(12.dp),
                    clip = false,
                )
                .size(300.dp)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXArtworkImage(track.artworkUrl, sourceColor, Modifier.fillMaxSize())
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

// ── Lyrics page: line highlight from provider lyrics ──
@Composable
private fun LyricsPage(track: melox.music.model.MusicTrack, sourceColor: Color) {
    var document by remember(track.id) { mutableStateOf<melox.lyrics.LyricsDocument?>(null) }
    var failed by remember(track.id) { mutableStateOf(false) }
    val positionMs by AudioPlayer.positionMs.collectAsState()
    LaunchedEffect(track.id) {
        failed = false
        document = runCatching {
            val provider = melox.music.provider.MeloXMusicProviders.create().require(track.id.source)
            (provider as? melox.music.provider.LyricsCapability)?.lyrics(track)
        }.getOrNull()
        if (document == null || document?.lines.isNullOrEmpty()) failed = document == null
    }
    val lines = document?.lines.orEmpty()
    val active = document?.highlightedIndex(positionMs) ?: -1
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val player = livePlayer()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active)
    }
    if (lines.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(vertical = 88.dp), contentAlignment = Alignment.Center) {
            Text(
                text = if (failed) "歌词加载失败" else "暂无歌词",
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 18.sp,
                color = Color.White.copy(alpha = 0.42f),
            )
        }
        return
    }
    androidx.compose.foundation.lazy.LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 88.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(lines.size) { index ->
            val line = lines[index]
            val on = index == active
            Column(Modifier.clickable { player?.seekTo(line.timeMs) }) {
                if (line.syllables.isNotEmpty()) {
                    Row {
                        line.syllables.forEach { syllable ->
                            val syllableActive = positionMs in syllable.startTimeMs until syllable.endTimeMs
                            val ended = positionMs >= syllable.endTimeMs
                            Text(
                                text = syllable.text,
                                fontFamily = MeloXLanTingProFontFamily,
                                fontSize = if (on) 26.sp else 22.sp,
                                fontWeight = if (syllableActive) FontWeight.Bold else if (on) FontWeight.Bold else FontWeight.Medium,
                                color = when {
                                    syllableActive -> Color.White
                                    ended -> Color.White.copy(alpha = 0.92f)
                                    else -> Color.White.copy(alpha = 0.38f)
                                },
                            )
                        }
                    }
                } else {
                    Text(
                        text = line.text,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = if (on) 26.sp else 22.sp,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = Color.White.copy(alpha = if (on) 1f else 0.38f),
                    )
                }
                line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                    Text(translation, fontSize = 15.sp, color = Color.White.copy(alpha = if (on) 0.72f else 0.28f))
                }
            }
        }
    }
}

@Composable
private fun QueuePage(sourceColor: Color) {
    val snapshot by melox.playback.PlaybackCommands.queue.collectAsState()
    val songs = snapshot.songs
    val player = livePlayer()
    val rowHeightPx = with(LocalDensity.current) { 64.dp.toPx() }
    Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
        if (songs.isEmpty()) {
            Text(
                text = "继续播放",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                fontFamily = MeloXLanTingProFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White,
            )
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MeloXSymbolIcon(MeloXSymbol.Queue, color = Color.White.copy(alpha = 0.55f), size = 40)
            }
        } else {
            val dragYById = remember { mutableStateMapOf<Long, Float>() }
            androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(songs.size, key = { songs[it].id }) { index ->
                    val song = songs[index]
                    val current = index == snapshot.index
                    val header = when {
                        snapshot.index < 0 -> null
                        index == 0 && snapshot.index > 0 -> "已播放"
                        index == snapshot.index -> "继续播放"
                        else -> null
                    }
                    Column(Modifier.fillMaxWidth()) {
                        if (header != null) {
                            Text(
                                text = header,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                fontFamily = MeloXLanTingProFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp,
                                color = Color.White,
                            )
                        }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .then(
                                if (!current) {
                                    Modifier
                                        .offset { IntOffset(0, (dragYById[song.id] ?: 0f).roundToInt()) }
                                        .pointerInput(song.id, rowHeightPx) {
                                            detectDragGesturesAfterLongPress(
                                                onDragEnd = { dragYById[song.id] = 0f },
                                                onDragCancel = { dragYById[song.id] = 0f },
                                            ) { change, dragAmount ->
                                                change.consume()
                                                val next = (dragYById[song.id] ?: 0f) + dragAmount.y
                                                dragYById[song.id] = next
                                                val queue = melox.playback.PlaybackCommands.queue.value
                                                val from = queue.songs.indexOfFirst { it.id == song.id }
                                                if (from < 0) return@detectDragGesturesAfterLongPress
                                                val currentIndex = queue.index
                                                val count = queue.songs.size
                                                if (next >= rowHeightPx && from + 1 < count && from + 1 > currentIndex) {
                                                    player?.move(from, from + 1)
                                                    dragYById[song.id] = next - rowHeightPx
                                                } else if (next <= -rowHeightPx && from - 1 > currentIndex) {
                                                    player?.move(from, from - 1)
                                                    dragYById[song.id] = next + rowHeightPx
                                                }
                                            }
                                        }
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { player?.seekToIndex(index) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXArtworkImage(song.artworkUrl, sourceColor, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (current) Color.White else Color.White.copy(alpha = 0.82f), fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal)
                            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp)
                        }
                        if (!current && songs.size > 1) {
                            Text(
                                "移除",
                                modifier = Modifier.clickable { player?.removeAt(index) }.padding(8.dp),
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 13.sp,
                            )
                        }
                    }
                    }
                }
            }
        }
    }
}

// ── Progress: 52dp section, 4→6dp track, Monospace 11sp labels ──
@Composable
private fun ProgressSection(
    progress: Float,
    durationMs: Long,
    onSeekFinished: (Float) -> Unit,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(progress) }
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
            value = (if (isScrubbing) scrub else progress).coerceIn(0f, 1f),
            onValueChange = {
                isScrubbing = true
                scrub = it
            },
            onValueChangeFinished = {
                onSeekFinished(scrub)
                isScrubbing = false
            },
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
private fun TransportSection(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = onPrevious) {
            MeloXSymbolIcon(MeloXSymbol.PreviousTrack, color = Color.White, size = 34)
        }
        PressScaleButton(size = 64.dp, pressScale = 0.86f, onClick = onTogglePlay) {
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
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = onNext) {
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

@Composable
private fun VolumeSection(player: MeloXDesktopPlayer?) {
    var volume by remember { mutableFloatStateOf(1f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        contentAlignment = Alignment.Center,
    ) {
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = {
                volume = it
                player?.setVolume(it)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White.copy(alpha = 0.96f),
                inactiveTrackColor = Color.White.copy(alpha = 0.20f),
            ),
        )
    }
}

@Composable
private fun RepeatShuffleSection(player: MeloXDesktopPlayer?) {
    val repeatMode by player?.repeatMode?.collectAsState() ?: remember { mutableIntStateOf(PlaybackCommands.REPEAT_OFF) }
    val shuffleEnabled by player?.shuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val repeatLabel = when (repeatMode) {
        PlaybackCommands.REPEAT_ONE -> "单曲"
        PlaybackCommands.REPEAT_ALL -> "列表"
        else -> "关闭"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "循环 $repeatLabel",
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    player?.setRepeatMode((repeatMode + 1) % 3)
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "随机 ${if (shuffleEnabled) "开" else "关"}",
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.82f),
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    player?.setShuffleEnabled(!shuffleEnabled)
                }
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
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
