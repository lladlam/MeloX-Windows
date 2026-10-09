package melox.lyrics

import com.ibm.icu.text.Transliterator
import java.util.Locale

enum class MeloXLyricScript { Original, Simplified, Traditional;
    companion object {
        fun fromSystem(locale: Locale = Locale.getDefault()): MeloXLyricScript {
            if (!locale.language.equals("zh", ignoreCase = true)) return Original
            return if (locale.script.equals("Hant", ignoreCase = true) ||
                locale.country.uppercase(Locale.ROOT) in setOf("TW", "HK", "MO")
            ) Traditional else Simplified
        }
    }
}

/** ICU4J is the JVM equivalent of Android ICU. Transliterator is not thread safe. */
object MeloXLyricScriptConverter {
    private val toSimplified by lazy { Transliterator.getInstance("Traditional-Simplified") }
    private val toTraditional by lazy { Transliterator.getInstance("Simplified-Traditional") }

    fun convertText(value: String, script: MeloXLyricScript): String {
        if (script == MeloXLyricScript.Original) return value
        val converter = if (script == MeloXLyricScript.Simplified) toSimplified else toTraditional
        return synchronized(converter) { converter.transliterate(value) }
    }

    fun convert(document: LyricsDocument, script: MeloXLyricScript): LyricsDocument {
        if (script == MeloXLyricScript.Original) return document
        val converter = if (script == MeloXLyricScript.Simplified) toSimplified else toTraditional
        return synchronized(converter) {
            document.copy(lines = document.lines.map { line ->
                line.copy(
                    text = converter.transliterate(line.text),
                    translation = line.translation
                        ?.takeIf { document.source != LyricSource.AmlL || line.adaptTranslation }
                        ?.let(converter::transliterate) ?: line.translation,
                    adaptTranslation = false,
                    syllables = line.syllables.map { it.copy(text = converter.transliterate(it.text)) },
                    accompaniment = line.accompaniment.map { vocal ->
                        vocal.copy(
                            text = converter.transliterate(vocal.text),
                            translation = vocal.translation?.let(converter::transliterate),
                            syllables = vocal.syllables.map { it.copy(text = converter.transliterate(it.text)) },
                        )
                    },
                )
            })
        }
    }
}
