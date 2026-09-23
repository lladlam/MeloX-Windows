package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import melox.settings.MeloXPlayerBackgroundMode
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.settings.MeloXThemeMode
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXSymbol
import melox.ui.glass.MeloXGlassSegmentedControl
import melox.ui.glass.MeloXGlassTextField
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
    currentSource: MusicSource = MusicSource.Netease,
    onSourceSelected: (MusicSource) -> Unit = {},
    onOpenMessages: () -> Unit = {},
    onOpenServices: () -> Unit = {},
) {
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    if (route != null) {
        SettingsDetail(route!!, currentSource, onSourceSelected, onBack = { route = null })
        return
    }
    val normalized = search.trim()
    Column(
        Modifier.fillMaxSize().background(MeloXColors.Background).verticalScroll(scroll).padding(top = 18.dp, bottom = 140.dp),
    ) {
        MeloXIosTopBar(title = "设置")
        Spacer(Modifier.height(14.dp))
        MeloXGlassTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.padding(horizontal = 20.dp),
            placeholder = { Text("搜索设置", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f)) },
        )
        Spacer(Modifier.height(20.dp))
        SettingsAccountCard()
        MeloXIosListRow(
            title = "音乐来源",
            subtitle = currentSource.displayName,
            leadingIcon = MeloXSymbol.MusicNote,
            showChevron = false,
        )
        MeloXIosListRow(
            title = "音乐源服务",
            subtitle = "登录 QQ 音乐、酷狗和其他来源",
            leadingIcon = MeloXSymbol.Settings,
            onClick = onOpenServices,
        )
        SettingsSections.forEach { (title, items) ->
            val visible = items.filter { item ->
                normalized.isBlank() || item.title.contains(normalized) || item.subtitle.contains(normalized)
            }
            if (visible.isEmpty()) return@forEach
            Text(title, Modifier.padding(start = 28.dp, bottom = 8.dp, top = 8.dp), color = MeloXColors.OnBackground.copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            visible.forEach { item ->
                MeloXIosListRow(
                    title = item.title,
                    subtitle = item.subtitle,
                    leadingIcon = item.symbol,
                    onClick = {
                        when (item) {
                            SettingsRoute.Messages -> onOpenMessages()
                            else -> route = item
                        }
                    },
                )
            }
            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun SettingsAccountCard() {
    var nickname by remember { mutableStateOf<String?>(null) }
    val loggedIn = remember { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        nickname = runCatching { NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }).accountProfile().nickname }.getOrNull()
    }
    MeloXIosListRow(
        title = nickname ?: if (loggedIn) "网易云账号" else "登录网易云音乐",
        subtitle = if (loggedIn) "已登录" else "登录后同步资料库、云盘和私信",
        leadingIcon = MeloXSymbol.Person,
        showChevron = false,
    )
}

@Composable
private fun SettingsDetail(
    route: SettingsRoute,
    currentSource: MusicSource,
    onSourceSelected: (MusicSource) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MeloXColors.Background).padding(top = 18.dp)) {
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
    val options = listOf(MusicQuality.Standard, MusicQuality.High, MusicQuality.Lossless, MusicQuality.HiResolution)
    var selected by remember { mutableStateOf(options.indexOf(MusicQualityPreferences.read()).coerceAtLeast(0)) }
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text("音质", color = MeloXColors.OnSurface, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        MeloXGlassSegmentedControl(
            items = options.map { it.title },
            selectedIndex = selected.coerceIn(0, options.lastIndex),
            onSelected = {
                selected = it
                MusicQualityPreferences.write(options[it])
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    PreferenceToggle("播放超过 5 秒时上一首先回到开头", MeloXSettingsRuntime.previousRestartsAfterFiveSeconds, "playback_previous_restarts")
    PreferenceToggle("登录后以心动模式开始播放", MeloXSettingsRuntime.startsHeartModeOnLaunch, "playback_heart_mode_on_launch")
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
private fun PreferenceToggle(title: String, checked: Boolean, key: String) {
    MeloXIosListRow(
        title = title,
        showChevron = false,
        trailingContent = {
            melox.ui.glass.MeloXGlassToggle(
                checked = checked,
                onCheckedChange = { MeloXSettingsPreferences.setBoolean(key, it) },
            )
        },
    )
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title,
        Modifier.padding(start = 28.dp, top = 16.dp, bottom = 8.dp),
        color = MeloXColors.OnBackground.copy(alpha = 0.48f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}
