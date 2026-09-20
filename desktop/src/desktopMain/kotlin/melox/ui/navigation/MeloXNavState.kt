package melox.ui.navigation

import androidx.compose.runtime.*

@Stable
class MeloXNavState {
    var currentRoute by mutableStateOf<Route>(Route.Home)
    var selectedTab by mutableStateOf(AppTab.Home)
    val backStack = mutableStateListOf<Route>()

    fun navigateTo(route: Route) {
        backStack.add(currentRoute)
        currentRoute = route
        when (route) {
            is Route.Home -> selectedTab = AppTab.Home
            is Route.Explore -> selectedTab = AppTab.Explore
            is Route.Library -> selectedTab = AppTab.Library
            is Route.Podcasts -> selectedTab = AppTab.Podcasts
            is Route.Downloads -> selectedTab = AppTab.Downloads
            is Route.Cloud -> selectedTab = AppTab.Cloud
            is Route.Settings -> selectedTab = AppTab.Settings
            is Route.Search -> selectedTab = AppTab.Search
            else -> {}
        }
    }

    fun navigateToTab(tab: AppTab) {
        selectedTab = tab
        backStack.clear()
        currentRoute = when (tab) {
            AppTab.Home -> Route.Home
            AppTab.Explore -> Route.Explore
            AppTab.Library -> Route.Library
            AppTab.Podcasts -> Route.Podcasts
            AppTab.Downloads -> Route.Downloads
            AppTab.Cloud -> Route.Cloud
            AppTab.Settings -> Route.Settings
            AppTab.Search -> Route.Search
        }
    }

    fun goBack() {
        if (backStack.isNotEmpty()) {
            currentRoute = backStack.removeLast()
            when (currentRoute) {
                is Route.Home -> selectedTab = AppTab.Home
                is Route.Explore -> selectedTab = AppTab.Explore
                is Route.Library -> selectedTab = AppTab.Library
                is Route.Podcasts -> selectedTab = AppTab.Podcasts
                is Route.Downloads -> selectedTab = AppTab.Downloads
                is Route.Cloud -> selectedTab = AppTab.Cloud
                is Route.Settings -> selectedTab = AppTab.Settings
                is Route.Search -> selectedTab = AppTab.Search
                else -> {}
            }
        }
    }

    val canGoBack: Boolean get() = backStack.isNotEmpty()
}

@Composable
fun rememberMeloXNavState(): MeloXNavState = remember { MeloXNavState() }
