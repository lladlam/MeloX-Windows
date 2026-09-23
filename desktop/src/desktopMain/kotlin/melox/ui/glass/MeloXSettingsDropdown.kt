package melox.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import melox.ui.foundation.MeloXMotion
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.publicdemo.PublicInteractiveHighlight
import melox.ui.theme.MeloXTypography
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

private const val PopupOvershootScale = 1.15f
private val PopupOvershootStart = 64.dp
private val PopupOvershootVertical = 32.dp

internal val LocalSettingsGroupRowIndex = staticCompositionLocalOf { mutableIntStateOf(0) }

private class MeloXPopupPositionProvider(
    private val targetMenuHeightPx: Int,
    private val onDirectionResolved: (Boolean) -> Unit,
) : PopupPositionProvider {
    private var opensAbove: Boolean? = null

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val above = opensAbove ?: run {
            val roomBelow = windowSize.height - anchorBounds.bottom
            val roomAbove = anchorBounds.top
            (roomBelow < targetMenuHeightPx && roomAbove > roomBelow).also {
                opensAbove = it
                onDirectionResolved(it)
            }
        }
        val x = (anchorBounds.right - popupContentSize.width)
            .coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0))
        val y = if (above) anchorBounds.bottom - popupContentSize.height else anchorBounds.top
        return IntOffset(x, y.coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)))
    }
}

@Composable
fun <T> MeloXSettingsDropdown(
    title: String,
    selected: T,
    items: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    grouped: Boolean = false,
    showTopSeparator: Boolean = false,
) {
    val groupRowIndex = LocalSettingsGroupRowIndex.current
    val effectiveSeparator = if (grouped) groupRowIndex.intValue++.let { it > 0 } else showTopSeparator
    val selectedLabel = items.firstOrNull { it.first == selected }?.second.orEmpty()
    val row = @Composable {
        MeloXSettingsDropdownRow(
            title = title,
            trailing = {
                MeloXPopupSelector(
                    value = selectedLabel,
                    items = items,
                    selected = selected,
                    onSelected = onSelected,
                    enabled = enabled,
                )
            },
            showTopSeparator = effectiveSeparator,
        )
    }
    if (grouped) {
        row()
    } else {
        MeloXSettingsDropdownGroup(modifier = modifier) { row() }
    }
}

@Composable
private fun MeloXSettingsDropdownGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .meloXContentSurface(
                shape = MeloXShapes.largeCard,
                surfaceColor = MaterialTheme.colorScheme.surface,
            ),
        content = content,
    )
}

@Composable
private fun MeloXSettingsDropdownRow(
    title: String,
    trailing: @Composable () -> Unit,
    showTopSeparator: Boolean,
) {
    val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val density = LocalDensity.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .drawBehind {
                if (showTopSeparator) {
                    val inset = with(density) { 16.dp.toPx() }
                    drawLine(
                        color = separator,
                        start = Offset(inset, 0f),
                        end = Offset(size.width - inset, 0f),
                        strokeWidth = with(density) { 1.dp.toPx() },
                    )
                }
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MeloXTypography.body,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Composable
private fun <T> MeloXPopupSelector(
    value: String,
    items: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    var popupAlive by remember { mutableStateOf(false) }
    var opensAbove by remember { mutableStateOf(false) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }
    val progress = remember { Animatable(0f) }
    val backdrop = LocalMeloXBackdrop.current

    LaunchedEffect(expanded) {
        if (expanded) {
            popupAlive = true
            progress.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = MeloXMotion.DropdownStiffness, visibilityThreshold = 0.001f),
            )
        } else {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = MeloXMotion.DropdownStiffness, visibilityThreshold = 0.001f),
            )
            popupAlive = false
        }
    }

    Box(Modifier.onSizeChanged { anchorSize = it }) {
        Row(
            Modifier
                .graphicsLayer { alpha = if (expanded) 0f else 1f }
                .clickable(
                    enabled = enabled,
                    interactionSource = null,
                    indication = null,
                    role = Role.Button,
                    onClick = { expanded = !expanded },
                )
                .padding(horizontal = 2.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(value, style = MeloXTypography.body, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            MeloXSymbolIcon(
                MeloXSymbol.ChevronUpDown,
                Modifier.size(15.dp).padding(start = 7.dp),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                size = 15,
            )
        }

        if (popupAlive && anchorSize != IntSize.Zero) {
            val density = LocalDensity.current
            val targetHeight = with(density) { (20.dp + 44.dp * items.size).roundToPx() }
            val provider = remember(anchorSize, targetHeight) {
                MeloXPopupPositionProvider(targetHeight) { opensAbove = it }
            }
            Popup(
                popupPositionProvider = provider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(
                    focusable = expanded,
                    dismissOnBackPress = expanded,
                    dismissOnClickOutside = expanded,
                ),
            ) {
                MeloXPopupGlassMenu(
                    hasBackdrop = backdrop,
                    progress = progress.value,
                    velocity = progress.velocity,
                    interactive = expanded,
                    opensAbove = opensAbove,
                    collapsedSize = anchorSize,
                    itemCount = items.size,
                ) {
                    items.forEach { (item, label) ->
                        MeloXPopupMenuItem(
                            title = label,
                            checked = item == selected,
                            enabled = expanded,
                            onClick = { onSelected(item); expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MeloXPopupGlassMenu(
    hasBackdrop: Boolean,
    progress: Float,
    velocity: Float,
    interactive: Boolean,
    opensAbove: Boolean,
    collapsedSize: IntSize,
    itemCount: Int,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { PublicInteractiveHighlight(scope) }
    val density = LocalDensity.current
    val geometryProgress = progress.coerceIn(-.04f, 1.06f)
    val visualProgress = progress.coerceIn(0f, 1f)
    val normalizedVelocity = (velocity / 18f).coerceIn(-1f, 1f)
    val pulse = max(sin(PI.toFloat() * visualProgress), abs(normalizedVelocity) * .65f).coerceIn(0f, 1f)
    val collapsedWidth = with(density) { collapsedSize.width.toDp() }
    val collapsedHeight = with(density) { collapsedSize.height.toDp() }
    val menuWidth = 238.dp
    val menuHeight = 20.dp + 44.dp * itemCount
    val width = lerpDp(collapsedWidth, menuWidth, geometryProgress)
    val height = lerpDp(collapsedHeight, menuHeight, geometryProgress)
    val shape = MeloXShapes.largeCard
    val surface = MaterialTheme.colorScheme.surface
    val blurRadius = lerpDp(3.dp, 16.dp, visualProgress)

    Box(
        Modifier
            .padding(
                start = PopupOvershootStart,
                top = if (opensAbove) PopupOvershootVertical else 0.dp,
                bottom = if (opensAbove) 0.dp else PopupOvershootVertical,
            )
            .size(width = menuWidth * PopupOvershootScale, height = menuHeight * PopupOvershootScale),
    ) {
        Box(
            Modifier
                .align(if (opensAbove) Alignment.BottomEnd else Alignment.TopEnd)
                .blur(10.dp * (1f - visualProgress), BlurredEdgeTreatment.Unbounded)
                .graphicsLayer {
                    alpha = visualProgress
                    transformOrigin = TransformOrigin(1f, if (opensAbove) 1f else 0f)
                }
                .width(width)
                .height(height)
                .then(
                    if (hasBackdrop) Modifier.meloXGlassSurface(
                        shape = shape,
                        tint = Color.White.copy(alpha = 0.08f + 0.10f * pulse),
                        surfaceColor = surface.copy(alpha = 0.72f),
                        blurRadius = blurRadius,
                    ) else Modifier.meloXContentSurface(shape, surface)
                )
                .then(if (interactive) highlight.modifier else Modifier)
                .then(if (interactive) highlight.gestureModifier else Modifier),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .padding(10.dp)
                    .graphicsLayer {
                        val contentScale = .92f + .08f * visualProgress
                        scaleX = contentScale
                        scaleY = contentScale
                        transformOrigin = TransformOrigin(1f, if (opensAbove) 1f else 0f)
                    },
                content = content,
            )
        }
    }
}

@Composable
private fun MeloXPopupMenuItem(title: String, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { PublicInteractiveHighlight(scope) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color.Black.copy(alpha = .15f * highlight.pressProgress), MeloXShapes.capsule)
            .clickable(enabled = enabled, interactionSource = null, indication = null, onClick = onClick)
            .then(if (enabled) highlight.modifier else Modifier)
            .then(if (enabled) highlight.gestureModifier else Modifier)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(24.dp), contentAlignment = Alignment.Center) {
            if (checked) MeloXSymbolIcon(MeloXSymbol.Checkmark, Modifier.size(17.dp), MeloXSystemColors.Red, size = 17)
        }
        Text(title, style = MeloXTypography.body, modifier = Modifier.weight(1f).padding(start = 8.dp))
    }
}

private fun lerpDp(start: Dp, stop: Dp, fraction: Float): Dp =
    Dp(start.value + (stop.value - start.value) * fraction)
