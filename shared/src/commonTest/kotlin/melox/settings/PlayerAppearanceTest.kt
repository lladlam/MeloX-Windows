package melox.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PlayerAppearanceTest {
    @Test
    fun backgroundModesMapToDistinctKindsAndShellsDiffer() {
        val kinds = MeloXPlayerBackgroundMode.entries.map(::playerBackdropKind)
        assertEquals(4, kinds.distinct().size)
        assertEquals(
            listOf(
                MeloXPlayerBackdropKind.FlowingLight,
                MeloXPlayerBackdropKind.AppleLyrics,
                MeloXPlayerBackdropKind.BlurredArtwork,
                MeloXPlayerBackdropKind.MeiMesh,
            ),
            kinds,
        )
        assertNotEquals(
            resolvedPlayerShell(MeloXPlayerShell.Classic),
            resolvedPlayerShell(MeloXPlayerShell.AppleMusic),
        )
        assertEquals(MeloXResolvedPlayerShell.ClassicColumn, resolvedPlayerShell(MeloXPlayerShell.Classic))
        assertTrue(playerShowsLyricsArtworkBackdrop(MeloXPlayerBackgroundMode.AppleLyrics, lyricsPage = true))
        assertFalse(playerShowsLyricsArtworkBackdrop(MeloXPlayerBackgroundMode.FlowingLight, lyricsPage = true))
        assertFalse(playerShowsLyricsArtworkBackdrop(MeloXPlayerBackgroundMode.AppleLyrics, lyricsPage = false))
        assertEquals(0f, playerIsolationAlpha(false))
        assertEquals(0f, playerIsolationAlpha(false, 1f))
        assertEquals(0f, playerIsolationAlpha(true, 0f))
        assertEquals(0f, playerIsolationAlpha(true, 0.34f))
        val mid = playerIsolationAlpha(true, 0.5f)
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(1f, playerIsolationAlpha(true, 1f))
        assertEquals(1f, playerIsolationAlpha(true, 0.82f))
        assertEquals(1f, playerIsolationAlpha(true, 1f))
        val dragging = playerIsolationAlpha(true, 0.5f)
        assertTrue(dragging > 0f && dragging < 1f)
        assertEquals(0f, playerIsolationAlpha(true, 0.34f))
    }

    @Test
    fun playerExpansionFollowsTheCollapseGestureDirection() {
        // Already expanded, target collapsed: the drag progress grows 0 -> 1, expansion must fall.
        assertTrue(meloXPlayerExpansion(currentExpanded = true, targetExpanded = false, fraction = 0f) > 0.99f)
        val halfway = meloXPlayerExpansion(currentExpanded = true, targetExpanded = false, fraction = 0.5f)
        assertTrue(halfway > 0f && halfway < 1f)
        assertTrue(meloXPlayerExpansion(currentExpanded = true, targetExpanded = false, fraction = 1f) < 0.01f)

        // Settled states ignore the reset fraction instead of flipping to the opposite extreme.
        assertEquals(1f, meloXPlayerExpansion(currentExpanded = true, targetExpanded = true, fraction = 0f))
        assertEquals(0f, meloXPlayerExpansion(currentExpanded = false, targetExpanded = false, fraction = 0f))

        // Expanding: the fraction already grows with expansion.
        assertTrue(meloXPlayerExpansion(currentExpanded = false, targetExpanded = true, fraction = 0f) < 0.01f)
        val expandingHalfway =
            meloXPlayerExpansion(currentExpanded = false, targetExpanded = true, fraction = 0.5f)
        assertTrue(expandingHalfway > 0f && expandingHalfway < 1f)
        assertTrue(meloXPlayerExpansion(currentExpanded = false, targetExpanded = true, fraction = 1f) > 0.99f)
    }

    @Test
    fun glassBlurAndLensChangeTheDraw() {
        val plain = meloXGlassDraw(0f, 0f, 0f, dark = true)
        val blurred = meloXGlassDraw(8f, 0f, 0f, dark = true)
        val lens = meloXGlassDraw(0f, 28f, 16f, dark = true)
        assertEquals(0f, plain.blurPx)
        assertEquals(0f, plain.lensShiftPx)
        assertTrue(blurred.blurPx > plain.blurPx)
        assertTrue(lens.lensShiftPx > plain.lensShiftPx)
        assertTrue(lens.refractionPx > plain.refractionPx)
        assertTrue(lens.highlightAlpha != plain.highlightAlpha || lens.lensShiftPx != plain.lensShiftPx)
    }

    @Test
    fun themeModesResolveDarkLightAndSystem() {
        assertEquals(MeloXResolvedTheme.Dark, resolveMeloXTheme(MeloXThemeMode.Dark, systemDark = false))
        assertEquals(MeloXResolvedTheme.Light, resolveMeloXTheme(MeloXThemeMode.Light, systemDark = true))
        assertEquals(MeloXResolvedTheme.Dark, resolveMeloXTheme(MeloXThemeMode.System, systemDark = true))
        assertEquals(MeloXResolvedTheme.Light, resolveMeloXTheme(MeloXThemeMode.System, systemDark = false))
        assertEquals(MeloXResolvedTheme.Dark, resolveMeloXTheme(MeloXThemeMode.System, systemDark = null))
    }

    @Test
    fun writingPlayerShellUpdatesRuntime() {
        val previous = MeloXSettingsRuntime.playerShell
        try {
            MeloXSettingsPreferences.setString("player_shell", MeloXPlayerShell.Classic.name)
            assertEquals(MeloXPlayerShell.Classic, MeloXSettingsRuntime.playerShell)
            MeloXSettingsPreferences.setString("player_shell", MeloXPlayerShell.AppleMusic.name)
            assertEquals(MeloXPlayerShell.AppleMusic, MeloXSettingsRuntime.playerShell)
        } finally {
            MeloXSettingsRuntime.playerShell = previous
            MeloXSettingsPreferences.setString("player_shell", previous.name)
        }
    }
}
