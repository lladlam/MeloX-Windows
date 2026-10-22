package melox.ui.screens

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

internal data object MeloXPlayerShellKey
internal data class MeloXPlayerArtworkKey(val mediaId: String?)

// Android v0.6.5, PlayerTransitionSpec: one linear master, one normalized k=6
// critically-damped easing for geometry/content/recede. Never ease the master again.
internal val MeloXPlayerAppleEasing = Easing { value ->
    val z = 6f * value.coerceIn(0f, 1f)
    (1f - (1f + z) * kotlin.math.exp(-z)) / (1f - 7f * kotlin.math.exp(-6f))
}
internal fun <T> playerContentSpec() = tween<T>(
    if (melox.settings.MeloXSettingsRuntime.reduceMotion) 0 else melox.settings.MeloXSettingsRuntime.playerTransitionDurationMs,
    easing = MeloXPlayerAppleEasing)

internal val LocalMeloXPlayerClosing = staticCompositionLocalOf { false }
internal fun playerSinkFraction(time: Float): Float {
    if (!time.isFinite()) return 0f
    val u = ((time - .62f) / .38f).coerceIn(0f, 1f)
    return if (u <= 0f || u >= 1f) 0f else kotlin.math.sin(kotlin.math.PI.toFloat() * u)
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun rememberPlayerBoundsTransform(): BoundsTransform {
    val closing = rememberUpdatedState(LocalMeloXPlayerClosing.current)
    val reduce = rememberUpdatedState(melox.settings.MeloXSettingsRuntime.reduceMotion)
    val peak = with(LocalDensity.current) { 22.dp.toPx() }
    val duration = if (reduce.value) 0 else melox.settings.MeloXSettingsRuntime.playerTransitionDurationMs
    return remember(peak, duration) { BoundsTransform { _, _ ->
        PlayerSinkBoundsSpec(duration, peak) { closing.value && !reduce.value }
    } }
}

private class PlayerSinkBoundsSpec(val duration: Int, val peak: Float, val closing: () -> Boolean) : FiniteAnimationSpec<Rect> {
    override fun <V : AnimationVector> vectorize(converter: TwoWayConverter<Rect, V>): VectorizedFiniteAnimationSpec<V> =
        object : VectorizedFiniteAnimationSpec<V> {
            val nanos = duration.coerceAtLeast(0).toLong() * 1_000_000L
            override fun getDurationNanos(initialValue: V, targetValue: V, initialVelocity: V) = nanos
            override fun getValueFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V {
                val t = if (nanos == 0L) 1f else (playTimeNanos.toFloat() / nanos).coerceIn(0f, 1f)
                val p = MeloXPlayerAppleEasing.transform(t)
                val a = converter.convertFromVector(initialValue)
                val b = converter.convertFromVector(targetValue)
                // Direction is deliberately read per frame, including interrupted closes.
                val dy = if (closing() && b.height < a.height * .6f) playerSinkFraction(t) * peak else 0f
                return converter.convertToVector(Rect(a.left + (b.left - a.left) * p,
                    a.top + (b.top - a.top) * p + dy, a.right + (b.right - a.right) * p,
                    a.bottom + (b.bottom - a.bottom) * p + dy))
            }
            override fun getVelocityFromNanos(playTimeNanos: Long, initialValue: V, targetValue: V, initialVelocity: V): V =
                converter.convertToVector(Rect.Zero)
        }
}
