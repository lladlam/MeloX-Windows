package melox.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.provider.bilibili.BilibiliPlaybackAssociation
import melox.provider.bilibili.BilibiliPlaybackAssociationStore
import melox.settings.MeloXSettingsRuntime
import kotlin.test.*

class BilibiliMatchedLyricsTest {
    private val original = MusicTrack(MusicResourceId(MusicSource.Bilibili, "BVoriginal:11"), "Song", emptyList(), durationMs = 60_000)
    private fun document(source: LyricSource, end: Long = 100_000, precise: Boolean = true) = LyricsDocument(
        listOf(LyricLine(1_000, text = "first"), LyricLine(end - 1_000, text = "last",
            syllables = if (precise) listOf(LyricSyllable("last", end - 1_000, end)) else emptyList())), source,
    )
    private open inner class Sources : BilibiliLyricSources() {
        var neteaseLyrics = 0
        var replacementSearches = 0
        val saved = mutableListOf<BilibiliPlaybackAssociation>()
        var amllDocument = document(LyricSource.AmlL)
        var qqDocument = document(LyricSource.QQMusic)
        var neteaseDocument = document(LyricSource.Netease)
        override suspend fun search(source: MusicSource, query: String) = listOf(
            MusicTrack(MusicResourceId(source, "42"), "Song", listOf(MusicArtistRef(name = "Artist"))),
        )
        override suspend fun lyrics(track: MusicTrack): LyricsDocument = if (track.id.source == MusicSource.Netease) {
            neteaseLyrics++; neteaseDocument
        } else qqDocument
        override suspend fun amll(track: MusicTrack) = amllDocument
        override suspend fun replacements(query: String): List<MusicTrack> {
            replacementSearches++
            return listOf(original.copy(id = MusicResourceId(MusicSource.Bilibili, "BVreplacement:22"), durationMs = 100_000))
        }
        override fun save(association: BilibiliPlaybackAssociation) { saved += association }
    }

    @Test fun matchingPrimaryNeverFetchesNeteaseLyricsOrSearchesReplacement() = runTest {
        val sources = Sources()
        assertEquals(LyricSource.AmlL, BilibiliMatchedLyrics(sources).load(original.copy(durationMs = 100_000), true).source)
        assertEquals(0, sources.neteaseLyrics)
        assertEquals(0, sources.replacementSearches)
        assertTrue(sources.saved.isEmpty())
    }

    @Test fun threePreciseSourcesSavePhysicalAssociationWithoutChangingLogicalTrack() = runTest {
        val sources = Sources()
        BilibiliMatchedLyrics(sources).load(original, true)
        assertEquals(1, sources.neteaseLyrics)
        assertEquals(1, sources.replacementSearches)
        val saved = sources.saved.single()
        assertEquals("BVoriginal", saved.originalBvid)
        assertEquals(11L, saved.originalCid)
        assertEquals("BVreplacement", saved.replacementBvid)
        assertEquals(22L, saved.replacementCid)
        assertEquals(100_000L, saved.consensusDurationMs)
        assertEquals("BVoriginal:11", original.id.value)
    }

    @Test fun disabledAlignmentStillLoadsMatchedLyricsButNeverWritesOrSearchesReplacement() = runTest {
        val sources = Sources()
        assertTrue(BilibiliMatchedLyrics(sources).load(original, false).lines.isNotEmpty())
        assertEquals(1, sources.neteaseLyrics)
        assertEquals(0, sources.replacementSearches)
        assertTrue(sources.saved.isEmpty())
    }

    @Test fun missingLowConfidenceOrConflictingThirdSourceCannotCreateAssociation() = runTest {
        for (third in listOf(LyricsDocument(emptyList()), document(LyricSource.Netease, precise = false), document(LyricSource.Netease, 130_000))) {
            val sources = Sources().apply { neteaseDocument = third }
            BilibiliMatchedLyrics(sources).load(original, true)
            assertEquals(0, sources.replacementSearches)
            assertTrue(sources.saved.isEmpty())
        }
    }

    @Test fun unknownDurationDoesNotSearchAndCancellationIsNotAnEmptySuccess() = runTest {
        val sources = object : Sources() {
            override suspend fun search(source: MusicSource, query: String): List<MusicTrack> = throw CancellationException("cancelled")
        }
        assertTrue(BilibiliMatchedLyrics(sources).load(original.copy(durationMs = null), true).lines.isEmpty())
        assertFailsWith<CancellationException> { BilibiliMatchedLyrics(sources).load(original, true) }
        assertTrue(sources.saved.isEmpty())
    }

    @Test fun unsafeCatalogVersionCannotReachLyricsOrPersistAssociation() = runTest {
        val sources = object : Sources() {
            override suspend fun search(source: MusicSource, query: String) = listOf(
                MusicTrack(MusicResourceId(source, "42"), "Song remix", emptyList()),
            )
            override suspend fun lyrics(track: MusicTrack): LyricsDocument = error("unsafe catalog match")
            override suspend fun amll(track: MusicTrack): LyricsDocument = error("unsafe catalog match")
        }
        assertTrue(BilibiliMatchedLyrics(sources).load(original, true).lines.isEmpty())
        assertTrue(sources.saved.isEmpty())
    }

    @Test fun loaderKeysAlignmentAndBypassesGenericLateUpgradeForBilibili() = runTest {
        val oldAuto = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val oldAlignment = MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled
        try {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = true
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = false
            val calls = mutableListOf<Boolean>()
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun bilibili(track: MusicTrack, registry: MusicProviderRegistry?, alignment: Boolean, preference: MeloXLyricPreference): LyricsDocument {
                    calls += alignment
                    return document(LyricSource.QQMusic)
                }
                override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument = error("generic upgrade bypasses verification")
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) = LyricsDocument(emptyList())
            }, backgroundScope)
            loader.observe(original).toList()
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = true
            loader.observe(original).toList()
            assertEquals(listOf(false, true), calls)
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = false
            assertTrue(loader.load(original).lines.isEmpty())
            assertEquals(listOf(false, true), calls)
        } finally {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = oldAuto
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = oldAlignment
        }
    }

    @Test fun productionSaveHonorsDisableAndInterveningAssociationRemoval() {
        val oldAuto = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val oldAlignment = MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled
        val entry = BilibiliPlaybackAssociation("BVlate-save-test", 99, "BVreplacement", 22, "Song", 100_000, 100_000)
        try {
            BilibiliPlaybackAssociationStore.remove(entry.originalBvid, entry.originalCid)
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = true
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = false
            val source = BilibiliLyricSources()
            source.save(entry)
            assertNull(BilibiliPlaybackAssociationStore.read(entry.originalBvid, entry.originalCid))
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = true
            source.save(entry)
            assertEquals(entry, BilibiliPlaybackAssociationStore.read(entry.originalBvid, entry.originalCid))
            val delayed = BilibiliLyricSources()
            BilibiliPlaybackAssociationStore.remove(entry.originalBvid, entry.originalCid)
            delayed.save(entry)
            assertNull(BilibiliPlaybackAssociationStore.read(entry.originalBvid, entry.originalCid))
        } finally {
            BilibiliPlaybackAssociationStore.remove(entry.originalBvid, entry.originalCid)
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = oldAuto
            MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled = oldAlignment
        }
    }
}
