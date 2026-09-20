package melox.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import melox.ui.navigation.*
import melox.ui.screens.ExploreScreen
import melox.ui.screens.HomeScreen
import melox.ui.screens.LibraryScreen
import melox.ui.screens.SearchScreen
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily

@Composable
fun MeloXApp() {
    val navState = rememberMeloXNavState()

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
            displayLarge = Typography().displayLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            displayMedium = Typography().displayMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            displaySmall = Typography().displaySmall.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineLarge = Typography().headlineLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineMedium = Typography().headlineMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineSmall = Typography().headlineSmall.copy(fontFamily = MeloXLanTingProFontFamily),
            titleLarge = Typography().titleLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            titleMedium = Typography().titleMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            titleSmall = Typography().titleSmall.copy(fontFamily = MeloXLanTingProFontFamily),
            bodyLarge = Typography().bodyLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            bodyMedium = Typography().bodyMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            bodySmall = Typography().bodySmall.copy(fontFamily = MeloXLanTingProFontFamily),
            labelLarge = Typography().labelLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            labelMedium = Typography().labelMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            labelSmall = Typography().labelSmall.copy(fontFamily = MeloXLanTingProFontFamily),
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MeloXColors.Background),
        ) {
            val windowWidth = maxWidth
            val isWideLayout = windowWidth >= 900.dp

            if (isWideLayout) {
                // Sidebar layout: sidebar on left, content on right
                Row(modifier = Modifier.fillMaxSize()) {
                    MeloXSidebar(navState = navState)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        ContentArea(navState = navState)
                    }
                }
            } else {
                // Bottom nav layout: content on top, nav bar on bottom
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        ContentArea(navState = navState)
                    }
                    MeloXBottomBar(navState = navState)
                }
            }
        }
    }
}

@Composable
private fun ContentArea(navState: MeloXNavState) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = navState.currentRoute,
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) + slideInHorizontally(
                        animationSpec = tween(200),
                        initialOffsetX = { if (targetState is Route.Search || targetState is Route.Settings) -it / 3 else it / 3 },
                    ) togetherWith fadeOut(animationSpec = tween(150))
                },
                label = "content_transition",
            ) { route ->
                when (route) {
                    is Route.Home -> HomeScreen(navState = navState)
                    is Route.Explore -> ExploreScreen(navState = navState)
                    is Route.Library -> LibraryScreen(navState = navState)
                    is Route.Podcasts -> PodcastsScreen()
                    is Route.Downloads -> DownloadsScreen()
                    is Route.Cloud -> CloudScreen()
                    is Route.Settings -> SettingsScreen()
                    is Route.Search -> SearchScreen()
                    is Route.Player -> PlayerScreen()
                    is Route.PlaylistDetail -> PlaylistDetailScreen(route.id, route.name)
                    is Route.AlbumDetail -> AlbumDetailScreen(route.id, route.name)
                    is Route.ProviderServices -> ProviderServicesScreen()
                }
            }
        }
    }
}

// ── Placeholder screen composables ──

@Composable
private fun PodcastsScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("播客", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun DownloadsScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("下载", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun CloudScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("云盘", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun SettingsScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("设置", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun PlayerScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("播放器", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun PlaylistDetailScreen(id: String, name: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("播放列表: $name", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun AlbumDetailScreen(id: String, name: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("专辑: $name", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}

@Composable
private fun ProviderServicesScreen() {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text("音乐源服务", style = MaterialTheme.typography.headlineMedium, color = MeloXColors.OnSurface)
    }
}
