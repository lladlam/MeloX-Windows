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
fun HomeScreen(
    source: MusicSource = MusicSource.Netease,
    onOpenTool: (String) -> Unit = {},
    onOpenPlaylist: (melox.library.NeteasePlaylistSummary) -> Unit = {},
) {
    if (source == MusicSource.Netease) {
        NeteaseHome(onOpenTool, onOpenPlaylist)
    } else {
        ProviderHome(source)
    }
}

private data class NeteaseBlock(val title: String, val trailing: String, val songs: List<melox.model.SearchSong> = emptyList(), val playlists: List<melox.library.NeteasePlaylistSummary> = emptyList())

@Composable
private fun NeteaseHome(
    onOpenTool: (String) -> Unit,
    onOpenPlaylist: (melox.library.NeteasePlaylistSummary) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val session = remember { melox.account.NeteaseSessionStore() }
    val client = remember { melox.library.NeteaseLibraryClient(cookieProvider = { melox.account.NeteaseSessionStore.readCookie() }) }
    val cache = remember { melox.library.NeteaseLibraryCache() }
    var content by remember { mutableStateOf<melox.library.NeteaseHomeContent?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var activeAction by remember { mutableStateOf<String?>(null) }
    var dailySongs by remember { mutableStateOf<List<melox.model.SearchSong>>(emptyList()) }

    val cacheKey = "${session.cookie.hashCode()}_${melox.settings.MeloXSettingsRuntime.musicArea}_${melox.settings.MeloXSettingsRuntime.podcastsEnabled}"

    suspend fun refresh(forceServer: Boolean) {
        if (refreshing) return
        refreshing = true
        runCatching {
            if (session.isLoggedIn && session.profile == null) session.refreshProfile(force = true)
            withContext(Dispatchers.IO) {
                client.homeContent(
                    area = melox.settings.MeloXSettingsRuntime.musicArea,
                    userId = session.profile?.userId,
                    podcastsEnabled = melox.settings.MeloXSettingsRuntime.podcastsEnabled,
                    refresh = forceServer,
                )
            }
        }.onSuccess {
            content = it
            cache.saveHomeContent(cacheKey, it)
            error = null
        }.onFailure { error = it.message ?: "首页加载失败" }
        refreshing = false
    }

    LaunchedEffect(cacheKey) {
        content = cache.loadHomeContent(cacheKey)
        if (session.isLoggedIn && session.profile == null) session.refreshProfile()
        if (melox.library.NeteaseLibraryCache.beginHomeColdStartRefresh(cacheKey)) refresh(false)
    }

    if (dailySongs.isNotEmpty()) {
        DailySongsPage(dailySongs, onBack = { dailySongs = emptyList() })
        return
    }

    val blocks = content?.let { value ->
        buildList {
            melox.settings.MeloXSettingsRuntime.homeSectionOrder.forEach { section ->
                when (section) {
                    "Playlists" -> if (melox.settings.MeloXSettingsRuntime.homePlaylistsEnabled && value.playlists.isNotEmpty())
                        add(NeteaseBlock("每日推荐", "下拉刷新", playlists = value.playlists))
                    "NewSongs" -> if (melox.settings.MeloXSettingsRuntime.homeNewSongsEnabled && value.newSongs.isNotEmpty())
                        add(NeteaseBlock("为你推荐", "新歌", songs = value.newSongs))
                }
            }
            if (value.recentlyTrending.isNotEmpty()) add(NeteaseBlock("近期云村热播", "来自网易云首页", songs = value.recentlyTrending))
            if (value.tailoredSongs.isNotEmpty()) add(NeteaseBlock("根据你的喜好为你推荐", "个性化", songs = value.tailoredSongs))
            if (value.chartPlaylists.isNotEmpty()) add(NeteaseBlock("排行榜", "网易云榜单", playlists = value.chartPlaylists))
            if (value.radarPlaylists.isNotEmpty()) add(NeteaseBlock("私人雷达", "你的雷达歌单", playlists = value.radarPlaylists))
            if (value.personalPlaylists.isNotEmpty()) add(NeteaseBlock("我的歌单", "为你保留", playlists = value.personalPlaylists))
            if (value.regionalSongs.isNotEmpty()) add(NeteaseBlock("${melox.settings.MeloXSettingsRuntime.musicArea}最近热门", "地区推荐", songs = value.regionalSongs))
            if (value.roamingSongs.isNotEmpty()) add(NeteaseBlock("私人漫游", "探索更多", songs = value.roamingSongs))
            if (value.similarSongs.isNotEmpty()) add(NeteaseBlock("相似歌曲", "根据当前播放", songs = value.similarSongs))
        }
    }

    if (content == null && refreshing) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HomeAccent) }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            MeloXIosTopBar(title = "发现", actions = {
                Text(
                    "刷新",
                    modifier = Modifier.clickable { scope.launch { refresh(true) } }.padding(horizontal = 8.dp),
                    color = HomeAccent,
                    fontSize = 14.sp,
                )
                session.profile?.let { profile ->
                    Box(Modifier.size(42.dp).clip(androidx.compose.foundation.shape.CircleShape)) {
                        MeloXArtworkImage(profile.avatarUrl, HomeAccent, Modifier.fillMaxSize())
                    }
                }
            })
        }
        item {
            Text(
                text = homeGreeting(),
                modifier = Modifier.padding(horizontal = 20.dp),
                style = MeloXTypography.headline,
                color = MeloXColors.OnBackground.copy(alpha = 0.58f),
            )
        }
        if (melox.settings.MeloXSettingsRuntime.homeQuickActionsEnabled) {
            item {
                HomeQuickActionsRow(activeAction, neteaseActions()) { action ->
                    if (action.tool != null) { onOpenTool(action.tool); return@HomeQuickActionsRow }
                    activeAction = action.title
                    scope.launch {
                        runCatching { action.run(client, session) }
                            .onSuccess { songs ->
                                if (action.title == "每日推荐") dailySongs = songs
                                else songs.firstOrNull()?.let { melox.playback.PlaybackCommands.playQueue(songs, it.id, heartMode = action.title == "心动模式") }
                                    ?: run { error = "没有可播放的推荐歌曲" }
                            }
                            .onFailure { error = it.message ?: "${action.title} 加载失败" }
                        activeAction = null
                    }
                }
            }
        }
        blocks.orEmpty().forEach { block ->
            item { SectionTitle(block.title, block.trailing) }
            if (block.playlists.isNotEmpty()) item { NeteaseCollectionRow(block.playlists, onOpenPlaylist) }
            if (block.songs.isNotEmpty()) item { NeteaseSongCarousel(block.songs) { song -> melox.playback.PlaybackCommands.playQueue(block.songs, song.id) } }
        }
        if (blocks != null && blocks.isEmpty() && error == null) {
            item { Text("网易云音乐当前没有返回可展示内容", Modifier.padding(horizontal = 20.dp), color = MeloXColors.OnBackground.copy(alpha = .5f)) }
        }
        error?.let { message -> item { Text(message, Modifier.padding(horizontal = 20.dp), color = MeloXColors.Error, fontSize = 13.sp) } }
    }
}

private data class NeteaseAction(val title: String, val eyebrow: String, val subtitle: String, val symbol: MeloXSymbol, val colors: List<Color>, val tool: String? = null, val run: suspend (melox.library.NeteaseLibraryClient, melox.account.NeteaseSessionStore) -> List<melox.model.SearchSong> = { _, _ -> emptyList() })

private fun neteaseActions(): List<NeteaseAction> = buildList {
    add(NeteaseAction("每日推荐", "每日更新", "为你定制的歌曲", MeloXSymbol.Calendar, listOf(Color(0xFFFF5B8A), Color(0xFFFF3147)), run = { c, _ -> c.dailyRecommendedSongs() }))
    add(NeteaseAction("热歌榜", "全站热门", "大家都在听", MeloXSymbol.Flame, listOf(Color(0xFFFFA14A), Color(0xFFFF5A36)), run = { c, _ -> c.hotSongs() }))
    add(NeteaseAction("心动模式", "为你心动", "喜欢与惊喜交替播放", MeloXSymbol.Heart, listOf(Color(0xFFFF6EAC), Color(0xFF9B5DE5)), run = { c, s ->
        val userId = s.profile?.userId ?: error("请先登录网易云音乐")
        val snapshot = c.snapshot(userId)
        val seed = snapshot.likedSongs.randomOrNull() ?: error("收藏歌曲为空")
        val playlistId = snapshot.likedPlaylistId ?: error("没有找到“我喜欢的音乐”歌单")
        c.intelligenceModeSongs(seed.id, playlistId)
    }))
    add(NeteaseAction("私人雷达", "持续发现", "发现符合你口味的歌单", MeloXSymbol.Podcast, listOf(Color(0xFF6B7BFF), Color(0xFF8C52FF)), run = { c, s ->
        val userId = s.profile?.userId ?: error("请先登录网易云音乐")
        val snapshot = c.snapshot(userId)
        val radar = snapshot.playlists.firstOrNull { it.name.contains("雷达") } ?: error("当前账号没有可用的私人雷达")
        c.playlistDetail(radar.id).songs
    }))
    add(NeteaseAction("私人漫游", "探索模式", "漫游到新的好音乐", MeloXSymbol.Walk, listOf(Color(0xFF26C6DA), Color(0xFF4285F4)), run = { c, _ -> c.personalFm(explore = true) }))
    add(NeteaseAction("相似歌曲", "从当前歌曲出发", "播放更多相似歌曲", MeloXSymbol.ListBullet, listOf(Color(0xFF58C9A3), Color(0xFF159D9A)), run = { c, _ ->
        val id = melox.playback.PlaybackCommands.currentSongId() ?: error("请先播放一首歌曲")
        c.similarSongsBlocking(id)
    }))
    add(NeteaseAction("听歌识曲", "快捷工具", "识别环境中正在播放的歌曲", MeloXSymbol.Mic, listOf(Color(0xFF7B61FF), Color(0xFF36C5F0)), tool = "Recognition"))
    add(NeteaseAction("私信", "网易云社交", "查看联系人和私信会话", MeloXSymbol.Message, listOf(Color(0xFFFF6B8B), Color(0xFFFF3B30)), tool = "Messages"))
    if (melox.settings.MeloXSettingsRuntime.podcastsEnabled && melox.settings.MeloXSettingsRuntime.podcastsHomePlacement)
        add(NeteaseAction("播客", "首页页面", "浏览播客与节目", MeloXSymbol.Podcast, listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)), tool = "Podcasts"))
    if (melox.settings.MeloXSettingsRuntime.downloadsEnabled && melox.settings.MeloXSettingsRuntime.downloadsHomePlacement)
        add(NeteaseAction("下载", "本地音乐", "浏览已下载的歌曲", MeloXSymbol.Download, listOf(Color(0xFF0EA5E9), Color(0xFF14B8A6)), tool = "Downloads"))
    if (melox.settings.MeloXSettingsRuntime.cloudMusicEnabled && melox.settings.MeloXSettingsRuntime.cloudHomePlacement)
        add(NeteaseAction("云盘", "个人音乐", "打开网易云音乐云盘", MeloXSymbol.Drive, listOf(Color(0xFF64748B), Color(0xFF6366F1)), tool = "Cloud"))
}

private fun homeGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    return when {
        hour < 5 -> "夜深了"
        hour < 11 -> "早上好"
        hour < 14 -> "中午好"
        hour < 18 -> "下午好"
        else -> "晚上好"
    }
}

@Composable
private fun DailySongsPage(songs: List<melox.model.SearchSong>, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp)) {
        item { MeloXIosTopBar(title = "每日推荐", onBack = onBack) }
        item {
            Text(
                "播放全部",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).clip(RoundedCornerShape(22.dp)).background(HomeAccent).clickable { songs.firstOrNull()?.let { melox.playback.PlaybackCommands.playQueue(songs, it.id) } }.padding(horizontal = 16.dp, vertical = 10.dp),
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }
        items(songs.size) { index -> NeteaseSongRow(songs[index]) { melox.playback.PlaybackCommands.playQueue(songs, songs[index].id) } }
    }
}

@Composable
private fun NeteaseCollectionRow(values: List<melox.library.NeteasePlaylistSummary>, onSelect: (melox.library.NeteasePlaylistSummary) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(values, key = { _, v -> v.id }) { index, playlist ->
            Column(Modifier.width(if (index == 0) 246.dp else 174.dp).clickable { onSelect(playlist) }.padding(8.dp)) {
                Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(14.dp))) {
                    MeloXArtworkImage(playlist.coverUrl, HomeAccent, Modifier.fillMaxSize())
                }
                Text(playlist.name, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold)
                if (melox.settings.MeloXSettingsRuntime.showPlaylistPlayCount && playlist.playCount > 0L) {
                    Text(compactPlayCount(playlist.playCount) + " 次播放", color = MeloXColors.OnBackground.copy(alpha = .42f), fontSize = 11.sp)
                }
            }
        }
    }
}

private fun compactPlayCount(value: Long): String = when {
    value >= 100_000_000L -> "%.1f亿".format(value / 100_000_000.0)
    value >= 10_000L -> "%.1f万".format(value / 10_000.0)
    else -> value.toString()
}

@Composable
private fun NeteaseSongCarousel(values: List<melox.model.SearchSong>, onSelect: (melox.model.SearchSong) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        items(values.chunked(3), key = { group -> group.joinToString("-") { it.id.toString() } }) { group ->
            Column(Modifier.width(320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                group.forEach { song -> NeteaseSongRow(song) { onSelect(song) } }
            }
        }
    }
}

@Composable
private fun NeteaseSongRow(song: melox.model.SearchSong, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(66.dp).clickable(onClick = onClick).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(9.dp))) { MeloXArtworkImage(song.artworkUrl, HomeAccent, Modifier.fillMaxSize()) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnBackground.copy(alpha = .48f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ProviderHome(source: MusicSource) {
    val scope = rememberCoroutineScope()
    var feed by remember(source) { mutableStateOf<MusicHomeFeed?>(null) }
    var loading by remember(source) { mutableStateOf(true) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    LaunchedEffect(source) {
        loading = true
        val registry = melox.music.provider.MeloXMusicProviders.create()
        val provider = runCatching { registry.require(source) }.getOrNull()
        val home = provider as? melox.music.provider.HomeFeedCapability
        if (home == null) { error = "${source.displayName} 暂未提供首页数据"; loading = false; return@LaunchedEffect }
        runCatching { withContext(Dispatchers.IO) { home.homeFeed() } }
            .onSuccess { feed = it; error = null }
            .onFailure { error = it.message ?: "首页加载失败" }
        loading = false
    }
    if (loading && feed == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = HomeAccent) }
        return
    }
    val recommended = feed?.recommendedPlaylists.orEmpty()
    val rankings = feed?.rankings.orEmpty()
    val newSongs = feed?.newSongs.orEmpty()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 18.dp, bottom = 146.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item { MeloXIosTopBar(title = "发现") }
        item { Text(homeGreeting(), Modifier.padding(horizontal = 20.dp), style = MeloXTypography.headline, color = MeloXColors.OnBackground.copy(alpha = 0.58f)) }
        if (recommended.isNotEmpty()) { item { SectionTitle("推荐歌单", source.displayName) }; item { CollectionRow(recommended) {} } }
        if (rankings.isNotEmpty()) { item { SectionTitle("排行榜", source.displayName) }; item { CollectionRowRankings(rankings) {} } }
        if (newSongs.isNotEmpty()) { item { SectionTitle("为你推荐", "新歌") }; item { ThreeLineSongCarousel(newSongs) { track -> melox.playback.ProviderPlaybackCommands.playQueue(newSongs, track.id) } } }
        if (recommended.isEmpty() && rankings.isEmpty() && newSongs.isEmpty()) {
            item { Text(error ?: "${source.displayName} 当前没有返回可展示内容", Modifier.padding(horizontal = 20.dp), color = if (error != null) MeloXColors.Error else MeloXColors.OnBackground.copy(alpha = .5f)) }
        }
    }
}

@Composable
private fun HomeQuickActionsRow(
    active: String?,
    actions: List<NeteaseAction> = emptyList(),
    perform: (NeteaseAction) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(actions, key = { it.title }) { action ->
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
