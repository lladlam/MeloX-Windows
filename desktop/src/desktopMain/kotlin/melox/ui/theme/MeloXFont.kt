package melox.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.FileFont
import java.io.File

private fun loadFont(path: String, weight: FontWeight): Font {
    val url = MeloXFontLoader::class.java.classLoader?.getResource(path)
        ?: error("Font resource not found: $path")
    return try {
        FileFont(file = File(url.toURI()), weight = weight, style = FontStyle.Normal)
    } catch (e: Exception) {
        val tempFile = File.createTempFile("font_", ".ttf")
        tempFile.deleteOnExit()
        url.openStream().use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        FileFont(file = tempFile, weight = weight, style = FontStyle.Normal)
    }
}

private object MeloXFontLoader

private val MiLanPro = FontFamily(
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Thin),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.ExtraLight),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Light),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Normal),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Medium),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.SemiBold),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Bold),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.ExtraBold),
    loadFont("fonts/mi_lan_pro_vf.ttf", FontWeight.Black),
)

private val SFProSymbols = FontFamily(
    loadFont("fonts/sf_pro_subset.ttf", FontWeight.Normal),
)

private val MaterialSymbols = FontFamily(
    loadFont("fonts/material_symbols_rounded_filled_static.ttf", FontWeight.Normal),
)

val MeloXLanTingProFontFamily = MiLanPro
val MeloXSFProFontFamily = SFProSymbols
val MeloXMaterialSymbolsFamily = MaterialSymbols

val LocalMeloXFontFamily = compositionLocalOf { MeloXLanTingProFontFamily }
