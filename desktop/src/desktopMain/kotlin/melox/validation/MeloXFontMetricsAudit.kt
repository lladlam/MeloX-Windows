package melox.validation

import androidx.compose.foundation.Canvas
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.theme.meloXLyricTypeface
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Font
import org.jetbrains.skia.Paint
import org.jetbrains.skia.TextBlob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** Independent native font advances and shaped paragraph, not a compositor self-comparison. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    val out = File(args[0]).apply { mkdirs() }
    val rows = JSONArray()
    val referenceData = JSONObject(requireNotNull(Thread.currentThread().contextClassLoader
        .getResourceAsStream("parity/mi-lan-pro-metrics.json")).bufferedReader().use { it.readText() })
    val fontBytes = requireNotNull(Thread.currentThread().contextClassLoader
        .getResourceAsStream("fonts/mi_lan_pro_vf.ttf")).use { it.readBytes() }
    val digest = java.security.MessageDigest.getInstance("SHA-256").digest(fontBytes)
        .joinToString("") { "%02x".format(it) }
    check(digest == referenceData.getString("font_sha256"))
    val references = referenceData.getJSONArray("rows")
    val staticManifest = JSONObject(requireNotNull(Thread.currentThread().contextClassLoader
        .getResourceAsStream("parity/mi-lan-pro-static.json")).bufferedReader().use { it.readText() })
    check(staticManifest.getJSONObject("source").getString("sha256") == digest)
    val entries = staticManifest.getJSONArray("entries")
    val styleWeightByCss = mutableMapOf<Int, Int>()
    val distinctFiles = mutableSetOf<String>()
    for (i in 0 until entries.length()) {
        val entry = entries.getJSONObject(i)
        val css = entry.getInt("css")
        styleWeightByCss[css] = entry.getInt("us_weight_class")
        val bytes = requireNotNull(Thread.currentThread().contextClassLoader
            .getResourceAsStream(entry.getString("file"))).use { it.readBytes() }
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        require(sha == entry.getString("sha256")) { "static instance ${entry.getString("file")} hash mismatch" }
        distinctFiles += entry.getString("file")
    }
    check(distinctFiles.size == 8) { "expected 8 frozen instance files, found ${distinctFiles.size}" }
    var failedCases = 0
    var frozenTypefacesVerified = 0
    var totalCases = 0
    withContext(Dispatchers.Main) {
        for (index in 0 until references.length()) for (size in listOf(34f, 59.5f, 23.12f))
            for (mode in listOf("All", "None")) {
            val expected = references.getJSONObject(index)
            val weight = expected.getInt("css")
            val fixture = expected.getInt("fixture")
            val synthesis = if (mode == "All") FontSynthesis.All else FontSynthesis.None
            val renderWeight = FontWeight(weight)
            val text = expected.getString("text")
            // Subpixel sampling keeps the reference row aligned with Android-like
            // linear (unhinted) rasterization; observed thin-stroke bias vs the
            // SkParagraph row stays inside the documented envelope below.
            val native = Font(meloXLyricTypeface(FontWeight(weight)), size).apply { isSubpixel = true }
            val prefix = "$weight-$fixture-$size-$mode"
            val record = JSONObject().put("weight", weight).put("mode", mode).put("render_weight", renderWeight.weight)
                .put("fixture", fixture).put("size_px", size).put("reference_axis", expected.getInt("axis"))
            record.put("native_advances", JSONArray(native.getWidths(native.getStringGlyphs(text)).toList()))
            if (size == 34f && fixture == 0) {
                // Static instances must expose no variation axis and the frozen
                // OS/2 style weight; a variable face here would reopen the
                // Windows design-advance collapse.
                val variations = native.typeface?.variations ?: emptyArray()
                record.put("native_measure_width", native.measureTextWidth(text))
                    .put("typeface_style", native.typeface?.fontStyle?.toString())
                    .put("typeface_variations", JSONArray(variations.map { JSONObject().put("tag", it.tag).put("value", it.value) }))
                require(variations.isEmpty()) { "CSS $weight typeface still has variation axes" }
                require(native.typeface?.fontStyle?.weight == styleWeightByCss[weight]) {
                    "CSS $weight typeface style weight ${native.typeface?.fontStyle?.weight} != ${styleWeightByCss[weight]}"
                }
                frozenTypefacesVerified++
            }
            val scene = ImageComposeScene(1600, 180) {
                val measurer = rememberTextMeasurer()
                val layout = measurer.measure(AnnotatedString(text), TextStyle(fontFamily = MeloXLanTingProFontFamily,
                    fontSize = size.sp, fontWeight = renderWeight, fontSynthesis = synthesis), softWrap = false)
                record.put("compose_width", layout.getLineRight(0))
                record.put("reference_width", expected.getDouble("advance_em") * size)
                record.put("compose_boxes", JSONArray(text.indices.map { i ->
                    val b = layout.getBoundingBox(i)
                    listOf(b.left, b.right)
                }))
                Canvas(Modifier.fillMaxSize()) {
                    drawRect(Color.Black)
                    drawText(layout, Color.White)
                    Paint().use { paint ->
                        paint.color = -1
                        TextBlob.makeFromPosH(native.getStringGlyphs(text),
                            FloatArray(text.length) { layout.getBoundingBox(it).left },
                            layout.firstBaseline, native)!!.use { blob ->
                            drawContext.canvas.nativeCanvas.drawTextBlob(blob, 0f, 90f, paint)
                        }
                    }
                }
            }
            try {
                scene.render(0L).use { image -> image.encodeToData(EncodedImageFormat.PNG)!!.use {
                    File(out, "$prefix.png").writeBytes(it.bytes)
                } }
                val image = ImageIO.read(File(out, "$prefix.png"))
                var reference = 0L; var candidate = 0L; var difference = 0L
                for (y in 0 until 90) for (x in 0 until image.width) {
                    val a = image.getRGB(x, y) and 255
                    val b = image.getRGB(x, y + 90) and 255
                    candidate += a; reference += b; difference += abs(a - b)
                }
                record.put("native_ink", reference).put("compose_ink", candidate)
                    .put("ink_ratio", candidate.toDouble() / reference)
                    .put("normalized_difference", difference.toDouble() / reference)
                val advanceError = abs(record.getDouble("compose_width") - record.getDouble("reference_width"))
                val inkRatio = candidate.toDouble() / reference
                // Independent HarfBuzz advances catch spacing/advance collapse.
                // The ink envelope catches outline substitution: excess ink above
                // +5% is synthetic bold (measured +19..22% when it existed); the
                // -15% floor allows the measured thin-stroke sampling bias of
                // SkParagraph vs the linear reference (worst -9.7%, CSS 100 CJK
                // at 34 px on Linux; Windows rows rasterize identical, ratio 1.0).
                val passed = advanceError <= .35 && inkRatio <= 1.05 && inkRatio >= 0.85
                record.put("advance_error_px", advanceError).put("accepted", passed)
                totalCases++
                if (!passed) failedCases++
                println("Font outlines $prefix ink=${candidate.toDouble() / reference} advanceError=$advanceError accepted=$passed")
                rows.put(record)
            } finally { scene.close(); native.close() }
        }
    }
    val passedAll = failedCases == 0 && frozenTypefacesVerified >= 5
    File(out, "metrics.json").writeText(JSONObject().put("results", rows).put("font_sha256", digest)
        .put("os", System.getProperty("os.name")).put("total_cases", totalCases)
        .put("failed_cases", failedCases).put("static_files_verified", distinctFiles.size)
        .put("frozen_typefaces_verified", frozenTypefacesVerified)
        .put("advance_limit_px", .35).put("ink_ratio_lower", 0.85).put("ink_ratio_upper", 1.05)
        .put("status", if (passedAll) "PASSED" else "FAILED")
        .put("android_pixel_acceptance", "BLOCKED").toString(2))
    check(passedAll) {
        "Independent font audit: $failedCases/$totalCases cases failed; " +
            "static files ${distinctFiles.size}/8; frozen typefaces $frozenTypefacesVerified"
    }
}
