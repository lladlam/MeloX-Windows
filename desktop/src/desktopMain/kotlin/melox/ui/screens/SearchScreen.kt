package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicAlbumSummary
import melox.music.model.MusicArtistSummary
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.CatalogSearchCapability
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.SearchCapability
import melox.network.MeloXSearchKind
import melox.network.MeloXSearchMediaItem
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.playback.ProviderPlaybackCommands
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXSearchBackMorphIcon
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassTextField
import melox.ui.glass.MeloXShapes
import melox.ui.glass.MeloXSystemColors
import melox.ui.theme.MeloXColors

private val SearchAccent = Color(0xFF0A84FF)
private val SearchCategories = listOf(
    "推荐歌单", "排行榜", "精品歌单", "华语", "欧美", "流行", "摇滚", "民谣", "电子", "轻音乐", "影视原声", "ACG",
)

private enum class SearchBackAction { ClearOverlay, ClearQuery, SwitchToHome }

@Composable
fun SearchScreen(
    source: MusicSource = MusicSource.Netease,
    onSearchExit: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val cookie = remember { { NeteaseSessionStore.readCookie() } }
    val universal = remember { NeteaseUniversalSearchClient(cookieProvider = cookie) }
    val library = remember { NeteaseLibraryClient(cookie) }
    val providerRegistry = remember { MeloXMusicProviders.create() }
    val currentProvider = remember(source) { runCatching { providerRegistry.require(source) }.getOrNull() }
    val providerSongSearch = currentProvider as? SearchCapability
    val providerCatalog = currentProvider as? CatalogSearchCapability

    val availableKinds = remember(source, providerCatalog) {
        if (source == MusicSource.Netease) MeloXSearchKind.entries
        else buildList {
            add(MeloXSearchKind.Songs)
            if (providerCatalog != null) {
                add(MeloXSearchKind.Playlists)
                add(MeloXSearchKind.Albums)
                add(MeloXSearchKind.Artists)
            }
        }
    }

    var query by rememberSaveable(source.name) { mutableStateOf("") }
    var searchTrigger by rememberSaveable(source.name) { mutableIntStateOf(0) }
    var skipSearchDebounce by rememberSaveable(source.name) { mutableStateOf(false) }
    var kind by rememberSaveable(source.name) { mutableStateOf(MeloXSearchKind.Songs) }
    var songs by remember(source) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var providerSongs by remember(source) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var providerPlaylists by remember(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var providerAlbums by remember(source) { mutableStateOf<List<MusicAlbumSummary>>(emptyList()) }
    var providerArtists by remember(source) { mutableStateOf<List<MusicArtistSummary>>(emptyList()) }
    var recommendations by remember(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var media by remember(source) { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var categoryTitle by remember(source) { mutableStateOf<String?>(null) }
    var categoryPlaylists by remember(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedDetail by remember(source) { mutableStateOf<MeloXSearchMediaItem?>(null) }
    var loading by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedActionSong by remember(source) { mutableStateOf<SearchSong?>(null) }

    LaunchedEffect(source, availableKinds) {
        if (kind !in availableKinds) kind = MeloXSearchKind.Songs
    }
    LaunchedEffect(source) {
        if (source == MusicSource.Netease) {
            runCatching { library.explorePlaylists("推荐歌单", 10) }
                .onSuccess { recommendations = it }
        }
    }
    LaunchedEffect(query, kind, source, searchTrigger) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            songs = emptyList()
            providerSongs = emptyList()
            providerPlaylists = emptyList()
            providerAlbums = emptyList()
            providerArtists = emptyList()
            media = emptyList()
            error = null
            loading = false
            return@LaunchedEffect
        }
        if (!skipSearchDebounce) delay(1500)
        skipSearchDebounce = false
        loading = true
        error = null
        val linkedId = if (source == MusicSource.Netease) parseSongLink(keyword) else null
        if (linkedId != null) {
            runCatching { universal.songDetail(linkedId) }
                .onSuccess { songs = listOfNotNull(it); media = emptyList(); kind = MeloXSearchKind.Songs }
                .onFailure { error = it.message ?: "无法读取歌曲链接" }
            loading = false
            return@LaunchedEffect
        }
        when (kind) {
            MeloXSearchKind.Songs -> {
                media = emptyList()
                if (source == MusicSource.Netease) {
                    providerSongs = emptyList()
                    runCatching { universal.searchSongs(keyword, page = 1, pageSize = 50) }
                        .onSuccess { songs = it.items.map { track -> track.toSearchSong() } }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    songs = emptyList()
                    val capability = providerSongSearch
                    if (capability == null) {
                        providerSongs = emptyList()
                        error = "${source.displayName} 当前没有歌曲搜索能力"
                    } else {
                        runCatching { capability.searchSongs(keyword, page = 1, pageSize = 50).items }
                            .onSuccess { providerSongs = it }
                            .onFailure { error = it.message ?: "搜索失败" }
                    }
                }
            }
            MeloXSearchKind.Playlists, MeloXSearchKind.Albums, MeloXSearchKind.Artists -> {
                songs = emptyList()
                providerSongs = emptyList()
                if (source == MusicSource.Netease) {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it }
                        .onFailure { error = it.message ?: "搜索失败" }
                } else {
                    media = emptyList()
                    val capability = providerCatalog
                    if (capability == null) error = "${source.displayName} 当前没有${kind.title}搜索"
                    else when (kind) {
                        MeloXSearchKind.Playlists -> runCatching { capability.searchPlaylists(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerPlaylists = it }.onFailure { error = it.message ?: "搜索失败" }
                        MeloXSearchKind.Albums -> runCatching { capability.searchAlbums(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerAlbums = it }.onFailure { error = it.message ?: "搜索失败" }
                        MeloXSearchKind.Artists -> runCatching { capability.searchArtists(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerArtists = it }.onFailure { error = it.message ?: "搜索失败" }
                        else -> Unit
                    }
                }
            }
            else -> {
                if (source != MusicSource.Netease) {
                    media = emptyList()
                    error = "${source.displayName} 不提供${kind.title}搜索"
                } else {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it; songs = emptyList() }
                        .onFailure { error = it.message ?: "搜索失败" }
                }
            }
        }
        loading = false
    }

    val backAction = when {
        selectedDetail != null || categoryTitle != null -> SearchBackAction.ClearOverlay
        query.isNotBlank() -> SearchBackAction.ClearQuery
        else -> SearchBackAction.SwitchToHome
    }
    fun consumeBack() {
        when (backAction) {
            SearchBackAction.ClearOverlay -> {
                selectedDetail = null
                categoryTitle = null
                categoryPlaylists = emptyList()
                error = null
            }
            SearchBackAction.ClearQuery -> {
                query = ""
                skipSearchDebounce = false
                searchTrigger += 1
            }
            SearchBackAction.SwitchToHome -> onSearchExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    consumeBack()
                    true
                } else false
            },
    ) {
        when {
            selectedDetail != null -> SearchCollectionDetail(
                item = selectedDetail!!,
                universal = universal,
                onBack = { selectedDetail = null },
            )
            categoryTitle != null -> SearchCategoryPage(
                title = categoryTitle!!,
                playlists = categoryPlaylists,
                loading = loading,
                error = error,
                onBack = { categoryTitle = null; categoryPlaylists = emptyList(); error = null },
                onPlaylist = { selectedDetail = it.asSearchItem() },
            )
            else -> Column(Modifier.fillMaxSize().padding(top = 26.dp)) {
                MeloXIosTopBar(title = "搜索")
                Spacer(Modifier.height(16.dp))
                SearchField(
                    value = query,
                    onValueChange = { query = it; skipSearchDebounce = false },
                    onSearch = {
                        skipSearchDebounce = true
                        searchTrigger += 1
                        rememberSearchQuery(query)
                    },
                    onBack = { if (query.isNotBlank()) query = "" else onSearchExit() },
                )
                if (query.isNotBlank()) {
                    SearchScopes(kind = kind, availableKinds = availableKinds, onKind = { kind = it })
                }
                Box(Modifier.weight(1f)) {
                    when {
                        query.isBlank() && searchHistory().isNotEmpty() -> SearchHistoryList(
                            values = searchHistory(),
                            onPick = { picked ->
                                query = picked
                                skipSearchDebounce = true
                                searchTrigger += 1
                            },
                            onClear = { clearSearchHistory() },
                        )
                        query.isBlank() && source == MusicSource.Netease -> SearchDiscovery(
                            recommendations = recommendations,
                            onPlaylist = { selectedDetail = it.asSearchItem() },
                            onCategory = { category ->
                                categoryTitle = category
                                loading = true
                                error = null
                                scope.launch {
                                    runCatching { library.explorePlaylists(category, 50) }
                                        .onSuccess { categoryPlaylists = it }
                                        .onFailure { error = it.message ?: "类别加载失败" }
                                    loading = false
                                }
                            },
                        )
                        query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "搜索 ${source.displayName} 的歌曲、歌单、专辑或歌手",
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                            )
                        }
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SearchAccent)
                        }
                        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        }
                        kind == MeloXSearchKind.Songs && source != MusicSource.Netease -> ProviderSearchSongResults(
                            values = providerSongs,
                            onPlay = { track ->
                                ProviderPlaybackCommands.playQueue(
                                    tracks = providerSongs,
                                    selectedTrackId = track.id,
                                    onFailure = { failure -> error = failure.message ?: "播放失败" },
                                )
                            },
                        )
                        kind == MeloXSearchKind.Songs -> SearchSongResults(
                            values = songs,
                            onPlay = { song -> PlaybackCommands.playQueue(songs, song.id) },
                            onMore = { selectedActionSong = it },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Playlists -> ProviderMediaResults(
                            titles = providerPlaylists.map { it.title to (it.creatorName.orEmpty()) },
                            artworks = providerPlaylists.map { it.artworkUrl },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Albums -> ProviderMediaResults(
                            titles = providerAlbums.map { it.title to "" },
                            artworks = providerAlbums.map { it.artworkUrl },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Artists -> ProviderMediaResults(
                            titles = providerArtists.map { it.name to "" },
                            artworks = providerArtists.map { it.artworkUrl },
                        )
                        else -> SearchMediaResults(media) { selectedDetail = it }
                    }
                }
            }
        }
        selectedActionSong?.let { song ->
            MeloXSongActionsOverlay(
                song = song,
                queue = songs,
                visible = true,
                onDismiss = { selectedActionSong = null },
            )
        }
    }
}

private fun NeteasePlaylistSummary.asSearchItem() = MeloXSearchMediaItem(
    id = id,
    kind = MeloXSearchKind.Playlists,
    title = name,
    subtitle = creatorName,
    artworkUrl = coverUrl,
    trackCount = trackCount,
)

private fun MusicTrack.toSearchSong() = SearchSong(
    id = id.value.toLongOrNull() ?: id.value.hashCode().toLong(),
    name = title,
    artists = artistText,
    album = album?.name.orEmpty(),
    artworkUrl = artworkUrl,
    durationMs = durationMs ?: 0L,
    providerTrack = this,
)

private fun parseSongLink(value: String): Long? {
    val match = Regex("""(?:id=|/song/|song\?id=)(\d{3,})""").find(value) ?: return null
    return match.groupValues.getOrNull(1)?.toLongOrNull()
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    MeloXGlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.padding(horizontal = 20.dp),
        leadingContent = {
            MeloXSearchBackMorphIcon(
                focused = focused,
                modifier = Modifier
                    .size(44.dp)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(11.dp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
            )
        },
        placeholder = {
            Text("搜索歌曲、歌手、专辑", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 17.sp)
        },
        trailingContent = {
            if (value.isNotBlank()) {
                Row {
                    Box(
                        modifier = Modifier.size(44.dp).clickable(role = Role.Button) { onSearch() }.semantics { contentDescription = "搜索" },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.Search, Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), size = 18)
                    }
                    Box(
                        modifier = Modifier.size(44.dp).clickable(role = Role.Button) { onValueChange("") }.semantics { contentDescription = "清除" },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.XMark, Modifier.size(15.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), size = 15)
                    }
                }
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 17.sp,
            lineHeight = 22.sp,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
        onFocusChanged = { focused = it },
    )
}

@Composable
private fun SearchScopes(
    kind: MeloXSearchKind,
    availableKinds: List<MeloXSearchKind>,
    onKind: (MeloXSearchKind) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(availableKinds) { item ->
            MeloXGlassButton(
                onClick = { onKind(item) },
                modifier = Modifier.height(44.dp),
                style = MeloXGlassButtonStyle.Bordered,
                shape = MeloXShapes.capsule,
                tint = if (item == kind) MeloXSystemColors.Blue.copy(alpha = .28f) else Color.Transparent,
                surfaceColor = if (item == kind) MeloXSystemColors.Blue.copy(alpha = .16f) else MaterialTheme.colorScheme.onBackground.copy(alpha = .045f),
                contentPadding = PaddingValues(horizontal = 15.dp),
            ) {
                Text(
                    item.title,
                    color = if (item == kind) SearchAccent else MaterialTheme.colorScheme.onSurface,
                    fontWeight = if (item == kind) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SearchDiscovery(
    recommendations: List<NeteasePlaylistSummary>,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
    onCategory: (String) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        if (recommendations.isNotEmpty()) {
            item { Text("热门推荐", modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MeloXColors.OnSurface) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(recommendations, key = { it.id }) { playlist ->
                        Column(Modifier.width(160.dp).clickable { onPlaylist(playlist) }) {
                            MeloXArtworkImage(playlist.coverUrl, MeloXColors.SurfaceVariant, Modifier.size(160.dp).clip(RoundedCornerShape(14.dp)))
                            Text(playlist.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, color = MeloXColors.OnSurface)
                        }
                    }
                }
            }
        }
        item { Text("浏览类别", modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MeloXColors.OnSurface) }
        items(SearchCategories.chunked(2)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category -> SearchCategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SearchCategoryCard(title: String, modifier: Modifier, onClick: () -> Unit) {
    val tint = when (title.hashCode().mod(5)) {
        0 -> Color(0xFFE76F51)
        1 -> Color(0xFF7B61FF)
        2 -> Color(0xFF2A9D8F)
        3 -> Color(0xFFE84A8A)
        else -> Color(0xFF3A86FF)
    }
    Box(
        modifier
            .height(72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

private fun searchPreferences() = melox.platform.getPreferences("melox_search_history")

private fun searchHistory(): List<String> =
    searchPreferences().getString("history", "").orEmpty().split('\n').filter { it.isNotBlank() }

private fun rememberSearchQuery(value: String) {
    val keyword = value.trim()
    if (keyword.isBlank()) return
    val next = listOf(keyword) + searchHistory().filterNot { it == keyword }
    searchPreferences().putString("history", next.take(12).joinToString("\n"))
}

private fun clearSearchHistory() {
    searchPreferences().putString("history", "")
}

@Composable
private fun SearchHistoryList(
    values: List<String>,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("最近搜索", color = MeloXColors.OnSurface, fontWeight = FontWeight.SemiBold)
            Text("清除", modifier = Modifier.clickable(onClick = onClear), color = SearchAccent, fontSize = 13.sp)
        }
        values.forEach { item ->
            Text(
                item,
                modifier = Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 10.dp),
                color = MeloXColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SearchSongResults(
    values: List<SearchSong>,
    onPlay: (SearchSong) -> Unit,
    onMore: ((SearchSong) -> Unit)? = null,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        items(values, key = { it.id }) { song ->
            Row(
                Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onPlay(song) },
                    onLongClick = { onMore?.invoke(song) },
                ).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(song.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium)
                    Text(song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ProviderSearchSongResults(values: List<MusicTrack>, onPlay: (MusicTrack) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        items(values, key = { "${it.id.source.storageValue}:${it.id.value}" }) { track ->
            Row(
                Modifier.fillMaxWidth().clickable { onPlay(track) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(track.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium)
                    Text(track.artistText, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchMediaResults(values: List<MeloXSearchMediaItem>, onOpen: (MeloXSearchMediaItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        items(values, key = { "${it.kind.name}:${it.id}" }) { item ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(item) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(item.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium)
                    if (item.subtitle.isNotBlank()) Text(item.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun ProviderMediaResults(titles: List<Pair<String, String>>, artworks: List<String?>) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        items(titles.size) { index ->
            val (title, subtitle) = titles[index]
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(artworks.getOrNull(index), MeloXColors.SurfaceVariant, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium)
                    if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryPage(
    title: String,
    playlists: List<NeteasePlaylistSummary>,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onPlaylist: (NeteasePlaylistSummary) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(top = 26.dp)) {
        MeloXIosTopBar(title = title, onBack = onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error, color = MaterialTheme.colorScheme.error) }
            else -> LazyColumn(contentPadding = PaddingValues(20.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(playlist) }, verticalAlignment = Alignment.CenterVertically) {
                        MeloXArtworkImage(playlist.coverUrl, MeloXColors.SurfaceVariant, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(playlist.name, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.trackCount} 首 · ${playlist.creatorName}", color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCollectionDetail(
    item: MeloXSearchMediaItem,
    universal: NeteaseUniversalSearchClient,
    onBack: () -> Unit,
) {
    var songs by remember(item.id, item.kind) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var loading by remember(item.id) { mutableStateOf(true) }
    var error by remember(item.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.id, item.kind) {
        loading = true
        runCatching { universal.collectionSongs(item) }
            .onSuccess { songs = it }
            .onFailure { error = it.message ?: "加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(top = 26.dp)) {
        MeloXIosTopBar(title = item.title, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXArtworkImage(item.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.padding(start = 16.dp)) {
                Text(item.title, color = MeloXColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MeloXColors.OnSurfaceVariant)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> SearchSongResults(songs, onPlay = { song -> PlaybackCommands.playQueue(songs, song.id) })
        }
    }
}
