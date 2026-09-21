package melox.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.ui.foundation.MeloXGlass
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.glassSurface
import melox.ui.theme.MeloXColors

private data class BottomBarTab(
    val tab: AppTab,
    val symbol: MeloXSymbol,
    val label: String,
)

private val barTabs = listOf(
    BottomBarTab(AppTab.Home, MeloXSymbol.Home, "首页"),
    BottomBarTab(AppTab.Explore, MeloXSymbol.Explore, "发现"),
    BottomBarTab(AppTab.Library, MeloXSymbol.Library, "资料库"),
    BottomBarTab(AppTab.Settings, MeloXSymbol.Settings, "设置"),
)

@Composable
fun MeloXBottomBar(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            barTabs.forEach { item ->
                BottomBarTabItem(
                    item = item,
                    isSelected = navState.selectedTab == item.tab,
                    onClick = { navState.navigateToTab(item.tab) },
                )
            }
        }

        SearchButton(
            isSelected = navState.selectedTab == AppTab.Search,
            onClick = { navState.navigateToTab(AppTab.Search) },
        )
    }
}

@Composable
private fun BottomBarTabItem(
    item: BottomBarTab,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val animatedSelectionProgress by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 380f,
        ),
        label = "tab_selection",
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> MeloXColors.Primary
            isHovered -> MeloXColors.OnSurface
            else -> MeloXColors.OnSurfaceVariant
        },
        animationSpec = tween(180),
        label = "tab_icon_color",
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isSelected -> MeloXColors.OnSurface
            isHovered -> MeloXColors.OnSurface
            else -> MeloXColors.OnSurfaceVariant
        },
        animationSpec = tween(180),
        label = "tab_label_color",
    )

    val selectionBackground = MeloXColors.Primary.copy(alpha = 0.12f * animatedSelectionProgress)

    Box(
        modifier = Modifier
            .height(44.dp)
            .width(64.dp)
            .clip(MeloXGlass.capsuleShape)
            .background(selectionBackground)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp),
        ) {
            MeloXSymbolIcon(
                symbol = item.symbol,
                color = iconColor,
                size = 22,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier.widthIn(max = (48 * animatedSelectionProgress).dp),
            ) {
                if (animatedSelectionProgress > 0.01f) {
                    androidx.compose.material3.Text(
                        text = item.label,
                        color = labelColor,
                        fontSize = 12.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchButton(
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected -> MeloXColors.Primary.copy(alpha = 0.18f)
            isHovered -> MeloXGlass.surfaceHighColor
            else -> MeloXGlass.surfaceColor
        },
        animationSpec = tween(180),
        label = "search_bg",
    )

    val iconColor by animateColorAsState(
        targetValue = when {
            isSelected -> MeloXColors.Primary
            isHovered -> MeloXColors.OnSurface
            else -> MeloXColors.OnSurfaceVariant
        },
        animationSpec = tween(180),
        label = "search_icon_color",
    )

    Box(
        modifier = Modifier
            .size(height = 44.dp, width = 64.dp)
            .glassSurface(
                shape = MeloXGlass.capsuleShape,
                backgroundColor = backgroundColor,
            )
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MeloXSymbolIcon(
            symbol = MeloXSymbol.Search,
            color = iconColor,
            size = 22,
        )
    }
}
