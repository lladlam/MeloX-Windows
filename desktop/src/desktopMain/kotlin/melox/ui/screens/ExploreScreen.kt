package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.music.model.MusicHomeFeed
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicRankingSummary
import melox.music.provider.MeloXMusicProviders
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXTypography

/**
 * 1:1 port of Android MeloXExploreScreen + MeloXExploreLayout
 * (ui/discovery/MeloXDiscoveryScreens.kt lines 720–915).
 *
 * Structure:
 *   Column top 18dp
 *   ├─ MeloXIosTopBar "探索" (own 20dp inset)
 *   ├─ Category glass chips LazyRow (38dp, spacing 8, padding 20/14)
 *   └─ CollectionGrid: hero card (aspect 1.75, "本周主推") + grid cards
 *
 * Data: provider HomeFeedCapability (QQ/Kugou) → playlists / rankings tabs.
 */
private val ExploreAccent = Color(0xFFFF3147)

private val ExploreCategories = listOf("推荐歌单", "排行榜")

@Composable
fun ExploreScreen(navState: melox.ui.navigation.MeloXNavState? = null) {
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<MusicHomeFeed?>(null) }
    var category by remember { mutableStateOf("推荐歌单") }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (refreshing) return
        scope.launch {
            refreshing = true
            val registry = MeloXMusicProviders.create()
            val home = registry.providers.firstNotNullOfOrNull { it as? melox.music.provider.HomeFeedCapability }
            if (home == null) {
                error = "当前音源暂未提供发现数据"
            } else {
                runCatching {
                    withContext(Dispatchers.IO) { home.homeFeed(playlistLimit = 40, newSongLimit = 0, rankingLimit = 30) }
                }.onSuccess {
                    feed = it
                    error = null
                    if (category == "推荐歌单" && it.recommendedPlaylists.isEmpty() && it.rankings.isNotEmpty()) {
                        category = "排行榜"
                    }
                }.onFailure { error = it.message ?: "发现页加载失败" }
            }
            refreshing = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    val categories = buildList {
        add("推荐歌单")
        if (feed?.rankings?.isNotEmpty() == true) add("排行榜")
    }

    Column(Modifier.fillMaxSize().padding(top = 18.dp)) {
        MeloXIosTopBar(title = "探索")
        CategoryChips(
            categories = categories,
            category = category,
            onCategory = { category = it },
        )
        Box(modifier = Modifier.weight(1f)) {
            val collections: List<Any> = when (category) {
                "排行榜" -> feed?.rankings.orEmpty()
                else -> feed?.recommendedPlaylists.orEmpty()
            }
            if (collections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (refreshing) CircularProgressIndicator(color = ExploreAccent)
                    else Text(
                        error ?: "暂无内容",
                        color = MeloXColors.OnBackground.copy(alpha = .5f),
                    )
                }
            } else {
                CollectionGrid(collections, refreshing)
            }
        }
    }
}

@Composable
private fun CategoryChips(
    categories: List<String>,
    category: String,
    onCategory: (String) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(categories.size) { index ->
            val item = categories[index]
            val selected = category == item
            Box(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        when {
                            selected -> ExploreAccent
                            else -> MeloXColors.Surface.copy(alpha = 0.5f)
                        }
                    )
                    .clickable { onCategory(item) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.removeSuffix("歌单"),
                    color = if (selected) Color.White else MeloXColors.OnBackground,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun CollectionGrid(
    values: List<Any>,
    refreshing: Boolean,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 146.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val first = values.firstOrNull()
        if (first is MusicPlaylistSummary) {
            item(key = "hero-${first.id.value}", span = { GridItemSpan(3) }) {
                HeroCollectionCard(first)
            }
            items(values.drop(1).filterIsInstance<MusicPlaylistSummary>(), key = { it.id.value }) { playlist ->
                CollectionCard(playlist, Modifier.fillMaxWidth())
            }
        } else {
            items(values.filterIsInstance<MusicRankingSummary>(), key = { it.id.value }) { ranking ->
                RankingCard(ranking, Modifier.fillMaxWidth())
            }
        }
        if (refreshing) {
            item(span = { GridItemSpan(3) }) {
                Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ExploreAccent)
                }
            }
        }
    }
}

@Composable
private fun HeroCollectionCard(value: MusicPlaylistSummary) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.75f)
            .clip(RoundedCornerShape(28.dp))
            .clickable { },
    ) {
        MeloXArtworkImage(
            url = value.artworkUrl,
            fallbackColor = MeloXColors.sourceColors[value.id.source.storageValue] ?: ExploreAccent,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .82f)))))
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
        }
    }
}

@Composable
private fun CollectionCard(
    value: MusicPlaylistSummary,
    modifier: Modifier,
) {
    Column(modifier.clickable { }.padding(0.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            MeloXArtworkImage(
                url = value.artworkUrl,
                fallbackColor = MeloXColors.sourceColors[value.id.source.storageValue] ?: ExploreAccent,
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
    }
}

@Composable
private fun RankingCard(
    value: MusicRankingSummary,
    modifier: Modifier,
) {
    Box(
        modifier
            .height(174.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MeloXColors.sourceColors[value.id.source.storageValue]?.copy(alpha = 0.85f)
                            ?: ExploreAccent.copy(alpha = 0.85f),
                        ExploreAccent.copy(alpha = 0.45f),
                    )
                )
            )
            .clickable { }
            .padding(12.dp),
    ) {
        Column {
            Text(
                value.title,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            value.subtitle?.let {
                Text(
                    it,
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
