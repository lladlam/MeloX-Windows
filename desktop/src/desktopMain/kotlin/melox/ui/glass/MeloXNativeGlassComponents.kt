package melox.ui.glass

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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as colorLerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
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
            .then(if (enabled) interactiveHighlight.modifier else Modifier)
            .then(if (enabled) interactiveHighlight.gestureModifier else Modifier)
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
@Composable
fun MeloXGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dark = true
    val accent = if (dark) Color(0xFF30D158) else Color(0xFF34C759)
    val track = if (dark) Color(0xFF787880).copy(alpha = 0.36f) else Color(0xFF787878).copy(alpha = 0.20f)
    val density = LocalDensity.current
    val travelPx = with(density) { 20.dp.toPx() }
    val tapThresholdPx = with(density) { 2.dp.toPx() }
    val scope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val animated = remember { Animatable(if (checked) 1f else 0f) }
    val currentChecked by rememberUpdatedState(checked)
    val currentOnChange by rememberUpdatedState(onCheckedChange)

    androidx.compose.runtime.LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (kotlin.math.abs(animated.value - target) > 0.01f && !didDrag) {
            animated.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 480f))
            fraction = target
        }
    }

    fun settle(target: Float) {
        scope.launch {
            animated.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 480f))
        }
        fraction = target
        currentOnChange(target == 1f)
    }

    Box(
        modifier = modifier
            .width(64.dp)
            .height(28.dp)
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
                onClick {
                    if (enabled) onCheckedChange(!checked)
                    enabled
                }
            }
            .pointerInput(enabled, travelPx) {
                if (!enabled) return@pointerInput
                detectTapGestures {
                    if (!didDrag) settle(if (currentChecked) 0f else 1f)
                    didDrag = false
                }
            }
            .pointerInput(enabled, travelPx) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { didDrag = false },
                    onHorizontalDrag = { _, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > tapThresholdPx) didDrag = true
                        val next = (animated.value + dragAmount / travelPx).coerceIn(0f, 1f)
                        fraction = next
                        scope.launch { animated.snapTo(next) }
                    },
                    onDragEnd = {
                        if (didDrag) settle(if (animated.value >= 0.5f) 1f else 0f)
                        didDrag = false
                    },
                    onDragCancel = { didDrag = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val shown = animated.value
        Box(
            Modifier
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
                .background(Color.White, MeloXShapes.capsule)
                .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
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
                modifier = Modifier.weight(1f),
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
