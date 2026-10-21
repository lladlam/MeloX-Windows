package melox.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.theme.MeloXTypography

@Composable
fun MeloXIosTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val desktop = melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled
    val resolvedPadding = if (desktop) PaddingValues(horizontal = 34.dp) else contentPadding
    Column(modifier = modifier.fillMaxWidth().padding(resolvedPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (desktop) 52.dp else if (onBack != null) 56.dp else 44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronLeft,
                    modifier = Modifier
                        .size(if (desktop) 34.dp else 44.dp)
                        .clickable(onClick = onBack),
                    color = if (desktop) MaterialTheme.colorScheme.onBackground.copy(alpha = .72f) else MeloXColors.Primary,
                    size = if (desktop) 18 else 24,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (onBack != null) Text(
                text = title,
                style = if (desktop) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else MeloXTypography.largeTitle,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            ) else Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
        if (onBack == null) Text(
            title, style = if (desktop) MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold) else MeloXTypography.largeTitle,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MeloXIosGroupedList(
    modifier: Modifier = Modifier,
    surfaceColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (melox.ui.desktop.LocalDesktopSettingsPresentation.current) {
        Column(modifier) { melox.ui.desktop.DesktopSettingsForm(content) }
        return
    }
    val desktop = melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(if (desktop) 10.dp else 28.dp)).background(surfaceColor), content = content)
}

@Composable
fun MeloXIosListRow(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    leadingIcon: MeloXSymbol? = null,
    leadingIconColor: Color = MeloXColors.Primary,
    trailingContent: @Composable (() -> Unit)? = null,
    showChevron: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    detail: String? = null,
    chevronTint: Color = melox.ui.glass.MeloXSystemColors.Red.copy(alpha = 0.88f),
    showTopSeparator: Boolean = false,
) {
    if (melox.ui.desktop.LocalDesktopSettingsPresentation.current) {
        melox.ui.desktop.DesktopSettingsRow(modifier, title, subtitle, leadingIcon, leadingIconColor,
            trailingContent, showChevron, onClick, leading, detail)
        return
    }
    val desktop = melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val separator = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (desktop) { if (subtitle != null) 58.dp else 48.dp } else if (subtitle != null) 68.dp else 56.dp)
            .drawBehind {
                if (showTopSeparator) drawLine(
                    color = separator,
                    start = Offset(16.dp.toPx(), 0f),
                    end = Offset(size.width - 16.dp.toPx(), 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .hoverable(interactionSource)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                else Modifier
            )
            .background(
                if (isHovered) Color.White.copy(alpha = 0.05f) else Color.Transparent
            )
            .padding(horizontal = if (desktop) 13.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        } else if (leadingIcon != null) {
            MeloXSymbolIcon(symbol = leadingIcon, color = leadingIconColor, size = 22)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = if (desktop) MaterialTheme.typography.bodyMedium else MeloXTypography.body,
                color = MaterialTheme.colorScheme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = if (desktop) MaterialTheme.typography.bodySmall else MeloXTypography.subheadline,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f),
                    maxLines = 2,
                )
            }
        }
        if (detail != null) Text(detail, style = MeloXTypography.subheadline, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.52f))
        if (trailingContent != null) {
            trailingContent()
        } else if (showChevron && onClick != null) {
            Spacer(Modifier.width(8.dp))
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronRight,
                 color = chevronTint,
                 size = 20,
                 iconSize = 19,
            )
        }
    }
}

@Composable
fun MeloXSegmentedControl(
    selectedIndex: Int,
    options: List<String>,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(MeloXGlass.capsuleShape)
            .background(MeloXColors.OnBackground.copy(alpha = 0.055f))
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(MeloXGlass.capsuleShape)
                        .background(
                            if (isSelected) MeloXColors.Primary
                            else Color.Transparent
                        )
                        .clickable { onSelected(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MeloXTypography.subheadline.copy(
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        color = if (isSelected) Color.White else MeloXColors.OnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun MeloXGlassToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MeloXColors.ToggleAccentDark,
    trackColor: Color = MeloXColors.ToggleTrackDark,
) {
    Box(
        modifier = modifier
            .size(width = 51.dp, height = 31.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) accentColor else trackColor)
            .clickable { onCheckedChange(!checked) },
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(27.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .shadow(2.dp, RoundedCornerShape(50))
                .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart),
        )
    }
}

@Composable
fun MeloXGlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        placeholder = {
            Text(
                text = placeholder,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurfaceVariant,
            )
        },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        textStyle = MeloXTypography.body.copy(color = MeloXColors.OnSurface),
        shape = MeloXGlass.capsuleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MeloXColors.OnBackground.copy(alpha = 0.055f),
            unfocusedContainerColor = MeloXColors.OnBackground.copy(alpha = 0.055f),
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            cursorColor = MeloXColors.Primary,
        ),
        singleLine = true,
    )
}
