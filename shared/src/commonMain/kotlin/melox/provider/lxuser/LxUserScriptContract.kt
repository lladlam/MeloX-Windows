package melox.provider.lxuser

import java.util.Locale
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata

/** The flat `toOldMusicInfo` fields consumed by LX Music scripts. */
internal fun buildLxMusicInfo(track: MusicTrack, sourceCode: String?, quality: String): Map<String, Any?> {
    val durationSeconds = track.durationMs?.coerceAtLeast(0L)?.div(1_000L)
    val interval = durationSeconds?.let { String.format(Locale.ROOT, "%02d:%02d", it / 60L, it % 60L) }
    val songId = track.id.value
    val albumId = track.album?.id?.value.orEmpty()
    val qualityEntry = listOf(mapOf("type" to quality, "size" to null))
    val qualityMap = mapOf(quality to mapOf<String, Any?>("size" to null))
    val sourceFields: Map<String, Any?> = when (val provider = track.providerMetadata) {
        is ProviderTrackMetadata.QQMusic -> mapOf(
            "songId" to (provider.numericSongId ?: songId),
            "strMediaMid" to (provider.mediaMid ?: provider.songMid),
            "albumMid" to albumId,
        )
        is ProviderTrackMetadata.Kugou -> mapOf("hash" to provider.hash, "albumId" to (provider.albumId ?: albumId))
        is ProviderTrackMetadata.Kuwo -> mapOf("songId" to provider.mid)
        else -> emptyMap()
    }
    val nestedMeta = buildMap<String, Any?> {
        put("songId", songId)
        put("albumName", track.album?.name ?: "")
        put("picUrl", track.artworkUrl)
        put("qualitys", qualityEntry)
        put("_qualitys", qualityMap)
        put("albumId", sourceFields["albumId"] ?: albumId)
        when (val provider = track.providerMetadata) {
            is ProviderTrackMetadata.QQMusic -> {
                put("strMediaMid", provider.mediaMid ?: provider.songMid)
                put("albumMid", albumId)
                put("id", provider.numericSongId)
            }
            is ProviderTrackMetadata.Kugou -> put("hash", provider.hash)
            is ProviderTrackMetadata.Kuwo -> put("songId", provider.mid)
            else -> Unit
        }
    }
    return mapOf(
        "name" to track.title,
        "singer" to track.artistText,
        "source" to sourceCode,
        "songmid" to songId,
        "interval" to interval,
        "albumName" to (track.album?.name ?: ""),
        "img" to (track.artworkUrl ?: ""),
        "typeUrl" to emptyMap<String, Any?>(),
        "albumId" to (sourceFields["albumId"] ?: albumId),
        "types" to qualityEntry,
        "_types" to qualityMap,
    ) + sourceFields + mapOf(
        // Retain the older MeloX extras as an additive superset.
        "id" to songId,
        "mid" to songId,
        "artist" to track.artistText,
        "title" to track.title,
        "duration" to durationSeconds,
        "durationMs" to track.durationMs,
        "quality" to quality,
        "meta" to nestedMeta,
    )
}

/** LX's fetch-style response uses lowercase names and comma-joined values. */
internal fun flattenLxResponseHeaders(headers: Map<String, List<String>>): Map<String, String> {
    val flattened = LinkedHashMap<String, String>()
    headers.forEach { (name, values) ->
        if (values.isNotEmpty()) {
            val key = name.lowercase(Locale.ROOT)
            val joined = values.joinToString(", ")
            flattened[key] = flattened[key]?.let { "$it, $joined" } ?: joined
        }
    }
    return flattened
}
