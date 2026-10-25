package melox.lyrics

import kotlin.test.*

class JapaneseRubyAlignmentTest {
    @Test fun kanaAnchorsKeepKanjiReadingsGrouped() {
        val units = LyricRomanizationAligner.units(LyricLine(0, text = "世界へ君のこと", romanization = "sekai e kimi no koto"))
        assertEquals(listOf("世界", "へ", "君", "の", "こ", "と"), units.map { it.originalText })
        assertEquals(listOf("sekai", "e", "kimi", "no", "ko", "to"), units.map { it.romanizationText })
    }

    @Test fun digraphsAndGeminatedConsonantsKeepSourceCharacters() {
        val parts = assertNotNull(JapaneseRubyAlignment.align("キャット", "kyatto"))
        assertEquals(listOf("キャ", "ッ", "ト"), parts.map { it.original })
        assertEquals(listOf("kya", "t", "to"), parts.map { it.reading })
    }

    @Test fun unalignedSuppliedReadingsAreNeverInvented() {
        assertNull(JapaneseRubyAlignment.align("こんにちは", "good morning"))
        assertNull(JapaneseRubyAlignment.align("hello", "hello"))
        val units = LyricRomanizationAligner.units(LyricLine(0, text = "こんにちは", romanization = "good morning"))
        assertEquals(listOf("good morning"), units.map { it.romanizationText })
    }

    @Test fun punctuationAndSurrogatePairsKeepOriginalOffsets() {
        val parts = assertNotNull(JapaneseRubyAlignment.align("ね、𠮷の！", "ne, yoshi no!"))
        assertEquals("ね、𠮷の！", parts.joinToString("") { it.original })
        assertEquals(listOf("ne", "yoshi", "no"), parts.mapNotNull { it.reading })
    }

    @Test fun visualWrapSplitsAuthoredTimingWithoutLosingFollowingWords() {
        val line = LyricLine(1000, text = "hello world", romanization = "HELLO WORLD",
            syllables = listOf(LyricSyllable("hello", 1000, 2000), LyricSyllable("world", 2200, 3200)),
            romanizationSyllables = listOf(LyricSyllable("HELLO", 1000, 2000), LyricSyllable("WORLD", 2200, 3200)))
        val rows = splitRomanizedSegments(line, listOf(0 to 3, 3 to 6, 6 to 11))
        assertEquals(listOf("hel", "lo", "world"), rows.map { it.line.text })
        assertEquals(listOf("HEL", "LO", "WORLD"), rows.map { it.units.single().reading })
        assertEquals(listOf(1000L to 1600L, 1600L to 2000L, 2200L to 3200L),
            rows.map { it.line.syllables.single().let { s -> s.startTimeMs to s.endTimeMs } })
        assertTrue(rows.all { it.line.romanization == null && it.line.romanizationSyllables.isEmpty() })
    }

    @Test fun repeatedJapaneseUnitsStayOnTheirOwnVisualRows() {
        val line = LyricLine(0, text = "君の君の", romanization = "kimi no kimi no")
        val rows = splitRomanizedSegments(line, listOf(0 to 2, 2 to 4))
        assertEquals(listOf("君の", "君の"), rows.map { it.line.text })
        assertEquals(listOf("kimi", "no"), rows[1].units.map { it.reading })
        assertEquals(listOf(0 to 1, 1 to 2), rows[1].units.map { it.start to it.end })
    }

    @Test fun pathologicalInputFallsBackBeforeRecursiveAlignment() {
        assertNull(JapaneseRubyAlignment.align("あ".repeat(257), "a".repeat(257)))
        assertNull(JapaneseRubyAlignment.align("あ", "a".repeat(1025)))
    }
}
