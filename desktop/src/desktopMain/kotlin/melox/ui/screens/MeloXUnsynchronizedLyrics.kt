package melox.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.lyrics.LyricsDocument
import melox.ui.desktop.desktopPromotedDragScroll

/** Plain text is manually browsed; it has no playback focus or seek targets. */
@Composable
internal fun MeloXUnsynchronizedLyrics(document: LyricsDocument, foreground: Color, interactive: Boolean, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val dragModifier = if (interactive) Modifier.desktopPromotedDragScroll(listState) else Modifier
    LazyColumn(modifier.fillMaxSize().then(dragModifier).testTag("player-lyrics").semantics {
        this[MeloXLyricWordLinesKey] = 0
        this[MeloXLyricDocumentKey] = "${document.source}:${document.quality};lines=${document.lines.size};unsynchronized=true"
    }, state = listState, contentPadding = PaddingValues(horizontal = 28.dp, vertical = 32.dp), userScrollEnabled = interactive) {
        item { Text("歌词暂无时间轴 · 可手动滚动", color = foreground.copy(alpha = .55f), fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 24.dp).testTag("lyrics-unsynchronized")) }
        itemsIndexed(document.lines) { index, line ->
            Text(line.text, color = foreground.copy(alpha = .85f), fontSize = 25.sp, lineHeight = 36.sp,
                modifier = Modifier.padding(bottom = 16.dp).testTag("lyric-line-$index"))
        }
    }
}
