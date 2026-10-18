package melox.ui.glass.publicdemo

/*
 * Based on the InteractiveHighlight demo shipped with
 * Kyant0/AndroidLiquidGlass (Apache-2.0). Desktop port of Android
 * ui/glass/publicdemo/PublicInteractiveHighlight.kt — the pinned AGSL focus
 * spot runs as a Skia runtime shader with explicitly owned native resources.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import melox.ui.glass.safeGlassProgress
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/** Public-demo press highlight with a moving optical focus point. */
class PublicInteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
) {
    private val pressSpec = spring<Float>(0.5f, 300f, 0.001f)
    private val positionSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    val pressProgress: Float get() = pressAnimation.value
    val offset: Offset get() = positionAnimation.value - startPosition

    val modifier: Modifier = Modifier.composed {
      val shaderOwner = remember { FocusShaderOwner() }
      DisposableEffect(shaderOwner) { onDispose { shaderOwner.close() } }
      Modifier.drawWithContent {
        val progress = safeGlassProgress(pressAnimation.value)
        if (progress > 0f) {
            val focus = position(size, positionAnimation.value)
            drawRect(Color.White.copy(alpha = 0.08f * progress), blendMode = BlendMode.Plus)
            shaderOwner.shader(progress, size, focus).use { shader -> drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus) }
        }
        drawContent()
      }
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        publicInspectDragGestures(
            onDragStart = { down ->
                startPosition = down
                animationScope.launch {
                    launch { pressAnimation.animateTo(1f, pressSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressAnimation.animateTo(0f, pressSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionSpec) }
                }
            },
        ) { change, _ ->
            animationScope.launch { positionAnimation.snapTo(change.position) }
        }
    }
}

/** No native shader compilation for controls that have never been pressed. */
private class FocusShaderOwner : AutoCloseable {
    private var effect: RuntimeEffect? = null
    private var builder: RuntimeShaderBuilder? = null
    fun shader(progress: Float, size: Size, focus: Offset): org.jetbrains.skia.Shader {
        val shader = builder ?: RuntimeShaderBuilder(RuntimeEffect.makeForShader(FocusShader).also { effect = it }).also { builder = it }
        // Skia runtime output is premultiplied, matching Backdrop's Skiko
        // setColorUniform implementation. Unpremultiplied white clips the panel.
        val alpha = .15f * progress
        shader.uniform("color", alpha, alpha, alpha, alpha)
        shader.uniform("radius", (size.minDimension * 1.5f).coerceAtLeast(.001f))
        shader.uniform("position", focus.x.coerceIn(0f, size.width), focus.y.coerceIn(0f, size.height))
        return shader.makeShader()
    }
    override fun close() { builder?.close(); effect?.close(); builder = null; effect = null }
}

// Same focus falloff and clamped focus coordinates as pinned PublicInteractiveHighlight.
private const val FocusShader = """
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;
half4 main(float2 coord) {
    float dist = distance(coord, position);
    return color * smoothstep(radius, radius * 0.5, dist);
}
"""

internal suspend fun PointerInputScope.publicInspectDragGestures(
    onDragStart: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDrag: (PointerInputChange, Offset) -> Unit,
) {
    // This is the catalog helper's key behavior: press starts on pointer
    // down, before Compose's drag-slop threshold is crossed.
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)
        val down = awaitFirstDown(false)
        onDragStart(down.position)
        onDrag(initialDown, Offset.Zero)
        val upEvent = drag(initialDown.id) { change ->
            onDrag(change, change.positionChange())
        }
        if (upEvent == null) onDragCancel() else onDragEnd()
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    if (currentEvent.changes.firstOrNull { it.id == pointerId }?.pressed != true) return null
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.changedToUpIgnoreConsumed()) return change
        // This observer follows absolute position even when the inner liquid
        // drag consumes movement; cancellation would extinguish its highlight.
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId,
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == pointer } ?: return null
        if (change.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.firstOrNull { it.pressed }
            if (otherDown == null) return change
            pointer = otherDown.id
        } else if (change.previousPosition != change.position) {
            return change
        }
    }
}
