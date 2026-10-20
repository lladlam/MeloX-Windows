package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.model.SearchSong
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.glass.MeloXGlassTextField
import melox.ui.screens.MeloXArtworkImage

@Composable
internal fun MeloXDesktopCollectionDetail(
    kind: String,
    title: String,
    subtitle: String?,
    metadata: String?,
    description: String?,
    artworkUrl: String?,
    songs: List<SearchSong>,
    loading: Boolean,
    error: String?,
    playingId: Long?,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    favoriteLabel: String? = null,
    onFavorite: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    filter: String = "",
    onFilterChange: ((String) -> Unit)? = null,
    onSong: (SearchSong) -> Unit,
    onMore: ((SearchSong) -> Unit)? = null,
    status: String? = null,
    tag: String,
) {
    val colors = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize().background(colors.background).testTag(tag),
        contentPadding = PaddingValues(start = 42.dp, top = 26.dp, end = 42.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item("toolbar") {
            Row(Modifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopCollectionIconButton(MeloXSymbol.ChevronLeft, "desktop-collection-back", onBack)
                Spacer(Modifier.weight(1f))
            }
        }
        item("header") {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 34.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(34.dp),
            ) {
                MeloXArtworkImage(
                    artworkUrl,
                    colors.surfaceVariant,
                    Modifier.size(278.dp).shadow(18.dp, RoundedCornerShape(11.dp)).clip(RoundedCornerShape(11.dp)),
                    contentDescription = title,
                )
                Column(Modifier.weight(1f).padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(kind.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = colors.onSurface.copy(alpha = .52f))
                    Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    subtitle?.takeIf(String::isNotBlank)?.let {
                        Text(it, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, color = Color.Red, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    metadata?.takeIf(String::isNotBlank)?.let {
                        Text(it, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.onSurface.copy(alpha = .56f))
                    }
                    description?.takeIf(String::isNotBlank)?.let {
                        Text(it, fontSize = 12.5.sp, lineHeight = 17.sp, color = colors.onSurface.copy(alpha = .56f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onPlayAll, enabled = songs.isNotEmpty(), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                            MeloXSymbolIcon(MeloXSymbol.Play, size = 14, color = Color.White)
                            Spacer(Modifier.width(7.dp)); Text("播放", fontSize = 13.sp)
                        }
                        OutlinedButton(onClick = onShuffle, enabled = songs.isNotEmpty(), shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            MeloXSymbolIcon(MeloXSymbol.Shuffle, size = 14); Spacer(Modifier.width(7.dp)); Text("随机播放", fontSize = 13.sp)
                        }
                        if (favoriteLabel != null && onFavorite != null) OutlinedButton(onClick = onFavorite, shape = RoundedCornerShape(8.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                            Text(favoriteLabel, fontSize = 13.sp)
                        }
                        if (onDownload != null) DesktopCollectionIconButton(MeloXSymbol.Download, "desktop-collection-download", onDownload)
                    }
                }
            }
        }
        status?.let { message -> item("status") { Text(message, Modifier.padding(bottom = 12.dp), color = colors.onSurfaceVariant, fontSize = 12.sp) } }
        if (onFilterChange != null) item("filter") {
            MeloXGlassTextField(
                value = filter,
                onValueChange = onFilterChange,
                modifier = Modifier.widthIn(max = 360.dp).padding(bottom = 14.dp).testTag("desktop-collection-search"),
                placeholder = { Text("搜索", fontSize = 13.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = colors.onSurface),
            )
        }
        item("columns") {
            Row(Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#", Modifier.width(38.dp), fontSize = 11.sp, color = colors.onSurface.copy(alpha = .42f))
                Text("标题", Modifier.weight(1f), fontSize = 11.sp, color = colors.onSurface.copy(alpha = .42f))
                Text("专辑", Modifier.weight(.72f), fontSize = 11.sp, color = colors.onSurface.copy(alpha = .42f))
                Text("时长", Modifier.width(54.dp), fontSize = 11.sp, color = colors.onSurface.copy(alpha = .42f))
                Spacer(Modifier.width(34.dp))
            }
            HorizontalDivider(color = colors.onSurface.copy(alpha = .10f))
        }
        when {
            loading -> item("loading") { Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            error != null -> item("error") { Text(error, Modifier.padding(vertical = 28.dp), color = colors.error) }
            songs.isEmpty() -> item("empty") { Text("暂无歌曲", Modifier.padding(vertical = 28.dp), color = colors.onSurface.copy(alpha = .48f)) }
            else -> itemsIndexed(songs, key = { _, song -> song.providerTrack?.id?.toString() ?: song.id.toString() }) { index, song ->
                DesktopCollectionTrackRow(index + 1, song, playingId == song.id, { onSong(song) }, onMore?.let { { it(song) } })
            }
        }
    }
}

@Composable
private fun DesktopCollectionTrackRow(index: Int, song: SearchSong, playing: Boolean, onClick: () -> Unit, onMore: (() -> Unit)?) {
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(7.dp)).hoverable(interaction)
            .background(if (hovered) colors.onSurface.copy(alpha = .055f) else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(index.toString(), Modifier.width(38.dp), fontSize = 12.sp, color = if (playing) Color.Red else colors.onSurface.copy(alpha = .48f))
        MeloXArtworkImage(song.artworkUrl, colors.surfaceVariant, Modifier.size(38.dp).clip(RoundedCornerShape(6.dp)))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(song.name, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, color = if (playing) Color.Red else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artists, fontSize = 11.5.sp, color = colors.onSurface.copy(alpha = .52f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(song.album, Modifier.weight(.72f), fontSize = 12.sp, color = colors.onSurface.copy(alpha = .58f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(desktopDuration(song.durationMs), Modifier.width(54.dp), fontSize = 12.sp, color = colors.onSurface.copy(alpha = .48f))
        if (onMore != null) DesktopCollectionIconButton(MeloXSymbol.Ellipsis, "desktop-collection-more:${song.id}", onMore)
        else Spacer(Modifier.width(34.dp))
    }
}

@Composable
private fun DesktopCollectionIconButton(symbol: MeloXSymbol, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        Modifier.size(34.dp).clip(CircleShape).hoverable(interaction)
            .background(if (hovered) MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else Color.Transparent)
            .clickable(onClick = onClick).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { MeloXSymbolIcon(symbol, size = 15, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .74f)) }
}

private fun desktopDuration(milliseconds: Long): String {
    if (milliseconds <= 0) return "--:--"
    val seconds = milliseconds / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
