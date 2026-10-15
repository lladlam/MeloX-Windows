package melox.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

object MeloXTypography {
    internal val family: FontFamily get() = if (melox.settings.MeloXSettingsRuntime.systemFontEnabled) FontFamily.Default else MeloXLanTingProFontFamily
    val largeTitle get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 41.sp,
    )
    val title2 get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    )
    val headline get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val body get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val subheadline get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )
    val caption get() = TextStyle(
        fontFamily = family,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}
