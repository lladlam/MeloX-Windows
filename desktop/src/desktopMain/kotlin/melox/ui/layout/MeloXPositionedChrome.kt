package melox.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import melox.layout.*
import melox.settings.MeloXSplitChromePosition

/** Exact physical-half placement from Android v0.6.5, with measured native safe insets. */
@Composable
internal fun MeloXPositionedChrome(split: Boolean, hasMedia: Boolean, coupledProgress: Float,
    navigationPosition: MeloXSplitChromePosition, miniPlayerPosition: MeloXSplitChromePosition,
    modifier: Modifier = Modifier, miniPlayer: @Composable () -> Unit, navigation: @Composable () -> Unit) {
    val safeInsets = WindowInsets.safeDrawing
    val direction = LocalLayoutDirection.current
    Layout(modifier = modifier, content = {
        Box(Modifier.clipToBounds().testTag("positioned-navigation")) { navigation() }
        Box(Modifier.clipToBounds().testTag("positioned-mini-player")) { if (split && hasMedia) miniPlayer() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val viewport = IntSize(width, height)
        val gap = 8.dp.roundToPx()
        val railWidth = 72.dp.roundToPx()
        val safeLeft = safeInsets.getLeft(this, direction).coerceAtLeast(gap)
        val safeRight = safeInsets.getRight(this, direction).coerceAtLeast(gap)
        val safeTop = safeInsets.getTop(this).coerceAtLeast(gap)
        fun controlConstraints(position: MeloXSplitChromePosition, other: MeloXSplitChromePosition?, mini: Boolean): Constraints {
            if (!split) return Constraints(minWidth = width, maxWidth = width, maxHeight = height)
            val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop)
            val childWidth = if (position.isSideRail) railWidth.coerceAtMost(area.width) else area.width
            return Constraints(minWidth = childWidth, maxWidth = childWidth, maxHeight = area.height)
        }
        val nav = measurables[0].measure(controlConstraints(navigationPosition, miniPlayerPosition.takeIf { hasMedia }, false))
        val mini = measurables[1].measure(if (split && hasMedia) controlConstraints(miniPlayerPosition, navigationPosition, true) else Constraints.fixed(0, 0))
        val offsets = if (split) meloXChromeOffsets(viewport, IntSize(nav.width, nav.height), IntSize(mini.width, mini.height),
            navigationPosition, miniPlayerPosition, gap, railWidth, safeLeft, safeRight, safeTop, coupledProgress)
            else MeloXChromeOffsets(IntOffset.Zero, IntOffset.Zero)
        layout(width, if (split) height else nav.height) {
            nav.place(offsets.navigation)
            mini.place(offsets.miniPlayer)
        }
    }
}
