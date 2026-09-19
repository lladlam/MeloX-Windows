package melox.playback

import melox.audio.MusicQualityRuntime
import melox.music.model.AudioQualityTier
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.PlaybackResolution
import melox.music.model.ProviderTrackMetadata
import melox.music.provider.MusicProviderRegistry
import melox.music.provider.PlaybackCapability
import melox.platform.logDebug
import melox.platform.logInfo
import melox.platform.logWarn
import java.io.IOException
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * Adds provider-aware `melox://track/...` URIs while delegating every legacy
 * `melox://song/<Long>` URI to the untouched NetEase resolver.
 *
 * Desktop stub: Media3 `ResolvingDataSource` is replaced by a simple resolver
 * that maps provider URIs to playback URLs. The desktop player will call
 * [resolveProviderRequest] directly instead of going through a DataSpec.
 */
class ProviderPlaybackResolver(
    private val neteaseResolver: NeteasePlaybackResolver,
    private val providers: MusicProviderRegistry,
    private val authKeyProvider: (MusicSource) -> String = { "" },
    private val providerPlaybackEnabled: (MusicSource) -> Boolean = { true },
    private val chkszPlayback: ChkszPlaybackResolver? = null,
    private val lxUserPlayback: LxUserPlaybackResolver? = null,
    private val thirdPartySourcesEnabled: () -> Boolean = { true },
    private val thirdPartyOnlyForMembership: () -> Boolean = { false },
) {
    private data class ResolveKey(
        val requestUri: String,
        val authKey: String,
        val quality: AudioQualityTier,
    )
class ResolvedRequest(
        val uri: String,
        val headers: Map<String, String> = emptyMap(),
        val expiresAtEpochMs: Long? = null,
    )

    private val cacheLock = Any()
    private val resolvedUris = object : LinkedHashMap<ResolveKey, ResolvedRequest>(MAX_RESOLVED_URIS, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ResolveKey, ResolvedRequest>?): Boolean =
            size > MAX_RESOLVED_URIS
    }
    private val inFlight = ConcurrentHashMap<ResolveKey, CompletableFuture<ResolvedRequest>>()

    private fun queryParam(uri: java.net.URI, key: String): String? {
        val query = uri.query ?: return null
        for (param in query.split("&")) {
            val parts = param.split("=", limit = 2)
            if (parts.firstOrNull() == key) return parts.getOrNull(1)
        }
        return null
    }

    fun resolveUri(uri: String): ResolvedRequest {
        val neteaseResolved = neteaseResolver.resolveUri(uri)
        if (neteaseResolved.cacheIdentity == "netease" && neteaseResolved.uri != uri) {
            return ResolvedRequest(neteaseResolved.uri, neteaseResolved.headers)
        }
        return resolveProviderRequest(uri)
    }

    fun resolveProviderRequest(uri: String): ResolvedRequest {
        val parsed = java.net.URI(uri)
        if (parsed.scheme != MeloXScheme) throw IOException("Unsupported scheme: ${parsed.scheme}")
        if (parsed.host == LegacySongHost) {
            val netease = neteaseResolver.resolveUri(uri)
            return ResolvedRequest(netease.uri, netease.headers)
        }
        if (parsed.host != ProviderTrackHost) throw IOException("Unsupported MeloX host: ${parsed.host}")
        val source = parseSource(parsed) ?: throw IOException("Invalid MeloX provider source: $uri")
        if (!providerPlaybackEnabled(source)) {
            throw IOException("${source.displayName} 播放接口已由远程兼容性配置临时关闭")
        }
        val resourceValue = parsed.path.substringAfter('/').substringAfter('/').takeIf(String::isNotBlank)
            ?: throw IOException("Invalid MeloX provider track ID: $uri")
        val quality = currentQuality(parsed)
        val key = ResolveKey(uri, authKeyProvider(source), quality)
        cached(key)?.let { return it }
        val pending = CompletableFuture<ResolvedRequest>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return runCatching { existing.get() }
            .getOrElse { throw IOException("Unable to resolve provider playback source", it.cause ?: it) }

        return try {
            val id = MusicResourceId(source, resourceValue)
            val track = MusicTrack(
                id = id,
                title = queryParam(parsed, TrackTitleQuery).orEmpty(),
                artists = queryParam(parsed, TrackArtistsQuery).orEmpty().split('\u001f')
                    .filter(String::isNotBlank)
                    .map { melox.music.model.MusicArtistRef(name = it) },
                durationMs = queryParam(parsed, TrackDurationQuery)?.toLongOrNull(),
                providerMetadata = providerMetadata(parsed, id),
            )
            logDebug(TAG, "resolve detail source=${source.storageValue} id=${resourceValue.take(8)} title=${track.title.take(40)} " +
                "artists=${track.artistText.take(60)} durationMs=${track.durationMs} metadata=${track.providerMetadata.javaClass.simpleName}")
            logInfo(TAG, "Resolve start source=${source.storageValue} quality=${quality.name} thirdParty=${thirdPartySourcesEnabled()}")
            val allowExternalResolver = source != MusicSource.Jellyfin
            val lx = if (allowExternalResolver && thirdPartySourcesEnabled() && !thirdPartyOnlyForMembership()) {
                runCatching { lxUserPlayback?.resolve(track, quality) }
                    .onFailure { logWarn(TAG, "LX stage failed source=${source.storageValue} error=${it.javaClass.simpleName}") }
                    .getOrNull()
            } else null
            if (lx != null) {
                logInfo(TAG, "Resolve success source=${source.storageValue} stage=lx script=${lx.sourceId}")
                val result = ResolvedRequest(lx.url, lx.requestHeaders)
                synchronized(cacheLock) { resolvedUris[key] = result }
                pending.complete(result)
                return result
            }
            val thirdParty = if (allowExternalResolver && thirdPartySourcesEnabled() && !thirdPartyOnlyForMembership()) {
                runCatching { chkszPlayback?.resolve(track, quality) }
                    .onFailure { logWarn(TAG, "CHKSZ stage failed source=${source.storageValue} error=${it.javaClass.simpleName}") }
                    .getOrNull()
            } else null
            if (thirdParty != null) {
                logInfo(TAG, "Resolve success source=${source.storageValue} stage=chksz")
                val result = ResolvedRequest(thirdParty.url, emptyMap())
                synchronized(cacheLock) { resolvedUris[key] = result }
                pending.complete(result)
                return result
            }
            logInfo(
                TAG,
                "Resolve fallback source=${source.storageValue} stage=provider chksz=${chkszPlayback?.cacheIdentity() ?: "unavailable"}",
            )
            val provider = providers.require(source)
            val playback = provider as? PlaybackCapability
                ?: throw IOException("${provider.displayName} 当前没有实现播放能力")
            val resolution = runBlocking(Dispatchers.IO) {
                playback.resolvePlayback(track, quality)
            }
            val result = when (resolution) {
                is PlaybackResolution.Playable -> {
                    ProviderPlaybackQualityRuntime.recordActual(
                        id = id,
                        requested = quality,
                        actual = resolution.actualQuality ?: resolution.requestedQuality,
                    )
                    ResolvedRequest(resolution.url, resolution.requestHeaders, resolution.expiresAtEpochMs)
                }
                is PlaybackResolution.Preview -> ResolvedRequest(resolution.url, emptyMap())
                PlaybackResolution.LoginRequired -> throw IOException("${provider.displayName} 需要登录后播放")
                PlaybackResolution.SubscriptionRequired -> {
                    if (allowExternalResolver && thirdPartySourcesEnabled()) {
                        resolveThirdParty(track, quality, source)
                            ?: throw IOException("${provider.displayName} 当前歌曲需要对应会员权益")
                    } else {
                        throw IOException("${provider.displayName} 当前歌曲需要对应会员权益")
                    }
                }
                PlaybackResolution.RegionRestricted -> throw IOException("${provider.displayName} 当前地区不可播放")
                PlaybackResolution.CopyrightRestricted -> throw IOException("${provider.displayName} 当前版权不可播放")
                is PlaybackResolution.Unavailable -> throw IOException(
                    resolution.reason ?: "${provider.displayName} 暂时没有可播放音源",
                )
            }
            synchronized(cacheLock) { resolvedUris[key] = result }
            pending.complete(result)
            result
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private fun resolveThirdParty(
        track: MusicTrack,
        quality: AudioQualityTier,
        source: MusicSource,
    ): ResolvedRequest? {
        val lx = runCatching { lxUserPlayback?.resolve(track, quality) }
            .onFailure { logWarn(TAG, "LX membership fallback failed source=${source.storageValue}: ${it.message}") }
            .getOrNull()
        if (lx != null) return ResolvedRequest(lx.url, lx.requestHeaders)
        val chksz = runCatching { chkszPlayback?.resolve(track, quality) }
            .onFailure { logWarn(TAG, "CHKSZ membership fallback failed source=${source.storageValue}: ${it.message}") }
            .getOrNull()
        return chksz?.let { ResolvedRequest(it.url, emptyMap()) }
    }

    private fun cached(key: ResolveKey): ResolvedRequest? = synchronized(cacheLock) {
        resolvedUris[key]?.also { cached ->
            if (cached.expiresAtEpochMs?.let { System.currentTimeMillis() >= it } == true) {
                resolvedUris.remove(key)
                return@synchronized null
            }
        }
    }

    private fun currentQuality(uri: java.net.URI): AudioQualityTier {
        val runtime = MusicQualityRuntime.selected.toCommonTier()
        return runtime.takeIf { MusicQualityRuntime.selected.apiLevel.isNotBlank() }
            ?: queryParam(uri, QualityQuery)
                ?.let { raw -> AudioQualityTier.entries.firstOrNull { it.name == raw } }
            ?: AudioQualityTier.Standard
    }

    private fun parseSource(uri: java.net.URI): MusicSource? {
        val raw = uri.path.substringAfter("/").substringBefore("/") ?: return null
        return MusicSource.entries.firstOrNull { it.storageValue == raw }
    }

    private fun providerMetadata(uri: java.net.URI, id: MusicResourceId): ProviderTrackMetadata = when (id.source) {
        MusicSource.Netease -> ProviderTrackMetadata.Netease(
            numericId = id.value.toLongOrNull()
                ?: throw IOException("Invalid NetEase track ID: ${id.value}"),
        )
        MusicSource.QQMusic -> ProviderTrackMetadata.QQMusic(
            songMid = id.value,
            mediaMid = queryParam(uri, QQMediaMidQuery)?.takeIf(String::isNotBlank),
            numericSongId = queryParam(uri, QQNumericIdQuery)?.toLongOrNull(),
        )
        MusicSource.Kugou -> ProviderTrackMetadata.Kugou(
            hash = id.value,
            albumAudioId = queryParam(uri, KugouAlbumAudioIdQuery)?.toLongOrNull(),
            albumId = queryParam(uri, KugouAlbumIdQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.Kuwo -> ProviderTrackMetadata.Kuwo(
            mid = id.value.toLongOrNull()
                ?: throw IOException("Invalid Kuwo track ID: ${id.value}"),
        )
        MusicSource.AppleMusic -> ProviderTrackMetadata.AppleMusic(
            catalogId = id.value,
            storefront = queryParam(uri, AppleStorefrontQuery).orEmpty().ifBlank { "us" },
            previewUrl = queryParam(uri, ApplePreviewUrlQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.Bilibili -> {
            val (bvid, cid) = Pair(id.value.substringBefore(":"), id.value.substringAfter(":").toLongOrNull() ?: 0L)
                ?: throw IOException("Invalid Bilibili track ID")
            ProviderTrackMetadata.Bilibili(bvid, cid)
        }
        MusicSource.Spotify -> ProviderTrackMetadata.Spotify(
            id.value,
            queryParam(uri, SpotifyIsrcQuery)?.takeIf(String::isNotBlank),
        )
        MusicSource.YouTubeMusic -> ProviderTrackMetadata.Empty
        MusicSource.Jellyfin -> ProviderTrackMetadata.Empty
        MusicSource.Local -> ProviderTrackMetadata.Local(
            contentUri = queryParam(uri, "localContentUri").orEmpty(),
            fileKey = id.value,
        )
    }

    companion object {
        private const val MeloXScheme = "melox"
        private const val LegacySongHost = "song"
        private const val ProviderTrackHost = "track"
        private const val QualityQuery = "qualityTier"
        private const val QQMediaMidQuery = "qqMediaMid"
        private const val QQNumericIdQuery = "qqNumericId"
        private const val KugouAlbumAudioIdQuery = "kgAlbumAudioId"
        private const val KugouAlbumIdQuery = "kgAlbumId"
        private const val AppleStorefrontQuery = "appleStorefront"
        private const val ApplePreviewUrlQuery = "applePreviewUrl"
        private const val SpotifyIsrcQuery = "spotifyIsrc"
        private const val TrackTitleQuery = "trackTitle"
        private const val TrackArtistsQuery = "trackArtists"
        private const val TrackDurationQuery = "trackDurationMs"
        private const val TAG = "MeloXThirdParty"
        private const val MAX_RESOLVED_URIS = 96

        fun isProviderTrackUri(uri: String): Boolean =
            uri.startsWith("$MeloXScheme://$ProviderTrackHost/")

        fun uriForTrack(
            track: MusicTrack,
            quality: AudioQualityTier,
        ): String = buildString {
            append("$MeloXScheme://$ProviderTrackHost/")
            append(track.id.source.storageValue)
            append("/")
            append(track.id.value)
            append("?${QualityQuery}=${quality.name}")
            append("&${TrackTitleQuery}=${track.title}")
            append("&${TrackArtistsQuery}=${track.artists.joinToString("\u001f") { it.name }}")
            track.durationMs?.let { append("&${TrackDurationQuery}=$it") }
        }
    }
}