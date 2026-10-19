/* Adapted from Kyant Backdrop 2.0.0 highlight/shadow modifiers (Apache-2.0, Copyright 2025 Kyant). */
package melox.ui.glass

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.FilterTileMode
import kotlin.math.ceil

/** Geometry and native shader stay cached while only the background pixels change. */
internal fun Modifier.meloXGlassDecoration(
    shape: Shape, rimAlpha: Float, innerRadius: Dp, innerAlpha: Float, innerColorAlpha: Float = .15f,
    rimWidth: Dp = .5.dp, ambient: Boolean = false,
): Modifier = composed {
    val inner = rememberGraphicsLayer()
    val rim = rememberGraphicsLayer()
    val shader = remember { HighlightShaderOwner() }
    val innerFilter = remember { MeloXGlassFilterOwner() }
    val rimFilter = remember { MeloXGlassFilterOwner() }
    DisposableEffect(shader, innerFilter, rimFilter, inner, rim) {
        onDispose {
            inner.renderEffect = null; rim.renderEffect = null
            innerFilter.close(); rimFilter.close(); shader.close()
        }
    }
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        val path = outline.glassPath()
        val radius = innerRadius.toPx().glassFinite().coerceAtLeast(0f)
        val stroke = Stroke(ceil(rimWidth.toPx().coerceAtMost(size.minDimension / 2f)) * 2f)
        inner.compositingStrategy = androidx.compose.ui.graphics.layer.CompositingStrategy.Offscreen
        inner.renderEffect = innerFilter.obtain(decorationBlur(radius))
        inner.alpha = safeGlassProgress(innerAlpha)
        rim.alpha = safeGlassProgress(rimAlpha)
        rim.blendMode = BlendMode.Plus
        rim.renderEffect = rimFilter.obtain(decorationBlur(rimWidth.toPx() / 2f))
        val radii = shape.glassCornerRadii(size, layoutDirection, this, outline)
        val brush = shader.brush(size.width, size.height, if (radii.size == 4) radii.toFloatArray() else FloatArray(4), ambient)
        val hasSize = size.width > 0 && size.height > 0
        if (hasSize) {
            if (innerAlpha > 0f) {
                inner.record {
                    clipPath(path) {
                        drawPath(path, Color.Black.copy(alpha = innerColorAlpha))
                        translate(0f, radius) { drawPath(path, Color.Black, blendMode = BlendMode.Clear) }
                    }
                }
            }
            rim.record(size = IntSize(ceil(size.width).toInt() + 2, ceil(size.height).toInt() + 2)) {
                translate(1f, 1f) { clipPath(path) { drawPath(path, brush, style = stroke) } }
            }
        }
        onDrawWithContent {
            drawContent()
            if (hasSize) {
                translate(-1f, -1f) { drawLayer(rim) }
                if (innerAlpha > 0f) clipPath(path) { drawLayer(inner) }
            }
        }
    }
}

/** Source outer shadow: blurred silhouette, 24dp radius and 4dp downward offset. */
internal fun Modifier.meloXGlassShadow(shape: Shape, alpha: Float, colorAlpha: Float = .10f, radius: Dp = 24.dp): Modifier = composed {
    val layer = rememberGraphicsLayer()
    val filter = remember { MeloXGlassFilterOwner() }
    DisposableEffect(filter, layer) { onDispose { layer.renderEffect = null; filter.close() } }
    drawWithCache {
        val radius = radius.toPx()
        val padding = ceil(radius * 3f).toInt()
        val path = shape.createOutline(size, layoutDirection, this).glassPath()
        layer.renderEffect = filter.obtain(decorationBlur(radius))
        layer.alpha = safeGlassProgress(alpha)
        val bounds = IntSize(ceil(size.width).toInt() + padding * 2, ceil(size.height).toInt() + padding * 2)
        layer.record(size = bounds) { translate(padding.toFloat(), padding.toFloat()) { drawPath(path, Color.Black.copy(alpha = colorAlpha)) } }
        onDrawWithContent {
            translate(-padding.toFloat(), -padding + radius / 6f) { drawLayer(layer) }
            drawContent()
        }
    }
}

private fun decorationBlur(radius: Float) = MeloXGlassFilterKey(
    0f, 0f, 0f, radius.glassFinite().coerceAtLeast(0f), emptyList(), MeloXGlassOptics(), FilterTileMode.DECAL,
)

private fun Outline.glassPath(): Path = when (this) {
    is Outline.Generic -> path
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
}

private class HighlightShaderOwner : AutoCloseable {
    private var effect: RuntimeEffect? = null
    private var shader: org.jetbrains.skia.Shader? = null
    private var key: List<Float>? = null
    fun brush(width: Float, height: Float, radii: FloatArray, ambient: Boolean): Brush {
        val next = listOf(width, height, if (ambient) 1f else 0f) + radii.toList()
        if (next != key) {
            val runtime = effect ?: RuntimeEffect.makeForShader(HighlightShader).also { effect = it }
            val replacement = RuntimeShaderBuilder(runtime).use {
                it.uniform("size", width, height); it.uniform("cornerRadii", radii)
                it.uniform("ambient", if (ambient) 1f else 0f); it.makeShader()
            }
            shader?.close(); shader = replacement; key = next
        }
        return ShaderBrush(checkNotNull(shader))
    }
    override fun close() { shader?.close(); effect?.close(); shader = null; effect = null; key = null }
}

// DefaultHighlightShaderString: 45-degree light, falloff=1, plus blend, inner 0.5dp stroke.
private const val HighlightShader = """
uniform float2 size;
uniform float4 cornerRadii;
uniform float ambient;
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 p = coord - halfSize;
    float r = coord.x >= 0.0 ? (coord.y <= 0.0 ? cornerRadii.y : cornerRadii.z) : (coord.y <= 0.0 ? cornerRadii.x : cornerRadii.w);
    float gradRadius = min(r * 1.5, min(halfSize.x, halfSize.y));
    float2 c = abs(p) - (halfSize - float2(gradRadius));
    float2 grad;
    if (c.x >= 0.0 || c.y >= 0.0) grad = sign(p) * max(c, 0.0) / max(length(max(c, 0.0)), 0.00001);
    else { float x = step(c.y, c.x); grad = sign(p) * float2(x, 1.0-x); }
    float d = dot(grad, float2(0.70710678, 0.70710678));
    if (ambient > 0.5) { float t = step(0.0, d); return half4(t, t, t, 1.0) * abs(d); }
    return half4(0.5) * abs(d);
}
"""
