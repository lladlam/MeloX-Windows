package melox.lyrics

import kotlin.math.max
import kotlin.math.min

data class LyricRomanizedUnit(val start: Int, val end: Int, val reading: String)
data class LyricRomanizedSegment(val line: LyricLine, val units: List<LyricRomanizedUnit>)

/** Android visual-row slicing, independent of the platform text measurer. Ranges are end-exclusive UTF-16 offsets. */
fun splitRomanizedSegments(line: LyricLine, rows: List<Pair<Int, Int>>): List<LyricRomanizedSegment> {
    val text = line.text
    val syllableRanges = mutableListOf<Triple<LyricSyllable, Int, Int>>()
    var syllableSearch = 0
    for (syllable in line.syllables) {
        if (syllable.text.isEmpty()) continue
        val found = text.indexOf(syllable.text, syllableSearch)
        if (found < 0) continue
        syllableSearch = found + syllable.text.length
        syllableRanges += Triple(syllable, found, syllableSearch)
    }
    val unitRanges = mutableListOf<Triple<String, Int, Int>>()
    var unitSearch = 0
    for (unit in LyricRomanizationAligner.units(line)) {
        if (unit.originalText.isEmpty()) continue
        val found = text.indexOf(unit.originalText, unitSearch)
        if (found < 0) continue
        unitSearch = found + unit.originalText.length
        unit.romanizationText?.trim()?.takeIf(String::isNotEmpty)?.let { unitRanges += Triple(it, found, unitSearch) }
    }
    if (unitRanges.isEmpty()) line.romanization?.trim()?.takeIf(String::isNotEmpty)?.let { unitRanges += Triple(it, 0, text.length) }
    val segments = rows.mapNotNull { (rowStart, rowEnd) ->
        val start = rowStart.coerceIn(0, text.length)
        val end = rowEnd.coerceIn(start, text.length)
        val segmentText = text.substring(start, end).trimEnd()
        if (segmentText.isEmpty()) return@mapNotNull null
        val syllables = syllableRanges.mapNotNull { (syllable, from, to) ->
            val left = max(start, from); val right = min(start + segmentText.length, to)
            if (right <= left) return@mapNotNull null
            val total = (to - from).coerceAtLeast(1)
            val duration = (syllable.endTimeMs - syllable.startTimeMs).coerceAtLeast(0L)
            val localStart = syllable.startTimeMs + duration * (left - from) / total
            val localEnd = syllable.startTimeMs + duration * (right - from) / total
            syllable.copy(text = text.substring(left, right), startTimeMs = localStart, endTimeMs = max(localEnd, localStart + 1L))
        }
        val units = unitRanges.mapNotNull { (reading, from, to) ->
            val left = max(start, from); val right = min(start + segmentText.length, to)
            if (right <= left) return@mapNotNull null
            val total = (to - from).coerceAtLeast(1)
            val fragment = if (left == from && right == to) reading else {
                val a = (left - from) * reading.length / total
                val b = (right - from) * reading.length / total
                reading.substring(a, b).trim()
            }
            fragment.takeIf(String::isNotBlank)?.let { LyricRomanizedUnit(left - start, right - start, it) }
        }
        LyricRomanizedSegment(line.copy(text = segmentText, syllables = syllables, translation = null,
            romanization = null, romanizationSyllables = emptyList(), accompaniment = emptyList()), units)
    }
    return segments.ifEmpty { listOf(LyricRomanizedSegment(line.copy(romanization = null), emptyList())) }
}
