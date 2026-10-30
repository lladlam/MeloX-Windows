package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import melox.lyrics.LyricsDocument
import melox.playback.MeloXPlaybackOutput
import melox.player.AudioPlayer
import melox.settings.MeloXSecondaryLyricMode
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.legal.pinnedString

internal data class FloatingLyricLines(val primary: String, val secondary: String)

internal fun floatingLyricLines(document: LyricsDocument?, position: Long, mode: MeloXSecondaryLyricMode,
                                translationEnabled: Boolean, romanizationEnabled: Boolean): FloatingLyricLines? {
    val index = document?.highlightedIndex(position.coerceAtLeast(0)) ?: return null
    val line = document.lines[index]
    val next = document.lines.getOrNull(index + 1)?.text.orEmpty()
    val translation = line.translation.orEmpty()
    val romanization = line.romanization.orEmpty()
    val secondary = when (mode) {
        MeloXSecondaryLyricMode.Translation -> translation
        MeloXSecondaryLyricMode.Romanization -> romanization
        MeloXSecondaryLyricMode.NextLine -> next
        MeloXSecondaryLyricMode.Hidden -> ""
        MeloXSecondaryLyricMode.Auto -> when {
            translationEnabled && translation.isNotBlank() -> translation
            romanizationEnabled && romanization.isNotBlank() -> romanization
            else -> next
        }
    }
    return FloatingLyricLines(line.text, secondary)
}

/** Independent always-on-top window follows the same real track, lyric loader and output clock. */
@Composable
internal fun FloatingLyricsWindow() {
    val state = rememberWindowState(width = 500.dp, height = 116.dp, position = WindowPosition.PlatformDefault)
    Window(onCloseRequest = { MeloXSettingsPreferences.setBoolean("floating_lyrics_enabled", false) },
        state = state, title = "MeloX Lyrics", undecorated = true, transparent = true,
        alwaysOnTop = true, resizable = false, focusable = false) {
        val track by AudioPlayer.currentTrack.collectAsState()
        val position by MeloXPlaybackOutput.positionMs.collectAsState()
        val lyricState = track?.let { rememberMeloXPlayerLyrics(it) }
        val lines = floatingLyricLines(lyricState?.document, position + MeloXSettingsRuntime.lyricAdvanceMs,
            MeloXSettingsRuntime.floatingSecondaryMode, MeloXSettingsRuntime.showLyricTranslation,
            MeloXSettingsRuntime.showLyricRomanization)
        val font = MeloXSettingsRuntime.floatingFontSizeSp
        val alpha = if (MeloXSettingsRuntime.floatingHighContrast) .86f else .58f
        Row(Modifier.fillMaxSize().padding(4.dp).clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF101015).copy(alpha = alpha)), verticalAlignment = Alignment.CenterVertically) {
            WindowDraggableArea(Modifier.weight(1f).fillMaxHeight()) {
                Column(Modifier.fillMaxSize().padding(start = 18.dp, top = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(lines?.primary ?: track?.title ?: pinnedString("settings_floating_show"), color = Color.White,
                        fontSize = font.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!lines?.secondary.isNullOrBlank()) Text(lines!!.secondary, color = Color.White.copy(alpha = .65f),
                        fontSize = (font * .68f).coerceAtLeast(10f).sp, textAlign = TextAlign.Center,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Text("×", Modifier.clickable { MeloXSettingsPreferences.setBoolean("floating_lyrics_enabled", false) }
                .padding(horizontal = 14.dp, vertical = 12.dp), color = Color.White.copy(alpha = .8f), fontSize = 20.sp)
        }
    }
}
