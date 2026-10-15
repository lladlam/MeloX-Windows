package melox.provider.local

import melox.music.model.AudioQualityTier
import melox.music.model.MusicPage
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.model.MusicResourceId
import melox.music.model.MusicArtistRef
import melox.music.model.MusicAlbumRef
import melox.music.model.ProviderTrackMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import melox.music.provider.MusicCapability
import melox.music.provider.MusicProvider
import melox.music.provider.PlaybackCapability
import melox.music.provider.SearchCapability
import melox.music.provider.LyricsCapability
import melox.lyrics.LyricsDocument
import melox.lyrics.AmlldbLyricsClient

class LocalProvider(
    context: Any? = null,
    private val repository: LocalMusicRepository = LocalMusicRepository(),
) : MusicProvider, SearchCapability, PlaybackCapability, LyricsCapability,
    melox.music.provider.FavoriteCapability, melox.music.provider.FavoriteStateCapability,
    melox.music.provider.PlaylistWriteCapability, melox.music.provider.PlaylistSyncCapability,
    melox.music.provider.PlaylistCapability {
    override val source: MusicSource = MusicSource.Local
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Playback, MusicCapability.Library, MusicCapability.Search, MusicCapability.Lyrics, MusicCapability.Favorites, MusicCapability.Playlists, MusicCapability.PlaylistWrite)

    private fun playlistSummary(value: LocalPlaylist) = melox.music.model.MusicPlaylistSummary(
        MusicResourceId(source, value.id), value.name, trackCount = value.trackKeys.size)

    private fun requirePlaylist(value: melox.music.model.MusicPlaylistSummary): LocalPlaylist {
        require(value.id.source == source) { "只能修改本地歌单" }
        return requireNotNull(repository.playlists().firstOrNull { it.id == value.id.value }) { "本地歌单不存在" }
    }

    override suspend fun writablePlaylists(page: Int, pageSize: Int) = withContext(Dispatchers.IO) {
        val all = repository.playlists()
        val size = pageSize.coerceIn(1, 200)
        MusicPage(all.drop((page.coerceAtLeast(1) - 1) * size).take(size).map(::playlistSummary), page, size, all.size.toLong())
    }

    override suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "歌单名称不能为空" }
        val playlist = LocalPlaylist(java.util.UUID.randomUUID().toString(), name.trim(), emptyList())
        repository.savePlaylist(playlist)
        playlistSummary(playlist)
    }

    override suspend fun addTrackToPlaylist(track: MusicTrack, playlist: melox.music.model.MusicPlaylistSummary) = withContext(Dispatchers.IO) {
        require(track.id.source == source) { "只能将本地曲目添加到本地歌单" }
        requireNotNull(repository.track(track.id.value)) { "本地文件未收录" }
        val current = requirePlaylist(playlist)
        repository.savePlaylist(current.copy(trackKeys = (current.trackKeys + track.id.value).distinct()))
    }

    override suspend fun renamePlaylist(playlist: melox.music.model.MusicPlaylistSummary, name: String) = withContext(Dispatchers.IO) {
        require(name.isNotBlank()) { "歌单名称不能为空" }
        repository.savePlaylist(requirePlaylist(playlist).copy(name = name.trim()))
    }

    override val canDeletePlaylists = true
    override suspend fun deletePlaylist(playlist: melox.music.model.MusicPlaylistSummary) = withContext(Dispatchers.IO) {
        repository.removePlaylist(requirePlaylist(playlist).id)
    }

    override suspend fun removeTrackFromPlaylist(track: MusicTrack, playlist: melox.music.model.MusicPlaylistSummary) = withContext(Dispatchers.IO) {
        require(track.id.source == source)
        val current = requirePlaylist(playlist)
        repository.savePlaylist(current.copy(trackKeys = current.trackKeys.filterNot { it == track.id.value }))
    }

    override suspend fun reorderPlaylistTrack(playlist: melox.music.model.MusicPlaylistSummary, track: MusicTrack, newIndex: Int) = withContext(Dispatchers.IO) {
        require(track.id.source == source)
        val current = requirePlaylist(playlist)
        require(track.id.value in current.trackKeys) { "曲目不在该本地歌单中" }
        val order = current.trackKeys.toMutableList().apply {
            remove(track.id.value)
            add(newIndex.coerceIn(0, size), track.id.value)
        }
        repository.savePlaylist(current.copy(trackKeys = order))
    }

    override suspend fun playlistDetail(playlist: melox.music.model.MusicPlaylistSummary, page: Int, pageSize: Int) = withContext(Dispatchers.IO) {
        val current = requirePlaylist(playlist)
        val records = repository.tracks().associateBy { it.fileKey }
        val size = pageSize.coerceIn(1, 200)
        val tracks = current.trackKeys.mapNotNull(records::get)
        melox.music.model.MusicPlaylistDetail(playlistSummary(current),
            tracks.drop((page.coerceAtLeast(1) - 1) * size).take(size).map(::musicTrack), tracks.size.toLong())
    }

    private fun musicTrack(record: LocalTrackRecord) = MusicTrack(MusicResourceId(source, record.fileKey), record.title,
        listOf(MusicArtistRef(name = record.artist)), album = record.album.takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it) },
        artworkUrl = record.artworkUri, durationMs = record.durationMs.takeIf { it > 0 },
        providerMetadata = ProviderTrackMetadata.Local(record.contentUri, record.fileKey))

    override suspend fun isFavorite(track: MusicTrack): Boolean = withContext(Dispatchers.IO) {
        require(track.id.source == source) { "只能读取本地曲目喜爱状态" }
        requireNotNull(repository.track(track.id.value)) { "本地文件未收录" }.isFavorite
    }

    override suspend fun setFavorite(track: MusicTrack, favorite: Boolean) = withContext(Dispatchers.IO) {
        require(track.id.source == source) { "只能修改本地曲目喜爱状态" }
        requireNotNull(repository.track(track.id.value)) { "本地文件未收录" }
        repository.setFavorite(track.id.value, favorite)
    }

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        val all = repository.tracks().filter { record ->
            query.isBlank() || listOf(record.title, record.artist, record.album, record.displayName).any { it.contains(query, true) }
        }
        val result = all.drop((page.coerceAtLeast(1) - 1) * pageSize.coerceAtLeast(1)).take(pageSize.coerceAtLeast(1)).map(::musicTrack)
        MusicPage(result, page, pageSize, all.size.toLong())
    }

    override suspend fun resolvePlayback(track: MusicTrack, quality: AudioQualityTier): PlaybackResolution = withContext(Dispatchers.IO) {
        val location = (track.providerMetadata as? ProviderTrackMetadata.Local)?.contentUri?.takeIf(String::isNotBlank)
            ?: repository.track(track.id.value)?.contentUri
            ?: return@withContext PlaybackResolution.Unavailable("本地文件未收录")
        val file = runCatching {
            if (location.startsWith("file:", true)) File(URI(location))
            else if (location.contains("://")) null else File(location)
        }.getOrNull()
        if (file == null || !file.isFile || !file.canRead()) PlaybackResolution.Unavailable("本地文件不存在或无法读取")
        else PlaybackResolution.Playable(file.canonicalPath, requestedQuality = quality, format = file.extension.lowercase())
    }

    override suspend fun lyrics(track: MusicTrack): LyricsDocument = withContext(Dispatchers.IO) {
        val local = repository.track(track.id.value) ?: return@withContext LyricsDocument(emptyList())
        local.cachedLyrics?.let { return@withContext it }
        val id = local.recognizedNeteaseId ?: return@withContext LyricsDocument(emptyList())
        AmlldbLyricsClient().lyrics(id).also { document ->
            if (document.lines.isNotEmpty()) repository.updateLyrics(track.id.value, document)
        }
    }
}
