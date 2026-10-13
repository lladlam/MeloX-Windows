package melox.playback

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import melox.lyrics.*
import melox.music.model.*
import melox.provider.local.*
import melox.platform.getPreferences
import melox.model.SearchSong
import kotlin.test.*

class DesktopLocalLyricsPersistenceTest {
    @Test fun rescanningRealFilePreservesFavoriteRecognitionArtworkAndAuthoredLyricsAcrossRepositoryReopen() = runBlocking<Unit> {
        val prefs = getPreferences("melox_local_music")
        val before = listOf("tracks", "roots").associateWith { prefs.getString(it, null) }
        val directory = Files.createTempDirectory("melox-local-lyrics").toFile()
        try {
            prefs.putValues(mapOf("tracks" to "[]", "roots" to "[]"))
            val bytes = ByteBuffer.allocate(44 + 4410 * 4).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + 4410 * 4).put("WAVEfmt ".toByteArray()).putInt(16)
                .putShort(1).putShort(2).putInt(44100).putInt(176400).putShort(4).putShort(16)
                .put("data".toByteArray()).putInt(4410 * 4).array()
            val file = File(directory, "generated-silence.wav").apply { writeBytes(bytes) }
            val repo = LocalMusicRepository()
            repo.addScanRoot(LocalScanRoot(directory.path, 0))
            val record = LocalMediaScanner(repo).scanAll().single { it.contentUri == file.toURI().toString() }
            val lyrics = TtmlLyricsParser.parse("""<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:00:00.000" end="00:00:01.000"><span begin="00:00:00.000" end="00:00:00.500">Generated</span><span begin="00:00:00.500" end="00:00:01.000"> lyrics</span></p></div></body></tt>""").copy(source = LyricSource.Local)
            assertEquals(1, lyrics.lines.size)
            assertEquals(2, lyrics.lines.first().syllables.size)
            repo.setFavorite(record.fileKey, true)
            assertTrue(repo.updateRecognition(record.fileKey, SearchSong(12345, "Recognized title", "Artist", "Album", "https://example.test/art.jpg")))
            assertTrue(repo.updateLyrics(record.fileKey, lyrics))
            LocalMediaScanner(repo).scanAll()
            val reopened = LocalMusicRepository()
            val persisted = assertNotNull(reopened.track(record.fileKey))
            assertTrue(persisted.isFavorite)
            assertEquals(12345L, persisted.recognizedNeteaseId)
            assertEquals("Recognized title", persisted.recognizedTitle)
            assertEquals("https://example.test/art.jpg", persisted.recognizedArtworkUrl)
            assertEquals(lyrics, persisted.cachedLyrics)
            val track = MusicTrack(MusicResourceId(MusicSource.Local, record.fileKey), record.title, emptyList())
            assertEquals(lyrics, LocalProvider(repository = reopened).lyrics(track))
            reopened.setFavorite(record.fileKey, false)
            assertFalse(assertNotNull(LocalMusicRepository().track(record.fileKey)).isFavorite)
            assertEquals(lyrics, LocalMusicRepository().track(record.fileKey)?.cachedLyrics)
            assertEquals(MusicSource.Local, track.id.source)
            assertTrue(LocalProvider(repository = reopened).lyrics(track.copy(id = MusicResourceId(MusicSource.Local, "missing"))).lines.isEmpty())
        } finally { prefs.putValues(before); directory.deleteRecursively() }
    }
}
