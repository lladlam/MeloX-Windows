package melox.ui.screens

import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import melox.ui.navigation.songShareUrl

/** Supplied recommendation/ranking lists are not writable NetEase playlists. */
internal fun playlistShareText(playlist: NeteasePlaylistSummary, songs: List<SearchSong>, supplied: Boolean): String {
    if (!supplied && playlist.providerPlaylist == null && playlist.id > 0L) {
        return "${playlist.name}\nhttps://music.163.com/playlist?id=${playlist.id}"
    }
    // Share real per-song links for providers without a public playlist URL in
    // their model. Never turn a synthetic or foreign ID into a NetEase URL.
    return buildString {
        append(playlist.name)
        songs.forEach { song ->
            append("\n\n${song.name} - ${song.artists}")
            songShareUrl(song)?.let { append("\n$it") }
        }
    }
}
