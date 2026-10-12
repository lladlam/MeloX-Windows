package melox.ui

import melox.ui.glass.meloXRecordBackdrop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import melox.library.NeteasePlaylistSummary
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.playback.PlaybackCommands
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MusicProviderSelectionStore
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.navigation.AppTab
import melox.ui.navigation.MeloXBottomChrome
import melox.ui.navigation.Route
import melox.ui.navigation.MeloXBackDispatcher
import melox.ui.navigation.LocalMeloXBackDispatcher
import melox.ui.navigation.MeloXBackHandler
import melox.ui.screens.AlbumDetailScreen
import melox.ui.screens.CloudScreen
import melox.ui.screens.DownloadsScreen
import melox.ui.screens.ExploreScreen
import melox.ui.screens.HomeScreen
import melox.ui.screens.LibraryScreen
import melox.ui.screens.LoginScreen
import melox.ui.screens.MessagesScreen
import melox.ui.screens.MeloXMiniPlayer
import melox.ui.screens.PlaylistDetailScreen
import melox.ui.screens.MeloXNowPlayingScreen
import melox.ui.screens.PodcastsScreen
import melox.ui.screens.ProviderServicesScreen
import melox.ui.screens.SearchScreen
import melox.ui.screens.SettingsScreen
import melox.ui.theme.MeloXColors

private fun playerAutomaticFractionSpec() = tween<Float>(
    durationMillis = MeloXSettingsRuntime.playerTransitionDurationMs,
    easing = androidx.compose.animation.core.LinearEasing,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp() {
    LaunchedEffect(Unit) {
        androidx.compose.runtime.withFrameNanos { }
        melox.remoteconfig.MeloXRemoteConfigRuntime.initializeAndRefresh()
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { melox.remoteconfig.MeloXRemoteConfigRuntime.stop() }
    }
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var selectedSource by remember { mutableStateOf(MusicProviderSelectionStore.selectedSource()) }
    var messagesVisible by remember { mutableStateOf(false) }
    var heartModeLaunchAttempted by remember { mutableStateOf(false) }
    val backDispatchers = remember { AppTab.entries.associateWith { MeloXBackDispatcher() } }
    val playerBackDispatcher = remember { MeloXBackDispatcher() }
    var settingsRouteRequest by remember { mutableStateOf<String?>(null) }
    val collectionTarget by melox.ui.navigation.MeloXCollectionLaunchBus.target.collectAsState()
    val collectionBackDispatcher = remember { MeloXBackDispatcher() }
    val searchLaunch = melox.ui.navigation.MeloXSearchLaunchBus.request

    LaunchedEffect(Unit) {
        MeloXSettingsPreferences.initialize()
        melox.playback.MeloXPlaybackModePreferences.initialize()
        PlaybackCommands.restoreLastQueue()
        val restored = if (MeloXSettingsRuntime.rememberLastTab) {
            MeloXSettingsPreferences.string("general_last_tab", MeloXSettingsRuntime.defaultTab)
        } else {
            MeloXSettingsRuntime.defaultTab
        }
        selectedTab = runCatching { AppTab.valueOf(restored) }.getOrDefault(AppTab.Home)
    }

    LaunchedEffect(selectedTab) {
        if (MeloXSettingsRuntime.rememberLastTab) {
            MeloXSettingsPreferences.setString("general_last_tab", selectedTab.name)
        }
    }

    val visibleTabs = visibleTabsFor(selectedSource)

    // ── Android state machine (MeloXApp.kt) ──
    var tabBarMinimized by remember { mutableStateOf(false) }
    var scrollAccumulator by remember { mutableFloatStateOf(0f) }
    val rootPageState = rememberSaveableStateHolder()
    val playerScope = rememberCoroutineScope()
    var playerTransitionJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val playerTransitionState = remember { SeekableTransitionState(false) }
    val playerTransition = rememberTransition(
        transitionState = playerTransitionState,
        label = "melox-player-transition",
    )

    val currentTrack by AudioPlayer.currentTrack.collectAsState()
    val hasMedia = currentTrack != null

    val openPlayer: () -> Unit = {
        if (hasMedia) {
            playerTransitionJob?.cancel()
            playerTransitionJob = playerScope.launch {
                playerTransitionState.animateTo(true, playerAutomaticFractionSpec())
            }
        }
    }
    val closePlayer: () -> Unit = {
        playerTransitionJob?.cancel()
        playerTransitionJob = playerScope.launch {
            playerTransitionState.animateTo(false, playerAutomaticFractionSpec())
        }
    }

    val playerOpen = playerTransitionState.currentState || playerTransitionState.targetState
    LaunchedEffect(searchLaunch) {
        if (searchLaunch != null) {
            closePlayer()
            searchLaunch.source?.let { MusicProviderSelectionStore.setSelectedSource(it); selectedSource = it }
            melox.ui.navigation.MeloXCollectionLaunchBus.dismiss()
            selectedTab = AppTab.Search
        }
    }
    LaunchedEffect(collectionTarget) { if (collectionTarget != null) closePlayer() }
    val rootFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { rootFocus.requestFocus() }
    LaunchedEffect(playerOpen, currentTrack?.id) {
        // Selecting an upcoming row removes that focused row from the list.
        // Give Escape a resident target after the selection and after reopening.
        if (playerOpen) rootFocus.requestFocus()
    }
    LaunchedEffect(selectedSource, hasMedia, heartModeLaunchAttempted) {
        if (heartModeLaunchAttempted || hasMedia || selectedSource != MusicSource.Netease) return@LaunchedEffect
        if (!MeloXSettingsRuntime.startsHeartModeOnLaunch) return@LaunchedEffect
        heartModeLaunchAttempted = true
        val session = melox.account.NeteaseSessionStore()
        if (!session.isLoggedIn) return@LaunchedEffect
        if (session.profile == null) session.refreshProfile(force = true)
        val userId = session.profile?.userId ?: return@LaunchedEffect
        val client = melox.library.NeteaseLibraryClient(cookieProvider = { melox.account.NeteaseSessionStore.readCookie() })
        val songs = runCatching {
            val snapshot = client.snapshot(userId)
            val seed = snapshot.likedSongs.randomOrNull() ?: error("收藏歌曲为空")
            val playlistId = snapshot.likedPlaylistId ?: error("没有可用歌单")
            client.intelligenceModeSongs(seed.id, playlistId)
        }.getOrNull().orEmpty()
        songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id, heartMode = true) }
    }

    // Scroll-based minimize: ±18px threshold, directional, resets on reversal
    // (Android tabBarMinimizeConnection, MeloXApp.kt lines 305–335)
    val tabBarMinimizeConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val y = available.y
                if (y < 0f) {
                    if (scrollAccumulator > 0f) scrollAccumulator = 0f
                    scrollAccumulator += y
                    if (scrollAccumulator <= -18f) {
                        tabBarMinimized = true
                        scrollAccumulator = 0f
                    }
                } else if (y > 0f) {
                    if (scrollAccumulator < 0f) scrollAccumulator = 0f
                    scrollAccumulator += y
                    if (scrollAccumulator >= 18f) {
                        tabBarMinimized = false
                        scrollAccumulator = 0f
                    }
                }
                return Offset.Zero
            }
        }
    }

    val selectedPageAvailable = melox.settings.MeloXContentLayout.rootPageAvailable(selectedSource, selectedTab.name)
    LaunchedEffect(visibleTabs, selectedTab, selectedPageAvailable) {
        if (!selectedPageAvailable) {
            selectedTab = visibleTabs.first()
        }
    }

    // Tab switch resets minimize (Android lines 399–406)
    LaunchedEffect(selectedTab) {
        tabBarMinimized = false
        scrollAccumulator = 0f
        // A shortcut's focused card can disappear with its page. Keep Escape reachable.
        if (selectedTab != AppTab.Search) rootFocus.requestFocus()
    }

    // Media disappeared → snap player closed (Android lines 350–354)
    LaunchedEffect(hasMedia) {
        if (!hasMedia) playerTransitionState.snapTo(false)
    }

    val composeDark = isSystemInDarkTheme()
    var systemDark by remember { mutableStateOf(desktopSystemDark() ?: composeDark) }
    LaunchedEffect(MeloXSettingsRuntime.themeMode) {
        if (MeloXSettingsRuntime.themeMode != melox.settings.MeloXThemeMode.System) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(750)
            val current = desktopSystemDark() ?: composeDark
            if (current != systemDark) systemDark = current
        }
    }
    val resolvedTheme = melox.settings.resolveMeloXTheme(MeloXSettingsRuntime.themeMode, systemDark)
    MaterialTheme(
        colorScheme = if (resolvedTheme == melox.settings.MeloXResolvedTheme.Light) meloXLightScheme() else meloXDarkScheme(),
        typography = melox.ui.theme.MeloXMaterialTypography,
        shapes = melox.ui.theme.MeloXMaterialShapes,
    ) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        melox.ui.screens.RemoteCompatibilityNotice()
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyUp || event.key != Key.Escape) return@onPreviewKeyEvent false
                        when {
                            collectionTarget != null -> if (!collectionBackDispatcher.dispatch()) melox.ui.navigation.MeloXCollectionLaunchBus.dismiss()
                            playerOpen -> if (!playerBackDispatcher.dispatch()) closePlayer()
                            messagesVisible -> messagesVisible = false
                            backDispatchers.getValue(selectedTab).dispatch() -> Unit
                            selectedTab != AppTab.Home -> {
                                tabBarMinimized = false
                                selectedTab = AppTab.Home
                            }
                            else -> return@onPreviewKeyEvent false
                        }
                        true
                    }
                    .focusRequester(rootFocus)
                    .focusable(),
            ) {
                // ── Page content: full-bleed, chrome overlays on top
                // (Android Scaffold structure — content is NOT clipped
                // above the bottom chrome) ──
                val pageBackdrop = melox.ui.glass.rememberMeloXLayerBackdrop()
                CompositionLocalProvider(melox.ui.glass.LocalMeloXLayerBackdrop provides pageBackdrop) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // Android meloXContentEnter/Exit: fade 320/240ms
                        fadeIn(MeloXMotion.contentEnterTween()) togetherWith
                            fadeOut(MeloXMotion.contentExitTween())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .meloXRecordBackdrop(pageBackdrop)
                        .nestedScroll(tabBarMinimizeConnection),
                    label = "melox-page-transition",
                ) { tab ->
                    // Descendants being recorded must not sample their own page layer.
                    // Skia's RenderNode graph cannot contain that recursive edge.
                    CompositionLocalProvider(melox.ui.glass.LocalMeloXLayerBackdrop provides null,
                        LocalMeloXBackDispatcher provides backDispatchers.getValue(tab)) {
                    rootPageState.SaveableStateProvider(tab.name) {
                        TabContent(
                            tab = tab,
                            source = selectedSource,
                            settingsRouteRequest = if (tab == AppTab.Settings) settingsRouteRequest else null,
                            onSettingsRouteConsumed = { settingsRouteRequest = null },
                            onOpenTool = { tool ->
                                when (tool) {
                                     "Messages" -> messagesVisible = true
                                     "Home" -> selectedTab = AppTab.Home
                                    "Podcasts" -> selectedTab = AppTab.Podcasts
                                    "Downloads" -> selectedTab = AppTab.Downloads
                                    "Cloud" -> selectedTab = AppTab.Cloud
                                }
                            },
                            onSourceSelected = { source ->
                                MusicProviderSelectionStore.setSelectedSource(source)
                                selectedSource = source
                            },
                        )
                    }
                    }
                }

                // ── Bottom chrome overlay (Android: aligned BottomCenter,
                // not inside a Column) ──
                Box(modifier = Modifier.align(Alignment.BottomCenter).zIndex(10f)) {
                MeloXBottomChrome(
                    selectedTab = selectedTab,
                    hasMedia = hasMedia,
                    minimized = tabBarMinimized,
                    visibleRootTabs = visibleTabs,
                    onSelect = { tab ->
                        tabBarMinimized = false
                        scrollAccumulator = 0f
                        selectedTab = tab
                    },
                    miniPlayer = { compactProgress ->
                        playerTransition.AnimatedVisibility(
                            visible = { value -> !value },
                            enter = androidx.compose.animation.EnterTransition.None,
                            exit = androidx.compose.animation.ExitTransition.None,
                        ) {
                            val miniVisibility = this
                            MeloXMiniPlayer(
                                compactProgress = compactProgress,
                                onExpand = openPlayer,
                                sharedTransitionScope = sharedScope,
                                animatedVisibilityScope = miniVisibility,
                            )
                        }
                    },
                )
                }

                AnimatedVisibility(
                    visible = messagesVisible,
                    modifier = Modifier.fillMaxSize().zIndex(15f),
                    enter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it },
                    exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it },
                ) {
                    MessagesScreen(onBack = { messagesVisible = false })
                }

                // ── Full player overlay (zIndex 20, Android lines 647–702) ──
                playerTransition.AnimatedVisibility(
                    visible = { value -> value },
                    enter = androidx.compose.animation.EnterTransition.None,
                    exit = androidx.compose.animation.ExitTransition.None,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(20f),
                ) {
                    val playerVisibility = this
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                    ) {
                        CompositionLocalProvider(LocalMeloXBackDispatcher provides playerBackDispatcher) {
                        MeloXNowPlayingScreen(
                            onOpenPlaybackSettings = {
                                settingsRouteRequest = "Playback"
                                selectedTab = AppTab.Settings
                                closePlayer()
                            },
                            navState = null,
                            onDismiss = closePlayer,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = playerVisibility,
                            playerExpanded = playerTransitionState.currentState,
                            playerTargetExpanded = playerTransitionState.targetState,
                            playerTransitionFraction = playerTransitionState.fraction,
                            onSeekCollapse = { fraction ->
                                playerTransitionJob?.cancel()
                                playerTransitionJob = null
                                playerTransitionState.seekTo(fraction.coerceIn(0f, 0.999f), targetState = false)
                            },
                            onSettleCollapse = { collapse ->
                                playerTransitionJob?.cancel()
                                playerTransitionJob = null
                                playerTransitionState.animateTo(
                                    targetState = !collapse,
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = 0.001f),
                                )
                            },
                        )
                        }
                    }
                }
                AnimatedContent(
                    targetState = collectionTarget,
                    transitionSpec = {
                        (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 4 } + fadeOut(tween(220)))
                    },
                    modifier = Modifier.fillMaxSize().zIndex(30f),
                    label = "collection-overlay",
                ) { target ->
                    CompositionLocalProvider(LocalMeloXBackDispatcher provides collectionBackDispatcher,
                        melox.ui.glass.LocalMeloXLayerBackdrop provides null) {
                        when (target) {
                            is melox.ui.navigation.MeloXCollectionTarget.Artist -> melox.ui.screens.ArtistDetailScreen(target.id,
                                onBack = melox.ui.navigation.MeloXCollectionLaunchBus::dismiss)
                            is melox.ui.navigation.MeloXCollectionTarget.Album -> AlbumDetailScreen(target.id.toString(), target.name,
                                onBack = melox.ui.navigation.MeloXCollectionLaunchBus::dismiss)
                            null -> Unit
                        }
                    }
                }
                }
            }
        }
        }
    }
}

@Composable
private fun visibleTabsFor(source: MusicSource): List<AppTab> {
    return melox.settings.MeloXContentLayout.visibleTabs(source).map(AppTab::valueOf)
}

@Composable
private fun TabContent(
    tab: AppTab,
    source: MusicSource,
    onOpenTool: (String) -> Unit,
    onSourceSelected: (MusicSource) -> Unit,
    settingsRouteRequest: String? = null,
    onSettingsRouteConsumed: () -> Unit = {},
) {
    val navState = melox.ui.navigation.rememberMeloXNavState()
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(settingsRouteRequest) {
        if (settingsRouteRequest == null) return@LaunchedEffect
        selectedPlaylist = null
        selectedAlbum = null
        navState.navigateToTab(tab)
    }
    val openPlaylist: (NeteasePlaylistSummary) -> Unit = { playlist ->
        selectedAlbum = null
        selectedPlaylist = playlist
    }
    val openProviderPlaylist: (MusicPlaylistSummary) -> Unit = { playlist ->
        selectedAlbum = null
        selectedPlaylist = MeloXLegacyUiBridge.playlist(playlist)
    }
    MeloXBackHandler(enabled = selectedAlbum != null || selectedPlaylist != null || navState.canGoBack, priority = 10) {
        when {
            selectedAlbum != null -> selectedAlbum = null
            selectedPlaylist != null -> selectedPlaylist = null
            else -> navState.goBack()
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            AppTab.Home -> HomeScreen(source = source, onOpenTool = onOpenTool, onOpenPlaylist = openPlaylist)
            AppTab.Explore -> ExploreScreen(
                source = source,
                onOpenPodcasts = { onOpenTool("Podcasts") },
                onOpenPlaylist = openPlaylist,
                onOpenProviderPlaylist = openProviderPlaylist,
            )
            AppTab.Library -> LibraryScreen(navState = navState)
            AppTab.Podcasts -> PodcastsScreen(navState = navState)
            AppTab.Downloads -> DownloadsScreen(navState = navState)
            AppTab.Cloud -> CloudScreen(navState = navState)
            AppTab.Settings -> SettingsScreen(
                routeRequest = settingsRouteRequest,
                onRouteRequestConsumed = onSettingsRouteConsumed,
                currentSource = source,
                onSourceSelected = onSourceSelected,
                onOpenMessages = { navState.navigateTo(Route.Messages) },
                onOpenServices = { navState.navigateTo(Route.ProviderServices) },
                onOpenLogin = { navState.navigateTo(Route.Login) },
            )
            AppTab.Search -> SearchScreen(source = source,
                onSearchExit = { onOpenTool("Home") }, onOpenPodcasts = { onOpenTool("Podcasts") })
        }
        when (navState.currentRoute) {
            Route.Login -> LoginScreen(navState)
            Route.Messages -> MessagesScreen()
            Route.ProviderServices -> ProviderServicesScreen(navState)
            else -> Unit
        }
        selectedPlaylist?.let { playlist ->
            PlaylistDetailScreen(
                playlist = playlist,
                onBack = { selectedPlaylist = null },
            )
        }
        selectedAlbum?.let { (id, name) ->
            AlbumDetailScreen(
                albumId = id,
                albumName = name,
                onBack = { selectedAlbum = null },
            )
        }
    }
}

private fun desktopSystemDark(): Boolean? {
    val property = runCatching {
        val toolkit = Class.forName("java.awt.Toolkit").getMethod("getDefaultToolkit").invoke(null)
        toolkit.javaClass.getMethod("getDesktopProperty", String::class.java)
            .invoke(toolkit, "awt.os.theme") as? String
    }.getOrNull()
    if (property != null) {
        return when {
            property.contains("dark", ignoreCase = true) -> true
            property.contains("light", ignoreCase = true) -> false
            else -> null
        }
    }
    if (!System.getProperty("os.name").orEmpty().lowercase().contains("linux")) return null
    return runCatching {
        val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.interface", "color-scheme").start()
        val value = process.inputStream.bufferedReader().readText()
        when {
            value.contains("prefer-dark") -> true
            value.contains("prefer-light") || value.contains("default") -> false
            else -> null
        }
    }.getOrNull()
}

private fun meloXDarkScheme() = darkColorScheme(
    primary = MeloXColors.Primary,
    onPrimary = MeloXColors.OnPrimary,
    background = MeloXColors.Background,
    surface = MeloXColors.Surface,
    surfaceVariant = MeloXColors.SurfaceVariant,
    onBackground = MeloXColors.OnBackground,
    onSurface = MeloXColors.OnSurface,
    onSurfaceVariant = MeloXColors.OnSurfaceVariant,
    outline = MeloXColors.Outline,
    outlineVariant = MeloXColors.OutlineVariant,
    error = MeloXColors.Error,
)

private fun meloXLightScheme() = lightColorScheme(
    primary = MeloXColors.LightPrimary,
    onPrimary = MeloXColors.OnPrimary,
    background = MeloXColors.LightBackground,
    surface = MeloXColors.LightSurface,
    surfaceVariant = MeloXColors.LightSurfaceVariant,
    onBackground = MeloXColors.LightOnBackground,
    onSurface = MeloXColors.LightOnSurface,
    onSurfaceVariant = MeloXColors.LightOnSurfaceVariant,
    outline = MeloXColors.LightSurfaceVariant,
    outlineVariant = MeloXColors.Separator,
    error = MeloXColors.LightError,
)
