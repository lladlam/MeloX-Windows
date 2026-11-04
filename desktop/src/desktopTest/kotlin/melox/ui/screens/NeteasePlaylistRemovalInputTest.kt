package melox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.awt.ComposePanel
import androidx.compose.ui.unit.dp
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import melox.library.NeteasePlaylistSummary
import melox.settings.MeloXSettingsPreferences
import melox.ui.desktop.DesktopLibraryPlaylistCard
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/** Native-window input against production playlist controls; no account or write is used. */
class NeteasePlaylistRemovalInputTest {
    @OptIn(ExperimentalSharedTransitionApi::class)
    @Test fun bothLayoutsOpenAndCancelConfirmation() {
        assumeTrue(System.getenv("MELOX_NATIVE_PLAYLIST_REMOVAL_TEST") == "1")
        check(!GraphicsEnvironment.isHeadless())
        for (desktop in listOf(false, true)) {
        val dismissed = AtomicInteger(); val removed = AtomicInteger()
        val requested = AtomicInteger(); val opened = AtomicInteger()
        val open = mutableStateOf(false)
        val previousDesktopLayout = MeloXSettingsPreferences.boolean("general_desktop_layout", false)
        val playlist = NeteasePlaylistSummary(2, "Fixture playlist", null, 0, "fixture", 10)
        var window: JFrame? = null
        try {
            SwingUtilities.invokeAndWait {
                MeloXSettingsPreferences.setBoolean("general_desktop_layout", desktop)
                val panel = ComposePanel().apply {
                    setContent {
                        MaterialTheme {
                            if (desktop) {
                                DesktopLibraryPlaylistCard(playlist, 205.dp,
                                    onOpen = { opened.incrementAndGet() },
                                    onRemove = { requested.incrementAndGet(); open.value = true })
                            } else SharedTransitionLayout {
                                val shared = this
                                AnimatedVisibility(true) {
                                    MeloXLibraryPlaylistsPage(listOf(playlist),
                                        onPlaylistClick = { opened.incrementAndGet() },
                                        onRemovePlaylist = { requested.incrementAndGet(); open.value = true },
                                        listState = rememberLazyListState(),
                                        sharedTransitionScope = shared, animatedVisibilityScope = this)
                                }
                            }
                            if (open.value) NeteasePlaylistRemovalDialog(
                                playlist, null, "",
                                onDismiss = { open.value = false; dismissed.incrementAndGet() },
                                onRemoved = { removed.incrementAndGet() },
                            )
                        }
                    }
                }
                window = JFrame("Playlist removal fixture").apply {
                    contentPane.add(panel); setSize(1000, 800); setLocation(0, 0); isVisible = true
                }
            }
            Thread.sleep(5000)
            val robot = Robot().apply { autoDelay = 100 }
            val evidence = File(requireNotNull(System.getenv("MELOX_PLAYLIST_REMOVAL_EVIDENCE"))).apply { mkdirs() }
            val layout = if (desktop) "desktop" else "mobile"
            // The first X11 click may only activate an unfocused window.
            robot.mouseMove(500, 700)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(1000)
            ImageIO.write(robot.createScreenCapture(requireNotNull(window).bounds), "png", File(evidence, "$layout-list.png"))
            robot.mouseMove(if (desktop) 45 else 952, if (desktop) 275 else 70)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(2500)
            ImageIO.write(robot.createScreenCapture(requireNotNull(window).bounds), "png", File(evidence, "$layout-after-click.png"))
            assertEquals(1, requested.get(), "$layout remove control did not open; row opens=${opened.get()}, focused=${window?.isFocused}, pointer=${java.awt.MouseInfo.getPointerInfo().location}, bounds=${window?.bounds}")
            assertEquals(0, opened.get(), "$layout remove control also opened the playlist")
            ImageIO.write(robot.createScreenCapture(requireNotNull(window).bounds), "png", File(evidence, "$layout-dialog.png"))
            robot.mouseMove(596, 449)
            robot.mouseWheel(2)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(1000)
            assertEquals(1, dismissed.get(), "cancel button did not dismiss the confirmation")
            assertEquals(0, removed.get())
            robot.mouseMove(if (desktop) 45 else 952, if (desktop) 275 else 70)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            Thread.sleep(1500)
            assertEquals(2, requested.get(), "$layout could not reopen the confirmation")
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
            Thread.sleep(1000)
            assertEquals(2, dismissed.get(), "Escape did not dismiss the confirmation")
            assertEquals(0, removed.get())
        } finally {
            SwingUtilities.invokeAndWait {
                window?.dispose()
                MeloXSettingsPreferences.setBoolean("general_desktop_layout", previousDesktopLayout)
            }
        }
        }
    }
}
