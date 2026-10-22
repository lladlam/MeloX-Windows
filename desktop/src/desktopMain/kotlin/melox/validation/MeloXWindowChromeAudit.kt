package melox.validation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import javax.imageio.ImageIO
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import melox.ui.desktop.LocalMeloXWindowChrome
import melox.ui.desktop.MeloXWindowChrome
import melox.ui.desktop.MeloXWindowFrame
import org.json.JSONArray
import org.json.JSONObject

/** Real OS readback after Robot pointer input on the production custom caption. */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { parentFile.mkdirs() }
    val screen = if (GraphicsEnvironment.isHeadless()) null else
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    if (screen == null || screen.width <= 0 || screen.height <= 0) {
        output.writeText(JSONObject().put("status", "UNSUPPORTED").put("reason", "No positive-size native display").toString(2) + "\n")
        check(!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) { "Windows caption audit requires a native display" }
        println("UNSUPPORTED: native caption input requires a positive-size display")
        return
    }
    val samples = JSONArray()
    var failure: Throwable? = null
    var closedByButton = false
    application(exitProcessOnExit = false) {
        val state = rememberWindowState(width = 640.dp, height = 480.dp)
        var dark by remember { mutableStateOf(true) }
        var immersive by remember { mutableStateOf(false) }
        var gradientPlayer by remember { mutableStateOf(false) }
        Window(onCloseRequest = ::exitApplication, state = state, undecorated = true, title = "MeloX window chrome audit") {
            val owner = remember(window, state) { MeloXWindowChrome(this, state) { closedByButton = true } }
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalMeloXWindowChrome provides owner) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    MeloXWindowFrame(playerVisible = gradientPlayer) {
                        Box(Modifier.fillMaxSize().background(if (gradientPlayer) Brush.horizontalGradient(listOf(Color(0xFF24558A), Color(0xFF7C346D)))
                            else Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)))) {
                            Text("Production app-owned caption / physical native pointer audit")
                        }
                    }
                }
            }
            LaunchedEffect(window) {
                val robot = Robot()
                suspend fun await(condition: () -> Boolean) {
                    withTimeout(10_000) { while (!condition()) delay(25) }
                    delay(350)
                }
                suspend fun click(x: Int, y: Int) {
                    robot.mouseMove(x, y)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    delay(35)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    delay(80)
                }
                fun record(stage: String) {
                    samples.put(JSONObject().put("stage", stage).put("placement", window.placement.toString())
                        .put("minimized", state.isMinimized).put("bounds", window.bounds.toString())
                        .put("undecorated", window.isUndecorated))
                }
                fun captionPoint(buttonFromRight: Int): Pair<Int, Int> {
                    val transform = window.graphicsConfiguration.defaultTransform
                    return (window.x + window.width - ((23 + buttonFromRight * 46) * density / transform.scaleX).toInt()) to
                        (window.y + (17 * density / transform.scaleY).toInt())
                }
                suspend fun captionClick(buttonFromRight: Int) {
                    val (x, y) = captionPoint(buttonFromRight)
                    click(x, y)
                }
                try {
                    await { window.isShowing && window.width > 0 }
                    check(window.isUndecorated && window.insets.top == 0) { "OS title bar still present" }
                    window.minimumSize = java.awt.Dimension(320, 240)
                    record("floating-no-native-titlebar")
                    captionClick(1)
                    await { window.placement == WindowPlacement.Maximized }
                    record("physical-maximize")
                    captionClick(1)
                    await { window.placement == WindowPlacement.Floating }
                    record("physical-restore")
                    state.placement = WindowPlacement.Fullscreen
                    await { window.placement == WindowPlacement.Fullscreen }
                    captionClick(1)
                    await { window.placement == WindowPlacement.Floating }
                    record("physical-fullscreen-restore")
                    captionClick(2)
                    await { state.isMinimized && window.extendedState and java.awt.Frame.ICONIFIED != 0 }
                    record("physical-minimize")
                    state.isMinimized = false
                    await { !state.isMinimized && window.isShowing && window.extendedState and java.awt.Frame.ICONIFIED == 0 }
                    window.toFront()
                    window.requestFocus()
                    delay(400)
                    val transform = window.graphicsConfiguration.defaultTransform
                    val dragX = window.x + (120 * density / transform.scaleX).toInt()
                    val dragY = window.y + (17 * density / transform.scaleY).toInt()
                    click(dragX, dragY); click(dragX, dragY)
                    await { window.placement == WindowPlacement.Maximized }
                    record("physical-caption-double-click")
                    captionClick(1)
                    await { window.placement == WindowPlacement.Floating }
                    val original = window.bounds
                    robot.mouseMove(window.x + window.width - 2, window.y + window.height - 2)
                    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    delay(100)
                    for (step in 1..12) {
                        robot.mouseMove(original.x + original.width - 2 + step * 6, original.y + original.height - 2 + step * 4)
                        delay(25)
                    }
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    await { window.width >= original.width + 40 && window.height >= original.height + 24 }
                    record("physical-corner-resize")
                    ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(output.parentFile, "window-caption-dark.png"))
                    dark = false
                    delay(400)
                    ImageIO.write(robot.createScreenCapture(window.bounds), "png", File(output.parentFile, "window-caption-light.png"))
                    melox.settings.MeloXSettingsPreferences.setBoolean("windows_immersive_caption", true)
                    immersive = true
                    gradientPlayer = true
                    delay(500)
                    val blended = robot.createScreenCapture(window.bounds)
                    ImageIO.write(blended, "png", File(output.parentFile, "window-caption-immersive.png"))
                    // A horizontal player gradient must continue unchanged through
                    // the transparent caption, away from text/buttons and borders.
                    val x = blended.width / 2
                    val above = blended.getRGB(x, (12 * density).toInt()) and 0xffffff
                    val below = blended.getRGB(x, (60 * density).toInt()) and 0xffffff
                    check(above == below) { "Caption interrupts the page surface: $above != $below" }
                    record("immersive-continuous-player-surface")
                    captionClick(1)
                    await { window.placement == WindowPlacement.Maximized }
                    record("immersive-physical-maximize")
                    captionClick(1)
                    await { window.placement == WindowPlacement.Floating }
                    record("immersive-physical-restore")
                    captionClick(0)
                    await { closedByButton }
                    record("physical-close")
                } catch (error: Throwable) {
                    failure = error
                    record("failed")
                } finally {
                    if (immersive) melox.settings.MeloXSettingsPreferences.setBoolean("windows_immersive_caption", false)
                    output.writeText(JSONObject().put("status", if (failure == null && closedByButton) "PASSED" else "FAILED")
                        .put("os", System.getProperty("os.name")).put("input", "java.awt.Robot real pointer")
                        .put("error", failure?.toString()).put("samples", samples).toString(2) + "\n")
                    exitApplication()
                }
            }
        }
    }
    failure?.let { throw it }
    check(closedByButton)
    println("PASS: undecorated native window, physical caption maximize/restore/minimize/double-click/resize/close")
}
