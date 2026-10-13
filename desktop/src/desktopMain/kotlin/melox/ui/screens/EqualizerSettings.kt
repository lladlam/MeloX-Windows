package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.playback.MeloXEqualizerController
import melox.playback.MeloXEqualizerSettings
import melox.settings.MeloXSettingsPreferences
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.MeloXGlassToggle
import melox.ui.glass.MeloXLiquidSlider
import melox.ui.legal.pinnedString
import kotlin.math.round

@Composable
internal fun EqualizerSettings() {
    val settings by MeloXEqualizerSettings.current.collectAsState()
    Column(Modifier.padding(horizontal = 20.dp).testTag("equalizer-settings").semantics {
        stateDescription = "${settings.enabled}:${settings.preset}:${settings.preampDb}"
    }) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXIosListRow(title = pinnedString("settings_equalizer"), showChevron = false,
                trailingContent = { MeloXGlassToggle(settings.enabled, onCheckedChange = {
                    MeloXSettingsPreferences.setBoolean("equalizer_enabled", it)
                }, modifier = Modifier.testTag("equalizer-enabled")) })
        }
        Spacer(Modifier.height(10.dp))
        if (!settings.enabled) return@Column
        Text(pinnedString("settings_eq_presets"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            MeloXEqualizerController.PRESETS.keys.forEach { preset ->
                Row(Modifier.fillMaxWidth().testTag("equalizer-preset-${preset.lowercase()}")
                    .semantics { selected = settings.preset == preset }
                    .clickable { MeloXSettingsPreferences.setString("equalizer_preset", preset) }
                    .padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(pinnedString("settings_eq_${preset.lowercase()}"), fontSize = 16.sp)
                    if (settings.preset == preset) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (settings.preset == "Custom") {
            Spacer(Modifier.height(12.dp))
            listOf("31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz", "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz").forEachIndexed { index, label ->
                EqualizerGainSlider(label, settings.gainsDb[index], "equalizer-band-$index") {
                    MeloXSettingsPreferences.setFloat("equalizer_custom_band_$index", it)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(pinnedString("settings_eq_preamp"), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Spacer(Modifier.height(8.dp))
        EqualizerGainSlider(pinnedString("settings_eq_input_gain"), settings.preampDb, "equalizer-preamp") {
            MeloXSettingsPreferences.setFloat("equalizer_preamp_db", it)
        }
    }
}

@Composable
private fun EqualizerGainSlider(title: String, value: Float, tag: String, onValueChange: (Float) -> Unit) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = round((dragging ?: value) * 2f) / 2f
    val label = pinnedString(if (shown > 0) "settings_eq_gain_positive" else "settings_eq_gain_db", shown)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    MeloXLiquidSlider(value, onValueChange = { onValueChange(round(it * 2f) / 2f) }, onTransientValueChange = { dragging = it },
        onValueChangeFinished = { dragging = null }, valueRange = -12f..12f, stepSize = .5f, visibilityThreshold = .001f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(tag), contentDescription = "$title, $label")
}
