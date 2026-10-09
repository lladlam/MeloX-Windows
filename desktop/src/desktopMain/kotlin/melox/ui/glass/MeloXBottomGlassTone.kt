package melox.ui.glass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import melox.ui.foundation.MeloXChromeTokens

/** Common scalar for all three chrome surfaces; backdrop sampler may provide intermediate tones. */
val LocalBottomBarTone = staticCompositionLocalOf { Float.NaN }

@Composable fun bottomBarTone(): Float = LocalBottomBarTone.current.takeUnless { it.isNaN() }?.coerceIn(0f, 1f)
    ?: if (MaterialTheme.colorScheme.background.luminance() < 0.5f) 1f else 0f

@Composable fun bottomLiquidGlassTint(): Color =
    lerp(Color.White.copy(alpha = 0.12f), Color.Black.copy(alpha = 0.10f), bottomBarTone())

@Composable fun bottomGlassFallbackColor(): Color = lerp(
    Color.White.copy(alpha = 0.56f), MaterialTheme.colorScheme.surface.copy(alpha = 0.58f), bottomBarTone(),
)

@Composable fun bottomGlassSurfaceColor(): Color = bottomGlassFallbackColor().copy(alpha = MeloXChromeTokens.BottomSurfaceAlpha)
