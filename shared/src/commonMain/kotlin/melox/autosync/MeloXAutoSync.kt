package melox.autosync

import java.io.File
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import melox.lyrics.LyricTimingKind
import melox.lyrics.LyricsDocument
import melox.music.model.MusicTrack
import melox.platform.logWarn
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsRuntime

/** Aligns the current recording locally. Unsupported or failed lines remain line-timed. */
object MeloXAutoSync {
    private val generation = Mutex()
    private val timelines = AutoSyncTimelineStore()

    /** Call from the player coroutine on Main; the work itself is dispatched off the UI thread. */
    suspend fun generate(track: MusicTrack, document: LyricsDocument): LyricsDocument? {
        if (!eligible(document) || !MeloXSettingsRuntime.autoSyncWordLyricsEnabled) return null
        val controller = PlaybackCommands.playerController() ?: return null
        val queue = PlaybackCommands.queue.value
        val entryId = queue.entryIds.getOrNull(queue.index) ?: return null
        fun current(): Boolean {
            val selected = PlaybackCommands.queue.value
            return MeloXSettingsRuntime.autoSyncWordLyricsEnabled && PlaybackCommands.playerController() === controller &&
                selected.entryIds.getOrNull(selected.index) == entryId && AudioPlayer.currentTrack.value?.id == track.id
        }
        if (!current()) return null
        val source = controller.playbackSourceForOutput()
        if (!current()) return null
        val trackKey = "${track.id.source.name}:${track.id.value}"
        val audioKey = audioIdentity(source.uri, source.headers)
        return withContext(Dispatchers.IO) {
            generation.withLock {
                suspend fun ensureCurrent() {
                    currentCoroutineContext().ensureActive()
                    if (!current()) throw CancellationException("AutoSync track changed")
                }
                ensureCurrent()
                val plan = document.lines.mapIndexedNotNull { index, line ->
                    if (line.timingKind != LyricTimingKind.LineSynchronized || !AutoSyncText.alignable(line.text)) null
                    else when (val language = AutoSyncText.language(line.text)) {
                        AutoSyncLanguage.English, AutoSyncLanguage.Chinese -> index to language
                        else -> null // Japanese requires the OpenJTalk dictionary and native frontend.
                    }
                }
                if (plan.isEmpty()) return@withLock null
                val languages = plan.map { it.second.tag }.distinct().sorted()
                val models = modelKey(languages)
                if (languages.none { MeloXAutoSyncModels.repository.installed(it) != null }) return@withLock null
                val sourceHash = AutoSyncTimelineStore.sourceHash(document)
                timelines.load(trackKey, sourceHash, audioKey, models)?.let { cached ->
                    ensureCurrent()
                    AutoSyncTimelineStore.merge(document, cached)?.let { return@withLock it }
                }
                val first = document.lines.first().timeMs.coerceAtLeast(0)
                val last = document.lines.last().timeMs.coerceAtLeast(first)
                val start = (first - 5_000).coerceAtLeast(0)
                val end = (last + 15_000).let { desired ->
                    track.durationMs?.takeIf { it > 0 }?.let { desired.coerceAtMost(it) } ?: desired
                }
                if (end <= start || end - start > 30 * 60_000L) return@withLock null
                val result = linkedMapOf<Int, List<AutoSyncUnit>>()
                AutoSyncAudioDecoder.withPcm(source.uri, source.headers, start, end) { pcm ->
                    val totalMs = start + pcm.sampleCount * 1000 / AutoSyncPcmFile.SAMPLE_RATE
                    for ((language, entries) in plan.groupBy({ it.second }, { it.first })) {
                        ensureCurrent()
                        try {
                            MeloXAutoSyncModels.repository.withInstalled(language.tag) { installed ->
                                OnnxAlignmentEngine.open(installed).use { engine ->
                                    for (index in entries) {
                                        ensureCurrent()
                                        val line = document.lines[index]
                                        val window = window(document, index, totalMs) ?: continue
                                        val samples = pcm.window(window.first, window.second)
                                        if (samples.size < AutoSyncPcmFile.SAMPLE_RATE / 10) continue
                                        val units = try { alignLine(engine, language, line.text, samples) }
                                        catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { emptyList() }
                                        val sanitized = sanitize(units, samples.size * 1000L / AutoSyncPcmFile.SAMPLE_RATE)
                                        if (sanitized.isNotEmpty()) result[index] = sanitized.map {
                                            it.copy(startMs = it.startMs + window.first, endMs = it.endMs + window.first)
                                        }
                                    }
                                }
                            }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { logWarn("MeloXAutoSync", "${language.tag} model unavailable: ${failure.javaClass.simpleName}") }
                    }
                }
                ensureCurrent()
                if (result.isEmpty() || models != modelKey(languages)) return@withLock null
                timelines.save(trackKey, sourceHash, audioKey, models, result)
                ensureCurrent()
                AutoSyncTimelineStore.merge(document, result)
            }
        }
    }

    // Several real providers leave quality at Fallback even for timestamped LRC;
    // plain, unsynchronized lyrics instead have no LineSynchronized markers.
    internal fun eligible(document: LyricsDocument): Boolean = document.lines.isNotEmpty() &&
        document.lines.size <= 5_000 && document.lines.any { it.timingKind == LyricTimingKind.LineSynchronized } &&
        document.lines.none { it.syllables.isNotEmpty() } &&
        document.lines.zipWithNext().all { (a, b) -> a.timeMs <= b.timeMs }

    private fun modelKey(languages: List<String>): String = AutoSyncTimelineStore.hash("autosync-v1|" + languages.joinToString("|") { language ->
        val model = MeloXAutoSyncModels.repository.installed(language)
        val identity = model?.let { runCatching { AutoSyncModelManifest.read(it).identity }.getOrNull() } ?: "missing"
        "$language:$identity"
    })

    private fun audioIdentity(uri: String, headers: Map<String, String>): String {
        val local = runCatching { if (uri.startsWith("file:")) File(URI(uri)) else File(uri) }.getOrNull()
        val revision = local?.takeIf { it.isFile }?.let { "${it.length()}:${it.lastModified()}" }.orEmpty()
        return AutoSyncTimelineStore.hash("$uri|$revision|${headers.toSortedMap()}")
    }

    private fun alignLine(engine: OnnxAlignmentEngine, language: AutoSyncLanguage, text: String, pcm: FloatArray): List<AutoSyncUnit> {
        val manifest = engine.manifest
        return when (language) {
            AutoSyncLanguage.English -> {
                if (!AutoSyncText.supportsEnglishSurface(text)) return emptyList()
                val normalized = AutoSyncText.english(text)
                val ids = AutoSyncText.englishIds(normalized, manifest)
                if (ids.isEmpty()) return emptyList()
                val alignment = engine.ctc(pcm, ids)
                if (!alignment.feasible) emptyList() else AutoSyncText.englishWords(text, normalized, ids, alignment.spans, manifest)
            }
            AutoSyncLanguage.Chinese -> {
                val normalized = AutoSyncText.chinese(text)
                val vocabulary = manifest.tokens.toHashSet()
                val count = normalized.count { it.toString() in vocabulary }
                if (count == 0 || count != normalized.length) return emptyList()
                AutoSyncText.chineseCharacters(text, normalized, manifest, engine.chinese(pcm, count))
            }
            else -> emptyList()
        }
    }

    private fun window(document: LyricsDocument, index: Int, totalMs: Long): Pair<Long, Long>? {
        val line = document.lines[index]
        val start = (line.timeMs - 300).coerceAtLeast(0)
        val next = document.lines.getOrNull(index + 1)?.timeMs?.takeIf { it > line.timeMs } ?: totalMs
        var end = (minOf(next, totalMs) + 300).coerceAtMost(totalMs)
        if (end - line.timeMs > 12_000) end = minOf(totalMs, line.timeMs + 12_000)
        if (end <= start) end = minOf(totalMs, start + 12_000)
        return (start to end).takeIf { end - start >= 250 }
    }

    private fun sanitize(units: List<AutoSyncUnit>, windowMs: Long): List<AutoSyncUnit> = buildList {
        var previous = 0L
        units.forEach { unit ->
            val a = unit.startMs.coerceIn(0, windowMs)
            val b = unit.endMs.coerceIn(0, windowMs)
            val start = minOf(a, b).coerceAtLeast(previous)
            val end = maxOf(a, b).let { if (it <= start) minOf(windowMs, start + 10) else it }
            if (end > start && unit.text.isNotBlank()) { add(AutoSyncUnit(unit.text, start, end)); previous = end }
        }
    }
}
