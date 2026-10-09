package melox.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import melox.lyrics.LyricsDocument
import melox.lyrics.MeloXLyricPresentation
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsRuntime
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class MeloXLyricCascadeState(count: Int) {
    var focusIndex by mutableIntStateOf(-1)
    var distance by mutableFloatStateOf(0f)
    var initial by mutableStateOf<Map<Int, Float>>(emptyMap())
    var destinations by mutableStateOf<Map<Int, Float>>(emptyMap())
    var reanchorAfterBrowse = false
    val scrollProgress = Animatable(1f)
    val lineProgress = List(count) { Animatable(1f) }
    var settledOffset: (Int, Int) -> Float = { _, _ -> 0f }

    fun offset(index: Int): Float {
        val from = initial[index] ?: return settledOffset(index, focusIndex)
        val destination = destinations[index] ?: 0f
        return from + distance * scrollProgress.value - (distance + from - destination) * lineProgress[index].value
    }

    suspend fun clear(focus: Int) {
        focusIndex = focus
        initial = emptyMap(); destinations = emptyMap(); distance = 0f
        scrollProgress.snapTo(1f)
    }
}

/** Independent delayed springs carry interrupted offsets; no disappearing lyric rows. */
@Composable
internal fun rememberMeloXLyricCascade(
    document: LyricsDocument?, list: LazyListState, rowHeights: Map<Int, Int>, target: Int,
    browsing: Boolean, interactive: Boolean, viewportHeight: Float, focusPosition: Float, fontScale: Float,
): MeloXLyricCascadeState {
    val lines = document?.lines.orEmpty()
    val state = remember(document) { MeloXLyricCascadeState(lines.size) }
    val density = LocalDensity.current
    val primaryHeight = with(density) { (40.8f * fontScale).sp.toPx() }
    val romanSize = max(34f * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
    val translationSize = max(34f * fontScale * MeloXSettingsRuntime.lyricTranslationFontScale, 13f)
    val showRoman = MeloXSettingsRuntime.showLyricRomanization
    val showTranslation = MeloXSettingsRuntime.showLyricTranslation
    val scale = MeloXSettingsRuntime.lyricFocusScale
    fun estimatedHeight(index: Int): Float {
        rowHeights[index]?.let { return it.toFloat() }
        val line = lines.getOrNull(index)
        var height = primaryHeight
        if (showRoman && !line?.romanization.isNullOrBlank()) height += with(density) { (romanSize * 1.2f).sp.toPx() + 4.dp.toPx() }
        if (showTranslation && !line?.translation.isNullOrBlank()) height += with(density) { (translationSize * 1.2f).sp.toPx() + 4.dp.toPx() }
        return height
    }
    state.settledOffset = { index, focus ->
        if (focus !in lines.indices || index <= focus) 0f else max(estimatedHeight(focus) * (scale - 1f), 0f)
    }
    val follow = MeloXSettingsRuntime.lyricAutoFollowEnabled
    val reduceMotion = MeloXSettingsRuntime.lyricReduceMotion
    LaunchedEffect(document, target, browsing, interactive, viewportHeight, focusPosition, fontScale, follow, reduceMotion) {
        if (!interactive || viewportHeight <= 0) return@LaunchedEffect
        if (browsing) {
            state.reanchorAfterBrowse = true
            state.clear(state.focusIndex)
            return@LaunchedEffect
        }
        if (target !in lines.indices) {
            list.scrollToItem(0)
            return@LaunchedEffect
        }
        val previous = state.focusIndex
        val desiredTop = viewportHeight * focusPosition - estimatedHeight(target) * focusPosition
        val targetOffset = -desiredTop.roundToInt()
        if (previous !in lines.indices) {
            list.scrollToItem(target + 1, targetOffset)
            state.clear(target)
            return@LaunchedEffect
        }
        val reanchor = state.reanchorAfterBrowse
        state.reanchorAfterBrowse = false
        if (previous == target && !reanchor) return@LaunchedEffect
        if (!follow) {
            state.focusIndex = target
            return@LaunchedEffect
        }
        if (reduceMotion) {
            state.clear(target)
            list.scrollToItem(target + 1, targetOffset)
            return@LaunchedEffect
        }
        fun activation(index: Int): Long = lines[index].syllables.minOfOrNull { it.startTimeMs } ?: lines[index].timeMs
        val available = if (target + 1 < lines.size) activation(target + 1) - activation(target) else null
        val baseDuration = if (available == null || available <= 0L) 300 else (available * .30f).coerceIn(50f, 240f).roundToInt()
        val fullDuration = max(baseDuration.toFloat(), MeloXSettingsRuntime.lyricCascadeDurationMs)
        val remaining = if (target + 1 < lines.size) max((activation(target + 1) - AudioPlayer.positionMs.value -
            MeloXSettingsRuntime.lyricAdvanceMs).toFloat(), 0f) else null
        val duration = if (remaining == null) fullDuration else if (remaining < MeloXSettingsRuntime.lyricSnapThresholdMs) 0f
            else min(fullDuration, remaining)
        val targetItem = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == target + 1 }
        if (duration <= 0f || targetItem == null) {
            state.clear(target)
            if (duration <= 0f) list.scrollToItem(target + 1, targetOffset) else list.animateScrollToItem(target + 1, targetOffset)
            return@LaunchedEffect
        }
        val overlapping = previous < target && (previous until target).all { activation(it + 1) - activation(it) <= 280L }
        val adjacent = (target - previous).coerceAtLeast(1) == 1 || overlapping
        if (!adjacent || reanchor) {
            state.clear(target)
            list.animateScrollToItem(target + 1, targetOffset)
            return@LaunchedEffect
        }
        val distance = targetItem.offset - desiredTop
        if (abs(distance) <= .5f) {
            state.clear(target)
            return@LaunchedEffect
        }
        val visible = list.layoutInfo.visibleItemsInfo.mapNotNull { (it.index - 1).takeIf(lines.indices::contains) }
        val first = min(visible.minOrNull() ?: max(target - 1, 0), max(target - 1, 0))
        val last = min((visible.maxOrNull() ?: target) + 4, lines.lastIndex)
        val moving = first..last
        val carried = moving.associateWith(state::offset)
        state.distance = distance
        state.initial = carried
        state.destinations = moving.associateWith { state.settledOffset(it, target) }
        state.scrollProgress.snapTo(0f)
        moving.forEach { state.lineProgress[it].snapTo(0f) }
        state.focusIndex = target
        val firstChasing = max(target - 1, 0)
        val maximumOrder = max(last - firstChasing, 0)
        val timings = MeloXLyricPresentation.cascadeTimings(maximumOrder, duration)
        val slowest = timings.first().durationMs
        coroutineScope {
            launch {
                var previousProgress = 0f
                list.scroll {
                    state.scrollProgress.animateTo(1f, tween(duration.roundToInt(), easing = Easing { MeloXLyricPresentation.smoothStep(it) })) {
                        scrollBy((value - previousProgress) * distance)
                        previousProgress = value
                    }
                }
            }
            moving.forEach { index ->
                val movementOrder = max(index - target, 0)
                val chaseOrder = (index - firstChasing).coerceAtLeast(0)
                val movementTiming = timings[min(movementOrder, timings.lastIndex)]
                val chaseTiming = timings[min(chaseOrder, timings.lastIndex)]
                val lineDuration = slowest + (chaseTiming.durationMs - slowest) * MeloXSettingsRuntime.lyricCascadeChaseSpeedGradient
                val bounce = MeloXLyricPresentation.cascadeBounce(chaseOrder, maximumOrder)
                launch {
                    if (movementTiming.delayMs > 0) delay(movementTiming.delayMs.toLong())
                    state.lineProgress[index].animateTo(1f, tween(max(lineDuration, 1f).roundToInt(),
                        easing = Easing { MeloXLyricPresentation.springFraction(it, bounce) }))
                }
            }
        }
        state.clear(target)
    }
    return state
}
