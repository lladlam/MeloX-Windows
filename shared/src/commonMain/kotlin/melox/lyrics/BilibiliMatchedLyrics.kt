package melox.lyrics

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.LyricsCapability
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.MusicProviderRegistry
import melox.music.provider.SearchCapability
import melox.provider.bilibili.*
import melox.settings.MeloXSettingsRuntime

/** Pinned MeloXProviderLyricsLoader.loadBilibiliMatched, with desktop provider boundaries. */
internal class BilibiliMatchedLyrics(private val sources: BilibiliLyricSources = BilibiliLyricSources()) {
    suspend fun load(track: MusicTrack, alignment: Boolean): LyricsDocument = coroutineScope {
        val duration = track.durationMs ?: 0L
        if (duration <= 0L) return@coroutineScope LyricsDocument(emptyList())
        val candidates = BilibiliLyricAlignment.extractTitleCandidates(track.title, track.artistText).take(3)
        if (candidates.isEmpty()) return@coroutineScope LyricsDocument(emptyList())
        suspend fun catalog(source: MusicSource, requested: List<BilibiliTitleCandidate>): MusicTrack? {
            for (candidate in requested) {
                val query = listOfNotNull(candidate.title, candidate.artist).filter(String::isNotBlank).joinToString(" ")
                val matches = attempt { sources.search(source, query) }.orEmpty()
                    .filter { BilibiliLyricAlignment.isSafeCatalogMatch(it, candidate) }
                matches.maxByOrNull {
                    (if (BilibiliLyricAlignment.normalize(it.title) == BilibiliLyricAlignment.normalize(candidate.title)) 100 else 50) +
                        if (it.artists.isNotEmpty()) 10 else 0
                }?.let { return it }
            }
            return null
        }
        suspend fun result(source: String, catalog: MusicTrack?, amll: Boolean = false): BilibiliLyricSourceResult? {
            catalog ?: return null
            val document = attempt { if (amll) sources.amll(catalog) else sources.lyrics(catalog) }
            return document?.takeIf { it.lines.isNotEmpty() }?.let { BilibiliLyricSourceResult(source, it, catalog) }
        }
        fun select(results: List<BilibiliLyricSourceResult>): LyricsDocument? = selectAutomaticLyricCandidate(
            results.mapIndexed { priority, value -> AutoLyricCandidate(priority, value.document) },
        )?.document

        val qqJob = async { result("qq", catalog(MusicSource.QQMusic, candidates)) }
        val neteaseJob = async { catalog(MusicSource.Netease, candidates) }
        val amllJob = async { result("amll", neteaseJob.await(), amll = true) }
        val qq = qqJob.await()
        val amll = amllJob.await()
        val primaryResults = listOfNotNull(amll, qq)
        val primaryDocument = select(primaryResults)
        val primary = primaryResults.firstOrNull { it.document == primaryDocument }
            ?: return@coroutineScope LyricsDocument(emptyList())
        if (primary.document.lines.size <= 1) return@coroutineScope primary.document
        val primaryDuration = BilibiliLyricAlignment.effectiveDuration(primary.document)
        if (primaryDuration != null && !BilibiliLyricAlignment.audioClearlyMismatches(duration, primaryDuration.durationMs)) {
            return@coroutineScope primary.document
        }

        val identity = qq?.catalogTrack ?: primary.catalogTrack
        val canonical = BilibiliTitleCandidate(identity.title, identity.artistText.takeUnless { it == "未知歌手" })
        val neteaseTrack = neteaseJob.await()?.takeIf { BilibiliLyricAlignment.isSafeCatalogMatch(it, canonical) }
            ?: catalog(MusicSource.Netease, listOf(canonical))
        val verification = async { result("netease", neteaseTrack) }
        val verificationAmll = async { result("amll", neteaseTrack, amll = true) }
        val netease = verification.await()
        val verifiedAmll = amll ?: verificationAmll.await()
        val results = listOfNotNull(verifiedAmll, qq, netease).distinctBy { it.source }
        val selected = select(results.ifEmpty { listOf(primary) }) ?: LyricsDocument(emptyList())
        val consensus = BilibiliLyricAlignment.consensus(results)
        if (alignment && consensus != null && BilibiliLyricAlignment.audioClearlyMismatches(duration, consensus)) {
            val original = BilibiliProvider.parseIdentity(track.id.value)
            if (original != null) {
                val query = listOfNotNull(canonical.title, canonical.artist).filter(String::isNotBlank).joinToString(" ")
                val replacements = attempt { sources.replacements(query) }.orEmpty()
                val replacement = BilibiliLyricAlignment.selectReplacement(
                    replacements, track.id.value, canonical.title, canonical.artist, consensus,
                )?.track
                val physical = replacement?.let { BilibiliProvider.parseIdentity(it.id.value) }
                if (physical != null) {
                    coroutineContext.ensureActive()
                    sources.save(BilibiliPlaybackAssociation(
                        original.first, original.second, physical.first, physical.second,
                        canonical.title, consensus, replacement.durationMs,
                    ))
                }
            }
        }
        selected
    }

    private suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { null }
}

/** Production methods use the existing real providers; overrides are reserved for regression tests. */
internal open class BilibiliLyricSources(registry: MusicProviderRegistry? = null) {
    private val providers by lazy { registry ?: MeloXMusicProviders.create() }
    private val associationRevision = BilibiliPlaybackAssociationStore.revision()
    open suspend fun search(source: MusicSource, query: String): List<MusicTrack> =
        (providers[source] as? SearchCapability)?.searchSongs(query, 1, 20)?.items.orEmpty()
    open suspend fun lyrics(track: MusicTrack): LyricsDocument =
        (providers[track.id.source] as? LyricsCapability)?.lyrics(track) ?: LyricsDocument(emptyList())
    open suspend fun amll(track: MusicTrack): LyricsDocument = track.id.value.toLongOrNull()?.let {
        AmlldbLyricsClient().lyrics(it, MeloXLyricScript.fromSystem())
    } ?: LyricsDocument(emptyList())
    open suspend fun replacements(query: String): List<MusicTrack> =
        (providers[MusicSource.Bilibili] as? BilibiliProvider)?.searchReplacementCandidates(query, 20).orEmpty()
    open fun save(association: BilibiliPlaybackAssociation) {
        // A request started before the user disabled automatic matching must not persist a late association.
        if (MeloXSettingsRuntime.automaticLyricSelectionEnabled && MeloXSettingsRuntime.bilibiliLyricAudioAlignmentEnabled &&
            BilibiliPlaybackAssociationStore.revision() == associationRevision) {
            BilibiliPlaybackAssociationStore.writeIfChanged(association)
        }
    }
}
