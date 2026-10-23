package melox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import melox.music.model.MusicTrack
import melox.playback.MeloXDesktopPlayer
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.smoothStep
import melox.ui.glass.meloXBackdropBlur

private const val ControlsHeight = 279

/** Pinned Android Scene/SharedHost geometry; one cover and resident alternate pages. */
@Composable
internal fun MeloXIOSPlayerScene(
    track: MusicTrack,
    sourceColor: Color,
    isPlaying: Boolean,
    playerProgress: Float,
    durationMs: Long,
    page: Int,
    onPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    onShowActions: () -> Unit,
    onShowQuality: () -> Unit,
    onShowOutput: () -> Unit,
    onSeekCollapse: (suspend (Float) -> Unit)?,
    onSettleCollapse: (suspend (Boolean) -> Unit)?,
    expansion: Float,
    player: MeloXDesktopPlayer?,
    sharedArtworkModifier: Modifier,
) {
    val mix by melox.playback.MeloXAutoMixTransitionRuntime.state.collectAsState()
    val displayedTrack = if (mix.active || mix.handedOff) mix.incoming ?: track else track
    var showsLyricsControls by remember(track.id) { mutableStateOf(true) }
    var activityGeneration by remember(track.id) { mutableIntStateOf(0) }
    var showSkyline by remember { mutableStateOf(false) }
    fun revealControls() { showsLyricsControls = true; activityGeneration += 1 }
    LaunchedEffect(page) { revealControls() }
    LaunchedEffect(page, track.id, showsLyricsControls, activityGeneration, MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs) {
        if (page != 1 || !showsLyricsControls) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs.toLong())
        showsLyricsControls = false
    }

    val transition = updateTransition(page, label = "scene-page-transition")
    val artworkAlpha by transition.animateFloat({ spring(.7f, 300f) }, "scene-artwork-alpha") { if (it == 0) 1f else 0f }
    val artworkOffset by transition.animateDp({ spring(.7f, 300f) }, "scene-artwork-offset") { if (it == 0) 0.dp else (-300).dp }
    val lyricsAlpha by transition.animateFloat({ spring(.7f, 300f) }, "scene-lyrics-alpha") { if (it == 1) 1f else 0f }
    val lyricsOffset by transition.animateDp({ spring(.7f, 300f) }, "scene-lyrics-offset") { if (it == 0) 400.dp else 0.dp }
    val lyricsScale by transition.animateFloat({ spring(.7f, 300f) }, "scene-lyrics-scale") { if (it == 2) .92f else 1f }
    val queueAlpha by transition.animateFloat({ spring(.7f, 300f) }, "scene-queue-alpha") { if (it == 2) 1f else 0f }
    val queueOffset by transition.animateDp({ spring(.7f, 300f) }, "scene-queue-offset") { if (it == 0) 400.dp else 0.dp }
    val queueScale by transition.animateFloat({ spring(.7f, 300f) }, "scene-queue-scale") { if (it == 1) .92f else 1f }
    val headerAlpha by transition.animateFloat({ spring(.7f, 300f) }, "scene-header-alpha") { if (it == 0) 0f else 1f }
    val headerOffset by transition.animateDp({ spring(.7f, 300f) }, "scene-header-offset") { if (it == 0) 40.dp else 0.dp }

    val sceneFocus = remember { FocusRequester() }
    LaunchedEffect(page, showSkyline, showsLyricsControls) { sceneFocus.requestFocus() }
    BoxWithConstraints(Modifier.fillMaxSize().focusRequester(sceneFocus).focusable()) {
        val isLandscape = maxWidth > maxHeight
        LaunchedEffect(isLandscape) { if (!isLandscape) showSkyline = false }
        val skylineVisible = isLandscape && page == 1 && showSkyline
        melox.ui.player.MeloXDisplayAwakeEffect(melox.settings.MeloXScreenAwakePolicy.required(
            MeloXSettingsRuntime.screenAwakeMode, page == 1, !showsLyricsControls,
            skylineVisible, MeloXSettingsRuntime.skylineKeepsScreenAwake,
            foreground = true, playerVisible = expansion >= .99f))
        melox.ui.navigation.MeloXBackHandler(skylineVisible, priority = 60) { showSkyline = false }
        val dragRangePx = (constraints.maxHeight * .86f).coerceAtLeast(1f)
        var collapseProgress by remember { mutableFloatStateOf(0f) }
        var seekJob by remember { mutableStateOf<Job?>(null) }
        var settleJob by remember { mutableStateOf<Job?>(null) }
        val scope = rememberCoroutineScope()
        val latestPage = rememberUpdatedState(page)
        val latestExpansion = rememberUpdatedState(expansion)
        val latestSeek = rememberUpdatedState(onSeekCollapse)
        val latestSettle = rememberUpdatedState(onSettleCollapse)
        val latestDismiss = rememberUpdatedState(onDismiss)
        DisposableEffect(Unit) { onDispose { seekJob?.cancel(); settleJob?.cancel() } }

        fun seekCollapse(delta: Float) {
            val next = (collapseProgress + delta / dragRangePx).coerceIn(0f, .999f)
            if (next == collapseProgress) return
            collapseProgress = next
            seekJob?.cancel()
            seekJob = scope.launch(start = CoroutineStart.UNDISPATCHED) { latestSeek.value?.invoke(next) }
        }
        suspend fun beginCollapse() {
            settleJob?.cancelAndJoin()
            seekJob?.cancelAndJoin()
            collapseProgress = (1f - latestExpansion.value).coerceIn(0f, .999f)
            latestSeek.value?.invoke(collapseProgress)
        }
        fun settle(velocity: Float) {
            val collapse = collapseProgress >= .42f || velocity >= 1200f
            settleJob?.cancel()
            settleJob = scope.launch {
                seekJob?.cancelAndJoin()
                if (latestSettle.value != null) latestSettle.value?.invoke(collapse)
                else if (collapse) latestDismiss.value()
                if (!collapse) collapseProgress = 0f
            }
        }
        val dragState = rememberDraggableState(::seekCollapse)
        val alternateScroll = remember(dragRangePx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || latestPage.value == 0 || collapseProgress <= 0f) return Offset.Zero
                    seekCollapse(available.y)
                    return Offset(0f, available.y)
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || latestPage.value == 0) return Offset.Zero
                    if (available.y <= 0f && collapseProgress <= 0f) return Offset.Zero
                    if (collapseProgress <= 0f) {
                        settleJob?.cancel()
                        collapseProgress = (1f - latestExpansion.value).coerceIn(0f, .999f)
                    }
                    seekCollapse(available.y)
                    return Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (latestPage.value == 0 || collapseProgress <= 0f) return Velocity.Zero
                    settle(available.y)
                    return available
                }
            }
        }
        val fullPlayerAlpha = smoothStep(expansion, .04f, .52f)
        Box(Modifier.fillMaxSize().testTag("now-playing").semantics {
            stateDescription = listOf("Artwork", "Lyrics", "Queue")[page]
        }.graphicsLayer { alpha = fullPlayerAlpha }
            .nestedScroll(alternateScroll)
            .draggable(dragState, Orientation.Vertical, enabled = page == 0,
                onDragStarted = { beginCollapse() }, onDragStopped = { settle(it) })) {
            if (isLandscape) {
                MeloXIOSLandscapePlayerScene(track, sourceColor, isPlaying, playerProgress, durationMs,
                    page, player, showsLyricsControls, onPage = { revealControls(); onPage(it) },
                    onDismiss, onShowActions, onShowQuality, onShowOutput,
                    onInterfaceInteraction = ::revealControls,
                    onInterfaceVisibilityChange = { if (it) revealControls() else showsLyricsControls = false },
                    showSkyline = skylineVisible, onShowSkyline = { showSkyline = true }, onHideSkyline = { showSkyline = false },
                    grabberDragModifier = Modifier.draggable(dragState, Orientation.Vertical,
                        onDragStarted = { beginCollapse() }, onDragStopped = { settle(it) }))
            } else Column(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                Grabber(
                    onDismiss = onDismiss,
                    dragModifier = if (page != 0) Modifier.draggable(dragState, Orientation.Vertical,
                        onDragStarted = { beginCollapse() }, onDragStopped = { settle(it) }) else Modifier,
                )
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Box(Modifier.fillMaxSize().zIndex(if (page == 0) 2f else 0f).graphicsLayer {
                        alpha = artworkAlpha; translationY = artworkOffset.toPx()
                    }.then(if (page != 0) Modifier.clearAndSetSemantics { } else Modifier)) {
                        ArtworkDetails(displayedTrack, onShowActions, interactive = page == 0)
                    }

                    Box(Modifier.fillMaxSize().zIndex(if (page == 1) 2f else 0f).graphicsLayer {
                        alpha = lyricsAlpha; translationY = lyricsOffset.toPx(); scaleX = lyricsScale; scaleY = lyricsScale
                    }.padding(top = 88.dp).then(if (page != 1) Modifier.clearAndSetSemantics { } else Modifier)) {
                        LyricsPage(track, sourceColor, interactive = page == 1,
                            interfaceHidden = !showsLyricsControls,
                            onInterfaceInteraction = ::revealControls,
                            onInterfaceVisibilityChange = { if (it) revealControls() else showsLyricsControls = false })
                    }
                    Box(Modifier.fillMaxSize().zIndex(if (page == 2) 2f else 0f).graphicsLayer {
                        alpha = queueAlpha; translationY = queueOffset.toPx(); scaleX = queueScale; scaleY = queueScale
                    }.padding(top = 80.dp, bottom = ControlsHeight.dp)
                        .then(if (page != 2) Modifier.clearAndSetSemantics { } else Modifier)) {
                        QueuePage(sourceColor, interactive = page == 2)
                    }
                    val headerShape = RoundedCornerShape(20.dp)
                    Row(Modifier.fillMaxWidth().height(72.dp).zIndex(3f).graphicsLayer {
                        alpha = headerAlpha; translationY = headerOffset.toPx()
                    }.then(if (page == 0) Modifier.clearAndSetSemantics { } else Modifier)
                        .then(if (page == 2) Modifier.meloXBackdropBlur(headerShape, 20.dp, Color.Black.copy(alpha = .10f)) else Modifier)
                        .padding(start = 84.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(displayedTrack.title, color = Color.White, fontSize = 20.sp, lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(displayedTrack.artistText, color = Color.White.copy(alpha = .64f), fontSize = 18.sp,
                                lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        Box(Modifier.size(40.dp).testTag("player-actions").clip(CircleShape)
                            .clickable(enabled = page != 0, onClick = onShowActions), Alignment.Center) {
                            MeloXSymbolIcon(MeloXSymbol.Ellipsis, color = Color.White, size = 22)
                        }
                    }
                }
            }
            AnimatedVisibility(visible = !isLandscape && (page != 1 || showsLyricsControls), modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn(tween(220, easing = FastOutSlowInEasing)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 8 },
                exit = fadeOut(tween(180, easing = FastOutSlowInEasing)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it / 8 }) {
                Box(Modifier.fillMaxWidth().height(ControlsHeight.dp).testTag("player-controls")) {
                    if (page != 0) Box(Modifier.fillMaxSize().meloXBackdropBlur(RectangleShape, 24.dp)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onClick = {}))
                    MeloXPlayerCoreControls(track, isPlaying, playerProgress, durationMs, page, player,
                        onPage, onShowQuality, onShowOutput, ::revealControls, Modifier.padding(horizontal = 32.dp))
                }
            }
            if (!skylineVisible) PersistentPlayerArtwork(track, sourceColor, isPlaying, page, expansion, sharedArtworkModifier, isLandscape)
        }
    }
}

@Composable
private fun ArtworkDetails(track: MusicTrack, onShowActions: () -> Unit, interactive: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val artworkSize = maxOf(170.dp, minOf(maxWidth, maxHeight - 92.dp))
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(artworkSize))
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, modifier = Modifier.testTag("player-artwork-title"), color = Color.White, fontSize = 20.sp, lineHeight = 24.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artistText, color = Color.White.copy(alpha = .64f), fontSize = 20.sp,
                        lineHeight = 24.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                }
                Box(Modifier.size(40.dp).testTag("player-actions").clip(CircleShape)
                    .clickable(enabled = interactive, onClick = onShowActions), Alignment.Center) {
                    MeloXSymbolIcon(MeloXSymbol.Ellipsis, color = Color.White, size = 22)
                }
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(ControlsHeight.dp))
        }
    }
}

@Composable
private fun PersistentPlayerArtwork(
    track: MusicTrack, sourceColor: Color, isPlaying: Boolean, page: Int, expansion: Float, sharedModifier: Modifier,
    isLandscape: Boolean,
) {
    val mix by melox.playback.MeloXAutoMixTransitionRuntime.state.collectAsState()
    // Eligibility is decided at transition entry. Visiting Lyrics/Queue must not
    // replay the paired-cover entrance when returning in the middle of a mix.
    var transitionEligible by remember(mix.outgoing?.id, mix.incoming?.id) {
        mutableStateOf(mix.active && page == 0 && MeloXSettingsRuntime.transitionUiEnabled && !MeloXSettingsRuntime.reduceMotion)
    }
    LaunchedEffect(page, MeloXSettingsRuntime.transitionUiEnabled, MeloXSettingsRuntime.reduceMotion) {
        if (page != 0 || !MeloXSettingsRuntime.transitionUiEnabled || MeloXSettingsRuntime.reduceMotion) transitionEligible = false
    }
    val showTransition = transitionEligible && page == 0 && MeloXSettingsRuntime.transitionUiEnabled && !MeloXSettingsRuntime.reduceMotion
    val headerProgress by animateFloatAsState(if (page == 0) 0f else 1f,
        tween(if (MeloXSettingsRuntime.reduceMotion) 0 else 480, easing = FastOutSlowInEasing), label = "persistent-artwork-page-frame")
    val switchBounce = remember { Animatable(1f) }
    var previousMediaId by remember { mutableStateOf(track.id) }
    LaunchedEffect(track.id, MeloXSettingsRuntime.reduceMotion, MeloXSettingsRuntime.artworkMotionEnabled) {
        val changed = previousMediaId != track.id
        previousMediaId = track.id
        switchBounce.snapTo(1f)
        if (changed && !MeloXSettingsRuntime.reduceMotion && MeloXSettingsRuntime.artworkMotionEnabled &&
            !mix.active && !mix.handedOff) {
            switchBounce.snapTo(0f)
            switchBounce.animateTo(1f, spring(dampingRatio = .42f, stiffness = 480f, visibilityThreshold = .001f))
        }
    }
    val playbackScale by animateFloatAsState(
        if (MeloXSettingsRuntime.reduceMotion || !MeloXSettingsRuntime.artworkMotionEnabled || isPlaying) 1f else .74f,
        if (isPlaying) MeloXMotion.artworkGrowSpec() else MeloXMotion.artworkShrinkSpec(), label = "shared-artwork-playback-scale")
    val elevation by animateDpAsState(if (isPlaying) 26.dp else 14.dp, spring(.92f, 320f), label = "shared-artwork-shadow")
    val blend = smoothStep(expansion, .30f, .88f)
    // During MiniPlay morph, register the artwork at the destination page's
    // real frame immediately. Otherwise restored Lyrics first opens a large
    // cover and then jumps to its 72dp header after the shell has settled.
    val destinationFrame = if (isLandscape || page == 0) 0f else 1f
    val pageFrameProgress = if (expansion > .01f && expansion < .99f) destinationFrame
        else if (isLandscape) 0f else headerProgress
    val effectiveScale = 1f + (playbackScale - 1f) * blend * (1f - pageFrameProgress)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = if (isLandscape) 20.dp else 32.dp)) {
        val contentTop = 30.dp
        val portraitHeight = (maxHeight - contentTop - ControlsHeight.dp).coerceAtLeast(1.dp)
        val fullSize = if (isLandscape) minOf(460.dp, (maxHeight - 42.dp).coerceAtLeast(1.dp), maxWidth * .43f)
            else minOf(maxWidth, portraitHeight - 92.dp).coerceAtLeast(1.dp)
        val fullX = (((if (isLandscape) maxWidth * .43f else maxWidth) - fullSize) / 2f).coerceAtLeast(0.dp)
        val fullY = if (isLandscape) (maxHeight - fullSize) / 2f
            else (contentTop + portraitHeight - fullSize - 78.dp - 30.dp).coerceAtLeast(0.dp)
        val coverSize = fullSize + (72.dp - fullSize) * pageFrameProgress
        val x = fullX * (1f - pageFrameProgress)
        val y = fullY + (22.dp - fullY) * pageFrameProgress
        val shape = RoundedCornerShape((6f + 6f * expansion.coerceIn(0f, 1f)).dp)
        Box(Modifier.offset(x, y).size(coverSize).then(sharedModifier)
            .testTag("player-persistent-artwork").semantics {
                stateDescription = "page=$page;frame=$pageFrameProgress;size=${coverSize.value};y=${y.value};motion=${switchBounce.value}"
            }) {
            MeloXAutoMixArtwork(track, sourceColor, Modifier.fillMaxSize().graphicsLayer {
                val bounceScale = .90f + .10f * switchBounce.value
                scaleX = effectiveScale * bounceScale; scaleY = effectiveScale * bounceScale
                alpha = .55f + .45f * (switchBounce.value / .4f).coerceIn(0f, 1f)
            }.then(if (showTransition && (mix.active || mix.handedOff)) Modifier else Modifier
                .shadow(elevation * blend * (1f - pageFrameProgress * .55f), shape, clip = false,
                    ambientColor = Color.Black.copy(alpha = .28f), spotColor = Color.Black.copy(alpha = .28f)).clip(shape)), transitionEligible = showTransition)
        }
    }
}
