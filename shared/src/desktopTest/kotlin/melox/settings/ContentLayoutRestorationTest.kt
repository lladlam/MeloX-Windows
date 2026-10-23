package melox.settings

import melox.music.model.MusicSource
import melox.platform.getPreferences
import kotlin.test.*

/** Exercises persisted settings across reinitialization and source/feature changes. */
class ContentLayoutRestorationTest {
    @BeforeTest fun reset() = MeloXSettingsPreferences.reset()
    @AfterTest fun cleanup() = MeloXSettingsPreferences.reset()

    @Test fun rootDestinationsMatchRealFeedCapabilitiesAndKeepNeteaseModulesSourceScoped() {
        MeloXSettingsPreferences.setString("tab_order", "Home,Explore,Library,Podcasts,Cloud,Settings")
        MeloXSettingsPreferences.setBoolean("placement_podcasts_tab", true)
        MeloXSettingsPreferences.setBoolean("placement_cloud_tab", true)
        val providers = melox.music.provider.MeloXMusicProviders.create()
        for (source in MusicSource.entries) {
            val pages = MeloXContentLayout.visibleTabs(source)
            val hasFeed = source == MusicSource.Netease || providers.require(source) is melox.music.provider.HomeFeedCapability
            assertEquals(hasFeed, "Home" in pages, "$source Home capability")
            assertEquals(hasFeed, "Explore" in pages, "$source Explore capability")
            assertEquals(source == MusicSource.Netease, "Cloud" in pages, "$source Cloud ownership")
            assertEquals(source == MusicSource.Netease, "Podcasts" in pages, "$source Podcasts ownership")
        }
        assertTrue(MeloXSettingsPreferences.boolean("placement_cloud_tab"))
    }

    @Test fun persistedTabOrderAndPlacementSurviveCriticalThenFullStartupWithoutLosingSettings() {
        MeloXSettingsPreferences.setString("tab_order", "Cloud,Library,Settings,Home,Explore,Downloads,Podcasts")
        MeloXSettingsPreferences.setBoolean("placement_cloud_tab", true)
        MeloXSettingsPreferences.setBoolean("tab_explore", false)
        MeloXSettingsRuntime.initializeCritical()
        MeloXSettingsRuntime.initialize(force = true)
        assertEquals(listOf("Cloud", "Library", "Settings", "Home"), MeloXContentLayout.visibleTabs(MusicSource.Netease))
        MeloXSettingsPreferences.setBoolean("feature_cloud_music", false)
        assertEquals(listOf("Library", "Settings", "Home"), MeloXContentLayout.visibleTabs(MusicSource.Netease))
        MeloXSettingsPreferences.setBoolean("tab_home", false)
        MeloXSettingsPreferences.setBoolean("tab_library", false)
        assertEquals(listOf("Settings"), MeloXContentLayout.visibleTabs(MusicSource.Netease))
        assertTrue(getPreferences("melox_app_settings").getBoolean("placement_cloud_tab", false))
    }

    @Test fun libraryRemembersPerSourceAndDisabledSavedPageFallsBackToConfiguredVisibleDefault() {
        MeloXContentLayout.rememberLibraryPage(MusicSource.Netease, "Cloud")
        MeloXContentLayout.rememberLibraryPage(MusicSource.Bilibili, "Downloads")
        MeloXContentLayout.rememberLibraryPage(MusicSource.Local, "Songs")
        MeloXContentLayout.rememberLibraryPage(MusicSource.Spotify, "Artists")
        MeloXContentLayout.rememberLibraryPage(MusicSource.YouTubeMusic, "Albums")
        MeloXSettingsRuntime.initialize(force = true)
        assertEquals("Cloud", MeloXContentLayout.initialLibraryPage(MusicSource.Netease))
        assertEquals("Downloads", MeloXContentLayout.initialLibraryPage(MusicSource.Bilibili))
        assertEquals("Songs", MeloXContentLayout.initialLibraryPage(MusicSource.Local))
        assertEquals("Artists", MeloXContentLayout.initialLibraryPage(MusicSource.Spotify))
        assertEquals("Albums", MeloXContentLayout.initialLibraryPage(MusicSource.YouTubeMusic))
        assertEquals("Cloud", MeloXSettingsPreferences.string("library_last_page"))
        assertEquals("Downloads", MeloXSettingsPreferences.string("library_last_page_bilibili"))
        MeloXSettingsPreferences.setString("library_default_page", "Playlists")
        MeloXSettingsPreferences.setBoolean("placement_cloud_library", false)
        assertEquals("Playlists", MeloXContentLayout.initialLibraryPage(MusicSource.Netease))
        assertEquals("Cloud", MeloXSettingsPreferences.string("library_last_page"), "Resolving visibility must not rewrite user history")
        MeloXSettingsPreferences.setBoolean("feature_downloads", false)
        assertEquals(listOf("Playlists"), MeloXContentLayout.libraryPages(MusicSource.Bilibili))
        assertEquals("Playlists", MeloXContentLayout.initialLibraryPage(MusicSource.Bilibili))
        assertEquals(listOf("Songs", "Playlists"), MeloXContentLayout.libraryPages(MusicSource.Local))
    }

    @Test fun rememberingOffUsesDefaultWithoutOverwritingPreviousPerProviderHistory() {
        MeloXContentLayout.rememberLibraryPage(MusicSource.Netease, "History")
        MeloXSettingsPreferences.setBoolean("library_remember_page", false)
        MeloXSettingsPreferences.setString("library_default_page", "Podcasts")
        MeloXContentLayout.rememberLibraryPage(MusicSource.Netease, "Playlists")
        MeloXSettingsRuntime.initialize(force = true)
        assertEquals("Podcasts", MeloXContentLayout.initialLibraryPage(MusicSource.Netease))
        assertEquals("History", MeloXSettingsPreferences.string("library_last_page"))
        assertEquals("Playlists", MeloXContentLayout.initialLibraryPage(MusicSource.Spotify))
        MeloXSettingsPreferences.setBoolean("library_remember_page", true)
        assertEquals("History", MeloXContentLayout.initialLibraryPage(MusicSource.Netease))
    }

    @Test fun homeReorderingKeepsQuickActionsInTheirSavedPositionAndHonorsFeatureTogglesAfterReload() {
        MeloXSettingsPreferences.setString("home_section_order", "NewSongs,QuickActions,Playlists")
        MeloXSettingsRuntime.initialize(force = true)
        assertEquals(listOf("NewSongs", "QuickActions", "Playlists"), MeloXContentLayout.visibleHomeSections())
        MeloXSettingsPreferences.setBoolean("home_quick_actions", false)
        assertEquals(listOf("NewSongs", "Playlists"), MeloXContentLayout.visibleHomeSections())
        assertEquals("NewSongs,QuickActions,Playlists", MeloXSettingsPreferences.string("home_section_order"))
        MeloXSettingsPreferences.setBoolean("home_quick_actions", true)
        assertEquals(listOf("NewSongs", "QuickActions", "Playlists"), MeloXContentLayout.visibleHomeSections())
    }

    @Test fun shortcutCanOpenEnabledCloudWithoutChangingDockOrPretendingOtherProviderOwnsIt() {
        MeloXSettingsPreferences.setBoolean("placement_cloud_home", true)
        assertFalse("Cloud" in MeloXContentLayout.visibleTabs(MusicSource.Netease))
        assertTrue(MeloXContentLayout.rootPageAvailable(MusicSource.Netease, "Cloud"))
        assertFalse(MeloXContentLayout.rootPageAvailable(MusicSource.Spotify, "Cloud"))
        MeloXSettingsPreferences.setBoolean("feature_cloud_music", false)
        assertFalse(MeloXContentLayout.rootPageAvailable(MusicSource.Netease, "Cloud"))
        assertEquals(listOf("Library", "Settings"), MeloXContentLayout.visibleTabs(MusicSource.Bilibili))
    }
}
