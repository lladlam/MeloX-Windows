package melox.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import melox.lyrics.LyricLine
import melox.settings.MeloXLyricsFontWeight
import melox.settings.MeloXLyricsRenderingQuality
import melox.settings.MeloXSettingsPreferences
import melox.ui.screens.MeloXGlyphLyricText
import org.jetbrains.skia.EncodedImageFormat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs

/** Generated text fixture through the production glyph compositor; no provider/playback simulation. */
@OptIn(ExperimentalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    val output = File(args.firstOrNull() ?: "build/verification/lyric-raster").apply { mkdirs() }
    val results = JSONArray()
    var maximumDifference = 0.0
    withContext(Dispatchers.Main) {
        MeloXSettingsPreferences.initialize()
        for (density in listOf(1f, 1.25f, 1.5f, 1.75f, 2f)) {
            for (weight in listOf(MeloXLyricsFontWeight.Bold, MeloXLyricsFontWeight.Heavy)) {
                MeloXSettingsPreferences.setString("lyrics_font_weight", weight.name)
                for ((index, text) in listOf("Like in me", "Like, like, like, yeah", "喜欢 喜欢 喜欢",
                    "office affinity fi ffi", "Cafe\u0301 \uD83C\uDFB5", "Like in me\nLike, like, like, yeah").withIndex()) {
                    val line = LyricLine(timeMs = 0, text = text)
                    val prefix = "${density}-${weight.name}-$index"
                    for (native in listOf(false, true)) {
                        val scene = ImageComposeScene(1100, 180, Density(density)) {
                            Box(Modifier.fillMaxSize().background(Color.Black)) {
                                MeloXGlyphLyricText(line, { 0L }, native, 1f, false, 1f,
                                    unplayedAlpha = 1f, followOwnTiming = true, renderingQuality = MeloXLyricsRenderingQuality.High)
                            }
                        }
                        try {
                            repeat(3) { scene.render(it * 16_000_000L).close() }
                            scene.render(64_000_000L).use { image ->
                                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                                    File(output, "$prefix-${if (native) "glyph" else "shaped"}.png").writeBytes(data.bytes)
                                }
                            }
                        } finally { scene.close() }
                    }
                    val shaped = ImageIO.read(File(output, "$prefix-shaped.png"))
                    val glyph = ImageIO.read(File(output, "$prefix-glyph.png"))
                    var referenceInk = 0L
                    var difference = 0L
                    for (y in 0 until shaped.height) for (x in 0 until shaped.width) {
                        val reference = shaped.getRGB(x, y) and 255
                        val candidate = glyph.getRGB(x, y) and 255
                        referenceInk += reference
                        difference += abs(candidate - reference)
                    }
                    check(referenceInk > 0)
                    val ratio = difference.toDouble() / referenceInk
                    maximumDifference = maxOf(maximumDifference, ratio)
                    results.put(JSONObject().put("density", density).put("weight", weight.name).put("fixture", index)
                        .put("reference_ink", referenceInk).put("absolute_pixel_difference", difference)
                        .put("normalized_difference", ratio))
                    println("Lyric raster $prefix normalized difference=$ratio")
                }
            }
        }
    }
    File(output, "raster.json").writeText(JSONObject().put("os", System.getProperty("os.name"))
        .put("check", "production unlifted individual glyphs versus the same production shaped text")
        .put("maximum_normalized_difference", maximumDifference).put("limit", .01)
        .put("status", if (maximumDifference <= .01) "PASSED" else "FAILED")
        .put("android_pixel_acceptance", "BLOCKED").put("results", results).toString(2))
    check(maximumDifference <= .01) { "Individual glyph geometry diverges from its own shaped paragraph: $maximumDifference" }
}
