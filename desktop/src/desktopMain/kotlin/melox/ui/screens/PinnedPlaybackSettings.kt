package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.playback.*
import melox.settings.*
import melox.ui.glass.MeloXGlassDialog
import melox.ui.legal.pinnedString
import kotlin.math.abs

internal fun qualityTitle(quality: MusicQuality) = pinnedString("settings_quality_" + when (quality) {
    MusicQuality.Standard -> "standard"; MusicQuality.High -> "high"; MusicQuality.Lossless -> "lossless"
    MusicQuality.HiResolution -> "hires"; MusicQuality.HighDefinitionSurround -> "surround"
    MusicQuality.ImmersiveSurround -> "immersive"; MusicQuality.UltraClearMaster -> "master"
})

@Composable
internal fun PinnedPlaybackSettings() {
    var quality by remember { mutableStateOf(MusicQualityPreferences.read()) }
    SettingsGlassGroup {
        SettingsChoice("settings_playback_quality", "playback_quality", quality, MusicQuality.entries.map { it to qualityTitle(it) }) {
            quality = it; PlaybackCommands.changeQuality(it)
        }
        SettingsChoice("settings_volume_slider", "playback_volume_mode", MeloXSettingsRuntime.volumeControlMode,
            listOf(MeloXVolumeControlMode.System to pinnedString("settings_volume_system"), MeloXVolumeControlMode.Player to pinnedString("settings_volume_player")), enabled = false) {
            MeloXSettingsPreferences.setString("playback_volume_mode", it.name)
        }
        SettingsToggleRow("settings_allow_other_apps", "playback_allow_other_apps", false, enabled = false)
        SettingsToggleRow("settings_remember_player_page", "playback_remember_page", true)
        SettingsToggleRow("settings_save_queue_on_exit", "playback_save_queue", true)
        SettingsToggleRow("settings_remember_last_song", "playback_remember_last_song", true, enabled = false)
        SettingsToggleRow("settings_heart_mode_on_launch", "playback_heart_mode_on_launch", false)
        SettingsToggleRow("settings_previous_restarts", "playback_previous_restarts", true)
        SettingsToggleRow("settings_show_quality_tip", "player_show_quality_tip", true)
        LyricsIntChoice("settings_player_transition_duration", "player_transition_duration_ms", MeloXSettingsRuntime.playerTransitionDurationMs,
            listOf(240, 300, 360, 460, 600)) { pinnedString("settings_duration_ms", it) }
    }
    Spacer(Modifier.height(10.dp))
    EqualizerSettings()
    Spacer(Modifier.height(10.dp))
    PinnedAutoMixSettings()
    Spacer(Modifier.height(10.dp))
    var clear by remember { mutableStateOf(false) }
    SettingsGlassGroup {
        SettingsActionButton(pinnedString("settings_clear_analysis_cache"), Modifier.testTag("analysis-cache-clear")) { clear = true }
    }
    if (clear) SettingsConfirmation(pinnedString("settings_clear_analysis_cache_confirm"), onDismiss = { clear = false }) {
        PlaybackCommands.playerController()?.clearAnalysisCache() ?: MeloXAudioAnalysisStore().clear()
        clear = false
    }
    SettingsPlatformNote(pinnedString("settings_volume_slider"), pinnedString("settings_allow_other_apps"), pinnedString("settings_remember_last_song"))
}

@Composable
private fun PinnedAutoMixSettings() {
    var settings by remember { mutableStateOf(MeloXAutoMixSettings.read()) }
    fun refresh() { settings = MeloXAutoMixSettings.read() }
    val modes = MeloXPlaybackModeRuntime
    var intro by remember { mutableStateOf(false) }
    SettingsGlassGroup {
        SettingsExternalToggleRow(pinnedString("settings_automix"), modes.autoMixEnabled, "setting-auto_mix") {
            PlaybackCommands.playerController()?.setAutoMixEnabled(it) ?: MeloXPlaybackModePreferences.setAutoMix(it)
        }
        SettingsToggleRow("settings_transition_animation", "transition_ui_enabled", true)
    }
    if (modes.autoMixEnabled) {
        Spacer(Modifier.height(10.dp))
        if (settings.mode == MeloXAutoMixMode.Smart) SettingsGlassGroup {
            SettingsExternalToggleRow(pinnedString("settings_smart_queue"), modes.smartQueueEnabled, "setting-smart_queue") {
                if (it && !MeloXSettingsPreferences.boolean("smart_queue_intro_shown")) intro = true
                else PlaybackCommands.playerController()?.setSmartQueueEnabled(it) ?: MeloXPlaybackModePreferences.setSmartQueue(it)
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsGlassGroup {
            SettingsChoice("settings_automix_mode", "automix_mode", settings.mode,
                listOf(MeloXAutoMixMode.Smart to pinnedString("settings_automix_smart"), MeloXAutoMixMode.Fixed to pinnedString("settings_automix_fixed"))) {
                MeloXPlaybackModePreferences.setAutoMixString("automix_mode", it.name); refresh()
            }
            if (settings.mode == MeloXAutoMixMode.Smart) {
                SettingsExternalToggleRow(pinnedString("settings_skip_quiet_opening"), settings.skipQuietOpening, "automix_skip_quiet_opening") {
                    MeloXPlaybackModePreferences.setAutoMixBoolean("automix_skip_quiet_opening", it); refresh()
                }
                SettingsExternalToggleRow(pinnedString("settings_analyze_streaming"), settings.analyzeStreaming, "automix_analyze_streaming") {
                    MeloXPlaybackModePreferences.setAutoMixBoolean("automix_analyze_streaming", it); refresh()
                }
                SettingsChoice("settings_smart_transition_length", "automix_transition_bars", settings.transitionBars,
                    listOf(4, 8, 16).map { it to pinnedString("settings_bars", it) }) { MeloXPlaybackModePreferences.setAutoMixInt("automix_transition_bars", it); refresh() }
                SettingsChoice("settings_tail_cut", "automix_tail_cut_bars", settings.tailCutBars,
                    listOf(0, 2, 4, 8).map { it to if (it == 0) pinnedString("settings_tail_keep") else pinnedString("settings_tail_cut_bars", it) }) {
                    MeloXPlaybackModePreferences.setAutoMixInt("automix_tail_cut_bars", it); refresh()
                }
                val confidence = listOf(.30f, .42f, .55f, .70f)
                SettingsChoice("settings_min_confidence", "automix_minimum_confidence", confidence.minBy { abs(settings.minimumConfidence - it) },
                    confidence.map { it to "${(it * 100).toInt()}%" }) { MeloXPlaybackModePreferences.setAutoMixFloat("automix_minimum_confidence", it); refresh() }
            }
            val durations = listOf(3000L, 6000L, 8000L, 12000L, 16000L, 20000L)
            SettingsChoice("settings_crossfade_duration", "automix_fixed_duration_ms", durations.minBy { abs(settings.fixedDurationMs - it) },
                durations.map { it to pinnedString("settings_seconds", (it / 1000).toInt()) }) { MeloXPlaybackModePreferences.setAutoMixLong("automix_fixed_duration_ms", it); refresh() }
            val preload = listOf(30000L, 60000L, 90000L, 120000L, 180000L)
            SettingsChoice("settings_preload_lead", "automix_preload_lead_ms", preload.minBy { abs(settings.preloadLeadMs - it) },
                preload.map { it to pinnedString("settings_seconds", (it / 1000).toInt()) }) { MeloXPlaybackModePreferences.setAutoMixLong("automix_preload_lead_ms", it); refresh() }
            SettingsChoice("settings_fade_curve", "automix_fade_curve", settings.fadeCurve,
                listOf(MeloXAutoMixFadeCurve.EqualPower to pinnedString("settings_fade_equal_power"), MeloXAutoMixFadeCurve.Smooth to pinnedString("settings_fade_smooth"),
                    MeloXAutoMixFadeCurve.Linear to pinnedString("settings_fade_linear"))) { MeloXPlaybackModePreferences.setAutoMixString("automix_fade_curve", it.name); refresh() }
            SettingsChoice("settings_analysis_fallback", "automix_fallback", settings.fallback,
                listOf(MeloXAutoMixFallback.Crossfade to pinnedString("settings_fallback_selected_duration"), MeloXAutoMixFallback.ShortCrossfade to pinnedString("settings_fallback_short_crossfade"),
                    MeloXAutoMixFallback.Normal to pinnedString("settings_fallback_normal"))) { MeloXPlaybackModePreferences.setAutoMixString("automix_fallback", it.name); refresh() }
            SettingsExternalToggleRow(pinnedString("settings_tempo_matching"), settings.tempoMatching, "automix_tempo_matching") {
                MeloXPlaybackModePreferences.setAutoMixBoolean("automix_tempo_matching", it); refresh()
            }
            if (settings.tempoMatching) {
                val amounts = listOf(.02f, .05f, .08f, .10f)
                SettingsChoice("settings_max_tempo_adjustment", "automix_max_tempo_adjustment", amounts.minBy { abs(settings.maxTempoAdjustment - it) },
                    amounts.map { it to "${(it * 100).toInt()}%" }) { MeloXPlaybackModePreferences.setAutoMixFloat("automix_max_tempo_adjustment", it); refresh() }
            }
        }
    }
    if (intro) MeloXGlassDialog(true, onDismiss = { intro = false }, modifier = Modifier.testTag("smart-queue-intro")) {
        Text(pinnedString("settings_smart_queue"), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp)); Text(pinnedString("settings_smart_queue_intro"))
        Spacer(Modifier.height(8.dp)); Text(pinnedString("settings_smart_queue_rules"), fontWeight = FontWeight.Bold)
        listOf("closest", "unique", "background").forEach { Text(pinnedString("settings_smart_queue_rule_$it")) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton(pinnedString("action_cancel"), Modifier.weight(1f)) { intro = false }
            SettingsActionButton(pinnedString("settings_action_enable"), Modifier.weight(1f).testTag("smart-queue-enable")) {
                PlaybackCommands.playerController()?.setSmartQueueEnabled(true) ?: MeloXPlaybackModePreferences.setSmartQueue(true)
                MeloXSettingsPreferences.setBoolean("smart_queue_intro_shown", true); intro = false
            }
        }
    }
}

@Composable
internal fun SettingsConfirmation(title: String, message: String? = null, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    MeloXGlassDialog(true, onDismiss = onDismiss, modifier = Modifier.testTag("settings-confirmation")) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        message?.let { Text(it, Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f)) }
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton(pinnedString("action_cancel"), Modifier.weight(1f).testTag("settings-confirm-cancel"), onClick = onDismiss)
            SettingsActionButton(pinnedString("settings_privacy_clear_confirm"), Modifier.weight(1f).testTag("settings-confirm-accept"), onClick = onConfirm)
        }
    }
}
