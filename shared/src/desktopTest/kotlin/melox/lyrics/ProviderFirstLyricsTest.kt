package melox.lyrics

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*
import melox.music.model.*
import melox.music.provider.MusicProviderRegistry
import melox.settings.MeloXSettingsRuntime

@OptIn(ExperimentalCoroutinesApi::class)
class ProviderFirstLyricsTest {
    private val track = MusicTrack(MusicResourceId(MusicSource.Netease, "test-fast-lyrics"), "song", emptyList())
    private fun document(text: String, source: LyricSource, word: Boolean = false) = LyricsDocument(
        listOf(LyricLine(1_000L, text = text, syllables = if (word) listOf(LyricSyllable(text, 1_000, 2_000)) else emptyList())),
        source,
    )

    @Test fun slowCrossProviderDoesNotBlockFastProviderAndLazyUpgradeArrives() = runTest {
        val old = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        MeloXSettingsRuntime.automaticLyricSelectionEnabled = true
        try {
            var providerCalls = 0
            var amllCalls = 0
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    providerCalls++
                    delay(100)
                    return document("provider", LyricSource.Netease)
                }
                override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    amllCalls++
                    delay(5_000)
                    return document("authored", LyricSource.AmlL, true)
                }
                override suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?): LyricsDocument {
                    delay(30_000)
                    return document("cross", LyricSource.QQMusic)
                }
            }, backgroundScope)
            val received = mutableListOf<LyricsDocument>()
            val collection = backgroundScope.async { loader.observe(track).toList(received) }
            runCurrent()
            advanceTimeBy(2_101)
            runCurrent()
            assertEquals(listOf("provider"), received.map { it.lines.single().text })
            assertEquals(1, providerCalls)
            advanceTimeBy(5_001)
            runCurrent()
            assertEquals(listOf("provider", "authored"), received.map { it.lines.single().text })
            assertTrue(collection.isCompleted)
            assertEquals(2, amllCalls)
        } finally { MeloXSettingsRuntime.automaticLyricSelectionEnabled = old }
    }

    @Test fun cancelledScreenNeverReceivesUpgradeForPreviousTrack() = runTest {
        val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) = document("provider", LyricSource.Netease)
            override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                delay(10_000)
                return document("old-upgrade", LyricSource.AmlL, true)
            }
            override suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?) = LyricsDocument(emptyList())
        }, backgroundScope)
        val received = mutableListOf<LyricsDocument>()
        val collection = backgroundScope.async { loader.observe(track).toList(received) }
        runCurrent()
        advanceTimeBy(2_001)
        runCurrent()
        collection.cancel()
        advanceTimeBy(20_000)
        runCurrent()
        assertEquals(listOf("provider"), received.map { it.lines.single().text })
    }

    @Test fun explicitProviderSelectionDoesNotMakeCrossProviderRequests() = runTest {
        val previous = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        MeloXSettingsRuntime.automaticLyricSelectionEnabled = false
        try {
            val loader = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference) = document("chosen", LyricSource.Netease)
                override suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument = error("must not fetch")
                override suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?): LyricsDocument = error("must not search")
            }, backgroundScope)
            val result = loader.observe(track).toList()
            assertEquals(listOf("chosen"), result.map { it.lines.single().text })
        } finally { MeloXSettingsRuntime.automaticLyricSelectionEnabled = previous }
    }
}
