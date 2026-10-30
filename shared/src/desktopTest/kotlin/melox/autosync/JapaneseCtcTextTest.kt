package melox.autosync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JapaneseCtcTextTest {
    private val manifest = AutoSyncModelManifest("ja", "ctc", "audio", null, emptyList(), 0, 0, 1, .02,
        listOf("<pad>", "<unk>", "pau", "k", "o", "n"), "test")
    private val mapping = JapaneseG2pMapper.Result(listOf(
        JapaneseG2pMapper.CharReading("こ", "こ", listOf("k", "o"), false),
        JapaneseG2pMapper.CharReading("ん", "ん", listOf("n"), false),
        JapaneseG2pMapper.CharReading("！", "", emptyList(), true),
    ), listOf("pau", "k", "o", "n", "pau"))

    @Test fun mapsActualPhonemeSpansToOriginalCharacters() {
        val targets = requireNotNull(JapaneseCtcText.targets(mapping, manifest))
        assertEquals(listOf(2, 3, 4, 5, 2), targets.ids)
        val spans = targets.ids.mapIndexed { index, id -> CtcForcedAligner.Span(id, index * 3, index * 3 + 1, 0.0) }
        assertEquals(listOf(AutoSyncUnit("こ", 60, 160), AutoSyncUnit("ん", 180, 220)),
            JapaneseCtcText.units(mapping, targets, spans, .02))
        assertTrue(JapaneseCtcText.units(mapping, targets, spans.dropLast(1), .02).isEmpty())
    }

    @Test fun refusesUnknownPronunciationOrMismatchedPhonemeStream() {
        val unknown = mapping.copy(readings = mapping.readings.toMutableList().also {
            it[0] = it[0].copy(uncertain = true)
        })
        assertNull(JapaneseCtcText.targets(unknown, manifest))
        assertNull(JapaneseCtcText.targets(mapping.copy(phonemes = listOf("k", "n")), manifest))
        assertNull(JapaneseCtcText.targets(mapping, manifest.copy(tokens = listOf("<pad>", "<unk>", "pau", "k", "n"))))
    }
}
