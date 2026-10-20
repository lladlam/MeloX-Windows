package melox.layout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeloXDesktopGeometryTest {
    @Test fun nativeLyricsSpecsViewportBreakpointGoldens() {
        for ((width, size) in listOf(258f to 24f, 299f to 24f, 300f to 28f, 527f to 28f,
            528f to 38f, 671f to 38f, 672f to 50f, 759f to 50f, 760f to 72f)) {
            assertEquals(size, meloXDesktopLyricsFontSize(width, compact = false))
            assertEquals(24f, meloXDesktopLyricsFontSize(width, compact = true))
        }
    }
    @Test fun sourceReferenceViewportGolden() {
        val g = MeloXDesktopPlayerGeometry(1200f, 768f)
        assertEquals(422f, g.playerWidth)
        assertEquals(308f, g.artworkSize)
        assertEquals(114f, g.artworkTop)
        assertEquals(80f, g.metadataTop)
        assertEquals(89f, g.playerLeading)
        assertEquals(600f, g.panelLeading)
        assertEquals(88f, g.lyricsTop)
    }

    @Test fun shortAndLargeSourceViewportGoldens() {
        val compact = MeloXDesktopPlayerGeometry(1239f, 600f)
        assertEquals(320f, compact.playerWidth)
        assertEquals(214f, compact.artworkSize)
        assertEquals(54f, compact.artworkTop)
        assertEquals(149.75f, compact.playerLeading)
        val large = MeloXDesktopPlayerGeometry(1620f, 1080f)
        assertEquals(1.35f, large.scale)
        assertEquals(415.8f, large.artworkSize, .001f)
        assertEquals(120.15f, large.playerLeading, .001f)
    }

    @Test fun sourceShelfBreakpointsAndInspectorReservation() {
        assertEquals(204f, meloXDesktopShelfWidth(980f, 200, 20))
        assertEquals(225f, meloXDesktopShelfWidth(1280f - 215f, 200, 20))
        assertEquals(221f, meloXDesktopShelfWidth(1280f - 215f - 258f, 200, 20))
        assertEquals(1f, meloXDesktopShelfWidth(80f, 200, 20))
        for (width in 980..2200) {
            val g = MeloXDesktopPlayerGeometry(width.toFloat(), 768f)
            assertEquals(width * .5f, g.panelLeading)
            assertTrue(g.playerLeading >= 0 && g.panelTrailing >= 32)
        }
    }
}
