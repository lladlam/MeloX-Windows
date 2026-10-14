package melox.lyrics

import melox.settings.MeloXLyricsGroupingMode
import melox.settings.MeloXLyricAnnotationDisplayMode
import melox.settings.MeloXSettingsRuntime
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Presentation equations from pinned 94e17c9 MeloXIOSLyricsPanel. No decoder/UI clock. */
object MeloXLyricPresentation {
    const val FontSize = 34f
    const val LineHeight = 40.8f
    const val FocusColorDurationMs = 120
    const val LiftContinuationMs = 320f
    const val GlowTailMs = 550f
    const val LongToneMaxScale = 1.0625f

    data class GlyphTiming(
        val textOffset: Int, val start: Float, val end: Float,
        val liftStart: Float, val liftEnd: Float,
        val syllableStart: Float, val syllableEnd: Float,
        val characterIndex: Int, val characterCount: Int,
        val wordStart: Float, val wordEnd: Float,
        val wordCharacterIndex: Int, val wordCharacterCount: Int,
        val usesWordTimingForLongTone: Boolean,
        val longToneStart: Float, val longToneDuration: Float,
        val longToneCharacterIndex: Int, val longToneCharacterCount: Int,
        val isLongTone: Boolean, val expansionAmount: Float, val glowAmount: Float,
    )

    data class GlyphVisual(
        val reveal: Float, val liftPx: Float, val scale: Float, val glow: Float,
        val shakeXPx: Float, val shakeYPx: Float,
    )

    val InactiveGlyph = GlyphVisual(0f, 0f, 1f, 0f, 0f, 0f)

    fun annotationVisible(enabled: Boolean, text: String?, mode: MeloXLyricAnnotationDisplayMode, active: Boolean): Boolean =
        enabled && !text.isNullOrBlank() && (mode == MeloXLyricAnnotationDisplayMode.AllLines || active)

    fun glyphTimings(
        line: LyricLine,
        liftMode: MeloXLyricsGroupingMode,
        longToneDetectionMode: MeloXLyricsGroupingMode,
        longToneThresholdMs: Int,
    ): List<GlyphTiming?> {
        val result = MutableList<GlyphTiming?>(line.text.length) { null }
        var searchFrom = 0
        for (syllable in line.syllables) {
            if (syllable.text.isEmpty()) continue
            val located = line.text.indexOf(syllable.text, startIndex = searchFrom)
            val startIndex = if (located >= 0) located else searchFrom.coerceAtMost(line.text.length)
            val count = syllable.text.length.coerceAtMost(line.text.length - startIndex)
            if (count <= 0) continue
            searchFrom = startIndex + count
            val duration = max(syllable.endTimeMs - syllable.startTimeMs, 0L).toFloat()
            val characterDuration = duration / count
            for (local in 0 until count) {
                val offset = startIndex + local
                val start = syllable.startTimeMs + characterDuration * local
                val end = if (local == count - 1) max(syllable.endTimeMs.toFloat(), start) else start + characterDuration
                result[offset] = GlyphTiming(offset, start, end, start, end + LiftContinuationMs,
                    syllable.startTimeMs.toFloat(), syllable.endTimeMs.toFloat(), local, count,
                    start, end, 0, 1, false, syllable.startTimeMs.toFloat(), duration, local, count,
                    false, 0f, 0f)
            }
        }
        wordBlocks(line.text).forEach { block ->
            val timedOffsets = (block.first until block.second).filter { result.getOrNull(it) != null && !line.text[it].isWhitespace() }
            val wordStart = timedOffsets.minOfOrNull { result[it]!!.start } ?: return@forEach
            val wordEnd = timedOffsets.maxOfOrNull { result[it]!!.end } ?: return@forEach
            val positions = timedOffsets.withIndex().associate { it.value to it.index }
            val latinWord = timedOffsets.size > 1 && timedOffsets.all { line.text[it] in 'A'..'Z' || line.text[it] in 'a'..'z' }
            for (offset in block.first until block.second) {
                result[offset] = result.getOrNull(offset)?.copy(wordStart = wordStart, wordEnd = wordEnd,
                    wordCharacterIndex = positions[offset] ?: max(timedOffsets.size - 1, 0),
                    wordCharacterCount = max(timedOffsets.size, 1), usesWordTimingForLongTone = latinWord)
            }
        }
        return result.map { timing -> timing?.let {
            val liftStart = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordStart else it.start
            val liftEnd = if (liftMode == MeloXLyricsGroupingMode.Word) it.wordEnd else it.end
            val usesWord = it.usesWordTimingForLongTone || longToneDetectionMode == MeloXLyricsGroupingMode.Word
            val toneStart = if (usesWord) it.wordStart else it.syllableStart
            val toneEnd = if (usesWord) it.wordEnd else it.syllableEnd
            val duration = max(toneEnd - toneStart, 0f)
            val longTone = !line.text[it.textOffset].isWhitespace() && duration >= longToneThresholdMs
            val emphasis = smootherStep((duration - longToneThresholdMs) / (2800f - longToneThresholdMs))
            it.copy(liftStart = liftStart, liftEnd = liftEnd + LiftContinuationMs,
                longToneStart = toneStart, longToneDuration = duration,
                longToneCharacterIndex = if (usesWord) it.wordCharacterIndex else it.characterIndex,
                longToneCharacterCount = if (usesWord) it.wordCharacterCount else it.characterCount,
                isLongTone = longTone, expansionAmount = if (longTone) .7f + .3f * emphasis else 0f,
                glowAmount = if (longTone) .32f + .38f * emphasis else 0f)
        } }
    }

    fun glyphVisual(timing: GlyphTiming, playbackTimeMs: Long, density: Float, reduceMotion: Boolean, fontScale: Float): GlyphVisual {
        val duration = max(timing.end - timing.start, 0f)
        val raw = when {
            playbackTimeMs < timing.start -> 0f
            playbackTimeMs >= timing.end || duration <= 0f -> 1f
            else -> ((playbackTimeMs - timing.start) / duration).coerceIn(0f, 1f)
        }
        val reveal = highlightReveal(playbackTimeMs.toFloat(), timing.start, timing.end, raw, timing.isLongTone)
        val lift = if (playbackTimeMs <= timing.liftStart) 0f else smootherStep(
            (playbackTimeMs - timing.liftStart) / max(timing.liftEnd - timing.liftStart, 1f))
        val rise = if (reduceMotion) 0f else min(max(FontSize * fontScale * .1f, 1.5f), 6f) * density
        val envelope = if (timing.isLongTone) longToneEnvelope(playbackTimeMs.toFloat(), timing.longToneStart,
            timing.longToneDuration, timing.longToneCharacterIndex, timing.longToneCharacterCount) else 0f
        val longScale = if (reduceMotion) 1f else 1f + (LongToneMaxScale - 1f) * envelope *
            timing.expansionAmount * MeloXSettingsRuntime.lyricLongToneStrength
        val bounce = if (reduceMotion || timing.isLongTone || !MeloXSettingsRuntime.lyricWordBounceEnabled) 1f else when {
            raw > 0f && raw < .5f -> 1f + .2f * (raw / .5f)
            raw >= .5f && raw < 1f -> 1.2f - .2f * ((raw - .5f) / .5f)
            else -> 1f
        }
        val glow = if (reduceMotion || !MeloXSettingsRuntime.lyricGlowEnabled ||
            MeloXSettingsRuntime.lyricGlowLongTonesOnly && !timing.isLongTone) 0f else {
            if (timing.isLongTone) envelope * timing.glowAmount else ordinaryGlow(playbackTimeMs.toFloat(), timing.end, raw)
        }
        val shake = if (reduceMotion || !timing.isLongTone || raw <= 0f || raw >= 1f) 0f else
            min(.58f * density, FontSize * fontScale * .013f * density) * envelope *
                timing.expansionAmount * MeloXSettingsRuntime.lyricLongToneStrength
        val phase = (playbackTimeMs - timing.longToneStart).coerceAtLeast(0f)
        return GlyphVisual(reveal, rise * lift, longScale * bounce, glow,
            sin(phase * .016f + timing.longToneCharacterIndex * 1.73f) * shake,
            sin(phase * .021f + timing.longToneCharacterIndex * .91f) * shake * .42f)
    }

    private fun ordinaryGlow(time: Float, end: Float, raw: Float): Float {
        if (time <= end) return smootherStep(raw / .24f) * (.82f + .18f * sin(Math.PI.toFloat() * raw)) * .55f
        return (1f - smootherStep(((time - end) / GlowTailMs).coerceIn(0f, 1f))) * .82f * .55f
    }

    private fun wordBlocks(text: String): List<Pair<Int, Int>> {
        if (text.isEmpty()) return emptyList()
        val tokens = mutableListOf<Pair<Int, Int>>()
        var phraseStart = -1
        for (index in 0..text.length) {
            val whitespace = index == text.length || text[index].isWhitespace()
            if (!whitespace && phraseStart < 0) phraseStart = index
            if (whitespace && phraseStart >= 0) {
                val iterator = BreakIterator.getWordInstance(Locale.ROOT).apply { setText(text.substring(phraseStart, index)) }
                var start = iterator.first()
                var end = iterator.next()
                var found = false
                while (end != BreakIterator.DONE) {
                    if (text.substring(phraseStart + start, phraseStart + end).any { it.isLetterOrDigit() }) {
                        tokens += phraseStart + start to phraseStart + end
                        found = true
                    }
                    start = end; end = iterator.next()
                }
                if (!found) tokens += phraseStart to index
                phraseStart = -1
            }
        }
        if (tokens.isEmpty()) return listOf(0 to text.length)
        val sorted = tokens.sortedBy { it.first }
        return buildList {
            sorted.forEachIndexed { index, token ->
                val start = if (index == 0) 0 else sorted[index - 1].second
                val end = if (index + 1 < sorted.size) sorted[index + 1].first else text.length
                if (start < end) add(start to end)
            }
        }
    }

    fun timingEffectsStrength(line: LyricLine, playbackTimeMs: Long, focusProgress: Float): Float {
        val focus = focusProgress.coerceIn(0f, 1f)
        if (focus <= 0f || line.syllables.isEmpty()) return 0f
        return min(focus, smootherStep((playbackTimeMs - line.syllables.minOf { it.startTimeMs }).toFloat() / FocusColorDurationMs))
    }

    private fun highlightReveal(time: Float, start: Float, end: Float, raw: Float, longTone: Boolean): Float {
        if (!longTone || end - start <= 460f) return smootherStep(raw)
        return (.82f * smootherStep(max(time - start, 0f) / 300f) + .08f * raw +
            .10f * smootherStep((time - (end - 160f)) / 160f)).coerceIn(0f, 1f)
    }

    private fun longToneEnvelope(time: Float, start: Float, duration: Float, index: Int, count: Int): Float {
        val stagger = if (count > 1) duration * .55f * index / (count - 1) else 0f
        val progress = ((time - start - stagger) / max(duration, 1000f)).coerceIn(0f, 1f)
        return if (progress <= .5f) smootherStep(progress / .5f) else 1f - smootherStep((progress - .5f) / .5f)
    }

    data class Interlude(val startTimeMs: Long, val countdownEndTimeMs: Long, val followingLyricTimeMs: Long, val followingLyricIndex: Int)

    fun interludes(lines: List<LyricLine>): List<Interlude> = buildList {
        lines.forEachIndexed { index, line ->
            val previous = lines.getOrNull(index - 1)
            val start = if (previous == null) 0L else max(previous.timeMs + (previous.durationMs ?: 0L),
                previous.syllables.maxOfOrNull { it.endTimeMs } ?: previous.timeMs)
            val end = (line.timeMs - 250L).coerceAtLeast(start)
            if (end - start >= 4_000L) add(Interlude(start, end, line.timeMs, index))
        }
    }

    data class CascadeTiming(val delayMs: Float, val durationMs: Float)

    fun cascadeTimings(maximumLineOrder: Int, animationDurationMs: Float): List<CascadeTiming> {
        val completion = animationDurationMs * MeloXSettingsRuntime.lyricCascadeCatchUpRatio
        val minimum = min(180f, animationDurationMs * .5f)
        return (0..maximumLineOrder.coerceAtLeast(0)).map { order ->
            if (order == 0) CascadeTiming(0f, animationDurationMs) else {
                val delay = MeloXSettingsRuntime.lyricCascadeFollowingDelayMs + order * MeloXSettingsRuntime.lyricCascadeDelayMs +
                    order.toFloat() * (order - 1) / 2f * MeloXSettingsRuntime.lyricCascadeDelayIncreaseMs
                CascadeTiming(delay, max(completion - delay, minimum))
            }
        }
    }

    fun cascadeBounce(chaseOrder: Int, maximumChaseOrder: Int): Float {
        if (!MeloXSettingsRuntime.lyricCascadeBounceEnabled) return 0f
        val normalized = (chaseOrder.coerceIn(0, maximumChaseOrder) + 1).toFloat() / max(maximumChaseOrder + 1, 1)
        return MeloXSettingsRuntime.lyricCascadeBounce * (1f - (1f - normalized) * MeloXSettingsRuntime.lyricCascadeBounceGradient)
    }

    fun springFraction(fraction: Float, bounce: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        if (bounce <= .0001f) return smoothStep(t)
        val damping = (1f - bounce.coerceIn(0f, .95f) * .72f).coerceIn(.18f, .999f)
        val omega = 9.5f
        val root = sqrt(max(1f - damping * damping, .0001f))
        val damped = omega * root
        fun sample(time: Float) = 1f - exp((-damping * omega * time).toDouble()).toFloat() *
            (kotlin.math.cos((damped * time).toDouble()).toFloat() + damping / root * sin((damped * time).toDouble()).toFloat())
        val end = sample(1f)
        return if (abs(end) > .0001f) sample(t) / end else sample(t)
    }

    fun smoothStep(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
    fun smootherStep(value: Float): Float = value.coerceIn(0f, 1f).let { it * it * it * (it * (it * 6f - 15f) + 10f) }
}
