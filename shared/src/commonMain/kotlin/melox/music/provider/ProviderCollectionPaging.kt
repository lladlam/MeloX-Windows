package melox.music.provider

import melox.music.model.MusicPlaylistDetail
import melox.music.model.MusicPlaylistSummary
import melox.music.model.MusicTrack
import melox.music.model.MusicSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Do not infer "not liked" from a partial first page or silently truncated library. */
internal suspend fun PlaylistCapability.containsNativeTrack(playlist: MusicPlaylistSummary, track: MusicTrack): Boolean {
    require(playlist.id.source == track.id.source)
    var count = 0L
    val signatures = mutableSetOf<List<String>>()
    for (page in 1..1_000) {
        currentCoroutineContext().ensureActive()
        val loaded = playlistDetail(playlist, page, 100)
        if (loaded.tracks.any { it.id == track.id }) return true
        val ids = loaded.tracks.map { it.id.value }
        if (ids.isNotEmpty()) check(signatures.add(ids)) { "音源重复返回同一页，无法确认喜爱状态" }
        count += ids.size
        if (loaded.hasMore == true) {
            check(ids.isNotEmpty()) { "喜爱曲库尚未完整读取，未更改喜爱状态" }
            continue
        }
        if (loaded.hasMore == false) return false
        if (loaded.total != null && count >= loaded.total) return false
        check(ids.isNotEmpty() || loaded.total == null) { "喜爱曲库尚未完整读取，未更改喜爱状态" }
        if (ids.isEmpty() || loaded.total == null && ids.size < 100) return false
    }
    error("喜爱曲库过大，尚未完整读取，未更改喜爱状态")
}

/** Loads all available pages without assuming a provider's first-page limit is the total. */
suspend fun PlaylistCapability.loadAllPlaylistTracks(
    playlist: MusicPlaylistSummary,
    pageSize: Int = 200,
): MusicPlaylistDetail {
    val first = playlistDetail(playlist, page = 1, pageSize = pageSize)
    val tracks = first.tracks.toMutableList()
    var target = maxOf(
        first.total ?: 0L,
        first.summary.trackCount?.toLong() ?: 0L,
        tracks.size.toLong(),
    )
    var page = 2
    var hasMore = first.hasMore
    // YouTube playlist entries are memberships: the same video can occur
    // multiple times. Its cursor pager already rejects repeated cursors.
    val preserveMemberships = playlist.id.source == MusicSource.YouTubeMusic
    while ((hasMore ?: (tracks.size < target)) && page <= 100) {
        val before = tracks.size
        val next = playlistDetail(playlist, page = page, pageSize = pageSize)
        hasMore = next.hasMore
        target = maxOf(target, next.total ?: 0L, next.summary.trackCount?.toLong() ?: 0L)
        tracks += next.tracks
        if (!preserveMemberships) {
            val distinct = tracks.distinctBy { it.id }
            tracks.clear()
            tracks += distinct
        }
        if (tracks.size == before || next.tracks.isEmpty()) {
            check(!preserveMemberships || hasMore != true) { "YouTube Music 歌单尚未完整读取" }
            break
        }
        page++
    }
    check(!preserveMemberships || hasMore != true) { "YouTube Music 歌单尚未完整读取" }
    return first.copy(
        summary = first.summary.copy(trackCount = tracks.size),
        tracks = tracks,
        total = maxOf(first.total ?: 0L, tracks.size.toLong()).takeIf { it > 0L },
        hasMore = hasMore,
    )
}
