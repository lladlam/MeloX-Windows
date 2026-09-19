package melox.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** 米兰亭 Pro 字体族 - 桌面版 */
val MeloXLanTingProFontFamily = FontFamily(
    Font(resource = "fonts/mi_lan_pro_vf.ttf", weight = FontWeight.Light),
    Font(resource = "fonts/mi_lan_pro_vf.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/mi_lan_pro_vf.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/mi_lan_pro_vf.ttf", weight = FontWeight.Bold),
)

/** SF Pro 字体族 - 桌面版 */
val MeloXSFProFontFamily = FontFamily(
    Font(resource = "fonts/sf_pro_subset.ttf", weight = FontWeight.Light),
    Font(resource = "fonts/sf_pro_subset.ttf", weight = FontWeight.Normal),
    Font(resource = "fonts/sf_pro_subset.ttf", weight = FontWeight.Medium),
    Font(resource = "fonts/sf_pro_subset.ttf", weight = FontWeight.Bold),
)

/** Material Symbols 字体族 - 图标 */
val MeloXMaterialSymbolsFamily = FontFamily(
    Font(resource = "fonts/material_symbols_rounded_filled_static.ttf", weight = FontWeight.Normal),
)

/** 组合本地字体提供者 */
val LocalMeloXFontFamily = compositionLocalOf<FontFamily> { MeloXLanTingProFontFamily }

/** 记忆化字体族选择 */
@Composable
fun rememberMeloXFontFamily(): FontFamily {
    val useSystemFont = remember { false } // 默认使用内置字体
    return remember(useSystemFont) {
        if (useSystemFont) FontFamily.Default else MeloXLanTingProFontFamily
    }
}
