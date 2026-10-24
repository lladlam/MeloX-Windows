package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.network.*
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.theme.MeloXDesktopTextFontFamily
import kotlin.math.floor

@Composable
internal fun MeloXDesktopPodcasts() {
    val revision by NeteaseSessionStore.revision.collectAsState()
    val client = remember(revision) { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var category by remember { mutableStateOf<MeloXPodcastCategory?>(null) }
    var podcast by remember { mutableStateOf<MeloXPodcast?>(null) }
    when {
        podcast != null -> MeloXDesktopPodcastDetail(client, requireNotNull(podcast)) { podcast = null }
        category != null -> DesktopPodcastCatalog(client, category, { category = null }, { podcast = it })
        else -> DesktopPodcastCatalog(client, null, {}, { podcast = it }, { category = it })
    }
}

/** DesktopDiscoveryView's podcast page and DesktopPodcastCategoryView. */
@Composable
private fun DesktopPodcastCatalog(client: NeteaseUniversalSearchClient, category: MeloXPodcastCategory?, onBack: () -> Unit,
    onPodcast: (MeloXPodcast) -> Unit, onCategory: (MeloXPodcastCategory) -> Unit = {}) = key(client, category?.id) {
    var podcasts by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var categories by remember { mutableStateOf<List<MeloXPodcastCategory>>(emptyList()) }
    var programs by remember { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }
    var offset by remember { mutableIntStateOf(0) }
    var hasMore by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<SearchSong?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    val scope = rememberCoroutineScope()
    val scroll = rememberLazyListState()
    suspend fun loadMore() {
        loading = true; error = null
        try {
            val page = client.podcastsByCategory(requireNotNull(category).id, offset, 30)
            podcasts = (podcasts + page.values).distinctBy { it.id }
            offset += page.values.size; hasMore = page.hasMore && page.values.isNotEmpty()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    LaunchedEffect(client, category?.id, retry) {
        if (category != null) { if (podcasts.isEmpty()) offset = 0; loadMore() }
        else {
            loading = true; error = null
            try { coroutineScope {
                val c = async { client.podcastCategories() }; val p = async { client.featuredPodcasts() }
                val r = async { client.recommendedPodcastPrograms() }
                categories = c.await(); podcasts = p.await(); programs = r.await()
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
            finally { loading = false }
        }
    }
    DesktopPodcastText {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag(if (category == null) "desktop-podcasts" else "desktop-podcast-category")) {
            LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll), state = scroll,
                contentPadding = PaddingValues(horizontal = 34.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(if (category == null) 32.dp else 22.dp)) {
                item("title") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(category?.name ?: desktopString("ui.navigation.podcasts"), Modifier.weight(1f), fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        if (category != null) Text(desktopFormat("ui.desktop.podcasts.count", podcasts.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (category == null && categories.isNotEmpty()) item("categories") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(desktopString("ui.search.browse_categories"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        val categoriesScroll = rememberLazyListState()
                        LazyRow(Modifier.desktopPromotedDragScroll(categoriesScroll, androidx.compose.foundation.gestures.Orientation.Horizontal),
                            state = categoriesScroll, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(categories, key = { it.id }) { item ->
                                Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
                                    .testTag("desktop-podcast-category:${item.id}").clickable { onCategory(item) }.padding(horizontal = 14.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MeloXSymbolIcon(MeloXSymbol.Podcast, size = 13); Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }
                if (podcasts.isNotEmpty()) item("podcasts") {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        if (category == null) Text(desktopString("ui.podcasts.editors_choice"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        DesktopPodcastGrid(podcasts, onPodcast)
                    }
                }
                if (programs.isNotEmpty()) {
                    item("episodes") { Text(desktopString("ui.desktop.podcasts.latest_episodes"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                    itemsIndexed(programs, key = { _, it -> it.id }) { index, program -> program.playbackSong?.let { song ->
                        DesktopCollectionTrackRow(index + 1, song, false, false, false,
                            { PlaybackCommands.playQueue(programs.mapNotNull { it.playbackSong }, song.id) },
                            { anchor = it; selected = song }, showsArtwork = true)
                    } }
                }
                if (loading) item("loading") { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) } }
                else if (error != null) item("error") { Text(requireNotNull(error), color = MaterialTheme.colorScheme.error); DesktopDialogButton("ui.common.retry") { retry++ } }
                else if (podcasts.isEmpty()) item("empty") { Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) { Text(desktopString("ui.desktop.podcasts.empty")) } }
                else if (hasMore) item("next-page") {
                    LaunchedEffect(offset) { scope.launch { loadMore() } }
                    Spacer(Modifier.height(1.dp))
                }
                item("clearance") { Spacer(Modifier.height(70.dp)) }
            }
            if (category != null) Box(Modifier.size(30.dp).testTag("desktop-podcast-back").clickable(onClick = onBack), contentAlignment = Alignment.Center) { MeloXSymbolIcon(MeloXSymbol.ChevronLeft, size = 13) }
        }
    }
    selected?.let { MeloXDesktopSongMenu(it, { selected = null }, anchor) }
}

@Composable
private fun DesktopPodcastGrid(podcasts: List<MeloXPodcast>, onOpen: (MeloXPodcast) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = floor((maxWidth.value + 20) / 165).toInt().coerceAtLeast(1)
        val width = ((maxWidth.value - (columns - 1) * 20) / columns).coerceAtMost(205f).dp
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            podcasts.chunked(columns).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEach { podcast -> DesktopHomeMediaCard(podcast.name, podcast.host?.nickname ?: podcast.category.orEmpty(), podcast.artworkUrl,
                    width, "desktop-podcast:${podcast.id}", onOpen = { onOpen(podcast) }, onPlay = { onOpen(podcast) }) }
            } }
        }
    }
}

/** DesktopPodcastDetailView: collection hero, 150pt order picker, paged artwork track rows. */
@Composable
internal fun MeloXDesktopPodcastDetail(client: NeteaseUniversalSearchClient, initial: MeloXPodcast, onBack: () -> Unit) = key(client, initial.id) {
    var podcast by remember { mutableStateOf(initial) }
    var songs by remember { mutableStateOf<List<SearchSong>>(emptyList()) }
    var offset by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(initial.programCount) }
    var hasMore by remember { mutableStateOf(false) }
    var ascending by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var operationError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<SearchSong?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    val scope = rememberCoroutineScope()
    val playback by melox.playback.MeloXPlaybackOutput.state.collectAsState()
    val queue by PlaybackCommands.queue.collectAsState()
    val current = queue.songs.getOrNull(queue.index)?.id
    suspend fun page(reset: Boolean) {
        loading = true; error = null
        try {
            if (reset) { podcast = requireNotNull(client.podcastDetail(initial.id)); offset = 0; songs = emptyList() }
            val page = client.podcastPrograms(initial.id, offset, 30, ascending)
            songs = (songs + page.values.mapNotNull { it.playbackSong }).distinctBy { it.id }
            offset += page.values.size; total = page.totalCount; hasMore = page.hasMore && page.values.isNotEmpty()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
        finally { loading = false }
    }
    LaunchedEffect(ascending, retry) { page(true) }
    DesktopPodcastText {
        MeloXDesktopCollectionDetail(kind = desktopString("ui.podcasts.podcast"), title = podcast.name, subtitle = podcast.host?.nickname,
            metadata = desktopFormat("ui.desktop.podcasts.metadata", maxOf(total, podcast.programCount), podcast.subscriberCount.toString()),
            description = podcast.description, artworkUrl = podcast.artworkUrl, songs = emptyList(), hasSongs = songs.isNotEmpty(),
            loading = loading && songs.isEmpty(), error = if (songs.isEmpty()) error else null, playingId = current, isPlaying = playback.isPlaying,
            onBack = onBack, onPlayAll = { songs.firstOrNull()?.let { PlaybackCommands.playQueue(songs, it.id) } },
            onShuffle = { songs.shuffled().let { shuffled -> shuffled.firstOrNull()?.let { PlaybackCommands.playQueue(shuffled, it.id) } } },
            isFavorite = podcast.subscribed, favoriteEnabled = !busy, onFavorite = {
                busy = true
                scope.launch {
                    try { client.setPodcastSubscribed(podcast.id, !podcast.subscribed); podcast = podcast.copy(subscribed = !podcast.subscribed) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { operationError = failure.message ?: desktopString("ui.error.favorite_failed") }
                    finally { busy = false }
                }
            }, onRetry = { retry++ }, onSong = { PlaybackCommands.playQueue(songs, it.id) }, tag = "desktop-podcast-detail",
            extraContent = {
                item("episode-order") { Row(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopString("ui.podcasts.episodes"), Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Box(Modifier.width(150.dp)) { DesktopSettingsPicker("", ascending, listOf(false to desktopString("ui.podcast.sort.newest"), true to desktopString("ui.podcast.sort.oldest")), "desktop-podcast-order") { ascending = it } }
                } }
                itemsIndexed(songs, key = { _, it -> it.id }) { index, song ->
                    DesktopCollectionTrackRow(index + 1, song, song.id == current, playback.isPlaying, false,
                        { PlaybackCommands.playQueue(songs, song.id) }, { anchor = it; selected = song }, showsArtwork = true)
                    Spacer(Modifier.height(2.dp))
                }
                if (error != null) item("page-error") { Text(requireNotNull(error), color = MaterialTheme.colorScheme.error); DesktopDialogButton("ui.common.retry") { scope.launch { page(false) } } }
                else if (hasMore && !loading) item("next-page") { LaunchedEffect(offset) { page(false) }; Spacer(Modifier.height(1.dp)) }
                else if (loading) item("loading-more") { CircularProgressIndicator(Modifier.padding(18.dp).size(20.dp), strokeWidth = 2.dp) }
                else if (songs.isEmpty()) item("empty") { Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) { Text(desktopString("ui.podcasts.no_episodes")) } }
            })
    }
    selected?.let { MeloXDesktopSongMenu(it, { selected = null }, anchor) }
    operationError?.let { DesktopMessageDialog("ui.error.favorite_failed", it, { operationError = null }) }
}

@Composable
private fun DesktopPodcastText(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground), content = content)
}
