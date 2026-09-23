package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryCache
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.music.model.MusicHomeFeed
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicRankingSummary
import melox.music.model.MusicSource
import melox.music.provider.HomeFeedCapability
import melox.music.provider.MeloXMusicProviders
import melox.settings.MeloXSettingsRuntime
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.theme.MeloXColors

private val ExploreAccent = Color(0xFFFF3147)

private val NeteaseExploreCategories = listOf(
    "推荐歌单", "排行榜", "精品歌单", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "轻音乐", "影视原声", "ACG",
)

private sealed interface ExploreCollection {
    val key: String
    val title: String
    val artworkUrl: String?
    val description: String?
    val creatorName: String
    val playCount: Long
    val fallbackColor: Color

    data class Netease(val playlist: NeteasePlaylistSummary) : ExploreCollection {
        override val key: String get() = "netease-${playlist.id}"
        override val title: String get() = playlist.name
        override val artworkUrl: String? get() = playlist.coverUrl
        override val description: String? get() = playlist.description
        override val creatorName: String get() = playlist.creatorName
        override val playCount: Long get() = playlist.playCount
        override val fallbackColor: Color get() = ExploreAccent
    }

    data class ProviderPlaylist(val playlist: MusicPlaylistSummary) : ExploreCollection {
        override val key: String get() = "playlist-${playlist.id.source.storageValue}-${playlist.id.value}"
        override val title: String get() = playlist.title
        override val artworkUrl: String? get() = playlist.artworkUrl
        override val description: String? get() = playlist.description
        override val creatorName: String get() = playlist.creatorName.orEmpty()
        override val playCount: Long get() = playlist.playCount ?: 0L
        override val fallbackColor: Color
            get() = MeloXColors.sourceColors[playlist.id.source.storageValue] ?: ExploreAccent
    }

    data class ProviderRanking(val ranking: MusicRankingSummary) : ExploreCollection {
        override val key: String get() = "ranking-${ranking.id.source.storageValue}-${ranking.id.value}"
        override val title: String get() = ranking.title
        override val artworkUrl: String? get() = ranking.artworkUrl
        override val description: String? get() = ranking.subtitle
        override val creatorName: String get() = ""
        override val playCount: Long get() = 0L
        override val fallbackColor: Color
            get() = MeloXColors.sourceColors[ranking.id.source.storageValue] ?: ExploreAccent
    }
}

@Composable
fun ExploreScreen(
    navState: melox.ui.navigation.MeloXNavState? = null,
    source: MusicSource = MusicSource.Netease,
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit = {},
    onOpenPodcasts: () -> Unit = {},
    onOpenProviderPlaylist: (MusicPlaylistSummary) -> Unit = {},
) {
    if (source == MusicSource.Netease) {
        NeteaseExploreDataScreen(
            onOpenPlaylist = onOpenPlaylist,
            onOpenPodcasts = onOpenPodcasts,
        )
    } else {
        ProviderExploreDataScreen(
            source = source,
            onOpenProviderPlaylist = onOpenProviderPlaylist,
        )
    }
}

@Composable
private fun NeteaseExploreDataScreen(
    onOpenPlaylist: (NeteasePlaylistSummary) -> Unit,
    onOpenPodcasts: () -> Unit,
) {
    val cache = remember { NeteaseLibraryCache() }
    val client = remember { NeteaseLibraryClient({ NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    val visibleCategories = NeteaseExploreCategories.filter { item ->
        item != "精品歌单" || MeloXSettingsRuntime.showHighQualityPlaylists
    } + if (MeloXSettingsRuntime.podcastsEnabled) listOf("播客") else emptyList()
    var category by remember { mutableStateOf(visibleCategories.first()) }
    var collections by remember { mutableStateOf<List<ExploreCollection.Netease>>(emptyList()) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (category == "播客" || refreshing) return
        val requested = category
        scope.launch {
            refreshing = true
            runCatching { client.explorePlaylists(requested) }
                .onSuccess {
                    if (category == requested) {
                        collections = it.map { playlist -> ExploreCollection.Netease(playlist) }
                    }
                    cache.saveExplore(requested, it)
                    error = null
                }
                .onFailure { error = it.message ?: "发现页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(category) {
        if (category == "播客") {
            onOpenPodcasts()
            return@LaunchedEffect
        }
        collections = cache.loadExplore(category).orEmpty().map { ExploreCollection.Netease(it) }
        if (NeteaseLibraryCache.beginExploreColdStartRefresh(category)) refresh()
    }

    ExploreLayout(
        categories = visibleCategories,
        category = category,
        onCategory = { category = it },
        collections = collections,
        refreshing = refreshing,
        error = error,
        onCollection = { collection ->
            if (collection is ExploreCollection.Netease) onOpenPlaylist(collection.playlist)
        },
    )
}

@Composable
private fun ProviderExploreDataScreen(
    source: MusicSource,
    onOpenProviderPlaylist: (MusicPlaylistSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val provider = remember(source) { MeloXMusicProviders.create().require(source) }
    val home = provider as? HomeFeedCapability
    var feed by remember(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var category by remember(source) { mutableStateOf("推荐歌单") }
    var refreshing by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }

    fun refresh() {
        if (refreshing || home == null) return
        scope.launch {
            refreshing = true
            runCatching {
                withContext(Dispatchers.IO) { home.homeFeed(playlistLimit = 40, newSongLimit = 0, rankingLimit = 30) }
            }.onSuccess {
                feed = it
                error = null
                if (category == "推荐歌单" && it.recommendedPlaylists.isEmpty() && it.rankings.isNotEmpty()) {
                    category = "排行榜"
                }
            }.onFailure { error = it.message ?: "发现页加载失败" }
            refreshing = false
        }
    }

    LaunchedEffect(source) { refresh() }

    val categories = buildList {
        add("推荐歌单")
        if (feed?.rankings?.isNotEmpty() == true) add("排行榜")
    }
    val collections = when (category) {
        "排行榜" -> feed?.rankings.orEmpty().map { ExploreCollection.ProviderRanking(it) }
        else -> feed?.recommendedPlaylists.orEmpty().map { ExploreCollection.ProviderPlaylist(it) }
    }

    ExploreLayout(
        categories = categories,
        category = category,
        onCategory = { category = it },
        collections = collections,
        refreshing = refreshing,
        error = if (home == null) "${source.displayName} 暂未提供发现数据" else error,
        onCollection = { collection ->
            if (collection is ExploreCollection.ProviderPlaylist) onOpenProviderPlaylist(collection.playlist)
        },
    )
}

@Composable
private fun ExploreLayout(
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
    collections: List<ExploreCollection>,
    refreshing: Boolean,
    error: String?,
    onCollection: (ExploreCollection) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        MeloXIosTopBar(title = "探索")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(categories.size) { index ->
                val item = categories[index]
                val selected = category == item
                MeloXGlassButton(
                    onClick = { onCategory(item) },
                    modifier = Modifier.height(38.dp),
                    style = if (selected) MeloXGlassButtonStyle.BorderedProminent else MeloXGlassButtonStyle.Bordered,
                    tint = if (selected) ExploreAccent else Color.Unspecified,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = item.removeSuffix("歌单"),
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            if (collections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(color = ExploreAccent)
                    else Text(
                        error ?: "暂无内容",
                        color = MeloXColors.OnBackground.copy(alpha = .5f),
                    )
                }
            } else {
                CollectionGrid(collections, onCollection)
            }
        }
    }
}

@Composable
private fun CollectionGrid(
    values: List<ExploreCollection>,
    onSelect: (ExploreCollection) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        values.firstOrNull()?.let { hero ->
            item(key = "hero-${hero.key}", span = { GridItemSpan(maxLineSpan) }) {
                HeroCollectionCard(hero) { onSelect(hero) }
            }
        }
        items(values.drop(1), key = { it.key }) { collection ->
            CollectionCard(collection, Modifier.fillMaxWidth()) { onSelect(collection) }
        }
    }
}

@Composable
private fun HeroCollectionCard(value: ExploreCollection, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .clip(RoundedCornerShape(28.dp))
            .clickable(onClick = onClick),
    ) {
        MeloXArtworkImage(
            url = value.artworkUrl,
            fallbackColor = value.fallbackColor,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text("本周主推", color = Color.White.copy(alpha = .72f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                value.title,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            (value.description ?: value.creatorName).takeIf(String::isNotBlank)?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CollectionCard(
    value: ExploreCollection,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            MeloXArtworkImage(
                url = value.artworkUrl,
                fallbackColor = value.fallbackColor,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            value.title,
            modifier = Modifier.padding(top = 7.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (MeloXSettingsRuntime.showPlaylistPlayCount && value.playCount > 0L) {
            Text(
                "${compactCount(value.playCount)} 次播放",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = .42f),
                fontSize = 11.sp,
            )
        }
    }
}

private fun compactCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}
