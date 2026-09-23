package melox.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import melox.ui.glass.publicdemo.PublicDampedDragAnimation
import kotlin.math.roundToInt

@Composable
fun MeloXLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onTransientValueChange: (Float) -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 100f,
    visibilityThreshold: Float = 0.01f,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    require(valueRange.endInclusive > valueRange.start) { "valueRange must not be empty" }
    require(stepSize >= 0f) { "stepSize must be non-negative" }
    val externalValue = quantizeMeloXSliderValue(value, valueRange, stepSize)
    var transientValue by remember { mutableFloatStateOf(externalValue) }
    var interacting by remember { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnTransientValueChange by rememberUpdatedState(onTransientValueChange)
    val steps = if (stepSize > 0f) {
        (((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt() - 1).coerceAtLeast(0)
    } else 0

    LaunchedEffect(externalValue, interacting) {
        if (!interacting) transientValue = externalValue
    }

    fun finish() {
        val settled = quantizeMeloXSliderValue(transientValue, valueRange, stepSize)
        transientValue = settled
        interacting = false
        if (settled != externalValue) latestOnValueChange(settled)
    }

    val semantics = Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo(transientValue, valueRange, steps)
        setProgress { requested ->
            transientValue = quantizeMeloXSliderValue(requested, valueRange, stepSize)
            finish()
            true
        }
    }
    val backdrop = LocalMeloXBackdrop.current
    if (!backdrop) {
        Slider(
            value = transientValue,
            onValueChange = {
                interacting = true
                transientValue = it.coerceIn(valueRange)
                latestOnTransientValueChange(transientValue)
            },
            onValueChangeFinished = ::finish,
            valueRange = valueRange,
            steps = steps,
            modifier = modifier.then(semantics),
        )
        return
    }

    MeloXDesktopLiquidSlider(
        value = { transientValue },
        onValueChange = {
            interacting = true
            transientValue = it.coerceIn(valueRange)
            latestOnTransientValueChange(transientValue)
        },
        onValueChangeFinished = ::finish,
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        modifier = modifier.then(semantics),
    )
}

@Composable
private fun MeloXDesktopLiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
) {
    val accentColor = Color(0xFF0091FF)
    val trackColor = Color(0xFF787880).copy(0.36f)

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            PublicDampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStopped = {
                    if (didDrag) onValueChange(targetValue)
                    onValueChangeFinished()
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange),
                    )
                },
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { current ->
                    if (dampedDragAnimation.targetValue != current) {
                        dampedDragAnimation.updateValue(current)
                    }
                }
        }

        Box(
            Modifier
                .clip(MeloXShapes.capsule)
                .background(trackColor)
                .pointerInput(animationScope) {
                    detectTapGestures { position ->
                        val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                        val targetValue = (if (isLtr) valueRange.start + delta
                        else valueRange.endInclusive - delta).coerceIn(valueRange)
                        dampedDragAnimation.animateToValue(targetValue)
                        onValueChange(targetValue)
                        onValueChangeFinished()
                    }
                }
                .height(6.dp)
                .fillMaxWidth(),
        )
        Box(
            Modifier
                .clip(MeloXShapes.capsule)
                .background(accentColor)
                .height(6.dp)
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    val width = (constraints.maxWidth * dampedDragAnimation.progress).roundToInt()
                    layout(width, placeable.height) {
                        placeable.place(0, 0)
                    }
                },
        )
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                    val velocity = dampedDragAnimation.velocity / 10f
                    scaleX = dampedDragAnimation.scaleX / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))
                    scaleY = dampedDragAnimation.scaleY * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))
                }
                .then(dampedDragAnimation.modifier)
                .meloXLiquidButton(
                    shape = MeloXShapes.capsule,
                    surfaceColor = Color.White.copy(alpha = 1f - dampedDragAnimation.pressProgress),
                    blurRadius = 8.dp * (1f - dampedDragAnimation.pressProgress),
                )
                .size(40.dp, 24.dp),
        )
    }
}

internal fun quantizeMeloXSliderValue(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    stepSize: Float,
): Float {
    val clamped = value.coerceIn(range.start, range.endInclusive)
    if (stepSize <= 0f) return clamped
    val steps = ((clamped - range.start) / stepSize).roundToInt()
    return (range.start + steps * stepSize).coerceIn(range.start, range.endInclusive)
}
