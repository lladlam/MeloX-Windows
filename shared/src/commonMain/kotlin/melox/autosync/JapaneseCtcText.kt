package melox.autosync

import kotlin.math.roundToLong

/** Maps OpenJTalk phonemes back to their original characters without guessing readings. */
internal object JapaneseCtcText {
    data class Targets(val ids: List<Int>, val indices: List<Int>)

    fun targets(mapping: JapaneseG2pMapper.Result, manifest: AutoSyncModelManifest): Targets? {
        val inner = mapping.readings.flatMap { it.phonemes }
        if (inner.isEmpty() || innerOffset(mapping.phonemes, inner) == null) return null
        // Do not assign artificial timing to a displayed letter or digit for
        // which the frontend could not attribute a pronunciation.
        if (mapping.readings.any { it.char.any(Char::isLetterOrDigit) && (it.uncertain || it.phonemes.isEmpty()) }) return null
        val vocabulary = manifest.tokens.withIndex().associate { it.value to it.index }
        val ids = mutableListOf<Int>()
        val indices = mutableListOf<Int>()
        mapping.phonemes.forEachIndexed { index, phoneme ->
            val id = vocabulary[phoneme]
            if (id != null && id != manifest.blankId && id != manifest.unknownId) {
                ids += id
                indices += index
            } else if (phoneme != "pau") return null
        }
        return Targets(ids, indices).takeIf { it.ids.isNotEmpty() }
    }

    fun units(mapping: JapaneseG2pMapper.Result, targets: Targets, spans: List<CtcForcedAligner.Span?>,
              frameSeconds: Double): List<AutoSyncUnit> {
        if (spans.size != targets.ids.size || spans.any { it == null } || !frameSeconds.isFinite() || frameSeconds <= 0) return emptyList()
        val inner = mapping.readings.flatMap { it.phonemes }
        var cursor = innerOffset(mapping.phonemes, inner) ?: return emptyList()
        val byPhoneme = targets.indices.zip(spans.filterNotNull()).toMap()
        return buildList {
            mapping.readings.forEach { reading ->
                val count = reading.phonemes.size
                val timed = (cursor until cursor + count).mapNotNull(byPhoneme::get)
                cursor += count
                if (count == 0) return@forEach
                if (timed.size != count) return emptyList()
                val start = (timed.first().startFrame * frameSeconds * 1000).roundToLong()
                val end = ((timed.last().endFrame + 1) * frameSeconds * 1000).roundToLong()
                if (end <= start) return emptyList()
                add(AutoSyncUnit(reading.char, start, end))
            }
        }
    }

    private fun innerOffset(full: List<String>, inner: List<String>): Int? =
        if (inner.size > full.size) null else (0..full.size - inner.size).firstOrNull { full.subList(it, it + inner.size) == inner }
}
