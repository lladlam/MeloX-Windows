package melox.ui.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import melox.settings.MeloXSettingsRuntime
import melox.ui.glass.meloXSampleBackdrop

/** Borderless Desktop regular-glass surface, sampling the recorded page only. */
@Composable
internal fun Modifier.desktopLiquidPanel(shape: Shape): Modifier {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val frosted = MeloXSettingsRuntime.frostedGlassEnabled
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) .24f else .34f)
    return clip(shape)
        .meloXSampleBackdrop(if (frosted) 24.dp else 12.dp, shape = shape,
            lensHeight = if (frosted) 0.dp else 24.dp,
            lensAmount = if (frosted) 0.dp else 12.dp,
            dispersion = !frosted, vibrant = !frosted)
        .drawBehind { drawRect(surface) }
}
