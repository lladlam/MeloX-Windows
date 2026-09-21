package melox.ui.navigation

sealed class Route {
    data object Home : Route()
    data object Explore : Route()
    data object Library : Route()
    data object Podcasts : Route()
    data object Downloads : Route()
    data object Cloud : Route()
    data object Settings : Route()
    data object Search : Route()
    data object Player : Route()
    data class PlaylistDetail(val id: String, val name: String) : Route()
    data class AlbumDetail(val id: String, val name: String) : Route()
    data object Login : Route()
    data object Messages : Route()
    data object ProviderServices : Route()
}

enum class AppTab(val label: String, val icon: String) {
    Home("发现", "home"),
    Explore("探索", "explore"),
    Library("音乐库", "library"),
    Podcasts("播客", "podcasts"),
    Downloads("下载", "downloads"),
    Cloud("云盘", "cloud"),
    Settings("设置", "settings"),
    Search("搜索", "search"),
}
