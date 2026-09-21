package melox.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont

private fun loadFont(path: String, weight: FontWeight): Font? {
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
        FileFont(file = file, weight = weight, style = FontStyle.Normal)
    } catch (e: Exception) {
        System.err.println("Failed to load font $path: ${e.message}")
        null
    }
}

private object MeloXFontLoader

private val MiLanPro = run {
    val font = loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Normal)
    if (font != null) FontFamily(font) else FontFamily.Default
}

private val SFProSymbols = run {
    val font = loadFont("fonts/sf_pro_subset.ttf", FontWeight.Normal)
    if (font != null) FontFamily(font) else FontFamily.Default
}

private val MaterialSymbols = run {
    val font = loadFont("fonts/material_symbols_rounded_filled_static.ttf", FontWeight.Normal)
    if (font != null) FontFamily(font) else FontFamily.Default
}

val MeloXLanTingProFontFamily = MiLanPro
val MeloXSFProFontFamily = SFProSymbols
val MeloXMaterialSymbolsFamily = MaterialSymbols

val LocalMeloXFontFamily = compositionLocalOf { MeloXLanTingProFontFamily }
