package melox.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.settings.MeloXSettingsRuntime

class LyricsLoadTest {
    @Test
    fun neteaseProviderLyricsAreRequestedOnce() = kotlinx.coroutines.runBlocking {
        var providerCalls = 0
        var matchedCalls = 0
        val loaded = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument =
                LyricsDocument(emptyList(), LyricSource.AmlL)

            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                providerCalls += 1
                return LyricsDocument(emptyList(), LyricSource.Netease)
            }

            override suspend fun matchedQq(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?): LyricsDocument =
                LyricsDocument(emptyList())

            override suspend fun matchedNetease(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?): LyricsDocument {
                matchedCalls += 1
                return doc("cross", LyricSource.Netease)
            }
        }).load(neteaseTrack())
        assertEquals(1, providerCalls)
        assertEquals(0, matchedCalls)
        assertTrue(loaded.lines.isEmpty())
    }

    @Test
    fun lineTimedAmllYieldsToWordTimedQqAndWordTimedAmllWins() = kotlinx.coroutines.runBlocking {
        val lineAmll = doc("amll-line", LyricSource.AmlL)
        val wordQq = doc("qq-word", LyricSource.QQMusic, syllables = true)
        val wordAmll = doc("amll-word", LyricSource.AmlL, syllables = true)
        val yielded = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference) = lineAmll
            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference) = wordQq
            override suspend fun matchedNetease(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?) =
                doc("netease", LyricSource.Netease, syllables = true)
        }).load(qqTrack())
        assertEquals("qq-word", yielded.lines.single().text)
        assertEquals(LyricSource.QQMusic, yielded.source)

        val preferred = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference) = wordAmll
            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference) = wordQq
        }).load(qqTrack())
        assertEquals("amll-word", preferred.lines.single().text)
        assertEquals(LyricSource.AmlL, preferred.source)
    }

    @Test
    fun qqFallsThroughToMatchedNetease() = kotlinx.coroutines.runBlocking {
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val loaded = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                calls += "amll"
                return LyricsDocument(emptyList(), LyricSource.AmlL)
            }

            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                calls += "provider"
                return LyricsDocument(emptyList(), LyricSource.QQMusic)
            }

            override suspend fun matchedNetease(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?): LyricsDocument {
                calls += "matched-netease"
                return doc("matched", LyricSource.Netease)
            }
        }).load(qqTrack())
        assertEquals(setOf("amll", "provider", "matched-netease"), calls.toSet())
        assertEquals("matched", loaded.lines.single().text)
    }

    @Test
    fun qqAmllUsesNativeIdAndMissStillReachesNetease() = kotlinx.coroutines.runBlocking {
        val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
        val matched = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun findMatchedNeteaseTrack(
                track: MusicTrack,
                registry: melox.music.provider.MusicProviderRegistry?,
            ): MusicTrack? {
                calls += "search-netease-id"
                return neteaseTrack().copy(id = MusicResourceId(MusicSource.Netease, "42"))
            }

            override suspend fun fetchAmll(query: AmlldbLyricQuery, preference: MeloXLyricPreference): LyricsDocument {
                calls += "amll:${query.bindingValue()}"
                return doc("ttml", LyricSource.AmlL, syllables = true)
            }

            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                calls += "provider"
                return LyricsDocument(emptyList(), LyricSource.QQMusic)
            }

            override suspend fun matchedNetease(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?): LyricsDocument {
                calls += "matched-netease"
                return doc("cross", LyricSource.Netease)
            }
        }).load(qqTrack())
        assertEquals(setOf("amll:qq:qq-1", "provider", "matched-netease"), calls.toSet())
        assertEquals("ttml", matched.lines.single().text)

        calls.clear()
        val missed = MeloXLyricsLoad(object : LyricDocumentSources() {
            override suspend fun findMatchedNeteaseTrack(
                track: MusicTrack,
                registry: melox.music.provider.MusicProviderRegistry?,
            ): MusicTrack? {
                calls += "search-netease-id"
                return null
            }

            override suspend fun fetchAmll(query: AmlldbLyricQuery, preference: MeloXLyricPreference): LyricsDocument {
                calls += "amll:${query.bindingValue()}"
                return LyricsDocument(emptyList())
            }

            override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                calls += "provider"
                return LyricsDocument(emptyList(), LyricSource.QQMusic)
            }

            override suspend fun matchedNetease(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?): LyricsDocument {
                calls += "matched-netease"
                return doc("cross", LyricSource.Netease)
            }
        }).load(qqTrack())
        assertEquals(setOf("amll:qq:qq-1", "provider", "matched-netease"), calls.toSet())
        assertEquals("cross", missed.lines.single().text)
    }

    @Test
    fun lineBindingYieldsToWordTimedQqAndWordBindingSkipsAmll() = kotlinx.coroutines.runBlocking {
        val track = qqTrack()
        LyricBindingStore.write(
            track.id,
            LyricBinding(BoundLyricSource.AmlL, resourceValue = "9", title = "s", artist = "a", durationMs = 1L),
        )
        val previousAuto = MeloXSettingsRuntime.automaticLyricSelectionEnabled
        val previousBind = MeloXSettingsRuntime.lyricStrongBindingEnabled
        MeloXSettingsRuntime.automaticLyricSelectionEnabled = true
        MeloXSettingsRuntime.lyricStrongBindingEnabled = true
        try {
            val lineCalls = java.util.concurrent.CopyOnWriteArrayList<String>()
            val yielded = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun binding(binding: LyricBinding, preference: MeloXLyricPreference): LyricsDocument {
                    lineCalls += "binding"
                    return doc("bound-line")
                }

                override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    lineCalls += "amll"
                    return doc("amll-line", LyricSource.AmlL)
                }

                override suspend fun provider(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    lineCalls += "provider"
                    return doc("qq-word", LyricSource.QQMusic, syllables = true)
                }
            }).load(track)
            assertEquals("qq-word", yielded.lines.single().text)
            assertTrue(lineCalls.contains("amll"))

            val wordCalls = mutableListOf<String>()
            val kept = MeloXLyricsLoad(object : LyricDocumentSources() {
                override suspend fun binding(binding: LyricBinding, preference: MeloXLyricPreference): LyricsDocument {
                    wordCalls += "binding"
                    return doc("bound-word", syllables = true)
                }

                override suspend fun amll(track: MusicTrack, registry: melox.music.provider.MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
                    wordCalls += "amll"
                    return doc("amll-word", LyricSource.AmlL, syllables = true)
                }
            }).load(track)
            assertEquals(listOf("binding"), wordCalls)
            assertEquals("bound-word", kept.lines.single().text)
        } finally {
            MeloXSettingsRuntime.automaticLyricSelectionEnabled = previousAuto
            MeloXSettingsRuntime.lyricStrongBindingEnabled = previousBind
            LyricBindingStore.clear()
        }
    }

    @Test
    fun switchesHideTranslationSyllablesScrollAndTap() {
        val line = LyricLine(
            timeMs = 15_000L,
            text = "整行",
            syllables = listOf(LyricSyllable("整", 15_000L, 15_400L), LyricSyllable("行", 15_400L, 15_800L)),
            translation = "line",
        )
        assertFalse(lyricLineShowsSyllables(line, wordByWord = false))
        assertTrue(lyricLineShowsSyllables(line, wordByWord = true))
        assertNull(lyricLineTranslation(line, showTranslation = false))
        assertEquals("line", lyricLineTranslation(line, showTranslation = true))
        assertFalse(lyricShouldAutoScroll(autoFollow = false, activeIndex = 2))
        assertTrue(lyricShouldAutoScroll(autoFollow = true, activeIndex = 2))
        assertFalse(lyricShouldAutoScroll(autoFollow = true, activeIndex = -1))
        assertNull(lyricTapTargetMs(tapSeek = false, line))
        assertEquals(15_000L, lyricTapTargetMs(tapSeek = true, line))
        val document = LyricsDocument(listOf(LyricLine(0L, text = "a"), line))
        assertEquals(1, document.highlightedIndex(15_000L))
        assertEquals(0, document.highlightedIndex(0L))
    }

    private fun neteaseTrack() = MusicTrack(
        id = MusicResourceId(MusicSource.Netease, "9"),
        title = "s",
        artists = listOf(MusicArtistRef(name = "a")),
    )

    private fun qqTrack() = MusicTrack(
        id = MusicResourceId(MusicSource.QQMusic, "qq-1"),
        title = "s",
        artists = listOf(MusicArtistRef(name = "a")),
    )

    private fun doc(text: String, source: LyricSource = LyricSource.Local, syllables: Boolean = false) = LyricsDocument(
        lines = listOf(
            LyricLine(
                timeMs = 0L,
                text = text,
                syllables = if (syllables) listOf(LyricSyllable(text, 0L, 400L)) else emptyList(),
            ),
        ),
        source = source,
    )
}
