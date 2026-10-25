package melox.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.lyrics.*
import melox.music.model.MusicTrack
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.settings.MeloXLyricAnnotationDisplayMode
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXPlayerBackgroundMode
import melox.settings.MeloXLyricsRenderingQuality
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.player.ArtworkDynamicPaletteProvider
import melox.ui.glass.meloXLiquidButton
import melox.ui.glass.LocalMeloXLayerBackdrop
import androidx.compose.ui.graphics.toArgb
import melox.network.MeloXHttpClient
import okhttp3.Request
import melox.ui.navigation.MeloXBackHandler
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class MeloXPlayerLyricsState(val track: MusicTrack) {
    var document by mutableStateOf<LyricsDocument?>(null)
    var loading by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)
    var shareIndex by mutableStateOf<Int?>(null)
}

internal val LocalMeloXPlayerLyrics = staticCompositionLocalOf<MeloXPlayerLyricsState?> { null }
internal val MeloXLyricTimeMsKey = SemanticsPropertyKey<Long>("MeloXLyricTimeMs")
internal val MeloXLyricBrowsingKey = SemanticsPropertyKey<Boolean>("MeloXLyricBrowsing")
internal val MeloXLyricWordLinesKey = SemanticsPropertyKey<Int>("MeloXLyricWordLines")
internal val MeloXLyricDocumentKey = SemanticsPropertyKey<String>("MeloXLyricDocument")

/** Keep the source press indication without Desktop's persistent mouse-hover fill. */
private class LyricTouchInteractionSource : MutableInteractionSource {
    private val delegate = MutableInteractionSource()
    override val interactions = delegate.interactions
    override suspend fun emit(interaction: Interaction) {
        if (interaction !is HoverInteraction) delegate.emit(interaction)
    }
    override fun tryEmit(interaction: Interaction): Boolean =
        interaction is HoverInteraction || delegate.tryEmit(interaction)
}

/** Owned above the page layers, so loading/upgrades survive Artwork↔Lyrics↔Queue. */
@Composable
internal fun rememberMeloXPlayerLyrics(track: MusicTrack): MeloXPlayerLyricsState {
    val preference = melox.lyrics.requestedLyricPreference()
    // A new request owns new display state, including when language changes after 30s.
    // Cancelled loads/upgrades can only hold the retired state, never the new language's state.
    val state = remember(track, preference, MeloXSettingsRuntime.automaticLyricSelectionEnabled,
        MeloXSettingsRuntime.lyricStrongBindingEnabled, MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled) {
        MeloXPlayerLyricsState(track)
    }
    LaunchedEffect(state) {
        state.loading = true
        state.error = null
        try {
            MeloXLyricsLoad.shared.observe(track, preference = preference).collect { document ->
                if (state.document == null || melox.playback.MeloXPlaybackOutput.positionMs.value <= MeloXLyricsLoad.AmllUpgradeWindowMs) {
                    state.document = document
                }
                state.loading = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            state.error = error.message ?: "歌词加载失败"
        } finally {
            state.loading = false
        }
    }
    return state
}

@Composable
internal fun LyricsPage(
    track: MusicTrack,
    sourceColor: Color,
    interactive: Boolean = true,
    interfaceHidden: Boolean = false,
    onInterfaceInteraction: () -> Unit = {},
    onInterfaceVisibilityChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val surface = LocalMeloXLyricSurface.current
    if (MeloXSettingsRuntime.lyricsStyle != melox.settings.MeloXLyricsStyle.AppleMusic) {
        MeloXAlternativeLyricsPanel(track, modifier, onInterfaceInteraction, interactive)
        return
    }
    val state = LocalMeloXPlayerLyrics.current ?: rememberMeloXPlayerLyrics(track)
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val renderedPosition = remember(track.id) { mutableLongStateOf(melox.playback.MeloXPlaybackOutput.positionMs.value) }
    var anchorPosition by remember(track.id) { mutableLongStateOf(melox.playback.MeloXPlaybackOutput.positionMs.value) }
    var anchorNanos by remember(track.id) { mutableLongStateOf(System.nanoTime()) }
    LaunchedEffect(track.id) {
        melox.playback.MeloXPlaybackOutput.positionMs.collect {
            anchorPosition = it
            anchorNanos = System.nanoTime()
            renderedPosition.longValue = it
        }
    }
    val quality = MeloXSettingsRuntime.lyricRenderingQuality
    val refreshRate = min(MeloXSettingsRuntime.lyricRefreshRate, if (quality == MeloXLyricsRenderingQuality.Low) 30 else 60)
    LaunchedEffect(track.id, interactive, playback.isPlaying, refreshRate) {
        if (!interactive || !playback.isPlaying) return@LaunchedEffect
        var lastFrame = 0L
        val minimumNanos = 1_000_000_000L / refreshRate.coerceIn(30, 120)
        while (true) {
            val frame = withFrameNanos { it }
            if (lastFrame != 0L && frame - lastFrame < minimumNanos) continue
            lastFrame = frame
            renderedPosition.longValue = anchorPosition + (System.nanoTime() - anchorNanos) / 1_000_000L
        }
    }
    val document = remember(state.document, MeloXSettingsRuntime.lyricPseudoTimingEnabled) {
        state.document?.let { if (MeloXSettingsRuntime.lyricPseudoTimingEnabled) it.withPseudoTiming() else it }
    }
    val lines = document?.lines.orEmpty()
    val wordByWord = MeloXSettingsRuntime.lyricWordByWordEnabled
    val usesWordTiming = wordByWord && lines.any { it.syllables.isNotEmpty() }
    val lyricAdvance = effectiveBilibiliLyricAdvance(MeloXSettingsRuntime.lyricAdvanceMs, rememberBilibiliLyricOffset(track))
    val timedAdvance = if (track.id.source == melox.music.model.MusicSource.Bilibili || MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord) lyricAdvance else 0L
    val lineAdvance = if (usesWordTiming) timedAdvance else lyricAdvance
    val interludes = remember(lines) { MeloXLyricPresentation.interludes(lines) }
    val interludeByIndex = remember(interludes) { interludes.associateBy { it.followingLyricIndex } }
    val countdownEnabled = MeloXSettingsRuntime.lyricInterludeCountdownEnabled
    val activeInterlude by remember(interludes, countdownEnabled) { derivedStateOf {
        if (countdownEnabled) interludes.firstOrNull {
            renderedPosition.longValue >= it.startTimeMs && renderedPosition.longValue < it.followingLyricTimeMs
        } else null
    } }
    val active by remember(document, lineAdvance, countdownEnabled) { derivedStateOf {
        activeInterlude?.followingLyricIndex ?: document?.highlightedIndex(renderedPosition.longValue + lineAdvance,
            LyricHighlightStrategy.FirstSyllableOrLineStart) ?: -1
    } }
    val colorLead = MeloXSettingsRuntime.lyricFocusColorLeadMs
    val colorActive by remember(document, lineAdvance, colorLead) { derivedStateOf {
        document?.highlightedIndex(renderedPosition.longValue + lineAdvance + colorLead,
            LyricHighlightStrategy.FirstSyllableOrLineStart) ?: -1
    } }
    val activeTimedLines by remember(document, timedAdvance) { derivedStateOf {
        document?.let { LyricTimelineProcessor.activeTimedLineIndexes(it, renderedPosition.longValue + timedAdvance) }.orEmpty()
    } }
    val timeProvider = remember(track.id, timedAdvance) { { renderedPosition.longValue + timedAdvance } }
    val listState = key(track.id) { rememberLazyListState() }
    val rowHeights = remember(document) { mutableStateMapOf<Int, Int>() }
    var browsing by remember(track.id) { mutableStateOf(false) }
    var interactionGeneration by remember(track.id) { mutableIntStateOf(0) }
    var scrollHideDistance by remember(track.id) { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val latestInteract = rememberUpdatedState(onInterfaceInteraction)
    val latestVisibility = rememberUpdatedState(onInterfaceVisibilityChange)
    val latestHidden = rememberUpdatedState(interfaceHidden)
    val hideThresholdPx = with(density) { MeloXSettingsRuntime.lyricScrollHideThresholdDp.dp.toPx() }
    val latestThreshold = rememberUpdatedState(hideThresholdPx)
    val userScroll = remember(track.id) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || abs(available.y) < .01f) return Offset.Zero
                browsing = true
                interactionGeneration += 1
                if (available.y > 0f) {
                    scrollHideDistance = 0f
                    latestInteract.value()
                    latestVisibility.value(true)
                } else {
                    if (!latestHidden.value) latestInteract.value()
                    scrollHideDistance += -available.y
                    if (scrollHideDistance >= latestThreshold.value) {
                        scrollHideDistance = 0f
                        if (!latestHidden.value) latestVisibility.value(false)
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(browsing, interactionGeneration, MeloXSettingsRuntime.lyricFollowDelayMs) {
        if (!browsing) return@LaunchedEffect
        delay(MeloXSettingsRuntime.lyricFollowDelayMs.toLong())
        browsing = false
    }

    BoxWithConstraints(modifier.fillMaxSize().testTag("player-lyrics").semantics {
        this[MeloXLyricBrowsingKey] = browsing
        this[MeloXLyricWordLinesKey] = lines.count { it.syllables.isNotEmpty() }
        this[MeloXLyricDocumentKey] = document?.let {
            "${it.source}:${it.quality};lines=${it.lines.size};pseudoTimingAllowed=${it.pseudoTimingAllowed};pseudoTimingEnabled=${MeloXSettingsRuntime.lyricPseudoTimingEnabled}"
        } ?: "loading"
    }) {
        val fontScale = surface.fontScale ?: MeloXSettingsRuntime.lyricFontScale
        val focusPosition = surface.focusPosition ?: MeloXSettingsRuntime.lyricFocusPosition
        val viewportPx = constraints.maxHeight.toFloat()
        val topPadding = maxHeight * focusPosition
        val bottomPadding = maxOf(maxHeight * (1f - focusPosition), 40.dp)
        val romanSize = max(34f * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
        val translationSize = max(34f * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale, 13f)
        val estimatedStride = with(density) {
            ((40.8f * fontScale + romanSize * 1.2f + translationSize * 1.2f).sp.toPx() +
                (8f + 28f * MeloXSettingsRuntime.lyricSpacingScale).dp.toPx()).coerceAtLeast(1f)
        }
        val target = activeTimedLines.minOrNull() ?: active
        val cascade = rememberMeloXLyricCascade(document, listState, rowHeights, target, browsing,
            interactive, viewportPx, focusPosition, fontScale)
        when {
            state.loading && state.document == null -> CircularProgressIndicator(
                color = surface.foreground.copy(alpha = .9f), modifier = Modifier.align(Alignment.Center))
            state.error != null && state.document == null -> Text(state.error.orEmpty(),
                color = surface.foreground.copy(alpha = .52f), fontSize = 15.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            lines.isEmpty() -> Text("暂无歌词", color = surface.foreground.copy(alpha = .42f), fontSize = 18.sp,
                modifier = Modifier.align(Alignment.Center))
            else -> Layout(modifier = Modifier.fillMaxSize().clipToBounds(), content = {
              LazyColumn(state = listState, modifier = Modifier.fillMaxSize().nestedScroll(userScroll), userScrollEnabled = interactive) {
                item(key = "lyrics-top-padding") { Spacer(Modifier.height(topPadding)) }
                itemsIndexed(lines, key = { index, line -> "${track.id}:${line.timeMs}:$index" }) { index, line ->
                    val activeAtOwnTime = index in activeTimedLines
                    val focused = activeInterlude == null && if (activeTimedLines.isNotEmpty()) activeAtOwnTime else index == colorActive
                    val focus by animateFloatAsState(if (focused) 1f else 0f,
                        tween(if (MeloXSettingsRuntime.lyricReduceMotion) 0 else 120,
                            easing = Easing { MeloXLyricPresentation.smoothStep(it) }), label = "lyric-focus-$index")
                    val scaleProgress by animateFloatAsState(if (index == target) 1f else 0f,
                        tween(if (MeloXSettingsRuntime.lyricReduceMotion) 0 else MeloXSettingsRuntime.lyricScaleBounceDurationMs,
                            easing = Easing { MeloXLyricPresentation.springFraction(it,
                                if (MeloXSettingsRuntime.lyricScaleBounceEnabled) MeloXSettingsRuntime.lyricScaleBounce else 0f) }),
                        label = "lyric-scale-$index")
                    val distance = if (browsing) {
                        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index + 1 }
                        abs((item?.offset?.toFloat() ?: viewportPx * focusPosition) + (rowHeights[index] ?: 0) * .5f - viewportPx * focusPosition)
                    } else abs(index - cascade.focusIndex.coerceAtLeast(0)) * estimatedStride
                    val opacity = lyricDistanceOpacity(distance, estimatedStride, MeloXSettingsRuntime.lyricDimAmount, focus) *
                        lyricEmphasis(focus, MeloXSettingsRuntime.lyricDimAmount)
                    val distanceScale = if (interfaceHidden) MeloXSettingsRuntime.lyricHiddenInterfaceBlurScale else MeloXSettingsRuntime.lyricDistanceBlurScale
                    val blur = lyricDistanceBlur(distance, estimatedStride, .8f * distanceScale * MeloXSettingsRuntime.lyricBlurStrength, focus)
                    val focusBlur = ((if (index == cascade.focusIndex - 1) 2.4f else 0f) + (if (index == cascade.focusIndex + 1) .7f else 0f)) *
                        .8f * MeloXSettingsRuntime.lyricBlurStrength * (1f - focus)
                    val flipped = line.agent?.alignment == LyricAgentAlignment.Flipped
                    val textAlign = if (flipped) TextAlign.End else TextAlign.Start
                    val showRoman = MeloXSettingsRuntime.showLyricRomanization && !line.romanization.isNullOrBlank()
                    val visibleRoman = MeloXLyricPresentation.annotationVisible(MeloXSettingsRuntime.showLyricRomanization,
                        line.romanization, MeloXSettingsRuntime.lyricRomanizationDisplayMode, focused)
                    val showTranslation = MeloXSettingsRuntime.showLyricTranslation && !line.translation.isNullOrBlank()
                    val visibleTranslation = MeloXLyricPresentation.annotationVisible(MeloXSettingsRuntime.showLyricTranslation,
                        line.translation, MeloXSettingsRuntime.lyricTranslationDisplayMode, focused)
                    val effectiveBlur = max(blur, focusBlur).let { if (!surface.allowsBlur) 0f else when (quality) {
                        MeloXLyricsRenderingQuality.Low -> 0f
                        MeloXLyricsRenderingQuality.Balanced -> it * .55f
                        MeloXLyricsRenderingQuality.High -> it
                    } }
                    val rowAlpha = Modifier.graphicsLayer { alpha = opacity.coerceIn(0f, 1f) }
                    val followingAlpha = lyricDistanceOpacity(estimatedStride, estimatedStride, MeloXSettingsRuntime.lyricDimAmount, 0f) *
                        lyricEmphasis(0f, MeloXSettingsRuntime.lyricDimAmount)
                    val unplayedAlpha = (followingAlpha / opacity.coerceAtLeast(.001f)).coerceIn(0f, 1f)
                    val interlude = interludeByIndex[index]?.takeIf { countdownEnabled }
                    if (interlude != null) MeloXLyricInterludeCountdown(interlude, { renderedPosition.longValue },
                        MeloXSettingsRuntime.lyricReduceMotion, Modifier.padding(bottom = 8.dp).graphicsLayer {
                            translationY = cascade.offset(index)
                            alpha = opacity.coerceIn(0f, 1f)
                        })
                    val canTap = interactive && MeloXSettingsRuntime.lyricTapSeekEnabled
                    val canShare = interactive && MeloXSettingsRuntime.lyricLongPressShareEnabled
                    Column(Modifier.fillMaxWidth().testTag("lyric-line-$index").semantics {
                        this[MeloXLyricTimeMsKey] = line.timeMs
                        selected = focused
                    }.onSizeChanged { if (it.height > 0) rowHeights[index] = it.height }
                        .graphicsLayer {
                            translationY = cascade.offset(index)
                            val scale = 1f + (MeloXSettingsRuntime.lyricFocusScale - 1f) * max(scaleProgress, focus)
                            scaleX = scale; scaleY = scale
                            transformOrigin = TransformOrigin(if (flipped) 1f else 0f, 0f)
                        }.combinedClickable(enabled = canTap || canShare,
                            interactionSource = remember { LyricTouchInteractionSource() },
                            indication = androidx.compose.foundation.LocalIndication.current,
                            onClick = { if (canTap) { onInterfaceInteraction(); PlaybackCommands.playerController()?.seekTo(line.timeMs) } },
                            onLongClick = if (canShare) ({ onInterfaceInteraction(); state.shareIndex = index }) else null)
                        .padding(horizontal = surface.horizontalInset.dp),
                        horizontalAlignment = if (flipped) Alignment.End else Alignment.Start) {
                        line.accompaniment.filter { it.timeMs < line.timeMs }.forEach {
                            MeloXTimedAccompaniment(it, timeProvider, fontScale, MeloXSettingsRuntime.lyricReduceMotion, quality, effectiveBlur)
                        }
                        val timed = line.syllables.isNotEmpty() && wordByWord && (focus > .001f || activeAtOwnTime)
                        if (visibleRoman) {
                            MeloXRubyLyricText(line, timeProvider, timed, fontScale, quality, effectiveBlur,
                                Modifier.fillMaxWidth().then(rowAlpha))
                        } else {
                            MeloXGlyphLyricText(line, timeProvider, timed, fontScale, MeloXSettingsRuntime.lyricReduceMotion,
                                if (activeAtOwnTime) 1f else focus, unplayedAlpha,
                                renderingQuality = quality, softBlurDp = effectiveBlur,
                                modifier = Modifier.fillMaxWidth().then(rowAlpha))
                        }
                        line.accompaniment.filterNot { it.timeMs < line.timeMs }.forEach {
                            MeloXTimedAccompaniment(it, timeProvider, fontScale, MeloXSettingsRuntime.lyricReduceMotion, quality, effectiveBlur)
                        }
                        if (showRoman && !visibleRoman) {
                            Spacer(Modifier.height(with(density) { (romanSize * 1.2f).sp.toDp() } + 4.dp))
                        }
                        if (showTranslation && !visibleTranslation) {
                            // Keep focus-only annotations from moving the line anchors.
                            Spacer(Modifier.height(with(density) { (translationSize * 1.2f).sp.toDp() } + 4.dp))
                        }
                        if (visibleTranslation) {
                            Text(line.translation.orEmpty(), fontSize = translationSize.sp, lineHeight = (translationSize * 1.2f).sp,
                                fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight, textAlign = textAlign,
                                color = surface.foreground.copy(alpha = MeloXSettingsRuntime.lyricTranslationOpacity),
                                fontFamily = MeloXLanTingProFontFamily,
                                modifier = Modifier.fillMaxWidth().testTag("lyric-translation-$index").then(rowAlpha).graphicsLayer {
                                    val radius = effectiveBlur.dp.toPx()
                                    renderEffect = if (radius > .05f) BlurEffect(radius, radius) else null
                                }.padding(top = 4.dp))
                        }
                    }
                    if (index != lines.lastIndex) Spacer(Modifier.height((surface.paragraphSpacing ?: (28f * MeloXSettingsRuntime.lyricSpacingScale)).dp))
                }
                item(key = "lyrics-bottom-padding") { Spacer(Modifier.height(bottomPadding + with(density) { (estimatedStride * 4f).toDp() })) }
              }
            }) { measurables, constraints ->
                val height = (constraints.maxHeight.toLong() + (estimatedStride * 4f).roundToInt()).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                val list = measurables.single().measure(constraints.copy(minHeight = height, maxHeight = height))
                layout(constraints.maxWidth, constraints.maxHeight) { list.placeRelative(0, 0) }
            }
        }
        if (interactive && interfaceHidden) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(279.dp).testTag("lyrics-reveal-controls")
            .zIndex(100f).clickable(interactionSource = remember { MutableInteractionSource() },
                indication = null, onClick = onInterfaceInteraction))
    }
}

// Equations are copied from pinned MeloXIOSLyricsPanel, not tuned from screenshots.
private fun lyricDistanceBlur(distance: Float, stride: Float, intensity: Float, focus: Float): Float =
    min(max(distance / stride - 1.35f, 0f) * 3.1f, 10f) * intensity * (1f - focus.coerceIn(0f, 1f))

private fun lyricDistanceOpacity(distance: Float, stride: Float, dim: Float, focus: Float): Float {
    val lineDistance = distance / stride
    val base = when {
        lineDistance <= 1f -> 1f - lineDistance * .44f
        lineDistance <= 2f -> .56f - (lineDistance - 1f) * .22f
        else -> max(.12f, .34f - (lineDistance - 2f) * .07f)
    }
    val opacity = 1f - (1f - base) * dim
    return opacity + (1f - opacity) * focus.coerceIn(0f, 1f)
}

private fun lyricEmphasis(focus: Float, dim: Float): Float {
    val unfocused = 1f - (1f - .52f) * dim
    return unfocused + (1f - unfocused) * focus.coerceIn(0f, 1f)
}

@Composable
internal fun MeloXLyricSharePanel(track: MusicTrack, sourceColor: Color, lines: List<LyricLine>, initial: Int, onDismiss: () -> Unit) {
    var selected by remember(lines, initial) { mutableStateOf(setOf(initial)) }
    var status by remember { mutableStateOf<String?>(null) }
    var generating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MeloXBackHandler(enabled = true, priority = 100, onBack = onDismiss)
    // A source Dialog has its own opaque scene. Its controls must not sample
    // the root tab's layer after lifting this panel into the player host.
    CompositionLocalProvider(LocalMeloXLayerBackdrop provides null) {
    Column(Modifier.fillMaxSize().testTag("lyric-share").zIndex(200f).background(MaterialTheme.colorScheme.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("取消", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable(onClick = onDismiss).padding(8.dp))
            Text("分享歌词", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text("全选", color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { selected = lines.indices.toSet() }.padding(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXArtworkImage(track.artworkUrl, sourceColor, Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(track.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground.copy(alpha = .58f))
                Text("已选择 ${selected.size} 行 · 无字符数量限制", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f), modifier = Modifier.padding(top = 4.dp))
            }
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(lines) { index, line ->
                val chosen = index in selected
                val radius = when {
                    !chosen -> 14.dp
                    index - 1 !in selected && index + 1 in selected -> 14.dp
                    index - 1 in selected && index + 1 !in selected -> 14.dp
                    else -> 5.dp
                }
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(radius))
                    .background(if (chosen) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else MaterialTheme.colorScheme.surface)
                    .clickable { selected = if (chosen) selected - index else selected + index }
                    .padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(line.text, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, lineHeight = 23.sp,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        status?.let { Text(it, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ShareAction("分享文本", selected.isNotEmpty() && !generating, Modifier.weight(1f)) {
                val text = selected.sorted().mapNotNull(lines::getOrNull).joinToString("\n") { it.text } + "\n——《${track.title}》 · ${track.artistText}"
                runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
                    .onSuccess { status = "歌词文本已复制，可粘贴分享" }.onFailure { status = it.message ?: "复制失败" }
            }
            ShareAction(if (generating) "生成中…" else "生成图片", selected.isNotEmpty() && !generating, Modifier.weight(1f)) {
                val chosen = selected.sorted().mapNotNull(lines::getOrNull)
                generating = true
                scope.launch {
                    try {
                        val chooser = JFileChooser().apply { selectedFile = File("${track.title.replace(Regex("[\\/:*?\"<>|]"), "_")}-歌词.png") }
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            withContext(Dispatchers.IO) { saveLyricShareImage(track, chosen, chooser.selectedFile) }
                            status = "歌词图片已保存"
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { status = error.message ?: "歌词图片生成失败" }
                    finally { generating = false }
                }
            }
        }
    }
}
}

@Composable
private fun ShareAction(text: String, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(50.dp).meloXLiquidButton(RoundedCornerShape(25.dp), enabled = enabled,
        surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(text, color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .35f),
            fontWeight = FontWeight.SemiBold)
    }
}

/** MeloXLyricShareImage: same 1080px canvas, artwork, palette, offsets and line budget. */
private suspend fun saveLyricShareImage(track: MusicTrack, lines: List<LyricLine>, destination: File) {
    require(lines.isNotEmpty()) { "请至少选择一行歌词" }
    val width = 1080
    val height = (340 + lines.size * 86 + 140).coerceAtMost(4_096)
    val palette = ArtworkDynamicPaletteProvider.paletteFor(track.artworkUrl)
    val cover = track.artworkUrl?.let { url -> runCatching {
        if (url.startsWith("http")) MeloXHttpClient.shared.newCall(Request.Builder().url(url).build()).execute().use {
            check(it.isSuccessful) { "Artwork HTTP ${it.code}" }
            it.body.byteStream().use(javax.imageio.ImageIO::read)
        } else javax.imageio.ImageIO.read(if (url.startsWith("file:")) File(java.net.URI(url)) else File(url))
    }.getOrNull() }
    val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
    image.createGraphics().let { graphics ->
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            when (MeloXSettingsRuntime.playerBackgroundMode) {
                MeloXPlayerBackgroundMode.FlowingLight, MeloXPlayerBackgroundMode.MeiMesh -> {
                    graphics.color = java.awt.Color(palette.average.toArgb(), true)
                    graphics.fillRect(0, 0, width, height)
                    palette.cells.ifEmpty { listOf(palette.average) }.forEachIndexed { index, color ->
                        graphics.color = java.awt.Color((color.toArgb() and 0x00FFFFFF) or (135 shl 24), true)
                        val x = width * ((index % 3) + .5f) / 3f
                        val y = height * ((index / 3) + .5f) / 3f
                        val radius = width * .55f
                        graphics.fill(java.awt.geom.Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2))
                    }
                }
                MeloXPlayerBackgroundMode.AppleLyrics -> {
                    graphics.color = java.awt.Color(palette.average.toArgb(), true)
                    graphics.fillRect(0, 0, width, height)
                    if (cover != null) {
                        val expanded = java.awt.image.BufferedImage(width + 360, height + 360, java.awt.image.BufferedImage.TYPE_INT_ARGB)
                        for (y in 0 until expanded.height) for (x in 0 until expanded.width) {
                            expanded.setRGB(x, y, cover.getRGB((x - 180).coerceIn(0, cover.width - 1), (y - 180).coerceIn(0, cover.height - 1)))
                        }
                        val transform = graphics.transform
                        graphics.rotate(Math.toRadians(-12.0), width / 2.0, height / 2.0)
                        graphics.composite = java.awt.AlphaComposite.SrcOver.derive(120f / 255f)
                        graphics.drawImage(expanded, -180, -180, null)
                        graphics.composite = java.awt.AlphaComposite.SrcOver
                        graphics.transform = transform
                    }
                }
                MeloXPlayerBackgroundMode.BlurredArtwork, MeloXPlayerBackgroundMode.CustomImage -> {
                    graphics.color = java.awt.Color(28, 28, 30)
                    graphics.fillRect(0, 0, width, height)
                    val custom = if (MeloXSettingsRuntime.playerBackgroundMode == MeloXPlayerBackgroundMode.CustomImage)
                        runCatching { melox.settings.MeloXCustomBackgroundStore().ownedImage(MeloXSettingsRuntime.customBackgroundPath)?.let(javax.imageio.ImageIO::read) }.getOrNull() else null
                    try { (custom ?: cover)?.let { drawShareCover(graphics, it, 0, 0, width, height, 145) } }
                    finally { custom?.flush() }
                }
            }
            graphics.paint = java.awt.GradientPaint(0f, 0f, java.awt.Color(255, 255, 255, 25),
                0f, height.toFloat(), java.awt.Color(0, 0, 0, 185))
            graphics.fillRect(0, 0, width, height)
            val coverRect = java.awt.geom.RoundRectangle2D.Float(72f, 70f, 220f, 220f, 68f, 68f)
            graphics.color = java.awt.Color(255, 255, 255, 55)
            graphics.fill(coverRect)
            if (cover != null) {
                val clip = graphics.clip
                graphics.clip(coverRect)
                drawShareCover(graphics, cover, 72, 70, 220, 220, 255)
                graphics.clip = clip
            }
            graphics.color = java.awt.Color.WHITE
            graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 48)
            graphics.drawString(track.title.ifBlank { "正在播放" }.take(24), 340, 155)
            graphics.font = java.awt.Font("SansSerif", java.awt.Font.PLAIN, 31)
            graphics.color = java.awt.Color(255, 255, 255, 180)
            graphics.drawString(track.artistText.take(34), 340, 215)
            graphics.drawString("MeloX Lyrics", 340, 270)
            graphics.font = java.awt.Font("SansSerif", java.awt.Font.BOLD, 40)
            graphics.color = java.awt.Color.WHITE
            lines.forEachIndexed { index, line -> graphics.drawString(line.text.take(36), 78, 340 + index * 86 + 55) }
        } finally { graphics.dispose() }
    }
    check(javax.imageio.ImageIO.write(image, "png", destination)) { "PNG 编码失败" }
}

private fun drawShareCover(graphics: java.awt.Graphics2D, cover: java.awt.image.BufferedImage,
    x: Int, y: Int, width: Int, height: Int, alpha: Int) {
    val scale = maxOf(width.toFloat() / cover.width, height.toFloat() / cover.height)
    val sourceWidth = width / scale
    val sourceHeight = height / scale
    val left = (cover.width - sourceWidth) / 2f
    val top = (cover.height - sourceHeight) / 2f
    graphics.composite = java.awt.AlphaComposite.SrcOver.derive(alpha / 255f)
    graphics.drawImage(cover, x, y, x + width, y + height,
        left.toInt(), top.toInt(), (left + sourceWidth).toInt(), (top + sourceHeight).toInt(), null)
    graphics.composite = java.awt.AlphaComposite.SrcOver
}
