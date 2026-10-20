package melox.ui.screens

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Presentation inputs only; loading, timing, seeking and glyph rendering remain production-owned. */
internal data class MeloXLyricSurface(
    val foreground: Color = Color.White,
    val fontScale: Float? = null,
    val focusPosition: Float? = null,
    val horizontalInset: Int = 8,
    val paragraphSpacing: Float? = null,
    val allowsBlur: Boolean = true,
)

internal val LocalMeloXLyricSurface = staticCompositionLocalOf { MeloXLyricSurface() }
