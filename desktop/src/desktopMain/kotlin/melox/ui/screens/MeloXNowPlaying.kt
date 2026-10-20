package melox.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.model.SearchSong
import melox.lyrics.MeloXLyricsLoad
import melox.lyrics.lyricLineShowsSyllables
import melox.lyrics.lyricLineTranslation
import melox.lyrics.lyricShouldAutoScroll
import melox.lyrics.lyricTapTargetMs
import melox.playback.MeloXDesktopPlayer
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.settings.MeloXPlayerShell
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXSettingsPreferences
import melox.settings.meloXPlayerExpansion
import melox.settings.playerIsolationAlpha
import melox.ui.foundation.smoothStep
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.meloXRecordBackdrop
import melox.ui.navigation.MeloXNavState
import melox.ui.player.MeloXPlayerBackdrop
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
    PlaybackCommands.playerController()

@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
fun MeloXNowPlayingScreen(
    onOpenPlaybackSettings: () -> Unit,
    navState: MeloXNavState? = null,
    onDismiss: (() -> Unit)? = null,
    onSeekCollapse: (suspend (Float) -> Unit)? = null,
    onSettleCollapse: (suspend (Boolean) -> Unit)? = null,
    playerExpanded: Boolean = true,
    playerTargetExpanded: Boolean = true,
    playerTransitionFraction: Float = 0f,
    sharedTransitionScope: androidx.compose.animation.SharedTransitionScope? = null,
    animatedVisibilityScope: androidx.compose.animation.AnimatedVisibilityScope? = null,
) {
    val player = livePlayer()
    val playerState by AudioPlayer.state.collectAsState()
    val playerProgress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val durationMs by AudioPlayer.durationMs.collectAsState()
    val track = currentTrack ?: return

    val rememberPage = MeloXSettingsPreferences.boolean("playback_remember_page", true)
    var currentPage by remember(rememberPage) {
        mutableIntStateOf(if (rememberPage) when (MeloXSettingsPreferences.string("playback_last_page", "Artwork")) {
            "Lyrics" -> 1
            "Queue" -> 2
            else -> 0
        } else 0)
    }
    fun selectPage(page: Int) {
        currentPage = page
        if (rememberPage) MeloXSettingsPreferences.setString("playback_last_page", listOf("Artwork", "Lyrics", "Queue")[page])
    }
    val lyrics = rememberMeloXPlayerLyrics(track)
    val queueSnapshot by PlaybackCommands.queue.collectAsState()
    val queuedIndex = melox.playback.classicQueueIndex(queueSnapshot.songs, queueSnapshot.index, track)
    val queuedSong = queuedIndex?.let { queueSnapshot.songs.getOrNull(it) }
    val actionSong = queuedSong ?: SearchSong(
        id = track.id.value.toLongOrNull() ?: 0L,
        name = track.title,
        artists = track.artistText,
        album = track.album?.name.orEmpty(),
        artworkUrl = track.artworkUrl,
        durationMs = track.durationMs ?: 0L,
        providerTrack = track,
    )
    val actionQueue = if (queuedSong != null) queueSnapshot.songs else listOf(actionSong)
    var showActions by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var outputError by remember { mutableStateOf<String?>(null) }
    val uiScope = rememberCoroutineScope()
    val showOutputSwitcher = {
        uiScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { melox.platform.showSystemAudioOutputSwitcher() }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { outputError = error.message ?: "无法打开系统音频输出" }
        }
        Unit
    }

    val sourceColor = MeloXColors.sourceColors[track.id.source.storageValue] ?: MeloXColors.Primary
    val expansion = meloXPlayerExpansion(playerExpanded, playerTargetExpanded, playerTransitionFraction)
    val isolation = playerIsolationAlpha(MeloXSettingsRuntime.playerBackgroundIsolationEnabled, expansion)

    // ── Collapse gesture (vertical drag on grabber/scene) ──
    val sharedShell = playerSharedShell(sharedTransitionScope, animatedVisibilityScope)
    CompositionLocalProvider(LocalMeloXPlayerLyrics provides lyrics) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(sharedShell)
            .background(Color.Black),
    ) {
        if (isolation > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = isolation)),
            )
        }
        val playerBackdrop = melox.ui.glass.rememberMeloXLayerBackdrop()
        CompositionLocalProvider(melox.ui.glass.LocalMeloXLayerBackdrop provides null) {
        MeloXPlayerBackdrop(
            mode = MeloXSettingsRuntime.playerBackgroundMode,
            lyricsPage = currentPage == 1,
            artworkUrl = track.artworkUrl,
            isPlaying = playerState.isPlaying,
            mediaId = "${track.id.source.storageValue}:${track.id.value}",
            reduceMotion = MeloXSettingsRuntime.reduceMotion,
            modifier = Modifier.fillMaxSize().meloXRecordBackdrop(playerBackdrop),
        )
        }

        CompositionLocalProvider(melox.ui.glass.LocalMeloXLayerBackdrop provides playerBackdrop) {
            val classic = MeloXSettingsRuntime.playerShell == MeloXPlayerShell.Classic
            if (MeloXSettingsRuntime.desktopLayoutEnabled) {
                melox.ui.desktop.MeloXDesktopNowPlaying(track,
                    onDismiss = { onDismiss?.invoke() ?: navState?.goBack() },
                    onActions = { showQuality = false; showActions = true })
            } else if (!classic) {
                MeloXIOSPlayerScene(
                    track = track,
                    sourceColor = sourceColor,
                    isPlaying = playerState.isPlaying,
                    playerProgress = playerProgress,
                    durationMs = durationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L,
                    page = currentPage,
                    onPage = ::selectPage,
                    onDismiss = { onDismiss?.invoke() ?: navState?.goBack() },
                    onShowActions = { showQuality = false; showActions = true },
                    onShowQuality = { showActions = false; showQuality = true },
                    onShowOutput = showOutputSwitcher,
                    onSeekCollapse = onSeekCollapse,
                    onSettleCollapse = onSettleCollapse,
                    expansion = expansion,
                    player = player,
                    sharedArtworkModifier = playerSharedElement(sharedTransitionScope, animatedVisibilityScope,
                        "${track.id.source.storageValue}:${track.id.value}"),
                )
            } else {
                ClassicPlayerScene(
                    track = track,
                    sourceColor = sourceColor,
                    isPlaying = playerState.isPlaying,
                    playerProgress = playerProgress,
                    durationMs = durationMs.takeIf { it > 0L } ?: track.durationMs ?: 0L,
                    currentPage = currentPage,
                    onPage = ::selectPage,
                    onDismiss = { onDismiss?.invoke() ?: navState?.goBack() },
                    onSeek = { value -> player?.seekTo((value * AudioPlayer.durationMs.value).toLong()) },
                    onPrevious = { player?.previous() },
                    onTogglePlay = { player?.togglePlay() },
                    onNext = { player?.next() },
                    player = player,
                    modifier = Modifier.fillMaxSize(),
                    actionSong = actionSong,
                    actionQueue = actionQueue,
                    showActions = showActions,
                    onShowActions = { showActions = true },
                    onDismissActions = { showActions = false },
                    onShowOutput = showOutputSwitcher,
                    onShowQuality = { showActions = false; showQuality = true },
                )
            }

            if (!classic || MeloXSettingsRuntime.desktopLayoutEnabled) {
                MeloXSongActionsOverlay(
                    song = actionSong,
                    queue = actionQueue,
                    visible = showActions,
                    onDismiss = { showActions = false },
                    fromPlayer = true,
                )
            }
        }
        CompositionLocalProvider(melox.ui.glass.LocalMeloXLayerBackdrop provides playerBackdrop) {
            MeloXPlayerQualityOverlay(track, showQuality, onDismiss = { showQuality = false },
                onOpenPlaybackSettings = onOpenPlaybackSettings)
            MeloXPlayerOutputError(outputError, onDismiss = { outputError = null })
            MeloXPlayerPlaybackError(playerState, onRetry = { player?.play() })
        }
        lyrics.shareIndex?.let { initial ->
            MeloXLyricSharePanel(track, sourceColor, lyrics.document?.lines.orEmpty(), initial,
                onDismiss = { lyrics.shareIndex = null })
        }
    }
    }
}

// ── Grabber: 30dp touch area, 60×5dp pill ──
@Composable
internal fun Grabber(
    onDismiss: () -> Unit,
    dragModifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = 1500f),
        label = "melox-grabber-press",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .testTag("player-collapse")
            .then(dragModifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(width = 60.dp, height = 5.dp)
                .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.52f)),
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
    mediaId: String?,
): Modifier {
    if (scope == null || visibility == null) return Modifier
    return with(scope) {
        Modifier.sharedElement(
            sharedContentState = rememberSharedContentState(MeloXPlayerArtworkKey(mediaId)),
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
    onOpenActions: () -> Unit = {},
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artworkSize = maxOf(170.dp, minOf(maxWidth, maxHeight - 92.dp))
        Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        val playbackScale by animateFloatAsState(
            targetValue = if (isPlaying) 1f else 0.74f,
            animationSpec = if (isPlaying) MeloXMotion.artworkGrowSpec() else MeloXMotion.artworkShrinkSpec(),
            label = "melox-artwork-scale",
        )
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
                    ambientShadowColor = Color.Black.copy(alpha = 0.28f),
                    spotShadowColor = Color.Black.copy(alpha = 0.28f),
                )
                .size(artworkSize)
                .clip(RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            MeloXArtworkImage(track.artworkUrl, sourceColor, Modifier.fillMaxSize())
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        Column(modifier = Modifier.weight(1f)) {
        Text(
            text = track.title,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = track.artistText,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            color = Color.White.copy(alpha = 0.64f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onOpenActions),
            contentAlignment = Alignment.Center,
        ) {
            MeloXSymbolIcon(MeloXSymbol.Ellipsis, color = Color.White, size = 22)
        }
        }
        Spacer(Modifier.height(8.dp))
    }
    }
}

@Composable
private fun ClassicPlayerScene(
    track: melox.music.model.MusicTrack,
    sourceColor: Color,
    isPlaying: Boolean,
    playerProgress: Float,
    durationMs: Long,
    currentPage: Int,
    onPage: (Int) -> Unit,
    onDismiss: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    player: MeloXDesktopPlayer?,
    modifier: Modifier = Modifier,
    actionSong: SearchSong,
    actionQueue: List<SearchSong>,
    showActions: Boolean,
    onShowActions: () -> Unit,
    onDismissActions: () -> Unit,
    onShowOutput: () -> Unit,
    onShowQuality: () -> Unit,
) {
    val window = melox.ui.layout.rememberMeloXWindowInfo()
    BoxWithConstraints(modifier) {
        val wide = window.isLandscape || window.supportsTwoPane
        val narrowArtwork = minOf(maxWidth - 38.dp, maxHeight * 0.43f)
        Box(Modifier.fillMaxSize().testTag("now-playing").semantics { stateDescription = listOf("Artwork", "Lyrics", "Queue")[currentPage] }) {
        Column(Modifier.fillMaxSize().widthIn(max = window.maxContentWidth).padding(horizontal = window.gutter)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                melox.ui.glass.MeloXGlassIconButton(MeloXSymbol.XMark, onDismiss, modifier = Modifier.testTag("player-collapse"), contentDescription = "收起播放器")
                Text(
                    "正在播放",
                    fontFamily = MeloXLanTingProFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.58f),
                )
                melox.ui.glass.MeloXGlassIconButton(MeloXSymbol.Ellipsis, onShowActions, modifier = Modifier.testTag("player-actions"), contentDescription = "播放操作")
            }
            if (wide && currentPage == 0) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(38.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXArtworkImage(
                        track.artworkUrl,
                        sourceColor,
                        Modifier.weight(0.46f).fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                    )
                    Column(Modifier.weight(0.54f)) {
                        ClassicMetadata(track)
                        Spacer(Modifier.height(20.dp))
                        MeloXPlayerCoreControls(track, isPlaying, playerProgress, durationMs, currentPage, player, onPage, onShowQuality, onShowOutput, {})
                    }
                }
            } else if (currentPage == 0) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MeloXArtworkImage(
                        track.artworkUrl,
                        sourceColor,
                        Modifier.size(narrowArtwork).clip(RoundedCornerShape(18.dp)),
                    )
                }
                ClassicMetadata(track)
                Spacer(Modifier.height(8.dp))
                MeloXPlayerCoreControls(track, isPlaying, playerProgress, durationMs, currentPage, player, onPage, onShowQuality, onShowOutput, {})
            } else if (wide) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeloXArtworkImage(
                        track.artworkUrl,
                        sourceColor,
                        Modifier.weight(0.40f).fillMaxWidth().clip(RoundedCornerShape(18.dp)),
                    )
                    Column(Modifier.weight(0.60f).fillMaxHeight()) {
                        ClassicMetadata(track)
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            if (currentPage == 1) LyricsPage(track, sourceColor) else QueuePage(sourceColor)
                        }
                        Spacer(Modifier.height(8.dp))
                        MeloXPlayerCoreControls(track, isPlaying, playerProgress, durationMs, currentPage, player, onPage, onShowQuality, onShowOutput, {})
                    }
                }
            } else {
                ClassicMetadata(track)
                Box(Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
                    if (currentPage == 1) LyricsPage(track, sourceColor) else QueuePage(sourceColor)
                }
                Spacer(Modifier.height(8.dp))
                MeloXPlayerCoreControls(track, isPlaying, playerProgress, durationMs, currentPage, player, onPage, onShowQuality, onShowOutput, {})
            }
        }
            MeloXSongActionsOverlay(
                song = actionSong,
                queue = actionQueue,
                visible = showActions,
                onDismiss = onDismissActions,
                fromPlayer = true,
            )
        }
    }
}

@Composable
private fun ClassicMetadata(track: melox.music.model.MusicTrack) {
    Column(Modifier.fillMaxWidth()) {
        Text(track.title, color = Color.White, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artistText, color = Color.White.copy(alpha = 0.56f), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ClassicControls(
    isPlaying: Boolean,
    progress: Float,
    durationMs: Long,
    currentPage: Int,
    onPage: (Int) -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    player: MeloXDesktopPlayer?,
    onShowOutput: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        ProgressSection(progress, durationMs, onSeekFinished = onSeek)
        TransportSection(isPlaying, onPrevious, onTogglePlay, onNext)
        VolumeSection(player)
        PageSelectorSection(player, currentPage, onShowOutput, onPage)
    }
}

// ── Progress: 52dp section, 4→6dp track, Monospace 11sp labels ──
@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
internal fun ProgressSection(
    progress: Float,
    durationMs: Long,
    qualityChip: (@Composable () -> Unit)? = null,
    onSeekFinished: (Float) -> Unit,
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrub by remember { mutableFloatStateOf(progress) }
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 4.dp,
        animationSpec = tween(120),
        label = "melox-progress-track",
    )
    val shownProgress = (if (isScrubbing) scrub else progress).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Slider(
            value = shownProgress,
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
                .testTag("player-progress")
                .height(20.dp),
            thumb = { Spacer(Modifier.size(0.dp)) },
            track = {
                Box(Modifier.fillMaxWidth().height(trackHeight).clip(CircleShape)
                    .background(Color.White.copy(alpha = .20f))) {
                    Box(Modifier.fillMaxWidth(shownProgress).fillMaxHeight().background(Color.White.copy(alpha = .96f)))
                }
            },
        )
        Box(
            modifier = Modifier.fillMaxWidth().height(26.dp),
        ) {
            Text(
                text = formatTime(((if (isScrubbing) scrub else progress) * durationMs).toLong()),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.align(Alignment.CenterStart),
            )
            qualityChip?.let { chip -> Box(Modifier.align(Alignment.Center)) { chip() } }
            Text(
                text = "−" + formatTime(((1f - shownProgress) * durationMs).toLong()),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.50f),
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

// ── Transport: play/pause 64dp glyph 48sp; skip 64dp glyph 34sp ──
@Composable
internal fun TransportSection(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.weight(1f))
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = onPrevious, tag = "player-previous") {
            MeloXSymbolIcon(MeloXSymbol.PreviousTrack, color = Color.White, size = 34)
        }
        Spacer(Modifier.weight(1f))
        PressScaleButton(size = 64.dp, pressScale = 0.86f, onClick = onTogglePlay, tag = "player-play-pause") {
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
        Spacer(Modifier.weight(1f))
        PressScaleButton(size = 64.dp, pressScale = 0.84f, onClick = onNext, tag = "player-next") {
            MeloXSymbolIcon(MeloXSymbol.NextTrack, color = Color.White, size = 34)
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun PressScaleButton(
    size: androidx.compose.ui.unit.Dp,
    pressScale: Float,
    onClick: () -> Unit,
    tag: String,
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
            .testTag(tag)
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun VolumeSection(player: MeloXDesktopPlayer?) {
    var volume by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 16.dp else 14.dp,
        animationSpec = tween(120),
        label = "melox-volume-thumb",
    )
    val volTrackHeight by animateDpAsState(
        targetValue = if (dragging) 4.dp else 3.dp,
        animationSpec = tween(120),
        label = "melox-volume-track",
    )
    Row(
        modifier = Modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MeloXSymbolIcon(MeloXSymbol.Volume, color = Color.White.copy(alpha = 0.62f), size = 12)
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                volume = it.coerceIn(0f, 1f)
                player?.setVolume(volume)
            },
            onValueChangeFinished = { dragging = false },
            valueRange = 0f..1f,
            modifier = Modifier.weight(1f).height(32.dp),
            thumb = {
                Box(
                    Modifier
                        .size(thumbSize)
                        .shadow(3.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            },
            track = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(volTrackHeight)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.20f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(volume.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Color.White.copy(alpha = 0.82f)),
                    )
                }
            },
        )
        MeloXSymbolIcon(MeloXSymbol.Volume, color = Color.White.copy(alpha = 0.62f), size = 15)
    }
}

// ── Source page selector: Lyrics / system audio output / Queue, with mode badge. ──
@Composable
internal fun PageSelectorSection(
    player: MeloXDesktopPlayer?,
    currentPage: Int,
    onShowOutput: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val repeatMode by player?.repeatMode?.collectAsState() ?: remember { mutableIntStateOf(PlaybackCommands.REPEAT_OFF) }
    val shuffleEnabled by player?.shuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val repeatOn = repeatMode != PlaybackCommands.REPEAT_OFF
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp).padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageSelectorButton(
            selected = currentPage == 1,
            enabled = true,
            onClick = { onSelect(if (currentPage == 1) 0 else 1) },
            tag = "player-show-lyrics",
            description = "歌词",
        ) {
            MeloXSymbolIcon(MeloXSymbol.Lyrics, color = pageIconColor(currentPage == 1), size = 22)
        }
        PageSelectorButton(selected = false, enabled = true, onClick = onShowOutput,
            tag = "player-output", description = "音频输出") {
            Canvas(Modifier.size(22.dp)) {
                val w = size.width
                val h = size.height
                val stroke = (size.minDimension * .085f).coerceAtLeast(1.35f)
                val color = Color.White.copy(alpha = .72f)
                drawArc(color, 205f, 130f, false, Offset(w * .08f, h * .02f), Size(w * .84f, h * .72f),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 205f, 130f, false, Offset(w * .24f, h * .18f), Size(w * .52f, h * .44f),
                    style = Stroke(stroke, cap = StrokeCap.Round))
                drawPath(Path().apply { moveTo(w * .50f, h * .50f); lineTo(w * .78f, h * .92f); lineTo(w * .22f, h * .92f); close() }, color)
            }
        }
        Box {
        PageSelectorButton(
            selected = currentPage == 2,
            enabled = true,
            onClick = { onSelect(if (currentPage == 2) 0 else 2) },
            tag = "player-show-queue",
            description = "接下来播放",
        ) {
            MeloXSymbolIcon(MeloXSymbol.Queue, color = pageIconColor(currentPage == 2), size = 22)
        }
            if (currentPage != 2 && (shuffleEnabled || repeatOn)) {
                Box(Modifier.align(Alignment.TopEnd).size(15.dp).clip(CircleShape).background(Color.White.copy(alpha = .82f)), Alignment.Center) {
                    Text(if (shuffleEnabled) "S" else if (repeatMode == PlaybackCommands.REPEAT_ONE) "1" else "R",
                        color = Color.Black.copy(alpha = .72f), fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }
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
    tag: String,
    description: String,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // press 0.86 (stiffness 650), selected scale 1.04
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.86f else 1f,
        animationSpec = MeloXMotion.pageSelectorPress(),
        label = "melox-page-selector",
    )
    val selectionScale by animateFloatAsState(if (selected) 1.04f else 1f,
        spring(.5f, 1500f), label = "scene-page-selection-scale-$tag")
    val bgAlpha by animateFloatAsState(
        targetValue = if (selected) 0.68f * 0.16f else 0f,
        animationSpec = tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "melox-page-sel-bg",
    )
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(tag).semantics { contentDescription = description; this.selected = selected }
            .graphicsLayer {
                scaleX = pressScale * selectionScale
                scaleY = pressScale * selectionScale
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
