package melox.lyrics

/**
 * Provider-neutral entry point for ordinary LRC lyrics. The mature timing and
 * annotation implementation remains shared with the existing NetEase parser.
 *
 * Ordinary provider LRC is a line-timed fallback, not genuine word timing. Keep
 * synthetic grapheme timing disabled so the player does not promote it into the
 * expensive word-by-word renderer. Providers with real QRC/KRC timing populate
 * [LyricLine.syllables] directly and are unaffected.
 */
object LrcLyricsParser {
    fun parse(
        lrc: String,
        translation: String = "",
        romanization: String = "",
    ): LyricsDocument {
        val timed = NeteaseLyricParser.parse(yrc = "", lrc = lrc, translatedLrc = translation,
            romanizedLrc = romanization).copy(pseudoTimingAllowed = false)
        if (timed.lines.isNotEmpty()) return timed.withoutPlaceholderLyrics()
        val lines = lrc.lineSequence().map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith('[') && !it.startsWith('<') }
            .map { LyricLine(0L, text = it, timingKind = LyricTimingKind.LineSynchronized) }.toList()
        return LyricsDocument(lines, pseudoTimingAllowed = false, isUnsynchronized = true).withoutPlaceholderLyrics()
    }
}
