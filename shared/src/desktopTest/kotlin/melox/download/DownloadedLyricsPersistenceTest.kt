package melox.download

import melox.lyrics.LrcLyricsParser
import org.json.JSONObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadedLyricsPersistenceTest {
    @Test fun downloadedPlainLyricsRemainUntimedAfterJsonRoundTripAndOldFilesRemainReadable() {
        val store = MeloXDownloadStore.instance()
        val plain = LrcLyricsParser.parse("First line\nSecond line")
        val reopened = store.decodeLyrics(JSONObject(store.encodeLyrics(plain).toString()))
        assertTrue(reopened.isUnsynchronized)
        assertFalse(reopened.pseudoTimingAllowed)
        assertNull(reopened.highlightedIndex(10_000))
        assertEquals(listOf("First line", "Second line"), reopened.lines.map { it.text })

        val old = store.decodeLyrics(JSONObject("""{"lines":[{"timeMs":1000,"text":"timed"}]}"""))
        assertFalse(old.isUnsynchronized)
        assertEquals(0, old.highlightedIndex(1_000))
    }
}
