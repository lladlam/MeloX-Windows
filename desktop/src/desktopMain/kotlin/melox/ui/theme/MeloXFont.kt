package melox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily

/** 米兰亭 Pro 字体族 - 桌面版（使用系统字体） */
val MeloXLanTingProFontFamily = FontFamily.Default

/** SF Pro 字体族 - 桌面版 */
val MeloXSFProFontFamily = FontFamily.Default

/** Material Symbols 字体族 - 图标 */
val MeloXMaterialSymbolsFamily = FontFamily.Default

/** 组合本地字体提供者 */
val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { MeloXLanTingProFontFamily }

/** 记忆化字体族选择 */
@Composable
fun rememberMeloXFontFamily(): FontFamily {
    val useSystemFont = remember { false }
    return remember(useSystemFont) {
        if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily
    }
}
