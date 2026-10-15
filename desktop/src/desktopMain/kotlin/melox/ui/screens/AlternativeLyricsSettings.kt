package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.settings.*
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.glass.*
import melox.ui.legal.pinnedString

@Composable
internal fun AlternativeLyricsSettings(showStyleSelector: Boolean = true) {
    Column {
        if (showStyleSelector) {
            MeloXSettingsDropdown(pinnedString("settings_lyrics_style"), MeloXSettingsRuntime.lyricsStyle,
                listOf(MeloXLyricsStyle.AppleMusic to "Apple Music", MeloXLyricsStyle.Eva to pinnedString("settings_lyrics_style_eva"), MeloXLyricsStyle.TextPV to pinnedString("settings_lyrics_style_text_pv")),
                onSelected = { MeloXSettingsPreferences.setString("lyrics_style", it.name) })
        }
        if (MeloXSettingsRuntime.lyricsStyle == MeloXLyricsStyle.TextPV) {
            Spacer(Modifier.height(10.dp))
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
                Text(pinnedString("settings_text_pv_style"), Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
                val titles = listOf("blue_bold", "kinetic_split", "blue_plane", "cyber_grunge", "geometric", "rain_city", "cyberpunk_hud", "emotion_cinema", "hysteric_night", "spider_web", "staggered", "calm_villain", "girly_clouds", "sweet_pink", null, "kawaii_pixel", "crime_scene", "haruhikage")
                MeloXTextPVStyle.entries.take(18).forEachIndexed { index, style ->
                    Row(Modifier.fillMaxWidth().testTag("lyrics-pv-${style.name.lowercase()}").semantics { selected = MeloXSettingsRuntime.textPVStyle == style }
                        .clickable { MeloXSettingsPreferences.setString("lyrics_text_pv_style", style.name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(titles[index]?.let { pinnedString("settings_pv_$it") } ?: "Fly Me to the Moon", fontSize = 16.sp)
                        if (MeloXSettingsRuntime.textPVStyle == style) Text("✓", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            LyricsPresentationSlider(pinnedString("settings_pv_motion_intensity"), MeloXSettingsRuntime.textPVMotionIntensity, 0f..2f, .1f, "lyrics-pv-intensity") {
                MeloXSettingsPreferences.setFloat("lyrics_text_pv_motion_intensity", it)
            }
            LyricsPresentationSlider(pinnedString("settings_pv_animation_speed"), MeloXSettingsRuntime.textPVAnimationSpeed, 0f..4f, .1f, "lyrics-pv-speed") {
                MeloXSettingsPreferences.setFloat("lyrics_text_pv_animation_speed", it)
            }
            MeloXGlassButton(onClick = {
                MeloXSettingsPreferences.setString("lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name)
                MeloXSettingsPreferences.setFloat("lyrics_text_pv_motion_intensity", 1f)
                MeloXSettingsPreferences.setFloat("lyrics_text_pv_animation_speed", 2f)
            }, modifier = Modifier.fillMaxWidth().testTag("lyrics-pv-reset")) { Text(pinnedString("settings_pv_reset")) }
        }
    }
}

@Composable
private fun LyricsPresentationSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, step: Float, tag: String, onValueChange: (Float) -> Unit) {
    Text(title, Modifier.padding(start = 16.dp, top = 12.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
    MeloXLiquidSlider(value, onValueChange, valueRange = range, stepSize = step, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(tag), contentDescription = title)
}
