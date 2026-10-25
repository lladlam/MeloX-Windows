package melox.playback

import melox.audio.MusicQuality
import melox.audio.MusicQualityRuntime
import melox.audio.NeteaseQualityClient
import melox.audio.NeteasePlaybackUnavailableException
import melox.music.model.AudioQualityTier
import melox.music.model.MusicTrack
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicArtistRef
import melox.music.model.ProviderTrackMetadata
import melox.network.NeteaseSearchClient
import melox.music.provider.PlaybackAccountStore
import melox.platform.logDebug
import melox.platform.logInfo
import melox.platform.logWarn
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Desktop playback resolver for NetEase tracks. Replaces Media3's
 * ResolvingDataSource with a simple URI-based resolver that the desktop
 * player can call directly.
 */
class NeteasePlaybackResolver(
    private val cookieProvider: () -> String = { "" },
    private val client: NeteaseSearchClient = NeteaseSearchClient(cookieProvider = cookieProvider),
    private val localSourceProvider: (Long) -> String? = { null },
    private val crossProviderFallback: CrossProviderPlaybackFallbackResolver? = null,
    private val chkszPlayback: ChkszPlaybackResolver? = null,
    private val lxUserPlayback: LxUserPlaybackResolver? = null,
    private val providerPlaybackEnabled: (melox.music.model.MusicSource) -> Boolean = { true },
    private val thirdPartySourcesEnabled: () -> Boolean = { true },
    private val thirdPartyOnlyForMembership: () -> Boolean = { false },
    private val qualityClient: NeteaseQualityClient = NeteaseQualityClient(cookieProvider = cookieProvider),
    thirdPartyPlayback: ThirdPartyPlaybackResolver? = null,
) {
    private val thirdPartyResolver = thirdPartyPlayback ?: ThirdPartyPlaybackResolver(
        thirdPartySourcesEnabled, thirdPartyOnlyForMembership, chkszPlayback, lxUserPlayback)
    private val fallbackChain = PlaybackFallbackChain(crossProviderFallback, thirdPartyResolver)
    private data class ResolveKey(
        val songId: Long,
        val quality: MusicQuality,
        val cookieHeader: String,
        val fallbackIdentity: String,
        val sourceIdentity: String,
        val metadataIdentity: String,
    )
    class ResolvedRequest(
        val uri: String,
        val headers: Map<String, String> = emptyMap(),
        val expiresAtEpochMs: Long? = null,
        val cacheIdentity: String = "netease",
    )

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, ResolvedRequest>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, ResolvedRequest>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<ResolvedRequest>>()

    @Volatile
    private var providerDelegate: ProviderPlaybackResolver? = null

    /**
     * Resolves the same source used by the desktop player for offline analysis.
     */
    fun resolveSongUri(
        songId: Long,
        quality: MusicQuality = MusicQualityRuntime.selected,
    ): String = resolveSongRequest(songId, quality, fallbackRequest = null).uri

    private fun resolveSongRequest(
        songId: Long,
        quality: MusicQuality,
        fallbackRequest: CrossProviderFallbackRequest?,
    ): ResolvedRequest {
        localSourceProvider(songId)?.let { return ResolvedRequest(it) }
        val cookieHeader = cookieProvider()
        val key = ResolveKey(
            songId = songId,
            quality = quality,
            cookieHeader = cookieHeader,
            fallbackIdentity = fallbackChain.cacheIdentity(),
            sourceIdentity = "${thirdPartySourcesEnabled()}:${thirdPartyOnlyForMembership()}:" +
                "${chkszPlayback?.cacheIdentity().orEmpty()}:${lxUserPlayback?.cacheIdentity().orEmpty()}",
            metadataIdentity = fallbackRequest?.let {
                "${it.title}\u001f${it.artist}\u001f${it.durationMs.orZero()}"
            }.orEmpty(),
        )
        cached(key)?.let { return it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching { existing.get() }
            .getOrElse { throw IOException("Unable to resolve playback source", it.cause ?: it) }
        return try {
            val resolved = try {
                val source = qualityClient.playbackSourceBlocking(songId = songId, requestedQuality = quality)
                if (quality == MusicQualityRuntime.selected) CrossProviderPlaybackRuntime.clear(songId)
                ResolvedRequest(source.url)
            } catch (error: NeteasePlaybackUnavailableException) {
                val track = fallbackRequest?.takeIf { it.title.isNotBlank() }?.let { request ->
                    MusicTrack(
                        id = MusicResourceId(MusicSource.Netease, songId.toString()),
                        title = request.title,
                        artists = CrossProviderPlaybackFallbackResolver.splitArtists(request.artist).map { MusicArtistRef(name = it) },
                        durationMs = request.durationMs,
                        providerMetadata = ProviderTrackMetadata.Netease(songId),
                    )
                } ?: throw error
                val fallback = fallbackChain.resolve(track, quality.toCommonTier(), membershipFailure = true) ?: throw error
                fallback.crossProvider?.let { cross ->
                    MusicQualityRuntime.recordActual(
                        songId = songId,
                        requested = quality,
                        actual = cross.actualQuality.toMusicQuality(quality),
                    )
                    if (quality == MusicQualityRuntime.selected) {
                        CrossProviderPlaybackRuntime.record(songId, cross.source)
                    }
                }
                ResolvedRequest(
                    uri = fallback.url,
                    headers = fallback.requestHeaders,
                    expiresAtEpochMs = fallback.expiresAtEpochMs,
                    cacheIdentity = fallback.cacheIdentity,
                )
            }
            synchronized(cacheLock) { resolvedUris[key] = resolved }
            pending.complete(resolved)
            resolved
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    fun resolveUri(uri: String): ResolvedRequest {
        val parsed = java.net.URI(uri)
        if (parsed.scheme != MELOX_SCHEME || parsed.host != SONG_HOST) {
            return ResolvedRequest(uri)
        }
        val songId = parsed.path.substringAfterLast('/').toLongOrNull()
            ?: throw IOException("Invalid MeloX song URI: $uri")
        localSourceProvider(songId)?.let { return ResolvedRequest(it) }
        val queryParams = queryParams(parsed)
        val requestedQuality = MusicQuality.fromApiLevel(queryParams[QUALITY_QUERY])
            ?: MusicQualityRuntime.selected
        val fallbackRequest = fallbackRequest(parsed, songId, requestedQuality)
        return resolveSongRequest(songId, requestedQuality, fallbackRequest)
    }

    fun prefetch(uri: String) {
        val parsed = java.net.URI(uri)
        if (parsed.scheme != MELOX_SCHEME || parsed.host != SONG_HOST) return
        val songId = parsed.path.substringAfterLast('/').toLongOrNull() ?: return
        val queryParams = queryParams(parsed)
        val quality = MusicQuality.fromApiLevel(queryParams[QUALITY_QUERY])
            ?: MusicQualityRuntime.selected
        resolveSongRequest(songId, quality, fallbackRequest(parsed, songId, quality))
    }

    internal fun fallbackRequest(
        uri: java.net.URI,
        songId: Long,
        quality: MusicQuality,
    ): CrossProviderFallbackRequest? {
        val queryParams = queryParams(uri)
        val title = queryParams[TITLE_QUERY]?.takeIf(String::isNotBlank) ?: return null
        val artist = queryParams[ARTIST_QUERY]?.takeIf(String::isNotBlank) ?: return null
        return CrossProviderFallbackRequest(
            songId = songId,
            title = title,
            artist = artist,
            durationMs = queryParams[DURATION_QUERY]?.toLongOrNull()?.takeIf { it > 0L },
            quality = quality.toCommonTier(),
        )
    }

    private fun queryParams(uri: java.net.URI): Map<String, String> = uri.rawQuery
        ?.split("&")?.mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size != 2) null else parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8")
        }?.toMap().orEmpty()

    private fun cached(key: ResolveKey): ResolvedRequest? = synchronized(cacheLock) {
        resolvedUris[key]?.also { cached ->
            if (cached.expiresAtEpochMs?.let { System.currentTimeMillis() >= it } == true) {
                resolvedUris.remove(key)
                return@synchronized null
            }
        }
    }

    private fun providerDelegate(): ProviderPlaybackResolver? {
        providerDelegate?.let { return it }
        val registry = ProviderPlaybackRuntime.registryOrNull() ?: return null
        return synchronized(this) {
            providerDelegate ?: ProviderPlaybackResolver(
                neteaseResolver = this,
                providers = registry,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
                providerPlaybackEnabled = providerPlaybackEnabled,
                chkszPlayback = chkszPlayback,
                lxUserPlayback = lxUserPlayback,
                thirdPartySourcesEnabled = thirdPartySourcesEnabled,
                thirdPartyOnlyForMembership = thirdPartyOnlyForMembership,
                crossProviderFallback = crossProviderFallback,
                thirdPartyPlayback = thirdPartyResolver,
            ).also { providerDelegate = it }
        }
    }

    companion object {
        private const val MELOX_SCHEME = "melox"
        private const val SONG_HOST = "song"
        private const val QUALITY_QUERY = "quality"
        private const val TITLE_QUERY = "title"
        private const val ARTIST_QUERY = "artist"
        private const val DURATION_QUERY = "durationMs"
        private const val MAX_RESOLVED_URIS = 96
        private const val PLAYBACK_CACHE_VERSION = 3

        fun uriForSong(
            songId: Long,
            quality: MusicQuality = MusicQualityRuntime.selected,
            title: String? = null,
            artist: String? = null,
            durationMs: Long? = null,
        ): String = buildString {
            append("$MELOX_SCHEME://$SONG_HOST/")
            append(songId)
            append("?$QUALITY_QUERY=${quality.apiLevel}")
            title?.takeIf(String::isNotBlank)?.let { append("&$TITLE_QUERY=${encodeQueryValue(it)}") }
            artist?.takeIf(String::isNotBlank)?.let { append("&$ARTIST_QUERY=${encodeQueryValue(it)}") }
            durationMs?.takeIf { it > 0L }?.let { append("&$DURATION_QUERY=$it") }
        }

        private fun encodeQueryValue(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}

private fun Long?.orZero(): Long = this ?: 0L

private fun AudioQualityTier.toMusicQuality(requested: MusicQuality): MusicQuality = when (this) {
    AudioQualityTier.Standard -> MusicQuality.Standard
    AudioQualityTier.High -> MusicQuality.High
    AudioQualityTier.Lossless -> MusicQuality.Lossless
    AudioQualityTier.HiResolution -> MusicQuality.HiResolution
    AudioQualityTier.Immersive -> requested.takeIf {
        it == MusicQuality.HighDefinitionSurround || it == MusicQuality.ImmersiveSurround
    } ?: MusicQuality.ImmersiveSurround
    AudioQualityTier.Master -> MusicQuality.UltraClearMaster
}
