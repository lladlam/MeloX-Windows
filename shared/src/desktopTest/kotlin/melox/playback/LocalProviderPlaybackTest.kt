package melox.playback

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.provider.local.LocalProvider
import melox.provider.local.LocalMusicRepository
import melox.provider.local.LocalTrackRecord
import melox.music.provider.LocalAggregationCapability
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class LocalProviderPlaybackTest {
    @Test fun registeredLocalLibraryReadsPersistedCatalogThroughItsAggregationCapability() = runBlocking {
        val repository = LocalMusicRepository()
        val previous = repository.tracks()
        val directory = Files.createTempDirectory("melox-local-library").toFile()
        try {
            repository.replaceTracks(emptyList())
            val capability = assertIs<LocalAggregationCapability>(MusicProviderRegistry(listOf(LocalProvider(repository = repository))).require(MusicSource.Local))
            assertTrue(capability.aggregationTracks(1, 2).items.isEmpty())
            val records = (1..3).map { index ->
                val file = File(directory, "library-$index.wav")
                val format = javax.sound.sampled.AudioFormat(8000f, 16, 1, true, false)
                javax.sound.sampled.AudioInputStream(java.io.ByteArrayInputStream(ByteArray(160)), format, 80).use {
                    javax.sound.sampled.AudioSystem.write(it, javax.sound.sampled.AudioFileFormat.Type.WAVE, file)
                }
                LocalTrackRecord("library-key-$index", file.toURI().toString(), file.name, "Library $index", "Artist", "Album", 10,
                    "audio/wav", file.length(), file.lastModified())
            }
            repository.replaceTracks(records)
            val first = capability.aggregationTracks(1, 2)
            val second = capability.aggregationTracks(2, 2)
            assertEquals(3L, first.total)
            assertEquals(listOf("library-key-1", "library-key-2"), first.items.map { it.id.value })
            assertEquals(listOf("library-key-3"), second.items.map { it.id.value })
            assertEquals(records[2].contentUri, assertIs<ProviderTrackMetadata.Local>(second.items.single().providerMetadata).contentUri)
        } finally {
            repository.replaceTracks(previous)
            directory.deleteRecursively()
        }
    }
    @Test fun realLocalProviderResolvesEncodedUnicodePathsAndMetadataThroughProductionUri() {
        val root = Files.createTempDirectory("melox-local-provider").toFile()
        try {
            val file = File(root, "本地 + & # track.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val track = MusicTrack(MusicResourceId(MusicSource.Local, "folder/key+%"), "标题 & + # %", listOf(MusicArtistRef(name = "歌手 & +")),
                providerMetadata = ProviderTrackMetadata.Local(file.toURI().toString(), "folder/key+%"))
            val resolver = ProviderPlaybackResolver(NeteasePlaybackResolver(cookieProvider = { "" }, localSourceProvider = { null }),
                MusicProviderRegistry(listOf(LocalProvider())))
            val request = resolver.resolveProviderRequest(ProviderPlaybackResolver.uriForTrack(track, AudioQualityTier.Standard))
            assertEquals(file.canonicalPath, request.uri)
            assertTrue(request.headers.isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun missingOrForeignLocalLocationsAreUnavailableRatherThanImpersonatingOnlineMusic() = runBlocking<Unit> {
        val provider = LocalProvider()
        for (location in listOf("/path/that/does/not/exist.wav", "https://example.com/foreign.mp3", "content://media/audio/1")) {
            val track = MusicTrack(MusicResourceId(MusicSource.Local, "missing"), "missing", emptyList(), providerMetadata = ProviderTrackMetadata.Local(location, "missing"))
            assertIs<PlaybackResolution.Unavailable>(provider.resolvePlayback(track, AudioQualityTier.Standard))
        }
    }
}
