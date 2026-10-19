package melox.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import melox.layout.*
import melox.settings.MeloXSettingsRuntime
import melox.ui.navigation.LocalMeloXBackDispatcher

internal val LocalMeloXPaneSize = staticCompositionLocalOf<IntSize?> { null }
internal val LocalMeloXListDetail = staticCompositionLocalOf { false }
internal val LocalMeloXInDetailPane = staticCompositionLocalOf { false }
private val LocalDetailPaneState = staticCompositionLocalOf<MeloXDetailPaneState?> { null }

internal class MeloXDetailPaneState {
    class Slot(val visible: State<Boolean>, val dismiss: State<(() -> Unit)?>, val content: @Composable () -> Unit)
    val slots = mutableStateListOf<Slot>()
}

/** One movable composition owns the detail, across toggle/resize and both pane placements. */
@Composable
internal fun MeloXAdaptiveDetail(visible: Boolean, onDismiss: (() -> Unit)? = null, content: @Composable (Boolean) -> Unit) {
    val state = LocalDetailPaneState.current
    if (state == null || LocalMeloXInDetailPane.current) {
        content(visible)
        return
    }
    val currentContent by rememberUpdatedState(content)
    val currentVisible = rememberUpdatedState(visible)
    val currentDismiss = rememberUpdatedState(onDismiss)
    val dispatcher = LocalMeloXBackDispatcher.current
    val currentDispatcher by rememberUpdatedState(dispatcher)
    val movable = remember {
        movableContentOf<Boolean> { shown ->
            CompositionLocalProvider(LocalMeloXInDetailPane provides true, LocalMeloXBackDispatcher provides currentDispatcher) {
                currentContent(shown)
            }
        }
    }
    val slot = remember { MeloXDetailPaneState.Slot(currentVisible, currentDismiss) { movable(currentVisible.value) } }
    DisposableEffect(state, slot) {
        state.slots.add(slot)
        onDispose { state.slots.remove(slot) }
    }
    if (!LocalMeloXListDetail.current) movable(visible)
}

@Composable
internal fun MeloXListDetailLayout(hasMedia: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val state = remember { MeloXDetailPaneState() }
    val split = meloXUsesListDetail(LocalWindowInfo.current.containerSize, MeloXSettingsRuntime.splitScreenEnabled)
    val navigation = MeloXSettingsRuntime.splitNavigationPosition
    val mini = MeloXSettingsRuntime.splitMiniPlayerPosition
    val divider = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(modifier.fillMaxSize().testTag(if (split) "layout-list-detail" else "layout-single-pane")) {
        val density = LocalDensity.current
        val size = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
        val leftWidth = if (split) size.width / 2 else size.width
        fun clearance(right: Boolean) = if (split) meloXPaneClearance(right, hasMedia, navigation, mini) else 0 to 0
        val (leftTop, leftSide) = clearance(false)
        val (rightTop, rightSide) = clearance(true)
        Layout(content = {
            Box(Modifier.fillMaxSize().testTag("layout-primary-pane").clipToBounds().padding(top = leftTop.dp).absolutePadding(left = leftSide.dp)) {
                CompositionLocalProvider(LocalDetailPaneState provides state, LocalMeloXListDetail provides split,
                    LocalMeloXPaneSize provides IntSize((leftWidth - with(density) { leftSide.dp.roundToPx() }).coerceAtLeast(0),
                        (size.height - with(density) { leftTop.dp.roundToPx() }).coerceAtLeast(0))) { content() }
            }
            Box(Modifier.fillMaxSize().testTag("layout-detail-pane").clipToBounds().padding(top = rightTop.dp).absolutePadding(right = rightSide.dp)) {
                if (split) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("layout-detail-background"),
                    contentAlignment = Alignment.Center) {
                    Image(painterResource("icons/pinned-v065-launcher-foreground.png"), null, Modifier.size(104.dp))
                }
                CompositionLocalProvider(LocalDetailPaneState provides state, LocalMeloXListDetail provides split, LocalMeloXInDetailPane provides true,
                    LocalMeloXPaneSize provides IntSize(((if (split) size.width - leftWidth else size.width) - with(density) { rightSide.dp.roundToPx() }).coerceAtLeast(0),
                        (size.height - with(density) { rightTop.dp.roundToPx() }).coerceAtLeast(0))) {
                    if (split) state.slots.forEach { slot -> key(slot) { slot.content() } }
                }
            }
            Box(Modifier.background(divider))
        }) { measurables, constraints ->
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val left = if (split) width / 2 else width
            val primary = measurables[0].measure(Constraints.fixed(left, height))
            val secondary = measurables[1].measure(Constraints.fixed(if (split) width - left else width, height))
            val line = measurables[2].measure(Constraints.fixed(if (split) 1 else 0, height))
            layout(width, height) {
                primary.place(0, 0)
                secondary.place(if (split) left else 0, 0)
                line.place(left, 0)
            }
        }
    }
}
