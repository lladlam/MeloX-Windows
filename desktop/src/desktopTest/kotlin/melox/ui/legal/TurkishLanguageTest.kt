package melox.ui.legal

import java.util.Locale
import melox.settings.MeloXAppLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class TurkishLanguageTest {
    @Test fun turkishDraftFallsBackToEnglishAndSurvivesReload() {
        val previous = MeloXAppLanguage.selectedTag
        val system = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            MeloXAppLanguage.select("en-US")
            val englishTheme = pinnedString("settings_general_theme")
            MeloXAppLanguage.select("tr")
            assertEquals("Bölünmüş ekran modu", pinnedString("settings_split_screen"))
            assertEquals(englishTheme, pinnedString("settings_general_theme"))
            assertEquals("1 şarkı", pinnedString("player_track_count", 1))
            MeloXAppLanguage.initialize()
            assertEquals("tr", MeloXAppLanguage.selectedTag)
            assertEquals("Bölünmüş ekran modu", pinnedString("settings_split_screen"))
            assertEquals(Locale.US, Locale.getDefault())
        } finally {
            MeloXAppLanguage.select(previous)
            Locale.setDefault(system)
        }
    }
}
