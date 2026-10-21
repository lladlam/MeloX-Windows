package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.music.provider.HomeFeedCapability
import melox.network.NeteaseMusicOperationsClient
import melox.ui.legal.pinnedString
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.music.model.MusicAlbumSummary
import melox.music.model.MusicArtistSummary
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.AlbumCapability
import melox.music.provider.ArtistCapability
import melox.music.provider.CatalogSearchCapability
import melox.music.provider.MeloXLegacyUiBridge
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.PlaylistCapability
import melox.music.provider.SearchCapability
import melox.network.MeloXSearchKind
import melox.network.MeloXSearchMediaItem
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.playback.ProviderPlaybackCommands
import melox.settings.MeloXSettingsRuntime
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
    "排行榜", "播客", "华语", "欧美", "日语", "韩语", "粤语", "流行", "摇滚", "民谣", "电子", "说唱", "R&B/Soul", "古典", "ACG", "影视原声", "学习", "工作", "放松", "夜晚",
)
private val SearchCategoryResourceKeys = listOf("charts", "podcasts", "chinese", "western", "japanese", "korean", "cantonese", "pop", "rock", "folk", "electronic", "hiphop", "rnb", "classical", "acg", "soundtrack", "study", "work", "relax", "night")
private fun searchCategoryLabel(id: String): String = SearchCategories.indexOf(id).takeIf { it >= 0 }
    ?.let { pinnedString("search_cat_${SearchCategoryResourceKeys[it]}") } ?: id

private enum class SearchBackAction { ClearOverlay, ClearQuery, SwitchToHome }

private sealed interface SearchDetailDestination {
    data class Playlist(val value: MusicPlaylistSummary) : SearchDetailDestination
    data class Album(val value: MusicAlbumSummary) : SearchDetailDestination
    data class Artist(val value: MusicArtistSummary) : SearchDetailDestination
    data class Media(val value: MeloXSearchMediaItem) : SearchDetailDestination
}

@Composable
fun SearchScreen(
    source: MusicSource = MusicSource.Netease,
    onSearchExit: () -> Unit = {},
    onOpenPodcasts: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val session = melox.account.rememberNeteaseSessionStore()
    val sessionRevision by NeteaseSessionStore.revision.collectAsState()
    val sessionCookie = remember(sessionRevision) { NeteaseSessionStore.readCookie() }
    val cookie = remember(sessionCookie) { { sessionCookie } }
    val universal = remember(cookie) { NeteaseUniversalSearchClient(cookieProvider = cookie) }
    val songClient = remember(cookie) { melox.network.NeteaseSearchClient(cookieProvider = cookie) }
    val library = remember(cookie) { NeteaseLibraryClient(cookie) }
    val operations = remember(cookie) { NeteaseMusicOperationsClient(cookie) }
    val providerRegistry = remember { MeloXMusicProviders.create() }
    val unifiedService = remember(providerRegistry) { melox.music.provider.UnifiedMusicService(providerRegistry) }
    val selectionRevision by melox.music.provider.MusicProviderSelectionStore.revision.collectAsState()
    val unifiedEnabled = remember(selectionRevision) { melox.music.provider.MusicProviderSelectionStore.unifiedEnabled() }
    val unifiedSources = remember(selectionRevision) { melox.music.provider.MusicProviderSelectionStore.unifiedSources() }
    var unifiedFailures by remember(source) { mutableStateOf<List<melox.music.provider.UnifiedMusicService.SearchFailure>>(emptyList()) }
    val currentProvider = remember(source) { runCatching { providerRegistry.require(source) }.getOrNull() }
    val providerSongSearch = currentProvider as? SearchCapability
    val providerCatalog = currentProvider as? CatalogSearchCapability

    val podcastsEnabled = melox.settings.MeloXSettingsRuntime.podcastsEnabled
    val availableKinds = remember(source, providerCatalog, podcastsEnabled) {
        if (source == MusicSource.Netease) MeloXSearchKind.entries.filter { it != MeloXSearchKind.Podcasts || podcastsEnabled }
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
    var providerRecommendations by remember(source) { mutableStateOf<List<MusicPlaylistSummary>>(emptyList()) }
    var history by remember { mutableStateOf(searchHistory()) }
    var categoryLoading by remember(source, sessionRevision) { mutableStateOf(false) }
    var categoryError by remember(source, sessionRevision) { mutableStateOf<String?>(null) }
    var media by remember(source) { mutableStateOf<List<MeloXSearchMediaItem>>(emptyList()) }
    var categoryTitle by remember(source) { mutableStateOf<String?>(null) }
    var categoryPlaylists by remember(source) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var selectedDetail by remember(source) { mutableStateOf<MeloXSearchMediaItem?>(null) }
    var selectedProviderPlaylist by remember(source) { mutableStateOf<MusicPlaylistSummary?>(null) }
    var selectedProviderAlbum by remember(source) { mutableStateOf<MusicAlbumSummary?>(null) }
    var selectedProviderArtist by remember(source) { mutableStateOf<MusicArtistSummary?>(null) }
    val destinationFocus = remember { FocusRequester() }
    LaunchedEffect(selectedDetail?.id, selectedProviderPlaylist?.id, selectedProviderAlbum?.id, selectedProviderArtist?.id, categoryTitle) {
        destinationFocus.requestFocus()
    }
    var loading by remember(source) { mutableStateOf(false) }
    var error by remember(source) { mutableStateOf<String?>(null) }
    var selectedActionSong by remember(source) { mutableStateOf<SearchSong?>(null) }
    // DesktopSearchView scope: catalog vs the signed-in user's library.
    var libraryScope by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var librarySongsLoaded by remember(sessionRevision) { mutableStateOf(false) }
    var libraryLiked by remember(sessionRevision) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var libraryRecent by remember(sessionRevision) { mutableStateOf<List<SearchSong>>(emptyList()) }
    var libraryPlaylists by remember(sessionRevision) { mutableStateOf<List<NeteasePlaylistSummary>>(emptyList()) }
    var libraryAlbums by remember(sessionRevision) { mutableStateOf<List<melox.network.MeloXAlbumSummary>>(emptyList()) }
    var libraryArtists by remember(sessionRevision) { mutableStateOf<List<melox.network.MeloXLibraryArtistSummary>>(emptyList()) }
    var libraryPodcasts by remember(sessionRevision) { mutableStateOf<List<melox.network.MeloXPodcast>>(emptyList()) }
    val details = remember(sessionRevision) { melox.network.NeteaseCollectionDetailsClient({ sessionCookie }) }
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    var songLinkResult by remember(source) { mutableStateOf(false) }
    val launch = melox.ui.navigation.MeloXSearchLaunchBus.request
    LaunchedEffect(launch, source) {
        if (launch == null || launch.source != null && launch.source != source) return@LaunchedEffect
        query = launch.query
        kind = launch.kind.takeIf { it in availableKinds } ?: MeloXSearchKind.Songs
        skipSearchDebounce = true
        searchTrigger += 1
        selectedDetail = null
        selectedProviderPlaylist = null; selectedProviderAlbum = null; selectedProviderArtist = null
        categoryTitle = null
        melox.ui.navigation.MeloXSearchLaunchBus.consume(launch)
    }

    LaunchedEffect(source, availableKinds) {
        if (kind !in availableKinds || (MeloXSettingsRuntime.desktopLayoutEnabled && kind == MeloXSearchKind.Users)) {
            kind = MeloXSearchKind.Songs
        }
    }
    LaunchedEffect(source, sessionRevision) {
        if (source == MusicSource.Netease) {
            runCatching { library.explorePlaylists("推荐歌单", 10) }
                .onSuccess { recommendations = it }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        } else {
            runCatching { withContext(Dispatchers.IO) {
                (currentProvider as? HomeFeedCapability)?.homeFeed(playlistLimit = 10, newSongLimit = 0, rankingLimit = 0)
            } }.onSuccess { providerRecommendations = it?.recommendedPlaylists.orEmpty() }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }
    LaunchedEffect(categoryTitle, source, sessionRevision) {
        val category = categoryTitle ?: return@LaunchedEffect
        categoryLoading = true; categoryError = null; categoryPlaylists = emptyList()
        try { categoryPlaylists = library.explorePlaylists(category, 50) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (failure: Exception) { categoryError = failure.message ?: pinnedString("search_category_failed") }
        finally { categoryLoading = false }
    }
    // DesktopSearchView suggestions use a 300ms debounce and only the catalog scope.
    LaunchedEffect(query, libraryScope, sessionRevision) {
        if (query.isBlank() || libraryScope || sessionCookie.isBlank()) { searchSuggestions = emptyList(); return@LaunchedEffect }
        delay(300)
        searchSuggestions = runCatching { universal.searchSuggestions(query) }.getOrDefault(emptyList())
    }

    LaunchedEffect(query, kind, source, searchTrigger, unifiedEnabled, unifiedSources, sessionRevision, libraryScope) {
        unifiedFailures = emptyList()
        songLinkResult = false
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
        error = null
        if (libraryScope) {
            songs = emptyList(); providerSongs = emptyList(); media = emptyList(); loading = true
            try {
                if (!NeteaseSessionStore.containsMusicU(sessionCookie)) {
                    error = melox.ui.desktop.desktopString("ui.desktop.library.sign_in_message")
                } else {
                    if (!librarySongsLoaded) {
                        val user = session.profile?.userId ?: throw IllegalStateException(melox.ui.desktop.desktopString("ui.desktop.library.sign_in_message"))
                        val snapshot = library.snapshot(user)
                        libraryLiked = snapshot.likedSongs
                        libraryRecent = snapshot.recentSongs
                        libraryPlaylists = snapshot.playlists
                        libraryAlbums = details.subscribedAlbums()
                        libraryArtists = details.subscribedArtists()
                        libraryPodcasts = universal.subscribedPodcasts()
                        librarySongsLoaded = true
                    }
                    fun matches(vararg values: String?) = values.filterNotNull().any { it.contains(keyword, ignoreCase = true) }
                    when (kind) {
                        MeloXSearchKind.Songs -> songs = (libraryLiked + libraryRecent).distinctBy { it.id }
                            .filter { matches(it.name, it.artists, it.album) }
                        MeloXSearchKind.Playlists -> media = libraryPlaylists.filter { matches(it.name, it.creatorName) }
                            .map { MeloXSearchMediaItem(it.id, MeloXSearchKind.Playlists, it.name, it.creatorName, it.coverUrl, it.trackCount) }
                        MeloXSearchKind.Albums -> media = libraryAlbums.filter { matches(it.name, it.artistText) }
                            .map { MeloXSearchMediaItem(it.id, MeloXSearchKind.Albums, it.name, it.artistText, it.artworkUrl, it.size ?: 0) }
                        MeloXSearchKind.Artists -> media = libraryArtists.filter { matches(it.name, it.aliases.joinToString(" ")) }
                            .map { MeloXSearchMediaItem(it.id, MeloXSearchKind.Artists, it.name, it.aliases.firstOrNull().orEmpty(), it.artworkUrl) }
                        MeloXSearchKind.Podcasts -> media = libraryPodcasts.filter { matches(it.name, it.host?.nickname, it.category) }
                            .map { MeloXSearchMediaItem(it.id, MeloXSearchKind.Podcasts, it.name, it.host?.nickname.orEmpty(), it.artworkUrl, it.programCount) }
                        else -> Unit
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: melox.ui.desktop.desktopString("ui.desktop.search.failed") }
            finally { loading = false }
            return@LaunchedEffect
        }
        if (!skipSearchDebounce) delay(if (MeloXSettingsRuntime.desktopLayoutEnabled) 320 else 1500)
        skipSearchDebounce = false
        loading = true
        val linkedId = if (source == MusicSource.Netease) parseSongLink(keyword) else null
        if (linkedId != null) {
            runCatching { universal.songDetail(linkedId) }
                .onSuccess {
                    songs = listOfNotNull(it)
                    providerSongs = emptyList()
                    media = emptyList()
                    songLinkResult = true
                    kind = MeloXSearchKind.Songs
                }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "无法读取歌曲链接" }
            loading = false
            return@LaunchedEffect
        }
        when (kind) {
            MeloXSearchKind.Songs -> {
                media = emptyList()
                if (unifiedEnabled && !MeloXSettingsRuntime.desktopLayoutEnabled) {
                    songs = emptyList()
                    providerSongs = emptyList()
                    runCatching { unifiedService.searchSongs(keyword, unifiedSources, pageSizePerProvider = 25) }
                        .onSuccess { result ->
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            providerSongs = result.aggregated.mapNotNull { it.recommendation?.track }.ifEmpty { result.tracks }
                            unifiedFailures = result.failures
                        }.onFailure { failure ->
                            if (failure is kotlinx.coroutines.CancellationException) throw failure
                            error = failure.message ?: "聚合搜索失败"
                        }
                } else if (source == MusicSource.Netease) {
                    providerSongs = emptyList()
                    runCatching { songClient.ensureArtwork(songClient.searchSongs(keyword)) }
                        .onSuccess { kotlinx.coroutines.currentCoroutineContext().ensureActive(); songs = it }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                } else {
                    songs = emptyList()
                    val capability = providerSongSearch
                    if (capability == null) {
                        providerSongs = emptyList()
                        error = "${source.displayName} 当前没有歌曲搜索能力"
                    } else {
                        runCatching { capability.searchSongs(keyword, page = 1, pageSize = 50).items }
                            .onSuccess { providerSongs = it }
                            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                    }
                }
            }
            MeloXSearchKind.Playlists, MeloXSearchKind.Albums, MeloXSearchKind.Artists -> {
                songs = emptyList()
                providerSongs = emptyList()
                if (source == MusicSource.Netease) {
                    runCatching { universal.searchMedia(keyword, kind) }
                        .onSuccess { media = it }
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                } else {
                    media = emptyList()
                    val capability = providerCatalog
                    if (capability == null) error = "${source.displayName} 当前没有${kind.title}搜索"
                    else when (kind) {
                        MeloXSearchKind.Playlists -> runCatching { capability.searchPlaylists(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerPlaylists = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                        MeloXSearchKind.Albums -> runCatching { capability.searchAlbums(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerAlbums = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                        MeloXSearchKind.Artists -> runCatching { capability.searchArtists(keyword, page = 1, pageSize = 40).items }
                            .onSuccess { providerArtists = it }.onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
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
                        .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: "搜索失败" }
                }
            }
        }
        loading = false
    }

    val backAction = when {
        selectedDetail != null || selectedProviderPlaylist != null || selectedProviderAlbum != null || selectedProviderArtist != null || categoryTitle != null -> SearchBackAction.ClearOverlay
        query.isNotBlank() -> SearchBackAction.ClearQuery
        else -> SearchBackAction.SwitchToHome
    }
    fun consumeBack() {
        when (backAction) {
            SearchBackAction.ClearOverlay -> {
                selectedDetail = null
                selectedProviderPlaylist = null
                selectedProviderAlbum = null
                selectedProviderArtist = null
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

    melox.ui.navigation.MeloXBackHandler(backAction != SearchBackAction.SwitchToHome, priority = 10) { consumeBack() }
    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(destinationFocus).focusable()
            .background(MeloXColors.pageBackground())
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                    consumeBack()
                    true
                } else false
            },
    ) {
        when {
            categoryTitle != null && !desktop -> SearchCategoryPage(
                title = categoryTitle!!,
                playlists = categoryPlaylists,
                loading = categoryLoading,
                error = categoryError,
                onBack = { categoryTitle = null },
                onPlaylist = { selectedDetail = it.asSearchItem() },
            )
            MeloXSettingsRuntime.desktopLayoutEnabled -> melox.ui.desktop.MeloXDesktopSearchScaffold(
                query = query,
                onQuery = { query = it; skipSearchDebounce = false },
                onSubmit = {
                    skipSearchDebounce = true
                    searchTrigger += 1
                    rememberSearchQuery(query)
                    history = searchHistory()
                },
                suggestions = searchSuggestions,
                onSuggestion = { picked ->
                    query = picked; searchSuggestions = emptyList()
                    skipSearchDebounce = true; searchTrigger += 1
                },
                libraryScope = libraryScope,
                onScope = { libraryScope = it; skipSearchDebounce = true; searchTrigger += 1 },
                kinds = availableKinds,
                kind = kind,
                onKind = { kind = it },
                loading = loading,
                error = error,
                searched = query.isNotBlank(),
                songs = songs,
                providerSongs = providerSongs,
                media = media,
                onPlaySong = { song -> PlaybackCommands.playQueue(songs, song.id) },
                onPlayProvider = { track -> ProviderPlaybackCommands.playQueue(
                    tracks = providerSongs, selectedTrackId = track.id,
                    onFailure = { failure -> error = failure.message ?: "播放失败" }) },
                onOpenMedia = { selectedDetail = it },
                onSongMenu = { song, _ -> selectedActionSong = song },
                onRetry = { searchTrigger += 1 },
            )
            else -> Column(Modifier.fillMaxSize().padding(top = 26.dp).testTag("search-state").semantics {
                stateDescription = if (unifiedEnabled) "Unified>${unifiedSources.sortedBy { it.storageValue }.joinToString(">") { it.storageValue }}" else "Provider>${source.storageValue}"
            }) {
                MeloXIosTopBar(title = pinnedString("tab_search"))
                Spacer(Modifier.height(16.dp))
                SearchField(
                    value = query,
                    onValueChange = { query = it; skipSearchDebounce = false },
                    onSearch = {
                        skipSearchDebounce = true
                        searchTrigger += 1
                        rememberSearchQuery(query)
                        history = searchHistory()
                    },
                    onBack = { if (query.isNotBlank()) query = "" else onSearchExit() },
                )
                if (query.isNotBlank()) {
                    SearchScopes(kind = kind, availableKinds = availableKinds, onKind = { kind = it })
                }
                Box(Modifier.weight(1f)) {
                    when {
                        query.isBlank() && history.isNotEmpty() -> SearchHistoryList(
                            values = history,
                            onPick = { picked ->
                                query = picked
                                skipSearchDebounce = true
                                searchTrigger += 1
                            },
                            onClear = { clearSearchHistory(); history = emptyList() },
                        )
                        query.isBlank() && source == MusicSource.Netease -> SearchDiscovery(
                            recommendations = recommendations,
                            onPlaylist = { selectedDetail = it.asSearchItem() },
                             onCategory = { category ->
                                if (category == "播客") { onOpenPodcasts(); return@SearchDiscovery }
                                categoryTitle = category
                            },
                        )
                        query.isBlank() && providerRecommendations.isNotEmpty() -> ProviderMediaResults(
                            titles = providerRecommendations.map { it.title to it.creatorName.orEmpty() },
                            artworks = providerRecommendations.map { it.artworkUrl },
                            onOpen = { selectedProviderPlaylist = providerRecommendations[it] })
                        query.isBlank() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                pinnedString("search_source_hint", source.displayName),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f),
                            )
                        }
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             CircularProgressIndicator(color = SearchAccent, modifier = Modifier.testTag("search-loading"))
                        }
                        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                             Text(error.orEmpty(), modifier = Modifier.testTag("search-error"), color = MaterialTheme.colorScheme.error)
                        }
                         kind == MeloXSearchKind.Songs && !songLinkResult && (unifiedEnabled || source != MusicSource.Netease) -> ProviderSearchSongResults(
                             values = providerSongs,
                             failures = unifiedFailures,
                             showSource = unifiedEnabled,
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
                            onLike = { song -> scope.launch {
                                runCatching { operations.setSongLiked(checkNotNull(song.neteaseSongId), true) }
                                    .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it
                                        error = it.message ?: pinnedString("library_add_failed") }
                            } },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Playlists -> ProviderMediaResults(
                            titles = providerPlaylists.map { it.title to (it.creatorName.orEmpty()) },
                            artworks = providerPlaylists.map { it.artworkUrl },
                            onOpen = { selectedProviderPlaylist = providerPlaylists[it] },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Albums -> ProviderMediaResults(
                            titles = providerAlbums.map { it.title to "" },
                            artworks = providerAlbums.map { it.artworkUrl },
                            onOpen = { selectedProviderAlbum = providerAlbums[it] },
                        )
                        source != MusicSource.Netease && kind == MeloXSearchKind.Artists -> ProviderMediaResults(
                            titles = providerArtists.map { it.name to "" },
                            artworks = providerArtists.map { it.artworkUrl },
                            onOpen = { selectedProviderArtist = providerArtists[it] },
                        )
                        else -> SearchMediaResults(media) { selectedDetail = it }
                    }
                }
            }
        }
        val destination = selectedProviderPlaylist?.let { SearchDetailDestination.Playlist(it) }
            ?: selectedProviderAlbum?.let { SearchDetailDestination.Album(it) }
            ?: selectedProviderArtist?.let { SearchDetailDestination.Artist(it) }
            ?: selectedDetail?.let { SearchDetailDestination.Media(it) }
        // Keep the result/category composition and scroll offset while details
        // animate above it. Snapshot destinations also survive their exit frame.
        melox.ui.navigation.MeloXDetailOverlay(destination, onBack = {
            selectedDetail = null; selectedProviderPlaylist = null; selectedProviderAlbum = null; selectedProviderArtist = null
        }) { target ->
            when (target) {
                is SearchDetailDestination.Playlist -> PlaylistDetailScreen(MeloXLegacyUiBridge.playlist(target.value), onBack = { selectedProviderPlaylist = null })
                is SearchDetailDestination.Album -> ProviderCollectionDetail(target.value.title, "", target.value.artworkUrl,
                    load = { (currentProvider as? AlbumCapability)?.albumDetail(target.value)?.tracks.orEmpty() }, onBack = { selectedProviderAlbum = null })
                is SearchDetailDestination.Artist -> ProviderCollectionDetail(target.value.name, "", target.value.artworkUrl,
                    load = { (currentProvider as? ArtistCapability)?.artistDetail(target.value)?.tracks.orEmpty() }, onBack = { selectedProviderArtist = null })
                is SearchDetailDestination.Media -> {
                    val item = target.value
                    when (item.kind) {
                        MeloXSearchKind.Artists -> ArtistDetailScreen(item.id, onBack = { selectedDetail = null })
                        MeloXSearchKind.Albums -> AlbumDetailScreen(item.id.toString(), item.title, onBack = { selectedDetail = null })
                        MeloXSearchKind.Users -> AccountProfileScreen(item.id, onBack = { selectedDetail = null })
                        MeloXSearchKind.Podcasts -> PodcastDetail(universal, melox.network.MeloXPodcast(item.id, item.title, item.artworkUrl), onBack = { selectedDetail = null })
                        else -> PlaylistDetailScreen(NeteasePlaylistSummary(item.id, item.title, item.artworkUrl,
                            creatorName = item.subtitle, trackCount = item.trackCount), onBack = { selectedDetail = null })
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

private fun MusicTrack.toSearchSong() = MeloXLegacyUiBridge.track(this).let { song ->
    if (id.source == MusicSource.Netease) song.copy(id = checkNotNull(song.neteaseSongId)) else song
}

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
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    MeloXGlassTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .padding(horizontal = if (desktop) 34.dp else 20.dp)
            .then(if (desktop) Modifier.widthIn(max = 520.dp) else Modifier)
            .testTag("search-query"),
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
            Text(pinnedString("search_placeholder"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f), fontSize = 17.sp)
        },
        trailingContent = {
            if (value.isNotBlank()) {
                Row {
                    Box(
                        modifier = Modifier.size(44.dp).testTag("search-submit").clickable(role = Role.Button) { onSearch() }.semantics { contentDescription = pinnedString("search_content_description") },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.Search, Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), size = 18)
                    }
                    Box(
                        modifier = Modifier.size(44.dp).testTag("search-clear").clickable(role = Role.Button) { onValueChange("") }.semantics { contentDescription = pinnedString("search_clear") },
                        contentAlignment = Alignment.Center,
                    ) {
                        MeloXSymbolIcon(MeloXSymbol.XMark, Modifier.size(15.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f), size = 15)
                    }
                }
            }
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = if (desktop) 14.sp else 17.sp,
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
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    LazyRow(
        contentPadding = PaddingValues(horizontal = if (desktop) 34.dp else 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(if (desktop) 6.dp else 8.dp),
    ) {
        items(availableKinds) { item ->
            MeloXGlassButton(
                onClick = { onKind(item) },
                modifier = Modifier.height(if (desktop) 32.dp else 44.dp).testTag("search-kind-${item.name.lowercase()}").semantics { selected = item == kind },
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
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    val categoryColumns = if (desktop) 4 else melox.ui.layout.rememberMeloXWindowInfo().gridColumns.coerceIn(2, 4)
    val horizontal = if (desktop) 34.dp else 20.dp
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (desktop) 104.dp else 140.dp)) {
        if (recommendations.isNotEmpty()) {
            item { Text(pinnedString("search_trending"), modifier = Modifier.padding(start = horizontal, top = 14.dp, bottom = 12.dp), fontSize = if (desktop) 20.sp else 24.sp, fontWeight = FontWeight.Bold, color = MeloXColors.pageOnSurface()) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = horizontal), horizontalArrangement = Arrangement.spacedBy(if (desktop) 20.dp else 14.dp)) {
                    items(recommendations, key = { it.id }) { playlist ->
                        Column(Modifier.width(if (desktop) 172.dp else 160.dp).clickable { onPlaylist(playlist) }) {
                            MeloXArtworkImage(playlist.coverUrl, MeloXColors.SurfaceVariant, Modifier.size(if (desktop) 172.dp else 160.dp).clip(RoundedCornerShape(if (desktop) 9.dp else 14.dp)))
                            Text(playlist.name, modifier = Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = if (desktop) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = MeloXColors.pageOnSurface())
                        }
                    }
                }
            }
        }
        item { Text(pinnedString("search_browse"), modifier = Modifier.padding(start = horizontal, top = 26.dp, bottom = 12.dp), fontSize = if (desktop) 20.sp else 24.sp, fontWeight = FontWeight.Bold, color = MeloXColors.pageOnSurface()) }
        items(SearchCategories.filter { it != "播客" || melox.settings.MeloXSettingsRuntime.podcastsEnabled }.chunked(categoryColumns)) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { category -> SearchCategoryCard(category, Modifier.weight(1f)) { onCategory(category) } }
                repeat(categoryColumns - pair.size) { Spacer(Modifier.weight(1f)) }
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
            .testTag("search-category-${SearchCategories.indexOf(title)}")
            .height(if (MeloXSettingsRuntime.desktopLayoutEnabled) 78.dp else 96.dp)
            .clip(if (MeloXSettingsRuntime.desktopLayoutEnabled) RoundedCornerShape(10.dp) else MeloXShapes.compact)
            .background(tint)
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(searchCategoryLabel(title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
            Text("最近搜索", color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.SemiBold)
            Text(pinnedString("search_clear"), modifier = Modifier.testTag("search-history-clear").clickable(onClick = onClear), color = SearchAccent, fontSize = 13.sp)
        }
        values.forEach { item ->
            Text(
                item,
                modifier = Modifier.fillMaxWidth().clickable { onPick(item) }.padding(vertical = 10.dp),
                color = MeloXColors.pageOnSurface(),
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
    onLike: ((SearchSong) -> Unit)? = null,
) {
    val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = if (desktop) 34.dp else 20.dp, end = if (desktop) 34.dp else 20.dp, bottom = if (desktop) 104.dp else MeloXBottomContentClearanceDp)) {
        if (values.isEmpty()) item { Text(pinnedString("search_no_songs"), Modifier.padding(vertical = 20.dp).testTag("search-empty"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f)) }
        items(values, key = { it.id }) { song ->
            SearchSwipeSongRow(song, onPlay = { onPlay(song) }, onMore = onMore?.let { { it(song) } }, modifier = Modifier.testTag("search-song-${song.id}"),
                endAction = onLike?.let { like -> MeloXSwipeAction(pinnedString("artist_add_library"), MeloXSymbol.Heart, Color(0xFFFF3B30)) { like(song) } })
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SearchSwipeSongRow(song: SearchSong, onPlay: () -> Unit, onMore: (() -> Unit)?,
    endAction: MeloXSwipeAction?, sourceLabel: String? = null, modifier: Modifier = Modifier) {
    MeloXSwipeActionRow(
        startActions = listOf(
            MeloXSwipeAction(pinnedString("player_play_next"), MeloXSymbol.NextTrack, Color(0xFF8E5AF7)) { PlaybackCommands.playNext(song) },
            MeloXSwipeAction(pinnedString("artist_play_later"), MeloXSymbol.Queue, Color(0xFFFF9F0A)) { PlaybackCommands.addToQueue(song) }),
        endActions = listOfNotNull(endAction),
        startFullSwipeActionIndex = if (melox.settings.MeloXSettingsRuntime.swipeFullAction == melox.settings.MeloXSwipeFullAction.AddToQueue) 1 else 0,
        onClick = onPlay, onLongClick = onMore, modifier = modifier) {
        val desktop = MeloXSettingsRuntime.desktopLayoutEnabled
        Row(Modifier.fillMaxWidth().height(if (desktop) 54.dp else 70.dp).padding(horizontal = if (desktop) 8.dp else 12.dp, vertical = if (desktop) 5.dp else 9.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXArtworkImage(song.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(if (desktop) 40.dp else 52.dp).clip(RoundedCornerShape(if (desktop) 6.dp else 8.dp)))
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurface(), fontSize = if (desktop) 14.sp else 17.sp, fontWeight = FontWeight.Medium)
                Text(listOfNotNull(song.artists, song.album.takeIf(String::isNotBlank), sourceLabel).joinToString(" · "),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ProviderSearchSongResults(
    values: List<MusicTrack>,
    failures: List<melox.music.provider.UnifiedMusicService.SearchFailure> = emptyList(),
    showSource: Boolean = false,
    onPlay: (MusicTrack) -> Unit,
) {
    val downloads = remember { melox.download.MeloXProviderDownloadStore.get() }
    LazyColumn(Modifier.fillMaxSize().testTag("search-provider-results").semantics {
        stateDescription = values.joinToString(">") { "${it.id.source.storageValue}:${it.id.value}" }
    }, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearanceDp)) {
        if (failures.isNotEmpty()) item {
            Text(failures.joinToString("；") { "${it.source.displayName}：${it.message}" },
                Modifier.padding(horizontal = 20.dp, vertical = 8.dp).testTag("search-provider-failures"),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f), fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (values.isEmpty()) item { Text(pinnedString("search_no_songs"), Modifier.padding(vertical = 20.dp).testTag("search-empty"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f)) }
        items(values, key = { "${it.id.source.storageValue}:${it.id.value}" }) { track ->
            SearchSwipeSongRow(MeloXLegacyUiBridge.track(track), onPlay = { onPlay(track) }, onMore = null,
                endAction = MeloXSwipeAction(pinnedString("search_download_local"), MeloXSymbol.Download, Color(0xFF0EA5E9)) { downloads.start(track) },
                sourceLabel = track.id.source.displayName.takeIf { showSource },
                modifier = Modifier.testTag("search-track-${track.id.source.storageValue}-${track.id.value}"))
            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        }
    }
}

@Composable
private fun SearchMediaResults(values: List<MeloXSearchMediaItem>, onOpen: (MeloXSearchMediaItem) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = MeloXBottomContentClearanceDp)) {
        if (values.isEmpty()) item { Text(pinnedString("search_no_results"), Modifier.padding(vertical = 20.dp).testTag("search-empty"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f)) }
        items(values, key = { "${it.kind.name}:${it.id}" }) { item ->
            Row(
                Modifier.fillMaxWidth().testTag("search-media-${item.kind.name.lowercase()}-${item.id}").clickable { onOpen(item) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(item.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(54.dp).clip(
                    if (item.kind == MeloXSearchKind.Artists || item.kind == MeloXSearchKind.Users) CircleShape else RoundedCornerShape(8.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Medium)
                    Text(item.subtitle.ifBlank { if (item.trackCount > 0) pinnedString("search_song_count_short", item.trackCount) else item.kind.title }, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
                }
                MeloXSymbolIcon(MeloXSymbol.ChevronRight, size = 15, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .3f))
            }
        }
    }
}

@Composable
private fun ProviderMediaResults(
    titles: List<Pair<String, String>>,
    artworks: List<String?>,
    onOpen: (Int) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 140.dp)) {
        items(titles.size) { index ->
            val (title, subtitle) = titles[index]
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(index) }.padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MeloXArtworkImage(artworks.getOrNull(index), MeloXColors.SurfaceVariant, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Medium)
                    if (subtitle.isNotBlank()) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
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
        MeloXIosTopBar(title = searchCategoryLabel(title), onBack = onBack)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error, color = MaterialTheme.colorScheme.error) }
            else -> LazyColumn(contentPadding = PaddingValues(20.dp, bottom = 140.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists, key = { it.id }) { playlist ->
                    Row(Modifier.fillMaxWidth().clickable { onPlaylist(playlist) }, verticalAlignment = Alignment.CenterVertically) {
                        MeloXArtworkImage(playlist.coverUrl, MeloXColors.SurfaceVariant, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(playlist.name, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${playlist.trackCount} 首 · ${playlist.creatorName}", color = MeloXColors.pageOnSurfaceVariant(), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCollectionDetail(
    title: String,
    subtitle: String,
    artwork: String?,
    load: suspend () -> List<MusicTrack>,
    onBack: () -> Unit,
) {
    var tracks by remember(title, artwork) { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var loading by remember(title, artwork) { mutableStateOf(true) }
    var error by remember(title, artwork) { mutableStateOf<String?>(null) }
    LaunchedEffect(title, artwork) {
        loading = true
        runCatching { load() }
            .onSuccess { tracks = it }
            .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it; error = it.message ?: pinnedString("search_content_failed") }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(top = 26.dp)) {
        MeloXIosTopBar(title = title, onBack = onBack)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXArtworkImage(artwork, MeloXColors.SurfaceVariant, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (subtitle.isNotBlank()) Text(subtitle, color = MeloXColors.pageOnSurfaceVariant())
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> ProviderSearchSongResults(tracks, onPlay = { track -> ProviderPlaybackCommands.playQueue(tracks, track.id,
                onFailure = { error = it.message ?: pinnedString("search_play_failed") }) })
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
                Text(item.title, color = MeloXColors.pageOnSurface(), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                if (item.subtitle.isNotBlank()) Text(item.subtitle, color = MeloXColors.pageOnSurfaceVariant())
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = SearchAccent) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> SearchSongResults(songs, onPlay = { song -> PlaybackCommands.playQueue(songs, song.id) })
        }
    }
}
