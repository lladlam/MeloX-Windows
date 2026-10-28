package melox.lyrics

import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.LyricsCapability
import melox.music.provider.MusicProviderRegistry
import melox.music.provider.SearchCapability
import melox.music.provider.ThirdPartyMusicSourceConsentStore
import melox.playback.LxUserPlaybackResolver
import melox.provider.lxuser.LxUserSourceStore
import melox.settings.MeloXSettingsRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

/** Provider-first load/AMLL upgrade with Android v0.6.5 translation-language snapshots. */
class MeloXLyricsLoad(
    private val sources: LyricDocumentSources = LyricDocumentSources(),
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private data class Key(
        val track: MusicTrack,
        val automatic: Boolean,
        val binding: LyricBinding?,
        val preference: MeloXLyricPreference,
        val registry: MusicProviderRegistry?,
        val bilibiliAlignment: Boolean,
        val lxIdentity: String,
    )
    private val lock = Any()
    private val cache = object : LinkedHashMap<Key, LyricsDocument>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, LyricsDocument>?): Boolean = size > 24
    }
    private val inFlight = mutableMapOf<Key, Deferred<LyricsDocument>>()

    private fun key(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): Key {
        val automatic = MeloXSettingsRuntime.automaticLyricSelectionEnabled || track.id.source == MusicSource.Local
        return Key(track, automatic,
            if (automatic && MeloXSettingsRuntime.lyricStrongBindingEnabled) LyricBindingStore.read(track.id) else null,
            preference, registry, MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled,
            if (ThirdPartyMusicSourceConsentStore.enabled()) LxUserSourceStore.list().joinToString("|") { it.id } else "")
    }

    /** Collection belongs to the screen's media/language identity; cancellation suppresses stale upgrades. */
    fun observe(track: MusicTrack, registry: MusicProviderRegistry? = null,
        preference: MeloXLyricPreference = requestedLyricPreference()): Flow<LyricsDocument> = flow {
        val key = key(track, registry, preference)
        val initial = load(key)
        emit(initial)
        if (track.id.source == MusicSource.Bilibili || !key.automatic || initial.source == LyricSource.AmlL || initial.lines.isEmpty()) return@flow
        val upgrade = workerScope.async { safe { sources.amll(track, registry, key.preference) } }
        try {
            val authored = withTimeoutOrNull(15_000L) { upgrade.await() } ?: return@flow
            if (authored.source != LyricSource.AmlL || !hasWordTiming(authored) || !isLyricUpgradeCompatible(initial, authored)) return@flow
            val processed = process(authored, key.preference)
            synchronized(lock) { cache[key] = processed }
            emit(processed)
        } finally {
            upgrade.cancel()
        }
    }

    suspend fun load(track: MusicTrack, registry: MusicProviderRegistry? = null,
        preference: MeloXLyricPreference = requestedLyricPreference()): LyricsDocument {
        return load(key(track, registry, preference))
    }

    private suspend fun load(key: Key): LyricsDocument {
        val pending = synchronized(lock) {
            cache[key]?.let { return it }
            inFlight.getOrPut(key) {
                workerScope.async {
                    val document = process(loadDocument(key), key.preference)
                    if (document.lines.isNotEmpty()) synchronized(lock) { cache[key] = document }
                    document
                }
            }
        }
        pending.invokeOnCompletion {
            synchronized(lock) { if (inFlight[key] === pending) inFlight.remove(key) }
        }
        return withTimeoutOrNull(45_000L) { pending.await() } ?: run {
            pending.cancel()
            throw IOException("歌词加载超时")
        }
    }

    private suspend fun loadDocument(key: Key): LyricsDocument {
        val (track, automatic, binding, preference, registry) = key
        if (!automatic) return safe { sources.provider(track, registry, preference) }
        if (track.id.source == MusicSource.Bilibili) {
            return sources.bilibili(track, registry, key.bilibiliAlignment, preference)
        }
        if (binding != null) {
            val bound = safe { sources.binding(binding, preference) }
            if (hasWordTiming(bound)) return bound
        }
        val order = automaticLyricSourcesFor(track.id.source)
        // Existing provider clients use blocking IO. These jobs deliberately run
        // outside the caller's scope so a slow losing source cannot delay first paint.
        val jobs = order.associateWith { source ->
            workerScope.async {
                safe {
                    when (source) {
                        LyricAutoSource.Current -> sources.provider(track, registry, preference)
                        LyricAutoSource.AmlL -> sources.amll(track, registry, preference)
                        LyricAutoSource.QQMusic -> sources.matchedQq(track, registry)
                        LyricAutoSource.Netease -> sources.matchedNetease(track, registry)
                    }
                }
            }
        }
        try {
            val current = withTimeoutOrNull(15_000L) { jobs.getValue(LyricAutoSource.Current).await() }
            if (current != null && current.lines.isNotEmpty()) {
                val amll = withTimeoutOrNull(2_000L) { jobs.getValue(LyricAutoSource.AmlL).await() }
                return amll?.takeIf(::hasWordTiming) ?: current
            }
            val candidates = order.filter { it != LyricAutoSource.Current }.mapIndexed { index, source ->
                val timeout = if (source == LyricAutoSource.QQMusic) 30_000L else 12_000L
                AutoLyricCandidate(index + 1, withTimeoutOrNull(timeout) { jobs.getValue(source).await() }
                    ?: LyricsDocument(emptyList()))
            }
            return selectAutomaticLyricCandidate(candidates)?.document ?: LyricsDocument(emptyList())
        } finally {
            jobs.values.forEach { it.cancel() }
        }
    }

    private fun process(document: LyricsDocument, preference: MeloXLyricPreference): LyricsDocument =
        applyLyricTranslationPreference(LyricTimelineProcessor.process(document), preference)

    private suspend fun safe(block: suspend () -> LyricsDocument): LyricsDocument = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        LyricsDocument(emptyList())
    }

    companion object {
        const val AmllUpgradeWindowMs = 30_000L
        val shared by lazy { MeloXLyricsLoad() }
    }
}

internal fun hasWordTiming(document: LyricsDocument): Boolean = document.lines.any { it.syllables.isNotEmpty() }

internal fun isLyricUpgradeCompatible(current: LyricsDocument, upgraded: LyricsDocument): Boolean {
    if (current.lines.isEmpty()) return true
    val ratio = upgraded.lines.size.toDouble() / current.lines.size
    return ratio in 0.5..2.0
}

internal enum class LyricAutoSource { AmlL, QQMusic, Netease, Current }

internal data class AutoLyricCandidate(val priority: Int, val document: LyricsDocument)

internal fun automaticLyricSourcesFor(source: MusicSource): List<LyricAutoSource> = buildList {
    add(LyricAutoSource.Current)
    add(LyricAutoSource.AmlL)
    if (source == MusicSource.QQMusic) {
        add(LyricAutoSource.Netease)
    } else {
        add(LyricAutoSource.QQMusic)
        if (source != MusicSource.Netease) add(LyricAutoSource.Netease)
    }
}.distinct()

internal fun selectAutomaticLyricCandidate(candidates: List<AutoLyricCandidate>): AutoLyricCandidate? {
    candidates.firstOrNull {
        it.document.source == LyricSource.AmlL &&
            it.document.lines.isNotEmpty() &&
            it.document.lines.any { line -> line.syllables.isNotEmpty() }
    }?.let { return it }
    return candidates
        .asSequence()
        .filterNot { it.document.source == LyricSource.AmlL }
        .filter { it.document.lines.isNotEmpty() }
        .minByOrNull(AutoLyricCandidate::priority)
}

open class LyricDocumentSources {
    open suspend fun bilibili(track: MusicTrack, registry: MusicProviderRegistry?, alignment: Boolean,
        preference: MeloXLyricPreference): LyricsDocument =
        BilibiliMatchedLyrics(BilibiliLyricSources(registry, preference)).load(track, alignment)

    open suspend fun binding(binding: LyricBinding, preference: MeloXLyricPreference): LyricsDocument = when (binding.source) {
        BoundLyricSource.AmlL -> {
            val query = AmlldbLyricQuery.fromBinding(binding.resourceValue) ?: return LyricsDocument(emptyList())
            AmlldbLyricsClient().lyrics(query, preference)
        }
        BoundLyricSource.Provider -> {
            val source = binding.provider ?: return LyricsDocument(emptyList())
            val lyrics = melox.music.provider.MeloXMusicProviders.create().require(source) as? LyricsCapability
                ?: return LyricsDocument(emptyList())
            lyrics.lyrics(
                MusicTrack(
                    id = melox.music.model.MusicResourceId(source, binding.resourceValue),
                    title = binding.title,
                    artists = binding.artist.split(Regex("\\s*(?:、|/|&|,|;|；)\\s*"))
                        .filter(String::isNotBlank)
                        .map { melox.music.model.MusicArtistRef(name = it) },
                    durationMs = binding.durationMs.takeIf { it > 0L },
                ), preference,
            )
        }
    }

    open suspend fun amll(track: MusicTrack, registry: MusicProviderRegistry? = null,
        preference: MeloXLyricPreference = requestedLyricPreference()): LyricsDocument {
        val direct = when (track.id.source) {
            MusicSource.QQMusic -> AmlldbLyricQuery.QQ(track.id.value)
            MusicSource.Spotify -> AmlldbLyricQuery.Spotify(track.id.value)
            MusicSource.AppleMusic -> AmlldbLyricQuery.AppleMusic(track.id.value)
            else -> null
        }
        if (direct != null) return fetchAmll(direct, preference)
        val id = neteaseSongIdForAmll(track, registry) ?: return LyricsDocument(emptyList(), LyricSource.AmlL)
        return fetchAmll(id, preference)
    }

    open suspend fun fetchAmll(query: AmlldbLyricQuery, preference: MeloXLyricPreference): LyricsDocument =
        AmlldbLyricsClient().lyrics(query, preference)

    open suspend fun neteaseSongIdForAmll(track: MusicTrack, registry: MusicProviderRegistry?): Long? {
        if (track.id.source == MusicSource.Netease) return track.id.value.toLongOrNull()
        return findMatchedNeteaseTrack(track, registry)?.id?.value?.toLongOrNull()
    }

    open suspend fun fetchAmll(neteaseSongId: Long, preference: MeloXLyricPreference): LyricsDocument =
        runCatching { AmlldbLyricsClient().lyrics(neteaseSongId, preference) }
            .getOrDefault(LyricsDocument(emptyList(), LyricSource.AmlL))

    open suspend fun findMatchedNeteaseTrack(track: MusicTrack, registry: MusicProviderRegistry?): MusicTrack? {
        val resolved = registry ?: melox.music.provider.MeloXMusicProviders.create()
        val search = resolved[MusicSource.Netease] as? SearchCapability ?: return null
        val artist = track.artistText.substringBefore(" /")
        val queries = listOf(track.title, "${track.title} $artist".trim()).filter { it.isNotBlank() }.distinct()
        for (query in queries) {
            val match = runCatching { search.searchSongs(query, 1, 10).items }.getOrDefault(emptyList())
                .filter { isSafeCrossProviderLyricMatch(track.title, track.artistText, track.durationMs ?: 0L, it) }
                .maxByOrNull { lyricTrackMatchScore(track, it) }
            if (match != null) return match
        }
        return null
    }

    open suspend fun provider(track: MusicTrack, registry: MusicProviderRegistry?, preference: MeloXLyricPreference): LyricsDocument {
        val resolved = registry ?: melox.music.provider.MeloXMusicProviders.create()
        val lyrics = resolved.require(track.id.source) as? LyricsCapability ?: return LyricsDocument(emptyList())
        if (track.id.source != MusicSource.Netease && ThirdPartyMusicSourceConsentStore.enabled()) {
            val userSource = try {
                runInterruptible(Dispatchers.IO) { LxUserPlaybackResolver().resolveLyrics(track) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
            if (userSource != null && userSource.lines.isNotEmpty()) return userSource
        }
        return lyrics.lyrics(track, preference)
    }

    open suspend fun matchedQq(track: MusicTrack, registry: MusicProviderRegistry?): LyricsDocument =
        matchedProvider(MusicSource.QQMusic, track, registry)

    open suspend fun matchedNetease(track: MusicTrack, registry: MusicProviderRegistry?): LyricsDocument =
        matchedProvider(MusicSource.Netease, track, registry)

    private suspend fun matchedProvider(
        source: MusicSource,
        track: MusicTrack,
        registry: MusicProviderRegistry?,
    ): LyricsDocument {
        if (track.id.source == source) return LyricsDocument(emptyList())
        val resolved = registry ?: melox.music.provider.MeloXMusicProviders.create()
        val provider = resolved[source] ?: return LyricsDocument(emptyList())
        val search = provider as? SearchCapability ?: return LyricsDocument(emptyList())
        val lyrics = provider as? LyricsCapability ?: return LyricsDocument(emptyList())
        val clean = track.title.replace(Regex("[（(].*?[）)]"), "").trim()
        val queries = buildList {
            if (clean.isNotBlank()) add(clean)
            if (track.title.isNotBlank()) add(track.title)
            track.artistText.substringBefore(" /").takeIf { it.isNotBlank() && it != "未知歌手" }?.let { artist ->
                add("$clean $artist".trim())
            }
        }.distinct()
        for (query in queries) {
            val results = runCatching { search.searchSongs(query, 1, 10).items }.getOrDefault(emptyList())
            val match = results
                .filter { isSafeCrossProviderLyricMatch(track.title, track.artistText, track.durationMs ?: 0L, it) }
                .maxByOrNull { lyricTrackMatchScore(track, it) }
                ?: continue
            val document = runCatching { lyrics.lyrics(match) }.getOrNull()
            if (document != null && document.lines.isNotEmpty()) return document
        }
        return LyricsDocument(emptyList())
    }
}

internal fun lyricTrackMatchScore(track: MusicTrack, candidate: MusicTrack): Int {
    val artist = normalizeLyricMatchText(track.artistText)
    val candidateArtist = normalizeLyricMatchText(candidate.artistText)
    val durationMs = track.durationMs ?: 0L
    return 100 +
        (if (artist.isNotBlank() && (candidateArtist.contains(artist) || artist.contains(candidateArtist))) 25 else 0) -
        (if (durationMs > 0L) {
            (candidate.durationMs?.let { kotlin.math.abs(durationMs - it) } ?: 0L) / 1_000L
        } else 0L).toInt()
}

internal enum class LyricTrackVersion { Original, Live, Dj, Remix, Instrumental }

internal data class LyricTitleIdentity(
    val baseTitle: String,
    val version: LyricTrackVersion,
    val versionLabel: String,
)

internal fun lyricTitleIdentity(title: String): LyricTitleIdentity {
    val normalized = title.lowercase()
    val version = when {
        Regex("(?:^|[^a-z])dj(?:[^a-z]|$)|电音|舞曲").containsMatchIn(normalized) -> LyricTrackVersion.Dj
        Regex("remix|重混|混音版").containsMatchIn(normalized) -> LyricTrackVersion.Remix
        Regex("live|现场|演唱会|音乐节").containsMatchIn(normalized) -> LyricTrackVersion.Live
        Regex("instrumental|伴奏|纯音乐|off vocal|karaoke").containsMatchIn(normalized) -> LyricTrackVersion.Instrumental
        else -> LyricTrackVersion.Original
    }
    val versionPattern = Regex(
        "(?i)(?:[（(【\\[]?\\s*(?:dj(?:\\s+version)?|[^）)】\\]]*remix|live|现场版?|演唱会版?|音乐节版?|instrumental|伴奏版?|纯音乐|off\\s*vocal|karaoke)\\s*[）)】\\]]?)",
    )
    val base = normalizeLyricMatchText(title.replace(versionPattern, ""))
    val label = normalizeLyricMatchText(title).removePrefix(base)
    return LyricTitleIdentity(base, version, label)
}

internal fun isSafeCrossProviderLyricMatch(
    targetTitle: String,
    targetArtist: String,
    targetDurationMs: Long,
    candidate: MusicTrack,
): Boolean {
    val target = lyricTitleIdentity(targetTitle)
    val other = lyricTitleIdentity(candidate.title)
    if (target.baseTitle.isBlank() || target.baseTitle != other.baseTitle) return false
    if (target.version != other.version) return false
    if (target.version != LyricTrackVersion.Original && target.versionLabel != other.versionLabel) return false
    if (targetDurationMs > 0L) {
        val candidateDuration = candidate.durationMs ?: return false
        val toleranceMs = if (target.version == LyricTrackVersion.Original) 2_000L else 1_000L
        if (kotlin.math.abs(targetDurationMs - candidateDuration) > toleranceMs) return false
    }
    val targetArtistKey = normalizeLyricMatchText(targetArtist.substringBefore(" /"))
    val candidateArtistKey = normalizeLyricMatchText(candidate.artistText)
    return targetArtistKey.isBlank() || candidateArtistKey.contains(targetArtistKey) || targetArtistKey.contains(candidateArtistKey)
}

internal fun normalizeLyricMatchText(value: String): String = value.lowercase()
    .replace(Regex("[^\\p{L}\\p{N}]"), "")

internal data class LyricPresentation(
    val showTranslation: Boolean,
    val wordByWord: Boolean,
    val autoFollow: Boolean,
    val tapSeek: Boolean,
) {
    companion object {
        fun fromSettings(): LyricPresentation = LyricPresentation(
            showTranslation = MeloXSettingsRuntime.showLyricTranslation,
            wordByWord = MeloXSettingsRuntime.lyricWordByWordEnabled,
            autoFollow = MeloXSettingsRuntime.lyricAutoFollowEnabled,
            tapSeek = MeloXSettingsRuntime.lyricTapSeekEnabled,
        )
    }
}

fun lyricLineShowsSyllables(line: LyricLine, wordByWord: Boolean): Boolean =
    wordByWord && line.syllables.isNotEmpty()

fun lyricLineTranslation(line: LyricLine, showTranslation: Boolean): String? =
    if (showTranslation) line.translation?.takeIf { it.isNotBlank() } else null

fun lyricShouldAutoScroll(autoFollow: Boolean, activeIndex: Int): Boolean =
    autoFollow && activeIndex >= 0

fun lyricTapTargetMs(tapSeek: Boolean, line: LyricLine): Long? =
    if (tapSeek) line.timeMs else null
