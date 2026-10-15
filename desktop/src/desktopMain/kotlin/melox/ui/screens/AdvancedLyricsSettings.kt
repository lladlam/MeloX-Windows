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
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.*
import melox.ui.legal.pinnedString
import kotlin.math.roundToInt

/** Pinned SettingsScreen.LyricsSettings groups, options and canonical persistence keys. */
@Composable
internal fun AdvancedLyricsSettings() {
    val r = MeloXSettingsRuntime
    Column(Modifier.testTag("advanced-lyrics-settings")) {
        LyricsGroup(topSpacing = false) {
            Box(Modifier.testTag("lyrics_style")) {
                MeloXSettingsDropdown(pinnedString("settings_lyrics_style"), r.lyricsStyle,
                    listOf(MeloXLyricsStyle.AppleMusic to "Apple Music", MeloXLyricsStyle.Eva to pinnedString("settings_lyrics_style_eva"),
                        MeloXLyricsStyle.TextPV to pinnedString("settings_lyrics_style_text_pv")),
                    onSelected = { MeloXSettingsPreferences.setString("lyrics_style", it.name) }, grouped = true)
            }
            LyricsStringChoice("settings_lyrics_rendering", "lyrics_rendering_quality", r.lyricRenderingQuality.name,
                MeloXLyricsRenderingQuality.entries.map { it.name }) {
                pinnedString("settings_lyrics_quality_${it.lowercase()}")
            }
            LyricsToggle("settings_lyrics_auto_select", "lyrics_auto_select", r.automaticLyricSelectionEnabled)
            LyricsToggle("settings_lyrics_word_by_word", "lyrics_word_by_word", r.lyricWordByWordEnabled)
            LyricsToggle("settings_lyrics_pseudo_timing", "lyrics_pseudo_timing", r.lyricPseudoTimingEnabled, "settings_lyrics_pseudo_timing_note")
            LyricsToggle("settings_lyrics_tap_seek", "lyrics_tap_seek", r.lyricTapSeekEnabled)
            LyricsToggle("settings_lyrics_long_press_share", "lyrics_long_press_share", r.lyricLongPressShareEnabled)
            LyricsToggle("settings_lyrics_interlude", "lyrics_interlude_countdown", r.lyricInterludeCountdownEnabled, "settings_lyrics_interlude_note")
            LyricsToggle("settings_lyrics_auto_follow", "lyrics_auto_follow", r.lyricAutoFollowEnabled)
            LyricsIntChoice("settings_lyrics_follow_delay", "lyrics_follow_delay_ms", r.lyricFollowDelayMs, listOf(1500, 3000, 5000, 8000)) {
                pinnedString("settings_seconds_float", it / 1000f)
            }
            LyricsToggle("settings_lyrics_reduce_motion", "lyrics_reduce_motion", r.lyricReduceMotion, "settings_lyrics_reduce_motion_note")
        }
        if (r.lyricsStyle == MeloXLyricsStyle.TextPV) {
            Spacer(Modifier.height(10.dp))
            AlternativeLyricsSettings(showStyleSelector = false)
        }
        LyricsGroup {
            LyricsToggle("settings_show_translation", "lyrics_translation", r.showLyricTranslation)
            LyricsFloatChoice("settings_translation_size", "lyrics_translation_font_scale", r.lyricTranslationFontScale, annotationSizes)
            LyricsFloatChoice("settings_translation_brightness", "lyrics_translation_opacity", r.lyricTranslationOpacity, annotationOpacities)
            LyricsAnnotationChoice("settings_translation_scope", "lyrics_translation_display_mode", r.lyricTranslationDisplayMode)
            LyricsToggle("settings_show_romanization", "lyrics_romanization", r.showLyricRomanization)
            LyricsAnnotationChoice("settings_romanization_scope", "lyrics_romanization_display_mode", r.lyricRomanizationDisplayMode)
            LyricsFloatChoice("settings_romanization_size", "lyrics_romanization_font_scale", r.lyricRomanizationFontScale, annotationSizes)
            LyricsFloatChoice("settings_romanization_brightness", "lyrics_romanization_opacity", r.lyricRomanizationOpacity, annotationOpacities)
        }
        LyricsGroup {
            LyricsIntChoice("settings_lyrics_advance", "lyrics_advance_ms", r.lyricAdvanceMs, listOf(-1000, -500, -200, 0, 200, 500, 1000, 2000, 5000)) {
                when {
                    it == 0 -> pinnedString("player_lyric_sync")
                    it > 0 -> pinnedString("settings_lyrics_ahead_ms", it)
                    else -> pinnedString("settings_lyrics_behind_ms", -it)
                }
            }
            LyricsToggle("settings_lyrics_advance_words", "lyrics_advance_word_by_word", r.lyricAdvanceAppliesToWordByWord)
            LyricsIntChoice("settings_lyrics_refresh_rate", "lyrics_refresh_rate", r.lyricRefreshRate, listOf(30, 60, 90, 120)) { pinnedString("settings_fps", it) }
            LyricsFloatChoice("settings_lyrics_font_size", "lyrics_font_scale", r.lyricFontScale, listOf(.85f, 1f, 1.12f, 1.25f))
            LyricsStringChoice("settings_lyrics_font_weight", "lyrics_font_weight", r.lyricFontWeight.name, MeloXLyricsFontWeight.entries.map { it.name }) {
                pinnedString("settings_weight_${it.lowercase()}")
            }
            LyricsStringChoice("settings_lyrics_lift", "lyrics_lift_mode", r.lyricLiftMode.name, MeloXLyricsGroupingMode.entries.map { it.name }) {
                pinnedString(if (it == MeloXLyricsGroupingMode.Word.name) "settings_lift_word" else "settings_lift_character")
            }
        }
        LyricsGroup {
            LyricsStringChoice("settings_long_tone_detection", "lyrics_long_tone_detection", r.lyricLongToneDetectionMode.name, MeloXLyricsGroupingMode.entries.map { it.name }) {
                pinnedString(if (it == MeloXLyricsGroupingMode.Word.name) "settings_detect_word" else "settings_detect_character")
            }
            LyricsToggle("settings_lyrics_glow", "lyrics_glow_enabled", r.lyricGlowEnabled)
            LyricsToggle("settings_glow_long_tones_only", "lyrics_glow_long_tones_only", r.lyricGlowLongTonesOnly)
            LyricsFloatChoice("settings_glow_strength", "lyrics_glow_strength", r.lyricGlowStrength, listOf(0f, .6f, 1f, 1.4f), ::offOrPercent)
            LyricsFloatChoice("settings_long_tone_stretch", "lyrics_long_tone_strength", r.lyricLongToneStrength, listOf(0f, .6f, 1f, 1.4f), ::offOrPercent)
            LyricsIntChoice("settings_long_tone_threshold", "lyrics_long_tone_threshold_ms", r.lyricLongToneThresholdMs, listOf(300, 500, 700, 950, 1200, 1500)) {
                pinnedString("settings_seconds_float", it / 1000f)
            }
            LyricsFloatChoice("settings_line_spacing", "lyrics_spacing_scale", r.lyricSpacingScale, listOf(.8f, 1f, 1.2f, 1.4f))
            LyricsFloatChoice("settings_depth_blur", "lyrics_blur_strength", r.lyricBlurStrength, listOf(0f, .5f, .8f, 1f), ::offOrPercent)
        }
        LyricsGroup {
            LyricsFloatChoice("settings_focus_scale", "lyrics_focus_scale", r.lyricFocusScale, listOf(1f, 1.02f, 1.04f, 1.08f))
            LyricsFloatChoice("settings_inactive_opacity", "lyrics_inactive_opacity", r.lyricInactiveOpacity, listOf(.3f, .42f, .5f, .6f))
            LyricsIntChoice("settings_controls_auto_hide", "lyrics_interface_auto_hide_ms", r.lyricInterfaceAutoHideDelayMs, (3..15).map { it * 1000 }) { pinnedString("settings_seconds", it / 1000) }
            LyricsIntChoice("settings_scroll_hide_threshold", "lyrics_scroll_hide_threshold_dp", r.lyricScrollHideThresholdDp, listOf(40, 80, 120, 160, 200, 240)) { pinnedString("settings_unit_dp", it) }
        }
        LyricsGroup {
            LyricsToggle("settings_cascade_bounce_enabled", "lyrics_cascade_bounce_enabled", r.lyricCascadeBounceEnabled)
            LyricsToggle("settings_scale_bounce_enabled", "lyrics_scale_bounce_enabled", r.lyricScaleBounceEnabled)
            LyricsIntChoice("settings_scale_bounce_duration", "lyrics_scale_bounce_duration_ms", r.lyricScaleBounceDurationMs, listOf(150, 250, 350, 450, 580, 700, 800)) { pinnedString("settings_duration_ms", it) }
            LyricsIntChoice("settings_focus_bounce_duration", "lyrics_focus_color_lead_ms", r.lyricFocusColorLeadMs, listOf(-300, -200, -100, -50, 0, 50, 100, 200, 300)) {
                if (it == 0) pinnedString("player_lyric_sync") else pinnedString("settings_signed_ms", it)
            }
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_max_cascade_bounce", "lyrics_cascade_bounce", r.lyricCascadeBounce, 0f.. .8f, 79)
            LyricsFloatSlider("settings_bounce_gradient", "lyrics_cascade_bounce_gradient", r.lyricCascadeBounceGradient, 0f..1f, 99)
            LyricsFloatSlider("settings_scale_bounce_amount", "lyrics_scale_bounce", r.lyricScaleBounce, 0f.. .5f, 49)
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_highlight_width", "lyrics_highlight_gradient_width", r.lyricHighlightGradientWidth, .4f..3f, 25) { pinnedString("settings_char_widths", it) }
            LyricsFloatSlider("settings_gradient_reduction", "lyrics_highlight_gradient_reduction", r.lyricHighlightGradientReduction, 0f..1f, 19)
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_focus_position", "lyrics_focus_position", r.lyricFocusPosition, .05f.. .8f, 74) { pinnedString("settings_focus_from_top", (it * 100).toInt()) }
            LyricsFloatSlider("settings_distance_blur", "lyrics_distance_blur_scale", r.lyricDistanceBlurScale, 0f..1.5f, 29)
            LyricsFloatSlider("settings_hidden_blur", "lyrics_hidden_blur_scale", r.lyricHiddenInterfaceBlurScale, 0f..1.5f, 29)
            LyricsFloatSlider("settings_dim_amount", "lyrics_dim_amount", r.lyricDimAmount, 0f..1f, 49)
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_cascade_delay", "lyrics_cascade_delay_ms", r.lyricCascadeDelayMs, 0f..100f, 99) { pinnedString("settings_milliseconds", it.toInt()) }
            LyricsFloatSlider("settings_cascade_delay_increase", "lyrics_cascade_delay_increase_ms", r.lyricCascadeDelayIncreaseMs, 0f..100f, 99) { pinnedString("settings_ms_per_line", it.toInt()) }
            LyricsFloatSlider("settings_following_delay", "lyrics_cascade_following_delay_ms", r.lyricCascadeFollowingDelayMs, 0f..200f, 199) { pinnedString("settings_milliseconds", it.toInt()) }
            LyricsFloatSlider("settings_catch_up_ratio", "lyrics_cascade_catch_up_ratio", r.lyricCascadeCatchUpRatio, .5f..1f, 49)
            LyricsFloatSlider("settings_chase_gradient", "lyrics_cascade_chase_gradient", r.lyricCascadeChaseSpeedGradient, 0f..1f, 99)
            LyricsFloatSlider("settings_cascade_duration", "lyrics_cascade_duration_ms", r.lyricCascadeDurationMs, 200f..1200f, 99) { pinnedString("settings_seconds_2f", it / 1000f) }
        }
        LyricsSliderGroup {
            LyricsFloatSlider("settings_snap_threshold", "lyrics_snap_threshold_ms", r.lyricSnapThresholdMs, 50f..500f, 89) { pinnedString("settings_milliseconds", it.toInt()) }
        }
    }
}

private val annotationSizes = listOf(.5f, .55f, .6f, .65f, .7f, .75f, .8f)
private val annotationOpacities = listOf(.4f, .5f, .6f, .7f, .8f, .9f)
private fun percent(value: Float) = pinnedString("settings_unit_percent", (value * 100).toInt())
private fun offOrPercent(value: Float) = if (value == 0f) pinnedString("settings_skyline_off") else percent(value)

@Composable
internal fun LyricsGroup(topSpacing: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (topSpacing) Spacer(Modifier.height(10.dp))
    CompositionLocalProvider(LocalSettingsGroupRowIndex provides mutableIntStateOf(0)) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
internal fun LyricsSliderGroup(content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(10.dp))
    Column(content = content)
}

@Composable
internal fun LyricsToggle(title: String, key: String, checked: Boolean, note: String? = null) {
    // Pinned SettingsToggleRow accepts `note` but intentionally renders only the title.
    MeloXIosListRow(title = pinnedString(title), showChevron = false,
        showTopSeparator = LocalSettingsGroupRowIndex.current.intValue++ > 0,
        trailingContent = { MeloXGlassToggle(checked, onCheckedChange = { MeloXSettingsPreferences.setBoolean(key, it) }, modifier = Modifier.testTag(key)) })
}

@Composable
internal fun LyricsIntChoice(title: String, key: String, value: Int, choices: List<Int>, label: (Int) -> String) {
    Box(Modifier.testTag(key)) {
        MeloXSettingsDropdown(pinnedString(title), value, choices.map { it to label(it) },
            onSelected = { MeloXSettingsPreferences.setInt(key, it) }, grouped = true)
    }
}

@Composable
private fun LyricsFloatChoice(title: String, key: String, value: Float, choices: List<Float>, label: (Float) -> String = ::percent) {
    Box(Modifier.testTag(key)) {
        MeloXSettingsDropdown(pinnedString(title), value, choices.map { it to label(it) },
            onSelected = { MeloXSettingsPreferences.setFloat(key, it) }, grouped = true)
    }
}

@Composable
private fun LyricsStringChoice(title: String, key: String, value: String, choices: List<String>, label: (String) -> String) {
    Box(Modifier.testTag(key)) {
        MeloXSettingsDropdown(pinnedString(title), value, choices.map { it to label(it) },
            onSelected = { MeloXSettingsPreferences.setString(key, it) }, grouped = true)
    }
}

@Composable
private fun LyricsAnnotationChoice(title: String, key: String, value: MeloXLyricAnnotationDisplayMode) {
    LyricsStringChoice(title, key, value.name, MeloXLyricAnnotationDisplayMode.entries.map { it.name }) {
        pinnedString(if (it == MeloXLyricAnnotationDisplayMode.FocusedLine.name) "settings_annotation_focused" else "settings_annotation_all")
    }
}

@Composable
internal fun LyricsFloatSlider(title: String, key: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, label: (Float) -> String = ::percent) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val step = (range.endInclusive - range.start) / (steps + 1)
    fun snap(v: Float) = (range.start + ((v - range.start) / step).roundToInt() * step).coerceIn(range)
    val shown = dragging ?: value
    val text = pinnedString(title)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        Text(label(shown), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
    }
    MeloXLiquidSlider(value, onValueChange = { MeloXSettingsPreferences.setFloat(key, snap(it)) },
        onTransientValueChange = { dragging = snap(it) }, onValueChangeFinished = { dragging = null },
        valueRange = range, stepSize = step, visibilityThreshold = .001f,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(key), contentDescription = "$text, ${label(shown)}")
}
