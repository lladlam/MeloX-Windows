package melox.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import melox.platform.MeloXPreferences
import melox.platform.getPreferences

data class MeloXEqualizerSettings(val enabled: Boolean = false, val preset: String = "Flat",
    val preampDb: Float = 0f, val gainsDb: List<Float> = List(10) { 0f }) {
    companion object {
        /** Pinned app-settings keys take priority. Old desktop values are read without deleting or renaming them. */
        fun read(app: MeloXPreferences = getPreferences("melox_app_settings"),
            legacy: MeloXPreferences = getPreferences("melox_playback")): MeloXEqualizerSettings {
            fun store(key: String) = if (app.contains(key)) app else legacy
            fun gain(key: String) = store(key).getFloat(key, 0f).let { if (it.isFinite()) it.coerceIn(-12f, 12f) else 0f }
            val preset = store("equalizer_preset").getString("equalizer_preset", "Flat")
                .takeIf { it in MeloXEqualizerController.PRESETS } ?: "Flat"
            return MeloXEqualizerSettings(store("equalizer_enabled").getBoolean("equalizer_enabled", false), preset,
                gain("equalizer_preamp_db"), if (preset == "Custom") List(10) { gain("equalizer_custom_band_$it") }
                else MeloXEqualizerController.PRESETS.getValue(preset).toList())
        }
        private val mutableCurrent by lazy { MutableStateFlow(read()) }
        val current get() = mutableCurrent.asStateFlow()
        fun refresh() { mutableCurrent.value = read() }
        fun resetLegacyOverrides(app: MeloXPreferences = getPreferences("melox_app_settings"),
            legacy: MeloXPreferences = getPreferences("melox_playback")) {
            val defaults = mutableMapOf("equalizer_enabled" to "false", "equalizer_preset" to "Flat", "equalizer_preamp_db" to "0.0")
            repeat(10) { defaults["equalizer_custom_band_$it"] = "0.0" }
            if (defaults.keys.any(legacy::contains)) app.putValues(defaults)
            refresh()
        }
    }
}
