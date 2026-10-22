package melox.ui.desktop

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import melox.ui.glass.bottomBarTone
import melox.ui.glass.bottomGlassSurfaceColor
import melox.ui.glass.bottomLiquidGlassTint
import melox.ui.glass.meloXLiquidBottomBar
import melox.ui.glass.MeloXBackdropEdge

/** The Android MiniPlay material, shared by both Desktop side panels. */
@Composable
internal fun Modifier.desktopLiquidPanel(shape: Shape, reflectPageAtRightEdge: Boolean = false): Modifier =
    this.background(MaterialTheme.colorScheme.background, shape)
        .meloXLiquidBottomBar(shape, bottomLiquidGlassTint(), bottomGlassSurfaceColor(),
            dark = bottomBarTone() > .5f,
            reflectionEdge = if (reflectPageAtRightEdge) MeloXBackdropEdge.Right else MeloXBackdropEdge.Left)
