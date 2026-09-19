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
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import melox.music.model.MusicTrack
import melox.music.provider.SearchCapability
import melox.player.AudioPlayer
import melox.player.PlayerState
import melox.provider.kugou.KugouProvider
import melox.provider.kuwo.KuwoProvider
import melox.provider.netease.NeteaseProvider
import melox.provider.qqmusic.QQMusicProvider
import melox.ui.theme.MeloXLanTingProFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeloXApp() {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<MusicTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var currentProvider by remember { mutableStateOf<String>("netease") }
    var showLyrics by remember { mutableStateOf(false) }

    val playerState by AudioPlayer.state.collectAsState()
    val playerProgress by AudioPlayer.progress.collectAsState()
    val currentTrack by AudioPlayer.currentTrack.collectAsState()

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF6200EE),
            onPrimary = Color.White,
            primaryContainer = Color(0xFF3700B3),
            secondary = Color(0xFF03DAC5),
        ),
        typography = Typography().copy(
            displayLarge = Typography().displayLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            displayMedium = Typography().displayMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            displaySmall = Typography().displaySmall.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineLarge = Typography().headlineLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineMedium = Typography().headlineMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            headlineSmall = Typography().headlineSmall.copy(fontFamily = MeloXLanTingProFontFamily),
            titleLarge = Typography().titleLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            titleMedium = Typography().titleMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            titleSmall = Typography().titleSmall.copy(fontFamily = MeloXLanTingProFontFamily),
            bodyLarge = Typography().bodyLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            bodyMedium = Typography().bodyMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            bodySmall = Typography().bodySmall.copy(fontFamily = MeloXLanTingProFontFamily),
            labelLarge = Typography().labelLarge.copy(fontFamily = MeloXLanTingProFontFamily),
            labelMedium = Typography().labelMedium.copy(fontFamily = MeloXLanTingProFontFamily),
            labelSmall = Typography().labelSmall.copy(fontFamily = MeloXLanTingProFontFamily),
        )
    ) {
        Column(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
            TopAppBar(
                title = { Text("MeloX Desktop", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1F1F1F)),
            )

            // Provider Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("netease" to "网易云", "qq" to "QQ", "kugou" to "酷狗", "kuwo" to "酷我").forEach { (id, name) ->
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6200EE), unfocusedBorderColor = Color(0xFF3C3C3C)),
                singleLine = true,
            )

            Button(
                onClick = {
                    if (searchQuery.isNotBlank()) {
                        isLoading = true
                        MainScope().launch {
                            searchResults = search(searchQuery, currentProvider)
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE)),
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("搜索", color = Color.White)
            }

            // Results List
            LazyColumn(modifier = Modifier.weight(1f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(searchResults) { track ->
                    TrackItem(track, currentTrack?.id == track.id) {
                        selectedTrack = track
                        AudioPlayer.play("", track)
                    }
                }
            }

            // Player Bar
            currentTrack?.let { track ->
                PlayerBar(track, playerState, playerProgress) {
                    if (playerState.isPlaying) AudioPlayer.pause() else AudioPlayer.resume()
                }
            }
        }
    }
}

@Composable
fun TrackItem(track: MusicTrack, isPlaying: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(if (isPlaying) Color(0xFF3C3C3C) else Color.Transparent)
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistText, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(track.id.source.displayName, color = Color(0xFF6200EE))
    }
}

@Composable
fun PlayerBar(track: MusicTrack, state: PlayerState, progress: Float, onTogglePlay: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF1F1F1F)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artistText, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), color = Color(0xFF6200EE))
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Text("⏮", modifier = Modifier.clickable { AudioPlayer.seekTo(0) })
                Text(if (state.isPlaying) "⏸" else "▶️", modifier = Modifier.clickable(onClick = onTogglePlay))
                Text("⏭", modifier = Modifier.clickable { })
            }
        }
    }
}

suspend fun search(query: String, providerId: String): List<MusicTrack> = try {
    when (providerId) {
        "netease" -> (NeteaseProvider() as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
        "qq" -> (QQMusicProvider() as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
        "kugou" -> (KugouProvider() as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
        "kuwo" -> (KuwoProvider() as? SearchCapability)?.searchSongs(query, 1, 20)?.items ?: emptyList()
        else -> emptyList()
    }
} catch (e: Exception) { emptyList() }
