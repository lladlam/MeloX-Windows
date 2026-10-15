package melox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

/** Android main 94e17c9 ui/theme/MeloXTheme.kt, including the app-wide font. */
private val pinnedTypography by lazy { materialTypography(MeloXLanTingProFontFamily) }
private val systemTypography by lazy { materialTypography(androidx.compose.ui.text.font.FontFamily.Default) }
val MeloXMaterialTypography: Typography get() = if (melox.settings.MeloXSettingsRuntime.systemFontEnabled) systemTypography else pinnedTypography

private fun materialTypography(family: androidx.compose.ui.text.font.FontFamily): Typography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = family),
        displayMedium = base.displayMedium.copy(fontFamily = family),
        displaySmall = base.displaySmall.copy(fontFamily = family),
        titleLarge = base.titleLarge.copy(fontFamily = family),
        titleMedium = base.titleMedium.copy(fontFamily = family),
        titleSmall = base.titleSmall.copy(fontFamily = family),
        labelLarge = base.labelLarge.copy(fontFamily = family),
        labelMedium = base.labelMedium.copy(fontFamily = family),
        labelSmall = base.labelSmall.copy(fontFamily = family),
        headlineLarge = MeloXTypography.largeTitle.copy(fontFamily = family),
        headlineMedium = MeloXTypography.title2.copy(fontFamily = family),
        headlineSmall = MeloXTypography.headline.copy(fontFamily = family),
        bodyLarge = MeloXTypography.body.copy(fontFamily = family),
        bodyMedium = MeloXTypography.subheadline.copy(fontFamily = family),
        bodySmall = MeloXTypography.caption.copy(fontFamily = family),
    )
}

val MeloXMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
