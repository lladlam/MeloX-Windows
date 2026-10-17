package melox.validation

import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.GraphicsEnvironment
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import melox.ui.player.MeloXImmersiveWindowOwner
import org.json.JSONArray
import org.json.JSONObject

/** Native OS window readback, not an offscreen layout claiming to enter fullscreen. */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { parentFile.mkdirs() }
    val screen = if (GraphicsEnvironment.isHeadless()) null else
        GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
    if (screen == null || screen.width <= 0 || screen.height <= 0) {
        output.writeText(JSONObject().put("status", "UNSUPPORTED").put("reason", "No positive-size native display")
            .put("os", System.getProperty("os.name")).toString(2) + "\n")
        check(!System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) { "Windows native window audit requires a usable display" }
        println("UNSUPPORTED: no positive-size native display")
        return
    }
    var failure: Throwable? = null
    val samples = JSONArray()
    application(exitProcessOnExit = false) {
        val state = rememberWindowState(width = 600.dp, height = 400.dp)
        val owner = remember(state) { MeloXImmersiveWindowOwner(state) }
        Window(onCloseRequest = ::exitApplication, state = state, title = "MeloX native immersive audit") {
            Text("Native fullscreen / placement restore audit")
            LaunchedEffect(window) {
                suspend fun await(placement: WindowPlacement) {
                    withTimeout(10_000) { while (!window.isShowing || window.placement != placement || window.width <= 0 || window.height <= 0) delay(25) }
                    delay(400)
                }
                fun record(stage: String) {
                    samples.put(JSONObject().put("stage", stage).put("requested", state.placement.toString())
                        .put("actual", window.placement.toString()).put("width", window.width).put("height", window.height))
                }
                try {
                    await(WindowPlacement.Floating)
                    for (original in listOf(WindowPlacement.Floating, WindowPlacement.Maximized, WindowPlacement.Fullscreen)) {
                        state.placement = original
                        await(original)
                        val bounds = window.bounds
                        owner.setEnabled(true)
                        await(WindowPlacement.Fullscreen)
                        check(window.width >= screen.width && window.height >= screen.height) { "Fullscreen did not cover the native display" }
                        record("${original}-entered")
                        owner.setEnabled(true)
                        owner.setEnabled(false)
                        await(original)
                        record("${original}-restored")
                        if (original == WindowPlacement.Floating) check(window.bounds == bounds) { "Floating window bounds were lost" }
                    }
                    state.placement = WindowPlacement.Floating
                    await(WindowPlacement.Floating)
                    owner.setEnabled(true)
                    await(WindowPlacement.Fullscreen)
                    state.placement = WindowPlacement.Maximized
                    await(WindowPlacement.Maximized)
                    owner.setEnabled(false)
                    await(WindowPlacement.Maximized)
                    record("manual-placement-preserved")
                    owner.setEnabled(true)
                    await(WindowPlacement.Fullscreen)
                    owner.close()
                    await(WindowPlacement.Maximized)
                    owner.setEnabled(true)
                    check(state.placement == WindowPlacement.Maximized) { "Closed owner reacquired fullscreen" }
                    record("disposed-and-late-request-ignored")
                    output.writeText(JSONObject().put("status", "PASSED").put("os", System.getProperty("os.name"))
                        .put("samples", samples).put("physical_player_input", "PENDING").toString(2) + "\n")
                    println("PASS: native fullscreen, floating bounds/maximized/fullscreen restore, manual placement and disposal")
                } catch (error: Throwable) {
                    failure = error
                    record("failed-current-state")
                    output.writeText(JSONObject().put("status", "FAILED").put("error", error.toString()).put("samples", samples).toString(2) + "\n")
                } finally {
                    owner.close()
                    exitApplication()
                }
            }
        }
    }
    failure?.let { throw it }
}
