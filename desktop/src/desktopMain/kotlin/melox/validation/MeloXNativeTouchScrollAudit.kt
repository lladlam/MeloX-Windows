package melox.validation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.win32.StdCallLibrary
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.event.InputEvent
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import melox.ui.desktop.desktopPromotedDragScroll
import org.json.JSONArray
import org.json.JSONObject

internal interface TouchInjectionApi : StdCallLibrary {
    fun InitializeTouchInjection(maxCount: Int, mode: Int): Boolean
    fun InjectTouchInput(count: Int, contacts: Pointer): Boolean
}

@Structure.FieldOrder("type", "pointerId", "frameId", "flags", "source", "target", "pixel", "himetric", "rawPixel", "rawHimetric", "time", "historyCount", "inputData", "keyStates", "performanceCount", "buttonChange")
internal open class AuditPointerInfo : Structure() {
    @JvmField var type = 2 // PT_TOUCH
    @JvmField var pointerId = 0
    @JvmField var frameId = 0
    @JvmField var flags = 0
    @JvmField var source: Pointer? = null
    @JvmField var target: Pointer? = null
    @JvmField var pixel = POINT()
    @JvmField var himetric = POINT()
    @JvmField var rawPixel = POINT()
    @JvmField var rawHimetric = POINT()
    @JvmField var time = 0
    @JvmField var historyCount = 0
    @JvmField var inputData = 0
    @JvmField var keyStates = 0
    @JvmField var performanceCount = 0L
    @JvmField var buttonChange = 0
}

@Structure.FieldOrder("info", "touchFlags", "mask", "contact", "rawContact", "orientation", "pressure")
internal open class AuditTouchInfo : Structure() {
    @JvmField var info = AuditPointerInfo()
    @JvmField var touchFlags = 0
    @JvmField var mask = 7
    @JvmField var contact = RECT()
    @JvmField var rawContact = RECT()
    @JvmField var orientation = 90
    @JvmField var pressure = 32000
}

/** Windows system-injected contact -> AWT -> Compose -> the production drag adapter.
 * The rows are input-test labels, not invented songs or a mock production service.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    val output = File(args.single()).apply { parentFile.mkdirs() }
    val contact = AuditTouchInfo()
    check(contact.info.size() == if (Native.POINTER_SIZE == 8) 96 else 88) { "Unexpected POINTER_INFO ABI: ${contact.info.size()}" }
    check(contact.size() == if (Native.POINTER_SIZE == 8) 144 else 136) { "Unexpected POINTER_TOUCH_INFO ABI: ${contact.size()}" }
    if (!System.getProperty("os.name").startsWith("Windows", true)) {
        output.writeText(JSONObject().put("status", "UNSUPPORTED").put("reason", "Windows InjectTouchInput only")
            .put("pointer_info_bytes", contact.info.size()).put("touch_info_bytes", contact.size()).toString(2) + "\n")
        return
    }
    check(!GraphicsEnvironment.isHeadless()) { "Windows native input audit needs a usable desktop" }
    val api = Native.load("user32", TouchInjectionApi::class.java)
    val samples = JSONArray()
    val events = JSONArray()
    var failure: Throwable? = null
    application(exitProcessOnExit = false) {
        val state = rememberWindowState(width = 640.dp, height = 620.dp)
        Window(onCloseRequest = ::exitApplication, state = state, title = "MeloX native touch scrolling audit") {
            val list = rememberLazyListState()
            MaterialTheme {
                LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(list)
                    .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                        events.put(JSONObject().put("event", "Press").put("types", event.changes.map { it.type.toString() }.joinToString()))
                    }, state = list) {
                    items((0..199).toList()) { number -> Text("Native scroll input audit row $number", Modifier.fillMaxWidth().height(46.dp).padding(12.dp)) }
                }
            }
            LaunchedEffect(window) {
                suspend fun await(condition: () -> Boolean) = withTimeout(8_000) { while (!condition()) delay(25) }
                fun position() = list.firstVisibleItemIndex * 46 + list.firstVisibleItemScrollOffset
                fun record(stage: String) { samples.put(JSONObject().put("stage", stage).put("index", list.firstVisibleItemIndex).put("offset", list.firstVisibleItemScrollOffset)) }
                suspend fun inject(x: Int, y: Int, flags: Int) {
                    contact.info.pixel.x = x; contact.info.pixel.y = y
                    contact.info.rawPixel.x = x; contact.info.rawPixel.y = y
                    contact.info.flags = flags
                    // The official InjectTouchPointerInput sample sets only
                    // rcContact and leaves rcContactRaw zeroed.
                    contact.contact.left = (x - 2).coerceAtLeast(0); contact.contact.right = x + 2
                    contact.contact.top = (y - 2).coerceAtLeast(0); contact.contact.bottom = y + 2
                    contact.write()
                    var error = 0
                    for (attempt in 0..1) {
                        if (api.InjectTouchInput(1, contact.pointer)) { delay(20); return }
                        error = Native.getLastError()
                        // ERROR_NOT_READY: frames closer than 0.1ms; retry once.
                        if (error != 21 || attempt == 1) break
                        delay(2)
                    }
                    error("InjectTouchInput failed: $error flags=$flags contact=$x,$y")
                }
                var touchFailure: Throwable? = null
                var mouseFailure: Throwable? = null
                var maxTouches = 0
                try {
                    // Initialize and inject on the same native UI thread.
                    maxTouches = User32.INSTANCE.GetSystemMetrics(95) // SM_MAXIMUMTOUCHES
                    check(api.InitializeTouchInjection(10, 3)) { "InitializeTouchInjection failed: ${Native.getLastError()}" }
                    await { window.isShowing && window.width > 0 }
                    window.toFront(); window.requestFocus()
                    await { window.isFocused }
                    delay(500)
                    val transform = window.graphicsConfiguration.defaultTransform
                    val x = ((window.x + window.width / 2) * transform.scaleX).toInt()
                    val y = ((window.y + window.height * .72) * transform.scaleY).toInt()
                    val distance = (220 * transform.scaleY).toInt()
                    samples.put(JSONObject().put("stage", "injection-context")
                        .put("thread", Thread.currentThread().name)
                        .put("window", listOf(window.x, window.y, window.width, window.height))
                        .put("device_bounds", window.graphicsConfiguration.bounds.toString())
                        .put("scale", listOf(transform.scaleX, transform.scaleY)).put("contact", listOf(x, y)))
                    record("initial")
                    // Enter hover first. The documented state machine starts in
                    // hover (INRANGE|UPDATE) and transitions to the first contact
                    // through INRANGE|INCONTACT|DOWN.
                    inject(x, y, 0x20002)
                    inject(x, y, 0x10006)
                    for (frame in 1..24) inject(x, y - distance * frame / 24, 0x20006)
                    for (frame in 1..8) inject(x, y - distance, 0x20006)
                    inject(x, y - distance, 0x40000)
                    await { position() >= 100 }
                    record("windows-injected-touch-scroll")
                } catch (error: Throwable) {
                    touchFailure = error
                    record("touch-failed")
                }
                try {
                    // The mouse path is independent evidence and still runs when
                    // the hosted session has no touch digitizer.
                    list.scrollToItem(0)
                    val robot = Robot()
                    val rx = window.x + window.width / 2
                    val ry = window.y + (window.height * .72).toInt()
                    robot.mouseMove(rx, ry); robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                    for (frame in 1..24) { robot.mouseMove(rx, ry - 220 * frame / 24); delay(20) }
                    delay(200)
                    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    await { position() >= 100 }
                    record("native-mouse-promoted-drag-scroll")
                } catch (error: Throwable) {
                    mouseFailure = error
                    record("mouse-failed")
                } finally {
                    // A hosted runner without any touch digitizer rejects system
                    // injection with ERROR_INVALID_PARAMETER. Report UNSUPPORTED,
                    // never PASSED, and keep the real error plus mouse evidence.
                    val unsupported = touchFailure != null && maxTouches <= 0
                    val status = when {
                        mouseFailure != null -> "FAILED"
                        unsupported -> "UNSUPPORTED"
                        touchFailure != null -> "FAILED"
                        else -> "PASSED"
                    }
                    output.writeText(JSONObject().put("status", status)
                        .put("input", "Windows InjectTouchInput and AWT Robot; not physical hardware acceptance")
                        .put("error", (mouseFailure ?: touchFailure)?.toString())
                        .put("touch_error", touchFailure?.toString())
                        .put("mouse_error", mouseFailure?.toString())
                        .put("max_touches", maxTouches)
                        .put("touch_contact_observed", events.length() > 0)
                        .put("samples", samples).put("received_contacts", events)
                        .put("pointer_info_bytes", contact.info.size()).put("touch_info_bytes", contact.size()).toString(2) + "\n")
                    exitApplication()
                }
            }
        }
    }
    // The report carries the detailed failure; fail CI only when the AWT mouse
    // path (which needs no digitizer) also fails.
    val report = runCatching { output.readText() }.getOrNull().orEmpty()
    if (!report.contains("\"status\": \"UNSUPPORTED\"") && report.contains("\"status\": \"FAILED\"")) {
        error("Native touch scroll audit failed; see $output")
    }
}
