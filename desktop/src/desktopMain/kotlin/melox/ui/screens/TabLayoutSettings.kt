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
import melox.ui.legal.pinnedString

internal fun layoutPageTitle(page: String): String = pinnedString("settings_tabs_" + when (page) {
    "Playlists" -> "playlist_page"; "QuickActions" -> "shortcuts"; "NewSongs" -> "new_songs"; else -> page.lowercase()
})

@Composable
internal fun TabLayoutSettings(source: MusicSource) {
    val runtime = MeloXSettingsRuntime
    LayoutGroup(topSpacing = false) {
        PreferenceToggle(layoutPageTitle("Home"), runtime.homeTabEnabled, "tab_home")
        PreferenceToggle(layoutPageTitle("Explore"), runtime.exploreTabEnabled, "tab_explore", showTopSeparator = true)
        PreferenceToggle(layoutPageTitle("Library"), runtime.libraryTabEnabled, "tab_library", showTopSeparator = true)
    }
    listOf(
        Triple(layoutPageTitle("Podcasts"), "podcasts", runtime.podcastsEnabled),
        Triple(layoutPageTitle("Downloads"), "downloads", runtime.downloadsEnabled),
        Triple(layoutPageTitle("Cloud"), "cloud", runtime.cloudMusicEnabled),
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
            PreferenceToggle(pinnedString("settings_tabs_home_shortcut"), home,
                "placement_${key}_home")
            PreferenceToggle(pinnedString("settings_tabs_library_page"), library,
                "placement_${key}_library", showTopSeparator = true)
            PreferenceToggle(pinnedString("settings_tabs_own_tab"), tab,
                "placement_${key}_tab", showTopSeparator = true)
        }
    }
    LayoutOrderGroup(pinnedString("settings_tabs_order"), "tab_order", runtime.tabOrder)
    LayoutGroup(Modifier.testTag("layout-home_section_order").semantics {
        stateDescription = runtime.homeSectionOrder.joinToString(">")
    }) {
        LayoutGroupTitle(pinnedString("settings_tabs_home_sections"))
        PreferenceToggle(pinnedString("settings_tabs_shortcuts"), runtime.homeQuickActionsEnabled, "home_quick_actions")
        PreferenceToggle(pinnedString("settings_tabs_playlists"), runtime.homePlaylistsEnabled, "home_playlists", showTopSeparator = true)
        PreferenceToggle(pinnedString("settings_tabs_new_songs"), runtime.homeNewSongsEnabled, "home_new_songs", showTopSeparator = true)
        PreferenceToggle(pinnedString("settings_tabs_remember_library"), runtime.rememberLibraryPage, "library_remember_page", showTopSeparator = true)
        LayoutOrderRows("home_section_order", runtime.homeSectionOrder)
    }
    val pages = MeloXContentLayout.libraryPages(source)
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
    MeloXSettingsDropdown(pinnedString("settings_tabs_default_library"), runtime.defaultLibraryPage.takeIf { it in pages } ?: pages.first(),
        pages.map { it to layoutPageTitle(it) },
        onSelected = { MeloXSettingsPreferences.setString("library_default_page", it) },
        grouped = true)
    Text(pinnedString("settings_tabs_search_note"), Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f), fontSize = 12.sp)
    }
}

@Composable
private fun LayoutGroup(modifier: Modifier = Modifier, topSpacing: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    if (topSpacing) Spacer(Modifier.height(10.dp))
    MeloXIosGroupedList(modifier, surfaceColor = MaterialTheme.colorScheme.surface, content = content)
}

@Composable
private fun LayoutGroupTitle(title: String) {
    Text(title, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
}

@Composable
private fun LayoutOrderGroup(title: String, key: String, order: List<String>) {
    Spacer(Modifier.height(10.dp))
    MeloXIosGroupedList(Modifier
        .testTag("layout-$key").semantics { stateDescription = order.joinToString(">") }) {
        LayoutGroupTitle(title)
        LayoutOrderRows(key, order)
    }
}

@Composable
private fun LayoutOrderRows(key: String, order: List<String>) {
    order.forEachIndexed { index, page ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (key == "tab_order") 10.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (key == "home_section_order" && page == "Playlists") pinnedString("settings_tabs_playlists") else layoutPageTitle(page), Modifier.weight(1f))
                listOf(-1 to "↑", 1 to "↓").forEach { (direction, label) ->
                    val target = index + direction
                    val enabled = target in order.indices
                    // Pinned Android draws these with SF Symbols, not text arrows.
                    MeloXActionIcon(label, Modifier.size(18.dp)
                        .testTag("layout-$key-$page-${if (direction < 0) "up" else "down"}")
                        .semantics { contentDescription = "${layoutPageTitle(page)}${if (direction < 0) "上移" else "下移"}" }
                        .clickable(enabled = enabled, role = Role.Button) {
                            val moved = order.toMutableList().apply { add(target, removeAt(index)) }
                            MeloXSettingsPreferences.setString(key, moved.joinToString(","))
                        }.padding(10.dp),
                        MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else .25f))
                }
            }
    }
}
