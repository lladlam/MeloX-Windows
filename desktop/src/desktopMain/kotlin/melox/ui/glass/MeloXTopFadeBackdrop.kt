package melox.ui.glass

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

// Same SkSL and uniforms as Android 3f877ea MeloXPinnedListPage.TopFadeShader.
private const val TopFadeShader = """
uniform shader content;
uniform float2 size;
layout(color) uniform half4 tint;
uniform float tintIntensity;
half4 main(float2 coord) {
    float mask = smoothstep(size.y, size.y * 0.40, coord.y);
    return mix(content.eval(coord) * mask, tint * mask, tintIntensity);
}
"""

internal fun Modifier.meloXTopFadeBackdrop(tint: Color): Modifier = composed {
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    val effect = remember { RuntimeEffect.makeForShader(TopFadeShader) }
    DisposableEffect(effect) { onDispose { effect.close() } }
    val filter = remember(bounds, tint) {
        RuntimeShaderBuilder(effect).use { builder ->
            builder.uniform("size", bounds.width.toFloat(), bounds.height.coerceAtLeast(1).toFloat())
            builder.uniform("tint", tint.red, tint.green, tint.blue, tint.alpha)
            builder.uniform("tintIntensity", .76f)
            ImageFilter.makeRuntimeShader(builder, "content", null)
        }
    }
    DisposableEffect(filter) { onDispose { filter.close() } }
    val renderEffect = remember(filter) { filter.asComposeRenderEffect() }
    onSizeChanged { bounds = it }.graphicsLayer { this.renderEffect = renderEffect }
}
