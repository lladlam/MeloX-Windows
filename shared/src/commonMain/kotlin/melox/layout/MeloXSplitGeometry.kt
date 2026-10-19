package melox.layout

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import melox.settings.MeloXSplitChromePosition
import kotlin.math.roundToInt

/** Official v0.6.5 window policy: aspect ratio, not a desktop width breakpoint. */
fun meloXUsesListDetail(size: IntSize, enabled: Boolean = true): Boolean = enabled && size.height > 0 && size.width > size.height

fun meloXSupportsPlayerTwoPane(widthDp: Int, heightDp: Int, enabled: Boolean = true): Boolean =
    enabled && widthDp >= 840 && heightDp >= 600

data class MeloXChromeOffsets(val navigation: IntOffset, val miniPlayer: IntOffset)

/** Divide before applying safe insets, so controls and content share the same divider. */
fun meloXChromeArea(viewport: IntSize, position: MeloXSplitChromePosition, otherPosition: MeloXSplitChromePosition?,
    miniPlayer: Boolean, margin: Int, railWidth: Int, safeLeft: Int = margin, safeRight: Int = margin, safeTop: Int = margin): IntRect {
    val divider = viewport.width / 2
    var left = if (position.right) divider + margin else safeLeft.coerceAtLeast(margin)
    var right = if (position.right) viewport.width - safeRight.coerceAtLeast(margin) else divider - margin
    if (otherPosition?.right == position.right && otherPosition.isSideRail && (!position.isSideRail || miniPlayer)) {
        val spacing = if (position == otherPosition && position.isSideRail) (margin / 8).coerceAtLeast(1) else margin
        if (position.right) right -= railWidth + spacing else left += railWidth + spacing
    }
    right = right.coerceAtLeast(left)
    return IntRect(left, safeTop, right, (viewport.height - margin).coerceAtLeast(safeTop))
}

fun meloXChromeOffsets(viewport: IntSize, navigation: IntSize, miniPlayer: IntSize,
    navigationPosition: MeloXSplitChromePosition, miniPlayerPosition: MeloXSplitChromePosition,
    gap: Int, railWidth: Int = 72, safeLeft: Int = gap, safeRight: Int = gap, safeTop: Int = gap,
    coupledProgress: Float = 0f): MeloXChromeOffsets {
    fun anchor(size: IntSize, position: MeloXSplitChromePosition, mini: Boolean): IntOffset {
        val other = if (mini) navigationPosition else miniPlayerPosition.takeIf { miniPlayer.height > 0 }
        val area = meloXChromeArea(viewport, position, other, mini, gap, railWidth, safeLeft, safeRight, safeTop)
        return IntOffset(if (position.right) area.right - size.width else area.left,
            when (position.vertical) {
                -1 -> area.top
                0 -> area.top + (area.height - size.height) / 2
                else -> area.bottom - size.height
            }.coerceAtLeast(area.top))
    }
    val nav = anchor(navigation, navigationPosition, false)
    var mini = anchor(miniPlayer, miniPlayerPosition, true)
    val overlaps = miniPlayer.height > 0 && mini.x < nav.x + navigation.width && nav.x < mini.x + miniPlayer.width &&
        mini.y < nav.y + navigation.height + gap && nav.y < mini.y + miniPlayer.height + gap
    if (overlaps) {
        val above = nav.y - miniPlayer.height - gap
        val below = nav.y + navigation.height + gap
        mini = IntOffset(mini.x, when {
            miniPlayerPosition.vertical == -1 -> below
            above >= safeTop -> above
            else -> below
        }.coerceIn(safeTop, (viewport.height - gap - miniPlayer.height).coerceAtLeast(safeTop)))
    }
    if (navigationPosition == miniPlayerPosition && navigationPosition.isSideRail && miniPlayer.height > 0) {
        val progress = coupledProgress.coerceIn(0f, 1f)
        mini = IntOffset((mini.x + (nav.x - mini.x) * progress).roundToInt(),
            (mini.y + (nav.y + (navigation.height - miniPlayer.height) / 2 - mini.y) * progress).roundToInt())
    }
    return MeloXChromeOffsets(nav, mini)
}

/** Top and outer-side pane reservations, in dp, from MeloXListDetailLayout. */
fun meloXPaneClearance(right: Boolean, hasMedia: Boolean, navigation: MeloXSplitChromePosition,
    miniPlayer: MeloXSplitChromePosition): Pair<Int, Int> {
    val positions = listOfNotNull(navigation, miniPlayer.takeIf { hasMedia }).filter { it.right == right }
    return positions.count { it.vertical == -1 } * 80 to positions.count { it.isSideRail } * 80
}
