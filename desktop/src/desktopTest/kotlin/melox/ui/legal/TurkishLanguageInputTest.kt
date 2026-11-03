package melox.ui.legal

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.awt.ComposePanel
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import javax.swing.SwingUtilities
import melox.platform.getPreferences
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXAppLanguage
import melox.ui.screens.SettingsScreen
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

class TurkishLanguageInputTest {
    @Test fun reloadFromEarlierProcess() {
        assumeTrue(System.getenv("MELOX_TURKISH_RELOAD") == "1")
        MeloXAppLanguage.initialize()
        assertEquals("tr", MeloXAppLanguage.selectedTag)
        assertEquals("Bölünmüş ekran modu", pinnedString("settings_split_screen"))
    }

    @Test fun captureBothLayouts() {
        assumeTrue(System.getenv("MELOX_NATIVE_TURKISH_TEST") == "1")
        check(!GraphicsEnvironment.isHeadless())
        val evidence = File(requireNotNull(System.getenv("MELOX_TURKISH_EVIDENCE"))).apply { mkdirs() }
        val prefs = getPreferences("melox_app_settings")
        for (desktop in listOf(false, true)) {
            prefs.putBoolean("general_desktop_layout", desktop)
            prefs.putString("application_language", "en-US")
            MeloXSettingsRuntime.initialize(force = true)
            var frame: JFrame? = null
            try {
                SwingUtilities.invokeAndWait {
                    val panel = ComposePanel().apply { setContent { MaterialTheme { SettingsScreen(routeRequest = "General") } } }
                    frame = JFrame("Language input fixture").apply {
                        contentPane.add(panel); setSize(1000, 800); setLocation(0, 0); isVisible = true
                    }
                }
                Thread.sleep(4000)
                val robot = Robot().apply { autoDelay = 100 }
                ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(evidence, "${if (desktop) "desktop" else "mobile"}-initial.png"))
                fun click(x: Int, y: Int) {
                    robot.mouseMove(x, y)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                }
                click(if (desktop) 690 else 925, if (desktop) 389 else 174)
                Thread.sleep(1500)
                ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(evidence, "${if (desktop) "desktop" else "mobile"}-menu.png"))
                click(if (desktop) 690 else 900, if (desktop) 526 else 426)
                Thread.sleep(1500)
                assertEquals("tr", MeloXAppLanguage.selectedTag)
                assertEquals("tr", prefs.getString("application_language", ""))
                robot.mouseMove(500, if (desktop) 480 else 650)
                robot.mouseWheel(3)
                robot.keyPress(KeyEvent.VK_ESCAPE)
                robot.keyRelease(KeyEvent.VK_ESCAPE)
                ImageIO.write(robot.createScreenCapture(requireNotNull(frame).bounds), "png", File(evidence, "${if (desktop) "desktop" else "mobile"}-selected.png"))
            } finally {
                SwingUtilities.invokeAndWait { frame?.dispose() }
            }
        }
    }
}
