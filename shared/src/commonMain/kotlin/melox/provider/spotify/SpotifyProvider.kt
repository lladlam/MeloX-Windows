package melox.provider.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import melox.lyrics.LrcLibLyricsClient
import melox.lyrics.LyricsDocument
import melox.music.model.*
import melox.music.provider.*
import okhttp3.OkHttpClient

/** Spotify-native Web API capabilities and librespot playback, mapped from Android main. */
class SpotifyProvider(
    private val clientId: String = "",
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
    private val playbackProviders: () -> List<MusicProvider> = { emptyList() },
) : MusicProvider, SearchCapability, CatalogSearchCapability, PlaybackCapability, DownloadCapability,
    FavoriteCapability, UserLibraryCapability, PlaylistCapability, PlaylistWriteCapability,
    AlbumCapability, ArtistCapability, LyricsCapability, LibraryCollectionCapability, PlaylistSyncCapability {
    private val api = SpotifyApiClient(clientId, httpClient)
    private val lyricsClient = LrcLibLyricsClient(httpClient)
    private val librespot get() = SpotifyLibrespotPlayback.shared
    override val source = MusicSource.Spotify
    override val displayName = source.displayName
    override val capabilities = setOf(
        MusicCapability.Search, MusicCapability.Playback, MusicCapability.Lyrics,
        MusicCapability.Library, MusicCapability.Playlists, MusicCapability.PlaylistWrite,
        MusicCapability.Albums, MusicCapability.Artists, MusicCapability.Favorites,
    )

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int) = api.searchSongs(query, page, pageSize)
    override suspend fun searchPlaylists(query: String, page: Int, pageSize: Int) = api.searchPlaylists(query, page, pageSize)
    override suspend fun searchAlbums(query: String, page: Int, pageSize: Int) = api.searchAlbums(query, page, pageSize)
    override suspend fun searchArtists(query: String, page: Int, pageSize: Int) = api.searchArtists(query, page, pageSize)
    override suspend fun accountSummary(): MusicAccountSummary? = api.accountSummary()
    override suspend fun userPlaylists(page: Int, pageSize: Int) = api.userPlaylists(page, pageSize)
    override suspend fun writablePlaylists(page: Int, pageSize: Int) = api.writablePlaylists(page, pageSize)
    override suspend fun playlistDetail(playlist: MusicPlaylistSummary, page: Int, pageSize: Int) = api.playlistDetail(playlist, page, pageSize)
    override suspend fun albumDetail(album: MusicAlbumSummary, page: Int, pageSize: Int) = api.albumDetail(album, page, pageSize)
    override suspend fun artistDetail(artist: MusicArtistSummary, page: Int, pageSize: Int) = api.artistDetail(artist, page, pageSize)
    override suspend fun lyrics(track: MusicTrack): LyricsDocument {
        require(track.id.source == source)
        return lyricsClient.lyrics(track.title, track.artists.firstOrNull()?.name.orEmpty(), track.durationMs ?: 0L)
    }
    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) = api.setFavorite(track, favorite)
    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) = api.addTrackToPlaylist(track, playlist)
    override suspend fun savedAlbums(page: Int, pageSize: Int) = api.savedAlbums(page, pageSize)
    override suspend fun followedArtists(page: Int, pageSize: Int) = api.followedArtists(page, pageSize)
    override suspend fun createPlaylist(name: String) = api.createPlaylist(name)
    override suspend fun renamePlaylist(playlist: MusicPlaylistSummary, name: String) {
        require(playlist.id.source == source)
        api.renamePlaylist(playlist, name)
    }
    override val canDeletePlaylists = true
    override suspend fun deletePlaylist(playlist: MusicPlaylistSummary) {
        require(playlist.id.source == source)
        api.deletePlaylist(playlist)
    }
    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: MusicPlaylistSummary) {
        require(track.id.source == source && playlist.id.source == source)
        api.removeTrackFromPlaylist(track, playlist)
    }
    override suspend fun reorderPlaylistTrack(playlist: MusicPlaylistSummary, track: MusicTrack, newIndex: Int) {
        require(track.id.source == source && playlist.id.source == source)
        api.reorderPlaylistTrack(playlist, track, newIndex)
    }

    private suspend fun nativePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        require(track.id.source == source)
        var session = SpotifySessionStore.read()
        if (!session.isLoggedIn) return PlaybackResolution.LoginRequired
        if (session.needsRefresh()) session = SpotifyOAuth(clientId.ifBlank { SpotifyClientConfig.effective() }, httpClient).refresh(session)
        val file = librespot.resolveToFile(track.id.value, session.accessToken)
        return PlaybackResolution.Playable(url = file.toURI().toString(), requestedQuality = quality, actualQuality = quality, format = "ogg")
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        require(track.id.source == source)
        return try { nativePlayback(track, quality) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { resolveFallback(track, quality) }
    }

    override suspend fun resolveDownload(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution =
        try { nativePlayback(track, quality) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { PlaybackResolution.Unavailable(error.message ?: "Spotify 下载失败") }

    private suspend fun resolveFallback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution {
        if (track.title.isBlank() || track.artists.isEmpty()) return PlaybackResolution.Unavailable("Spotify fallback 缺少曲目标题或艺人信息")
        val query = "${track.title} ${track.artists.first().name}"
        val providers = playbackProviders().filter { it.source != source && it is SearchCapability && it is PlaybackCapability }
        val candidates = coroutineScope {
            providers.map { provider -> async {
                try { (provider as SearchCapability).searchSongs(query, 1, 10).items }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptyList() }
            } }.awaitAll().flatten()
        }
        for (match in SpotifyTrackMatcher.rank(track, candidates)) {
            val provider = providers.firstOrNull { it.source == match.candidate.id.source } as? PlaybackCapability ?: continue
            val resolution = try { provider.resolvePlayback(match.candidate, quality) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (resolution is PlaybackResolution.Playable || resolution is PlaybackResolution.Preview) return resolution
        }
        return PlaybackResolution.Unavailable("未找到可可靠匹配的非 Spotify 音源")
    }
}
