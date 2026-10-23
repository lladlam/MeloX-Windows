package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.music.model.MusicTrack
import melox.music.provider.MeloXLegacyUiBridge
import melox.playback.PlaybackCommands
import melox.playback.ProviderPlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopTextFontFamily

/** Desktop collection header/list for provider albums and artists opened from Search. */
@Composable
internal fun MeloXDesktopProviderCollection(title: String, subtitle: String, artwork: String?, tracks: List<MusicTrack>,
    loading: Boolean, error: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    val queue by PlaybackCommands.queue.collectAsState()
    val playback by melox.player.AudioPlayer.state.collectAsState()
    val scroll = rememberLazyListState()
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).desktopPromotedDragScroll(scroll)
            .testTag("desktop-provider-collection"), state = scroll,
            contentPadding = PaddingValues(horizontal = 34.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item("header") {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(Modifier.testTag("desktop-provider-collection-back").clip(RoundedCornerShape(5.dp))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onBack)
                        .padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MeloXSymbolIcon(MeloXSymbol.ChevronLeft, size = 12)
                        Text(desktopString("ui.common.back"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        MeloXArtworkImage(artwork, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(220.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(title, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold)
                            if (subtitle.isNotBlank()) Text(subtitle, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(desktopFormat("ui.common.song_count", tracks.size), fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DesktopDialogButton("ui.common.play_all", tracks.isNotEmpty(), "desktop-provider-collection-play",
                                prominent = true) { tracks.firstOrNull()?.let { ProviderPlaybackCommands.playQueue(tracks, it.id) } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            when {
                loading -> item("loading") {
                    Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
                error != null -> item("error") {
                    Column(Modifier.fillMaxWidth().height(260.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                        DesktopDialogButton("ui.common.retry", onClick = onRetry)
                    }
                }
                tracks.isEmpty() -> item("empty") {
                    Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                        Text(desktopString("ui.desktop.library.empty"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> itemsIndexed(tracks, key = { _, track -> "${track.id.source.storageValue}:${track.id.value}" }) { index, track ->
                    val song = melox.music.provider.MeloXLegacyUiBridge.track(track)
                    DesktopCollectionTrackRow(index + 1, song, queue.songs.getOrNull(queue.index)?.id == song.id,
                        playback.isPlaying, false, { ProviderPlaybackCommands.playQueue(tracks, track.id) }, onMore = null, showsArtwork = true)
                }
            }
            item("clearance") { Spacer(Modifier.height(80.dp)) }
        }
    }
}
