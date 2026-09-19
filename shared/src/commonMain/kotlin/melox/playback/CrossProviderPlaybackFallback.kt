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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
) {
    fun cacheIdentity(): String = if (enabledProvider()) {
        val fallback = fallbackConfigProvider()
        "enabled:${fallback.order.joinToString(",")}:${fallback.disabledProviders.sorted().joinToString(",")}:" +
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
            .filterNot { it.source.storageValue in fallbackConfig.disabledProviders }
            .sortedBy { order[it.source.storageValue] ?: Int.MAX_VALUE }
        if (providers.isEmpty()) {
            eventLogger("skipped song=${request.songId}: no eligible providers")
            return null
        }

        val artists = splitArtists(request.artist)
        val primaryArtist = artists.firstOrNull() ?: return null
        val sourceTrack = MusicTrack(
            id = MusicResourceId(MusicSource.Netease, request.songId.toString()),
            title = request.title,
            artists = artists.map { MusicArtistRef(name = it) },
            durationMs = request.durationMs?.takeIf { it > 0L },
            availability = TrackAvailability.Unavailable,
            providerMetadata = ProviderTrackMetadata.Netease(request.songId),
        )
        val query = "${request.title} $primaryArtist"

        return runBlocking(Dispatchers.IO) {
            val resolved = withTimeoutOrNull(fallbackConfig.timeoutMs.toLong()) {
            val candidates = coroutineScope {
                providers.map { provider ->
                    async {
                        val search = provider as SearchCapability
                        runCatching { search.searchSongs(query, page = 1, pageSize = 10).items }
                            .onSuccess { eventLogger("search ${provider.source.storageValue}: ${it.size} candidates") }
                            .onFailure { eventLogger("search ${provider.source.storageValue} failed: ${it.javaClass.simpleName}") }
                            .getOrDefault(emptyList())
                    }
                }.awaitAll().flatten()
            }
            val ranked = if (sourceTrack.durationMs != null) {
                SpotifyTrackMatcher.rank(sourceTrack, candidates)
            } else {
                val sourceArtists = TrackAggregation.normalizeArtist(sourceTrack.artistText)
                candidates.asSequence()
                    .mapNotNull { SpotifyTrackMatcher.score(sourceTrack, it) }
                    .filter { match ->
                        TrackAggregation.normalizeArtist(match.candidate.artistText) == sourceArtists
                    }
                    .sortedByDescending { it.score }
                    .toList()
            }
            eventLogger("strict matches song=${request.songId}: ${ranked.size}")
            for (match in ranked) {
                val provider = providers.firstOrNull { it.source == match.candidate.id.source } ?: continue
                val playback = provider as PlaybackCapability
                val resolution = runCatching {
                    playback.resolvePlayback(match.candidate, request.quality)
                }.getOrNull()
                if (resolution is PlaybackResolution.Playable) {
                    eventLogger("resolved song=${request.songId} via ${provider.source.storageValue}")
                    return@withTimeoutOrNull CrossProviderFallbackResult(
                        source = provider.source,
                        resourceId = match.candidate.id.value,
                        url = resolution.url,
                        requestHeaders = resolution.requestHeaders,
                        actualQuality = resolution.actualQuality ?: resolution.requestedQuality,
                        expiresAtEpochMs = resolution.expiresAtEpochMs,
                    )
                }
                eventLogger(
                    "playback ${provider.source.storageValue} rejected: ${resolution?.javaClass?.simpleName ?: "error"}",
                )
            }
            null
            }
            if (resolved == null) eventLogger("unresolved or timed out song=${request.songId}")
            resolved
        }
    }

    private fun isEligibleFallbackProvider(provider: MusicProvider): Boolean =
        provider.source in EligibleSources && provider is SearchCapability && provider is PlaybackCapability

    companion object {
        val EligibleSources = setOf(
            MusicSource.QQMusic,
            MusicSource.Kugou,
            MusicSource.Kuwo,
            MusicSource.Bilibili,
        )

        internal fun splitArtists(value: String): List<String> = value
            .split(Regex("\\s*(?:/|、|，|,)\\s*"))
            .map(String::trim)
            .filter(String::isNotBlank)
    }
}