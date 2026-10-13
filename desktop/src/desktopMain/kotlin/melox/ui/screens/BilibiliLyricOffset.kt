package melox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.provider.bilibili.BilibiliLyricOffsetStore

/** Pinned per-resource offset, observed by every presentation without changing playback time. */
@Composable
internal fun rememberBilibiliLyricOffset(track: MusicTrack): Int {
    if (track.id.source != MusicSource.Bilibili) return 0
    val offset by BilibiliLyricOffsetStore.state(track.id.value)
    return offset
}

internal fun effectiveBilibiliLyricAdvance(globalAdvanceMs: Int, trackOffsetMs: Int): Long =
    BilibiliLyricOffsetStore.effectiveAdvance(globalAdvanceMs, trackOffsetMs)
