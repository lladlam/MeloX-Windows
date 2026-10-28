package melox.playback

import melox.music.model.AudioQualityTier
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.provider.lxuser.LxUserRuntime
import melox.provider.lxuser.LxUserSourceStore
import melox.provider.lxuser.buildLxMusicInfo
import melox.network.NeteaseSearchClient
import melox.lyrics.LrcLyricsParser
import melox.lyrics.LyricsDocument
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.ProviderTrackMetadata
import melox.platform.logDebug
import melox.platform.logInfo
import melox.platform.logWarn
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal data class LxUserPlaybackResult(
    val sourceId: String,
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap(),
)

/** Resolves a song through locally installed LX Music user API scripts. */
class LxUserPlaybackResolver {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<LxUserPlaybackResult?>>()
    fun cacheIdentity(): String = LxUserSourceStore.list().joinToString("|") { it.id }

    internal fun resolveArtwork(track: MusicTrack): String? =
        resolveAction(track, "pic")?.let { value ->
            when (value) {
                is String -> value
                is Map<*, *> -> sequenceOf("url", "picUrl", "artworkUrl")
                    .mapNotNull { value[it]?.toString() }.firstOrNull { it.startsWith("http") }
                else -> null
            }
        }?.takeIf { it.startsWith("http://") || it.startsWith("https://") }

    internal fun resolveLyrics(track: MusicTrack): LyricsDocument? {
        val value = resolveAction(track, "lyric") ?: return null
        val data = (value as? Map<*, *>) ?: return (value as? String)?.let(LrcLyricsParser::parse)
        val lyric = data["lyric"]?.toString().orEmpty().ifBlank { data["lrc"]?.toString().orEmpty() }
        if (lyric.isBlank()) return null
        return LrcLyricsParser.parse(
            lrc = lyric,
            translation = data["tlyric"]?.toString().orEmpty().ifBlank { data["translation"]?.toString().orEmpty() },
            romanization = data["rlyric"]?.toString().orEmpty().ifBlank { data["romalrc"]?.toString().orEmpty() },
        )
    }

    private fun resolveAction(track: MusicTrack, action: String): Any? {
        val sourceCode = when (track.id.source) {
            MusicSource.QQMusic -> "tx"
            MusicSource.Kugou, MusicSource.KugouLite -> "kg"
            MusicSource.Kuwo -> "kw"
            MusicSource.Netease -> "wy"
            else -> return null
        }
        val song = buildLxMusicInfo(track, sourceCode, AudioQualityTier.Standard.toLxQuality())
        for (record in LxUserSourceStore.list()) {
            val script = LxUserSourceStore.script(record.id) ?: continue
            val result = runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                if (!runtime.supportsSource(sourceCode) || !runtime.supportsAction(sourceCode, action)) return@runCatching null
                runtime.callAction(action, song + mapOf(
                    "source" to sourceCode,
                    "type" to "128k",
                    "musicInfo" to song,
                ))
            }.onFailure { error ->
                error.propagateCancellation()
                logWarn(TAG, "LX $action failed script=${record.id} detail=${error.safeLogMessage()}")
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    internal fun resolve(
        songId: Long,
        title: String,
        artist: String,
        durationMs: Long?,
        quality: AudioQualityTier,
    ): LxUserPlaybackResult? {
        return resolve(
            MusicTrack(
                id = MusicResourceId(MusicSource.Netease, songId.toString()),
                title = title,
                artists = listOf(MusicArtistRef(name = artist)),
                durationMs = durationMs,
            ),
            quality,
        )
    }

    internal fun resolve(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val key = "${track.id.source.storageValue}:${track.id.value}:${quality.name}:${cacheIdentity()}"
        val pending = CompletableFuture<LxUserPlaybackResult?>()
        val previous = inFlight.putIfAbsent(key, pending)
        if (previous != null) return try {
            previous.get(45, TimeUnit.SECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (_: Exception) { null }
        return try {
            resolveUncached(track, quality).also { pending.complete(it) }
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    private fun resolveUncached(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val sourceCode = when (track.id.source) {
            MusicSource.QQMusic -> "tx"
            MusicSource.Kugou, MusicSource.KugouLite -> "kg"
            MusicSource.Kuwo -> "kw"
            MusicSource.Netease -> "wy"
            else -> return null
        }
        val title = track.title
        val artist = track.artistText
        if (title.isBlank() || artist.isBlank()) return null
        val resourceId = track.id.value
        val lxQuality = quality.toLxQuality()
        logDebug(TAG, "LX resolve start provider=${track.id.source.storageValue} id=${resourceId.take(8)} quality=$lxQuality " +
            "source=$sourceCode title=${title.take(40)} artist=${artist.take(40)}")
        val records = LxUserSourceStore.list()
        for (record in records) {
            val script = LxUserSourceStore.script(record.id) ?: continue
            var phase = "load"
            runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                phase = "request"
                // Provider identifiers are not interchangeable across services.
                if (!runtime.supportsSource(sourceCode) || !runtime.supportsAction(sourceCode, "musicUrl")) {
                    throw IOException("LX 音乐源不支持该服务")
                }
                lxQualityFallbacks(lxQuality).map { runtime.qualityFor(sourceCode, it) }.distinct().asSequence()
                    .mapNotNull { sourceQuality ->
                        val song = buildLxMusicInfo(track, sourceCode, sourceQuality)
                        logDebug(TAG, "LX candidate script=${record.id} source=$sourceCode quality=$sourceQuality")
                        val response = runCatching {
                            runtime.callAction("musicUrl", song + mapOf(
                                "source" to sourceCode, "type" to sourceQuality, "musicInfo" to song,
                            ))
                        }.onFailure {
                            it.propagateCancellation()
                            logWarn(TAG, "LX candidate failed script=${record.id} source=$sourceCode quality=$sourceQuality detail=${it.safeLogMessage()}")
                        }.getOrNull()
                        val url = lxPlaybackUrl(response)
                        logDebug(TAG, "LX candidate result script=${record.id} source=$sourceCode quality=$sourceQuality url=${url != null}")
                        url?.let { LxUserPlaybackResult(record.id, it) }
                    }.firstOrNull() ?: throw IOException("LX 音乐源没有返回可播放链接")
            }.onSuccess {
                logInfo(TAG, "LX resolved source=${track.id.source.storageValue} script=${record.id}")
                return it
            }.onFailure { error ->
                error.propagateCancellation()
                logWarn(
                    TAG,
                    "LX failed source=${track.id.source.storageValue} script=${record.id} phase=$phase " +
                        "error=${error.javaClass.simpleName} detail=${error.safeLogMessage()}",
                )
            }
        }
        if (records.isNotEmpty() && track.id.source != MusicSource.Netease) {
            resolveViaNeteaseMatch(track, quality)?.let { return it }
        }
        logInfo(TAG, "LX unresolved source=${track.id.source.storageValue} scripts=${LxUserSourceStore.list().size}")
        return null
    }

    private fun resolveViaNeteaseMatch(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        val query = track.title.trim()
        val candidates = runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                NeteaseSearchClient().searchSongs(query, limit = 50)
            }
        }.onFailure {
            it.propagateCancellation()
            logWarn(TAG, "LX Netease match search failed title=${track.title.take(40)} detail=${it.safeLogMessage()}")
        }
            .getOrNull().orEmpty()
        val requestedArtist = normalizeLxText(track.artistText)
        val match = candidates
            .asSequence()
            .filter { normalizeLxText(it.name) == normalizeLxText(track.title) }
            .filter { requestedArtist.isBlank() || normalizeLxText(it.artists).contains(requestedArtist) }
            .filter { candidate -> track.durationMs == null || candidate.durationMs <= 0L || kotlin.math.abs(candidate.durationMs - track.durationMs) <= 3_000L }
            .filter { candidate -> candidate.name.lowercase(Locale.ROOT).let { "live" !in it && "翻唱" !in it && "cover" !in it } }
            .sortedBy { candidate -> kotlin.math.abs(candidate.durationMs - (track.durationMs ?: candidate.durationMs)) }
            .firstOrNull()
        if (match == null) {
            logWarn(TAG, "LX Netease match not found title=${track.title.take(40)} candidates=${candidates.size}")
            return null
        }

        val neteaseTrack = MusicTrack(
            id = MusicResourceId(MusicSource.Netease, match.id.toString()),
            title = match.name,
            artists = listOf(MusicArtistRef(name = match.artists)),
            durationMs = match.durationMs,
            artworkUrl = match.artworkUrl,
            providerMetadata = ProviderTrackMetadata.Netease(match.id),
        )
        logInfo(TAG, "LX Netease match source=${track.id.source.storageValue} id=${track.id.value.take(8)} -> ${match.id}")
        return resolveNeteaseTrack(neteaseTrack, quality)
    }

    private fun resolveNeteaseTrack(track: MusicTrack, quality: AudioQualityTier): LxUserPlaybackResult? {
        for (record in LxUserSourceStore.list()) {
            val script = LxUserSourceStore.script(record.id) ?: continue
            val result = runCatching {
                val runtime = LxUserRuntime.session(record.id, script)
                    if (!runtime.supportsSource("wy") || !runtime.supportsAction("wy", "musicUrl")) return@runCatching null
                    lxQualityFallbacks(quality.toLxQuality()).map { runtime.qualityFor("wy", it) }.distinct().asSequence().mapNotNull { sourceQuality ->
                        val song = buildLxMusicInfo(track, "wy", sourceQuality)
                        val response = runCatching {
                            runtime.callAction("musicUrl", song + mapOf(
                                "source" to "wy",
                                "type" to sourceQuality,
                                "musicInfo" to song,
                            ))
                        }.onFailure {
                            it.propagateCancellation()
                            logWarn(TAG, "LX Netease candidate failed quality=$sourceQuality detail=${it.safeLogMessage()}")
                        }.getOrNull()
                        val url = lxPlaybackUrl(response)
                        url?.let { LxUserPlaybackResult(record.id, it) }
                    }.firstOrNull()
            }.onFailure { it.propagateCancellation() }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private companion object {
        const val TAG = "MeloXThirdParty"
    }
}

private fun lxPlaybackUrl(value: Any?): String? {
    val url = when (value) {
        is String -> value
        is Map<*, *> -> (value["url"] as? String) ?: ((value["data"] as? Map<*, *>)?.get("url") as? String)
        else -> null
    }
    return url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
}

private fun Throwable.propagateCancellation() {
    when (this) {
        is InterruptedException -> { Thread.currentThread().interrupt(); throw this }
        is java.util.concurrent.CancellationException -> throw this
        is Error -> throw this
    }
}

private fun lxQualityFallbacks(requested: String): List<String> = when (requested) {
    "flac24bit" -> listOf("flac24bit", "flac", "320k", "128k")
    "flac" -> listOf("flac", "320k", "128k")
    "320k" -> listOf("320k", "128k")
    else -> listOf(requested)
}

private fun normalizeLxText(value: String): String = value
    .lowercase(Locale.ROOT)
    .replace(Regex("[\\s\\p{Punct}·•，。！？、（）()\\[\\]【】]"), "")


private fun AudioQualityTier.toLxQuality(): String = when (this) {
    AudioQualityTier.Standard -> "128k"
    AudioQualityTier.High -> "320k"
    AudioQualityTier.Lossless -> "flac"
    AudioQualityTier.HiResolution, AudioQualityTier.Immersive, AudioQualityTier.Master -> "flac24bit"
}

private fun Throwable.safeLogMessage(): String = message.orEmpty()
    .replace(Regex("https?://\\S+"), "<url>")
    .replace(Regex("(?i)(apikey|api_key|token|key)=([^&\\s]+)"), "$1=<redacted>")
    .replace('\n', ' ')
    .take(240)
    .ifBlank { "none" }
