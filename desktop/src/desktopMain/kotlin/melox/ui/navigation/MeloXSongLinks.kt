package melox.ui.navigation

import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.music.model.ProviderTrackMetadata

/** Provider transport policy shared by both layouts, independent of their UI. */
internal fun songShareUrl(song: SearchSong): String? {
    song.neteaseSongId?.let { return "https://music.163.com/song?id=$it" }
    val track = song.providerTrack ?: return null
    val id = java.net.URLEncoder.encode(track.id.value, "UTF-8")
    return when (track.id.source) {
        MusicSource.QQMusic -> "https://y.qq.com/n/ryqq/songDetail/$id"
        MusicSource.Spotify -> "https://open.spotify.com/track/$id"
        MusicSource.YouTubeMusic -> "https://music.youtube.com/watch?v=$id"
        MusicSource.Bilibili -> (track.providerMetadata as? ProviderTrackMetadata.Bilibili)?.let { "https://www.bilibili.com/video/${it.bvid}" }
        else -> null
    }
}
