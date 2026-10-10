package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import melox.network.NeteaseSearchClient
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

private enum class SettingsRoute(val title: String, val subtitle: String, val symbol: MeloXSymbol) {
    General("通用", "主题、启动行为与链接处理", MeloXSymbol.Settings),
    Appearance("外观", "背景、封面动画与屏幕常亮", MeloXSymbol.Sparkles),
    Content("内容", "地区、歌单信息和发现内容", MeloXSymbol.Grid),
    Playback("播放", "音质、播放行为与自动混音", MeloXSymbol.MusicNote),
    Lyrics("歌词", "翻译、罗马音、逐字与歌词交互", MeloXSymbol.Lyrics),
    Storage("存储管理", "空间统计、下载与缓存清理", MeloXSymbol.Drive),
    Features("功能模块", "播客、云盘、最近播放等模块", MeloXSymbol.ListBullet),
    Messages("私信与站内分享", "联系人、会话历史与文字私信", MeloXSymbol.Message),
    About("关于 MeloX", "版本、项目主页与开源信息", MeloXSymbol.Info),
}

private val SettingsSections = listOf(
    "应用" to listOf(SettingsRoute.General, SettingsRoute.Appearance, SettingsRoute.Content, SettingsRoute.Playback, SettingsRoute.Lyrics, SettingsRoute.Storage),
    "扩展" to listOf(SettingsRoute.Features, SettingsRoute.Messages),
    "关于" to listOf(SettingsRoute.About),
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
    Box(Modifier.fillMaxSize()) {
    val normalized = search.trim()
    Column(
        Modifier.fillMaxSize().background(MeloXColors.pageBackground()).verticalScroll(scroll)
            .padding(horizontal = 20.dp).padding(top = 18.dp, bottom = 140.dp)
            .then(if (route != null) Modifier.clearAndSetSemantics { } else Modifier),
    ) {
        MeloXIosTopBar(title = "设置", contentPadding = PaddingValues(0.dp))
        Spacer(Modifier.height(14.dp))
        MeloXGlassTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            leadingContent = { MeloXSymbolIcon(MeloXSymbol.Search, size = 20, iconSize = 24, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) },
            placeholder = { Text("搜索设置", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 16.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, lineHeight = 21.sp),
        )
        Spacer(Modifier.height(20.dp))
        if (normalized.isBlank() || "账号 登录 网易云 服务 音乐源".contains(normalized)) {
            SettingsAccountCard(onOpenLogin = onOpenLogin, onOpenServices = onOpenServices)
            Spacer(Modifier.height(24.dp))
        }
        SettingsSections.forEach { (title, items) ->
            val visible = items.filter { item ->
                normalized.isBlank() || item.title.contains(normalized) || item.subtitle.contains(normalized)
            }
            if (visible.isEmpty()) return@forEach
            Text(title, Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp), color = MeloXColors.pageOnBackground().copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            MeloXIosGroupedList {
            visible.forEachIndexed { index, item ->
                MeloXIosListRow(
                    title = item.title,
                    leading = { Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                        MeloXSymbolIcon(item.symbol, size = 22, iconSize = 24, color = melox.ui.glass.MeloXSystemColors.Red)
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
    }
    AnimatedVisibility(
        visible = route != null,
        enter = meloXPageEnter(fromRight = true),
        exit = meloXPageExit(toRight = true),
        modifier = Modifier.fillMaxSize().zIndex(1f),
    ) {
        detailRoute?.let { SettingsDetail(it, currentSource, onSourceSelected, onBack = { route = null }) }
    }
    }
}

@Composable
private fun SettingsAccountCard(onOpenLogin: () -> Unit, onOpenServices: () -> Unit) {
    var nickname by remember { mutableStateOf<String?>(null) }
    var loggedIn by remember { mutableStateOf(NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie())) }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) {
            nickname = null
            return@LaunchedEffect
        }
        nickname = runCatching { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }).accountProfile().nickname }.getOrNull()
    }
    val accent = melox.ui.glass.MeloXSystemColors.Red
    Text("账号", Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.48f))
    MeloXIosGroupedList {
    MeloXIosListRow(
        title = nickname ?: if (loggedIn) "网易云账号" else "登录网易云音乐",
        detail = if (loggedIn) "已登录" else null,
        leading = { MeloXSymbolIcon(MeloXSymbol.Person, size = 30, iconSize = 24, weight = FontWeight.Medium, color = accent) },
        onClick = onOpenLogin,
    )
    MeloXIosListRow(
        title = "音乐服务",
        subtitle = "切换音乐源、登录账号与聚合设置",
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
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().testTag("settings-${route.name.lowercase()}")
        .background(MeloXColors.pageBackground()).padding(top = 18.dp)) {
        MeloXIosTopBar(title = route.title, onBack = onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 48.dp)) {
            when (route) {
                SettingsRoute.General -> GeneralSettings()
                SettingsRoute.Appearance -> AppearanceSettings()
                SettingsRoute.Content -> ContentSettings()
                SettingsRoute.Playback -> PlaybackSettings()
                SettingsRoute.Lyrics -> LyricsSettings()
                SettingsRoute.Storage -> StorageSettings()
                SettingsRoute.Features -> FeatureSettings()
                SettingsRoute.Messages -> MessagesSettings()
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
}

@Composable
private fun PreferenceToggle(title: String, checked: Boolean, key: String, subtitle: String? = null, showTopSeparator: Boolean = false) {
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
private fun LyricsSettings() {
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
    SectionLabel("播客位置")
    PreferenceToggle("首页", MeloXSettingsRuntime.podcastsHomePlacement, "placement_podcasts_home")
    PreferenceToggle("独立标签页", MeloXSettingsRuntime.podcastsTabPlacement, "placement_podcasts_tab")
    PreferenceToggle("音乐库", MeloXSettingsRuntime.podcastsLibraryPlacement, "placement_podcasts_library")
    SectionLabel("下载位置")
    PreferenceToggle("首页", MeloXSettingsRuntime.downloadsHomePlacement, "placement_downloads_home")
    PreferenceToggle("独立标签页", MeloXSettingsRuntime.downloadsTabPlacement, "placement_downloads_tab")
    PreferenceToggle("音乐库", MeloXSettingsRuntime.downloadsLibraryPlacement, "placement_downloads_library")
    SectionLabel("云盘位置")
    PreferenceToggle("首页", MeloXSettingsRuntime.cloudHomePlacement, "placement_cloud_home")
    PreferenceToggle("独立标签页", MeloXSettingsRuntime.cloudTabPlacement, "placement_cloud_tab")
    PreferenceToggle("音乐库", MeloXSettingsRuntime.cloudLibraryPlacement, "placement_cloud_library")
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
private fun MessagesSettings() {
    MeloXIosListRow(
        title = "私信",
        subtitle = "桌面版暂不提供站内私信",
        leadingIcon = MeloXSymbol.Message,
        showChevron = false,
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
