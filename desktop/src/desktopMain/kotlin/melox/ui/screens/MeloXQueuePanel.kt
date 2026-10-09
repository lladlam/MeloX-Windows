package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.playback.MeloXPlaybackModeRuntime
import melox.playback.PlaybackCommands
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.meloXBackdropBlur

/** Source queue sections, scroll residency, native track identity and upcoming-only reorder. */
@Composable
internal fun QueuePage(sourceColor: Color, interactive: Boolean = true, modifier: Modifier = Modifier) {
    val queue by PlaybackCommands.queue.collectAsState()
    val player = PlaybackCommands.playerController()
    val repeat by player?.repeatMode?.collectAsState() ?: remember { mutableIntStateOf(0) }
    val shuffle by player?.shuffleEnabled?.collectAsState() ?: remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val historyCount = queue.index.coerceIn(0, queue.songs.size)
    val currentEntryId = queue.entryIds.getOrNull(queue.index)
    LaunchedEffect(currentEntryId, historyCount) {
        listState.scrollToItem(if (historyCount == 0) 0 else historyCount + 1)
    }
    val upcoming = if (queue.index >= 0) ((queue.index + 1) until queue.songs.size).toList() else emptyList()
    val manual = upcoming.filter { queue.origins.getOrNull(it) == PlaybackCommands.QUEUE_ORIGIN_MANUAL }
    val continuing = upcoming.filter { queue.origins.getOrNull(it) != PlaybackCommands.QUEUE_ORIGIN_MANUAL }
    Column(modifier.fillMaxSize()) {
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(shape)
            .meloXBackdropBlur(shape, 20.dp, Color.Black.copy(alpha = .095f)).padding(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QueueModeButton(MeloXSymbol.Shuffle, shuffle, interactive, "queue-shuffle", "随机播放", Modifier.weight(1f)) {
                    player?.setShuffleEnabled(!shuffle)
                }
                QueueModeButton(if (repeat == PlaybackCommands.REPEAT_ONE) MeloXSymbol.RepeatOne else MeloXSymbol.Repeat,
                    repeat != PlaybackCommands.REPEAT_OFF, interactive, "queue-repeat", "循环播放", Modifier.weight(1f)) {
                    player?.cycleRepeatMode()
                }
                QueueModeButton(MeloXSymbol.Infinity, MeloXPlaybackModeRuntime.autoplayEnabled, interactive,
                    "queue-autoplay", "无尽播放", Modifier.weight(1f)) {
                    player?.setAutoplayEnabled(!MeloXPlaybackModeRuntime.autoplayEnabled)
                }
            }
        }
        LazyColumn(state = listState, userScrollEnabled = interactive, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 10.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            fun section(title: String, indices: List<Int>, subdued: Boolean = false) {
                item(key = "section-$title") {
                    Text(title, color = Color.White.copy(alpha = if (subdued) .62f else 1f), fontSize = if (subdued) 18.sp else 22.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                }
                items(indices, key = { "$title-${queue.entryIds.getOrNull(it) ?: it}" }) { index ->
                    val song = queue.songs[index]
                    val entryId = queue.entryIds.getOrNull(index)
                    var dragOffset by remember(entryId) { mutableFloatStateOf(0f) }
                    Row(Modifier.fillMaxWidth().testTag("queue-track-$index").clip(RoundedCornerShape(12.dp))
                        .pointerInput(interactive, entryId, queue.index) {
                            if (!interactive || index <= queue.index) return@pointerInput
                            detectDragGesturesAfterLongPress(onDragEnd = { dragOffset = 0f }, onDragCancel = { dragOffset = 0f }) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val currentQueue = PlaybackCommands.queue.value
                                val current = currentQueue.entryIds.indexOf(entryId)
                                val rowHeight = 52.dp.toPx()
                                if (dragOffset > rowHeight && current > currentQueue.index && current < currentQueue.songs.lastIndex) {
                                    player?.move(current, current + 1); dragOffset -= rowHeight
                                } else if (dragOffset < -rowHeight && current > currentQueue.index + 1) {
                                    player?.move(current, current - 1); dragOffset += rowHeight
                                }
                            }
                        }.clickable(enabled = interactive) {
                            PlaybackCommands.queue.value.entryIds.indexOf(entryId).takeIf { it >= 0 }?.let { selected ->
                                player?.seekToIndex(selected)
                                player?.play()
                            }
                        }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MeloXArtworkImage(song.artworkUrl, sourceColor, Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(song.name, color = Color.White, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artists, color = Color.White.copy(alpha = .58f), fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                        }
                        if (index > queue.index) MeloXSymbolIcon(MeloXSymbol.ListBullet, size = 22, iconSize = 20, color = Color.White.copy(alpha = .48f))
                    }
                }
            }
            if (historyCount > 0) section("历史记录", (0 until historyCount).toList(), subdued = true)
            if (manual.isNotEmpty()) section("队列", manual)
            section("继续播放", continuing)
            if (continuing.isEmpty()) item(key = "continue-empty") {
                Box(Modifier.fillMaxWidth().height(96.dp), Alignment.Center) {
                    Text("没有待播放歌曲", color = Color.White.copy(alpha = .55f), fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun QueueModeButton(symbol: MeloXSymbol, selected: Boolean, enabled: Boolean, tag: String, description: String,
    modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).testTag(tag).clip(RoundedCornerShape(22.dp))
        .background(Color.White.copy(alpha = if (selected) .70f else .12f))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected; contentDescription = description }, contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = 24, iconSize = 22,
            color = if (!enabled) Color.White.copy(alpha = .25f) else if (selected) Color.Black.copy(alpha = .62f) else Color.White.copy(alpha = .86f))
    }
}
