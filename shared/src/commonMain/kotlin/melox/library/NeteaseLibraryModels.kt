package melox.library

import melox.model.SearchSong
import melox.music.model.MusicPlaylistSummary

data class NeteasePlaylistSummary(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val trackCount: Int,
    val creatorName: String,
    val creatorUserId: Long? = null,
    val playCount: Long = 0L,
    val description: String? = null,
    /**
     * Optional provider-neutral backing playlist. Keeping this on the legacy UI
     * model lets the existing MeloX playlist list/detail animation renderer stay
     * the single renderer for NetEase, QQ Music and Kugou.
     */
    val providerPlaylist: MusicPlaylistSummary? = null,
)

data class NeteasePlaylistDetail(
    val summary: NeteasePlaylistSummary,
    val songs: List<SearchSong>,
)

data class NeteaseLibrarySnapshot(
    val playlists: List<NeteasePlaylistSummary>,
    val likedSongs: List<SearchSong>,
    val recentSongs: List<SearchSong>,
    val likedPlaylistId: Long? = null,
)
