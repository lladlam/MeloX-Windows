package melox.ui.screens

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import melox.lyrics.LyricLine
import melox.lyrics.LyricsDocument
import melox.settings.MeloXSecondaryLyricMode

class FloatingLyricsTest {
    private val document = LyricsDocument(listOf(
        LyricLine(1_000, text = "first", translation = "translated", romanization = "romanized"),
        LyricLine(2_000, text = "second"),
    ))

    @Test fun outputFollowsPlaybackClockAndSecondaryMode() {
        fun select(time: Long, mode: MeloXSecondaryLyricMode, translation: Boolean = true, romanization: Boolean = true) =
            floatingLyricLines(document, time, mode, translation, romanization)
        assertNull(select(999, MeloXSecondaryLyricMode.Auto))
        assertEquals(FloatingLyricLines("first", "translated"), select(1_000, MeloXSecondaryLyricMode.Auto))
        assertEquals(FloatingLyricLines("first", "romanized"), select(1_500, MeloXSecondaryLyricMode.Auto, false))
        assertEquals(FloatingLyricLines("first", "second"), select(1_500, MeloXSecondaryLyricMode.Auto, false, false))
        assertEquals(FloatingLyricLines("first", ""), select(1_500, MeloXSecondaryLyricMode.Hidden))
        assertEquals(FloatingLyricLines("first", "second"), select(1_500, MeloXSecondaryLyricMode.NextLine))
        assertEquals(FloatingLyricLines("second", ""), select(2_000, MeloXSecondaryLyricMode.Auto))
    }
}
