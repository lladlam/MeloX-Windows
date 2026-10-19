package melox.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.*
import kotlin.test.*
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.settings.MeloXSettingsRuntime

@OptIn(ExperimentalCoroutinesApi::class)
class LyricLanguageLoadingTest {
    private val track = MusicTrack(MusicResourceId(MusicSource.Netease, "language-routing-test"), "song", emptyList())
    private fun preference(language: String) = MeloXLyricPreference.fromSetting(language)

    @Test fun lateOldLanguageAndRegionalCacheCannotOverwriteNewLanguage() = runTest {
        val previousAuto = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val previousLanguage = MeloXSettingsRuntime.lyricLanguage
        MeloXSettingsRuntime.automaticLyricSelectionEnabled = false
        try {
            val calls = mutableListOf<String>()
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    calls += preference.cacheKey()
                    delay(if (preference.languageTag == "en") 5_000 else 100)
                    return TtmlLyricsParser.parse(LyricLanguageTest.InlineTtml, preference)
                }
            }, backgroundScope)
            MeloXSettingsRuntime.lyricLanguage = "en"
            val old = async { loader.load(track) }
            runCurrent()
            MeloXSettingsRuntime.lyricLanguage = "tr"
            val current = async { loader.load(track) }
            advanceTimeBy(101)
            runCurrent()
            assertEquals("Merhaba", current.await().lines.single().translation)
            advanceTimeBy(5_000)
            runCurrent()
            assertEquals("Hello", old.await().lines.single().translation)
            assertEquals("Merhaba", loader.load(track).lines.single().translation)
            assertEquals("Hello", loader.load(track, preference = preference("en-GB")).lines.single().translation)
            assertEquals(listOf("en:Original", "tr:Original"), calls)
        } finally {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = previousAuto
            MeloXSettingsRuntime.lyricLanguage = previousLanguage
        }
    }

    @Test fun cancelledOldLanguageUpgradeKeepsSnapshotAndCannotEmitIntoNewCollection() = runTest {
        val previousAuto = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val previousLanguage = MeloXSettingsRuntime.lyricLanguage
        MeloXSettingsRuntime.automaticLyricSelectionEnabled = true
        MeloXSettingsRuntime.lyricLanguage = "en"
        try {
            val snapshots = mutableListOf<String>()
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) =
                    LyricsDocument(listOf(LyricLine(1000, text = "Original", translation = "中文")), LyricSource.Netease)
                override suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?) = LyricsDocument(emptyList())
                override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    snapshots += preference.cacheKey()
                    delay(5_000)
                    return TtmlLyricsParser.parse(LyricLanguageTest.InlineTtml, preference)
                }
            }, backgroundScope)
            val oldResults = mutableListOf<LyricsDocument>()
            val old = backgroundScope.async { loader.observe(track).toList(oldResults) }
            advanceTimeBy(2_001)
            runCurrent()
            assertEquals(1, oldResults.size)
            assertNull(oldResults.single().lines.single().translation)
            old.cancel()
            MeloXSettingsRuntime.lyricLanguage = "tr"
            val newResults = mutableListOf<LyricsDocument>()
            val current = backgroundScope.async { loader.observe(track).toList(newResults) }
            advanceTimeBy(10_000)
            runCurrent()
            assertTrue(current.isCompleted)
            assertEquals(1, oldResults.size)
            assertEquals(listOf(null, "Merhaba"), newResults.map { it.lines.single().translation })
            assertEquals(listOf("en:Original", "en:Original", "tr:Original", "tr:Original"), snapshots)
        } finally {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = previousAuto
            MeloXSettingsRuntime.lyricLanguage = previousLanguage
        }
    }
}
