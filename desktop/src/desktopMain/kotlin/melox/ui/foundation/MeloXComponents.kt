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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import melox.ui.theme.MeloXTypography

@Composable
fun MeloXIosTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (onBack != null) 56.dp else 44.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                MeloXSymbolIcon(
                    symbol = MeloXSymbol.ChevronLeft,
                    modifier = Modifier
                        .size(44.dp)
                        .clickable(onClick = onBack),
                    color = MeloXColors.Primary,
                    size = 24,
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                style = MeloXTypography.largeTitle,
                color = MeloXColors.OnBackground,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions()
            }
        }
    }
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
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (subtitle != null) 68.dp else 56.dp)
            .hoverable(interactionSource)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                else Modifier
            )
            .background(
                if (isHovered) Color.White.copy(alpha = 0.05f) else Color.Transparent
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            MeloXSymbolIcon(symbol = leadingIcon, color = leadingIconColor, size = 22)
            Spacer(modifier = Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MeloXTypography.body,
                color = MeloXColors.OnSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MeloXTypography.subheadline,
                    color = MeloXColors.OnSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        if (trailingContent != null) {
            trailingContent()
        } else if (showChevron && onClick != null) {
            MeloXSymbolIcon(
                symbol = MeloXSymbol.ChevronRight,
                color = MeloXColors.Primary.copy(alpha = 0.88f),
                size = 20,
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
