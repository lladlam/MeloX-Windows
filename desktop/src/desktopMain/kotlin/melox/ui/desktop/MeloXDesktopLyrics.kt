package melox.ui.desktop

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import melox.layout.meloXDesktopLyricsFontSize
import melox.music.model.MusicTrack
import melox.settings.MeloXSettingsRuntime
import melox.ui.screens.LocalMeloXLyricSurface
import melox.ui.screens.LyricsPage
import melox.ui.screens.MeloXLyricSurface

/** Source desktop typography/color adapter over the real lyric loader and timed glyphs. */
@Composable
internal fun DesktopLyrics(track: MusicTrack, foreground: Color, active: Boolean = true,
    compact: Boolean = false, focusOffset: Float? = null) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontSize = meloXDesktopLyricsFontSize(maxWidth.value, compact) * MeloXSettingsRuntime.lyricFontScale
        val focus = if (compact) 69.734375f / (maxHeight.value - fontSize * 1.2f).coerceAtLeast(1f)
            else focusOffset?.let { it / maxHeight.value.coerceAtLeast(1f) } ?: .5f
        CompositionLocalProvider(LocalMeloXLyricSurface provides MeloXLyricSurface(foreground = foreground,
            fontScale = fontSize / 34f, focusPosition = focus.coerceIn(0f, 1f), horizontalInset = 20,
            paragraphSpacing = if (compact) 36f else 50f, allowsBlur = !compact)) {
            LyricsPage(track, foreground, interactive = active)
        }
    }
}
