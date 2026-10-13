package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.network.NeteaseMusicOperationsClient
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXGlassSheet
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXSymbol
import melox.ui.theme.MeloXColors
import melox.ui.navigation.MeloXBackHandler
import melox.ui.legal.pinnedString

private enum class SongActionPage { Main, Comments }

@Composable
fun MeloXSongActionsOverlay(
    song: SearchSong,
    queue: List<SearchSong>,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val provider = song.providerTrack
    val neteaseSongId = song.neteaseSongId
    val ops = remember { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var page by remember(song.id, visible) { mutableStateOf(SongActionPage.Main) }
    var message by remember(song.id, visible) { mutableStateOf<String?>(null) }
    var liked by remember(song.id, visible) { mutableStateOf(false) }
    var comments by remember(song.id, visible) { mutableStateOf<List<String>>(emptyList()) }
    MeloXBackHandler(enabled = visible, priority = 100) {
        if (page != SongActionPage.Main) page = SongActionPage.Main else onDismiss()
    }

    LaunchedEffect(visible, neteaseSongId, page) {
        if (!visible || page != SongActionPage.Comments || neteaseSongId == null) return@LaunchedEffect
        runCatching { ops.songComments(neteaseSongId) }
            .onSuccess { loaded -> comments = loaded.map { "${it.user}: ${it.content}" } }
            .onFailure { message = it.message ?: "评论加载失败" }
    }

    MeloXGlassSheet(
        visible = visible,
        onDismiss = {
            if (page != SongActionPage.Main) page = SongActionPage.Main else onDismiss()
        },
        modifier = Modifier.testTag("song-actions").onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyUp && event.key == Key.Escape) {
                if (page != SongActionPage.Main) page = SongActionPage.Main else onDismiss()
                true
            } else false
        },
        title = if (page == SongActionPage.Main) "歌曲操作" else "评论",
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp),
        ) {
            Text(song.name, color = MeloXColors.OnSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 16.dp))
            Text(song.artists, color = MeloXColors.OnSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            message?.let { Text(it, color = Color(0xFFFF8A90), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            when (page) {
                SongActionPage.Main -> {
                    if (provider?.id?.source == MusicSource.Bilibili) {
                        val persistedOffset by melox.provider.bilibili.BilibiliLyricOffsetStore.state(provider.id.value)
                        var displayedOffset by remember(provider.id.value, persistedOffset) { mutableStateOf(persistedOffset) }
                        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp)) {
                            Text(pinnedString("player_lyric_offset"), fontWeight = FontWeight.SemiBold)
                            Text(when {
                                displayedOffset == 0 -> pinnedString("player_lyric_sync")
                                displayedOffset > 0 -> pinnedString("player_lyric_ahead", displayedOffset)
                                else -> pinnedString("player_lyric_behind", displayedOffset)
                            }, color = MeloXColors.OnSurfaceVariant, fontSize = 12.sp)
                            melox.ui.glass.MeloXLiquidSlider(
                                value = displayedOffset.toFloat(),
                                onTransientValueChange = { displayedOffset = it.toInt() },
                                onValueChange = { displayedOffset = it.toInt(); melox.provider.bilibili.BilibiliLyricOffsetStore.write(provider.id.value, displayedOffset) },
                                valueRange = -5_000f..5_000f, stepSize = 100f, visibilityThreshold = 1f,
                                contentDescription = pinnedString("player_lyric_offset"), modifier = Modifier.testTag("bilibili-lyric-offset"),
                            )
                        }
                    }
                    Text("更多操作", Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MeloXColors.OnSurface)
                    if (song.album.isNotBlank()) {
                        ActionRow("查看专辑", MeloXSymbol.Library) {
                            val id = (song.albumId ?: provider?.album?.id?.value?.toLongOrNull())?.takeIf { neteaseSongId != null && it > 0L }
                            onDismiss()
                            if (id != null) melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Album(id, song.album))
                            else melox.ui.navigation.MeloXSearchLaunchBus.post(song.album, melox.network.MeloXSearchKind.Albums, provider?.id?.source ?: MusicSource.Netease)
                        }
                    }
                    if (song.artists.isNotBlank()) {
                        ActionRow("查看歌手", MeloXSymbol.Person) {
                            val id = (song.artistId ?: provider?.artists?.firstOrNull()?.id?.value?.toLongOrNull())?.takeIf { neteaseSongId != null && it > 0L }
                            onDismiss()
                            if (id != null) melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Artist(id))
                            else melox.ui.navigation.MeloXSearchLaunchBus.post(provider?.artists?.firstOrNull()?.name ?: song.artists, melox.network.MeloXSearchKind.Artists, provider?.id?.source ?: MusicSource.Netease)
                        }
                    }
                    ActionRow("下一首播放", MeloXSymbol.NextTrack) {
                        PlaybackCommands.playNext(song)
                        onDismiss()
                    }
                    ActionRow("添加到播放队列", MeloXSymbol.Plus) {
                        PlaybackCommands.addToQueue(song)
                        onDismiss()
                    }
                    if (neteaseSongId == null) {
                        ActionRow("喜爱（当前来源不支持）", MeloXSymbol.Heart, enabled = false) {}
                        ActionRow("评论（当前来源不支持）", MeloXSymbol.Chat, enabled = false) {}
                    } else {
                        ActionRow(if (liked) "取消喜爱" else "喜爱", MeloXSymbol.Heart) {
                            val desired = !liked
                            scope.launch {
                                runCatching { ops.setSongLiked(neteaseSongId, desired) }
                                    .onSuccess { liked = desired }
                                    .onFailure { message = it.message ?: "喜爱失败" }
                            }
                        }
                        ActionRow("评论", MeloXSymbol.Chat) { page = SongActionPage.Comments }
                    }
                    if (queue.isNotEmpty()) {
                        ActionRow("播放此队列", MeloXSymbol.Play) {
                            val playing = song.providerTrack
                            val index = queue.indexOfFirst { it === song }.takeIf { it >= 0 }
                                ?: queue.indexOfFirst { queued ->
                                    val queuedTrack = queued.providerTrack
                                    when {
                                        playing != null && queuedTrack?.id == playing.id -> true
                                        playing == null && queuedTrack == null && queued.id == song.id && song.id > 0L -> true
                                        else -> false
                                    }
                                } ?: -1
                            val player = PlaybackCommands::class.java
                                .getMethod("getActiveController${'$'}shared")
                                .invoke(PlaybackCommands) as? melox.playback.MeloXDesktopPlayer
                            when {
                                index >= 0 && player != null && player.snapshot().size == queue.size &&
                                    index == player.currentMediaItemIndex -> Unit
                                index >= 0 && player != null && player.snapshot().size == queue.size ->
                                    player.seekToIndex(index)
                                else -> PlaybackCommands.playQueue(queue, song.id)
                            }
                            onDismiss()
                        }
                    }
                }
                SongActionPage.Comments -> {
                    if (comments.isEmpty()) {
                        Text("暂无评论", color = MeloXColors.OnSurfaceVariant, modifier = Modifier.padding(16.dp))
                    } else {
                        comments.forEach { line ->
                            Text(line, color = MeloXColors.OnSurface, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, symbol: MeloXSymbol, enabled: Boolean = true, onClick: () -> Unit) {
    MeloXIosListRow(
        modifier = Modifier.testTag("song-action-$title"),
        title = title,
        leadingIcon = symbol,
        showChevron = false,
        onClick = if (enabled) onClick else null,
    )
}
