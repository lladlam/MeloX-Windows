package melox.lyrics

import melox.settings.MeloXLyricsGroupingMode.Character
import melox.settings.MeloXLyricsGroupingMode.Word
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricPresentationTest {
    @Test fun repeatedWordsKeepTheirOwnProviderTimesAndDoNotConsumeTheGap() {
        val line = LyricLine(0, text = "ha ha", syllables = listOf(
            LyricSyllable("ha", 1000, 1400), LyricSyllable("ha", 2200, 2600)))
        val timing = MeloXLyricPresentation.glyphTimings(line, Character, Character, 950)
        assertEquals(1000f, timing[0]?.start)
        assertNull(timing[2])
        assertEquals(2200f, timing[3]?.start)
        val first = MeloXLyricPresentation.glyphVisual(timing[0]!!, 2000, 1f, false, 1f)
        val second = MeloXLyricPresentation.glyphVisual(timing[3]!!, 2000, 1f, false, 1f)
        assertEquals(1f, first.reveal)
        assertEquals(0f, second.reveal)
        assertEquals(0f, second.liftPx)
    }

    @Test fun LatinLongToneCombinesSyllablesWhileLiftStillRespectsTheChosenGrouping() {
        val line = LyricLine(1000, text = "sing", syllables = listOf(
            LyricSyllable("s", 1000, 1300), LyricSyllable("i", 1300, 1600), LyricSyllable("ng", 1600, 2200)))
        val characters = MeloXLyricPresentation.glyphTimings(line, Character, Character, 950).filterNotNull()
        val words = MeloXLyricPresentation.glyphTimings(line, Word, Character, 950).filterNotNull()
        assertTrue(characters.all { it.isLongTone && it.longToneDuration == 1200f })
        assertEquals(1600f, characters[2].liftStart)
        assertTrue(words.all { it.liftStart == 1000f })
        assertEquals(listOf(0, 1, 2, 3), characters.map { it.longToneCharacterIndex })
    }

    @Test fun ReducedMotionKeepsWordProgressButRemovesLiftExpansionGlowAndShake() {
        val line = LyricLine(0, text = "啊", syllables = listOf(LyricSyllable("啊", 1000, 3000)))
        val timing = MeloXLyricPresentation.glyphTimings(line, Word, Word, 950).single()!!
        val reduced = MeloXLyricPresentation.glyphVisual(timing, 2000, 3.25f, true, 1.5f)
        assertTrue(reduced.reveal > .8f && reduced.reveal < 1f)
        assertEquals(0f, reduced.liftPx)
        assertEquals(1f, reduced.scale)
        assertEquals(0f, reduced.glow)
        assertEquals(0f, reduced.shakeXPx, .00001f)
        assertEquals(0f, reduced.shakeYPx, .00001f)
    }

    @Test fun InstrumentalGapsStartAfterTheLastAuthoredWordRatherThanTheNominalLineDuration() {
        val lines = listOf(
            LyricLine(500, 1000, "voice", listOf(LyricSyllable("voice", 500, 6000))),
            LyricLine(7000, 1000, "next"), LyricLine(12500, 1000, "last"))
        val interludes = MeloXLyricPresentation.interludes(lines)
        assertEquals(1, interludes.size)
        assertEquals(8000L, interludes.single().startTimeMs)
        assertEquals(12250L, interludes.single().countdownEndTimeMs)
        assertEquals(2, interludes.single().followingLyricIndex)
    }

    @Test fun UntimedPunctuationAndZeroDurationProviderWordsDoNotCreateInvalidGlyphEffects() {
        val line = LyricLine(0, text = "啊！", syllables = listOf(LyricSyllable("啊", 1000, 1000)))
        val timing = MeloXLyricPresentation.glyphTimings(line, Character, Character, 950)
        assertNull(timing[1])
        assertFalse(timing[0]!!.isLongTone)
        val fx = MeloXLyricPresentation.glyphVisual(timing[0]!!, 1000, 1f, false, 1f)
        assertTrue(listOf(fx.reveal, fx.liftPx, fx.scale, fx.glow, fx.shakeXPx, fx.shakeYPx).all(Float::isFinite))
        assertEquals(1f, fx.reveal)
    }
}
