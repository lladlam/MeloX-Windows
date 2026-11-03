package melox.ui.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.IntOffset
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import melox.model.SearchSong
import melox.network.MeloXWikiSection
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSongWikiMenuInputTest {
    @Test fun nativePointerOpensSongWiki() {
        assumeTrue(System.getenv("MELOX_NATIVE_WIKI_TEST") == "1")
        check(!GraphicsEnvironment.isHeadless())
        val calls = AtomicInteger()
        val dismissed = AtomicInteger()
        var frame: JFrame? = null
        try {
            SwingUtilities.invokeAndWait {
                val panel = ComposePanel()
                panel.setContent {
                    MaterialTheme {
                        var open by remember { mutableStateOf(true) }
                        if (open) MeloXDesktopSongMenu(
                            SearchSong(123, "Wiki fixture", "", "", null),
                            { open = false; dismissed.incrementAndGet() },
                            anchor = IntOffset(200, 150),
                            wikiLoader = { id ->
                                assertEquals(123L, id)
                                calls.incrementAndGet()
                                listOf(MeloXWikiSection("Fixture section", listOf("Fixture detail")))
                            },
                        )
                    }
                }
                frame = JFrame("Song wiki menu fixture").apply {
                    contentPane.add(panel); setSize(1000, 800); setLocation(0, 0); isVisible = true
                }
            }
            val robot = Robot().apply { autoDelay = 100 }
            Thread.sleep(3000)
            SwingUtilities.invokeAndWait { requireNotNull(frame).setSize(1001, 800) }
            Thread.sleep(1500)
            val evidence = System.getenv("MELOX_WIKI_EVIDENCE")?.let(::File)?.apply { mkdirs() }
            evidence?.let { ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(it, "menu.png")) }
            robot.mouseMove(250, 336)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
            while (calls.get() == 0 && System.nanoTime() < deadline) Thread.sleep(100)
            assertEquals(1, calls.get(), "menu click did not start the wiki request")
            SwingUtilities.invokeAndWait { requireNotNull(frame).setSize(1001, 800) }
            Thread.sleep(1500)
            evidence?.let { ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(it, "menu-wiki.png")) }
        } finally {
            SwingUtilities.invokeLater { frame?.dispose() }
        }
    }
}
