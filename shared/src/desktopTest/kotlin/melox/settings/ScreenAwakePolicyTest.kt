package melox.settings

import kotlin.test.*

class ScreenAwakePolicyTest {
    @Test fun hiddenLyricsScopeReleasesOnQueueCollapseAndForegroundLoss() {
        fun request(lyrics: Boolean = true, hidden: Boolean = true, foreground: Boolean = true, visible: Boolean = true) =
            MeloXScreenAwakePolicy.required(MeloXScreenAwakeMode.HiddenLyricsInterface, lyrics, hidden,
                false, true, foreground, visible)
        assertFalse(request(hidden = false), "Showing lyrics controls does not hold the hidden-only scope")
        assertTrue(request(), "Real auto-hide enters hidden lyrics scope")
        assertFalse(request(lyrics = false), "Switching to queue clears hold")
        assertFalse(request(foreground = false), "Minimize/focus loss clears hold")
        assertFalse(request(visible = false), "Player collapse clears hold")
        assertTrue(request(), "Returning to foreground hidden lyrics reacquires hold")
        assertTrue(MeloXScreenAwakePolicy.required(MeloXScreenAwakeMode.Player, false, false, false, false, true))
        assertFalse(MeloXScreenAwakePolicy.required(MeloXScreenAwakeMode.Lyrics, false, true, false, false, true))
    }

    @Test fun skylineOverrideIsIndependentOfPlayerScopeAndClearsWhenLeavingSkyline() {
        fun request(skyline: Boolean, enabled: Boolean, foreground: Boolean = true) =
            MeloXScreenAwakePolicy.required(MeloXScreenAwakeMode.Disabled, true, false, skyline, enabled, foreground)
        assertTrue(request(true, true))
        assertFalse(request(true, false), "Turning off Skyline keep-awake is effective immediately")
        assertFalse(request(false, true), "Returning to ordinary landscape lyrics clears Skyline override")
        assertFalse(request(true, true, foreground = false))
        assertFalse(MeloXScreenAwakePolicy.required(MeloXScreenAwakeMode.Player, true, true, true, true, true, playerVisible = false))
    }
}
