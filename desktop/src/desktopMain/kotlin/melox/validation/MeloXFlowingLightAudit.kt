package melox.validation

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import java.io.File
import kotlinx.coroutines.*
import melox.ui.player.*
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** Generated artwork is an explicit renderer fixture, never a playback/provider substitute. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    val output = File(args.first()).apply { mkdirs() }
    File(output, "audit.json").delete()
    val artwork = File(output, "generated-artwork.png")
    Surface.makeRasterN32Premul(160, 160).use { surface ->
        surface.canvas.clear(0xff237bba.toInt())
        org.jetbrains.skia.Paint().use { paint ->
            val colors = intArrayOf(0xffed3762.toInt(), 0xff2198cf.toInt(), 0xffd5bb42.toInt(),
                0xff632faf.toInt(), 0xffd37c33.toInt(), 0xff227b5b.toInt(), 0xff2251c1.toInt(),
                0xffc844b1.toInt(), 0xff33b9aa.toInt())
            for (i in colors.indices) {
                paint.color = colors[i]
                surface.canvas.drawRect(org.jetbrains.skia.Rect.makeXYWH((i % 3) * 54f, (i / 3) * 54f, 54f, 54f), paint)
            }
        }
        surface.makeImageSnapshot().use { image -> image.encodeToData()!!.use { artwork.writeBytes(it.bytes) } }
    }
    val rows = JSONArray()
    withContext(Dispatchers.Main) {
        melox.settings.MeloXSettingsPreferences.initialize()
        for (quality in MeloXLyricsRenderingQuality.entries) {
            var playing by mutableStateOf(true)
            var reduced by mutableStateOf(false)
            var mounted by mutableStateOf(true)
            val scene = ImageComposeScene(320, 240, Density(1f), coroutineContext = coroutineContext) {
                if (mounted) MeloXFlowingLightBackdrop(artwork.absolutePath, playing, quality = quality, reduceMotion = reduced)
            }
            val start = System.nanoTime()
            suspend fun settle(ms: Long) {
                val until = System.nanoTime() + ms * 1_000_000
                do { scene.render(System.nanoTime() - start).close(); delay(16) } while (System.nanoTime() < until)
            }
            fun snapshot(name: String): IntArray = scene.render(System.nanoTime() - start).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)!!.use { File(output, "${quality.name}-$name.png").writeBytes(it.bytes) }
                Bitmap().use { bitmap ->
                    check(bitmap.allocN32Pixels(image.width, image.height)); check(image.readPixels(bitmap))
                    IntArray(image.width * image.height) { bitmap.getColor(it % image.width, it / image.width) }
                }
            }
            fun changed(a: IntArray, b: IntArray) = a.indices.count { a[it] != b[it] }
            try {
                settle(1_500)
                val a = snapshot("flow-a")
                settle(700)
                val b = snapshot("flow-b")
                val motion = changed(a, b)
                check(motion > a.size / 3) { "Flowing Light is visually frozen: $quality $motion" }
                playing = false
                settle(250)
                val paused = snapshot("pause-a")
                settle(500)
                check(changed(paused, snapshot("pause-b")) == 0) { "Paused flow kept animating" }
                playing = true
                settle(600)
                check(changed(paused, snapshot("resume")) > a.size / 3) { "Flow did not resume" }
                reduced = true
                settle(200)
                val still = snapshot("reduced-a")
                settle(500)
                check(changed(still, snapshot("reduced-b")) == 0) { "Reduce motion kept animating" }
                reduced = false
                repeat(12) { mounted = false; settle(18); mounted = true; settle(24) }
                rows.put(JSONObject().put("quality", quality.name).put("changed_pixels", motion)
                    .put("pixels", a.size).put("pause_frozen", true).put("resume_moves", true)
                    .put("reduce_motion_frozen", true).put("unmount_remount_cycles", 12))
            } finally { scene.close() }
        }
    }
    val colors = ArtworkDynamicPalette.Fallback.cells
    fun field(phase: Float, energy: Float = 0f, beat: Float = 0f, reactive: Boolean = false, uniform: Boolean = false) = IntArray(48 * 110).also {
        fillFlowingMeshPixels(it, 48, 110, if (uniform) List(9) { Color(.4f, .5f, .6f) } else colors,
            if (uniform) Color(.4f, .5f, .6f) else ArtworkDynamicPalette.Fallback.average,
            phase, energy, beat, reactive, FloatArray(9), FloatArray(9))
    }
    val wrap = field(FlowPhasePeriod)
    val zero = field(0f)
    check(wrap.indices.all { i -> (0..16 step 8).all { shift -> abs(((wrap[i] ushr shift) and 255) - ((zero[i] ushr shift) and 255)) <= 1 } })
    check(field(1f).contentEquals(field(1f, 1f, 1f))) { "Unanalysed clock drove the field" }
    val uniformA = field(0f, uniform = true)
    val uniformB = field(3f, 1f, 1f, true, true)
    check(uniformA.indices.all { i -> (0..16 step 8).all { shift -> abs(((uniformA[i] ushr shift) and 255) - ((uniformB[i] ushr shift) and 255)) <= 1 } }) {
        "Beat changed brightness rather than only field geometry"
    }
    File(output, "audit.json").writeText(JSONObject().put("status", "PASSED")
        .put("source", "production Flowing Light Canvas with generated artwork fixture")
        .put("android_commit", "53244b9d6b21d7e3a609e8cb11876efef70f218b")
        .put("phase_wrap_continuous", true).put("no_synthetic_reactivity", true).put("no_beat_brightness_pulse", true)
        .put("samples", rows).toString(2) + "\n", Charsets.UTF_8)
    println("PASS: Flowing Light actual raster motion, pause/resume, reduced motion, wrap and disposal")
}
