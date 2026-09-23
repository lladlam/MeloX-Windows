package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.account.NeteaseSessionStore
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.network.NeteaseSearchClient
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
fun SettingsScreen() {
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    var search by remember { mutableStateOf("") }
    val scroll = rememberScrollState()
    if (route != null) {
        SettingsDetail(route!!, onBack = { route = null })
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
        SettingsSections.forEach { (title, items) ->
            val visible = items.filter { item ->
                normalized.isBlank() || item.title.contains(normalized) || item.subtitle.contains(normalized)
            }
            if (visible.isEmpty()) return@forEach
            Text(title, Modifier.padding(start = 28.dp, bottom = 8.dp, top = 8.dp), color = MeloXColors.OnBackground.copy(alpha = 0.48f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            visible.forEach { item ->
                MeloXIosListRow(title = item.title, subtitle = item.subtitle, leadingIcon = item.symbol, onClick = { route = item })
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
private fun SettingsDetail(route: SettingsRoute, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MeloXColors.Background).padding(top = 18.dp)) {
        MeloXIosTopBar(title = route.title, onBack = onBack)
        when (route) {
            SettingsRoute.Playback -> PlaybackSettings()
            SettingsRoute.Lyrics -> LyricsSettings()
            SettingsRoute.About -> AboutSettings()
            else -> Text(route.subtitle, Modifier.padding(20.dp), color = MeloXColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun PlaybackSettings() {
    val options = listOf(MusicQuality.Standard, MusicQuality.High, MusicQuality.Lossless, MusicQuality.HiResolution)
    var selected by remember { mutableStateOf(options.indexOf(MusicQualityPreferences.read()).coerceAtLeast(0)) }
    Column(Modifier.padding(20.dp)) {
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
}

@Composable
private fun LyricsSettings() {
    var translation by remember { mutableStateOf(true) }
    var romanization by remember { mutableStateOf(false) }
    Column {
        MeloXGlassToggleRow("翻译", translation) { translation = it }
        MeloXGlassToggleRow("罗马音", romanization) { romanization = it }
    }
}

@Composable
private fun AboutSettings() {
    MeloXIosListRow(title = "MeloX", subtitle = "Windows 桌面版", leadingIcon = MeloXSymbol.Info, showChevron = false)
}

@Composable
private fun MeloXGlassToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    MeloXIosListRow(
        title = title,
        showChevron = false,
        trailingContent = { melox.ui.glass.MeloXGlassToggle(checked, onChange) },
    )
}
