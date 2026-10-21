package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.round

internal val LocalDesktopSettingsPresentation = staticCompositionLocalOf { false }

/** Mac Form(.columns) controls. The phone grouped-list and glass-switch are separate. */
@Composable
internal fun DesktopSettingsForm(content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(
        fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.fillMaxWidth().testTag("desktop-settings-form"), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun DesktopSettingsCheckbox(title: String, value: Boolean, tag: String, enabled: Boolean = true,
    subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 26.dp).testTag(tag)
            .toggleable(value, enabled, Role.Checkbox, onChange), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            val shape = RoundedCornerShape(3.dp)
            Box(Modifier.size(14.dp).clip(shape).background(if (value) Color(0xFFDC0011).copy(alpha = if (enabled) 1f else .4f) else MaterialTheme.colorScheme.surface)
                .border(.65.dp, color.copy(alpha = .4f), shape), contentAlignment = Alignment.Center) {
                if (value) MeloXSymbolIcon(MeloXSymbol.Checkmark, size = 10, weight = FontWeight.Bold, color = Color.White)
            }
            Text(title, color = color, fontSize = 13.sp)
        }
        subtitle?.let { Text(it, Modifier.padding(start = 21.dp), color = color.copy(alpha = .55f), fontSize = 11.sp, lineHeight = 14.sp) }
    }
}

@Composable
internal fun <T> DesktopSettingsPicker(title: String, value: T, items: List<Pair<T, String>>, tag: String,
    enabled: Boolean = true, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
        Box(Modifier.widthIn(min = 140.dp, max = 240.dp)) {
            Row(Modifier.height(23.dp).clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .065f))
                .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f), RoundedCornerShape(5.dp))
                .testTag(tag).clickable(enabled = enabled, role = Role.Button) { expanded = true }
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(items.firstOrNull { it.first == value }?.second.orEmpty(), Modifier.weight(1f), fontSize = 13.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .4f))
                MeloXSymbolIcon(MeloXSymbol.ChevronUpDown, size = 9)
            }
            if (expanded) Popup(alignment = Alignment.TopEnd, onDismissRequest = { expanded = false }, properties = PopupProperties(focusable = true)) {
                Surface(Modifier.widthIn(min = 180.dp, max = 300.dp).testTag("desktop-settings-picker-menu"),
                    shape = RoundedCornerShape(6.dp), shadowElevation = 8.dp,
                    border = androidx.compose.foundation.BorderStroke(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f))) {
                    Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()).padding(5.dp)) {
                        items.forEachIndexed { index, (option, label) ->
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            Row(Modifier.fillMaxWidth().heightIn(min = 24.dp).clip(RoundedCornerShape(4.dp))
                                .background(if (hovered) Color(0xFFDC0011) else Color.Transparent)
                                .testTag("settings-option:$label").semantics { selected = option == value }
                                .hoverable(interaction).clickable(role = Role.RadioButton) { onChange(option); expanded = false }
                                .padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Box(Modifier.width(12.dp)) { if (option == value) MeloXSymbolIcon(MeloXSymbol.Checkmark, size = 10, color = if (hovered) Color.White else MaterialTheme.colorScheme.onSurface) }
                                Text(label, fontSize = 13.sp, color = if (hovered) Color.White else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DesktopSettingsChoice(title: String, selected: Boolean, tag: String, enabled: Boolean, onClick: () -> Unit) {
    DesktopSettingsCheckbox(title, selected, tag, enabled, onChange = { onClick() })
}

@Composable
internal fun DesktopSettingsButton(title: String, modifier: Modifier = Modifier, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(modifier.heightIn(min = 23.dp).clip(RoundedCornerShape(5.dp))
        .background(colors.onSurface.copy(alpha = .065f)).border(.5.dp, colors.onSurface.copy(alpha = .18f), RoundedCornerShape(5.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 9.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        Text(title, fontSize = 13.sp, lineHeight = 15.sp, fontFamily = MeloXDesktopTextFontFamily,
            color = (if (destructive) colors.error else colors.onSurface).copy(alpha = if (enabled) 1f else .4f))
    }
}

@Composable
internal fun DesktopSettingsRow(modifier: Modifier, title: String, subtitle: String?, leadingIcon: MeloXSymbol?,
    leadingIconColor: Color, trailingContent: (@Composable () -> Unit)?, showChevron: Boolean,
    onClick: (() -> Unit)?, leading: (@Composable () -> Unit)?, detail: String?) {
    Row(modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 28.dp else 42.dp)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        if (leading != null) leading() else if (leadingIcon != null) MeloXSymbolIcon(leadingIcon, size = 14, color = leadingIconColor)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = 13.sp)
            subtitle?.let { Text(it, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
        }
        detail?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) }
        if (trailingContent != null) trailingContent() else if (showChevron && onClick != null) MeloXSymbolIcon(MeloXSymbol.ChevronRight, size = 9)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun DesktopSettingsSlider(value: Float, onChange: (Float) -> Unit, onTransient: (Float) -> Unit,
    range: ClosedFloatingPointRange<Float>, step: Float, modifier: Modifier, description: String,
    onFinished: () -> Unit, enabled: Boolean) {
    var transient by remember { mutableStateOf<Float?>(null) }
    val shown = transient ?: value
    fun snap(v: Float) = (if (step > 0f) range.start + round((v - range.start) / step) * step else v).coerceIn(range)
    Slider(shown, onValueChange = { transient = snap(it); onTransient(snap(it)) }, modifier = modifier.height(24.dp).semantics { contentDescription = description },
        enabled = enabled, valueRange = range,
        steps = if (step > 0) ((range.endInclusive - range.start) / step).toInt().minus(1).coerceAtLeast(0) else 0,
        onValueChangeFinished = { transient?.let(onChange); transient = null; onFinished() },
        thumb = {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surface)
                .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .35f), RoundedCornerShape(6.dp)))
        }, track = {
            val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = .18f)
            Canvas(Modifier.fillMaxWidth().height(3.dp)) {
                drawRoundRect(trackColor, cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                drawRoundRect(Color(0xFFDC0011).copy(alpha = if (enabled) 1f else .4f),
                    size = androidx.compose.ui.geometry.Size(size.width * ((shown - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f), size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
            }
        })
}
