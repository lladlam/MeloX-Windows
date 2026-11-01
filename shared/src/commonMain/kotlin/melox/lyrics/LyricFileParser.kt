package melox.lyrics

import java.util.Base64

/** Detect synchronized lyric formats used by local sidecars. */
object LyricFileParser {
    fun parse(bytes: ByteArray): LyricsDocument {
        if (bytes.size >= 4 && bytes[0] == 'k'.code.toByte() && bytes[1] == 'r'.code.toByte() &&
            bytes[2] == 'c'.code.toByte() && bytes[3] == '1'.code.toByte()) {
            return clean(KugouKrcLyricsParser.decodeAndParse(Base64.getEncoder().encodeToString(bytes)))
        }
        return parse(bytes.toString(Charsets.UTF_8))
    }

    fun parse(text: String): LyricsDocument {
        val value = text.trim().removePrefix("\uFEFF")
        val document = when {
            value.contains(Regex("<(?:\\w+:)?tt(?:\\s|>)")) -> TtmlLyricsParser.parse(value)
            LyricifyLyricsParser.isSyllable(value) -> LyricifyLyricsParser.parseSyllable(value)
            LyricifyLyricsParser.isLines(value) -> LyricifyLyricsParser.parseLines(value)
            value.contains(Regex("<\\d+,\\d+,\\d+>")) -> KugouKrcLyricsParser.parse(value)
            value.contains(Regex("\\(\\d+,\\d+\\)")) -> QQMusicQrcLyricsParser.parse(value)
            else -> LrcLyricsParser.parse(value)
        }
        return clean(document)
    }

    private fun clean(document: LyricsDocument): LyricsDocument = document.copy(
        source = LyricSource.Local,
        lines = document.lines.filterNot { line ->
            line.text.filter { it.isLetterOrDigit() }.lowercase() in placeholders
        },
    )

    private val placeholders = setOf(
        "此歌曲为没有填词的纯音乐请您欣赏", "此歌曲为没有填词的纯音乐请欣赏", "此歌曲為沒有填詞的純音樂請您欣賞",
        "此歌曲為沒有填詞的純音樂請欣賞", "此歌曲为dj舞曲请您欣赏", "此歌曲为没有填词的dj舞曲请您欣赏",
        "纯音乐请欣赏", "纯音乐请您欣赏", "纯音乐", "純音樂請欣賞", "純音樂請您欣賞", "純音樂",
        "暂无歌词", "暫無歌詞", "此歌曲暂无歌词", "此歌曲暫無歌詞",
    )
}
