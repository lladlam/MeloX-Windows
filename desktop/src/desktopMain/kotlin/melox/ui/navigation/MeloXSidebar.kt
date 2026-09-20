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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

private data class SidebarNavItem(
    val tab: AppTab,
    val icon: String,
    val label: String,
)

private val sidebarItems = listOf(
    SidebarNavItem(AppTab.Home, "✦", "发现"),
    SidebarNavItem(AppTab.Explore, "◈", "探索"),
    SidebarNavItem(AppTab.Library, "♫", "音乐库"),
    SidebarNavItem(AppTab.Podcasts, "◉", "播客"),
    SidebarNavItem(AppTab.Downloads, "↓", "下载"),
    SidebarNavItem(AppTab.Cloud, "☁", "云盘"),
    SidebarNavItem(AppTab.Settings, "⚙", "设置"),
)

@Composable
fun MeloXSidebar(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(200.dp)
            .background(MeloXColors.SidebarBackground)
            .padding(top = 16.dp),
    ) {
        // App title
        Text(
            text = "MeloX",
            color = MeloXColors.Primary,
            fontFamily = MeloXLanTingProFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation items
        sidebarItems.forEach { item ->
            SidebarNavItemView(
                item = item,
                isSelected = navState.selectedTab == item.tab,
                onClick = { navState.navigateToTab(item.tab) },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Search item at bottom of nav section
        SidebarNavItemView(
            item = SidebarNavItem(AppTab.Search, "⌕", "搜索"),
            isSelected = navState.selectedTab == AppTab.Search,
            onClick = { navState.navigateToTab(AppTab.Search) },
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Mini player at bottom
        MiniPlayerSection()
    }
}

@Composable
private fun SidebarNavItemView(
    item: SidebarNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isSelected -> MeloXColors.SidebarItemSelected
        isHovered -> MeloXColors.SidebarItemHover
        else -> MeloXColors.SidebarBackground
    }

    val textColor = when {
        isSelected -> MeloXColors.OnSurface
        isHovered -> MeloXColors.OnSurface
        else -> MeloXColors.OnSurfaceVariant
    }

    val iconColor = when {
        isSelected -> MeloXColors.Primary
        isHovered -> MeloXColors.OnSurface
        else -> MeloXColors.OnSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.icon,
            color = iconColor,
            fontSize = 16.sp,
            modifier = Modifier.width(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = item.label,
            color = textColor,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 14.sp,
            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun MiniPlayerSection() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = MeloXColors.MiniPlayerBackground,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Placeholder album art
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MeloXColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text("♫", color = MeloXColors.OnSurfaceVariant, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "未在播放",
                    color = MeloXColors.OnSurface,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "选择一首歌开始",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}
