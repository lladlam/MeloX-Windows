package melox.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import melox.ui.foundation.MeloXMotion
import melox.ui.navigation.*
import melox.ui.screens.AlbumDetailScreen
import melox.ui.screens.CloudScreen
import melox.ui.screens.DownloadsScreen
import melox.ui.screens.ExploreScreen
import melox.ui.screens.HomeScreen
import melox.ui.screens.LibraryScreen
import melox.ui.screens.LoginScreen
import melox.ui.screens.MessagesScreen
import melox.ui.screens.MiniPlayer
import melox.ui.screens.PlayerScreen
import melox.ui.screens.PlaylistDetailScreen
import melox.ui.screens.PodcastsScreen
import melox.ui.screens.ProviderServicesScreen
import melox.ui.screens.SearchScreen
import melox.ui.screens.SettingsScreen
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

@Composable
fun MeloXApp() {
    val navState = rememberMeloXNavState()
    val currentTrack by melox.player.AudioPlayer.currentTrack.collectAsState()
    val hasTrack = currentTrack != null

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MeloXColors.Primary,
            onPrimary = MeloXColors.OnPrimary,
            primaryContainer = MeloXColors.PrimaryVariant,
            secondary = MeloXColors.Secondary,
            background = MeloXColors.Background,
            surface = MeloXColors.Surface,
            surfaceVariant = MeloXColors.SurfaceVariant,
            onBackground = MeloXColors.OnSurface,
            onSurface = MeloXColors.OnSurface,
            onSurfaceVariant = MeloXColors.OnSurfaceVariant,
            outline = MeloXColors.Outline,
            outlineVariant = MeloXColors.OutlineVariant,
            error = MeloXColors.Error,
        ),
        typography = Typography().copy(
            displayLarge = MeloXTypography.largeTitle,
            displayMedium = MeloXTypography.title2,
            displaySmall = MeloXTypography.headline,
            headlineLarge = MeloXTypography.largeTitle,
            headlineMedium = MeloXTypography.title2,
            headlineSmall = MeloXTypography.headline,
            titleLarge = MeloXTypography.headline,
            titleMedium = MeloXTypography.subheadline,
            titleSmall = MeloXTypography.caption,
            bodyLarge = MeloXTypography.body,
            bodyMedium = MeloXTypography.subheadline,
            bodySmall = MeloXTypography.caption,
            labelLarge = MeloXTypography.subheadline,
            labelMedium = MeloXTypography.caption,
            labelSmall = MeloXTypography.caption,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MeloXColors.Background),
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ContentArea(navState = navState)
            }

            if (hasTrack) {
                MiniPlayer(navState = navState)
            }

            MeloXBottomBar(navState = navState)
        }
    }
}

@Composable
private fun ContentArea(navState: MeloXNavState) {
    AnimatedContent(
        targetState = navState.currentRoute,
        transitionSpec = {
            fadeIn(
                animationSpec = tween(MeloXMotion.PageEnterMillis),
            ) + slideInHorizontally(
                animationSpec = tween(MeloXMotion.PageEnterMillis),
                initialOffsetX = { fullWidth ->
                    when (targetState) {
                        is Route.Search, is Route.Settings -> -fullWidth / 3
                        else -> fullWidth / 3
                    }
                },
            ) togetherWith fadeOut(
                animationSpec = tween(MeloXMotion.PageExitMillis),
            )
        },
        label = "content_transition",
    ) { route ->
        when (route) {
            is Route.Home -> HomeScreen(navState = navState)
            is Route.Explore -> ExploreScreen(navState = navState)
            is Route.Library -> LibraryScreen(navState = navState)
            is Route.Podcasts -> PodcastsScreen(navState = navState)
            is Route.Downloads -> DownloadsScreen(navState = navState)
            is Route.Cloud -> CloudScreen(navState = navState)
            is Route.Settings -> SettingsScreen()
            is Route.Search -> SearchScreen()
            is Route.Player -> PlayerScreen(navState = navState)
            is Route.PlaylistDetail -> PlaylistDetailScreen(
                navState = navState,
                playlistId = route.id,
                playlistName = route.name,
            )
            is Route.AlbumDetail -> AlbumDetailScreen(
                navState = navState,
                albumId = route.id,
                albumName = route.name,
            )
            is Route.Login -> LoginScreen(navState = navState)
            is Route.Messages -> MessagesScreen()
            is Route.ProviderServices -> ProviderServicesScreen(navState = navState)
        }
    }
}
