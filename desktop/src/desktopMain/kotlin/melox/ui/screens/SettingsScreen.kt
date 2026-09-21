package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import melox.ui.foundation.*
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

// ── Settings state holder ──

@Stable
private class SettingsState {
    var isLoggedIn by mutableStateOf(false)
    var username by mutableStateOf("MeloX")
    var theme by mutableIntStateOf(0)
    var launchBehavior by mutableIntStateOf(0)
    var linkHandling by mutableIntStateOf(0)
    var audioQuality by mutableIntStateOf(0)
    var autoMix by mutableStateOf(false)
    var crossfadeSeconds by mutableFloatStateOf(0f)
    var translation by mutableStateOf(true)
    var romanization by mutableStateOf(false)
    var wordByWord by mutableStateOf(false)
    var backgroundMode by mutableIntStateOf(0)
    var coverAnimation by mutableStateOf(true)
    var cacheSize by mutableStateOf("128 MB")
}

private val themeOptions = listOf("自动", "深色", "浅色")
private val launchOptions = listOf("上次退出位置", "首页", "音乐库")
private val linkOptions = listOf("内置浏览器", "系统浏览器")
private val qualityOptions = listOf("标准", "高品", "无损")
private val backgroundOptions = listOf("模糊", "渐变", "纯色")

// ── Top-level composable ──

@Composable
fun SettingsScreen() {
    val state = remember { SettingsState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        MeloXIosTopBar(title = "设置")

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // ── Account ──
            item {
                SectionGroup {
                    if (!state.isLoggedIn) {
                        MeloXIosListRow(
                            title = "登录账号",
                            subtitle = "登录以同步音乐和收藏",
                            leadingIcon = MeloXSymbol.Person,
                            onClick = { state.isLoggedIn = true },
                        )
                    } else {
                        MeloXIosListRow(
                            title = state.username,
                            subtitle = "点击退出登录",
                            leadingIcon = MeloXSymbol.PersonFill,
                            trailingContent = {
                                MeloXSymbolIcon(
                                    symbol = MeloXSymbol.ChevronRight,
                                    color = MeloXColors.Primary.copy(alpha = 0.88f),
                                    size = 20,
                                )
                            },
                            onClick = { state.isLoggedIn = false },
                        )
                    }
                }
            }

            // ── Application ──
            item {
                SectionGroup(header = "应用") {
                    SegmentedRow("主题", themeOptions, state.theme) { state.theme = it }
                    SegmentedRow("启动行为", launchOptions, state.launchBehavior) { state.launchBehavior = it }
                    SegmentedRow("链接处理", linkOptions, state.linkHandling) { state.linkHandling = it }
                }
            }

            // ── Playback ──
            item {
                SectionGroup(header = "播放") {
                    SegmentedRow("音质选择", qualityOptions, state.audioQuality) { state.audioQuality = it }
                    ToggleRow("自动混音", state.autoMix) { state.autoMix = it }
                    ValueRow("交叉淡化", if (state.crossfadeSeconds == 0f) "关闭" else "${state.crossfadeSeconds.toInt()}秒")
                }
            }

            // ── Lyrics ──
            item {
                SectionGroup(header = "歌词") {
                    ToggleRow("翻译", state.translation) { state.translation = it }
                    ToggleRow("音译", state.romanization) { state.romanization = it }
                    ToggleRow("逐字歌词", state.wordByWord) { state.wordByWord = it }
                }
            }

            // ── Appearance ──
            item {
                SectionGroup(header = "外观") {
                    SegmentedRow("背景模式", backgroundOptions, state.backgroundMode) { state.backgroundMode = it }
                    ToggleRow("封面动画", state.coverAnimation) { state.coverAnimation = it }
                }
            }

            // ── Storage ──
            item {
                SectionGroup(header = "存储") {
                    ValueRow("缓存大小", state.cacheSize)
                    MeloXIosListRow(
                        title = "清除缓存",
                        leadingIcon = MeloXSymbol.Trash,
                        leadingIconColor = MeloXColors.Error,
                        showChevron = false,
                        onClick = { state.cacheSize = "0 B" },
                    )
                }
            }

            // ── About ──
            item {
                SectionGroup(header = "关于") {
                    ValueRow("版本", "1.0.0")
                    MeloXIosListRow(
                        title = "项目主页",
                        leadingIcon = MeloXSymbol.Globe,
                        onClick = {},
                    )
                    MeloXIosListRow(
                        title = "开源许可",
                        leadingIcon = MeloXSymbol.ListBullet,
                        onClick = {},
                    )
                }
            }
        }
    }
}

// ── Section group with rounded rectangle background ──

@Composable
private fun SectionGroup(
    header: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (header != null) {
            Text(
                text = header,
                style = MeloXTypography.caption,
                color = MeloXColors.OnSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MeloXGlass.largeCardShape)
                .background(MeloXColors.SurfaceVariant),
        ) {
            content()
        }
    }
}

// ── Segmented control row ──

@Composable
private fun SegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    MeloXIosListRow(
        title = title,
        showChevron = false,
        trailingContent = {
            MeloXSegmentedControl(
                selectedIndex = selectedIndex,
                options = options,
                onSelected = onSelect,
            )
        },
    )
}

// ── Toggle row with glass toggle ──

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MeloXIosListRow(
        title = title,
        showChevron = false,
        trailingContent = {
            MeloXGlassToggle(
                checked = checked,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

// ── Value display row ──

@Composable
private fun ValueRow(
    title: String,
    value: String,
) {
    MeloXIosListRow(
        title = title,
        showChevron = false,
        trailingContent = {
            Text(
                text = value,
                style = MeloXTypography.subheadline,
                color = MeloXColors.OnSurfaceVariant,
            )
        },
    )
}
