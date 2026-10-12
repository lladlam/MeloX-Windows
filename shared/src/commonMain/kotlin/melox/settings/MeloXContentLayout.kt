package melox.settings

import melox.music.model.MusicSource

/** Shared presentation policy for the settings controls and their actual destinations.
 * Preference names and serialized page identities match the pinned Android source.
 */
object MeloXContentLayout {
    val tabs = listOf("Home", "Explore", "Library", "Podcasts", "Downloads", "Cloud", "Settings")
    val homeSections = listOf("QuickActions", "Playlists", "NewSongs")

    fun visibleTabs(source: MusicSource): List<String> {
        val order = if (source == MusicSource.Bilibili) listOf("Library", "Settings") else MeloXSettingsRuntime.tabOrder
        return order.filter { page ->
            when (page) {
                "Home" -> MeloXSettingsRuntime.homeTabEnabled
                "Explore" -> MeloXSettingsRuntime.exploreTabEnabled
                "Library" -> MeloXSettingsRuntime.libraryTabEnabled
                "Podcasts" -> MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsTabPlacement
                "Downloads" -> MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsTabPlacement
                "Cloud" -> MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudTabPlacement
                "Settings" -> true
                else -> false
            }
        }.let { (it + "Settings").distinct() }
    }

    /** A module's shortcut can open its real page without adding it to the dock. */
    fun rootPageAvailable(source: MusicSource, page: String): Boolean = page in visibleTabs(source) || when (page) {
        "Search" -> true
        "Cloud" -> source == MusicSource.Netease && MeloXSettingsRuntime.cloudMusicEnabled
        "Podcasts" -> source == MusicSource.Netease && MeloXSettingsRuntime.podcastsEnabled
        "Downloads" -> MeloXSettingsRuntime.downloadsEnabled
        else -> false
    }

    fun libraryPages(source: MusicSource): List<String> = when (source) {
        MusicSource.Local -> listOf("Songs")
        MusicSource.Bilibili -> listOfNotNull("Playlists", "Downloads".takeIf {
            MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsLibraryPlacement
        })
        MusicSource.Netease -> buildList {
            add("Songs"); add("Playlists")
            if (MeloXSettingsRuntime.podcastsEnabled && MeloXSettingsRuntime.podcastsLibraryPlacement) add("Podcasts")
            if (MeloXSettingsRuntime.cloudMusicEnabled && MeloXSettingsRuntime.cloudLibraryPlacement) add("Cloud")
            if (MeloXSettingsRuntime.listeningHistoryEnabled) add("History")
            if (MeloXSettingsRuntime.downloadsEnabled && MeloXSettingsRuntime.downloadsLibraryPlacement) add("Downloads")
        }
        else -> listOf("Playlists")
    }

    fun libraryLastPageKey(source: MusicSource): String = if (source == MusicSource.Netease) {
        "library_last_page"
    } else "library_last_page_${source.storageValue}"

    fun initialLibraryPage(source: MusicSource): String {
        val pages = libraryPages(source)
        val default = MeloXSettingsRuntime.defaultLibraryPage.takeIf { it in pages } ?: pages.first()
        val saved = if (MeloXSettingsRuntime.rememberLibraryPage) {
            MeloXSettingsPreferences.string(libraryLastPageKey(source), default)
        } else default
        return saved.takeIf { it in pages } ?: default
    }

    fun rememberLibraryPage(source: MusicSource, page: String) {
        if (MeloXSettingsRuntime.rememberLibraryPage && page in libraryPages(source)) {
            MeloXSettingsPreferences.setString(libraryLastPageKey(source), page)
        }
    }

    fun visibleHomeSections(): List<String> = MeloXSettingsRuntime.homeSectionOrder.filter {
        when (it) {
            "QuickActions" -> MeloXSettingsRuntime.homeQuickActionsEnabled
            "Playlists" -> MeloXSettingsRuntime.homePlaylistsEnabled
            "NewSongs" -> MeloXSettingsRuntime.homeNewSongsEnabled
            else -> false
        }
    }
}
