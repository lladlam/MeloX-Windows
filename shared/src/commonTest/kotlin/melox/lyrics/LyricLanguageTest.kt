package melox.lyrics

import java.util.Locale
import kotlin.test.*

/** Translation-routing contract from the immutable Android v0.6.5 tree. */
class LyricLanguageTest {
    @Test fun explicitLanguageAndChineseScriptOverrideLocale() {
        val system = Locale.forLanguageTag("zh-Hans-CN")
        assertEquals(MeloXLyricPreference("tr", MeloXLyricScript.Original), MeloXLyricPreference.fromSetting("tr", system))
        assertEquals(MeloXLyricScript.Traditional, MeloXLyricPreference.fromSetting("zh-Hant", system).script)
        assertEquals(MeloXLyricScript.Simplified, MeloXLyricPreference.fromSetting("zh-Hans", Locale.TAIWAN).script)
        assertEquals(MeloXLyricPreference.system(Locale.ENGLISH), MeloXLyricPreference.fromSetting("unknown", Locale.ENGLISH))
        assertEquals(MeloXLyricPreference.system(Locale.ENGLISH), MeloXLyricPreference.fromSetting("", Locale.ENGLISH))
        assertEquals(MeloXLyricPreference("tr", MeloXLyricScript.Original), MeloXLyricPreference.fromSetting("system", Locale.forLanguageTag("tr-TR")))
        assertEquals(MeloXLyricScript.Traditional, MeloXLyricPreference.system(Locale.forLanguageTag("zh-Hant-TW")).script)
        assertEquals(MeloXLyricPreference(null, MeloXLyricScript.Original), MeloXLyricPreference.system(Locale.ROOT))
    }

    @Test fun regionalVariantsShareCacheButLanguagesAndChineseScriptsDoNot() {
        fun key(value: String) = MeloXLyricPreference.fromSetting(value, Locale.ENGLISH).cacheKey()
        assertEquals(key("en"), key(" en-US "))
        assertNotEquals(key("en"), key("tr"))
        assertNotEquals(key("zh-Hans"), key("zh-Hant"))
    }

    @Test fun inlineTranslationUsesTargetLanguageWithoutChangingWordsOrTimings() {
        val english = TtmlLyricsParser.parse(InlineTtml, MeloXLyricPreference.fromSetting("en-US"))
        val turkish = TtmlLyricsParser.parse(InlineTtml, MeloXLyricPreference.fromSetting("tr"))
        val missing = TtmlLyricsParser.parse(InlineTtml, MeloXLyricPreference.fromSetting("de"))
        assertEquals("Hello", english.lines.single().translation)
        assertEquals("Merhaba", turkish.lines.single().translation)
        assertNull(missing.lines.single().translation)
        assertEquals("Original", missing.lines.single().text)
        assertEquals(english.lines.single().syllables, turkish.lines.single().syllables)
        assertEquals(english.lines.single().syllables, missing.lines.single().syllables)
        assertEquals("roman", missing.lines.single().romanization)
    }

    @Test fun metadataAndBackgroundUseTargetLanguageAndDoNotBorrowAnotherTrack() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">
              <head><metadata>
                <itunes:translation><itunes:text for="L1" xml:lang="zh-Hans">你好（和声）</itunes:text></itunes:translation>
                <itunes:translation><itunes:text for="L1" xml:lang="en-GB">Hello（Backing）</itunes:text></itunes:translation>
                <itunes:translation><itunes:text for="L1" xml:lang="tr">Merhaba（Arka）</itunes:text></itunes:translation>
              </metadata></head>
              <body><div><p begin="00:00:01.000" end="00:00:03.000" itunes:key="L1"><span begin="00:00:01.000" end="00:00:02.000">Original</span><span ttm:role="x-bg"><span begin="00:00:02.000" end="00:00:03.000">Background</span></span></p></div></body>
            </tt>
        """.trimIndent()
        fun line(language: String) = TtmlLyricsParser.parse(ttml, MeloXLyricPreference.fromSetting(language)).lines.single()
        assertEquals("Hello", line("en").translation)
        assertEquals("Backing", line("en").accompaniment.single().translation)
        assertEquals("Merhaba", line("tr").translation)
        assertEquals("Arka", line("tr").accompaniment.single().translation)
        assertNull(line("ja").translation)
        assertNull(line("ja").accompaniment.single().translation)
        assertEquals("Background", line("ja").accompaniment.single().text)
    }

    @Test fun chineseOnlyProviderNeverMasqueradesAsRequestedForeignTranslation() {
        for (source in listOf(LyricSource.Netease, LyricSource.QQMusic, LyricSource.Kugou)) {
            val original = LyricsDocument(listOf(LyricLine(0, text = "Original", translation = "中文", romanization = "roman",
                accompaniment = listOf(LyricAccompaniment(0, 1000, "Backing", translation = "和声")))), source)
            val result = applyLyricTranslationPreference(original, MeloXLyricPreference.fromSetting("en"))
            assertNull(result.lines.single().translation)
            assertNull(result.lines.single().accompaniment.single().translation)
            assertEquals("Original", result.lines.single().text)
            assertEquals("roman", result.lines.single().romanization)
            assertEquals(original, applyLyricTranslationPreference(original, MeloXLyricPreference.fromSetting("zh-Hans")))
        }
        val selected = TtmlLyricsParser.parse(InlineTtml, MeloXLyricPreference.fromSetting("tr"))
        assertEquals(selected, applyLyricTranslationPreference(selected, MeloXLyricPreference.fromSetting("tr")))
    }

    @Test fun chinesePreferenceDoesNotFallBackToForeignTranslation() {
        val foreignOnly = InlineTtml.replace("<span ttm:role=\"x-translation\" xml:lang=\"zh-Hans\">你好</span>", "")
        assertNull(TtmlLyricsParser.parse(foreignOnly, MeloXLyricPreference.fromSetting("zh-Hans")).lines.single().translation)
        assertEquals("你好", TtmlLyricsParser.parse(InlineTtml, MeloXLyricPreference.fromSetting("zh-Hans")).lines.single().translation)
    }

    companion object {
        val InlineTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body><div><p begin="00:00:01.000" end="00:00:02.000"><span begin="00:00:01.000" end="00:00:02.000">Original</span><span ttm:role="x-translation" xml:lang="zh-Hans">你好</span><span ttm:role="x-translation" xml:lang="en-US">Hello</span><span ttm:role="x-translation" xml:lang="tr">Merhaba</span><span ttm:role="x-roman">roman</span></p></div></body>
            </tt>
        """.trimIndent()
    }
}
