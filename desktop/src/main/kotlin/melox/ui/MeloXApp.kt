package melox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import melox.music.model.MusicTrack
import melox.music.model.MusicSource
import melox.music.model.PlaybackResolution
import melox.provider.netease.NeteaseProvider
import melox.provider.qqmusic.QQMusicProvider
import melox.provider.kugou.KugouProvider
import melox.provider.kuwo.KuwoProvider
import melox.provider.spotify.SpotifyProvider
import melox.provider.youtubemusic.YouTubeMusicProvider
import melox.music.provider.SearchCapability
import melox.music.provider.MusicProvider
import melox.network.MeloXHttpClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeloXApp() {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var currentProvider by remember { mutableStateOf<String>("netease") }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3700B3),
            secondary = Color(0xFF03DAC5),
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            // Top Bar
            TopAppBar(
                title = { Text("MeloX Desktop", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1F1F1F)),
            )

            // Provider Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val providers = listOf(
                    "netease" to "网易云",
                    "qq" to "QQ音乐",
                    "kugou" to "酷狗",
                    "kuwo" to "酷我",
                    "spotify" to "Spotify",
                    "youtube" to "YouTube",
                )
                providers.forEach { (id, name) ->
                    FilterChip(
                        selected = currentProvider == id,
                        onClick = { currentProvider = id },
                        label = { Text(name, color = if (currentProvider == id) Color.White else Color.Gray) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = if (currentProvider == id) Color(0xFF6200EE) else Color(0xFF2C2C2C),
                        ),
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("搜索歌曲...", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6200EE),
                    unfocusedBorderColor = Color(0xFF3C3C3C),
                ),
                singleLine = true,
            )

            // Search Button
            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        isLoading = true
                        kotlinx.coroutines.MainScope().launch {
                            searchResults = search(searchQuery, currentProvider)
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("搜索", color = Color.White)
                }
            }

            // Results List
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(searchResults) { track ->
                    TrackItem(
                        track = track,
                        isSelected = selectedTrack?.id == track.id,
                        onClick = { selectedTrack = track },
                    )
                }
            }

            // Player Bar
            selectedTrack?.let { track ->
                PlayerBar(track)
            }
        }
    }
}

@Composable
fun TrackItem(track: MusicTrack, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF3C3C3C) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = track.id.source.displayName,
            color = Color(0xFF6200EE),
        )
    }
}

@Composable
fun PlayerBar(track: MusicTrack) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF1F1F1F),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = track.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artistText,
                color = Color.Gray,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text("⏮", modifier = Modifier.clickable { })
                Text("▶️", modifier = Modifier.clickable { })
                Text("⏭", modifier = Modifier.clickable { })
            }
        }
    }
}

suspend fun search(query: String, providerId: String): List<MusicTrack> {
    return try {
        when (providerId) {
            "netease" -> {
                val provider = NeteaseProvider()
                (provider as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
            }
            "qq" -> {
                val provider = QQMusicProvider()
                (provider as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
            }
            "kugou" -> {
                val provider = KugouProvider()
                (provider as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
            }
            "kuwo" -> {
                val provider = KuwoProvider()
                (provider as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
            }
            else -> emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}
