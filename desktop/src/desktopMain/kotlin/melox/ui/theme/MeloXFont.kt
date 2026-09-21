package melox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// TODO: Load actual fonts from resources (Font() constructor varies by platform)
private val MeloXLanTingPro = FontFamily.Default
private val MeloXSFPro = FontFamily.Default
private val MeloXMaterialSymbols = FontFamily.Default

val MeloXLanTingProFontFamily = MeloXLanTingPro
val MeloXSFProFontFamily = MeloXSFPro
val MeloXMaterialSymbolsFamily = MeloXMaterialSymbols

val LocalMeloXFontFamily = compositionLocalOf { MeloXLanTingProFontFamily }

@Composable
fun rememberMeloXFontFamily(): FontFamily {
    val useSystemFont = remember { false }
    return remember(useSystemFont) {
        if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily
    }
}
