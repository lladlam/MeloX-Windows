package melox.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.platform.Font as DesktopFont

private val extractedFonts = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

private fun loadFont(path: String, weight: FontWeight, axisWeight: Int = weight.weight): Font? {
    return try {
        val cl = Thread.currentThread().contextClassLoader
            ?: MeloXFontLoader::class.java.classLoader
            ?: return null
        val url = cl.getResource(path) ?: return null
        val file = try {
            java.io.File(url.toURI())
        } catch (e: Exception) {
            // All nine variable-weight instances must share the same extracted
            // 19 MB Android font, rather than create nine identical temp files.
            extractedFonts.computeIfAbsent(url.toExternalForm()) {
                val temp = java.io.File.createTempFile("melox_font_", ".ttf")
                try {
                    url.openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    temp.deleteOnExit()
                    temp
                } catch (error: Exception) {
                    temp.delete()
                    throw error
                }
            }
        }
        DesktopFont(file = file, weight = weight, style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight)))
    } catch (e: Exception) {
        melox.platform.logError("MeloXFont", "Failed to load font $path: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

private object MeloXFontLoader

/** Android CSS 800/900 both select wght=700. SkParagraph synthesizes bold when
 * requested weight minus the native face weight is >=200, even with Compose
 * FontSynthesis.None. Request the equivalent 800 alias to retain the exact
 * pinned outlines/advances instead of emboldening the 700 face a second time.
 * Persisted logical weight and source variable-font bytes remain untouched.
 */
internal fun lanTingRenderWeight(weight: FontWeight): FontWeight =
    if (weight == FontWeight.Black) FontWeight.ExtraBold else weight

private val MiLanPro = run {
    // Android res/font/mi_lan_pro.xml maps CSS weights to private axis coordinates.
    val axes = listOf(100 to 150, 200 to 200, 300 to 250, 400 to 340,
        500 to 400, 600 to 540, 700 to 630, 800 to 700, 900 to 700)
    val fonts = axes.mapNotNull { (weight, axis) -> loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight(weight), axis) }
    check(fonts.size == axes.size) { "Android 小米兰亭 Pro 字体未完整加载：fonts/mi_lan_pro_vf.ttf" }
    melox.platform.logInfo("MeloXFont", "Android MiLan Pro loaded: fonts/mi_lan_pro_vf.ttf; CSS/axis weights=$axes")
    FontFamily(fonts)
}

private val sfFonts = listOf(FontWeight.Normal, FontWeight.Medium).mapNotNull { weight ->
    loadFont("fonts/sf_pro_subset.ttf", weight)
}
private val SFProSymbols = if (sfFonts.isNotEmpty()) FontFamily(sfFonts) else FontFamily.Default

private val sfTypefaces by lazy {
    val bytes = requireNotNull(MeloXFontLoader::class.java.getResourceAsStream("/fonts/sf_pro_subset.ttf")) {
        "SF Symbols font could not be loaded"
    }.use { it.readBytes() }
    org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
        val base = requireNotNull(org.jetbrains.skia.FontMgr.default.makeFromData(data))
        mapOf(FontWeight.Normal to base, FontWeight.Medium to
            base.makeClone(org.jetbrains.skia.FontVariation("wght", FontWeight.Medium.weight.toFloat())))
    }
}

internal fun meloXSfTypeface(weight: FontWeight): org.jetbrains.skia.Typeface =
    sfTypefaces.getValue(weight)

private val lyricTypefaces by lazy {
    val bytes = requireNotNull(MeloXFontLoader::class.java.getResourceAsStream("/fonts/mi_lan_pro_vf.ttf")).use { it.readBytes() }
    org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
        val base = requireNotNull(org.jetbrains.skia.FontMgr.default.makeFromData(data))
        try {
            melox.platform.logInfo("MeloXFont", "Native lyric typeface loaded: family=${base.familyName}; bytes=${bytes.size}")
            listOf(100 to 150, 200 to 200, 300 to 250, 400 to 340, 500 to 400, 600 to 540,
                700 to 630, 800 to 700, 900 to 700).associate { (weight, axis) ->
                weight to base.makeClone(org.jetbrains.skia.FontVariation("wght", axis.toFloat()))
            }
        } finally { base.close() }
    }
}

internal fun meloXLyricTypeface(weight: FontWeight): org.jetbrains.skia.Typeface =
    lyricTypefaces.getValue(weight.weight)

private val MaterialSymbols = run {
    val font = loadFont("fonts/material_symbols_rounded_filled_static.ttf", FontWeight.Normal)
    if (font != null) FontFamily(font) else FontFamily.Default
}

val MeloXLanTingProFontFamily = MiLanPro
val MeloXSFProFontFamily = SFProSymbols
val MeloXMaterialSymbolsFamily = MaterialSymbols

val LocalMeloXFontFamily = compositionLocalOf { MeloXLanTingProFontFamily }
