package melox.lyrics

import kotlin.test.*

class LyricAnnotationAlignmentTest {
    @Test fun unmatchedHeaderDoesNotShiftEveryTranslation() {
        val primary = listOf(
            LyricLine(0, text = "credit header"),
            LyricLine(10_000, text = "first verse"),
            LyricLine(20_000, text = "untranslated verse"),
            LyricLine(30_000, text = "last verse"),
        )
        val annotations = listOf(LyricLine(10_000, text = "first translation"), LyricLine(30_000, text = "last translation"))
        val result = NeteaseLyricParser.alignSecondary(primary, annotations)
        assertNull(result[0])
        assertEquals("first translation", result[1]?.text)
        assertNull(result[2])
        assertEquals("last translation", result[3]?.text)
    }

    @Test fun lrclibInstrumentalLinesRemainUntimedByGraphemeProcessor() {
        val result = LrcLibLyricsClient().parseLrc("[00:01.50]voice\n[00:03.000]\n[00:07.25]next voice")
        assertEquals(listOf(1_500L, 3_000L, 7_250L), result.lines.map { it.timeMs })
        assertEquals("", result.lines[1].text)
        assertFalse(result.pseudoTimingAllowed)
    }
}
