package melox.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
import melox.ui.theme.MeloXColors
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Desktop port of Android ui/glass/MeloXBackdropComponents.kt.
 *
 * Android samples a recorded layer (Kyant backdrop library) with vibrancy +
 * blur + lens refraction + chromatic aberration. Desktop records the page
 * through GraphicsLayer and samples it at each control's root coordinates:
 *   Background → recorded page crop → GPU blur
 *   → Tint      → tint Hue/normal blend
 *   → Highlight → top-edge specular gradient
 *   → Border    → 0.5dp inner white stroke
 *   → Shadow    → outer soft shadow
 *   → Content   → press/drag transform (graphicsLayer, exact Android math)
 */

/** The screen backdrop sampled by all MeloX liquid controls (Android parity token). */
val LocalMeloXBackdrop = staticCompositionLocalOf<Boolean> { false }

/** Shared interaction state for a liquid surface and the content that rides on it. */
class MeloXLiquidInteraction internal constructor(
    internal val highlight: PublicInteractiveHighlight,
)


@Composable
fun rememberMeloXLiquidInteraction(): MeloXLiquidInteraction {
    val animationScope = androidx.compose.runtime.rememberCoroutineScope()
    return androidx.compose.runtime.remember(animationScope) {
        MeloXLiquidInteraction(PublicInteractiveHighlight(animationScope))
    }
}

/**
 * Official LiquidButton-style glass, generalized so existing MeloX controls
 * keep their exact iOS-derived size, shape and content.
 */
fun Modifier.meloXLiquidButton(
    shape: Shape,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    blurRadius: Dp? = null,
    lensRadius: Dp? = null,
    refractionHeight: Dp? = null,
    interaction: MeloXLiquidInteraction? = null,
    dark: Boolean = true,
): Modifier {
    val pressProgress = if (enabled) interaction?.highlight?.pressProgress ?: 0f else 0f
    val dragOffset = if (enabled) interaction?.highlight?.offset ?: Offset.Zero else Offset.Zero
    // Regular material spec: blur 2dp, lens 24dp, refraction 12dp (MeloXGlassSpec)
    return meloXGlassSurface(
        shape = shape,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        pressProgress = pressProgress,
        dragOffset = dragOffset,
        blurRadius = blurRadius ?: 2.dp,
        lensRadius = lensRadius ?: 0.dp,
        refractionHeight = refractionHeight ?: 0.dp,
        dark = dark,
    ).then(
        if (enabled && interaction != null) interaction.highlight.modifier else Modifier
    ).then(
        if (enabled && interaction != null) interaction.highlight.gestureModifier else Modifier
    )
}

/** Applies the same optical press/drag transform to content placed over liquid glass. */
fun Modifier.meloXLiquidContentTransform(interaction: MeloXLiquidInteraction?): Modifier =
    if (interaction == null) this else graphicsLayer {
        val controlHeight = size.height.coerceAtLeast(1f)
        val dragOffset = interaction.highlight.offset
        val pressProgress = interaction.highlight.pressProgress
        val baseScale = 1f + (4f / controlHeight * 12f) * pressProgress // 4dp in px-ish terms
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
        val maxDragScale = 4f / controlHeight * 12f
        val angle = atan2(dragOffset.y, dragOffset.x)
        scaleX = baseScale + maxDragScale * abs(cos(angle) * dragOffset.x / size.maxDimension.coerceAtLeast(1f)) *
            (size.width / controlHeight).coerceAtMost(1f)
        scaleY = baseScale + maxDragScale * abs(sin(angle) * dragOffset.y / size.maxDimension.coerceAtLeast(1f)) *
            (controlHeight / size.width.coerceAtLeast(1f)).coerceAtMost(1f)
    }

/**
 * Shared material entry point for all Native Component-style controls.
 * Reproduces the Android drawBackdrop stack with Skia primitives.
 */
fun Modifier.meloXGlassSurface(
    shape: Shape,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    pressProgress: Float = 0f,
    dragOffset: Offset = Offset.Zero,
    blurRadius: Dp = 2.dp,
    lensRadius: Dp = 0.dp,
    refractionHeight: Dp = 0.dp,
    dark: Boolean? = null,
): Modifier = composed {
    val dark = dark ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val alphaScale = if (enabled) 1f else 0.48f
    val isPlain = surfaceColor == Color.Transparent && tint == Color.Unspecified
    if (isPlain) return@composed Modifier
    // Page recording disables self-sampling. Match Android's no-backdrop
    // fallback there, preserving explicit 5.5% fills without a fake glass slab.
    if (LocalMeloXLayerBackdrop.current == null) {
        val stableSurface = when {
            surfaceColor != Color.Unspecified -> surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale)
            tint != Color.Unspecified -> tint.copy(alpha = maxOf(tint.alpha, if (dark) 0.18f else 0.22f) * alphaScale)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = (if (dark) 0.84f else 0.88f) * alphaScale)
        }
        return@composed Modifier.background(stableSurface, shape)
    }
    if (melox.settings.MeloXSettingsRuntime.frostedGlassEnabled) {
        return@composed Modifier.clip(shape).meloXSampleBackdrop(blurRadius).drawBehind {
            if (tint != Color.Unspecified) drawRect(tint.copy(alpha = tint.alpha * alphaScale))
            if (surfaceColor != Color.Unspecified) drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale))
        }
    }

    Modifier
        .shadowLike(shape, pressProgress, enabled)
        .clip(shape)
        .meloXSampleBackdrop(blurRadius)
        .drawBehind {
            val optical = melox.settings.meloXGlassDraw(
                blurRadius.toPx(),
                lensRadius.toPx(),
                refractionHeight.toPx(),
                dark,
            )
            val fill = when {
                surfaceColor != Color.Unspecified -> surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale)
                tint != Color.Unspecified -> tint.copy(alpha = tint.alpha * alphaScale)
                else -> null
            }
            if (fill != null) drawRect(fill)
            drawRect(
                Color.White.copy(alpha = if (dark) 0.045f else 0.12f),
                blendMode = BlendMode.Screen,
            )
            if (optical.lensShiftPx > 0f) {
                translate(-optical.lensShiftPx * 0.35f, -optical.refractionPx * 0.25f) {
                    drawRect(
                        Color.White.copy(alpha = (0.05f + optical.refractionPx / 180f).coerceIn(0.05f, 0.22f)),
                        blendMode = BlendMode.Screen,
                    )
                }
            }
            if (pressProgress > 0.001f) {
                drawRect(Color.White.copy(alpha = 0.08f * pressProgress), blendMode = BlendMode.Plus)
            }
        }
        .drawWithContent {
            drawContent()
            val optical = melox.settings.meloXGlassDraw(
                blurRadius.toPx(),
                lensRadius.toPx(),
                refractionHeight.toPx(),
                dark,
            )
            val hAlpha = (optical.highlightAlpha + 0.30f * pressProgress).coerceAtMost(1f)
            val shift = (optical.refractionPx / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.35f)
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = hAlpha * 0.55f),
                    (0.5f + shift).coerceAtMost(0.92f) to Color.White.copy(alpha = hAlpha * 0.10f),
                    1f to Color.White.copy(alpha = 0f),
                ),
                blendMode = BlendMode.Plus,
            )
            // Border stage: inner hairline
            drawPath(
                outlineToPath(shape.createOutline(size, layoutDirection, this)),
                Color.White.copy(alpha = if (dark) 0.22f else 0.60f),
                style = Stroke(width = 0.75f),
            )
        }
        .then(dragTransformLike(dragOffset, pressProgress))
}


private fun outlineToPath(outline: androidx.compose.ui.graphics.Outline): Path = when (outline) {
    is androidx.compose.ui.graphics.Outline.Generic -> outline.path
    is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
    is androidx.compose.ui.graphics.Outline.Rectangle -> Path().apply { addRect(outline.rect) }
}

private fun Modifier.shadowLike(shape: Shape, pressProgress: Float, enabled: Boolean): Modifier =
    graphicsLayer {
        shadowElevation = 24f * ((0.08f + 0.22f * pressProgress) * if (enabled) 1f else 0.35f)
        this.shape = shape.let { s ->
            // graphicsLayer needs a concrete shape; RoundedCornerShape works for capsules
            s
        }
    }

private fun Modifier.dragTransformLike(dragOffset: Offset, pressProgress: Float): Modifier =
    graphicsLayer {
        val controlHeight = size.height.coerceAtLeast(1f)
        val scale = 1f + (48f / controlHeight) * pressProgress
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
        val maxDragScale = 48f / controlHeight
        val angle = atan2(dragOffset.y, dragOffset.x)
        scaleX = scale + maxDragScale * abs(cos(angle) * dragOffset.x / size.maxDimension.coerceAtLeast(1f)) *
            (size.width / controlHeight).coerceAtMost(1f)
        scaleY = scale + maxDragScale * abs(sin(angle) * dragOffset.y / size.maxDimension.coerceAtLeast(1f)) *
            (controlHeight / size.width.coerceAtLeast(1f)).coerceAtMost(1f)
    }

/** Official LiquidBottomTabs-style outer panel (Android meloXLiquidBottomBar). */
fun Modifier.meloXLiquidBottomBar(
    shape: Shape,
    tint: Color,
    surfaceColor: Color,
    pressProgress: Float = 0f,
    dark: Boolean = true,
    refractionHeight: Dp = 24.dp,
): Modifier = composed {
    val frosted = melox.settings.MeloXSettingsRuntime.frostedGlassEnabled
    val p = pressProgress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
    val tone = bottomBarTone()
    Modifier
        .graphicsLayer {
            val scale = 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f) * p
            scaleX = scale
            scaleY = scale
        }
        .then(if (frosted) Modifier else Modifier.shadowLike(shape, p, true))
        .clip(shape)
        .meloXSampleBackdrop(if (frosted) 8.dp else 2.dp)
        .drawBehind { drawRect(surfaceColor) }
        .drawWithContent {
            drawContent()
            if (!frosted) {
                val rim = (0.48f + (0.32f - 0.48f) * tone + 0.30f * p).coerceAtMost(1f)
                drawPath(
                    outlineToPath(shape.createOutline(size, layoutDirection, this)),
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = rim), Color.White.copy(alpha = rim * 0.12f))),
                    style = Stroke(width = 0.5.dp.toPx()),
                )
            }
        }
}

/** Hidden red tab capture; the moving droplet samples these actual pixels. */
fun Modifier.meloXLiquidCaptureLayer(shape: Shape, surfaceColor: Color): Modifier =
    clip(shape).meloXSampleBackdrop(2.dp).drawBehind { drawRect(surfaceColor) }

/** Moving/selected tab lens used inside the bottom panel (Android meloXLiquidTabSelection). */
fun Modifier.meloXLiquidTabSelection(
    shape: Shape,
    selected: Boolean,
    tint: Color,
    panelBackdrop: MeloXLayerBackdrop? = null,
    pressProgress: Float = 0f,
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    velocity: Float = 0f,
    dark: Boolean = true,
): Modifier {
    if (!selected) return this
    return this
        .graphicsLayer {
            this.scaleX = scaleX
            this.scaleY = scaleY
            val normalizedVelocity = velocity / 10f
            this.scaleX /= 1f - (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            this.scaleY *= 1f - (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
            shadowElevation = 24f * 0.84f * pressProgress
            this.shape = shape
        }
        .clip(shape)
        .meloXSampleBackdrop(0.dp, panelBackdrop)
        .drawBehind {
            drawRect(tint, alpha = 1f - pressProgress)
            drawRect(Color.Black.copy(alpha = 0.03f * pressProgress))
        }
        .drawWithContent {
            drawContent()
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = 0.90f * pressProgress * 0.5f),
                    1f to Color.White.copy(alpha = 0f),
                ),
                blendMode = BlendMode.Plus,
            )
            drawPath(
                outlineToPath(shape.createOutline(size, layoutDirection, this)),
                Color.White.copy(alpha = (if (dark) 0.32f else 0.48f) + 0.35f * pressProgress),
                style = Stroke(width = 0.75f),
            )
        }
}

/**
 * Recorded background blur (Android meloXBackdropBlur).
 */
fun Modifier.meloXBackdropBlur(
    shape: Shape,
    blurRadius: Dp = 20.dp,
    surfaceColor: Color = Color.Transparent,
    dark: Boolean = true,
): Modifier =
    clip(shape).meloXSampleBackdrop(blurRadius).drawBehind {
        if (surfaceColor != Color.Transparent) drawRect(surfaceColor)
    }

/**
 * Standard content-layer material (Android meloXContentSurface): opaque
 * surface fill, distinct from Liquid Glass.
 */
fun Modifier.meloXContentSurface(
    shape: Shape,
    surfaceColor: Color = Color.Unspecified,
): Modifier {
    val color = if (surfaceColor == Color.Unspecified) MeloXColors.Surface else surfaceColor
    return background(color, shape)
}
