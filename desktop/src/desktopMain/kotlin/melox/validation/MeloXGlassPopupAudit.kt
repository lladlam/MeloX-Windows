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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import melox.ui.glass.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject

/** Exercise the actual popup material separately from native window placement. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
internal suspend fun auditMeloXGlassPopup(output: File) = withContext(Dispatchers.Main) {
    output.mkdirs()
    File(output, "audit.json").delete()
    val cases = JSONArray()
    for (dark in listOf(false, true)) for (sampled in listOf(false, true)) {
        val ownersBefore = MeloXGlassFilterOwner.ownershipCounts()
        var progress by mutableFloatStateOf(1f)
        var velocity by mutableFloatStateOf(0f)
        val surface = if (dark) Color(0xff202830) else Color(0xffe8ecf0)
        val label = "${if (dark) "dark" else "light"}-${if (sampled) "sampled" else "fallback"}"
        val scene = ImageComposeScene(480, 420, Density(1f), coroutineContext = coroutineContext) {
            MaterialTheme(colorScheme = if (dark) darkColorScheme(surface = surface) else lightColorScheme(surface = surface)) {
                val backdrop = rememberMeloXLayerBackdrop()
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize().meloXRecordBackdrop(backdrop).drawBehind {
                        for (y in 0..20) for (x in 0..24) drawRect(
                            if ((x + y) % 2 == 0) Color.Red else Color.Blue, Offset(x * 20f, y * 20f), Size(20f, 20f))
                    })
                    // Keep the legacy flag true in the unavailable case: it must
                    // never substitute for an actual non-recursive backdrop.
                    CompositionLocalProvider(LocalMeloXBackdrop provides true,
                        LocalMeloXLayerBackdrop provides if (sampled) backdrop else null) {
                        check(meloXGlassAvailable() == sampled)
                        MeloXPopupGlassMenu(meloXGlassAvailable(), progress, velocity, false, false, IntSize(80, 36), 4) {
                            Text("Popup material fixture")
                        }
                    }
                }
            }
        }
        val start = System.nanoTime()
        suspend fun settle() { repeat(8) { scene.render(System.nanoTime() - start).close(); delay(16) } }
        try {
            settle()
            val bounds = checkNotNull(scene.captureSemantics().tagged("settings-popup-surface")).boundsInWindow
            scene.render(System.nanoTime() - start).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)?.use { File(output, "$label.png").writeBytes(it.bytes) }
                Bitmap().use { bitmap ->
                    check(bitmap.allocN32Pixels(image.width, image.height)); check(image.readPixels(bitmap))
                    check(bitmap.getColor(bounds.center.x.toInt(), bounds.center.y.toInt()) == surface.toArgb()) {
                        "Popup center leaked the sampled page instead of pinned opaque surface: $label"
                    }
                }
            }
            for (p in listOf(.5f, Float.NaN, Float.POSITIVE_INFINITY)) {
                progress = p; velocity = Float.NaN; settle()
            }
            cases.put(JSONObject().put("case", label).put("opaque_center", true).put("nonfinite_animation_values", "PASSED"))
        } finally {
            scene.close()
            val after = MeloXGlassFilterOwner.ownershipCounts()
            check(after.getValue("owners") == ownersBefore.getValue("owners"))
            check(after.getValue("created_chains") - after.getValue("released_chains") ==
                ownersBefore.getValue("created_chains") - ownersBefore.getValue("released_chains"))
        }
    }
    File(output, "audit.json").writeText(JSONObject().put("status", "PASSED").put("cases", cases)
        .put("scope", "production popup material; native cross-window placement and pinned Android pixels pending").toString(2) + "\n")
}
