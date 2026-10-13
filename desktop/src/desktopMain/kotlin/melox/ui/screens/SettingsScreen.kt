package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import melox.account.NeteaseSessionStore
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.download.MeloXDownloadStore
import melox.music.model.MusicSource
import melox.network.MeloXHttpClient
import melox.platform.meloXCacheDir
import melox.platform.meloXDataDir
import melox.playback.MeloXMediaCache
import melox.playback.PlaybackCommands
import melox.settings.MeloXPlayerBackgroundMode
import melox.settings.MeloXPlayerShell
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXThemeMode
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.meloXPageEnter
import melox.ui.foundation.meloXPageExit
import melox.ui.navigation.MeloXBackHandler
import melox.ui.glass.MeloXGlassSegmentedControl
import melox.ui.glass.MeloXGlassTextField
import melox.ui.glass.MeloXSettingsDropdown
import melox.ui.glass.LocalSettingsGroupRowIndex
import melox.ui.theme.MeloXColors
import melox.ui.legal.pinnedString
import melox.account.rememberNeteaseSessionStore
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.CircularProgressIndicator

private enum class SettingsRoute(val resourceKey: String, val symbol: String) {
    General("general", "⚙"),
    Appearance("appearance", "✦"),
    Content("content", "▦"),
    TabLayout("tabs", "▥"),
    Playback("playback", "♫"),
    Lyrics("lyrics", "❞"),
    Storage("storage", "▰"),
    Features("features", "☷"),
    Messages("messages", "✉"),
    RemoteConfig("remote_config", "⌁"),
    About("about", "ⓘ"),
    Legal("legal", "▤"),
    Privacy("privacy", "⌘"),
    ;
    val title: String get() = pinnedString("settings_route_$resourceKey")
    val subtitle: String get() = pinnedString(if (this == Privacy) "settings_privacy_personalization" else "settings_sub_$resourceKey")
    val keywords: String get() = pinnedString(if (this == Privacy) "settings_privacy_algorithm" else "settings_kw_$resourceKey")
}

private val SettingsSections = listOf(
    "settings_section_app" to listOf(SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content, SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.Storage),
    "settings_section_more" to listOf(SettingsRoute.Features, SettingsRoute.Messages, SettingsRoute.TabLayout),
    "settings_section_about" to listOf(SettingsRoute.RemoteConfig, SettingsRoute.About, SettingsRoute.Legal, SettingsRoute.Privacy),
)

@Composable
fun SettingsScreen(
    routeRequest: String? = null,
    onRouteRequestConsumed: () -> Unit = {},
    currentSource: MusicSource = MusicSource.Netease,
    onSourceSelected: (MusicSource) -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenServices: () -> Unit = {},
    onOpenLogin: () -> Unit = {},
) {
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    val detailFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    val equalizer by melox.playback.MeloXEqualizerSettings.current.collectAsState()
    LaunchedEffect(route, equalizer.enabled, MeloXSettingsRuntime.lyricsStyle) { detailFocus.requestFocus() }
    val session = rememberNeteaseSessionStore()
    LaunchedEffect(session.cookie) { if (session.isLoggedIn) session.refreshProfile() }
    LaunchedEffect(routeRequest) {
        if (routeRequest == null) return@LaunchedEffect
        route = SettingsRoute.entries.firstOrNull { it.name == routeRequest }
        onRouteRequestConsumed()
    }
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    var detailRoute by remember { mutableStateOf<SettingsRoute?>(null) }
    if (route != null) detailRoute = route
    MeloXBackHandler(enabled = route != null) { route = null }
    Box(Modifier.fillMaxSize().focusRequester(detailFocus).focusable()) {
    val normalized = search.trim().lowercase()
    val visibleSections = SettingsSections.mapNotNull { (title, items) ->
        items.filter { normalized.isBlank() || "${it.title} ${it.subtitle} ${it.keywords}".lowercase().contains(normalized) }
            .takeIf { it.isNotEmpty() }?.let { title to it }
    }
    Column(
        Modifier.fillMaxSize().testTag("settings-root").background(MeloXColors.pageBackground()).verticalScroll(scroll)
             .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = MeloXBottomContentClearanceDp)
            .then(if (route != null) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        MeloXIosTopBar(title = pinnedString("tab_settings"), contentPadding = PaddingValues(0.dp))
        Spacer(Modifier.height(14.dp))
        MeloXGlassTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth().testTag("settings-search"),
            leadingContent = { MeloXSymbolIcon(MeloXSymbol.Search, size = 20, iconSize = 24, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) },
             placeholder = { Text(pinnedString("settings_search_placeholder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(20.dp))
        if (normalized.isBlank() || pinnedString("settings_account_keywords").lowercase().contains(normalized)) {
            SettingsAccountCard(session, onOpenLogin = onOpenLogin, onOpenServices = onOpenServices)
            Spacer(Modifier.height(24.dp))
        }
        visibleSections.forEach { (title, items) ->
            Text(pinnedString(title), Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp), color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
            items.forEachIndexed { index, item ->
                MeloXIosListRow(
                    modifier = Modifier.testTag("settings-open-${item.name.lowercase()}"),
                    title = item.title,
                    leading = { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        MeloXActionIcon(item.symbol, Modifier.size(22.dp), melox.ui.glass.MeloXSystemColors.Red)
                    } },
                    showTopSeparator = index > 0,
                    onClick = {
                        when (item) {
                            SettingsRoute.Messages -> onOpenMessages()
                            else -> route = item
                        }
                    },
                )
            }
            }
            Spacer(Modifier.height(22.dp))
        }
        if (visibleSections.isEmpty() && normalized.isNotBlank()) {
            Text(pinnedString("settings_search_empty"), Modifier.padding(vertical = 36.dp).testTag("settings-search-empty"),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .48f))
        }
        if (normalized.isBlank()) {
            SettingsDangerButton(pinnedString("settings_reset_player"), Modifier.testTag("settings-reset")) {
                MeloXSettingsPreferences.reset()
                melox.playback.MeloXPlaybackModePreferences.reset()
                PlaybackCommands.changeQuality(MusicQuality.Standard)
            }
            Spacer(Modifier.height(18.dp))
            if (session.isLoggedIn) SettingsDangerButton(pinnedString("settings_sign_out"), Modifier.testTag("settings-sign-out")) { session.clear() }
        }
    }
    AnimatedVisibility(
        visible = route != null,
        enter = meloXPageEnter(fromRight = true),
        exit = meloXPageExit(toRight = true),
        modifier = Modifier.fillMaxSize().zIndex(1f),
    ) {
        detailRoute?.let { SettingsDetail(it, currentSource, onSourceSelected, onOpenMessages, onBack = { route = null }) }
    }
    }
}

@Composable
private fun SettingsAccountCard(session: NeteaseSessionStore, onOpenLogin: () -> Unit, onOpenServices: () -> Unit) {
    val accent = melox.ui.glass.MeloXSystemColors.Red
    Text(pinnedString("accessibility_account"), Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f))
    MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface) {
    MeloXIosListRow(
        title = session.profile?.nickname ?: if (session.isLoggedIn && session.isRefreshing) pinnedString("settings_account_loading") else pinnedString("app_login_netease"),
        modifier = Modifier.testTag("settings-open-login"),
        detail = if (session.isLoggedIn && session.profile != null) pinnedString("provider_logged_in") else null,
        leading = {
            when {
                session.profile != null -> MeloXArtworkImage(session.profile?.avatarUrl, accent, Modifier.size(30.dp).clip(CircleShape))
                session.isLoggedIn && session.isRefreshing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp, color = accent)
                else -> MeloXSymbolIcon(MeloXSymbol.PersonFill, size = 30, iconSize = 24, weight = FontWeight.Medium, color = accent)
            }
        },
        onClick = onOpenLogin,
    )
    MeloXIosListRow(
        title = pinnedString("tab_services"),
        subtitle = pinnedString("settings_services_subtitle"),
        modifier = Modifier.testTag("settings-open-services"),
        leading = { MeloXSymbolIcon(MeloXSymbol.MusicNote, size = 30, iconSize = 24, color = accent) },
        showTopSeparator = true,
        onClick = onOpenServices,
    )
    }
}

@Composable
private fun SettingsDetail(
    route: SettingsRoute,
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    onOpenMessages: () -> Unit,
    onBack: () -> Unit,
) {
    if (route == SettingsRoute.Legal || route == SettingsRoute.Privacy) {
        melox.ui.glass.MeloXPinnedListPage(route.title, onNavigateBack = onBack,
            bottomPadding = MeloXBottomContentClearanceDp,
            modifier = Modifier.testTag("settings-${route.name.lowercase()}")) {
            item {
                if (route == SettingsRoute.Legal) LegalSettings() else PrivacySettings()
            }
        }
        return
    }
    Column(Modifier.fillMaxSize().testTag("settings-${route.name.lowercase()}")
        .background(MeloXColors.pageBackground()).padding(top = 18.dp)) {
        MeloXIosTopBar(title = route.title, onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = MeloXBottomContentClearanceDp)) {
            when (route) {
                SettingsRoute.General -> GeneralSettings()
                SettingsRoute.Appearance -> AppearanceSettings()
                SettingsRoute.Content -> ContentSettings()
                SettingsRoute.TabLayout -> TabLayoutSettings(currentSource)
                SettingsRoute.Playback -> PlaybackSettings()
                SettingsRoute.Lyrics -> LyricsSettings()
                SettingsRoute.Storage -> StorageSettings()
                SettingsRoute.Features -> FeatureSettings()
                SettingsRoute.Messages -> MessagesSettings(onOpenMessages)
                SettingsRoute.RemoteConfig -> RemoteConfigSettings()
                SettingsRoute.Legal -> LegalSettings()
                SettingsRoute.Privacy -> PrivacySettings()
                SettingsRoute.About -> AboutSettings()
            }
        }
    }
}

@Composable
private fun GeneralSettings() {
    PreferenceToggle("歌单显示专辑封面", MeloXSettingsRuntime.playlistShowArtwork, "playlist_show_artwork")
    PreferenceToggle("歌单隐藏搜索栏", MeloXSettingsRuntime.playlistSearchBarHidden, "playlist_search_bar_hidden", "顶部下拉显示搜索")
    val modes = listOf(MeloXThemeMode.System, MeloXThemeMode.Light, MeloXThemeMode.Dark)
    val labels = listOf("跟随系统", "浅色", "深色")
    val selected = modes.indexOf(MeloXSettingsRuntime.themeMode).coerceAtLeast(0)
    SectionLabel("主题")
    MeloXGlassSegmentedControl(
        items = labels,
        selectedIndex = selected,
        onSelected = { MeloXSettingsPreferences.setString("theme_mode", modes[it].name) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))
    PreferenceToggle("记住上次标签页", MeloXSettingsRuntime.rememberLastTab, "general_remember_tab")
    MeloXSettingsDropdown("启动页面", MeloXSettingsRuntime.defaultTab,
        listOf("Home" to "首页", "Explore" to "发现", "Library" to "音乐库", "Settings" to "设置"),
        onSelected = { MeloXSettingsPreferences.setString("general_default_tab", it) },
        modifier = Modifier.padding(horizontal = 20.dp))
    MeloXSettingsDropdown("歌曲完整侧滑操作", MeloXSettingsRuntime.swipeFullAction,
        listOf(melox.settings.MeloXSwipeFullAction.PlayNext to "下一首播放", melox.settings.MeloXSwipeFullAction.AddToQueue to "添加到队列"),
        onSelected = { MeloXSettingsPreferences.setString("general_swipe_full_action", it.name) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    PreferenceToggle("不自动缩小底栏", MeloXSettingsRuntime.disableAutomaticTabBarShrink, "general_disable_auto_tabbar_shrink")
}

@Composable
private fun AppearanceSettings() {
    val modes = MeloXPlayerBackgroundMode.entries
    val labels = listOf("流动光影", "Apple 歌词", "模糊封面", "Mei Mesh")
    val selected = modes.indexOf(MeloXSettingsRuntime.playerBackgroundMode).coerceAtLeast(0)
    SectionLabel("播放器背景")
    MeloXGlassSegmentedControl(
        items = labels,
        selectedIndex = selected,
        onSelected = { MeloXSettingsPreferences.setString("player_background_mode", modes[it].name) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))
    val shells = listOf(MeloXPlayerShell.AppleMusic, MeloXPlayerShell.Classic)
    val shellLabels = listOf("Apple Music", "经典播放器")
    SectionLabel("播放器外观")
    MeloXGlassSegmentedControl(
        items = shellLabels,
        selectedIndex = shells.indexOf(MeloXSettingsRuntime.playerShell).coerceAtLeast(0),
        tagPrefix = "player-shell",
        onSelected = { MeloXSettingsPreferences.setString("player_shell", shells[it].name) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))
    PreferenceToggle("封面播放动效", MeloXSettingsRuntime.artworkMotionEnabled, "player_artwork_motion")
    PreferenceToggle("减少动态效果", MeloXSettingsRuntime.reduceMotion, "reduce_motion")
    PreferenceToggle("使用毛玻璃", MeloXSettingsRuntime.frostedGlassEnabled, "player_frosted_glass")
    PreferenceToggle("播放时保持屏幕常亮", MeloXSettingsRuntime.keepScreenOn, "player_keep_screen_on")
    PreferenceToggle("沉浸式播放", MeloXSettingsRuntime.immersivePlaybackEnabled, "immersive_playback")
}

@Composable
private fun ContentSettings() {
    val areas = listOf("全部", "华语", "欧美", "日语", "韩语")
    val current = MeloXSettingsRuntime.musicArea.takeIf { it in areas } ?: "全部"
    SectionLabel("新碟与发现地区")
    MeloXGlassSegmentedControl(
        items = areas,
        selectedIndex = areas.indexOf(current),
        onSelected = { MeloXSettingsPreferences.setString("music_area", areas[it]) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    )
    Spacer(Modifier.height(12.dp))
    PreferenceToggle("显示歌单播放量", MeloXSettingsRuntime.showPlaylistPlayCount, "content_playlist_play_count")
    PreferenceToggle("发现页显示精品歌单", MeloXSettingsRuntime.showHighQualityPlaylists, "content_high_quality_playlist")
    CrossProviderFallbackSetting()
}

@Composable
private fun PlaybackSettings() {
    var quality by remember { mutableStateOf(MusicQualityPreferences.read()) }
    MeloXIosGroupedList(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        CompositionLocalProvider(LocalSettingsGroupRowIndex provides mutableIntStateOf(0)) {
            MeloXSettingsDropdown(
                title = "播放音质", selected = quality, items = MusicQuality.entries.map { it to it.title },
                onSelected = { quality = it; PlaybackCommands.changeQuality(it) }, grouped = true,
            )
        }
        PreferenceToggle("记住播放器上次页面", MeloXSettingsPreferences.boolean("playback_remember_page", true),
            "playback_remember_page", showTopSeparator = true)
        PreferenceToggle("退出时保存播放队列", MeloXSettingsPreferences.boolean("playback_save_queue", true),
            "playback_save_queue", subtitle = "重新打开软件时恢复退出前的播放列表、当前歌曲和进度。", showTopSeparator = true)
        PreferenceToggle("登录后以心动模式开始播放", MeloXSettingsRuntime.startsHeartModeOnLaunch,
            "playback_heart_mode_on_launch", showTopSeparator = true)
        PreferenceToggle("播放超过 5 秒时上一首先回到开头", MeloXSettingsRuntime.previousRestartsAfterFiveSeconds,
            "playback_previous_restarts", showTopSeparator = true)
        PreferenceToggle("显示播放音质提示", MeloXSettingsRuntime.showPlayerQualityTip,
            "player_show_quality_tip", subtitle = "控制进度条下方的音质提示。", showTopSeparator = true)
        CompositionLocalProvider(LocalSettingsGroupRowIndex provides mutableIntStateOf(1)) {
            MeloXSettingsDropdown(
                title = "播放器展开/收回时长", selected = MeloXSettingsRuntime.playerTransitionDurationMs,
                items = listOf(240, 300, 360, 460, 600).map { it to "$it 毫秒" },
                onSelected = { MeloXSettingsPreferences.setInt("player_transition_duration_ms", it) }, grouped = true,
            )
        }
    }
    AutoMixPlaybackSettings()
    Spacer(Modifier.height(12.dp))
    EqualizerSettings()
}

@Composable
internal fun PreferenceToggle(title: String, checked: Boolean, key: String, subtitle: String? = null, showTopSeparator: Boolean = false) {
    MeloXIosListRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        showTopSeparator = showTopSeparator,
        trailingContent = {
            melox.ui.glass.MeloXGlassToggle(
                checked = checked,
                modifier = Modifier.testTag("setting-$key"),
                onCheckedChange = { MeloXSettingsPreferences.setBoolean(key, it) },
            )
        },
    )
}

@Composable
private fun AutoMixPlaybackSettings() {
    var settings by remember { mutableStateOf(melox.playback.MeloXAutoMixSettings.read()) }
    val modes = melox.playback.MeloXPlaybackModeRuntime
    SectionLabel("AutoMix 与智能队列")
    MeloXIosListRow(title = "自动过渡", showChevron = false, trailingContent = {
        melox.ui.glass.MeloXGlassToggle(modes.autoMixEnabled, onCheckedChange = {
            PlaybackCommands.playerController()?.setAutoMixEnabled(it) ?: melox.playback.MeloXPlaybackModePreferences.setAutoMix(it)
        }, modifier = Modifier.testTag("setting-auto_mix"))
    })
    MeloXIosListRow(title = "智能队列", subtitle = "分析候选歌曲，按兼容节拍逐首扩展队列", showChevron = false, trailingContent = {
        melox.ui.glass.MeloXGlassToggle(modes.smartQueueEnabled, onCheckedChange = {
            PlaybackCommands.playerController()?.setSmartQueueEnabled(it) ?: melox.playback.MeloXPlaybackModePreferences.setSmartQueue(it)
        }, modifier = Modifier.testTag("setting-smart_queue"))
    })
    MeloXSettingsDropdown("过渡模式", settings.mode, melox.playback.MeloXAutoMixMode.entries.map { it to if (it == melox.playback.MeloXAutoMixMode.Smart) "智能分析" else "固定时长" },
        onSelected = { melox.playback.MeloXPlaybackModePreferences.setAutoMixString("automix_mode", it.name); settings = settings.copy(mode = it) })
    MeloXSettingsDropdown("音量曲线", settings.fadeCurve, melox.playback.MeloXAutoMixFadeCurve.entries.map { it to it.name },
        onSelected = { melox.playback.MeloXPlaybackModePreferences.setAutoMixString("automix_fade_curve", it.name); settings = settings.copy(fadeCurve = it) })
    MeloXSettingsDropdown("固定过渡时长", settings.fixedDurationMs, listOf(3000L, 5000L, 8000L, 12000L, 20000L).map { it to "${it / 1000} 秒" },
        onSelected = { melox.playback.MeloXPlaybackModePreferences.setAutoMixLong("automix_fixed_duration_ms", it); settings = settings.copy(fixedDurationMs = it) })
    MeloXSettingsDropdown("分析失败回退", settings.fallback, melox.playback.MeloXAutoMixFallback.entries.map { it to when (it) {
        melox.playback.MeloXAutoMixFallback.Crossfade -> "交叉淡化"
        melox.playback.MeloXAutoMixFallback.ShortCrossfade -> "短交叉淡化"
        else -> "正常播放"
    } }, onSelected = { melox.playback.MeloXPlaybackModePreferences.setAutoMixString("automix_fallback", it.name); settings = settings.copy(fallback = it) })
    MeloXIosListRow(title = "节拍匹配", showChevron = false, trailingContent = {
        melox.ui.glass.MeloXGlassToggle(settings.tempoMatching, onCheckedChange = {
            melox.playback.MeloXPlaybackModePreferences.setAutoMixBoolean("automix_tempo_matching", it); settings = settings.copy(tempoMatching = it)
        })
    })
    MeloXIosListRow(title = "流媒体音频分析", showChevron = false, trailingContent = {
        melox.ui.glass.MeloXGlassToggle(settings.analyzeStreaming, onCheckedChange = {
            melox.playback.MeloXPlaybackModePreferences.setAutoMixBoolean("automix_analyze_streaming", it); settings = settings.copy(analyzeStreaming = it)
        })
    })
}

@Composable
private fun LyricsSettings() {
    AlternativeLyricsSettings()
    PreferenceToggle("显示翻译", MeloXSettingsRuntime.showLyricTranslation, "lyrics_translation")
    PreferenceToggle("显示罗马音", MeloXSettingsRuntime.showLyricRomanization, "lyrics_romanization")
    PreferenceToggle("逐字歌词", MeloXSettingsRuntime.lyricWordByWordEnabled, "lyrics_word_by_word")
    PreferenceToggle("自动跟随当前歌词", MeloXSettingsRuntime.lyricAutoFollowEnabled, "lyrics_auto_follow")
    PreferenceToggle("点击歌词跳转进度", MeloXSettingsRuntime.lyricTapSeekEnabled, "lyrics_tap_seek")
}

@Composable
private fun FeatureSettings() {
    PreferenceToggle("播客", MeloXSettingsRuntime.podcastsEnabled, "feature_podcasts")
    PreferenceToggle("最近播放", MeloXSettingsRuntime.listeningHistoryEnabled, "feature_history")
    PreferenceToggle("下载", MeloXSettingsRuntime.downloadsEnabled, "feature_downloads")
    PreferenceToggle("音乐云盘", MeloXSettingsRuntime.cloudMusicEnabled, "feature_cloud_music")
}

@Composable
private fun StorageSettings() {
    val downloads = remember { MeloXDownloadStore.instance() }
    val downloadDir = remember { File(meloXDataDir(), "melox_downloads") }
    val cacheDir = remember { File(meloXCacheDir()) }
    var fileCount by remember { mutableStateOf(downloads.downloads.size) }
    var cacheFiles by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(downloads.downloads.size) {
        fileCount = downloads.downloads.size
        cacheFiles = cacheDir.listFiles()?.size ?: 0
    }
    MeloXIosListRow(title = "下载目录", subtitle = downloadDir.absolutePath, leadingIcon = MeloXSymbol.Drive, showChevron = false)
    MeloXIosListRow(title = "已下载歌曲", subtitle = "$fileCount 首", leadingIcon = MeloXSymbol.MusicNote, showChevron = false)
    MeloXIosListRow(title = "缓存目录", subtitle = cacheDir.absolutePath, leadingIcon = MeloXSymbol.Drive, showChevron = false)
    MeloXIosListRow(title = "缓存条目", subtitle = "$cacheFiles", leadingIcon = MeloXSymbol.Drive, showChevron = false)
    MeloXIosListRow(
        title = "清理网络与播放缓存",
        subtitle = message ?: "不删除已下载歌曲",
        leadingIcon = MeloXSymbol.Drive,
        showChevron = false,
        onClick = {
            runCatching {
                MeloXHttpClient.clearCache()
                MeloXMediaCache.clear()
            }.onSuccess {
                cacheFiles = cacheDir.listFiles()?.size ?: 0
                message = "缓存已清理"
            }.onFailure {
                message = it.message ?: "清理失败"
            }
        },
    )
}

@Composable
private fun MessagesSettings(onOpenMessages: () -> Unit) {
    MeloXIosListRow(
        title = "私信",
        subtitle = "查看联系人、会话详情并发送文字私信",
        leadingIcon = MeloXSymbol.Message,
        onClick = onOpenMessages,
    )
}

@Composable
private fun AboutSettings() {
    MeloXIosListRow(title = "MeloX", subtitle = "Windows 桌面版", leadingIcon = MeloXSymbol.Info, showChevron = false)
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
        color = MeloXColors.pageOnBackground().copy(alpha = 0.48f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
