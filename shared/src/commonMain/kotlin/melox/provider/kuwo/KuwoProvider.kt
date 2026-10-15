package melox.provider.kuwo

import melox.music.model.*
import melox.music.provider.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

class KuwoProvider(
    private val sessionProvider: () -> KuwoSession = { KuwoSession("", "") },
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) : MusicProvider, SearchCapability, PlaybackCapability, LyricsCapability, UserLibraryCapability, PlaylistCapability {
    override val source: MusicSource = MusicSource.Kuwo
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Search, MusicCapability.Playback,
        MusicCapability.Lyrics, MusicCapability.Library, MusicCapability.Playlists)

    private val api = KuwoApiClient(httpClient)
    private val playlists = KuwoPlaylistClient(sessionProvider, httpClient)
    private val lyricsClient = KuwoLyricsClient(httpClient)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> =
        withContext(Dispatchers.IO) { api.searchSongs(query, page, pageSize) }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        withContext(Dispatchers.IO) { api.resolvePlayback(track, quality) }

    override suspend fun lyrics(track: MusicTrack) = withContext(Dispatchers.IO) { lyricsClient.lyrics(track) }
    override suspend fun accountSummary(): MusicAccountSummary? = sessionProvider().takeIf(KuwoSession::isLoggedIn)?.let {
        MusicAccountSummary(source, it.userId, "酷我用户 ${it.userId}", subtitle = "手机号登录")
    }
    override suspend fun userPlaylists(page: Int, pageSize: Int) = withContext(Dispatchers.IO) { playlists.userPlaylists(page, pageSize) }
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) = withContext(Dispatchers.IO) { playlists.detail(playlist, page, pageSize) }
}
