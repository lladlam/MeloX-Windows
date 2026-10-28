package melox.playback

import melox.music.model.AudioQualityTier
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.model.ProviderTrackMetadata
import melox.music.model.TrackAvailability
import melox.music.provider.MusicProvider
import melox.music.provider.MusicProviderRegistry
import melox.music.provider.PlaybackCapability
import melox.music.provider.SearchCapability
import melox.music.provider.TrackAggregation
import melox.provider.spotify.SpotifyTrackMatcher
import melox.remoteconfig.MeloXRemoteConfigDefaults
import melox.remoteconfig.MeloXRemoteFallbackConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

object CrossProviderPlaybackPreferences {
    private const val PreferencesName = "melox_playback"
    private const val EnabledKey = "cross_provider_unavailable_fallback"

    private fun prefs() = melox.platform.getPreferences(PreferencesName)

    fun enabled(): Boolean = prefs().getBoolean(EnabledKey, false)

    fun setEnabled(enabled: Boolean) = prefs().putBoolean(EnabledKey, enabled)
}

object CrossProviderPlaybackRuntime {
    private val sourceByNeteaseSong = ConcurrentHashMap<Long, MusicSource>()

    fun record(songId: Long, source: MusicSource) {
        sourceByNeteaseSong[songId] = source
    }

    fun sourceFor(songId: Long?): MusicSource? = songId?.let(sourceByNeteaseSong::get)

    fun clear(songId: Long? = null) {
        if (songId == null) sourceByNeteaseSong.clear() else sourceByNeteaseSong.remove(songId)
    }
}

internal data class CrossProviderFallbackRequest(
    val songId: Long,
    val title: String,
    val artist: String,
    val durationMs: Long?,
    val quality: AudioQualityTier,
    val source: MusicSource = MusicSource.Netease,
    val resourceId: String = songId.toString(),
)

internal data class CrossProviderFallbackResult(
    val source: MusicSource,
    val resourceId: String,
    val url: String,
    val requestHeaders: Map<String, String>,
    val actualQuality: AudioQualityTier,
    val expiresAtEpochMs: Long?,
)

class CrossProviderPlaybackFallbackResolver(
    private val enabledProvider: () -> Boolean,
    private val registryProvider: () -> MusicProviderRegistry?,
    private val cacheIdentityProvider: () -> String = { "" },
    private val eventLogger: (String) -> Unit = {},
    private val fallbackConfigProvider: () -> MeloXRemoteFallbackConfig = {
        MeloXRemoteConfigDefaults.Config.fallback
    },
    private val providerPlaybackEnabled: (MusicSource) -> Boolean = { true },
) {
    fun cacheIdentity(): String = if (enabledProvider()) {
        val fallback = fallbackConfigProvider()
        "enabled:${fallback.enabled}:${fallback.order.joinToString(",")}:${fallback.disabledProviders.sorted().joinToString(",")}:" +
            "${EligibleSources.filter(providerPlaybackEnabled).map { it.storageValue }.sorted().joinToString(",")}:" +
            "${fallback.timeoutMs}:${cacheIdentityProvider()}"
    } else {
        "disabled"
    }

    internal fun resolve(request: CrossProviderFallbackRequest): CrossProviderFallbackResult? {
        if (!enabledProvider() || request.title.isBlank() || request.artist.isBlank()) {
            eventLogger("skipped song=${request.songId}: disabled or missing metadata")
            return null
        }
        val fallbackConfig = fallbackConfigProvider()
        if (!fallbackConfig.enabled) {
            eventLogger("skipped song=${request.songId}: remotely disabled")
            return null
        }
        val order = fallbackConfig.order.withIndex().associate { (index, source) -> source to index }
        val providers = registryProvider()?.providers.orEmpty()
            .filter(::isEligibleFallbackProvider)
            .filterNot { it.source == request.source }
            .filter { providerPlaybackEnabled(it.source) }
            .filterNot { it.source.storageValue in fallbackConfig.disabledProviders }
            .sortedBy { order[it.source.storageValue] ?: Int.MAX_VALUE }
        if (providers.isEmpty()) {
            eventLogger("skipped song=${request.songId}: no eligible providers")
            return null
        }

        val artists = splitArtists(request.artist)
        val primaryArtist = artists.firstOrNull() ?: return null
        val sourceTrack = MusicTrack(
            id = MusicResourceId(request.source, request.resourceId),
            title = request.title,
            artists = artists.map { MusicArtistRef(name = it) },
            durationMs = request.durationMs?.takeIf { it > 0L },
            availability = TrackAvailability.Unavailable,
            providerMetadata = if (request.source == MusicSource.Netease) ProviderTrackMetadata.Netease(request.songId) else ProviderTrackMetadata.Empty,
        )
        val query = "${request.title} $primaryArtist"

        return runBlocking(Dispatchers.IO) {
            val resolved = withTimeoutOrNull(fallbackConfig.timeoutMs.toLong()) {
                // Provider order is the priority; a later source must not win a parallel race.
                for (provider in providers) {
                    val search = provider as SearchCapability
                    val candidates = runCatching { search.searchSongs(query, page = 1, pageSize = 10).items }
                        .onFailure { if (it is CancellationException) throw it }
                        .onSuccess { eventLogger("search ${provider.source.storageValue}: ${it.size} candidates") }
                        .onFailure { eventLogger("search ${provider.source.storageValue} failed: ${it.javaClass.simpleName}") }
                        .getOrDefault(emptyList())
                    val ranked = SpotifyTrackMatcher.rank(sourceTrack, candidates).let { matches ->
                        if (sourceTrack.durationMs != null) matches else matches.filter { artistSet(sourceTrack) == artistSet(it.candidate) }
                    }
                    eventLogger("strict matches ${provider.source.storageValue} song=${request.songId}: ${ranked.size}")
                    for (match in ranked) {
                        if (provider.source != match.candidate.id.source) continue
                        val playback = provider as PlaybackCapability
                        val resolution = runCatching { playback.resolvePlayback(match.candidate, request.quality) }
                            .onFailure { if (it is CancellationException) throw it }.getOrNull()
                        if (resolution is PlaybackResolution.Playable) {
                            eventLogger("resolved song=${request.songId} via ${provider.source.storageValue}")
                            return@withTimeoutOrNull CrossProviderFallbackResult(provider.source, match.candidate.id.value,
                                resolution.url, resolution.requestHeaders, resolution.actualQuality ?: resolution.requestedQuality,
                                resolution.expiresAtEpochMs)
                        }
                        eventLogger("playback ${provider.source.storageValue} rejected: ${resolution?.javaClass?.simpleName ?: "error"}")
                    }
                }
                null
            }
            if (resolved == null) eventLogger("unresolved or timed out song=${request.songId}")
            resolved
        }
    }

    private fun isEligibleFallbackProvider(provider: MusicProvider): Boolean =
        provider.source in EligibleSources && provider is SearchCapability && provider is PlaybackCapability

    private fun artistSet(track: MusicTrack) = track.artists.map { TrackAggregation.normalizeArtist(it.name) }.filter(String::isNotBlank).toSet()

    companion object {
        val EligibleSources = setOf(
            MusicSource.Netease,
            MusicSource.QQMusic,
            MusicSource.Kugou,
            MusicSource.KugouLite,
            MusicSource.Qishui,
            MusicSource.Kuwo,
            MusicSource.Bilibili,
        )

        internal fun splitArtists(value: String): List<String> = value
            .split(Regex("\\s*(?:/|、|，|,)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }
}
