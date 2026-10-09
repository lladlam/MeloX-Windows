package melox.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp

/** Android main 94e17c9 ui/theme/MeloXTheme.kt, including the app-wide font. */
val MeloXMaterialTypography = Typography().let { base ->
    val family = MeloXLanTingProFontFamily
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
        headlineLarge = MeloXTypography.largeTitle,
        headlineMedium = MeloXTypography.title2,
        headlineSmall = MeloXTypography.headline,
        bodyLarge = MeloXTypography.body,
        bodyMedium = MeloXTypography.subheadline,
        bodySmall = MeloXTypography.caption,
    )
}

val MeloXMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)
