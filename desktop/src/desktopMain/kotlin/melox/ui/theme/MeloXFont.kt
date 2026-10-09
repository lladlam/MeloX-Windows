package melox.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.platform.Font as DesktopFont

private fun loadFont(path: String, weight: FontWeight, axisWeight: Int = weight.weight): Font? {
    return try {
        val cl = Thread.currentThread().contextClassLoader
            ?: MeloXFontLoader::class.java.classLoader
            ?: return null
        val url = cl.getResource(path) ?: return null
        val file = try {
            java.io.File(url.toURI())
        } catch (e: Exception) {
            val temp = java.io.File.createTempFile("melox_font_", ".ttf")
            temp.deleteOnExit()
            url.openStream().use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            temp
        }
        DesktopFont(file = file, weight = weight, style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight)))
    } catch (e: Exception) {
        System.err.println("Failed to load font $path: ${e.message}")
        null
    }
}

private object MeloXFontLoader

private val MiLanPro = run {
    // Android res/font/mi_lan_pro.xml maps CSS weights to private axis coordinates.
    val axes = listOf(100 to 150, 200 to 200, 300 to 250, 400 to 340,
        500 to 400, 600 to 540, 700 to 630, 800 to 700, 900 to 700)
    val fonts = axes.mapNotNull { (weight, axis) -> loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight(weight), axis) }
    if (fonts.isNotEmpty()) FontFamily(fonts) else FontFamily.Default
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
