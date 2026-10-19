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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import melox.ui.glass.publicdemo.PublicDampedDragAnimation
import kotlin.math.roundToInt

/** Read-only optical state for assertions; gestures still enter through pointer input. */
internal val MeloXSliderOpticalStateKey = SemanticsPropertyKey<List<Float>>("MeloXSliderOpticalState")

@Composable
fun MeloXLiquidSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onTransientValueChange: (Float) -> Unit = {},
    valueRange: ClosedFloatingPointRange<Float>,
    stepSize: Float = 0f,
    visibilityThreshold: Float = 0.01f,
    modifier: Modifier = Modifier,
    contentDescription: String,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
) {
    require(valueRange.endInclusive > valueRange.start) { "valueRange must not be empty" }
    require(stepSize >= 0f) { "stepSize must be non-negative" }
    val externalValue = quantizeMeloXSliderValue(value, valueRange, stepSize)
    val currentEnabled = rememberUpdatedState(enabled)
    val currentExternalValue = rememberUpdatedState(externalValue)
    var transientValue by remember { mutableFloatStateOf(externalValue) }
    var interacting by remember { mutableStateOf(false) }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnTransientValueChange by rememberUpdatedState(onTransientValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val steps = if (stepSize > 0f) {
        (((valueRange.endInclusive - valueRange.start) / stepSize).roundToInt() - 1).coerceAtLeast(0)
    } else 0

    LaunchedEffect(externalValue, interacting) {
        if (!interacting) transientValue = externalValue
    }

    fun finish() {
        // Node removal can synchronously finish/cancel the previous detector
        // before its remembered callback is refreshed. Read current state here.
        if (!currentEnabled.value) {
            transientValue = currentExternalValue.value
            interacting = false
            return
        }
        val settled = quantizeMeloXSliderValue(transientValue, valueRange, stepSize)
        transientValue = settled
        interacting = false
        if (settled != externalValue) latestOnValueChange(settled)
        latestOnFinished()
    }

    val semantics = Modifier.semantics(mergeDescendants = true) {
        this.contentDescription = contentDescription
        progressBarRangeInfo = ProgressBarRangeInfo(transientValue, valueRange, steps)
        if (!enabled) disabled()
        if (enabled) setProgress { requested ->
            transientValue = quantizeMeloXSliderValue(requested, valueRange, stepSize)
            finish()
            true
        }
    }
    val backdrop = LocalMeloXLayerBackdrop.current
    if (backdrop == null || melox.settings.MeloXSettingsRuntime.frostedGlassEnabled) {
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
            enabled = enabled,
            modifier = modifier.then(semantics),
        )
        return
    }

    MeloXDesktopLiquidSlider(
        value = { transientValue },
        onValueChange = {
            if (currentEnabled.value) {
                interacting = true
                transientValue = it.coerceIn(valueRange)
                latestOnTransientValueChange(transientValue)
            }
        },
        onValueChangeFinished = ::finish,
        onValueChangeCancelled = { transientValue = currentExternalValue.value; interacting = false },
        valueRange = valueRange,
        visibilityThreshold = visibilityThreshold,
        modifier = modifier.then(semantics),
        enabled = enabled,
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
    onValueChangeCancelled: () -> Unit = {},
    enabled: Boolean = true,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accentColor = if (dark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val trackColor = if (dark) Color(0xFF787880).copy(.36f) else Color(0xFF787878).copy(.20f)
    val trackBackdrop = rememberMeloXLayerBackdrop()
    val pageBackdrop = LocalMeloXLayerBackdrop.current

    BoxWithConstraints(
        modifier.fillMaxWidth().graphicsLayer { alpha = if (enabled) 1f else .45f },
        contentAlignment = Alignment.CenterStart,
    ) {
        val trackWidth = constraints.maxWidth
        val latestTrackWidth by rememberUpdatedState(trackWidth)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val latestOnValueChange by rememberUpdatedState(onValueChange)
        val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
        val latestOnCancelled by rememberUpdatedState(onValueChangeCancelled)
        val dampedDragAnimation = remember(animationScope, valueRange, visibilityThreshold, isLtr) {
            PublicDampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { didDrag = false },
                onDragStopped = {
                    if (didDrag) latestOnValueChange(targetValue)
                    latestOnFinished()
                },
                onTap = { latestOnFinished() },
                onDragCancelled = { didDrag = false; latestOnCancelled() },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / latestTrackWidth.coerceAtLeast(1))
                    latestOnValueChange(
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

        Box(Modifier.meloXRecordBackdrop(trackBackdrop)) {
        Box(
            Modifier
                .clip(MeloXShapes.capsule)
                .background(trackColor)
                .pointerInput(animationScope, enabled, isLtr, trackWidth, valueRange) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { position ->
                        val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth.coerceAtLeast(1))
                        val targetValue = (if (isLtr) valueRange.start + delta
                        else valueRange.endInclusive - delta).coerceIn(valueRange)
                        dampedDragAnimation.animateToValue(targetValue)
                        latestOnValueChange(targetValue)
                        latestOnFinished()
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
                    val width = (constraints.maxWidth * safeGlassProgress(dampedDragAnimation.progress)).roundToInt()
                    layout(width, placeable.height) {
                        placeable.place(0, 0)
                    }
                },
        )
        }
        val p = safeGlassProgress(dampedDragAnimation.pressProgress)
        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * safeGlassProgress(dampedDragAnimation.progress))
                            .coerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                    val velocity = dampedDragAnimation.velocity.glassFinite() / 10f
                    scaleX = (dampedDragAnimation.scaleX.glassFinite() / (1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f))).glassFinite()
                    scaleY = (dampedDragAnimation.scaleY.glassFinite() * (1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f))).glassFinite()
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .semantics {
                    this[MeloXSliderOpticalStateKey] = listOf(p, dampedDragAnimation.scaleX, dampedDragAnimation.scaleY)
                }
                .meloXGlassShadow(MeloXShapes.capsule, 1f, .05f, 4.dp)
                .meloXGlassDecoration(MeloXShapes.capsule, p, 4.dp * p, p, .15f,
                    rimWidth = .5.dp / 1.5f, ambient = true)
                .clip(MeloXShapes.capsule)
                .meloXSampleBackdrop(8.dp * (1f - p), trackBackdrop, shape = MeloXShapes.capsule,
                    lensHeight = if (p > .001f) 10.dp * p else 0.dp,
                    lensAmount = if (p > .001f) 14.dp * p else 0.dp,
                    dispersion = true, underlay = pageBackdrop, sampleScale = Offset(2f / 3f + p / 3f, p))
                .drawBehind { drawRect(Color.White.copy(alpha = 1f - p)) }
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
