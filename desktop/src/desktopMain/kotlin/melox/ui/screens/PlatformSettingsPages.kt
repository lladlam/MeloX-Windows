package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.settings.*
import melox.music.model.MusicSource
import melox.ui.glass.MeloXGlassTextField
import melox.ui.legal.pinnedString

/** Source layout retained; unavailable OS adapters are disabled instead of stored no-op enables. */
@Composable
internal fun SystemPlaybackSettings() {
    SettingsGlassGroup {
        SettingsToggleRow("settings_system_lyrics", "system_lyrics_enabled", false, enabled = false)
        SettingsChoice("settings_system_title_format", "system_lyrics_title_mode", MeloXSettingsRuntime.systemLyricTitleMode,
            listOf(MeloXSystemLyricTitleMode.LyricFirst to pinnedString("settings_system_title_lyric"),
                MeloXSystemLyricTitleMode.SongFirst to pinnedString("settings_system_title_song")), enabled = false) {}
        SettingsToggleRow("settings_lyric_notification", "lyrics_notifications_enabled", false, enabled = false)
        SettingsToggleRow("settings_hyperos_island", "hyperos_super_island_enabled", false, enabled = false)
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        listOf("next_line" to false, "progress" to true, "artwork" to true, "background_only" to false, "dismiss_paused" to true).forEach { (key, value) ->
            SettingsToggleRow("settings_notify_$key", "lyrics_notification_$key", value, enabled = false)
        }
    }
    listOf(Triple("title_template", "title_template", "{lyric}"), Triple("subtitle_template", "subtitle_template", "{song} · {artist}"),
        Triple("fallback", "fallback", "{song} · {artist}")).forEach { (title, key, fallback) ->
        Text(pinnedString("settings_notify_$title"), Modifier.padding(top = 8.dp, bottom = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        MeloXGlassTextField(MeloXSettingsPreferences.string("lyrics_notification_$key", fallback), onValueChange = {}, enabled = false,
            modifier = Modifier.fillMaxWidth().testTag("lyrics_notification_$key"))
        Spacer(Modifier.height(6.dp))
    }
    Text(pinnedString("settings_notify_variables"), Modifier.padding(bottom = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
    SettingsActionButton(pinnedString("settings_notify_test"), Modifier.testTag("notification-test"), enabled = false) {}
    SettingsPlatformNote(pinnedString("settings_route_system_playback"))
}

@Composable
internal fun FloatingLyricsSettings() {
    SettingsGlassGroup {
        SettingsToggleRow("settings_floating_show", "floating_lyrics_enabled", false, enabled = false)
        SettingsChoice("settings_floating_secondary", "floating_lyrics_secondary_mode", MeloXSettingsRuntime.floatingSecondaryMode,
            listOf("Auto" to "secondary_auto", "Translation" to "translation", "Romanization" to "romanization", "NextLine" to "next_line", "Hidden" to "hidden")
                .map { (key, label) -> MeloXSecondaryLyricMode.valueOf(key) to pinnedString("settings_floating_$label") }, enabled = false) {}
        SettingsChoice("settings_floating_font_size", "floating_lyrics_font_size", MeloXSettingsRuntime.floatingFontSizeSp,
            listOf(14, 16, 18, 20, 24, 28).map { it to pinnedString("settings_unit_sp", it) }, enabled = false) {}
        SettingsToggleRow("settings_floating_contrast", "floating_lyrics_high_contrast", true, enabled = false)
    }
    SettingsPlatformNote(pinnedString("settings_route_floating"))
}

@Composable
internal fun RecognitionSettings() {
    val duration = MeloXSettingsPreferences.string("recognition_duration", "6").toIntOrNull() ?: 6
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        listOf(3 to "fast", 6 to "recommended", 9 to "noisy").forEach { (value, label) ->
            SettingsChoiceRow(pinnedString("settings_recognition_seconds_$label"), duration == value, "recognition-duration-$value", enabled = false) {}
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(pinnedString("settings_recognition_start"), Modifier.testTag("recognition-start"), enabled = false) {}
    SettingsPlatformNote(pinnedString("settings_route_recognition"))
}

@Composable
internal fun ListenTogetherSettings() {
    SettingsInfoCard(pinnedString("settings_listen_info"))
    Spacer(Modifier.height(12.dp))
    SettingsActionButton(pinnedString("settings_listen_start"), Modifier.testTag("listen-together-start"), enabled = false) {}
    Spacer(Modifier.height(10.dp))
    MeloXGlassTextField("", onValueChange = {}, enabled = false, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(pinnedString("settings_listen_paste"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f)) })
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(pinnedString("settings_listen_join"), Modifier.testTag("listen-together-join"), enabled = false) {}
    SettingsPlatformNote(pinnedString("settings_route_listen_together"))
}

@Composable
internal fun DeveloperSettings() {
    // Pinned release builds omit the DEBUG-only performance/AutoMix controls.
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun ExperimentalSettings(source: MusicSource) {
    var confirm by remember { mutableStateOf<String?>(null) }
    if (source == MusicSource.Bilibili) {
        SettingsGlassGroup { SettingsToggleRow("settings_bilibili_align", "bilibili_lyric_audio_alignment", false, enabled = false) }
        SettingsInfoCard(pinnedString("settings_bilibili_align_warning"))
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_bilibili_clear"), Modifier.testTag("bilibili-associations-clear")) { confirm = "bilibili" }
        Spacer(Modifier.height(10.dp))
    }
    val store = melox.music.provider.PlaybackAccountStore
    SettingsGlassGroup {
        // Separate playback credentials exist, but desktop login screens do not yet accept the playback slot.
        SettingsExternalToggleRow(pinnedString("settings_playback_second_account"), store.isEnabled(), "playback-second-account", enabled = false) {}
    }
    SettingsPlatformNote(pinnedString("settings_playback_second_account"))
    Spacer(Modifier.height(10.dp))
    if (!MeloXSettingsRuntime.automaticLyricSelectionEnabled) {
        SettingsInfoCard(pinnedString("settings_auto_lyrics_required"))
    } else {
        SettingsGlassGroup {
            SettingsExternalToggleRow(pinnedString("settings_lyric_binding"), MeloXSettingsRuntime.lyricStrongBindingEnabled, "experimental_lyric_strong_binding") {
                MeloXSettingsPreferences.setBoolean("experimental_lyric_strong_binding", it)
                if (!it) melox.lyrics.LyricBindingStore.clear()
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton(pinnedString("settings_clear_lyric_bindings"), Modifier.testTag("lyric-bindings-clear")) { confirm = "lyrics" }
    }
    confirm?.let { target ->
        SettingsConfirmation(pinnedString(if (target == "lyrics") "settings_clear_lyric_bindings_title" else "settings_bilibili_clear_title"),
            pinnedString(if (target == "lyrics") "settings_clear_lyric_bindings_body" else "settings_bilibili_clear_body"), onDismiss = { confirm = null }) {
            if (target == "lyrics") melox.lyrics.LyricBindingStore.clear() else melox.provider.bilibili.BilibiliPlaybackAssociationStore.clear()
            confirm = null
        }
    }
}
