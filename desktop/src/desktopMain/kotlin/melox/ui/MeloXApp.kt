package melox.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import melox.library.NeteasePlaylistSummary
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MusicProviderSelectionStore
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXMotion
import melox.ui.navigation.AppTab
import melox.ui.navigation.MeloXBottomChrome
import melox.ui.navigation.Route
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
    durationMillis = MeloXMotion.PlayerTransitionDurationMillis,
    easing = androidx.compose.animation.core.LinearEasing,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp() {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var selectedSource by remember { mutableStateOf(MusicProviderSelectionStore.selectedSource()) }

    LaunchedEffect(Unit) {
        MeloXSettingsPreferences.initialize()
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

    LaunchedEffect(visibleTabs, selectedTab) {
        if (selectedTab !in visibleTabs && selectedTab != AppTab.Search) {
            selectedTab = visibleTabs.first()
        }
    }

    // Tab switch resets minimize (Android lines 399–406)
    LaunchedEffect(selectedTab) {
        tabBarMinimized = false
        scrollAccumulator = 0f
    }

    // Media disappeared → snap player closed (Android lines 350–354)
    LaunchedEffect(hasMedia) {
        if (!hasMedia) playerTransitionState.snapTo(false)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MeloXColors.Primary,
            onPrimary = MeloXColors.OnPrimary,
            background = MeloXColors.Background,
            surface = MeloXColors.Surface,
            surfaceVariant = MeloXColors.SurfaceVariant,
            onBackground = MeloXColors.OnBackground,
            onSurface = MeloXColors.OnSurface,
            onSurfaceVariant = MeloXColors.OnSurfaceVariant,
            outline = Color(0xFF3C3C3C),
            outlineVariant = Color(0xFF2C2C2C),
            error = MeloXColors.Error,
        ),
    ) {
        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            val sharedScope = this
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MeloXColors.Background),
            ) {
                // ── Page content: full-bleed, chrome overlays on top
                // (Android Scaffold structure — content is NOT clipped
                // above the bottom chrome) ──
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        // Android meloXContentEnter/Exit: fade 320/240ms
                        fadeIn(MeloXMotion.contentEnterTween()) togetherWith
                            fadeOut(MeloXMotion.contentExitTween())
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(tabBarMinimizeConnection),
                    label = "melox-page-transition",
                ) { tab ->
                    rootPageState.SaveableStateProvider(tab.name) {
                        TabContent(
                            tab = tab,
                            source = selectedSource,
                            onOpenTool = { tool ->
                                selectedTab = when (tool) {
                                    "Podcasts" -> AppTab.Podcasts
                                    "Downloads" -> AppTab.Downloads
                                    "Cloud" -> AppTab.Cloud
                                    "Messages" -> AppTab.Settings
                                    else -> selectedTab
                                }
                            },
                            onSourceSelected = { source ->
                                MusicProviderSelectionStore.setSelectedSource(source)
                                selectedSource = source
                            },
                        )
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
                        MeloXNowPlayingScreen(
                            navState = null,
                            onDismiss = closePlayer,
                            sharedTransitionScope = sharedScope,
                            animatedVisibilityScope = playerVisibility,
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
                                    animationSpec = spring(dampingRatio = 1f, stiffness = 420f),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun visibleTabsFor(source: MusicSource): List<AppTab> {
    if (source == MusicSource.Bilibili) {
        return listOf(AppTab.Library, AppTab.Settings)
    }
    return buildList {
        add(AppTab.Home)
        add(AppTab.Explore)
        add(AppTab.Library)
        if (MeloXSettingsRuntime.podcastsTabPlacement) add(AppTab.Podcasts)
        if (MeloXSettingsRuntime.downloadsTabPlacement) add(AppTab.Downloads)
        if (MeloXSettingsRuntime.cloudTabPlacement) add(AppTab.Cloud)
        add(AppTab.Settings)
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
    source: MusicSource,
    onOpenTool: (String) -> Unit,
    onSourceSelected: (MusicSource) -> Unit,
) {
    val navState = melox.ui.navigation.rememberMeloXNavState()
    var selectedPlaylist by remember { mutableStateOf<NeteasePlaylistSummary?>(null) }
    var selectedAlbum by remember { mutableStateOf<Pair<String, String>?>(null) }
    val openPlaylist: (NeteasePlaylistSummary) -> Unit = { playlist ->
        selectedAlbum = null
        selectedPlaylist = playlist
    }
    val openProviderPlaylist: (MusicPlaylistSummary) -> Unit = { playlist ->
        selectedAlbum = null
        selectedPlaylist = MeloXLegacyUiBridge.playlist(playlist)
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
                currentSource = source,
                onSourceSelected = onSourceSelected,
                onOpenMessages = { navState.navigateTo(Route.Messages) },
                onOpenServices = { navState.navigateTo(Route.ProviderServices) },
            )
            AppTab.Search -> SearchScreen(source = source)
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
