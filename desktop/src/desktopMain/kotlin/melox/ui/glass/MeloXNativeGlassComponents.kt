package melox.ui.glass

import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
import melox.ui.glass.publicdemo.PublicDampedDragAnimation
import melox.ui.glass.publicdemo.publicInspectDragGestures
import kotlin.math.roundToInt

/**
 * Desktop port of Android ui/glass/MeloXNativeGlassComponents.kt.
 * Optical stack is the Windows Skia glass surface; interaction math matches Android.
 */
@Composable
fun MeloXGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: MeloXGlassButtonStyle = MeloXGlassButtonStyle.Bordered,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    shape: Shape = MeloXShapes.capsule,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        PublicInteractiveHighlight(animationScope)
    }
    val buttonTint = when (style) {
        MeloXGlassButtonStyle.Bordered -> tint
        MeloXGlassButtonStyle.BorderedProminent -> MeloXSystemColors.Red
        MeloXGlassButtonStyle.Plain -> Color.Transparent
        MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red
    }
    val buttonSurface = when {
        surfaceColor != Color.Unspecified -> surfaceColor
        style == MeloXGlassButtonStyle.BorderedProminent -> MeloXSystemColors.Red.copy(alpha = 0.92f)
        style == MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red.copy(alpha = 0.12f)
        style == MeloXGlassButtonStyle.Plain -> Color.Transparent
        else -> Color.Unspecified
    }
    val contentColor = when (style) {
        MeloXGlassButtonStyle.BorderedProminent -> Color.White
        MeloXGlassButtonStyle.Destructive -> MeloXSystemColors.Red
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = modifier
            .meloXGlassSurface(
                shape = shape,
                enabled = enabled,
                tint = buttonTint,
                surfaceColor = buttonSurface,
                pressProgress = if (enabled) interactiveHighlight.pressProgress else 0f,
                dragOffset = if (enabled) interactiveHighlight.offset else androidx.compose.ui.geometry.Offset.Zero,
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .then(if (enabled && !melox.settings.MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled && !melox.settings.MeloXSettingsRuntime.frostedGlassEnabled) interactiveHighlight.gestureModifier else Modifier)
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

@Composable
fun MeloXGlassIconButton(
    symbol: MeloXSymbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(44.dp),
    enabled: Boolean = true,
    selected: Boolean = false,
    contentDescription: String? = null,
) {
    MeloXGlassButton(
        onClick = onClick,
        enabled = enabled,
        shape = MeloXShapes.circle,
        contentPadding = PaddingValues(10.dp),
        modifier = modifier.semantics {
            this.contentDescription = contentDescription ?: symbol.sfName
        },
    ) {
        MeloXSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(24.dp),
            color = if (selected) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface,
            size = 24,
        )
    }
}

/** Compact iOS-style switch used by settings and provider controls. */
val MeloXToggleVisualProgressKey = SemanticsPropertyKey<Float>("MeloXToggleVisualProgress")

@Composable
fun MeloXGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (melox.ui.desktop.LocalDesktopSettingsPresentation.current) {
        Box(modifier.width(14.dp)) { melox.ui.desktop.DesktopSettingsCheckbox("", checked, "desktop-checkbox", enabled, onChange = onCheckedChange) }
        return
    }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accent = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val track = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.20f)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val travelPx = with(density) { 20.dp.toPx() }
    val tapThresholdPx = with(density) { 2.dp.toPx() }
    val scope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var dragDistance by remember { mutableStateOf(0f) }
    var settleRevision by remember { mutableIntStateOf(0) }
    val animated = remember { Animatable(if (checked) 1f else 0f) }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)
    val trackBackdrop = rememberMeloXLayerBackdrop()
    val pageBackdrop = LocalMeloXLayerBackdrop.current
    val optics = remember(scope) {
        PublicDampedDragAnimation(scope, if (checked) 1f else 0f, 0f..1f, .001f, 1f, 1.5f,
            onDrag = { _, _ -> })
    }
    LaunchedEffect(optics) { snapshotFlow { animated.value }.collectLatest(optics::updateValue) }

    androidx.compose.runtime.LaunchedEffect(checked, didDrag, settleRevision) {
        val target = if (checked) 1f else 0f
        if (kotlin.math.abs(animated.value - target) > 0.01f && !didDrag) {
            animated.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 480f))
        }
    }

    fun settle(target: Float) {
        currentOnChange(target == 1f)
        // Callers may require confirmation or reject the change. Only checked owns
        // the resting appearance; drag position is transient until the release.
        didDrag = false
        settleRevision++
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(28.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                this[MeloXToggleVisualProgressKey] = animated.value
                if (!enabled) disabled()
                onClick {
                    if (enabled) onCheckedChange(!checked)
                    enabled
                }
            }
            .pointerInput(enabled, optics) {
                if (!enabled) return@pointerInput
                // Read-only press observer keeps the optical response alive while
                // the existing state-owning drag detector consumes movement.
                publicInspectDragGestures(onDragStart = { optics.press() }, onDragEnd = { optics.release() },
                    onDragCancel = { optics.release() }) { _, _ -> }
            }
            .pointerInput(enabled, travelPx) {
                if (!enabled) return@pointerInput
                detectTapGestures {
                    if (!didDrag) settle(if (currentChecked) 0f else 1f)
                    didDrag = false
                }
            }
            .pointerInput(enabled, travelPx, isLtr) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { didDrag = false; dragDistance = 0f },
                    onHorizontalDrag = { _, dragAmount ->
                        dragDistance += kotlin.math.abs(dragAmount)
                        if (dragDistance > tapThresholdPx) didDrag = true
                        val delta = dragAmount / travelPx * if (isLtr) 1f else -1f
                        val next = (animated.value + delta).coerceIn(0f, 1f)
                        scope.launch { animated.snapTo(next) }
                    },
                    onDragEnd = {
                        if (didDrag) settle(if (animated.value >= 0.5f) 1f else 0f)
                        else settleRevision++
                        didDrag = false
                    },
                    onDragCancel = { didDrag = false; settleRevision++ },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val shown = animated.value
        Box(
            Modifier
                .then(if (pageBackdrop != null) Modifier.meloXRecordBackdrop(trackBackdrop) else Modifier)
                .clip(MeloXShapes.capsule)
                .drawBehind { drawRect(colorLerp(track, accent, shown)) }
                .size(width = 64.dp, height = 28.dp),
        )
        Box(
            Modifier
                .offset {
                    val padding = with(density) { 2.dp.roundToPx() }
                    IntOffset(
                        x = (padding + travelPx * shown).roundToInt(),
                        y = padding,
                    )
                }
                .graphicsLayer {
                    val velocity = optics.velocity.glassFinite() / 50f
                    scaleX = if (pageBackdrop == null) 1f else (optics.scaleX.glassFinite() / (1f - (velocity * .75f).coerceIn(-.2f, .2f))).glassFinite()
                    scaleY = if (pageBackdrop == null) 1f else (optics.scaleY.glassFinite() * (1f - (velocity * .25f).coerceIn(-.2f, .2f))).glassFinite()
                    alpha = if (enabled) 1f else .45f
                }
                .then(if (pageBackdrop == null) Modifier.background(Color.White, MeloXShapes.capsule) else {
                    val p = safeGlassProgress(optics.pressProgress)
                    Modifier.meloXGlassShadow(MeloXShapes.capsule, 1f, .05f, 4.dp)
                        .meloXGlassDecoration(MeloXShapes.capsule, p, 4.dp * p, p, .15f,
                            rimWidth = .5.dp / 1.5f, ambient = true)
                        .clip(MeloXShapes.capsule)
                        .meloXSampleBackdrop(8.dp * (1f - p), trackBackdrop, shape = MeloXShapes.capsule,
                            lensHeight = if (p > .001f) 5.dp * p else 0.dp,
                            lensAmount = if (p > .001f) 10.dp * p else 0.dp, dispersion = true, underlay = pageBackdrop)
                        .drawBehind { drawRect(Color.White.copy(alpha = 1f - p)) }
                })
                .size(width = 40.dp, height = 24.dp),
        )
    }
}

@Composable
fun MeloXGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusChanged: ((Boolean) -> Unit)? = null,
) {
    if (melox.ui.desktop.LocalDesktopSettingsPresentation.current) {
        Row(modifier.heightIn(min = 24.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
            .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f), androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leadingContent?.invoke()
            BasicTextField(value, onValueChange, enabled = enabled, singleLine = singleLine,
                textStyle = TextStyle(fontFamily = melox.ui.theme.MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                modifier = Modifier.weight(1f).onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
                decorationBox = { inner -> Box { if (value.isBlank()) placeholder?.invoke(); inner() } })
            trailingContent?.invoke()
        }
        return
    }
    Row(
        modifier = modifier
            .heightIn(min = 50.dp)
            .meloXGlassSurface(
                shape = MeloXShapes.capsule,
                enabled = enabled,
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f),
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leadingContent?.invoke()
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            textStyle = textStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) placeholder?.invoke()
                    innerTextField()
                }
            },
        )
        trailingContent?.invoke()
    }
}

@Composable
fun MeloXGlassToolbarButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    MeloXGlassButton(
        onClick = onClick,
        modifier = modifier,
        shape = MeloXShapes.capsule,
        style = if (selected) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
        tint = if (selected) MeloXSystemColors.Red.copy(alpha = 0.22f) else Color.Unspecified,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = if (selected) MeloXSystemColors.Red else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun MeloXGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MeloXShapes.card,
    material: MeloXGlassMaterial = MeloXGlassMaterial.Regular,
    surfaceColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .meloXGlassSurface(
                shape = shape,
                surfaceColor = surfaceColor,
            )
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ) else Modifier,
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun MeloXGlassSegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String? = null,
) {
    Row(
        modifier = modifier
            .meloXGlassSurface(
                shape = MeloXShapes.capsule,
                surfaceColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.055f),
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            MeloXGlassButton(
                onClick = { onSelected(index) },
                modifier = Modifier.weight(1f).semantics { selected = index == selectedIndex }
                    .then(tagPrefix?.let { Modifier.testTag("$it-$index") } ?: Modifier),
                style = if (index == selectedIndex) MeloXGlassButtonStyle.BorderedProminent
                else MeloXGlassButtonStyle.Plain,
                shape = MeloXShapes.capsule,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item,
                    style = melox.ui.theme.MeloXTypography.subheadline,
                    maxLines = 1,
                )
            }
        }
    }
}
