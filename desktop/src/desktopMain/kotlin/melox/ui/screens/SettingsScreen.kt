package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

// ── Settings state holder ──

@Stable
private class SettingsState {
    var searchQuery by mutableStateOf("")
    var isLoggedIn by mutableStateOf(false)
    var username by mutableStateOf("")
    var theme by mutableStateOf("自动")
    var launchBehavior by mutableStateOf("上次退出位置")
    var linkHandling by mutableStateOf("内置浏览器")
    var audioQuality by mutableStateOf("标准")
    var autoMix by mutableStateOf(false)
    var crossfadeSeconds by mutableFloatStateOf(0f)
    var translation by mutableStateOf(true)
    var romanization by mutableStateOf(false)
    var wordByWord by mutableStateOf(false)
    var backgroundMode by mutableStateOf("模糊")
    var coverAnimation by mutableStateOf(true)
    var keepScreenOn by mutableStateOf(false)
    var cacheSize by mutableStateOf("128 MB")
}

// ── Top-level composable ──

@Composable
fun SettingsScreen() {
    val state = remember { SettingsState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        SettingsTopBar()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item { SearchBar(state) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
            item { AccountSection(state) }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("应用") {
                    DropdownSettingItem(
                        label = "主题",
                        value = state.theme,
                        options = listOf("自动", "深色", "浅色"),
                        onSelect = { state.theme = it },
                    )
                    DropdownSettingItem(
                        label = "启动行为",
                        value = state.launchBehavior,
                        options = listOf("上次退出位置", "首页", "音乐库"),
                        onSelect = { state.launchBehavior = it },
                    )
                    DropdownSettingItem(
                        label = "链接处理",
                        value = state.linkHandling,
                        options = listOf("内置浏览器", "系统浏览器"),
                        onSelect = { state.linkHandling = it },
                        showDivider = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("播放") {
                    DropdownSettingItem(
                        label = "音质选择",
                        value = state.audioQuality,
                        options = listOf("标准", "高品", "无损"),
                        onSelect = { state.audioQuality = it },
                    )
                    ToggleSettingItem(
                        label = "自动混音",
                        checked = state.autoMix,
                        onCheckedChange = { state.autoMix = it },
                    )
                    SliderSettingItem(
                        label = "交叉淡化",
                        value = state.crossfadeSeconds,
                        valueRange = 0f..10f,
                        displayValue = if (state.crossfadeSeconds == 0f) "关闭" else "${state.crossfadeSeconds.toInt()}秒",
                        onValueChange = { state.crossfadeSeconds = it },
                        showDivider = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("歌词") {
                    ToggleSettingItem(
                        label = "翻译",
                        checked = state.translation,
                        onCheckedChange = { state.translation = it },
                    )
                    ToggleSettingItem(
                        label = "音译",
                        checked = state.romanization,
                        onCheckedChange = { state.romanization = it },
                    )
                    ToggleSettingItem(
                        label = "逐字歌词",
                        checked = state.wordByWord,
                        onCheckedChange = { state.wordByWord = it },
                        showDivider = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("外观") {
                    DropdownSettingItem(
                        label = "背景模式",
                        value = state.backgroundMode,
                        options = listOf("模糊", "渐变", "纯色"),
                        onSelect = { state.backgroundMode = it },
                    )
                    ToggleSettingItem(
                        label = "封面动画",
                        checked = state.coverAnimation,
                        onCheckedChange = { state.coverAnimation = it },
                    )
                    ToggleSettingItem(
                        label = "保持屏幕常亮",
                        checked = state.keepScreenOn,
                        onCheckedChange = { state.keepScreenOn = it },
                        enabled = false,
                        showDivider = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("存储") {
                    DisplaySettingItem(
                        label = "缓存大小",
                        value = state.cacheSize,
                    )
                    ClickableSettingItem(
                        label = "清除缓存",
                        onClick = { state.cacheSize = "0 B" },
                        showDivider = false,
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(24.dp)) }
            item {
                SettingsGroup("关于") {
                    DisplaySettingItem(label = "版本", value = "1.0.0")
                    ClickableSettingItem(label = "项目主页", showDivider = false)
                }
            }
        }
    }
}

// ── Top bar ──

@Composable
private fun SettingsTopBar() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MeloXColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "设置",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Search ──

@Composable
private fun SearchBar(state: SettingsState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "搜索设置",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MeloXColors.Primary.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = MeloXColors.Primary,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
    }
}

// ── Account section ──

@Composable
private fun AccountSection(state: SettingsState) {
    SettingsGroup(title = null) {
        if (!state.isLoggedIn) {
            ClickableSettingItem(
                label = "登录账号",
                subtitle = "登录以同步音乐和收藏",
                trailingIcon = "→",
                onClick = { state.isLoggedIn = true },
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .hoverable(remember { MutableInteractionSource() })
                    .clickable { state.isLoggedIn = false }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MeloXColors.Primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = state.username.firstOrNull()?.toString() ?: "U",
                        color = MeloXColors.OnPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MeloX 用户",
                        color = MeloXColors.TextPrimary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = "点击退出登录",
                        color = MeloXColors.TextTertiary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    text = "→",
                    color = MeloXColors.TextTertiary,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

// ── Section group container ──

@Composable
private fun SettingsGroup(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        if (title != null) {
            Text(
                text = title,
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MeloXColors.SurfaceVariant),
        ) {
            content()
        }
    }
}

// ── Toggle item ──

@Composable
private fun ToggleSettingItem(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(enabled = enabled, interactionSource = interactionSource, indication = null) {
                    onCheckedChange(!checked)
                }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = if (enabled) MeloXColors.TextPrimary else MeloXColors.TextTertiary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MeloXColors.OnPrimary,
                    checkedTrackColor = MeloXColors.Primary,
                    uncheckedThumbColor = MeloXColors.TextSecondary,
                    uncheckedTrackColor = MeloXColors.SurfaceHigh,
                    disabledCheckedThumbColor = MeloXColors.OnPrimary.copy(alpha = 0.5f),
                    disabledCheckedTrackColor = MeloXColors.Primary.copy(alpha = 0.5f),
                    disabledUncheckedThumbColor = MeloXColors.TextTertiary,
                    disabledUncheckedTrackColor = MeloXColors.SurfaceHigh.copy(alpha = 0.5f),
                ),
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MeloXColors.Outline.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )
        }
    }
}

// ── Dropdown item ──

@Composable
private fun DropdownSettingItem(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    showDivider: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null) { expanded = true }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                color = MeloXColors.TextTertiary,
                fontSize = 16.sp,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MeloXColors.Outline.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )
        }

    DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MeloXColors.SurfaceVariant, RoundedCornerShape(10.dp)),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option,
                            color = if (option == value) MeloXColors.Primary else MeloXColors.TextPrimary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 14.sp,
                            fontWeight = if (option == value) FontWeight.Medium else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    trailingIcon = if (option == value) {
                        {
                            Text("✓", color = MeloXColors.Primary, fontSize = 14.sp)
                        }
                    } else null,
                )
            }
        }
    }
}

// ── Slider item ──

@Composable
private fun SliderSettingItem(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit,
    showDivider: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = MeloXColors.TextPrimary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = displayValue,
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 14.sp,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MeloXColors.Primary,
                activeTrackColor = MeloXColors.Primary,
                inactiveTrackColor = MeloXColors.SurfaceHigh,
            ),
        )
        if (showDivider) {
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
    if (showDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(start = 20.dp),
            color = MeloXColors.Outline.copy(alpha = 0.3f),
            thickness = 0.5.dp,
        )
    }
}

// ── Display-only item ──

@Composable
private fun DisplaySettingItem(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MeloXColors.TextSecondary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 14.sp,
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        color = MeloXColors.Outline.copy(alpha = 0.3f),
        thickness = 0.5.dp,
    )
}

// ── Clickable item ──

@Composable
private fun ClickableSettingItem(
    label: String,
    subtitle: String? = null,
    trailingIcon: String = "›",
    onClick: () -> Unit = {},
    showDivider: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 15.sp,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = MeloXColors.TextTertiary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 12.sp,
                    )
                }
            }
            Text(
                text = trailingIcon,
                color = MeloXColors.TextTertiary,
                fontSize = 16.sp,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 20.dp),
                color = MeloXColors.Outline.copy(alpha = 0.3f),
                thickness = 0.5.dp,
            )
        }
    }
}
