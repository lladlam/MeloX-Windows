package melox.library
import melox.model.SearchSong

data class NeteaseHomePodcast(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val programId: Long? = null,
    val playbackSong: SearchSong? = null,
)
data class NeteaseHomeContent(
    val playlists: List<NeteasePlaylistSummary>,
    val newSongs: List<SearchSong>,
    val recentlyTrending: List<SearchSong> = emptyList(),
    val tailoredSongs: List<SearchSong> = emptyList(),
    val chartPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val radarPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val personalPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val regionalSongs: List<SearchSong> = emptyList(),
    val roamingSongs: List<SearchSong> = emptyList(),
    val similarSongs: List<SearchSong> = emptyList(),
    val podcasts: List<NeteaseHomePodcast> = emptyList(),
)
