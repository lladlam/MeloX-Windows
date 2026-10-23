package melox.validation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import melox.ui.glass.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject

/** Explicit generated-pixel fixture around production components, never an app data source. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
internal fun auditMeloXGlassScene(output: File) = runBlocking {
    output.mkdirs()
    File(output, "audit.json").delete()
    val samples = JSONArray()
    val ownership = JSONArray()
    withContext(Dispatchers.Main) {
        melox.settings.MeloXSettingsPreferences.initialize()
        for (density in listOf(1f, 1.25f, 2f)) for (dark in listOf(false, true)) for (frosted in listOf(false, true)) {
            melox.settings.MeloXSettingsPreferences.setBoolean("player_frosted_glass", frosted)
            check(melox.settings.MeloXSettingsRuntime.frostedGlassEnabled == frosted)
            var phase by mutableIntStateOf(0)
            var mounted by mutableStateOf(true)
            var checked by mutableStateOf(false)
            var acceptChange by mutableStateOf(true)
            var toggleEnabled by mutableStateOf(true)
            var toggleCommits = 0
            var clicks = 0
            var sliderValue by mutableFloatStateOf(.4f)
            var sliderEnabled by mutableStateOf(true)
            var sliderWidth by mutableIntStateOf(240)
            var sliderCommits = 0
            var cancelChangedPixels = 0
            val sliderEvents = mutableListOf<String>()
            val beforeOwnership = MeloXGlassFilterOwner.ownershipCounts()
            val prefix = "${density}-${if (dark) "dark" else "light"}-${if (frosted) "frosted" else "liquid"}"
            val scene = ImageComposeScene((360 * density).toInt(), (240 * density).toInt(), Density(density), coroutineContext = coroutineContext) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val backdrop = rememberMeloXLayerBackdrop()
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().meloXRecordBackdrop(backdrop).drawBehind {
                            val offset = phase * 4
                            val cell = 12.dp.toPx()
                            for (y in 0..(size.height / cell).toInt()) for (x in 0..(size.width / cell).toInt()) {
                                drawRect(if ((x + y + phase) % 2 == 0) Color(0xFF2563BD) else Color(0xFFE9C742),
                                    Offset(x * cell + offset, y * cell), Size(cell, cell))
                            }
                        })
                        if (mounted) CompositionLocalProvider(LocalMeloXLayerBackdrop provides backdrop, LocalMeloXBackdrop provides true) {
                            Column(Modifier.padding(32.dp)) {
                                MeloXGlassButton(onClick = { clicks++ }, modifier = Modifier.size(160.dp, 48.dp).testTag("glass-button"),
                                    tint = Color.White.copy(alpha = .1f), surfaceColor = Color.White.copy(alpha = .08f)) { Text("Glass") }
                                Spacer(Modifier.height(24.dp))
                                MeloXGlassToggle(checked, { toggleCommits++; if (acceptChange) checked = it },
                                    enabled = toggleEnabled, modifier = Modifier.testTag("glass-toggle"))
                                Spacer(Modifier.height(24.dp))
                                MeloXLiquidSlider(sliderValue, { sliderValue = it; sliderCommits++; sliderEvents += "commit:$it enabled=$sliderEnabled" },
                                    onTransientValueChange = { sliderEvents += "transient:$it enabled=$sliderEnabled" },
                                    onValueChangeFinished = { sliderEvents += "finished enabled=$sliderEnabled" }, valueRange = 0f..1f,
                                    modifier = Modifier.size(sliderWidth.dp, 48.dp).testTag("glass-slider"),
                                    contentDescription = "Glass slider", enabled = sliderEnabled)
                            }
                            Box(Modifier.offset(220.dp, 32.dp).size(90.dp, 48.dp)
                                .graphicsLayer { scaleX = 1.25f; scaleY = 1.2f }
                                .clip(MeloXShapes.capsule).meloXSampleBackdrop(0.dp))
                        }
                    }
                }
            }
            val start = System.nanoTime()
            suspend fun settle(ms: Long = 100L) {
                val end = System.nanoTime() + ms * 1_000_000
                do { scene.render(System.nanoTime() - start).close(); delay(16) } while (System.nanoTime() < end)
            }
            fun snapshot(name: String): IntArray = scene.render(System.nanoTime() - start).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)?.use { File(output, "$prefix-$name.png").writeBytes(it.bytes) }
                Bitmap().use { bitmap ->
                    check(bitmap.allocN32Pixels(image.width, image.height)); check(image.readPixels(bitmap))
                    IntArray(image.width * image.height) { bitmap.getColor(it % image.width, it / image.width) }
                }
            }
            fun point(tag: String) = checkNotNull(scene.captureSemantics().tagged(tag)).boundsInWindow.center
            fun send(type: PointerEventType, p: Offset) = scene.sendPointerEvent(type, p, button = PointerButton.Primary)
            try {
                settle(250)
                val resting = snapshot("rest")
                val imageWidth = (360 * density).toInt()
                val cell = 12 * density
                var alignedPixels = 0; var sampledPixels = 0
                for (y in (50 * density).toInt() until (66 * density).toInt()) {
                    for (x in (240 * density).toInt() until (290 * density).toInt()) {
                        // Ignore antialiased checker edges; this zero-effects sample
                        // must remain aligned under a non-uniform ancestor scale.
                        if (x % cell < 2 || x % cell > cell - 2 || y % cell < 2 || y % cell > cell - 2) continue
                        val expected = if ((kotlin.math.floor(x / cell).toInt() + kotlin.math.floor(y / cell).toInt()) % 2 == 0) 0xff2563bd.toInt() else 0xffe9c742.toInt()
                        if (resting[y * imageWidth + x] == expected) alignedPixels++
                        sampledPixels++
                    }
                }
                check(alignedPixels >= sampledPixels * .95f) { "Scaled control magnified/misaligned its sampled background: $alignedPixels/$sampledPixels" }
                val button = point("glass-button")
                send(PointerEventType.Move, button); send(PointerEventType.Press, button)
                settle(300)
                val pressed = snapshot("pressed")
                if (frosted) check(resting.contentEquals(pressed)) { "Frosted button enabled liquid press optics" }
                else check(!resting.contentEquals(pressed)) { "Production button press has no optical response" }
                val pixelWidth = (360 * density).toInt()
                var white = 0; var centerPixels = 0
                for (y in (40 * density).toInt() until (72 * density).toInt()) {
                    for (x in (80 * density).toInt() until (144 * density).toInt()) {
                        val pixel = pressed[y * pixelWidth + x]
                        if ((pixel shr 16 and 255) > 250 && (pixel shr 8 and 255) > 250 && (pixel and 255) > 250) white++
                        centerPixels++
                    }
                }
                check(white < centerPixels / 4) { "Press focus washed out the sampled background; check premultiplied uniforms" }
                send(PointerEventType.Release, button); settle(700)
                check(clicks == 1) { "Physical button press did not produce exactly one click" }
                val beforeBackground = snapshot("released")
                phase = 1; settle(100)
                val afterBackground = snapshot("fresh-background")
                val width = (360 * density).toInt()
                var changedInside = 0
                for (y in (38 * density).toInt() until (74 * density).toInt()) {
                    for (x in (40 * density).toInt() until (180 * density).toInt()) {
                        val i = y * width + x
                        if (beforeBackground[i] != afterBackground[i]) changedInside++
                    }
                }
                check(changedInside > 100 * density * density) { "Cached sampler did not refresh its actual background" }
                val toggle = point("glass-toggle")
                val restingThumb = scene.captureSemantics().single { it.config.getOrNull(MeloXToggleOpticalStateKey) != null }
                check(kotlin.math.abs(restingThumb.boundsInWindow.center.y - toggle.y) <= .6f) {
                    "Switch thumb is not vertically centered in its track"
                }
                send(PointerEventType.Move, toggle); send(PointerEventType.Press, toggle); settle(300)
                snapshot("toggle-pressed")
                send(PointerEventType.Release, toggle); settle(800)
                check(checked) { "Physical switch tap failed" }
                acceptChange = false
                send(PointerEventType.Press, toggle)
                repeat(12) { index -> send(PointerEventType.Move, toggle - Offset((index + 1) * 3f * density, 0f)); settle(20) }
                send(PointerEventType.Release, toggle - Offset(36f * density, 0f)); settle(1000)
                val node = checkNotNull(scene.captureSemantics().tagged("glass-toggle"))
                check(checked && node.config.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On)
                check((node.config.getOrNull(MeloXToggleVisualProgressKey) ?: 0f) >= .99f) { "Rejected switch drag left stale visual state" }
                snapshot("toggle-rejected-drag")
                acceptChange = true
                val beforeDisabledDrag = toggleCommits
                send(PointerEventType.Move, toggle); send(PointerEventType.Press, toggle); settle(100)
                repeat(6) { index -> send(PointerEventType.Move, toggle - Offset((index + 1) * 3f * density, 0f)); settle(24) }
                toggleEnabled = false; settle(150)
                send(PointerEventType.Release, toggle - Offset(18f * density, 0f)); settle(700)
                check(toggleCommits == beforeDisabledDrag && checked) { "Switch committed after being disabled during drag" }
                toggleEnabled = true; settle(800)
                val restoredToggle = checkNotNull(scene.captureSemantics().tagged("glass-toggle"))
                check((restoredToggle.config.getOrNull(MeloXToggleVisualProgressKey) ?: 0f) >= .99f) {
                    "Disabling a switch during drag left its thumb between states"
                }
                snapshot("toggle-cancel-restored")
                val restoredOptics = scene.captureSemantics().mapNotNull { it.config.getOrNull(MeloXToggleOpticalStateKey) }.single()
                check(restoredOptics[0] <= .001f && kotlin.math.abs(restoredOptics[1] - 1f) <= .001f && kotlin.math.abs(restoredOptics[2] - 1f) <= .001f) {
                    "Cancelled switch retained pressed optical deformation: $restoredOptics"
                }
                send(PointerEventType.Move, toggle); send(PointerEventType.Press, toggle); settle(100)
                send(PointerEventType.Release, toggle); settle(800)
                check(!checked && toggleCommits == beforeDisabledDrag + 1) { "First switch tap after cancellation was lost or duplicated" }
                val sliderBounds = checkNotNull(scene.captureSemantics().tagged("glass-slider")).boundsInWindow
                val sliderTap = Offset(sliderBounds.left + sliderBounds.width * .8f, sliderBounds.center.y)
                send(PointerEventType.Move, sliderTap); send(PointerEventType.Press, sliderTap); settle(100)
                send(PointerEventType.Release, sliderTap); settle(800)
                check(sliderValue > .7f && sliderValue < .9f) { "Continuous slider tap was quantized or lost: $sliderValue" }
                if (!frosted) {
                    val thumb = Offset(sliderBounds.left + sliderBounds.width * sliderValue, sliderBounds.center.y)
                    send(PointerEventType.Move, thumb); send(PointerEventType.Press, thumb); settle(300)
                    snapshot("slider-pressed")
                    repeat(12) { index -> send(PointerEventType.Move, thumb - Offset((index + 1) * 6f * density, 0f)); settle(20) }
                    send(PointerEventType.Release, thumb - Offset(72f * density, 0f)); settle(800)
                    check(sliderValue < .7f && sliderValue > .2f) { "Liquid slider thumb drag failed: $sliderValue" }
                    // Resize an existing composition, without recreating the control.
                    // The pressed thumb scales by 1.5, so a 54px physical drag
                    // contributes ~36px locally: .3 on the new 120px track.
                    sliderWidth = 120; sliderValue = .4f; settle(800)
                    val resized = checkNotNull(scene.captureSemantics().tagged("glass-slider")).boundsInWindow
                    val resizedThumb = Offset(resized.left + resized.width * sliderValue, resized.center.y)
                    send(PointerEventType.Move, resizedThumb); send(PointerEventType.Press, resizedThumb); settle(250)
                    repeat(12) { index -> send(PointerEventType.Move, resizedThumb + Offset((index + 1) * 4.5f * density, 0f)); settle(24) }
                    send(PointerEventType.Release, resizedThumb + Offset(54f * density, 0f)); settle(800)
                    check(sliderValue in .66f.. .74f) { "Resized slider used its old track width: $sliderValue" }
                    snapshot("slider-resized")
                    val restingSlider = snapshot("slider-before-cancel")
                    val commitsBeforeCancel = sliderCommits
                    val cancelPoint = Offset(resized.left + resized.width * sliderValue, resized.center.y)
                    send(PointerEventType.Move, cancelPoint); send(PointerEventType.Press, cancelPoint); settle(250)
                    repeat(4) { index -> send(PointerEventType.Move, cancelPoint - Offset((index + 1) * 3f * density, 0f)); settle(24) }
                    sliderEnabled = false; settle(250)
                    send(PointerEventType.Release, cancelPoint - Offset(12f * density, 0f)); settle(250)
                    check(sliderCommits == commitsBeforeCancel) { "Disabled in-flight slider gesture committed a value: ${sliderEvents.takeLast(16)}" }
                    sliderEnabled = true; settle(1000)
                    val restoredSlider = snapshot("slider-cancel-restored")
                    val restoredNode = checkNotNull(scene.captureSemantics().tagged("glass-slider"))
                    val restoredProgress = restoredNode.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current
                    check(restoredProgress == sliderValue) { "Cancelled slider retained its uncommitted transient value: $restoredProgress/$sliderValue" }
                    cancelChangedPixels = restingSlider.indices.count { restingSlider[it] != restoredSlider[it] }
                    val optics = scene.captureSemantics().mapNotNull { it.config.getOrNull(MeloXSliderOpticalStateKey) }.single()
                    check(optics[0] <= .001f && kotlin.math.abs(optics[1] - 1f) <= .001f && kotlin.math.abs(optics[2] - 1f) <= .001f) {
                        "Cancelled slider retained pressed optics: $optics"
                    }
                }
                sliderEnabled = false; settle(100)
                val disabledValue = sliderValue
                val disabledTap = Offset(sliderBounds.left + sliderBounds.width * .2f, sliderBounds.center.y)
                send(PointerEventType.Move, disabledTap); send(PointerEventType.Press, disabledTap); settle(50)
                send(PointerEventType.Release, disabledTap); settle(100)
                check(sliderValue == disabledValue) { "Disabled slider accepted physical input" }
                snapshot("slider-disabled")
                repeat(8) { mounted = false; settle(32); mounted = true; settle(32) }
                samples.put(JSONObject().put("density", density).put("dark", dark).put("frosted", frosted).put("physical_button_clicks", clicks)
                    .put("changed_pixels_inside_glass", changedInside).put("rejected_switch_drag_restored", true)
                    .put("switch_centered_thumb", true).put("switch_inflight_cancel_restored", true)
                    .put("scaled_background_aligned_pixels", alignedPixels).put("scaled_background_checked_pixels", sampledPixels)
                    .put("slider_value", sliderValue).put("disabled_slider_preserved_value", true)
                    .put("resize_and_inflight_cancel", if (frosted) "not exercised: Material fallback" else "PASSED")
                    .put("cancel_edge_coverage_changed_pixels", cancelChangedPixels)
                    .put("mount_dispose_cycles", 8).put("memory", JSONObject(melox.diagnostics.MeloXMemoryDiagnostics.snapshot())))
            } finally {
                scene.close()
                val after = MeloXGlassFilterOwner.ownershipCounts()
                check(after.getValue("owners") == beforeOwnership.getValue("owners")) { "Scene retained glass filter owners after disposal" }
                check(after.getValue("created_chains") - after.getValue("released_chains") ==
                    beforeOwnership.getValue("created_chains") - beforeOwnership.getValue("released_chains")) { "Scene retained owned native filter chains" }
                ownership.put(JSONObject().put("case", prefix).put("before", JSONObject(beforeOwnership)).put("after", JSONObject(after)))
            }
        }
    }
    File(output, "audit.json").writeText(JSONObject().put("status", "PASSED").put("fixture", "generated checkerboard; real production glass components")
        .put("samples", samples).put("owned_filter_disposal", ownership).put("android_pixel_acceptance", "PENDING_PINNED_REFERENCE")
        .put("memory_scope", "observations only; no forced GC and no whole-process plateau assertion").toString(2) + "\n")
    auditMeloXGlassPopup(File(output.parentFile, "glass-popup"))
}
