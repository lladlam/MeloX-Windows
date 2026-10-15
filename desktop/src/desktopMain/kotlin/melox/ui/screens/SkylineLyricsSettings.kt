package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.legal.pinnedString

/** Pinned SettingsScreen.SkylineLyricsSettings, canonical keys/ranges/reset boundary. */
@Composable
internal fun SkylineLyricsSettings() {
    val r = MeloXSettingsRuntime
    Column(Modifier.testTag("skyline-settings")) {
        LyricsGroup(topSpacing = false) {
            LyricsToggle("settings_skyline_auto", "lyrics_skyline_enabled", r.skylineEnabled, "settings_skyline_auto_note")
            LyricsToggle("settings_skyline_song_info", "lyrics_skyline_song_info", r.skylineShowSongInfo)
            LyricsToggle("settings_skyline_keep_awake", "lyrics_skyline_keep_awake", r.skylineKeepsScreenAwake, "settings_skyline_keep_awake_note")
            LyricsIntChoice("settings_skyline_ambient_count", "lyrics_skyline_ambient_lines", r.skylineAmbientLines, (0..4).toList()) {
                if (it == 0) pinnedString("settings_skyline_off") else pinnedString("settings_skyline_lines", it)
            }
        }
        LyricsGroup {
            LyricsIntChoice("settings_skyline_max_chars", "lyrics_skyline_ambient_max_characters", r.skylineAmbientMaximumCharacters, (1..4).toList()) { pinnedString("settings_skyline_chars", it) }
            LyricsIntChoice("settings_skyline_max_visible", "lyrics_skyline_ambient_max_visible", r.skylineAmbientMaximumVisibleTexts, listOf(4, 8, 12, 16, 20, 24)) { pinnedString("settings_skyline_groups", it) }
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_skyline_current_size", "lyrics_skyline_current_font_size", r.skylineCurrentFontSize, 36f..84f, 47) { pinnedString("settings_unit_sp", it.toInt()) }
            LyricsFloatSlider("settings_skyline_word_scale", "lyrics_skyline_current_max_scale", r.skylineCurrentMaximumScale, 1f..1.2f, 19) { pinnedString("settings_unit_scale", it) }
            LyricsFloatSlider("settings_skyline_center_width", "lyrics_skyline_current_width", r.skylineCurrentWidth, .4f..0.82f, 20) { pinnedString("settings_unit_percent", (it * 100).toInt()) }
            LyricsFloatSlider("settings_skyline_next_size", "lyrics_skyline_next_font_size", r.skylineNextFontSize, 14f..44f, 29) { pinnedString("settings_unit_sp", it.toInt()) }
            LyricsFloatSlider("settings_skyline_next_brightness", "lyrics_skyline_next_opacity", r.skylineNextOpacity, .2f..0.8f, 11) { pinnedString("settings_unit_percent", (it * 100).toInt()) }
            LyricsFloatSlider("settings_skyline_center_spacing", "lyrics_skyline_current_spacing", r.skylineCurrentSpacing, 4f..36f, 31) { pinnedString("settings_unit_dp", it.toInt()) }
            LyricsFloatSlider("settings_skyline_bg_size", "lyrics_skyline_ambient_font_size", r.skylineAmbientFontSize, 24f..72f, 47) { pinnedString("settings_unit_sp", it.toInt()) }
            LyricsFloatSlider("settings_skyline_bg_brightness", "lyrics_skyline_ambient_opacity", r.skylineAmbientOpacity, .4f..1.8f, 13) { pinnedString("settings_unit_times_1", it) }
            LyricsFloatSlider("settings_skyline_bg_blur", "lyrics_skyline_ambient_blur", r.skylineAmbientBlur, 0f..2f, 19) { pinnedString("settings_unit_times_1", it) }
            LyricsFloatSlider("settings_skyline_max_tilt", "lyrics_skyline_ambient_max_tilt", r.skylineAmbientMaximumTilt, 0f..20f, 19) { pinnedString("settings_unit_degrees", it.toInt()) }
            LyricsFloatSlider("settings_skyline_drift", "lyrics_skyline_ambient_drift", r.skylineAmbientDrift, 0f..2f, 19) { pinnedString("settings_unit_times_1", it) }
        }
        Text(pinnedString("settings_skyline_reset"), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp).testTag("skyline-reset").clickable {
                // The source reset preserves enabled, song info, keep awake and ambient line count.
                listOf("lyrics_skyline_current_font_size" to 54f, "lyrics_skyline_current_max_scale" to 1.1f,
                    "lyrics_skyline_next_font_size" to 24f, "lyrics_skyline_current_spacing" to 14f,
                    "lyrics_skyline_current_width" to .64f, "lyrics_skyline_next_opacity" to .48f,
                    "lyrics_skyline_ambient_font_size" to 44f, "lyrics_skyline_ambient_opacity" to 1f,
                    "lyrics_skyline_ambient_blur" to 1f, "lyrics_skyline_ambient_max_tilt" to 8f,
                    "lyrics_skyline_ambient_drift" to 1f).forEach { (key, value) -> MeloXSettingsPreferences.setFloat(key, value) }
                MeloXSettingsPreferences.setInt("lyrics_skyline_ambient_max_characters", 4)
                MeloXSettingsPreferences.setInt("lyrics_skyline_ambient_max_visible", 16)
            })
    }
}
