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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
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
 * The sampled pixels pass through vibrancy, blur and a real SkSL lens. Cached
 * normal-based rim and inner/outer shadow layers follow pinned geometry; the
 * press/drag transform encloses the complete surface and its content.
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
    dark: Boolean? = null,
): Modifier = composed {
    val activeInteraction = interaction ?: rememberMeloXLiquidInteraction()
    val frosted = melox.settings.MeloXSettingsRuntime.frostedGlassEnabled
    val pressProgress = if (enabled) activeInteraction.highlight.pressProgress else 0f
    val dragOffset = if (enabled) activeInteraction.highlight.offset else Offset.Zero
    // Regular material spec: blur 2dp, lens 24dp, refraction 12dp (MeloXGlassSpec)
    Modifier.meloXGlassSurface(
        shape = shape,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        pressProgress = pressProgress,
        dragOffset = dragOffset,
        blurRadius = blurRadius ?: 2.dp,
        lensRadius = lensRadius ?: 24.dp,
        refractionHeight = refractionHeight ?: 12.dp,
        dark = dark,
    ).then(
        if (enabled && !frosted) activeInteraction.highlight.modifier else Modifier
    ).then(
        if (enabled && !frosted) activeInteraction.highlight.gestureModifier else Modifier
    )
}

/** Applies the same optical press/drag transform to content placed over liquid glass. */
fun Modifier.meloXLiquidContentTransform(interaction: MeloXLiquidInteraction?): Modifier =
    if (interaction == null) this else graphicsLayer {
        val controlHeight = size.height.coerceAtLeast(1f)
        val dragOffset = interaction.highlight.offset.let { Offset(it.x.glassFinite(), it.y.glassFinite()) }
        val pressProgress = safeGlassProgress(interaction.highlight.pressProgress)
        val baseScale = 1f + (4.dp.toPx() / controlHeight) * pressProgress
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
        val maxDragScale = 4.dp.toPx() / controlHeight
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
    lensRadius: Dp = 24.dp,
    refractionHeight: Dp = 12.dp,
    dark: Boolean? = null,
): Modifier = composed {
    val dark = dark ?: (MaterialTheme.colorScheme.background.luminance() < 0.5f)
    val progress = safeGlassProgress(pressProgress)
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
        .then(dragTransformLike(dragOffset, progress))
        .meloXGlassShadow(shape, (.08f + .22f * progress) * if (enabled) 1f else .35f)
        .meloXGlassDecoration(shape, ((if (dark) .32f else .48f) + .30f * progress).coerceAtMost(1f),
            4.dp + 8.dp * progress, (.10f + .30f * progress) * if (enabled) 1f else .35f)
        .clip(shape)
        .meloXSampleBackdrop(blurRadius, shape = shape, lensHeight = lensRadius, lensAmount = refractionHeight,
            depth = progress > .01f, dispersion = true, vibrant = true)
        .drawBehind {
            drawRect(
                Color.White.copy(alpha = if (dark) 0.045f else 0.12f),
                blendMode = BlendMode.Screen,
            )
            if (progress > 0.001f) {
                drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
            }
            if (tint != Color.Unspecified && tint.alpha > .001f) {
                drawRect(tint.copy(alpha = tint.alpha * alphaScale), blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = tint.alpha * .75f * alphaScale))
            }
            if (surfaceColor != Color.Unspecified) drawRect(surfaceColor.copy(alpha = surfaceColor.alpha * alphaScale))
        }
}


private fun Modifier.dragTransformLike(dragOffset: Offset, pressProgress: Float): Modifier =
    graphicsLayer {
        val dragOffset = Offset(dragOffset.x.glassFinite(), dragOffset.y.glassFinite())
        val pressProgress = safeGlassProgress(pressProgress)
        val controlHeight = size.height.coerceAtLeast(1f)
        val scale = 1f + (4.dp.toPx() / controlHeight) * pressProgress
        val maxOffset = size.minDimension.coerceAtLeast(1f)
        translationX = maxOffset * tanh(0.05f * dragOffset.x / maxOffset)
        translationY = maxOffset * tanh(0.05f * dragOffset.y / maxOffset)
        val maxDragScale = 4.dp.toPx() / controlHeight
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
    val p = safeGlassProgress(pressProgress)
    val tone = bottomBarTone()
    Modifier
        .graphicsLayer {
            val scale = if (frosted) 1f else 1f + 16.dp.toPx() / size.width.coerceAtLeast(1f) * p
            scaleX = scale
            scaleY = scale
        }
        .then(if (frosted) Modifier else Modifier.meloXGlassShadow(shape, .08f + .22f * p))
        .then(if (frosted) Modifier else Modifier.meloXGlassDecoration(shape,
            (.48f - .16f * tone + .30f * p).coerceAtMost(1f), 4.dp + 8.dp * p, .10f + .30f * p))
        .clip(shape)
        .meloXSampleBackdrop(if (frosted) 8.dp else 2.dp, shape = shape,
            lensHeight = if (frosted) 0.dp else 24.dp, lensAmount = if (frosted) 0.dp else 24.dp, vibrant = !frosted)
        .drawBehind { drawRect(surfaceColor) }
}

/** Hidden red tab capture; the moving droplet samples these actual pixels. */
fun Modifier.meloXLiquidCaptureLayer(shape: Shape, surfaceColor: Color, pressProgress: Float = 0f): Modifier = composed {
    val frosted = melox.settings.MeloXSettingsRuntime.frostedGlassEnabled
    val p = safeGlassProgress(pressProgress)
    val lens = if (!frosted && p > .001f) 24.dp * p else 0.dp
    Modifier.then(if (frosted || p <= .001f) Modifier else Modifier.meloXGlassDecoration(shape, p, 0.dp, 0f))
        .clip(shape).meloXSampleBackdrop(if (frosted) 8.dp else 2.dp, shape = shape,
        lensHeight = lens, lensAmount = lens, vibrant = !frosted).drawBehind { drawRect(surfaceColor) }
}

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
): Modifier = composed {
    if (!selected) return@composed Modifier
    val p = safeGlassProgress(pressProgress)
    val frosted = melox.settings.MeloXSettingsRuntime.frostedGlassEnabled
    val pageBackdrop = LocalMeloXLayerBackdrop.current
    val tone = bottomBarTone()
    val lens = !frosted && p > .001f
    Modifier
        .graphicsLayer {
            this.scaleX = if (frosted) 1f else scaleX.glassFinite()
            this.scaleY = if (frosted) 1f else scaleY.glassFinite()
            val normalizedVelocity = if (frosted) 0f else velocity.glassFinite() / 10f
            this.scaleX /= 1f - (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
            this.scaleY *= 1f - (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
            this.shape = shape
        }
        .then(if (frosted) Modifier else Modifier.meloXGlassShadow(shape, .5f + .5f * p, .10f))
        .then(if (frosted) Modifier else Modifier.meloXGlassDecoration(shape,
            .48f - .16f * tone + (1f - (.48f - .16f * tone)) * p,
            4.dp + 4.dp * p, .60f + .40f * p, .15f))
        .clip(shape)
        .meloXSampleBackdrop(if (frosted) 8.dp else 0.dp, if (frosted) pageBackdrop else panelBackdrop,
            shape = shape, lensHeight = if (lens) 8.75.dp * p else 0.dp,
            lensAmount = if (lens) 12.25.dp * p else 0.dp, dispersion = !frosted,
            underlay = if (frosted) null else pageBackdrop)
        .drawBehind {
            val selectedTint = androidx.compose.ui.graphics.lerp(Color.Black.copy(alpha = .1f), Color.White.copy(alpha = .1f), tone)
            drawRect(if (frosted) tint else selectedTint, alpha = if (frosted) 1f else 1f - p)
            if (!frosted) drawRect(Color.Black.copy(alpha = 0.03f * p))
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
