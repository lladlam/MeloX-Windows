package melox.provider.local

import java.io.File
import java.nio.file.Files
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.runBlocking
import melox.lyrics.*
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.platform.getPreferences
import kotlin.test.*

class LocalLyricsSidecarTest {
    @Test fun parserDetectsTimedFormatsAndPreservesLyricifyDuet() {
        val lyricify = LyricFileParser.parse("[offset:100]\n[4]你(1000,400)好(1400,600)\n[8]和声(1500,300)\n[5]再见(2500,500)")
        assertEquals(listOf("你好", "再见"), lyricify.lines.map { it.text })
        assertEquals(listOf(900L, 1300L), lyricify.lines.first().syllables.map { it.startTimeMs })
        assertEquals(LyricAgentAlignment.Flipped, lyricify.lines.last().agent?.alignment)
        assertEquals("和声", lyricify.lines.first().accompaniment.single().text)
        val lines = LyricFileParser.parse("[type:LyricifyLines]\n[offset:200]\n[1000,2500]Hello")
        assertEquals(800L, lines.lines.single().timeMs)
        assertEquals(1500L, lines.lines.single().durationMs)
        assertTrue(LyricFileParser.parse("[00:00.00]纯音乐，请欣赏").lines.isEmpty())
        assertEquals("QRC", LyricFileParser.parse("[1000,500]QRC(1000,500)").lines.single().text)
        val plain = "[1000,500]<0,500,0>KRC".toByteArray()
        val compressed = java.io.ByteArrayOutputStream().also { out -> DeflaterOutputStream(out).use { it.write(plain) } }.toByteArray()
        val key = byteArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206.toByte(), 210.toByte(), 110, 105)
        val krc = "krc1".toByteArray() + compressed.mapIndexed { index, byte -> (byte.toInt() xor key[index % key.size].toInt()).toByte() }.toByteArray()
        assertEquals("KRC", LyricFileParser.parse(krc).lines.single().text)
    }

    @Test fun untimedTextRetainsLinesWithoutCreatingPlaybackEvents() {
        val doc = LyricFileParser.parse("[ar:Artist]\nFirst line\n\nSecond line\n纯音乐，请欣赏")
        assertTrue(doc.isUnsynchronized)
        assertEquals(listOf("First line", "Second line"), doc.lines.map { it.text })
        assertNull(doc.highlightedIndex(50_000L))
        assertEquals(doc, LyricTimelineProcessor.process(doc))
        assertEquals(doc, doc.withPseudoTiming())
        assertTrue(LyricTimelineProcessor.activeTimedLineIndexes(doc, 0).isEmpty())
        assertNull(LyricTimelineProcessor.nextEventTimeMs(doc, 0))
    }

    @Test fun localProviderAndLoaderReadBestSidecarAndNoticeChangesWithoutRescan() = runBlocking {
        val prefs = getPreferences("melox_local_music")
        val previous = listOf("tracks", "roots").associateWith { prefs.getString(it, null) }
        val dir = Files.createTempDirectory("melox-sidecar").toFile()
        try {
            prefs.putValues(mapOf("tracks" to "[]", "roots" to "[]"))
            val audio = File(dir, "音乐 + #.wav").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val repo = LocalMusicRepository()
            repo.addScanRoot(LocalScanRoot(dir.path, 0))
            val record = LocalMediaScanner(repo).scanAll().single { it.contentUri == audio.toURI().toString() }
            val track = MusicTrack(MusicResourceId(MusicSource.Local, record.fileKey), record.title, emptyList())
            val cached = LyricsDocument(listOf(LyricLine(1000, text = "cached")))
            repo.updateLyrics(record.fileKey, cached)
            val provider = LocalProvider(repository = repo)
            val line = File(dir, "音乐 + #.LRC").apply { writeText("[00:01.00]line lyric") }
            val words = File(dir, "音乐 + #.lys").apply { writeText("[0]first(1000,300)word(1300,400)") }
            assertEquals("firstword", provider.lyrics(track).lines.single().text)
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) = provider.lyrics(track)
                override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) = LyricsDocument(emptyList())
                override suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?) = LyricsDocument(emptyList())
                override suspend fun matchedNetease(track: MusicTrack, registry: MusicProviderRegistry?) = LyricsDocument(emptyList())
            })
            assertEquals("firstword", loader.load(track).lines.single().text)
            words.writeText("[0]changed(1000,500)")
            assertEquals("changed", loader.load(track).lines.single().text)
            words.delete()
            assertEquals("line lyric", loader.load(track).lines.single().text)
            line.delete()
            assertEquals("cached", loader.load(track).lines.single().text)
            assertEquals(cached, LocalMusicRepository().track(record.fileKey)?.cachedLyrics)
        } finally {
            prefs.putValues(previous)
            dir.deleteRecursively()
        }
    }
}
