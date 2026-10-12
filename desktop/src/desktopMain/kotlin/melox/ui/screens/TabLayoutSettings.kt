package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicSource
import melox.settings.MeloXContentLayout
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXIosGroupedList
import melox.ui.glass.MeloXSettingsDropdown

internal fun layoutPageTitle(page: String): String = when (page) {
    "Home" -> "首页"; "Explore" -> "发现"; "Library" -> "音乐库"; "Settings" -> "设置"
    "Podcasts" -> "播客"; "Downloads" -> "下载"; "Cloud" -> "云盘"; "History" -> "最近播放"
    "Songs" -> "歌曲"; "Playlists" -> "歌单"; "QuickActions" -> "快捷入口"; "NewSongs" -> "推荐新歌"
    else -> page
}

@Composable
internal fun TabLayoutSettings(source: MusicSource) {
    val runtime = MeloXSettingsRuntime
    LayoutGroup {
        PreferenceToggle("首页", runtime.homeTabEnabled, "tab_home")
        PreferenceToggle("发现", runtime.exploreTabEnabled, "tab_explore", showTopSeparator = true)
        PreferenceToggle("音乐库", runtime.libraryTabEnabled, "tab_library", showTopSeparator = true)
    }
    listOf(
        Triple("播客", "podcasts", runtime.podcastsEnabled),
        Triple("下载", "downloads", runtime.downloadsEnabled),
        Triple("云盘", "cloud", runtime.cloudMusicEnabled),
    ).forEach { (title, key, enabled) ->
        val home = when (key) {
            "podcasts" -> runtime.podcastsHomePlacement
            "downloads" -> runtime.downloadsHomePlacement
            else -> runtime.cloudHomePlacement
        }
        val library = when (key) {
            "podcasts" -> runtime.podcastsLibraryPlacement
            "downloads" -> runtime.downloadsLibraryPlacement
            else -> runtime.cloudLibraryPlacement
        }
        val tab = when (key) {
            "podcasts" -> runtime.podcastsTabPlacement
            "downloads" -> runtime.downloadsTabPlacement
            else -> runtime.cloudTabPlacement
        }
        LayoutGroup {
            LayoutGroupTitle(title)
            PreferenceToggle("首页快捷入口", home,
                "placement_${key}_home", if (enabled) null else "请先在功能模块中启用$title")
            PreferenceToggle("音乐库子页", library,
                "placement_${key}_library", showTopSeparator = true)
            PreferenceToggle("独立标签页", tab,
                "placement_${key}_tab", showTopSeparator = true)
        }
    }
    LayoutOrderGroup("标签栏顺序", "tab_order", runtime.tabOrder)
    LayoutGroup(Modifier.testTag("layout-home_section_order").semantics {
        stateDescription = runtime.homeSectionOrder.joinToString(">")
    }) {
        LayoutGroupTitle("首页区块")
        PreferenceToggle("快捷入口", runtime.homeQuickActionsEnabled, "home_quick_actions")
        PreferenceToggle("推荐歌单", runtime.homePlaylistsEnabled, "home_playlists", showTopSeparator = true)
        PreferenceToggle("推荐新歌", runtime.homeNewSongsEnabled, "home_new_songs", showTopSeparator = true)
        PreferenceToggle("记住音乐库子页面", runtime.rememberLibraryPage, "library_remember_page", showTopSeparator = true)
        LayoutOrderRows("home_section_order", runtime.homeSectionOrder)
    }
    val pages = MeloXContentLayout.libraryPages(source)
    MeloXSettingsDropdown("音乐库默认页", runtime.defaultLibraryPage.takeIf { it in pages } ?: pages.first(),
        pages.map { it to layoutPageTitle(it) },
        onSelected = { MeloXSettingsPreferences.setString("library_default_page", it) },
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
    Text("搜索保持为独立的右侧 Liquid Glass 按钮。\n关闭记住子页面时，音乐库使用默认页。", Modifier.padding(horizontal = 36.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f), fontSize = 12.sp)
}

@Composable
private fun LayoutGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    MeloXIosGroupedList(modifier.padding(horizontal = 20.dp, vertical = 5.dp), content = content)
}

@Composable
private fun LayoutGroupTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 18.dp, vertical = 8.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
}

@Composable
private fun LayoutOrderGroup(title: String, key: String, order: List<String>) {
    MeloXIosGroupedList(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
        .testTag("layout-$key").semantics { stateDescription = order.joinToString(">") }) {
        LayoutGroupTitle(title)
        LayoutOrderRows(key, order)
    }
}

@Composable
private fun LayoutOrderRows(key: String, order: List<String>) {
    order.forEachIndexed { index, page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (key == "home_section_order" && page == "Playlists") "推荐歌单" else layoutPageTitle(page), Modifier.weight(1f))
                listOf(-1 to "↑", 1 to "↓").forEach { (direction, label) ->
                    val target = index + direction
                    val enabled = target in order.indices
                    Text(label, Modifier.testTag("layout-$key-$page-${if (direction < 0) "up" else "down"}")
                        .semantics { contentDescription = "${layoutPageTitle(page)}${if (direction < 0) "上移" else "下移"}" }
                        .clickable(enabled = enabled, role = Role.Button) {
                            val moved = order.toMutableList().apply { add(target, removeAt(index)) }
                            MeloXSettingsPreferences.setString(key, moved.joinToString(","))
                        }.padding(horizontal = 12.dp, vertical = 10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .25f))
                }
            }
    }
}
