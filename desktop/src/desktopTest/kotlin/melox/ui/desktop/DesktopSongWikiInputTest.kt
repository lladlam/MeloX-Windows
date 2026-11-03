package melox.ui.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.awt.ComposePanel
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.Rectangle
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import melox.network.MeloXWikiSection
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Native input on the production dialog; responses are intentionally local fixtures. */
class DesktopSongWikiInputTest {
    @Test fun retryScrollAndEscape() {
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
                        if (open) DesktopSongWikiDialog(123, "Fixture song", {
                            open = false; dismissed.incrementAndGet()
                        }) {
                            assertEquals(123L, it)
                            if (calls.incrementAndGet() == 1) error("Fixture network error")
                            (1..50).map { index -> MeloXWikiSection("Section $index", listOf("Detail $index")) }
                        }
                    }
                }
                frame = JFrame("Song wiki input fixture").apply {
                    contentPane.add(panel); setSize(1000, 800); setLocation(0, 0); isVisible = true
                }
            }
            fun await(condition: () -> Boolean) {
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
                while (!condition() && System.nanoTime() < deadline) Thread.sleep(100)
                assertTrue(condition(), "timed out waiting for wiki fixture")
            }
            await { calls.get() == 1 }
            Thread.sleep(1200)
            val robot = Robot().apply { autoDelay = 100 }
            val evidence = System.getenv("MELOX_WIKI_EVIDENCE")?.let(::File)?.apply { mkdirs() }
            evidence?.let { ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(it, "full-error.png")) }
            // ComposePanel renders Dialog in its own layer, rather than always creating an AWT window.
            val dialog = Window.getWindows().firstOrNull { it.isShowing && it !== frame }
            val bounds = dialog?.bounds ?: Rectangle(248, 297, 504, 206)
            evidence?.let { ImageIO.write(robot.createScreenCapture(bounds), "png", File(it, "error.png")) }
            // Retry is on the left, immediately above the bottom action row.
            robot.mouseMove(bounds.x + 50, bounds.y + bounds.height - 66)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            await { calls.get() == 2 }
            // Xvfb may fall back to software rendering and miss a layer invalidation.
            SwingUtilities.invokeAndWait { requireNotNull(frame).setSize(1001, 800) }
            Thread.sleep(1500)
            val contentBounds = dialog?.bounds ?: requireNotNull(frame).bounds
            robot.mouseMove(contentBounds.x + contentBounds.width / 2, contentBounds.y + contentBounds.height / 2)
            val before = robot.createScreenCapture(contentBounds)
            robot.mouseWheel(8)
            SwingUtilities.invokeAndWait { requireNotNull(frame).setSize(1000, 800) }
            Thread.sleep(1000)
            val after = robot.createScreenCapture(contentBounds)
            evidence?.let {
                ImageIO.write(before, "png", File(it, "before-scroll.png"))
                ImageIO.write(after, "png", File(it, "after-scroll.png"))
            }
            assertTrue((0 until before.height).any { y ->
                (0 until before.width).any { x -> before.getRGB(x, y) != after.getRGB(x, y) }
            }, "mouse wheel did not change the displayed wiki")
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            await { dismissed.get() == 1 }
            assertEquals(2, calls.get())
        } finally {
            SwingUtilities.invokeAndWait { frame?.dispose() }
        }
    }
}
