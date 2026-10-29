package melox.autosync

import java.text.Normalizer

/**
 * Kotlin port of MeloX-AutoSync `src/autosync/lyrics/ja_pronunciation.py`
 * (`JapanesePronunciationMapper`), verified against the golden vectors in
 * `src/test/resources/autosync/ja_g2p_vectors.json` produced by the reference
 * chain.
 *
 * Contract, matching the reference exactly:
 *  - characters are never dropped, so [Result.reconstruct] always equals the input;
 *  - a kanji may cover several morae (never "one kanji = one syllable");
 *  - a character whose phonemes cannot be attributed is marked `uncertain`
 *    instead of receiving an invented reading.
 */
internal class JapaneseG2pMapper(private val frontend: JaOpenJtalk) {

    private companion object { const val MAX_KANJI_MORAE = 12 }

    data class CharReading(
        val char: String,
        val kana: String,
        val phonemes: List<String>,
        val uncertain: Boolean,
    )

    data class Result(
        val readings: List<CharReading>,
        /** Full sequence including the boundary `pau` used for CTC alignment. */
        val phonemes: List<String>,
    ) {
        /** Losslessly rebuild the original text. */
        fun reconstruct(): String = readings.joinToString("") { it.char }
    }

    fun isAvailable(): Boolean = frontend.isOpen()

    fun map(text: String): Result? {
        // The reference dynamic-programming table is quadratic in text length.
        // Long or malformed lines remain line-timed instead of exhausting memory.
        if (!frontend.isOpen() || text.length > 256 || '\u0000' in text) return null
        val chars = text.toList()
        // Overrides are not configurable in the app yet; the reference applies
        // them before NFKC and they only affect the reading, never the output text.
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)

        runCatching { mapTokenBased(normalized, chars) }
            .getOrNull()
            ?.let { return it }

        // Fallback: whole-string G2P + DP alignment.
        val kana = runCatching { toKana(text) }.getOrNull()
            ?: return Result(
                chars.map { CharReading(it.toString(), "", emptyList(), uncertain = true) },
                emptyList(),
            )

        val readings = chars.map { CharReading(it.toString(), "", emptyList(), uncertain = false) }.toMutableList()
        val spans = alignKanaToChars(chars, kana)
        if (spans == null) {
            return Result(readings.map { it.copy(uncertain = true) }, convert(kana))
        }
        val full = convert(kana)
        val inner = stripPau(full)
        val counts = attributeCounts(kana, inner.size)
        readings.indices.forEach { index ->
            val span = spans[index] ?: run {
                readings[index] = readings[index].copy(uncertain = true)
                return@forEach
            }
            val start = span.first
            val end = span.last + 1
            val phonemes = inner.subList(counts[start], counts[end])
            readings[index] = readings[index].copy(
                kana = kana.substring(start, end),
                phonemes = phonemes,
                uncertain = phonemes.isEmpty(),
            )
        }
        return Result(readings, full)
    }

    // ------------------------------------------------------------------ G2P

    fun toKana(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val tokens = tokenize(normalized)
        if (tokens != null) return tokens.joinToString("") { it.second }
        return katakanaToHiragana(frontend.kana(normalized).orEmpty())
    }

    private fun mapTokenBased(normalized: String, chars: List<Char>): Result? {
        val tokens = tokenize(normalized) ?: return null
        if (tokens.joinToString("") { it.first } != normalized) return null
        if (normalized.length != chars.size) return null

        val charSpans = arrayOfNulls<IntRange>(normalized.length)
        var position = 0
        var kanaPosition = 0
        for ((surface, kana) in tokens) {
            val local = alignKanaToChars(surface.toList(), kana) ?: return null
            local.forEachIndexed { index, span ->
                if (span != null) {
                    val start = kanaPosition + span.first
                    charSpans[position + index] = start until (kanaPosition + span.last + 1)
                }
            }
            position += surface.length
            kanaPosition += kana.length
        }

        val kanaAll = tokens.joinToString("") { it.second }
        val full = convert(kanaAll)
        val inner = stripPau(full)
        val counts = attributeCounts(kanaAll, inner.size)

        val readings = chars.map { CharReading(it.toString(), "", emptyList(), uncertain = false) }.toMutableList()
        readings.indices.forEach { index ->
            val span = charSpans[index] ?: run {
                readings[index] = readings[index].copy(uncertain = true)
                return@forEach
            }
            val start = span.first
            val end = span.last + 1
            val phonemes = inner.subList(counts[start], counts[end])
            readings[index] = readings[index].copy(
                kana = kanaAll.substring(start, end),
                phonemes = phonemes,
                uncertain = phonemes.isEmpty(),
            )
        }
        return Result(readings, full)
    }

    /**
     * `(surface, hiragana reading)` tokens, or `null` when the frontend cannot
     * split the text. Non-kana pronunciation markers are dropped, exactly like
     * the reference tokenizer.
     */
    private fun tokenize(text: String): List<Pair<String, String>>? {
        val raw = frontend.tokens(text) ?: return null
        val tokens = raw.mapNotNull { (surface, pronunciation) ->
            if (surface.isEmpty()) return@mapNotNull null
            val clean = pronunciation.filter { isKana(it) || it == 'ー' }
            surface to katakanaToHiragana(clean)
        }
        return tokens.takeIf { it.isNotEmpty() }
    }

    /** `["pau", *phonemes, "pau"]`, matching `pyopenjtalk_phonemes`. */
    private fun convert(kana: String): List<String> {
        if (kana.isEmpty()) return listOf("pau", "pau")
        val phonemes = frontend.phonemes(kana) ?: return listOf("pau", "pau")
        return buildList(phonemes.size + 2) {
            add("pau")
            addAll(phonemes)
            add("pau")
        }
    }

    private fun stripPau(phonemes: List<String>): List<String> {
        var start = 0
        var end = phonemes.size
        if (end > start && phonemes[start] == "pau") start++
        if (end > start && phonemes[end - 1] == "pau") end--
        return phonemes.subList(start, end)
    }

    /** Cumulative phoneme count per kana prefix, repaired to be monotonic. */
    private fun attributeCounts(kana: String, innerLength: Int): IntArray {
        val n = kana.length
        val counts = IntArray(n + 1)
        for (index in 0..n) {
            counts[index] = stripPau(convert(kana.substring(0, index))).size
        }
        counts[n] = innerLength
        for (index in n - 1 downTo 0) counts[index] = minOf(counts[index], counts[index + 1])
        for (index in 1..n) counts[index] = maxOf(counts[index], counts[index - 1])
        counts[n] = innerLength
        return counts
    }

    /**
     * Per-character `[start, end)` kana spans. Kana consume one, kanji between
     * one and [MAX_KANJI_MORAE], and latin/digits/punctuation consume none.
     */
    private fun alignKanaToChars(chars: List<Char>, kana: String): List<IntRange?>? {
        val n = chars.size
        val m = kana.length
        val inf = Int.MAX_VALUE / 4
        val dp = Array(n + 1) { IntArray(m + 1) { inf } }
        val parentI = Array(n + 1) { IntArray(m + 1) { -1 } }
        val parentJ = Array(n + 1) { IntArray(m + 1) { -1 } }
        dp[0][0] = 0
        for (i in 0 until n) {
            val character = chars[i]
            val hiragana = katakanaToHiragana(Normalizer.normalize(character.toString(), Normalizer.Form.NFKC))
            for (j in 0..m) {
                val base = dp[i][j]
                if (base >= inf) continue
                when {
                    isKana(character) -> if (j < m) {
                        val cost = if (kana[j].toString() == hiragana) 0 else 1
                        if (dp[i + 1][j + 1] > base + cost) {
                            dp[i + 1][j + 1] = base + cost
                            parentI[i + 1][j + 1] = i
                            parentJ[i + 1][j + 1] = j
                        }
                    }
                    isKanji(character) -> {
                        val limit = minOf(MAX_KANJI_MORAE, m - j)
                        for (k in 1..limit) {
                            if (dp[i + 1][j + k] > base) {
                                dp[i + 1][j + k] = base
                                parentI[i + 1][j + k] = i
                                parentJ[i + 1][j + k] = j
                            }
                        }
                    }
                    else -> if (dp[i + 1][j] > base) {
                        dp[i + 1][j] = base
                        parentI[i + 1][j] = i
                        parentJ[i + 1][j] = j
                    }
                }
            }
        }
        if (dp[n][m] >= inf) return null
        val spans = arrayOfNulls<IntRange>(n)
        var i = n
        var j = m
        while (i > 0) {
            val previousI = parentI[i][j]
            val previousJ = parentJ[i][j]
            if (previousI < 0) return null
            if (previousI in 0 until n) spans[previousI] = previousJ until j
            i = previousI
            j = previousJ
        }
        return spans.toList()
    }

    fun katakanaToHiragana(text: String): String = buildString(text.length) {
        for (character in text) {
            val code = character.code
            append(if (code in 0x30A1..0x30F6) (code - 0x60).toChar() else character)
        }
    }

    private fun isKanji(character: Char): Boolean {
        val code = character.code
        return code in 0x4E00..0x9FFF || code in 0x3400..0x4DBF || code in 0xF900..0xFAFF
    }

    private fun isKana(character: Char): Boolean {
        val code = character.code
        return code in 0x3041..0x309F || code in 0x30A0..0x30FF
    }
}
