package melox.ui.navigation

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private data class BottomNavItem(
    val tab: AppTab,
    val icon: String,
    val label: String,
)

private val bottomNavItems = listOf(
    BottomNavItem(AppTab.Home, "✦", "发现"),
    BottomNavItem(AppTab.Explore, "◈", "探索"),
    BottomNavItem(AppTab.Library, "♫", "音乐库"),
    BottomNavItem(AppTab.Downloads, "↓", "下载"),
    BottomNavItem(AppTab.Search, "⌕", "搜索"),
)

@Composable
fun MeloXBottomBar(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MeloXColors.SidebarBackground,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            bottomNavItems.forEach { item ->
                BottomNavItemView(
                    item = item,
                    isSelected = navState.selectedTab == item.tab,
                    onClick = { navState.navigateToTab(item.tab) },
                )
            }
        }
    }
}

@Composable
private fun BottomNavItemView(
    item: BottomNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textColor = when {
        isSelected -> MeloXColors.Primary
        isHovered -> MeloXColors.OnSurface
        else -> MeloXColors.OnSurfaceVariant
    }

    val bgColor = when {
        isSelected -> MeloXColors.SidebarItemSelected
        isHovered -> MeloXColors.SidebarItemHover
        else -> MeloXColors.SidebarBackground
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = item.icon,
            color = textColor,
            fontSize = 18.sp,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = item.label,
            color = textColor,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
