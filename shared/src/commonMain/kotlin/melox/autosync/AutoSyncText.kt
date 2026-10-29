package melox.autosync

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToLong

internal enum class AutoSyncLanguage(val tag: String) { English("en"), Chinese("zh"), Japanese("ja"), Unknown("und") }
internal data class AutoSyncUnit(val text: String, val startMs: Long, val endMs: Long)

/** Text routing and display spans follow Android's AutoSync text contract. */
internal object AutoSyncText {
    private val unsupportedEnglish = Regex("[^A-Za-z0-9' ]+")
    private val whitespace = Regex("\\s+")
    private val word = Regex("[A-Za-z0-9'\u2018\u2019]+")
    private val unsupportedChinese = Regex("[^\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff\\u3041-\\u309f\\u30a0-\\u30ff\\uff10-\\uff19\\d]+")
    private val special = setOf("<pad>", "<s>", "</s>", "<unk>", "|", " ")
    private val credits = listOf("作词", "作詞", "作曲", "编曲", "編曲", "制作人", "監製", "监制", "混音", "母带", "母帶",
        "录音", "錄音", "吉他", "贝斯", "貝斯", "鼓 ", "弦乐", "和声", "和聲", "出品", "发行", "發行",
        "produced by", "written by", "composed by", "arranged by", "lyrics by", "music by", "mixed by", "mastered by", "recording by", "op:", "sp:", "op：", "sp：")
    private val instrumental = listOf("间奏", "間奏", "前奏", "尾奏", "纯音乐", "純音樂", "instrumental", "[music]", "（music）")

    fun language(text: String): AutoSyncLanguage {
        var latin = 0; var han = 0; var kana = 0
        for (character in text) when {
            character in 'A'..'Z' || character in 'a'..'z' -> latin++
            character.code in 0x3040..0x30ff -> kana++
            character.code in 0x4e00..0x9fff -> han++
        }
        return when {
            kana > 0 -> AutoSyncLanguage.Japanese
            latin > 0 && han > 0 -> AutoSyncLanguage.Unknown
            latin > 0 -> AutoSyncLanguage.English
            han > 0 -> AutoSyncLanguage.Chinese
            else -> AutoSyncLanguage.Unknown
        }
    }

    fun alignable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 2 || trimmed.none(Char::isLetterOrDigit)) return false
        val lower = trimmed.lowercase(Locale.ROOT)
        return credits.none(lower::contains) && instrumental.none(lower::contains)
    }

    fun english(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('\u2019', '\'').replace('\u2018', '\'')
        return whitespace.replace(unsupportedEnglish.replace(normalized, " "), " ").trim().uppercase(Locale.ROOT)
    }

    fun supportsEnglishSurface(text: String): Boolean = text.none {
        it.isLetterOrDigit() && it !in 'A'..'Z' && it !in 'a'..'z' && it !in '0'..'9'
    }

    fun chinese(text: String): String = unsupportedChinese.replace(Normalizer.normalize(text, Normalizer.Form.NFKC), "")

    fun englishIds(normalized: String, manifest: AutoSyncModelManifest): List<Int> {
        val vocabulary = manifest.tokens.withIndex().associate { it.value to it.index }
        val ids = normalized.map { if (it == ' ') manifest.delimiterId else vocabulary[it.toString()] ?: manifest.unknownId }
        // The published English vocabulary has no digits. An UNK label cannot
        // honestly time an audible syllable, even when the rest of a word fits.
        return ids.takeIf { it.none { id -> id == manifest.unknownId || id == manifest.blankId } }.orEmpty()
    }

    fun englishWords(original: String, normalized: String, ids: List<Int>, spans: List<CtcForcedAligner.Span?>,
                     manifest: AutoSyncModelManifest): List<AutoSyncUnit> {
        if (!supportsEnglishSurface(original)) return emptyList()
        val groups = mutableListOf<MutableList<CtcForcedAligner.Span>>()
        var current = mutableListOf<CtcForcedAligner.Span>()
        ids.forEachIndexed { index, id ->
            val token = manifest.tokens.getOrNull(id) ?: return@forEachIndexed
            if (token == "|" || token == " ") {
                if (current.isNotEmpty()) { groups += current; current = mutableListOf() }
            } else if (token !in special) spans.getOrNull(index)?.let(current::add)
        }
        if (current.isNotEmpty()) groups += current
        if (groups.isEmpty()) return emptyList()
        val originalWords = word.findAll(original).map { it.value }.toList()
        // Display text must map back to exact original offsets; otherwise the
        // glyph renderer could assign a real timestamp to the wrong character.
        if (originalWords.size != groups.size || originalWords.joinToString(" ") { english(it) } != normalized) return emptyList()
        val display = originalWords
        return groups.mapIndexed { index, group ->
            AutoSyncUnit(display.getOrElse(index) { return emptyList() },
                (group.first().startFrame * manifest.frameHopSeconds * 1000).roundToLong(),
                ((group.last().endFrame + 1) * manifest.frameHopSeconds * 1000).roundToLong())
        }
    }

    /** Refuse a line if a model token cannot be mapped back to its actual displayed character. */
    fun chineseCharacters(original: String, normalized: String, manifest: AutoSyncModelManifest,
                          spans: List<ZhCifPostprocess.TokenSpan>): List<AutoSyncUnit> {
        if (normalized.isEmpty() || spans.isEmpty()) return emptyList()
        val vocabulary = manifest.tokens.toHashSet()
        val kept = normalized.indices.filter { normalized[it].toString() in vocabulary }
        if (kept.size != normalized.length || kept.size != spans.size) return emptyList()
        val originalPositions = IntArray(normalized.length) { -1 }
        var cursor = 0
        original.forEachIndexed { index, char ->
            if (cursor < normalized.length && char == normalized[cursor]) originalPositions[cursor++] = index
        }
        if (cursor != normalized.length) return emptyList()
        return kept.mapIndexed { index, position ->
            AutoSyncUnit(original[originalPositions[position]].toString(), spans[index].startMs, spans[index].endMs)
        }
    }
}
