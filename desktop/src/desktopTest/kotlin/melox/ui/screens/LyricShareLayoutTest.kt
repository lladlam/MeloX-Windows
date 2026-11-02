package melox.ui.screens

import java.awt.Font
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import melox.lyrics.LyricLine
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LyricShareLayoutTest {
    @Test fun longAndManyLinesFitWithoutLosingText() {
        val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            val font = Font("SansSerif", Font.BOLD, 40)
            val longLine = "这是一段较长的歌词 accompanied by a long lyric ".repeat(5)
            val layout = LyricShareLayout.measure(listOf(longLine) + List(60) { "歌词 $it" }, font, graphics.fontRenderContext)
            val wrappedCount = layout.rows.size - 60
            assertTrue(wrappedCount > 1)
            assertEquals(longLine, layout.rows.take(wrappedCount).joinToString("") { it.text })
            assertEquals((0 until 60).map { "歌词 $it" }, layout.rows.drop(wrappedCount).map { it.text })
            assertTrue(layout.height > 4096)
            assertTrue(layout.rows.all { it.baseline < layout.height && (it.layout?.visibleAdvance ?: 0f) <= 924f })
        } finally { graphics.dispose() }
    }

    @Test fun oversizedImageFailsInsteadOfDroppingLyrics() {
        val graphics = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics()
        try {
            val error = assertFailsWith<IllegalArgumentException> {
                LyricShareLayout.measure(List(200) { "歌词 $it" }, Font("SansSerif", Font.BOLD, 40), graphics.fontRenderContext)
            }
            assertTrue(error.message.orEmpty().contains("歌词图片过长"))
        } finally { graphics.dispose() }
    }

    @Test fun realPngIncludesLastSelectedLine() = runBlocking {
        val track = MusicTrack(MusicResourceId(MusicSource.Local, "test"), "Test", emptyList())
        val lines = List(60) { LyricLine(timeMs = it * 1000L, text = "Last line $it") }
        val destination = Files.createTempFile("melox-lyrics-share-", ".png").toFile()
        saveLyricShareImage(track, lines, destination)
        val image = ImageIO.read(destination)
        assertTrue(image.height > 4096)
        val lastBaseline = 340 + 59 * 86 + 45
        val whitePixels = (lastBaseline - 45 until lastBaseline).sumOf { y ->
            (78 until 400).count { x ->
                val pixel = image.getRGB(x, y)
                (pixel shr 16 and 255) > 240 && (pixel shr 8 and 255) > 240 && (pixel and 255) > 240
            }
        }
        assertTrue(whitePixels > 20, "last lyric must be painted into the actual PNG")
    }
}
