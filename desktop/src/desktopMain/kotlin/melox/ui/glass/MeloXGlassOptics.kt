/*
 * Lens shader adapted from Kyant Backdrop 2.0.0 internal/Shaders.kt.
 * Copyright 2025 Kyant. Licensed under the Apache License, Version 2.0.
 * https://www.apache.org/licenses/LICENSE-2.0
 * Shader changes: safe normalization at the exact center; shared dispersion switch.
 */
package melox.ui.glass

import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import java.util.concurrent.atomic.AtomicLong

internal fun safeGlassProgress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
internal fun Float.glassFinite(): Float = if (isFinite()) this else 0f

internal data class MeloXGlassOptics(
    val height: Float = 0f,
    val amount: Float = 0f,
    val depth: Boolean = false,
    val dispersion: Boolean = false,
    val vibrant: Boolean = false,
)

internal data class MeloXGlassFilterKey(
    val width: Float, val height: Float, val padding: Float, val blur: Float,
    val radii: List<Float>, val optics: MeloXGlassOptics,
    val tileMode: FilterTileMode = FilterTileMode.CLAMP,
)

/** One owned chain per mounted sampler, not an unbounded cache of animation frames. */
internal class MeloXGlassFilterOwner : AutoCloseable {
    companion object {
        private val owners = AtomicLong()
        private val created = AtomicLong()
        private val released = AtomicLong()
        /** Owned final chains, not Skia byte counts or global GPU allocations. */
        internal fun ownershipCounts() = mapOf("owners" to owners.get(), "created_chains" to created.get(), "released_chains" to released.get())
    }
    init { owners.incrementAndGet() }
    private var key: MeloXGlassFilterKey? = null
    private var native: ImageFilter? = null
    private var effect: RenderEffect? = null
    private var lens: RuntimeEffect? = null
    internal val nativeFilter: ImageFilter? get() = native
    var creations = 0; private set
    var cacheHits = 0; private set
    var releases = 0; private set
    var closed = false; private set

    fun obtain(next: MeloXGlassFilterKey): RenderEffect? {
        check(!closed)
        if (key == next) { cacheHits++; return effect }
        val resources = mutableListOf<ImageFilter>()
        var chain: ImageFilter? = null
        fun append(filter: ImageFilter) { resources += filter; chain = filter }
        try {
            if (next.optics.vibrant) {
                // Backdrop 2.0.0 vibrancy(): saturation 1.5, luminance .213/.715/.072.
                ColorFilter.makeMatrix(ColorMatrix(
                    1.3935f, -.3575f, -.036f, 0f, 0f,
                    -.1065f, 1.1425f, -.036f, 0f, 0f,
                    -.1065f, -.3575f, 1.464f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )).use { append(ImageFilter.makeColorFilter(it, chain, null)) }
            }
            if (next.blur > 0f) append(ImageFilter.makeBlur(next.blur, next.blur, next.tileMode, chain))
            if (next.width > 0f && next.height > 0f && next.optics.height > 0f && next.optics.amount > 0f && next.radii.size == 4) {
                val shader = lens ?: RuntimeEffect.makeForShader(LensShader).also { lens = it }
                RuntimeShaderBuilder(shader).use { builder ->
                    builder.uniform("size", next.width, next.height)
                    builder.uniform("offset", -next.padding, -next.padding)
                    builder.uniform("cornerRadii", next.radii.toFloatArray())
                    builder.uniform("refractionHeight", next.optics.height)
                    builder.uniform("refractionAmount", -next.optics.amount)
                    builder.uniform("depthEffect", if (next.optics.depth) 1f else 0f)
                    builder.uniform("chromaticAberration", if (next.optics.dispersion) 1f else 0f)
                    append(ImageFilter.makeRuntimeShader(builder, "content", chain))
                }
            }
            val composed = chain?.asComposeRenderEffect()
            native?.let { it.close(); releases++; released.incrementAndGet() }
            native = chain
            effect = composed
            key = next
            if (chain != null) { creations++; created.incrementAndGet() }
            resources.filter { it !== chain }.forEach { it.close() }
            return effect
        } catch (failure: Throwable) {
            resources.forEach { it.close() }
            throw failure
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        native?.let { it.close(); releases++; released.incrementAndGet() }
        native = null; effect = null; key = null
        lens?.close(); lens = null
        owners.decrementAndGet()
    }
}

private const val LensShader = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
float2 safeNormalize(float2 v) { return v / max(length(v), 0.00001); }
float radiusAt(float2 p, float4 r) {
    if (p.x >= 0.0) { if (p.y <= 0.0) return r.y; else return r.z; }
    else { if (p.y <= 0.0) return r.x; else return r.w; }
}
float sdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 c = abs(p) - (halfSize - float2(r));
    return length(max(c, 0.0)) - r + min(max(c.x, c.y), 0.0);
}
float2 gradSdRoundedRect(float2 p, float2 halfSize, float r) {
    float2 c = abs(p) - (halfSize - float2(r));
    if (c.x >= 0.0 || c.y >= 0.0) return sign(p) * safeNormalize(max(c, 0.0));
    float x = step(c.y, c.x);
    return sign(p) * float2(x, 1.0 - x);
}
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 centered = (coord + offset) - halfSize;
    float radius = radiusAt(coord, cornerRadii);
    float sd = sdRoundedRect(centered, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float x = 1.0 - -sd / refractionHeight;
    float d = (1.0 - sqrt(max(0.0, 1.0 - x * x))) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = safeNormalize(gradSdRoundedRect(centered, halfSize, gradRadius) + depthEffect * safeNormalize(centered));
    float2 refracted = coord + d * grad;
    if (chromaticAberration == 0.0) return content.eval(refracted);
    float2 delta = d * grad * ((centered.x * centered.y) / (halfSize.x * halfSize.y));
    half4 red = content.eval(refracted + delta);
    half4 orange = content.eval(refracted + delta * (2.0 / 3.0));
    half4 yellow = content.eval(refracted + delta * (1.0 / 3.0));
    half4 green = content.eval(refracted);
    half4 cyan = content.eval(refracted - delta * (1.0 / 3.0));
    half4 blue = content.eval(refracted - delta * (2.0 / 3.0));
    half4 purple = content.eval(refracted - delta);
    return half4((red.r + orange.r + yellow.r) / 3.5 + purple.r / 7.0,
        orange.g / 7.0 + (yellow.g + green.g + cyan.g) / 3.5,
        (cyan.b + blue.b + purple.b) / 3.0,
        (red.a + orange.a + yellow.a + green.a + cyan.a + blue.a + purple.a) / 7.0);
}
"""
