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
) : MusicProvider, SearchCapability, PlaybackCapability, LyricsCapability {
    override val source: MusicSource = MusicSource.Local
    override val displayName: String = source.displayName
    override val capabilities: Set<MusicCapability> = setOf(MusicCapability.Playback, MusicCapability.Library, MusicCapability.Search, MusicCapability.Lyrics)

    override suspend fun searchSongs(query: String, page: Int, pageSize: Int): MusicPage<MusicTrack> = withContext(Dispatchers.IO) {
        val all = repository.tracks().filter { record ->
            query.isBlank() || listOf(record.title, record.artist, record.album, record.displayName).any { it.contains(query, true) }
        }
        val result = all.drop((page.coerceAtLeast(1) - 1) * pageSize.coerceAtLeast(1)).take(pageSize.coerceAtLeast(1)).map { record ->
            MusicTrack(MusicResourceId(source, record.fileKey), record.title,
                listOf(MusicArtistRef(name = record.artist)), album = record.album.takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it) },
                artworkUrl = record.artworkUri, durationMs = record.durationMs.takeIf { it > 0 },
                providerMetadata = ProviderTrackMetadata.Local(record.contentUri, record.fileKey))
        }
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
