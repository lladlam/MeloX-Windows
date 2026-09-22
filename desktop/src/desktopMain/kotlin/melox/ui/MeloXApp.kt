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
import melox.player.AudioPlayer
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

// Android visible tabs (Search excluded; Settings guaranteed last)
private val VisibleTabs = listOf(
    AppTab.Home,
    AppTab.Explore,
    AppTab.Library,
    AppTab.Settings,
)

private fun playerAutomaticFractionSpec() = tween<Float>(
    durationMillis = MeloXMotion.PlayerTransitionDurationMillis,
    easing = androidx.compose.animation.core.LinearEasing,
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MeloXApp() {
    var selectedTab by remember { mutableStateOf(AppTab.Home) }

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
                    visibleRootTabs = VisibleTabs,
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
                            MeloXMiniPlayer(
                                compactProgress = compactProgress,
                                onExpand = openPlayer,
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
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    tab: AppTab,
) {
    val navState = melox.ui.navigation.rememberMeloXNavState()
    when (tab) {
        AppTab.Home -> HomeScreen(navState = navState)
        AppTab.Explore -> ExploreScreen(navState = navState)
        AppTab.Library -> LibraryScreen(navState = navState)
        AppTab.Podcasts -> PodcastsScreen(navState = navState)
        AppTab.Downloads -> DownloadsScreen(navState = navState)
        AppTab.Cloud -> CloudScreen(navState = navState)
        AppTab.Settings -> SettingsScreen()
        AppTab.Search -> SearchScreen()
    }
}
