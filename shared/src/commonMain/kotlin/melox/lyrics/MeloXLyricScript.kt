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

/** Android v0.6.5 translation-track selection and Chinese script preference. */
data class MeloXLyricPreference(val languageTag: String?, val script: MeloXLyricScript) {
    fun cacheKey(): String = "${languageTag ?: "zh"}:${script.name}"

    companion object {
        fun fromSetting(value: String, locale: Locale = Locale.getDefault()): MeloXLyricPreference =
            when (val tag = value.trim()) {
                "", "system" -> system(locale)
                "zh-Hans", "zh-CN", "zh-SG" -> MeloXLyricPreference(null, MeloXLyricScript.Simplified)
                "zh-Hant", "zh-TW", "zh-HK", "zh-MO" -> MeloXLyricPreference(null, MeloXLyricScript.Traditional)
                else -> {
                    val language = tag.lowercase(Locale.ROOT).substringBefore('-')
                    if (language != "zh" && language.length in 2..3 && language.all { it in 'a'..'z' }) {
                        MeloXLyricPreference(language, MeloXLyricScript.Original)
                    } else system(locale)
                }
            }

        fun system(locale: Locale = Locale.getDefault()): MeloXLyricPreference {
            val language = locale.language.lowercase(Locale.ROOT)
            return when {
                language == "zh" -> MeloXLyricPreference(null, MeloXLyricScript.fromSystem(locale))
                language.isBlank() || language == "und" -> MeloXLyricPreference(null, MeloXLyricScript.Original)
                else -> MeloXLyricPreference(language, MeloXLyricScript.Original)
            }
        }
    }
}

fun requestedLyricPreference(): MeloXLyricPreference =
    MeloXLyricPreference.fromSetting(melox.settings.MeloXSettingsRuntime.lyricLanguage)

fun applyLyricTranslationPreference(document: LyricsDocument, preference: MeloXLyricPreference): LyricsDocument {
    val chineseOnly = document.source in setOf(LyricSource.Netease, LyricSource.QQMusic, LyricSource.Kugou)
    val selected = if (preference.languageTag != null && chineseOnly) document.copy(lines = document.lines.map { line ->
        line.copy(translation = null, accompaniment = line.accompaniment.map { it.copy(translation = null) })
    }) else document
    return MeloXLyricScriptConverter.convert(selected, preference.script)
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
