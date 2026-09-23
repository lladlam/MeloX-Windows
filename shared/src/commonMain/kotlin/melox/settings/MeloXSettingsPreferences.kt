package melox.settings

import melox.playback.MeloXPlaybackModePreferences
import melox.platform.getPreferences

object MeloXSettingsPreferences {
    private const val NAME = "melox_app_settings"

    private fun prefs() = getPreferences(NAME)

    fun initialize() = MeloXSettingsRuntime.initialize()

    fun initializeCritical() = MeloXSettingsRuntime.initializeCritical()

    fun boolean(key: String, default: Boolean = false): Boolean = prefs().getBoolean(key, default)

    fun string(key: String, default: String = ""): String = prefs().getString(key, default) ?: default

    fun int(key: String, default: Int = 0): Int = prefs().getInt(key, default)

    fun float(key: String, default: Float = 0f): Float = prefs().getFloat(key, default)

    fun long(key: String, default: Long = 0L): Long = prefs().getLong(key, default)

    fun setLong(key: String, value: Long) {
        prefs().putLong(key, value)
    }

    fun setBoolean(key: String, value: Boolean) {
        prefs().putBoolean(key, value)
        if (key == "lyrics_auto_select" && !value) {
            prefs().putBoolean("experimental_lyric_strong_binding", false)
            MeloXSettingsRuntime.lyricStrongBindingEnabled = false
        }
        when (key) {
            "developer_performance_overlay" -> MeloXSettingsRuntime.performanceOverlayEnabled = value
            "feature_podcasts" -> MeloXSettingsRuntime.podcastsEnabled = value
            "feature_history" -> MeloXSettingsRuntime.listeningHistoryEnabled = value
            "feature_downloads" -> MeloXSettingsRuntime.downloadsEnabled = value
            "feature_cloud_music" -> MeloXSettingsRuntime.cloudMusicEnabled = value
            "placement_podcasts_home" -> MeloXSettingsRuntime.podcastsHomePlacement = value
            "placement_podcasts_tab" -> MeloXSettingsRuntime.podcastsTabPlacement = value
            "placement_podcasts_library" -> MeloXSettingsRuntime.podcastsLibraryPlacement = value
            "placement_downloads_home" -> MeloXSettingsRuntime.downloadsHomePlacement = value
            "placement_downloads_tab" -> MeloXSettingsRuntime.downloadsTabPlacement = value
            "placement_downloads_library" -> MeloXSettingsRuntime.downloadsLibraryPlacement = value
            "placement_cloud_home" -> MeloXSettingsRuntime.cloudHomePlacement = value
            "placement_cloud_tab" -> MeloXSettingsRuntime.cloudTabPlacement = value
            "placement_cloud_library" -> MeloXSettingsRuntime.cloudLibraryPlacement = value
            "player_flowing_backdrop" -> MeloXSettingsRuntime.flowingBackdropEnabled = value
            "player_artwork_motion" -> MeloXSettingsRuntime.artworkMotionEnabled = value
            "reduce_motion" -> MeloXSettingsRuntime.reduceMotion = value
            "player_background_isolation" -> MeloXSettingsRuntime.playerBackgroundIsolationEnabled = value
            "player_frosted_glass" -> MeloXSettingsRuntime.frostedGlassEnabled = value
            "player_keep_screen_on" -> MeloXSettingsRuntime.keepScreenOn = value
            "lyrics_translation" -> MeloXSettingsRuntime.showLyricTranslation = value
            "lyrics_auto_select" -> MeloXSettingsRuntime.automaticLyricSelectionEnabled = value
            "experimental_lyric_strong_binding" -> MeloXSettingsRuntime.lyricStrongBindingEnabled = value
            "lyrics_romanization" -> MeloXSettingsRuntime.showLyricRomanization = value
            "lyrics_word_by_word" -> MeloXSettingsRuntime.lyricWordByWordEnabled = value
            "lyrics_pseudo_timing" -> MeloXSettingsRuntime.lyricPseudoTimingEnabled = value
            "lyrics_tap_seek" -> MeloXSettingsRuntime.lyricTapSeekEnabled = value
            "lyrics_long_press_share" -> MeloXSettingsRuntime.lyricLongPressShareEnabled = value
            "lyrics_interlude_countdown" -> MeloXSettingsRuntime.lyricInterludeCountdownEnabled = value
            "lyrics_auto_follow" -> MeloXSettingsRuntime.lyricAutoFollowEnabled = value
            "lyrics_reduce_motion" -> MeloXSettingsRuntime.lyricReduceMotion = value
            "lyrics_glow_long_tones_only" -> MeloXSettingsRuntime.lyricGlowLongTonesOnly = value
            "lyrics_glow_enabled" -> MeloXSettingsRuntime.lyricGlowEnabled = value
            "lyrics_word_bounce_enabled" -> MeloXSettingsRuntime.lyricWordBounceEnabled = value
            "lyrics_cascade_bounce_enabled" -> MeloXSettingsRuntime.lyricCascadeBounceEnabled = value
            "lyrics_scale_bounce_enabled" -> MeloXSettingsRuntime.lyricScaleBounceEnabled = value
            "lyrics_advance_word_by_word" -> MeloXSettingsRuntime.lyricAdvanceAppliesToWordByWord = value
            "lyrics_skyline_enabled" -> MeloXSettingsRuntime.skylineEnabled = value
            "lyrics_skyline_song_info" -> MeloXSettingsRuntime.skylineShowSongInfo = value
            "lyrics_skyline_keep_awake" -> MeloXSettingsRuntime.skylineKeepsScreenAwake = value
            "system_lyrics_enabled" -> MeloXSettingsRuntime.systemLyricsEnabled = value
            "lyrics_notifications_enabled" -> MeloXSettingsRuntime.lyricNotificationsEnabled = value
            "lyrics_notification_next_line" -> MeloXSettingsRuntime.lyricNotificationShowNextLine = value
            "lyrics_notification_progress" -> MeloXSettingsRuntime.lyricNotificationShowProgress = value
            "lyrics_notification_artwork" -> MeloXSettingsRuntime.lyricNotificationShowArtwork = value
            "lyrics_notification_background_only" -> MeloXSettingsRuntime.lyricNotificationBackgroundOnly = value
            "lyrics_notification_dismiss_paused" -> MeloXSettingsRuntime.lyricNotificationDismissWhenPaused = value
            "floating_lyrics_enabled" -> MeloXSettingsRuntime.floatingLyricsEnabled = value
            "floating_lyrics_high_contrast" -> MeloXSettingsRuntime.floatingHighContrast = value
            "tab_home" -> MeloXSettingsRuntime.homeTabEnabled = value
            "home_quick_actions" -> MeloXSettingsRuntime.homeQuickActionsEnabled = value
            "home_playlists" -> MeloXSettingsRuntime.homePlaylistsEnabled = value
            "home_new_songs" -> MeloXSettingsRuntime.homeNewSongsEnabled = value
            "tab_explore" -> MeloXSettingsRuntime.exploreTabEnabled = value
            "tab_library" -> MeloXSettingsRuntime.libraryTabEnabled = value
            "general_remember_tab" -> MeloXSettingsRuntime.rememberLastTab = value
            "general_disable_auto_tabbar_shrink" -> MeloXSettingsRuntime.disableAutomaticTabBarShrink = value
            "library_remember_page" -> MeloXSettingsRuntime.rememberLibraryPage = value
            "download_lyrics" -> MeloXSettingsRuntime.downloadLyricsEnabled = value
            "content_playlist_play_count" -> MeloXSettingsRuntime.showPlaylistPlayCount = value
            "content_high_quality_playlist" -> MeloXSettingsRuntime.showHighQualityPlaylists = value
            "general_clipboard_links" -> MeloXSettingsRuntime.clipboardLinksEnabled = value
            "general_haptic_feedback" -> MeloXSettingsRuntime.hapticFeedbackEnabled = value
            "playback_previous_restarts" -> MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = value
            "playback_heart_mode_on_launch" -> MeloXSettingsRuntime.startsHeartModeOnLaunch = value
            "immersive_playback" -> MeloXSettingsRuntime.immersivePlaybackEnabled = value
            "player_show_quality_tip" -> MeloXSettingsRuntime.showPlayerQualityTip = value
            "system_font" -> MeloXSettingsRuntime.systemFontEnabled = value
        }
    }

    fun setInt(key: String, value: Int) {
        prefs().putInt(key, value)
        when (key) {
            "lyrics_advance_ms" -> MeloXSettingsRuntime.lyricAdvanceMs = value.coerceIn(-5_000, 5_000)
            "lyrics_follow_delay_ms" -> MeloXSettingsRuntime.lyricFollowDelayMs = value.coerceIn(1_000, 8_000)
            "lyrics_refresh_rate" -> MeloXSettingsRuntime.lyricRefreshRate =
                value.takeIf { it in setOf(30, 60, 90, 120) } ?: 60
            "lyrics_background_frame_rate" -> MeloXSettingsRuntime.lyricBackgroundFrameRate =
                value.takeIf { it in setOf(15, 24, 30, 45, 60) } ?: 24
            "player_transition_duration_ms" -> MeloXSettingsRuntime.playerTransitionDurationMs =
                value.coerceIn(200, 1_200)
            "lyrics_long_tone_threshold_ms" -> MeloXSettingsRuntime.lyricLongToneThresholdMs = value.coerceIn(300, 1_500)
            "lyrics_interface_auto_hide_ms" -> MeloXSettingsRuntime.lyricInterfaceAutoHideDelayMs = value.coerceIn(3_000, 15_000)
            "lyrics_scroll_hide_threshold_dp" -> MeloXSettingsRuntime.lyricScrollHideThresholdDp = value.coerceIn(40, 240)
            "lyrics_scale_bounce_duration_ms" -> MeloXSettingsRuntime.lyricScaleBounceDurationMs = value.coerceIn(150, 800)
            "lyrics_focus_color_lead_ms" -> MeloXSettingsRuntime.lyricFocusColorLeadMs = value.coerceIn(-300, 300)
            "lyrics_skyline_ambient_lines" -> MeloXSettingsRuntime.skylineAmbientLines = value.coerceIn(0, 4)
            "lyrics_skyline_ambient_max_characters" -> MeloXSettingsRuntime.skylineAmbientMaximumCharacters = value.coerceIn(1, 4)
            "lyrics_skyline_ambient_max_visible" -> MeloXSettingsRuntime.skylineAmbientMaximumVisibleTexts = value.coerceIn(4, 24)
            "floating_lyrics_font_size" -> MeloXSettingsRuntime.floatingFontSizeSp = value.coerceIn(14, 28)
        }
    }

    fun setFloat(key: String, value: Float) {
        prefs().putFloat(key, value)
        when (key) {
            "lyrics_font_scale" -> MeloXSettingsRuntime.lyricFontScale = value.coerceIn(.8f, 1.25f)
            "lyrics_focus_position" -> MeloXSettingsRuntime.lyricFocusPosition = value.coerceIn(.05f, .8f)
            "lyrics_spacing_scale" -> MeloXSettingsRuntime.lyricSpacingScale = value.coerceIn(.7f, 1.5f)
            "lyrics_blur_strength" -> MeloXSettingsRuntime.lyricBlurStrength = value.coerceIn(0f, 1.5f)
            "lyrics_distance_blur_scale" -> MeloXSettingsRuntime.lyricDistanceBlurScale = value.coerceIn(0f, 1.5f)
            "lyrics_hidden_blur_scale" -> MeloXSettingsRuntime.lyricHiddenInterfaceBlurScale = value.coerceIn(0f, 1.5f)
            "lyrics_dim_amount" -> MeloXSettingsRuntime.lyricDimAmount = value.coerceIn(0f, 1f)
            "lyrics_focus_scale" -> MeloXSettingsRuntime.lyricFocusScale = value.coerceIn(1f, 1.08f)
            "lyrics_inactive_opacity" -> MeloXSettingsRuntime.lyricInactiveOpacity = value.coerceIn(.15f, .65f)
            "lyrics_glow_strength" -> MeloXSettingsRuntime.lyricGlowStrength = value.coerceIn(0f, 1.5f)
            "lyrics_long_tone_strength" -> MeloXSettingsRuntime.lyricLongToneStrength = value.coerceIn(0f, 1.5f)
            "lyrics_highlight_gradient_width" -> MeloXSettingsRuntime.lyricHighlightGradientWidth = value.coerceIn(.4f, 3f)
            "lyrics_highlight_gradient_reduction" -> MeloXSettingsRuntime.lyricHighlightGradientReduction = value.coerceIn(0f, 1f)
            "lyrics_romanization_font_scale" -> MeloXSettingsRuntime.lyricRomanizationFontScale = value.coerceIn(.5f, .8f)
            "lyrics_romanization_opacity" -> MeloXSettingsRuntime.lyricRomanizationOpacity = value.coerceIn(.4f, .9f)
            "lyrics_translation_font_scale" -> MeloXSettingsRuntime.lyricTranslationFontScale = value.coerceIn(.5f, .8f)
            "lyrics_translation_opacity" -> MeloXSettingsRuntime.lyricTranslationOpacity = value.coerceIn(.4f, .9f)
            "lyrics_cascade_delay_ms" -> MeloXSettingsRuntime.lyricCascadeDelayMs = value.coerceIn(0f, 100f)
            "lyrics_cascade_delay_increase_ms" -> MeloXSettingsRuntime.lyricCascadeDelayIncreaseMs = value.coerceIn(0f, 100f)
            "lyrics_cascade_following_delay_ms" -> MeloXSettingsRuntime.lyricCascadeFollowingDelayMs = value.coerceIn(0f, 200f)
            "lyrics_cascade_catch_up_ratio" -> MeloXSettingsRuntime.lyricCascadeCatchUpRatio = value.coerceIn(.5f, 1f)
            "lyrics_cascade_chase_gradient" -> MeloXSettingsRuntime.lyricCascadeChaseSpeedGradient = value.coerceIn(0f, 1f)
            "lyrics_cascade_duration_ms" -> MeloXSettingsRuntime.lyricCascadeDurationMs = value.coerceIn(200f, 1_200f)
            "lyrics_snap_threshold_ms" -> MeloXSettingsRuntime.lyricSnapThresholdMs = value.coerceIn(50f, 500f)
            "lyrics_cascade_bounce" -> MeloXSettingsRuntime.lyricCascadeBounce = value.coerceIn(0f, .8f)
            "lyrics_cascade_bounce_gradient" -> MeloXSettingsRuntime.lyricCascadeBounceGradient = value.coerceIn(0f, 1f)
            "lyrics_scale_bounce" -> MeloXSettingsRuntime.lyricScaleBounce = value.coerceIn(0f, .5f)
            "lyrics_skyline_current_font_size" -> MeloXSettingsRuntime.skylineCurrentFontSize = value.coerceIn(36f, 84f)
            "lyrics_skyline_current_max_scale" -> MeloXSettingsRuntime.skylineCurrentMaximumScale = value.coerceIn(1f, 1.2f)
            "lyrics_skyline_next_font_size" -> MeloXSettingsRuntime.skylineNextFontSize = value.coerceIn(14f, 44f)
            "lyrics_skyline_current_spacing" -> MeloXSettingsRuntime.skylineCurrentSpacing = value.coerceIn(4f, 36f)
            "lyrics_skyline_current_width" -> MeloXSettingsRuntime.skylineCurrentWidth = value.coerceIn(.4f, .82f)
            "lyrics_skyline_next_opacity" -> MeloXSettingsRuntime.skylineNextOpacity = value.coerceIn(.2f, .8f)
            "lyrics_skyline_ambient_font_size" -> MeloXSettingsRuntime.skylineAmbientFontSize = value.coerceIn(24f, 72f)
            "lyrics_skyline_ambient_opacity" -> MeloXSettingsRuntime.skylineAmbientOpacity = value.coerceIn(.4f, 1.8f)
            "lyrics_skyline_ambient_blur" -> MeloXSettingsRuntime.skylineAmbientBlur = value.coerceIn(0f, 2f)
            "lyrics_skyline_ambient_max_tilt" -> MeloXSettingsRuntime.skylineAmbientMaximumTilt = value.coerceIn(0f, 20f)
            "lyrics_skyline_ambient_drift" -> MeloXSettingsRuntime.skylineAmbientDrift = value.coerceIn(0f, 2f)
            "lyrics_text_pv_motion_intensity" -> MeloXSettingsRuntime.textPVMotionIntensity = value.coerceIn(0f, 2f)
            "lyrics_text_pv_animation_speed" -> MeloXSettingsRuntime.textPVAnimationSpeed = value.coerceIn(0f, 4f)
        }
    }

    fun setString(key: String, value: String) {
        prefs().putString(key, value)
        when (key) {
            "theme_mode" -> MeloXSettingsRuntime.themeMode = runCatching {
                MeloXThemeMode.valueOf(value)
            }.getOrDefault(MeloXThemeMode.System)
            "music_area" -> MeloXSettingsRuntime.musicArea = value
            "tab_order" -> MeloXSettingsRuntime.tabOrder = value.split(',')
                .filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
                .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
            "home_section_order" -> MeloXSettingsRuntime.homeSectionOrder = value.split(',')
                .filter { it in setOf("QuickActions", "Playlists", "NewSongs") }.distinct()
                .let { order -> (order + listOf("QuickActions", "Playlists", "NewSongs")).distinct() }
            "general_default_tab" -> MeloXSettingsRuntime.defaultTab = value
            "general_swipe_full_action" -> MeloXSettingsRuntime.swipeFullAction = runCatching {
                MeloXSwipeFullAction.valueOf(value)
            }.getOrDefault(MeloXSwipeFullAction.PlayNext)
            "library_default_page" -> MeloXSettingsRuntime.defaultLibraryPage = value
            "lyrics_romanization_display_mode" -> MeloXSettingsRuntime.lyricRomanizationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.FocusedLine)
            "lyrics_translation_display_mode" -> MeloXSettingsRuntime.lyricTranslationDisplayMode = runCatching {
                MeloXLyricAnnotationDisplayMode.valueOf(value)
            }.getOrDefault(MeloXLyricAnnotationDisplayMode.AllLines)
            "lyrics_rendering_quality" -> MeloXSettingsRuntime.lyricRenderingQuality = runCatching {
                MeloXLyricsRenderingQuality.valueOf(value)
            }.getOrDefault(MeloXLyricsRenderingQuality.High)
            "lyrics_style" -> MeloXSettingsRuntime.lyricsStyle = runCatching {
                MeloXLyricsStyle.valueOf(value)
            }.getOrDefault(MeloXLyricsStyle.AppleMusic)
            "player_background_mode" -> {
                MeloXSettingsRuntime.playerBackgroundMode = runCatching {
                    MeloXPlayerBackgroundMode.valueOf(value)
                }.getOrDefault(MeloXPlayerBackgroundMode.FlowingLight)
                MeloXSettingsRuntime.flowingBackdropEnabled =
                    MeloXSettingsRuntime.playerBackgroundMode != MeloXPlayerBackgroundMode.BlurredArtwork
            }
            "player_shell" -> MeloXSettingsRuntime.playerShell = runCatching {
                MeloXPlayerShell.valueOf(value)
            }.getOrDefault(MeloXPlayerShell.AppleMusic)
            "lyrics_text_pv_style" -> {
                MeloXSettingsRuntime.textPVStyle = runCatching {
                    MeloXTextPVStyle.valueOf(value)
                }.getOrDefault(MeloXTextPVStyle.BlueBold)
                setFloat("lyrics_text_pv_animation_speed", MeloXSettingsRuntime.textPVStyle.referenceAnimationSpeed)
            }
            "lyrics_font_weight" -> MeloXSettingsRuntime.lyricFontWeight = runCatching {
                MeloXLyricsFontWeight.valueOf(value)
            }.getOrDefault(MeloXLyricsFontWeight.Heavy)
            "lyrics_lift_mode" -> MeloXSettingsRuntime.lyricLiftMode = runCatching {
                MeloXLyricsGroupingMode.valueOf(value)
            }.getOrDefault(MeloXLyricsGroupingMode.Character)
            "lyrics_long_tone_detection" -> MeloXSettingsRuntime.lyricLongToneDetectionMode = runCatching {
                MeloXLyricsGroupingMode.valueOf(value)
            }.getOrDefault(MeloXLyricsGroupingMode.Character)
            "system_lyrics_title_mode" -> MeloXSettingsRuntime.systemLyricTitleMode = runCatching {
                MeloXSystemLyricTitleMode.valueOf(value)
            }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
            "lyrics_notification_title_template" -> MeloXSettingsRuntime.lyricNotificationTitleTemplate = value
            "lyrics_notification_subtitle_template" -> MeloXSettingsRuntime.lyricNotificationSubtitleTemplate = value
            "lyrics_notification_fallback" -> MeloXSettingsRuntime.lyricNotificationFallback = value
            "floating_lyrics_secondary_mode" -> MeloXSettingsRuntime.floatingSecondaryMode = runCatching {
                MeloXSecondaryLyricMode.valueOf(value)
            }.getOrDefault(MeloXSecondaryLyricMode.Auto)
            "playback_volume_mode" -> MeloXSettingsRuntime.volumeControlMode = runCatching {
                MeloXVolumeControlMode.valueOf(value)
            }.getOrDefault(MeloXVolumeControlMode.System)
            "player_screen_awake_mode" -> MeloXSettingsRuntime.screenAwakeMode = runCatching {
                MeloXScreenAwakeMode.valueOf(value)
            }.getOrDefault(MeloXScreenAwakeMode.Disabled)
        }
    }


    fun reset() {
        prefs().clear()
        MeloXSettingsRuntime.initialize(force = true)
    }

    fun resetRecommendedPlayerSettings() {
        listOf(
            "player_background_mode", "player_shell", "player_flowing_backdrop",
            "player_artwork_motion", "player_background_isolation", "player_frosted_glass",
            "player_transition_duration_ms", "lyrics_style", "lyrics_rendering_quality",
            "lyrics_text_pv_style", "lyrics_text_pv_motion_intensity", "lyrics_text_pv_animation_speed",
        ).forEach { prefs().remove(it) }
        MeloXPlaybackModePreferences.reset()
        MeloXSettingsRuntime.initialize(force = true)
    }
}
