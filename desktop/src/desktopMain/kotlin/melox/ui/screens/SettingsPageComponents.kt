package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import melox.settings.MeloXSettingsPreferences
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.foundation.MeloXIosListRow
import melox.ui.glass.*
import melox.ui.legal.pinnedString

@Composable
internal fun SettingsGlassGroup(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalSettingsGroupRowIndex provides mutableIntStateOf(0)) {
        MeloXIosGroupedList(surfaceColor = MaterialTheme.colorScheme.surface, content = content)
    }
}

@Composable
internal fun SettingsToggleRow(title: String, key: String, default: Boolean, enabled: Boolean = true, subtitle: String? = null) {
    var value by remember(key) { mutableStateOf(MeloXSettingsPreferences.boolean(key, default)) }
    SettingsExternalToggleRow(pinnedString(title), value, "setting-$key", enabled, subtitle) {
        value = it
        MeloXSettingsPreferences.setBoolean(key, it)
    }
}

@Composable
internal fun SettingsExternalToggleRow(title: String, value: Boolean, tag: String, enabled: Boolean = true, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    MeloXIosListRow(title = title, subtitle = subtitle, showChevron = false,
        showTopSeparator = LocalSettingsGroupRowIndex.current.intValue++ > 0,
        trailingContent = { MeloXGlassToggle(value, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.testTag(tag)) })
}

@Composable
internal fun <T> SettingsChoice(title: String, key: String, value: T, items: List<Pair<T, String>>, enabled: Boolean = true, onChange: (T) -> Unit) {
    Box(Modifier.testTag(key)) {
        MeloXSettingsDropdown(pinnedString(title), value, items, onChange, grouped = true, enabled = enabled)
    }
}

@Composable
internal fun SettingsChoiceRow(title: String, selected: Boolean, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().testTag(tag).semantics { this.selected = selected }
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SettingsInfoCard(value: String, modifier: Modifier = Modifier) {
    SettingsGlassGroup {
        Text(value, modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
    }
}

/** Platform availability is explicit. Unimplemented adapters must never persist a successful enable. */
@Composable
internal fun SettingsPlatformNote(vararg features: String) {
    val locale = java.util.Locale.getDefault()
    val available = when (locale.language) {
        "zh" -> "当前桌面端尚未实现：${features.joinToString("、")}。"
        "ja" -> "このデスクトップ版では未対応：${features.joinToString("、")}。"
        else -> "Not available in this desktop build: ${features.joinToString(", ")}."
    }
    Text(available,
        Modifier.padding(top = 10.dp).testTag("settings-platform-availability"), fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
}
