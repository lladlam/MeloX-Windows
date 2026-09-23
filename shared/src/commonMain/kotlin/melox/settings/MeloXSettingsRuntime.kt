package melox.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import melox.playback.MeloXPlaybackModePreferences
import melox.platform.getPreferences

enum class MeloXThemeMode { System, Light, Dark }
enum class MeloXSwipeFullAction { PlayNext, AddToQueue }
enum class MeloXLyricAnnotationDisplayMode { FocusedLine, AllLines }
enum class MeloXLyricsStyle { AppleMusic, Eva, TextPV }
enum class MeloXLyricsRenderingQuality { Low, Balanced, High }
enum class MeloXPlayerBackgroundMode { FlowingLight, AppleLyrics, BlurredArtwork, MeiMesh }
enum class MeloXPlayerShell { AppleMusic, Classic }
enum class MeloXTextPVStyle {
    BlueBold, KineticSplit, BluePlane, CyberGrunge, Geometric, RainCity,
    CyberpunkHUD, EmotionCinema, HystericNight, SpiderWeb, StaggeredText,
    CalmVillain, GirlyClouds, SweetPink, FlyMeToTheMoon, KawaiiPixel,
    CrimeScene, Haruhikage,
    /** Compatibility values kept for installs that used the early Android preview. */
    Dynamic, Minimal, Cyber,
}

val MeloXTextPVStyle.referenceAnimationSpeed: Float
    get() = when (this) {
        MeloXTextPVStyle.StaggeredText -> 3.4f
        MeloXTextPVStyle.GirlyClouds -> 1.5f
        MeloXTextPVStyle.SweetPink, MeloXTextPVStyle.KawaiiPixel -> 1f
        MeloXTextPVStyle.FlyMeToTheMoon -> 3.7f
        MeloXTextPVStyle.CrimeScene -> 2.5f
        MeloXTextPVStyle.Haruhikage -> .8f
        else -> 2f
    }
enum class MeloXVolumeControlMode { System, Player }
enum class MeloXSecondaryLyricMode { Auto, Translation, Romanization, NextLine, Hidden }
enum class MeloXSystemLyricTitleMode { LyricFirst, SongFirst }
enum class MeloXScreenAwakeMode { Disabled, Player, Lyrics, HiddenLyricsInterface }
enum class MeloXLyricsGroupingMode { Word, Character }
enum class MeloXLyricsFontWeight(val composeWeight: FontWeight) {
    Light(FontWeight.Light), Regular(FontWeight.Normal), Medium(FontWeight.Medium),
    SemiBold(FontWeight.SemiBold), Bold(FontWeight.Bold), Heavy(FontWeight.Black),
}

/** Process-visible settings used by UI paths that need immediate recomposition. */
object MeloXSettingsRuntime {
    var performanceOverlayEnabled by mutableStateOf(false)
        internal set
    var themeMode by mutableStateOf(MeloXThemeMode.System)
        internal set
    var podcastsEnabled by mutableStateOf(true)
        internal set
    var listeningHistoryEnabled by mutableStateOf(true)
        internal set
    var downloadsEnabled by mutableStateOf(true)
        internal set
    var cloudMusicEnabled by mutableStateOf(true)
        internal set
    var podcastsHomePlacement by mutableStateOf(true)
        internal set
    var podcastsTabPlacement by mutableStateOf(false)
        internal set
    var podcastsLibraryPlacement by mutableStateOf(true)
        internal set
    var downloadsHomePlacement by mutableStateOf(false)
        internal set
    var downloadsTabPlacement by mutableStateOf(false)
        internal set
    var downloadsLibraryPlacement by mutableStateOf(true)
        internal set
    var cloudHomePlacement by mutableStateOf(false)
        internal set
    var cloudTabPlacement by mutableStateOf(false)
        internal set
    var cloudLibraryPlacement by mutableStateOf(true)
        internal set
    var flowingBackdropEnabled by mutableStateOf(true)
        internal set
    var playerBackgroundMode by mutableStateOf(MeloXPlayerBackgroundMode.FlowingLight)
        internal set
    var playerShell by mutableStateOf(MeloXPlayerShell.AppleMusic)
        internal set
    var artworkMotionEnabled by mutableStateOf(true)
        internal set
    var reduceMotion by mutableStateOf(false)
        internal set
    var playerTransitionDurationMs by mutableStateOf(360)
        internal set
    var playerBackgroundIsolationEnabled by mutableStateOf(true)
        internal set
    var frostedGlassEnabled by mutableStateOf(false)
        internal set
    var keepScreenOn by mutableStateOf(false)
        internal set
    var screenAwakeMode by mutableStateOf(MeloXScreenAwakeMode.Disabled)
        internal set
    var immersivePlaybackEnabled by mutableStateOf(false)
        internal set
    var showLyricTranslation by mutableStateOf(true)
        internal set
    var automaticLyricSelectionEnabled by mutableStateOf(true)
        internal set
    var lyricStrongBindingEnabled by mutableStateOf(false)
        internal set
    var showLyricRomanization by mutableStateOf(true)
        internal set
    var lyricWordByWordEnabled by mutableStateOf(true)
        internal set
    var lyricPseudoTimingEnabled by mutableStateOf(false)
        internal set
    var lyricTapSeekEnabled by mutableStateOf(true)
        internal set
    var lyricLongPressShareEnabled by mutableStateOf(true)
        internal set
    var lyricInterludeCountdownEnabled by mutableStateOf(true)
        internal set
    var lyricAutoFollowEnabled by mutableStateOf(true)
        internal set
    var lyricReduceMotion by mutableStateOf(false)
        internal set
    var lyricAdvanceMs by mutableStateOf(0)
        internal set
    var lyricAdvanceAppliesToWordByWord by mutableStateOf(false)
        internal set
    var lyricRefreshRate by mutableStateOf(60)
        internal set
    var lyricBackgroundFrameRate by mutableStateOf(24)
        internal set
    var lyricRenderingQuality by mutableStateOf(MeloXLyricsRenderingQuality.High)
        internal set
    var lyricRomanizationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.FocusedLine)
        internal set
    var lyricTranslationDisplayMode by mutableStateOf(MeloXLyricAnnotationDisplayMode.AllLines)
        internal set
    var lyricFollowDelayMs by mutableStateOf(3_000)
        internal set
    var lyricFontScale by mutableStateOf(1f)
        internal set
    var lyricFocusPosition by mutableStateOf(.25f)
        internal set
    var lyricFontWeight by mutableStateOf(MeloXLyricsFontWeight.Heavy)
        internal set
    var lyricLiftMode by mutableStateOf(MeloXLyricsGroupingMode.Character)
        internal set
    var lyricLongToneDetectionMode by mutableStateOf(MeloXLyricsGroupingMode.Character)
        internal set
    var lyricGlowLongTonesOnly by mutableStateOf(true)
        internal set
    var lyricLongToneThresholdMs by mutableStateOf(950)
        internal set
    var lyricSpacingScale by mutableStateOf(1f)
        internal set
    var lyricBlurStrength by mutableStateOf(1f)
        internal set
    var lyricDistanceBlurScale by mutableStateOf(1.05f)
        internal set
    var lyricHiddenInterfaceBlurScale by mutableStateOf(.85f)
        internal set
    var lyricDimAmount by mutableStateOf(1f)
        internal set
    var lyricFocusScale by mutableStateOf(1.02f)
        internal set
    var lyricInactiveOpacity by mutableStateOf(.42f)
        internal set
    var lyricGlowStrength by mutableStateOf(1f)
        internal set
    var lyricGlowEnabled by mutableStateOf(true)
        internal set
    var lyricLongToneStrength by mutableStateOf(1f)
        internal set
    var lyricWordBounceEnabled by mutableStateOf(false)
        internal set
    var lyricHighlightGradientWidth by mutableStateOf(.7f)
        internal set
    var lyricHighlightGradientReduction by mutableStateOf(.65f)
        internal set
    var lyricRomanizationFontScale by mutableStateOf(.65f)
        internal set
    var lyricRomanizationOpacity by mutableStateOf(.9f)
        internal set
    var lyricTranslationFontScale by mutableStateOf(.65f)
        internal set
    var lyricTranslationOpacity by mutableStateOf(.9f)
        internal set
    var lyricInterfaceAutoHideDelayMs by mutableStateOf(5_000)
        internal set
    var lyricScrollHideThresholdDp by mutableStateOf(200)
        internal set
    var lyricCascadeDelayMs by mutableStateOf(21f)
        internal set
    var lyricCascadeDelayIncreaseMs by mutableStateOf(5f)
        internal set
    var lyricCascadeFollowingDelayMs by mutableStateOf(30f)
        internal set
    var lyricCascadeCatchUpRatio by mutableStateOf(.97f)
        internal set
    var lyricCascadeChaseSpeedGradient by mutableStateOf(.70f)
        internal set
    var lyricCascadeDurationMs by mutableStateOf(740f)
        internal set
    var lyricSnapThresholdMs by mutableStateOf(260f)
        internal set
    var lyricCascadeBounceEnabled by mutableStateOf(true)
        internal set
    var lyricCascadeBounce by mutableStateOf(.26f)
        internal set
    var lyricCascadeBounceGradient by mutableStateOf(.85f)
        internal set
    var lyricScaleBounceEnabled by mutableStateOf(true)
        internal set
    var lyricScaleBounce by mutableStateOf(.32f)
        internal set
    var lyricScaleBounceDurationMs by mutableStateOf(580)
        internal set
    var lyricFocusColorLeadMs by mutableStateOf(0)
        internal set
    var lyricsStyle by mutableStateOf(MeloXLyricsStyle.AppleMusic)
        internal set
    var textPVStyle by mutableStateOf(MeloXTextPVStyle.BlueBold)
        internal set
    var textPVMotionIntensity by mutableStateOf(1f)
        internal set
    var textPVAnimationSpeed by mutableStateOf(2f)
        internal set
    var skylineEnabled by mutableStateOf(true)
        internal set
    var skylineShowSongInfo by mutableStateOf(true)
        internal set
    var skylineKeepsScreenAwake by mutableStateOf(true)
        internal set
    var skylineAmbientLines by mutableStateOf(2)
        internal set
    var skylineCurrentFontSize by mutableStateOf(54f)
        internal set
    var skylineCurrentMaximumScale by mutableStateOf(1.1f)
        internal set
    var skylineNextFontSize by mutableStateOf(24f)
        internal set
    var skylineCurrentSpacing by mutableStateOf(14f)
        internal set
    var skylineCurrentWidth by mutableStateOf(.64f)
        internal set
    var skylineNextOpacity by mutableStateOf(.48f)
        internal set
    var skylineAmbientFontSize by mutableStateOf(44f)
        internal set
    var skylineAmbientMaximumCharacters by mutableStateOf(4)
        internal set
    var skylineAmbientMaximumVisibleTexts by mutableStateOf(16)
        internal set
    var skylineAmbientOpacity by mutableStateOf(1f)
        internal set
    var skylineAmbientBlur by mutableStateOf(1f)
        internal set
    var skylineAmbientMaximumTilt by mutableStateOf(8f)
        internal set
    var skylineAmbientDrift by mutableStateOf(1f)
        internal set
    var systemLyricsEnabled by mutableStateOf(false)
        internal set
    var lyricNotificationsEnabled by mutableStateOf(false)
        internal set
    var systemLyricTitleMode by mutableStateOf(MeloXSystemLyricTitleMode.LyricFirst)
        internal set
    var lyricNotificationShowNextLine by mutableStateOf(false)
        internal set
    var lyricNotificationShowProgress by mutableStateOf(true)
        internal set
    var lyricNotificationShowArtwork by mutableStateOf(true)
        internal set
    var lyricNotificationBackgroundOnly by mutableStateOf(false)
        internal set
    var lyricNotificationDismissWhenPaused by mutableStateOf(true)
        internal set
    var lyricNotificationTitleTemplate by mutableStateOf("{lyric}")
        internal set
    var lyricNotificationSubtitleTemplate by mutableStateOf("{song} · {artist}")
        internal set
    var lyricNotificationFallback by mutableStateOf("{song} · {artist}")
        internal set
    var floatingLyricsEnabled by mutableStateOf(false)
        internal set
    var floatingSecondaryMode by mutableStateOf(MeloXSecondaryLyricMode.Auto)
        internal set
    var floatingFontSizeSp by mutableStateOf(18)
        internal set
    var floatingHighContrast by mutableStateOf(true)
        internal set
    var homeTabEnabled by mutableStateOf(true)
        internal set
    var homeQuickActionsEnabled by mutableStateOf(true)
        internal set
    var homePlaylistsEnabled by mutableStateOf(true)
        internal set
    var homeNewSongsEnabled by mutableStateOf(true)
        internal set
    var homeSectionOrder by mutableStateOf(listOf("QuickActions", "Playlists", "NewSongs"))
        internal set
    var exploreTabEnabled by mutableStateOf(true)
        internal set
    var libraryTabEnabled by mutableStateOf(true)
        internal set
    var rememberLastTab by mutableStateOf(true)
        internal set
    var disableAutomaticTabBarShrink by mutableStateOf(false)
        internal set
    var tabOrder by mutableStateOf(listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings"))
        internal set
    var defaultTab by mutableStateOf("Home")
        internal set
    var rememberLibraryPage by mutableStateOf(true)
        internal set
    var defaultLibraryPage by mutableStateOf("Songs")
        internal set
    var downloadLyricsEnabled by mutableStateOf(true)
        internal set
    var musicArea by mutableStateOf("全部")
        internal set
    var showPlaylistPlayCount by mutableStateOf(true)
        internal set
    var showHighQualityPlaylists by mutableStateOf(true)
        internal set
    var clipboardLinksEnabled by mutableStateOf(true)
        internal set
    var hapticFeedbackEnabled by mutableStateOf(true)
        internal set
    var swipeFullAction by mutableStateOf(MeloXSwipeFullAction.PlayNext)
        internal set
    var previousRestartsAfterFiveSeconds by mutableStateOf(true)
        internal set
    var startsHeartModeOnLaunch by mutableStateOf(false)
        internal set
    var volumeControlMode by mutableStateOf(MeloXVolumeControlMode.System)
        internal set
    var showPlayerQualityTip by mutableStateOf(true)
        internal set
    var systemFontEnabled by mutableStateOf(false)
        internal set
    var smartQueueEnabled by mutableStateOf(false)
        internal set
    var transitionUiEnabled by mutableStateOf(false)
        internal set

    private var initialized = false

    private fun prefs() = getPreferences("melox_app_settings")


    /** Reads only state required to draw the first root frame. The remaining settings load after setContent. */
    fun initializeCritical() {
        themeMode = runCatching {
            MeloXThemeMode.valueOf((prefs().getString("theme_mode", MeloXThemeMode.System.name) ?: MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        homeTabEnabled = prefs().getBoolean("tab_home", true)
        exploreTabEnabled = prefs().getBoolean("tab_explore", true)
        libraryTabEnabled = prefs().getBoolean("tab_library", true)
        podcastsTabPlacement = prefs().getBoolean("placement_podcasts_tab", false)
        downloadsTabPlacement = prefs().getBoolean("placement_downloads_tab", false)
        cloudTabPlacement = prefs().getBoolean("placement_cloud_tab", false)
        rememberLastTab = prefs().getBoolean("general_remember_tab", true)
        disableAutomaticTabBarShrink = prefs().getBoolean("general_disable_auto_tabbar_shrink", false)
        tabOrder = (prefs().getString("tab_order", "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings") ?: "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings")
            .split(',').filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
            .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
        defaultTab = (prefs().getString("general_default_tab", "Home") ?: "Home")
        rememberLibraryPage = prefs().getBoolean("library_remember_page", true)
        defaultLibraryPage = (prefs().getString("library_default_page", "Songs") ?: "Songs")
        clipboardLinksEnabled = prefs().getBoolean("general_clipboard_links", true)
        systemFontEnabled = prefs().getBoolean("system_font", false)
    }

    fun initialize(force: Boolean = false) {
        if (initialized && !force) return
        initialized = true
        performanceOverlayEnabled = prefs().getBoolean("developer_performance_overlay", false)
        themeMode = runCatching {
            MeloXThemeMode.valueOf((prefs().getString("theme_mode", MeloXThemeMode.System.name) ?: MeloXThemeMode.System.name))
        }.getOrDefault(MeloXThemeMode.System)
        podcastsEnabled = prefs().getBoolean("feature_podcasts", true)
        listeningHistoryEnabled = prefs().getBoolean("feature_history", true)
        downloadsEnabled = prefs().getBoolean("feature_downloads", true)
        cloudMusicEnabled = prefs().getBoolean("feature_cloud_music", true)
        podcastsHomePlacement = prefs().getBoolean("placement_podcasts_home", true)
        podcastsTabPlacement = prefs().getBoolean("placement_podcasts_tab", false)
        podcastsLibraryPlacement = prefs().getBoolean("placement_podcasts_library", true)
        downloadsHomePlacement = prefs().getBoolean("placement_downloads_home", false)
        downloadsTabPlacement = prefs().getBoolean("placement_downloads_tab", false)
        downloadsLibraryPlacement = prefs().getBoolean("placement_downloads_library", true)
        cloudHomePlacement = prefs().getBoolean("placement_cloud_home", false)
        cloudTabPlacement = prefs().getBoolean("placement_cloud_tab", false)
        cloudLibraryPlacement = prefs().getBoolean("placement_cloud_library", true)
        val legacyFlowingBackdropEnabled = prefs().getBoolean("player_flowing_backdrop", true)
        playerBackgroundMode = (prefs().getString("player_background_mode", "") ?: "")
            .takeIf { it.isNotBlank() }
            ?.let { value -> runCatching { MeloXPlayerBackgroundMode.valueOf(value) }.getOrNull() }
            ?: if (legacyFlowingBackdropEnabled) {
                MeloXPlayerBackgroundMode.FlowingLight
            } else {
                MeloXPlayerBackgroundMode.BlurredArtwork
            }
        flowingBackdropEnabled = playerBackgroundMode != MeloXPlayerBackgroundMode.BlurredArtwork
        playerShell = runCatching {
            MeloXPlayerShell.valueOf((prefs().getString("player_shell", MeloXPlayerShell.AppleMusic.name) ?: MeloXPlayerShell.AppleMusic.name))
        }.getOrDefault(MeloXPlayerShell.AppleMusic)
        artworkMotionEnabled = prefs().getBoolean("player_artwork_motion", true)
        reduceMotion = prefs().getBoolean("reduce_motion", false)
        playerTransitionDurationMs = prefs().getInt("player_transition_duration_ms", 360)
            .let { stored -> if (stored == 575) 360 else stored }
            .coerceIn(200, 1_200)
        playerBackgroundIsolationEnabled = prefs().getBoolean("player_background_isolation", true)
        frostedGlassEnabled = prefs().getBoolean("player_frosted_glass", false)
        keepScreenOn = prefs().getBoolean("player_keep_screen_on", false)
        screenAwakeMode = runCatching {
            MeloXScreenAwakeMode.valueOf(
                prefs().getString(
                    "player_screen_awake_mode",
                    if (keepScreenOn) MeloXScreenAwakeMode.Player.name else MeloXScreenAwakeMode.Disabled.name,
                ) ?: MeloXScreenAwakeMode.Disabled.name,
            )
        }.getOrDefault(MeloXScreenAwakeMode.Disabled)
        immersivePlaybackEnabled = prefs().getBoolean("immersive_playback", false)
        showPlayerQualityTip = prefs().getBoolean("player_show_quality_tip", true)
        systemFontEnabled = prefs().getBoolean("system_font", false)
        smartQueueEnabled = MeloXPlaybackModePreferences.smartQueue()
        transitionUiEnabled = prefs().getBoolean("transition_ui_enabled", false)
        showLyricTranslation = prefs().getBoolean("lyrics_translation", true)
        automaticLyricSelectionEnabled = prefs().getBoolean("lyrics_auto_select", true)
        lyricStrongBindingEnabled = prefs().getBoolean("experimental_lyric_strong_binding", false)
        showLyricRomanization = prefs().getBoolean("lyrics_romanization", false)
        lyricWordByWordEnabled = prefs().getBoolean("lyrics_word_by_word", true)
        lyricPseudoTimingEnabled = prefs().getBoolean("lyrics_pseudo_timing", false)
        lyricTapSeekEnabled = prefs().getBoolean("lyrics_tap_seek", true)
        lyricLongPressShareEnabled = prefs().getBoolean("lyrics_long_press_share", true)
        lyricInterludeCountdownEnabled = prefs().getBoolean("lyrics_interlude_countdown", true)
        lyricAutoFollowEnabled = prefs().getBoolean("lyrics_auto_follow", true)
        lyricReduceMotion = prefs().getBoolean("lyrics_reduce_motion", false)
        lyricAdvanceMs = prefs().getInt("lyrics_advance_ms", 0).coerceIn(-5_000, 5_000)
        lyricAdvanceAppliesToWordByWord = prefs().getBoolean("lyrics_advance_word_by_word", false)
        lyricRefreshRate = prefs().getInt("lyrics_refresh_rate", 60)
            .takeIf { it in setOf(30, 60, 90, 120) } ?: 60
        lyricBackgroundFrameRate = prefs().getInt("lyrics_background_frame_rate", 24)
            .takeIf { it in setOf(15, 24, 30, 45, 60) } ?: 24
        lyricRenderingQuality = runCatching {
            MeloXLyricsRenderingQuality.valueOf(
                prefs().getString("lyrics_rendering_quality", MeloXLyricsRenderingQuality.High.name)
                    ?: MeloXLyricsRenderingQuality.High.name,
            )
        }.getOrDefault(MeloXLyricsRenderingQuality.High)
        lyricRomanizationDisplayMode = annotationMode("lyrics_romanization_display_mode", MeloXLyricAnnotationDisplayMode.FocusedLine)
        lyricTranslationDisplayMode = annotationMode("lyrics_translation_display_mode", MeloXLyricAnnotationDisplayMode.AllLines)
        lyricFollowDelayMs = prefs().getInt("lyrics_follow_delay_ms", 3_000).coerceIn(1_000, 8_000)
        lyricFontScale = prefs().getFloat("lyrics_font_scale", 1f).coerceIn(.8f, 1.25f)
        lyricFocusPosition = prefs().getFloat("lyrics_focus_position", .25f).coerceIn(.05f, .8f)
        lyricFontWeight = runCatching {
            MeloXLyricsFontWeight.valueOf((prefs().getString("lyrics_font_weight", MeloXLyricsFontWeight.Heavy.name) ?: MeloXLyricsFontWeight.Heavy.name))
        }.getOrDefault(MeloXLyricsFontWeight.Heavy)
        lyricLiftMode = runCatching {
            MeloXLyricsGroupingMode.valueOf((prefs().getString("lyrics_lift_mode", MeloXLyricsGroupingMode.Character.name) ?: MeloXLyricsGroupingMode.Character.name))
        }.getOrDefault(MeloXLyricsGroupingMode.Character)
        lyricLongToneDetectionMode = runCatching {
            MeloXLyricsGroupingMode.valueOf((prefs().getString("lyrics_long_tone_detection", MeloXLyricsGroupingMode.Character.name) ?: MeloXLyricsGroupingMode.Character.name))
        }.getOrDefault(MeloXLyricsGroupingMode.Character)
        lyricGlowLongTonesOnly = prefs().getBoolean("lyrics_glow_long_tones_only", true)
        lyricLongToneThresholdMs = prefs().getInt("lyrics_long_tone_threshold_ms", 950).coerceIn(300, 1_500)
        lyricSpacingScale = prefs().getFloat("lyrics_spacing_scale", 1f).coerceIn(.7f, 1.5f)
        lyricBlurStrength = prefs().getFloat("lyrics_blur_strength", 1f).coerceIn(0f, 1.5f)
        lyricDistanceBlurScale = prefs().getFloat("lyrics_distance_blur_scale", 1.05f).coerceIn(0f, 1.5f)
        lyricHiddenInterfaceBlurScale = prefs().getFloat("lyrics_hidden_blur_scale", .85f).coerceIn(0f, 1.5f)
        lyricDimAmount = prefs().getFloat("lyrics_dim_amount", 1f).coerceIn(0f, 1f)
        lyricFocusScale = prefs().getFloat("lyrics_focus_scale", 1.02f).coerceIn(1f, 1.08f)
        lyricInactiveOpacity = prefs().getFloat("lyrics_inactive_opacity", .42f).coerceIn(.15f, .65f)
        lyricGlowStrength = prefs().getFloat("lyrics_glow_strength", 1f).coerceIn(0f, 1.5f)
        lyricGlowEnabled = prefs().getBoolean("lyrics_glow_enabled", true)
        lyricLongToneStrength = prefs().getFloat("lyrics_long_tone_strength", 1f).coerceIn(0f, 1.5f)
        lyricWordBounceEnabled = prefs().getBoolean("lyrics_word_bounce_enabled", false)
        lyricHighlightGradientWidth = prefs().getFloat("lyrics_highlight_gradient_width", .7f).coerceIn(.4f, 3f)
        lyricHighlightGradientReduction = prefs().getFloat("lyrics_highlight_gradient_reduction", .65f).coerceIn(0f, 1f)
        lyricRomanizationFontScale = prefs().getFloat("lyrics_romanization_font_scale", .65f).coerceIn(.5f, .8f)
        lyricRomanizationOpacity = prefs().getFloat("lyrics_romanization_opacity", .9f).coerceIn(.4f, .9f)
        lyricTranslationFontScale = prefs().getFloat("lyrics_translation_font_scale", .65f).coerceIn(.5f, .8f)
        lyricTranslationOpacity = prefs().getFloat("lyrics_translation_opacity", .9f).coerceIn(.4f, .9f)
        lyricInterfaceAutoHideDelayMs = prefs().getInt("lyrics_interface_auto_hide_ms", 5_000).coerceIn(3_000, 15_000)
        lyricScrollHideThresholdDp = prefs().getInt("lyrics_scroll_hide_threshold_dp", 200).coerceIn(40, 240)
        lyricCascadeDelayMs = prefs().getFloat("lyrics_cascade_delay_ms", 21f).coerceIn(0f, 100f)
        lyricCascadeDelayIncreaseMs = prefs().getFloat("lyrics_cascade_delay_increase_ms", 5f).coerceIn(0f, 100f)
        lyricCascadeFollowingDelayMs = prefs().getFloat("lyrics_cascade_following_delay_ms", 30f).coerceIn(0f, 200f)
        lyricCascadeCatchUpRatio = prefs().getFloat("lyrics_cascade_catch_up_ratio", .97f).coerceIn(.5f, 1f)
        lyricCascadeChaseSpeedGradient = prefs().getFloat("lyrics_cascade_chase_gradient", .70f).coerceIn(0f, 1f)
        lyricCascadeDurationMs = prefs().getFloat("lyrics_cascade_duration_ms", 740f).coerceIn(200f, 1_200f)
        lyricSnapThresholdMs = prefs().getFloat("lyrics_snap_threshold_ms", 260f).coerceIn(50f, 500f)
        lyricCascadeBounceEnabled = prefs().getBoolean("lyrics_cascade_bounce_enabled", true)
        lyricCascadeBounce = prefs().getFloat("lyrics_cascade_bounce", .26f).coerceIn(0f, .8f)
        lyricCascadeBounceGradient = prefs().getFloat("lyrics_cascade_bounce_gradient", .85f).coerceIn(0f, 1f)
        lyricScaleBounceEnabled = prefs().getBoolean("lyrics_scale_bounce_enabled", true)
        lyricScaleBounce = prefs().getFloat("lyrics_scale_bounce", .32f).coerceIn(0f, .5f)
        lyricScaleBounceDurationMs = prefs().getInt("lyrics_scale_bounce_duration_ms", 580).coerceIn(150, 800)
        lyricFocusColorLeadMs = prefs().getInt("lyrics_focus_color_lead_ms", 0).coerceIn(-300, 300)
        lyricsStyle = runCatching {
            MeloXLyricsStyle.valueOf((prefs().getString("lyrics_style", MeloXLyricsStyle.AppleMusic.name) ?: MeloXLyricsStyle.AppleMusic.name))
        }.getOrDefault(MeloXLyricsStyle.AppleMusic)
        textPVStyle = runCatching {
            MeloXTextPVStyle.valueOf((prefs().getString("lyrics_text_pv_style", MeloXTextPVStyle.BlueBold.name) ?: MeloXTextPVStyle.BlueBold.name))
        }.getOrDefault(MeloXTextPVStyle.BlueBold)
        textPVMotionIntensity = prefs().getFloat("lyrics_text_pv_motion_intensity", 1f)
            .coerceIn(0f, 2f)
        textPVAnimationSpeed = prefs().getFloat(
            "lyrics_text_pv_animation_speed",
            textPVStyle.referenceAnimationSpeed,
        ).coerceIn(0f, 4f)
        skylineEnabled = prefs().getBoolean("lyrics_skyline_enabled", true)
        skylineShowSongInfo = prefs().getBoolean("lyrics_skyline_song_info", true)
        skylineKeepsScreenAwake = prefs().getBoolean("lyrics_skyline_keep_awake", true)
        skylineAmbientLines = prefs().getInt("lyrics_skyline_ambient_lines", 2).coerceIn(0, 4)
        skylineCurrentFontSize = prefs().getFloat("lyrics_skyline_current_font_size", 54f).coerceIn(36f, 84f)
        skylineCurrentMaximumScale = prefs().getFloat("lyrics_skyline_current_max_scale", 1.1f).coerceIn(1f, 1.2f)
        skylineNextFontSize = prefs().getFloat("lyrics_skyline_next_font_size", 24f).coerceIn(14f, 44f)
        skylineCurrentSpacing = prefs().getFloat("lyrics_skyline_current_spacing", 14f).coerceIn(4f, 36f)
        skylineCurrentWidth = prefs().getFloat("lyrics_skyline_current_width", .64f).coerceIn(.4f, .82f)
        skylineNextOpacity = prefs().getFloat("lyrics_skyline_next_opacity", .48f).coerceIn(.2f, .8f)
        skylineAmbientFontSize = prefs().getFloat("lyrics_skyline_ambient_font_size", 44f).coerceIn(24f, 72f)
        skylineAmbientMaximumCharacters = prefs().getInt("lyrics_skyline_ambient_max_characters", 4).coerceIn(1, 4)
        skylineAmbientMaximumVisibleTexts = prefs().getInt("lyrics_skyline_ambient_max_visible", 16).coerceIn(4, 24)
        skylineAmbientOpacity = prefs().getFloat("lyrics_skyline_ambient_opacity", 1f).coerceIn(.4f, 1.8f)
        skylineAmbientBlur = prefs().getFloat("lyrics_skyline_ambient_blur", 1f).coerceIn(0f, 2f)
        skylineAmbientMaximumTilt = prefs().getFloat("lyrics_skyline_ambient_max_tilt", 8f).coerceIn(0f, 20f)
        skylineAmbientDrift = prefs().getFloat("lyrics_skyline_ambient_drift", 1f).coerceIn(0f, 2f)
        systemLyricsEnabled = prefs().getBoolean("system_lyrics_enabled", false)
        lyricNotificationsEnabled = prefs().getBoolean("lyrics_notifications_enabled", false)
        systemLyricTitleMode = runCatching {
            MeloXSystemLyricTitleMode.valueOf(
                (prefs().getString("system_lyrics_title_mode", MeloXSystemLyricTitleMode.LyricFirst.name) ?: MeloXSystemLyricTitleMode.LyricFirst.name),
            )
        }.getOrDefault(MeloXSystemLyricTitleMode.LyricFirst)
        lyricNotificationShowNextLine = prefs().getBoolean("lyrics_notification_next_line", false)
        lyricNotificationShowProgress = prefs().getBoolean("lyrics_notification_progress", true)
        lyricNotificationShowArtwork = prefs().getBoolean("lyrics_notification_artwork", true)
        lyricNotificationBackgroundOnly = prefs().getBoolean("lyrics_notification_background_only", false)
        lyricNotificationDismissWhenPaused = prefs().getBoolean("lyrics_notification_dismiss_paused", true)
        lyricNotificationTitleTemplate = (prefs().getString("lyrics_notification_title_template", "{lyric}") ?: "{lyric}")
        lyricNotificationSubtitleTemplate = (prefs().getString("lyrics_notification_subtitle_template", "{song} · {artist}") ?: "{song} · {artist}")
        lyricNotificationFallback = (prefs().getString("lyrics_notification_fallback", "{song} · {artist}") ?: "{song} · {artist}")
        floatingLyricsEnabled = prefs().getBoolean("floating_lyrics_enabled", false)
        floatingSecondaryMode = runCatching {
            MeloXSecondaryLyricMode.valueOf(
                (prefs().getString("floating_lyrics_secondary_mode", MeloXSecondaryLyricMode.Auto.name) ?: MeloXSecondaryLyricMode.Auto.name),
            )
        }.getOrDefault(MeloXSecondaryLyricMode.Auto)
        floatingFontSizeSp = prefs().getInt("floating_lyrics_font_size", 18).coerceIn(14, 28)
        floatingHighContrast = prefs().getBoolean("floating_lyrics_high_contrast", true)
        homeTabEnabled = prefs().getBoolean("tab_home", true)
        homeQuickActionsEnabled = prefs().getBoolean("home_quick_actions", true)
        homePlaylistsEnabled = prefs().getBoolean("home_playlists", true)
        homeNewSongsEnabled = prefs().getBoolean("home_new_songs", true)
        homeSectionOrder = (prefs().getString("home_section_order", "QuickActions,Playlists,NewSongs") ?: "QuickActions,Playlists,NewSongs")
            .split(',').filter { it in setOf("QuickActions", "Playlists", "NewSongs") }.distinct()
            .let { order -> (order + listOf("QuickActions", "Playlists", "NewSongs")).distinct() }
        exploreTabEnabled = prefs().getBoolean("tab_explore", true)
        libraryTabEnabled = prefs().getBoolean("tab_library", true)
        rememberLastTab = prefs().getBoolean("general_remember_tab", true)
        disableAutomaticTabBarShrink = prefs().getBoolean("general_disable_auto_tabbar_shrink", false)
        tabOrder = (prefs().getString("tab_order", "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings") ?: "Home,Explore,Library,Podcasts,Downloads,Cloud,Settings")
            .split(',').filter { it in setOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings") }.distinct()
            .let { order -> (order + listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")).distinct() }
        defaultTab = (prefs().getString("general_default_tab", "Home") ?: "Home")
        rememberLibraryPage = prefs().getBoolean("library_remember_page", true)
        defaultLibraryPage = (prefs().getString("library_default_page", "Songs") ?: "Songs")
        downloadLyricsEnabled = prefs().getBoolean("download_lyrics", true)
        musicArea = (prefs().getString("music_area", "全部") ?: "全部")
        showPlaylistPlayCount = prefs().getBoolean("content_playlist_play_count", true)
        showHighQualityPlaylists = prefs().getBoolean("content_high_quality_playlist", true)
        clipboardLinksEnabled = prefs().getBoolean("general_clipboard_links", true)
        hapticFeedbackEnabled = prefs().getBoolean("general_haptic_feedback", true)
        swipeFullAction = runCatching {
            MeloXSwipeFullAction.valueOf(
                (prefs().getString("general_swipe_full_action", MeloXSwipeFullAction.PlayNext.name) ?: MeloXSwipeFullAction.PlayNext.name),
            )
        }.getOrDefault(MeloXSwipeFullAction.PlayNext)
        previousRestartsAfterFiveSeconds = prefs().getBoolean("playback_previous_restarts", true)
        startsHeartModeOnLaunch = prefs().getBoolean("playback_heart_mode_on_launch", false)
        volumeControlMode = runCatching {
            MeloXVolumeControlMode.valueOf((prefs().getString("playback_volume_mode", MeloXVolumeControlMode.System.name) ?: MeloXVolumeControlMode.System.name))
        }.getOrDefault(MeloXVolumeControlMode.System)
    }

    private fun annotationMode(key: String, default: MeloXLyricAnnotationDisplayMode): MeloXLyricAnnotationDisplayMode =
        runCatching { MeloXLyricAnnotationDisplayMode.valueOf(prefs().getString(key, default.name) ?: default.name) }
            .getOrDefault(default)
}
