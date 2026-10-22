package melox.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.*
import melox.ui.desktop.desktopLiquidPanel
import melox.ui.glass.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject

/** Generated optical target around the production panels, never a business data source. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    val output = File(args.single()).apply { mkdirs() }
    val results = JSONArray()
    withContext(Dispatchers.Main) {
        melox.settings.MeloXSettingsPreferences.initialize()
        for (density in listOf(1f, 2f)) for (dark in listOf(false, true)) for (frosted in listOf(false, true)) {
            melox.settings.MeloXSettingsPreferences.setBoolean("player_frosted_glass", frosted)
            var phase by mutableIntStateOf(0)
            val before = MeloXGlassFilterOwner.ownershipCounts()
            val width = (900 * density).toInt()
            val prefix = "$density-$dark-$frosted"
            val scene = ImageComposeScene(width, (320 * density).toInt(), Density(density), coroutineContext = coroutineContext) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                    val backdrop = rememberMeloXLayerBackdrop()
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                        // Nothing is painted behind the Dock. Its only source is
                        // the live page to its right, just like the app layout.
                        Box(Modifier.offset(207.dp).size(693.dp, 320.dp).meloXRecordBackdrop(backdrop).drawBehind {
                            drawRect(if (phase == 0) Color(0xFF1479CE) else Color(0xFFE89924))
                        })
                        CompositionLocalProvider(LocalMeloXLayerBackdrop provides backdrop) {
                            Box(Modifier.offset(8.dp, 8.dp).size(199.dp, 304.dp)
                                .desktopLiquidPanel(RoundedCornerShape(18.dp), reflectPageAtRightEdge = true))
                            Box(Modifier.offset(693.dp, 8.dp).size(199.dp, 304.dp)
                                .desktopLiquidPanel(RoundedCornerShape(18.dp)))
                        }
                    }
                }
            }
            val start = System.nanoTime()
            suspend fun settle() { repeat(6) { scene.render(System.nanoTime() - start).close(); delay(16) } }
            fun snapshot(name: String) = scene.render(System.nanoTime() - start).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "$prefix-$name.png").writeBytes(it.bytes) }
                Bitmap().use { bitmap ->
                    check(bitmap.allocN32Pixels(image.width, image.height)); check(image.readPixels(bitmap))
                    IntArray(image.width * image.height) { bitmap.getColor(it % image.width, it / image.width) }
                }
            }
            try {
                settle(); val blue = snapshot("blue")
                phase = 1; settle(); val amber = snapshot("amber")
                var dockChanged = 0; var inspectorChanged = 0; var equalMaterial = 0; var count = 0
                for (y in (60 * density).toInt() until (260 * density).toInt()) {
                    for (x in (45 * density).toInt() until (155 * density).toInt()) {
                        val d = y * width + x
                        val i = y * width + x + (685 * density).toInt()
                        check(blue[d] == amber[d] && blue[i] == amber[i]) { "Page imagery leaked through the readable panel body" }
                        if (blue[d] == blue[i] && amber[d] == amber[i]) equalMaterial++
                        count++
                    }
                }
                var edgeCount = 0
                for (y in (60 * density).toInt() until (260 * density).toInt()) {
                    for (offset in (5 * density).toInt() until (23 * density).toInt()) {
                        val d = y * width + (207 * density).toInt() - offset
                        val i = y * width + (693 * density).toInt() + offset
                        if (blue[d] != amber[d]) dockChanged++
                        if (blue[i] != amber[i]) inspectorChanged++
                        edgeCount++
                    }
                }
                check(dockChanged > edgeCount * .95f) { "Dock did not reflect the changing page: $dockChanged/$edgeCount" }
                check(inspectorChanged > edgeCount * .95f) { "Inspector sampled stale pixels: $inspectorChanged/$edgeCount" }
                check(equalMaterial > count * .95f) { "Panel material differs: $equalMaterial/$count" }
                results.put(JSONObject().put("density", density).put("dark", dark).put("frosted", frosted)
                    .put("dock_live_pixels", dockChanged).put("inspector_live_pixels", inspectorChanged)
                    .put("tested_edge_pixels", edgeCount).put("readable_body_unchanged", true)
                    .put("identical_material_pixels", equalMaterial).put("tested_pixels", count))
            } finally { scene.close() }
            check(MeloXGlassFilterOwner.ownershipCounts()["owners"] == before["owners"]) { "Panel filters were not released" }
        }
    }
    File(output, "audit.json").writeText(JSONObject().put("status", "PASSED").put("cases", results)
        .put("scope", "generated optical target, production panel rendering; not Android pixel parity or hardware FPS")
        .toString(2) + "\n")
    println("PASS Desktop glass: 8 cases, live adjacent-page reflection, identical panel material, filters released")
}
