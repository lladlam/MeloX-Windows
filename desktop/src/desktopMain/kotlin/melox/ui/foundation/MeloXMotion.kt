package melox.ui.foundation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Animation constants — exact 1:1 port of Android ui/animation/MeloXMotion.kt.
 */
object MeloXMotion {
    const val PageEnterMillis = 280
    const val PageExitMillis = 220
    const val ContentEnterMillis = 320
    const val ContentExitMillis = 240
    const val IconEnterMillis = 180
    const val IconExitMillis = 120
    const val PanelEnterStiffness = 420f
    const val PanelExitStiffness = 500f
    const val DropdownStiffness = 320f

    fun <T> pageEnterTween(): TweenSpec<T> = tween(PageEnterMillis, easing = FastOutSlowInEasing)
    fun <T> pageExitTween(): TweenSpec<T> = tween(PageExitMillis, easing = FastOutSlowInEasing)
    fun <T> contentEnterTween(): TweenSpec<T> = tween(ContentEnterMillis, easing = FastOutSlowInEasing)
    fun <T> contentExitTween(): TweenSpec<T> = tween(ContentExitMillis, easing = FastOutSlowInEasing)
    fun <T> iconEnterTween(): TweenSpec<T> = tween(IconEnterMillis, easing = FastOutSlowInEasing)
    fun <T> iconExitTween(): TweenSpec<T> = tween(IconExitMillis, easing = FastOutSlowInEasing)

    fun panelEnterSpec() = spring<Float>(dampingRatio = 0.86f, stiffness = PanelEnterStiffness)
    fun panelExitSpec() = spring<Float>(dampingRatio = 0.90f, stiffness = PanelExitStiffness)
    fun dropdownSpec() = spring<Float>(dampingRatio = 0.82f, stiffness = DropdownStiffness, visibilityThreshold = 0.001f)

    // ── Bottom chrome (from MeloXApp.kt audit) ──
    fun tabMinimizeSpec() = spring<Float>(dampingRatio = 0.90f, stiffness = 330f, visibilityThreshold = 0.001f)
    fun mediaRevealSpec() = spring<Float>(dampingRatio = 0.90f, stiffness = 360f)
    fun tabSelectionAlphaSpec() = spring<Float>(dampingRatio = 0.86f, stiffness = 440f)
    fun tabColorSpec() = spring<Float>(dampingRatio = 0.84f, stiffness = 480f)

    // ── Mini player ──
    fun miniSwipeDismiss() = spring<Float>(dampingRatio = 0.68f, stiffness = 360f)
    fun miniSwipeBounceBack() = spring<Float>(dampingRatio = 0.72f, stiffness = 430f)
    fun miniReveal() = spring<Float>(dampingRatio = 0.90f, stiffness = 360f)

    // ── Player transition (MeloXPlayerTransitionSpec.kt) ──
    const val PlayerTransitionDurationMillis = 360
    fun playerTransitionSpec() = tween<Float>(PlayerTransitionDurationMillis, easing = FastOutSlowInEasing)
    fun playerGestureSettle() = spring<Float>(dampingRatio = 1.0f, stiffness = 420f, visibilityThreshold = 0.001f)
    fun playerArtworkBounds() = tween<Float>(300, easing = FastOutSlowInEasing)

    // ── Artwork playback scale (paused shrink) ──
    fun artworkGrowSpec() = spring<Float>(dampingRatio = 0.70f, stiffness = 280f, visibilityThreshold = 0.001f)
    fun artworkShrinkSpec() = spring<Float>(dampingRatio = 0.94f, stiffness = 360f, visibilityThreshold = 0.001f)
    fun artworkShadowSpec() = spring<Float>(dampingRatio = 0.92f, stiffness = 320f)

    // ── Press interactions ──
    fun interactivePress() = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    fun transportPress() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 620f)
    fun pageSelectorPress() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 650f)

    // ── Liquid drag (slider/toggle/dock) ──
    fun liquidValue() = spring<Float>(dampingRatio = 1.0f, stiffness = 1000f)
    fun liquidVelocity() = spring<Float>(dampingRatio = 0.5f, stiffness = 300f)
    fun liquidPressProgress() = spring<Float>(dampingRatio = 1.0f, stiffness = 1000f, visibilityThreshold = 0.001f)
    fun liquidScaleX() = spring<Float>(dampingRatio = 0.6f, stiffness = 250f, visibilityThreshold = 0.001f)
    fun liquidScaleY() = spring<Float>(dampingRatio = 0.7f, stiffness = 250f, visibilityThreshold = 0.001f)

    // ── Scene page cross-fades ──
    fun scenePageSpec() = spring<Float>(dampingRatio = 0.7f, stiffness = 300f)

    // ── Controls reveal ──
    fun controlsEnterAlpha() = tween<Float>(220, easing = FastOutSlowInEasing)
    fun controlsEnterOffset() = tween<Float>(260, easing = FastOutSlowInEasing)
    fun controlsExitAlpha() = tween<Float>(180, easing = FastOutSlowInEasing)
    fun controlsExitOffset() = tween<Float>(220, easing = FastOutSlowInEasing)

    // ── Page transitions helpers ──
    const val PLAYER_PAGE_ARTWORK = 0
    const val PLAYER_PAGE_LYRICS = 1
    const val PLAYER_PAGE_QUEUE = 2
}

/**
 * Hermite smoothstep: t²(3−2t). Exact port of Android smoothStep().
 */
fun smoothStep(value: Float, start: Float, end: Float): Float {
    if (start == end) return if (value < start) 0f else 1f
    val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun lerpDp(a: androidx.compose.ui.unit.Dp, b: androidx.compose.ui.unit.Dp, fraction: Float): androidx.compose.ui.unit.Dp =
    androidx.compose.ui.unit.Dp(a.value + (b.value - a.value) * fraction)
