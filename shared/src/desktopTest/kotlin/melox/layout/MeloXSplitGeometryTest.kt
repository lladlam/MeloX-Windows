package melox.layout

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import melox.settings.MeloXSplitChromePosition.*
import melox.settings.MeloXSplitChromePosition
import melox.settings.meloXChromeScrollCoupled
import kotlin.test.*

/** Official Android v0.6.5 golden coordinates, plus native safe-area/odd-width edges. */
class MeloXSplitGeometryTest {
    private val viewport = IntSize(800, 320)

    @Test fun distinctWindowPoliciesAndToggle() {
        assertTrue(meloXUsesListDetail(IntSize(800, 360)))
        assertFalse(meloXUsesListDetail(IntSize(800, 360), false))
        assertFalse(meloXUsesListDetail(IntSize(360, 800)))
        assertFalse(meloXUsesListDetail(IntSize(800, 800)))
        assertFalse(meloXUsesListDetail(IntSize(800, 0)))
        assertFalse(meloXSupportsPlayerTwoPane(792, 360))
        assertFalse(meloXSupportsPlayerTwoPane(840, 599))
        assertTrue(meloXSupportsPlayerTwoPane(840, 600))
        assertFalse(meloXSupportsPlayerTwoPane(840, 600, false))
    }

    @Test fun goldenSharedCorners() {
        val bottom = meloXChromeOffsets(viewport, IntSize(384, 72), IntSize(384, 68), BottomLeft, BottomLeft, 8)
        assertEquals(IntOffset(8, 240), bottom.navigation)
        assertEquals(IntOffset(8, 164), bottom.miniPlayer)
        val top = meloXChromeOffsets(viewport, IntSize(384, 72), IntSize(384, 68), TopRight, TopRight, 8)
        assertEquals(IntOffset(408, 8), top.navigation)
        assertEquals(IntOffset(408, 88), top.miniPlayer)
    }

    @Test fun goldenRailsAndCompactSlot() {
        val nav = IntSize(72, 272)
        val mini = IntSize(72, 200)
        val left = meloXChromeOffsets(viewport, nav, mini, Left, Left, 8)
        assertEquals(IntOffset(8, 24), left.navigation)
        assertEquals(IntOffset(81, 60), left.miniPlayer)
        val compact = meloXChromeOffsets(viewport, nav, mini, Left, Left, 8, coupledProgress = 1f)
        assertEquals(IntOffset(8, 60), compact.miniPlayer)
        val right = meloXChromeOffsets(viewport, nav, mini, Right, Right, 8)
        assertEquals(IntOffset(720, 24), right.navigation)
        assertEquals(IntOffset(647, 60), right.miniPlayer)
    }

    @Test fun railsOwnOuterEdgeAndSafeInsetsDoNotMoveDivider() {
        val left = meloXChromeArea(IntSize(801, 600), BottomLeft, Left, false, 8, 72, 24, 32, 20)
        assertEquals(104, left.left)
        assertEquals(392, left.right)
        assertEquals(20, left.top)
        val right = meloXChromeArea(IntSize(801, 600), TopRight, null, false, 8, 72, 24, 32, 20)
        assertEquals(408, right.left)
        assertEquals(769, right.right)
        assertEquals(592, right.bottom)
    }

    @Test fun everyPositionPairHasExpectedPaneReservationAndCoupling() {
        for (nav in MeloXSplitChromePosition.entries) for (mini in MeloXSplitChromePosition.entries) {
            assertEquals(nav == mini, meloXChromeScrollCoupled(true, nav, mini))
            assertTrue(meloXChromeScrollCoupled(false, nav, mini))
            val noMedia = meloXPaneClearance(false, false, nav, mini)
            assertEquals(if (!nav.right && nav.vertical == -1) 80 else 0, noMedia.first)
            assertEquals(if (!nav.right && nav.isSideRail) 80 else 0, noMedia.second)
        }
        assertEquals(160 to 0, meloXPaneClearance(true, true, TopRight, TopRight))
        assertEquals(0 to 160, meloXPaneClearance(false, true, Left, Left))
        assertEquals(BottomLeft, MeloXSplitChromePosition.fromPreference("unknown"))
    }
}
