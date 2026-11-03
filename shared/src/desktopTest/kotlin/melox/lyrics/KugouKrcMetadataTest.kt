package melox.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KugouKrcMetadataTest {
    @Test fun authoredWordsKeepKugouSourceAndRealTiming() {
        val document = KugouKrcLyricsParser.parse("[1000,2000]<0,500,0>Hello <500,800,0>world")
        assertEquals(LyricSource.Kugou, document.source)
        assertEquals(LyricQuality.WordSynchronized, document.quality)
        assertFalse(document.pseudoTimingAllowed)
        assertEquals(listOf(1000L, 1500L), document.lines.single().syllables.map { it.startTimeMs })
        assertEquals(document, document.withPseudoTiming())
        assertEquals(LyricSource.Local, LyricFileParser.parse("[1000,2000]<0,500,0>Hello").source)
    }

    @Test fun lineAndPlainFallbackDoNotInventWordOrPlaybackTiming() {
        val line = KugouKrcLyricsParser.parse("[1000,2000]Hello world")
        assertEquals(LyricQuality.LineSynchronized, line.quality)
        assertEquals(LyricSource.Kugou, line.source)
        assertFalse(line.pseudoTimingAllowed)
        assertTrue(line.withPseudoTiming().lines.single().syllables.isEmpty())
        val plain = KugouKrcLyricsParser.parse("Plain lyric\nAnother line")
        assertEquals(LyricSource.Kugou, plain.source)
        assertTrue(plain.isUnsynchronized)
        assertNull(plain.highlightedIndex(5000))
    }
}
