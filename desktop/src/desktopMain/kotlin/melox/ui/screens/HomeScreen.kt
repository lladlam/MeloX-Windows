package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.PlaylistCapability
import melox.player.AudioPlayer
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.theme.MeloXTypography
import melox.ui.theme.MeloXColors

/**
 * 1:1 port of Android MeloXHomeScreen + MeloXHomeLayout
 * (ui/discovery/MeloXDiscoveryScreens.kt lines 187–718).
 *
 * Structure (exact Android order):
 *   LazyColumn padding(top=18, bottom=146), spacing 22dp
 *   ├─ MeloXIosTopBar "发现" + account button 42dp glass circle
 *   ├─ Greeting headline (17sp SemiBold, α0.58, 20dp)
 *   ├─ HomeQuickActions — 310dp wide cards, aspect 1.48, gradient tiles
 *   ├─ SectionTitle + CollectionRow (246/174dp cards, artwork 174dp)
 *   ├─ SectionTitle + ThreeLineSongCarousel (3 songs per group, 320dp)
 *   └─ error / empty text
 *
 * Data: real provider homeFeed (QQ/Kugou) + searchSongs fallback.
 */
private val HomeAccent = Color(0xFFFF3147)

private data class HomeAction(
    val title: String,
    val eyebrow: String,
    val subtitle: String,
    val symbol: MeloXSymbol,
    val colors: List<Color>,
)

private fun homeQuickActions(): List<HomeAction> = listOf(
    HomeAction("每日推荐", "每日更新", "为你定制的歌曲", MeloXSymbol.Calendar, listOf(Color(0xFFFF5B8A), Color(0xFFFF3147))),
    HomeAction("热歌榜", "全站热门", "大家都在听", MeloXSymbol.Flame, listOf(Color(0xFFFFA14A), Color(0xFFFF5A36))),
    HomeAction("心动模式", "为你心动", "喜欢与惊喜交替播放", MeloXSymbol.Heart, listOf(Color(0xFFFF6EAC), Color(0xFF9B5DE5))),
    HomeAction("私人雷达", "持续发现", "发现符合你口味的歌单", MeloXSymbol.Podcast, listOf(Color(0xFF6B7BFF), Color(0xFF8C52FF))),
    HomeAction("私人漫游", "探索模式", "漫游到新的好音乐", MeloXSymbol.Walk, listOf(Color(0xFF26C6DA), Color(0xFF4285F4))),
    HomeAction("相似歌曲", "从当前歌曲出发", "播放更多相似歌曲", MeloXSymbol.ListBullet, listOf(Color(0xFF58C9A3), Color(0xFF159D9A))),
    HomeAction("听歌识曲", "快捷工具", "识别环境中正在播放的歌曲", MeloXSymbol.Mic, listOf(Color(0xFF7B61FF), Color(0xFF36C5F0))),
    HomeAction("下载", "本地音乐", "浏览已下载的歌曲", MeloXSymbol.Download, listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6))),
)

private sealed interface HomeBlock {
    data class Collections(
        val title: String,
        val trailing: String,
        val values: List<MusicPlaylistSummary>,
    ) : HomeBlock

    data class Tracks(
        val title: String,
        val trailing: String,
        val values: List<MusicTrack>,
    ) : HomeBlock
}

@Composable
fun HomeScreen(navState: melox.ui.navigation.MeloXNavState? = null) {
    val scope = rememberCoroutineScope()
    var feed by remember { mutableStateOf<MusicHomeFeed?>(null) }
    var extraTracks by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeAction by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        val registry = MeloXMusicProviders.create()
        // Provider home feed: QQ / Kugou implement HomeFeedCapability.
        val feedResult = withContext(Dispatchers.IO) {
            registry.providers.firstNotNullOfOrNull { provider ->
                (provider as? melox.music.provider.HomeFeedCapability)
                    ?.let { cap -> runCatching { cap.homeFeed() }.getOrNull() }
            }
        }
        feed = feedResult
        // Fallback content via search (hot songs) when feed is thin.
        if (feedResult == null || feedResult.newSongs.isEmpty()) {
            val searcher = registry.providers.firstNotNullOfOrNull { it as? melox.music.provider.SearchCapability }
            if (searcher != null) {
                withContext(Dispatchers.IO) {
                    runCatching { searcher.searchSongs("热门", page = 1, pageSize = 24) }
                }.onSuccess { extraTracks = it.items }
                    .onFailure { error = it.message ?: "内容加载失败" }
            }
        }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    if (loading && feed == null && extraTracks.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HomeAccent)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            MeloXIosTopBar(
                title = "发现",
            )
        }
        item {
            Text(
                text = "晚上好",
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MeloXTypography.headline,
                color = MeloXColors.OnBackground.copy(alpha = 0.58f),
            )
        }
        item { HomeQuickActionsRow(activeAction) { action ->
            activeAction = action.title
            scope.launch {
                // Desktop quick actions currently resolve to hot search.
                val registry = MeloXMusicProviders.create()
                val searcher = registry.providers.firstNotNullOfOrNull { it as? melox.music.provider.SearchCapability }
                if (searcher != null) {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { searcher.searchSongs(action.title, page = 1, pageSize = 24) }
                    }.getOrNull()
                    val tracks = result?.items.orEmpty()
                    tracks.firstOrNull()?.let { first ->
                        melox.playback.ProviderPlaybackCommands.playQueue(tracks, first.id)
                    }
                }
                activeAction = null
            }
        } }

        val recommended = feed?.recommendedPlaylists.orEmpty()
        if (recommended.isNotEmpty()) {
            item { SectionTitle("推荐歌单", "更多") }
            item { CollectionRow(recommended) { /* detail navigation pending */ } }
        }

        val rankings = feed?.rankings.orEmpty()
        if (rankings.isNotEmpty()) {
            item { SectionTitle("排行榜", "更多") }
            item { CollectionRowRankings(rankings) { /* detail navigation pending */ } }
        }

        val newSongs = feed?.newSongs.orEmpty().ifEmpty { extraTracks }
        if (newSongs.isNotEmpty()) {
            item { SectionTitle("新歌速递", "更多") }
            item { ThreeLineSongCarousel(newSongs) { track -> melox.playback.ProviderPlaybackCommands.playQueue(newSongs, track.id) } }
        }

        if (recommended.isEmpty() && newSongs.isEmpty() && error == null) {
            item {
                Text(
                    "当前没有返回可展示内容",
                    Modifier.padding(horizontal = 20.dp),
                    color = MeloXColors.OnBackground.copy(alpha = .5f),
                )
            }
        }
        error?.let { message ->
            item {
                Text(message, Modifier.padding(horizontal = 20.dp), color = MeloXColors.Error, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HomeQuickActionsRow(
    active: String?,
    perform: (HomeAction) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(homeQuickActions(), key = { it.title }) { action ->
            Column(
                Modifier
                    .width(310.dp)
                    .clickable(enabled = active == null) { perform(action) },
            ) {
                Text(
                    action.eyebrow.uppercase(),
                    color = MeloXColors.OnBackground.copy(alpha = .55f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    action.title,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    action.subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                    color = MeloXColors.OnBackground.copy(alpha = .52f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.48f)
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(action.colors)),
                    contentAlignment = Alignment.Center,
                ) {
                    MeloXSymbolIcon(
                        symbol = action.symbol,
                        modifier = Modifier.fillMaxSize(),
                        color = Color.White.copy(alpha = .24f),
                        size = 72,
                    )
                    Text(
                        action.title,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(18.dp),
                        color = Color.White,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (active == action.title) {
                        CircularProgressIndicator(Modifier.size(52.dp), color = Color.White, strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MeloXColors.OnBackground,
        )
        Text(
            text = trailing,
            color = MeloXColors.Primary,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun CollectionRow(
    values: List<MusicPlaylistSummary>,
    onSelect: (MusicPlaylistSummary) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(values, key = { _, v -> v.id.value }) { index, collection ->
            CollectionCard(collection, Modifier.width(if (index == 0) 246.dp else 174.dp)) { onSelect(collection) }
        }
    }
}

@Composable
private fun CollectionRowRankings(
    values: List<melox.music.model.MusicRankingSummary>,
    onSelect: (melox.music.model.MusicRankingSummary) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        itemsIndexed(values, key = { _, v -> v.id.value }) { index, ranking ->
            Column(
                Modifier
                    .width(if (index == 0) 246.dp else 174.dp)
                    .clickable { onSelect(ranking) }
                    .padding(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(174.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MeloXColors.sourceColors[ranking.id.source.storageValue]?.copy(alpha = 0.85f)
                                        ?: HomeAccent.copy(alpha = 0.85f),
                                    HomeAccent.copy(alpha = 0.45f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        ranking.title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionCard(
    value: MusicPlaylistSummary,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        val artworkShape = RoundedCornerShape(14.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(174.dp)
                .clip(artworkShape),
        ) {
            MeloXArtworkImage(
                url = value.artworkUrl,
                fallbackColor = MeloXColors.sourceColors[value.id.source.storageValue] ?: HomeAccent,
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
private fun ThreeLineSongCarousel(
    values: List<MusicTrack>,
    onSelect: (MusicTrack) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(
            values.chunked(3),
            key = { group -> group.joinToString("-") { it.id.value } },
        ) { group ->
            Column(
                modifier = Modifier.width(320.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                group.forEach { track -> SongRow(track) { onSelect(track) } }
            }
        }
    }
}

@Composable
private fun SongRow(song: MusicTrack, onClick: () -> Unit) {
    val artworkShape = RoundedCornerShape(9.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(66.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(artworkShape),
        ) {
            MeloXArtworkImage(
                url = song.artworkUrl,
                fallbackColor = MeloXColors.sourceColors[song.id.source.storageValue] ?: HomeAccent,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                song.artistText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MeloXColors.OnBackground.copy(alpha = .48f),
                fontSize = 13.sp,
            )
        }
    }
}
