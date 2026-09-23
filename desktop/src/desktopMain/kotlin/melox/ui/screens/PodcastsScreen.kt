package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.account.NeteaseSessionStore
import melox.network.MeloXPodcast
import melox.network.MeloXPodcastCategory
import melox.network.MeloXPodcastProgram
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.glass.MeloXGlassButton
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors

@Composable
fun PodcastsScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    val client = remember { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var selected by remember { mutableStateOf<MeloXPodcast?>(null) }
    if (selected != null) {
        PodcastDetail(client, selected!!, onBack = { selected = null })
    } else {
        PodcastHome(client, modifier, onPodcast = { selected = it })
    }
}

@Composable
private fun PodcastHome(
    client: NeteaseUniversalSearchClient,
    modifier: Modifier,
    onPodcast: (MeloXPodcast) -> Unit,
) {
    var categories by remember { mutableStateOf<List<MeloXPodcastCategory>>(emptyList()) }
    var featured by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var personalized by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var categoryPodcasts by remember { mutableStateOf<List<MeloXPodcast>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<MeloXPodcastCategory?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            categories = client.podcastCategories()
            featured = client.featuredPodcasts()
            personalized = client.personalizedPodcasts()
        }.onFailure { error = it.message ?: "播客加载失败" }
        loading = false
    }
    LaunchedEffect(selectedCategory?.id) {
        val category = selectedCategory ?: run { categoryPodcasts = emptyList(); return@LaunchedEffect }
        runCatching { client.podcastsByCategory(category.id).values }
            .onSuccess { categoryPodcasts = it }
            .onFailure { error = it.message ?: "分类加载失败" }
    }

    when {
        loading && featured.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MeloXColors.Primary)
        }
        error != null && featured.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        }
        else -> LazyColumn(
            modifier.fillMaxSize().background(MeloXColors.Background),
            contentPadding = PaddingValues(top = 22.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                Text("播客", Modifier.padding(horizontal = 20.dp), color = MeloXColors.OnBackground, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            if (categories.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            MeloXGlassButton(onClick = { selectedCategory = null }, modifier = Modifier.height(36.dp)) { Text("为你推荐") }
                        }
                        items(categories, key = { it.id }) { category ->
                            MeloXGlassButton(onClick = { selectedCategory = category }, modifier = Modifier.height(36.dp)) { Text(category.name) }
                        }
                    }
                }
            }
            if (selectedCategory == null) {
                if (personalized.isNotEmpty()) item { PodcastRow("为你推荐", personalized, onPodcast) }
                if (featured.isNotEmpty()) item { PodcastRow("热门播客", featured, onPodcast) }
            } else {
                item { PodcastRow(selectedCategory?.name ?: "分类", categoryPodcasts, onPodcast) }
            }
        }
    }
}

@Composable
private fun PodcastRow(title: String, values: List<MeloXPodcast>, onPodcast: (MeloXPodcast) -> Unit) {
    Column {
        Text(title, Modifier.padding(start = 20.dp, bottom = 12.dp), color = MeloXColors.OnSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(values, key = { it.id }) { podcast ->
                Column(Modifier.width(148.dp).clickable { onPodcast(podcast) }) {
                    MeloXArtworkImage(podcast.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(148.dp).clip(RoundedCornerShape(14.dp)))
                    Text(podcast.name, Modifier.padding(top = 7.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.SemiBold)
                    Text(podcast.host?.nickname.orEmpty(), maxLines = 1, color = MeloXColors.OnSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun PodcastDetail(client: NeteaseUniversalSearchClient, podcast: MeloXPodcast, onBack: () -> Unit) {
    var programs by remember(podcast.id) { mutableStateOf<List<MeloXPodcastProgram>>(emptyList()) }
    var loading by remember(podcast.id) { mutableStateOf(true) }
    var error by remember(podcast.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(podcast.id) {
        runCatching { client.podcastPrograms(podcast.id).values }
            .onSuccess { programs = it }
            .onFailure { error = it.message ?: "节目加载失败" }
        loading = false
    }
    Column(Modifier.fillMaxSize().background(MeloXColors.Background).padding(top = 18.dp)) {
        MeloXIosTopBar(title = podcast.name, onBack = onBack)
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXArtworkImage(podcast.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
            Column(Modifier.padding(start = 16.dp)) {
                Text(podcast.name, color = MeloXColors.OnSurface, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(podcast.host?.nickname.orEmpty(), color = MeloXColors.OnSurfaceVariant)
            }
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MeloXColors.Primary) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MaterialTheme.colorScheme.error) }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(programs, key = { it.id }) { program ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            program.playbackSong?.let { PlaybackCommands.playQueue(listOf(it), it.id) }
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXArtworkImage(program.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(program.name, color = MeloXColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(program.radioName, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
