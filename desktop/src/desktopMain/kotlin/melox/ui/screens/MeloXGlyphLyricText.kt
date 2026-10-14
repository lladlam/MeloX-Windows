package melox.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.lyrics.*
import melox.settings.MeloXLyricsRenderingQuality
import melox.settings.MeloXSettingsRuntime
import melox.ui.theme.MeloXLanTingProFontFamily
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private data class DrawableLyricGlyph(
    val offset: Int, val bounds: Rect, val region: org.jetbrains.skia.Rect,
)

/** Pinned glyph masks/lift/glow. Only the Canvas subscribes to the high-frequency clock. */
@Composable
internal fun MeloXGlyphLyricText(
    line: LyricLine, playbackTimeProvider: () -> Long, supportsTimedLyrics: Boolean,
    fontScale: Float, reduceMotion: Boolean, timingEffectsStrength: Float,
    unplayedAlpha: Float = MeloXSettingsRuntime.lyricInactiveOpacity,
    followOwnTiming: Boolean = false, renderingQuality: MeloXLyricsRenderingQuality,
    softBlurDp: Float = 0f, modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val flipped = line.agent?.alignment == LyricAgentAlignment.Flipped
    val weight = MeloXSettingsRuntime.lyricFontWeight.composeWeight
    // This composable owns one row layout already via remember below. Keeping
    // another 64 historical paragraphs per live row multiplies native text
    // retention when the window is resized or lyric settings change.
    val measurer = rememberTextMeasurer(cacheSize = 0)
    BoxWithConstraints(modifier) {
        val width = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val style = TextStyle(color = Color.White, fontFamily = MeloXLanTingProFontFamily,
            fontSize = (34f * fontScale).sp, lineHeight = (40.8f * fontScale).sp,
            fontWeight = weight, textAlign = if (flipped) TextAlign.End else TextAlign.Start)
        val layout = remember(line.text, width, style, density, isRtl) {
            measurer.measure(AnnotatedString(line.text), style, constraints = Constraints(minWidth = width, maxWidth = width), softWrap = true)
        }
        val height = with(density) { layout.size.height.toDp() }
        // Measure and rasterize through the SAME shaped Compose paragraph. Independent
        // native drawString differed from the shaped variable-weight paragraph on
        // Windows, especially at Heavy. Its geometry must not be independently resolved.
        // One immutable row mask per layout; no per-frame layout or per-glyph surface.
        val rowMask = remember(layout, density, isRtl) {
            Surface.makeRasterN32Premul(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1)).use { surface ->
                surface.canvas.clear(0)
                CanvasDrawScope().draw(density, if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                    surface.canvas.asComposeCanvas(), Size(layout.size.width.toFloat(), layout.size.height.toFloat())) {
                    drawText(layout, color = Color.White)
                }
                surface.makeImageSnapshot()
            }
        }
        val paint = remember { Paint().apply { color = 0xFFFFFFFF.toInt(); isAntiAlias = true } }
        val blur = remember(softBlurDp, density) {
            with(density) { softBlurDp.dp.toPx() }.takeIf { it > .05f }?.let {
                ImageFilter.makeBlur(it, it, FilterTileMode.DECAL)
            }
        }
        val glowRadius = with(density) {
            (style.fontSize.toPx() * if (renderingQuality == MeloXLyricsRenderingQuality.High) .30f else .18f).coerceAtLeast(3.dp.toPx())
        }
        val glowMask = remember(glowRadius) { ImageFilter.makeBlur(glowRadius, glowRadius, FilterTileMode.DECAL) }
        DisposableEffect(rowMask) { onDispose { rowMask.close() } }
        DisposableEffect(paint) { onDispose { paint.close() } }
        DisposableEffect(blur) { onDispose { blur?.close() } }
        DisposableEffect(glowMask) { onDispose { glowMask.close() } }
        val glyphs = remember(layout, line.text) {
            val measured = buildList {
                var offset = 0
                while (offset < line.text.length) {
                    val character = line.text[offset]
                    val count = if (Character.isHighSurrogate(character) && offset + 1 < line.text.length &&
                        Character.isLowSurrogate(line.text[offset + 1])) 2 else 1
                    if (character != '\n' && character != '\r' && !Character.isLowSurrogate(character)) {
                        val bounds = runCatching { layout.getBoundingBox(offset) }.getOrNull()?.takeIf {
                            it.width.isFinite() && it.height.isFinite() && it.width > 0f && it.height > 0f
                        }
                        if (bounds != null) {
                            add(offset to bounds)
                        }
                    }
                    offset += count
                }
            }
            measured.groupBy { layout.getLineForOffset(it.first) }.flatMap { (row, entries) ->
                // Fractional strict image crops cut antialiased pixels at every
                // advance on Windows. Pixel-aligned partitions tile the complete
                // shaped row, including the last glyph's right-side ink overhang.
                // Bounds remain unchanged for the source timing/pivot/reveal math.
                val ordered = entries.sortedBy { it.second.left }.distinctBy { it.second.left }
                ordered.mapIndexedNotNull { index, (offset, bounds) ->
                    val left = if (index == 0) 0f else bounds.left.roundToInt().toFloat()
                    val right = if (index == ordered.lastIndex) layout.size.width.toFloat()
                        else ordered[index + 1].second.left.roundToInt().toFloat()
                    if (right <= left) null else DrawableLyricGlyph(offset, bounds,
                        org.jetbrains.skia.Rect.makeLTRB(left, layout.getLineTop(row).roundToInt().toFloat(),
                            right, layout.getLineBottom(row).roundToInt().toFloat()))
                }
            }
        }
        val liftMode = MeloXSettingsRuntime.lyricLiftMode
        val longToneMode = MeloXSettingsRuntime.lyricLongToneDetectionMode
        val threshold = MeloXSettingsRuntime.lyricLongToneThresholdMs
        val timings = remember(line, liftMode, longToneMode, threshold) {
            MeloXLyricPresentation.glyphTimings(line, liftMode, longToneMode, threshold)
        }
        val animates = supportsTimedLyrics && timingEffectsStrength > .001f && line.text.isNotEmpty()
        Canvas(Modifier.fillMaxWidth().height(height).testTag(if (animates) "lyric-timed-text" else "lyric-static-text")
            .semantics { text = AnnotatedString(line.text) }) {
            fun drawGlyph(glyph: DrawableLyricGlyph, alpha: Float, glow: Boolean = false) {
                paint.alpha = (alpha.coerceIn(0f, 1f) * 255f).roundToInt()
                paint.imageFilter = if (glow) glowMask else blur
                drawContext.canvas.nativeCanvas.drawImageRect(rowMask, glyph.region, glyph.region, paint, true)
                paint.imageFilter = null
            }
            if (!animates) {
                if (blur == null) drawText(layout, color = Color.White)
                else glyphs.forEach { drawGlyph(it, 1f) }
                return@Canvas
            }
            val time = playbackTimeProvider()
            val strength = if (followOwnTiming) timingEffectsStrength.coerceIn(0f, 1f) else
                MeloXLyricPresentation.timingEffectsStrength(line, time, timingEffectsStrength)
            val inactive = unplayedAlpha.coerceIn(0f, 1f)
            if (strength <= .0001f) {
                drawText(layout, color = Color.White.copy(alpha = inactive))
                return@Canvas
            }
            if ((renderingQuality != MeloXLyricsRenderingQuality.High || reduceMotion) &&
                (!MeloXSettingsRuntime.lyricWordByWordEnabled || reduceMotion)) {
                drawText(layout, color = Color.White.copy(alpha = inactive))
                val first = line.syllables.minOfOrNull { it.startTimeMs } ?: line.timeMs
                val last = line.syllables.maxOfOrNull { it.endTimeMs } ?: (line.timeMs + (line.durationMs ?: 2_000L))
                val reveal = ((time - first).toFloat() / (last - first).coerceAtLeast(1L)).coerceIn(0f, 1f)
                if (reveal > 0f) {
                    if (isRtl) clipRect(left = size.width * (1f - reveal)) { drawText(layout, color = Color.White) }
                    else clipRect(right = size.width * reveal) { drawText(layout, color = Color.White) }
                }
                return@Canvas
            }
            for (glyph in glyphs) {
                val bounds = glyph.bounds
                val fx = timings.getOrNull(glyph.offset)?.let {
                    MeloXLyricPresentation.glyphVisual(it, time, density.density, reduceMotion, fontScale)
                } ?: MeloXLyricPresentation.InactiveGlyph
                withTransform({
                    translate(fx.shakeXPx * strength, -fx.liftPx * strength + fx.shakeYPx * strength)
                    val scale = 1f + (fx.scale - 1f) * strength
                    scale(scale, scale, bounds.center)
                }) {
                    drawGlyph(glyph, inactive)
                    val reveal = fx.reveal.coerceIn(0f, 1f)
                    val glow = fx.glow * strength * MeloXSettingsRuntime.lyricGlowStrength
                    if (reveal > 0f && glow > .001f && renderingQuality != MeloXLyricsRenderingQuality.Low && !reduceMotion) {
                        val front = if (isRtl) bounds.right - bounds.width * reveal else bounds.left + bounds.width * reveal
                        clipRect(left = if (isRtl) front - glowRadius else bounds.left - glowRadius,
                            top = bounds.top - glowRadius, right = if (isRtl) bounds.right + glowRadius else front + glowRadius,
                            bottom = bounds.bottom + glowRadius) { drawGlyph(glyph, glow.coerceIn(0f, 1f) * .648f, glow = true) }
                    }
                    clipRect(bounds.left, bounds.top, bounds.right, bounds.bottom) {
                        if (reveal <= 0f) return@clipRect
                        val feather = max(bounds.width * MeloXSettingsRuntime.lyricHighlightGradientWidth, 1.5f * density.density)
                        val front = if (isRtl) bounds.right + feather - (bounds.width + feather) * reveal
                            else bounds.left - feather + (bounds.width + feather) * reveal
                        val solidLeft = if (isRtl) max(front, bounds.left) else bounds.left
                        val solidRight = if (isRtl) bounds.right else min(front, bounds.right)
                        if (solidRight > solidLeft) clipRect(solidLeft, bounds.top, solidRight, bounds.bottom) { drawGlyph(glyph, 1f) }
                        val stops = when (renderingQuality) {
                            MeloXLyricsRenderingQuality.Low -> 1
                            MeloXLyricsRenderingQuality.Balanced -> 2
                            MeloXLyricsRenderingQuality.High -> 3
                        }
                        for (step in 0 until stops) {
                            val a = step.toFloat() / stops
                            val b = (step + 1f) / stops
                            val mid = (a + b) * .5f
                            val mask = (1f - mid) * (1f - MeloXSettingsRuntime.lyricHighlightGradientReduction * mid)
                            val alpha = mask + (1f - mask) * glow.coerceIn(0f, 1f) * .14f
                            val left = if (isRtl) max(front - feather * b, bounds.left) else max(front + feather * a, bounds.left)
                            val right = if (isRtl) min(front - feather * a, bounds.right) else min(front + feather * b, bounds.right)
                            if (right > left) clipRect(left, bounds.top, right, bounds.bottom) { drawGlyph(glyph, alpha) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MeloXTimedAccompaniment(vocal: LyricAccompaniment, time: () -> Long,
    fontScale: Float, reduceMotion: Boolean, quality: MeloXLyricsRenderingQuality, blur: Float) {
    val end = vocal.durationMs?.let { vocal.timeMs + it } ?: vocal.syllables.maxOfOrNull { it.endTimeMs } ?: vocal.timeMs
    val visible by remember(vocal, time) { derivedStateOf { time() in (vocal.timeMs - 600L)..(end + 600L) } }
    AnimatedVisibility(visible, enter = fadeIn(tween(600)) + slideInVertically(tween(600)) { it / 2 } + expandVertically(tween(600)),
        exit = fadeOut(tween(600)) + slideOutVertically(tween(600)) { it / 2 } + shrinkVertically(tween(600))) {
        MeloXGlyphLyricText(LyricLine(vocal.timeMs, vocal.durationMs, vocal.text, vocal.syllables, agent = vocal.agent),
            time, vocal.syllables.isNotEmpty(), fontScale * .68f, reduceMotion, 1f,
            followOwnTiming = true, renderingQuality = quality, softBlurDp = blur,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("lyric-accompaniment"))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeloXRubyLyricText(line: LyricLine, time: () -> Long, timed: Boolean,
    fontScale: Float, quality: MeloXLyricsRenderingQuality, blur: Float, modifier: Modifier = Modifier) {
    if (MeloXSettingsRuntime.lyricWordByWordEnabled) {
        Column(modifier) {
            MeloXGlyphLyricText(line, time, timed, fontScale, false, 1f, renderingQuality = quality, softBlurDp = blur,
                modifier = Modifier.fillMaxWidth())
            if (!line.romanization.isNullOrBlank()) {
                val size = max(34f * fontScale * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
                Text(line.romanization.orEmpty(), color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricRomanizationOpacity),
                    fontFamily = MeloXLanTingProFontFamily, fontSize = size.sp, lineHeight = (size * 1.2f).sp,
                    fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight,
                    textAlign = if (line.agent?.alignment == LyricAgentAlignment.Flipped) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        return
    }
    val units = remember(line) { LyricRomanizationAligner.units(line) }
    if (units.isEmpty()) {
        MeloXGlyphLyricText(line, time, timed, fontScale, true, 1f, renderingQuality = quality, softBlurDp = blur, modifier = modifier)
        return
    }
    val flipped = line.agent?.alignment == LyricAgentAlignment.Flipped
    val primarySize = 34f * fontScale
    val rubySize = max(primarySize * MeloXSettingsRuntime.lyricRomanizationFontScale, 13f)
    FlowRow(modifier.testTag("lyric-ruby"), horizontalArrangement = Arrangement.spacedBy(2.dp,
        if (flipped) Alignment.End else Alignment.Start), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        units.forEach { unit ->
            val start = unit.originalSyllables.minOfOrNull { it.startTimeMs } ?: 0L
            val end = unit.originalSyllables.maxOfOrNull { it.endTimeMs }?.coerceAtLeast(start + 1) ?: 1L
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(unit.originalText, color = Color.White, fontFamily = MeloXLanTingProFontFamily,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (!timed || unit.originalSyllables.isEmpty()) 1f else .3f +
                            ((time() - start).toFloat() / (end - start)).coerceIn(0f, 1f) * .7f
                    }, fontSize = primarySize.sp, lineHeight = (40.8f * fontScale).sp,
                    fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight, maxLines = 1,
                    style = TextStyle(shadow = if (blur > .05f) Shadow(Color.White.copy(alpha = .48f), Offset.Zero, blur) else null))
                val romanization = unit.romanizationText
                if (!romanization.isNullOrBlank()) {
                    val originalCount = unit.originalText.codePointCount(0, unit.originalText.length).coerceAtLeast(1)
                    val rubyCount = romanization.codePointCount(0, romanization.length).coerceAtLeast(1)
                    Text(romanization, color = Color.White.copy(alpha = MeloXSettingsRuntime.lyricRomanizationOpacity),
                        fontFamily = MeloXLanTingProFontFamily, fontSize = (rubySize * (originalCount * 1.85f / rubyCount).coerceIn(.68f, 1f)).sp,
                        lineHeight = (rubySize * 1.2f).sp, fontWeight = MeloXSettingsRuntime.lyricFontWeight.composeWeight, maxLines = 1,
                        style = TextStyle(shadow = if (blur > .05f) Shadow(Color.White.copy(alpha = .42f), Offset.Zero, blur) else null))
                }
            }
        }
    }
}

@Composable
internal fun MeloXLyricInterludeCountdown(interlude: MeloXLyricPresentation.Interlude, time: () -> Long,
    reduceMotion: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth(.28f).height(48.dp).testTag("lyric-interlude")) {
        val now = time()
        if (now < interlude.startTimeMs) return@Canvas
        val duration = (interlude.countdownEndTimeMs - interlude.startTimeMs).coerceAtLeast(1L)
        val elapsed = (now - interlude.startTimeMs).coerceIn(0L, duration)
        val remaining = interlude.countdownEndTimeMs - now
        if (remaining <= 0) return@Canvas
        val radius = 5.dp.toPx()
        val gap = 18.dp.toPx()
        repeat(3) { index ->
            val progress = ((elapsed - duration.toFloat() * index / 3f) / (duration / 3f)).coerceIn(.25f, 1f)
            val breathe = if (reduceMotion) 1f else 1f + sin(elapsed.toDouble() / 1500.0 * 2.0 * Math.PI).toFloat() * .05f
            drawCircle(Color.White.copy(alpha = progress * (remaining / 375f).coerceIn(0f, 1f)),
                radius * breathe * if (index == 2) 1f + progress * .18f else 1f, Offset(radius + index * gap, size.height / 2f))
        }
    }
}
