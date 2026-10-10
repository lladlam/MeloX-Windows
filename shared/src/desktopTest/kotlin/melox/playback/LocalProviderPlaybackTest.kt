package melox.playback

import kotlinx.coroutines.runBlocking
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.provider.local.LocalProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class LocalProviderPlaybackTest {
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
