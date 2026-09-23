package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.network.MeloXCloudSong
import melox.network.NeteaseUniversalSearchClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors

@Composable
fun CloudScreen(
    navState: MeloXNavState,
    modifier: Modifier = Modifier,
) {
    val client = remember { NeteaseUniversalSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    var values by remember { mutableStateOf<List<MeloXCloudSong>>(emptyList()) }
    var quota by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun refresh() {
        loading = true
        error = null
        runCatching { client.cloudSongs() }
            .onSuccess { page ->
                values = page.values
                quota = if (page.maxBytes > 0L) "${formatBytes(page.usedBytes)} / ${formatBytes(page.maxBytes)} · ${page.totalCount} 首" else "${page.totalCount} 首"
            }
            .onFailure { error = it.message ?: "云盘加载失败" }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    Column(modifier.fillMaxSize().background(MeloXColors.Background).padding(top = 18.dp)) {
        MeloXIosTopBar(title = "音乐云盘", onBack = { navState.goBack() })
        Text(quota, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MeloXColors.Primary) }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MeloXColors.Error) }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(values, key = { it.id }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            PlaybackCommands.playQueue(values.map { it.song }, item.song.id)
                        }.padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MeloXArtworkImage(item.song.artworkUrl, MeloXColors.SurfaceVariant, Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(item.song.name, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurface, fontWeight = FontWeight.Medium)
                            Text(item.song.artists, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp)
                        }
                        Text("删除", color = MeloXColors.Error, modifier = Modifier.clickable {
                            scope.launch {
                                runCatching { client.deleteCloudSong(item.id) }
                                refresh()
                            }
                        })
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.1f GB".format(mb / 1024.0) else "%.0f MB".format(mb)
}
