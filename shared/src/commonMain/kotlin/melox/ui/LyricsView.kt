package melox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.lyrics.LyricLine
import melox.lyrics.LyricsDocument

@Composable
fun LyricsView(
    lyrics: LyricsDocument?,
    currentPositionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (lyrics == null || lyrics.lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无歌词", color = Color.Gray, fontSize = 16.sp)
        }
        return
    }

    val listState = rememberLazyListState()
    val currentLineIndex = remember(currentPositionMs, lyrics) {
        lyrics.lines.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
    }

    LaunchedEffect(currentLineIndex) {
        listState.animateScrollToItem(currentLineIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 100.dp),
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            LyricLineItem(
                line = line,
                isCurrent = index == currentLineIndex,
                isPast = index < currentLineIndex,
            )
        }
    }
}

@Composable
fun LyricLineItem(
    line: LyricLine,
    isCurrent: Boolean,
    isPast: Boolean,
) {
    val textColor = when {
        isCurrent -> Color.White
        isPast -> Color(0xFF666666)
        else -> Color(0xFF999999)
    }
    val fontSize = if (isCurrent) 20.sp else 16.sp
    val fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = line.text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 4.dp),
        )
        if (!line.translation.isNullOrBlank()) {
            Text(
                text = line.translation,
                color = textColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
