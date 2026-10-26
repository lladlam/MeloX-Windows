package melox.provider.qishui

import melox.music.model.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

/** Protocol mapping from Android 0.7.0 and guowenye/qishui-api (MIT, Copyright 2026 arrow). */
internal object QishuiParsing {
    fun objects(array: JSONArray?): List<JSONObject> = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    fun text(json: JSONObject?, key: String): String = json?.opt(key)?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
    private fun id(value: String) = value.takeIf(String::isNotBlank)?.let { MusicResourceId(MusicSource.Qishui, it) }

    fun image(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        is JSONArray -> image(value.opt(0))
        is JSONObject -> image(value.opt("url")) ?: image(value.opt("urls"))?.let { base ->
            val uri = text(value, "uri")
            if (uri.isNotBlank() && !base.endsWith(uri)) base + uri else base
        } ?: image(value.opt("uri"))
        else -> null
    }

    fun track(resource: JSONObject): MusicTrack? {
        val entity = resource.optJSONObject("entity") ?: resource
        val track = entity.optJSONObject("track_wrapper")?.optJSONObject("track") ?: entity.optJSONObject("track") ?: entity
        val trackId = id(text(track, "id")) ?: return null
        val name = text(track, "name").ifBlank { text(track, "trackName") }
        if (name.isBlank()) return null
        val album = track.optJSONObject("album")
        val artwork = image(album?.opt("url_cover")) ?: image(album?.opt("cover_url"))
        val bitrate = listOf("bit_rate", "bitrate", "max_bitrate", "br").maxOf { track.optInt(it, 0) }
            .takeIf { it > 0 }?.let { if (it < 10_000) it * 1_000 else it }
        return MusicTrack(trackId, name, objects(track.optJSONArray("artists")).map { artist ->
            MusicArtistRef(id(text(artist, "id")), text(artist, "name").ifBlank {
                text(artist, "simple_display_name").ifBlank { text(artist.optJSONObject("user_info"), "nickname") }
            })
        }.filter { it.name.isNotBlank() },
            album = album?.let { MusicAlbumRef(id(text(it, "id")), text(it, "name"), artwork) }, artworkUrl = artwork,
            durationMs = track.optLong("duration", track.optLong("duration_ms", 0)).takeIf { it > 0 },
            bestQuality = audioQualityForBitrate(bitrate), bestBitrate = bitrate)
    }

    fun playlist(resource: JSONObject): MusicPlaylistSummary? {
        val entity = resource.optJSONObject("entity") ?: resource
        val playlist = entity.optJSONObject("playlist") ?: entity
        val playlistId = id(text(playlist, "id")) ?: return null
        val title = text(playlist, "title").ifBlank { text(playlist, "name") }
        if (title.isBlank()) return null
        return MusicPlaylistSummary(playlistId, title,
            artworkUrl = image(playlist.opt("url_cover")) ?: image(playlist.opt("cover_url")),
            description = text(playlist, "description").ifBlank { text(playlist, "intro") }.takeIf(String::isNotBlank),
            trackCount = playlist.optInt("count_tracks", playlist.optInt("track_count", -1)).takeIf { it >= 0 })
    }

    fun searchResources(body: JSONObject): List<JSONObject> = objects(body.optJSONArray("result_groups"))
        .flatMap { objects(it.optJSONArray("data")) }.ifEmpty { objects(body.optJSONArray("tracks")) }

    data class Audio(val url: String, val bitrate: Int, val format: String?, val durationMs: Long?, val expiresAtMs: Long?, val preview: Boolean)

    fun audio(body: JSONObject, fullDurationMs: Long?, quality: AudioQualityTier): Audio? {
        val player = body.optJSONObject("track_player") ?: return null
        val model = player.optJSONObject("video_model") ?: runCatching { JSONObject(text(player, "video_model")) }.getOrNull() ?: return null
        val rawList = model.opt("video_list")
        val variants = when (rawList) {
            is JSONArray -> objects(rawList)
            is JSONObject -> rawList.keys().asSequence().mapNotNull { rawList.optJSONObject(it) }.toList()
            else -> emptyList()
        }
        if (text(model, "spade_a").isNotBlank() || text(player, "spade_a").isNotBlank()) return null
        val candidates = variants.filter { text(it, "spade_a").isBlank() }.mapNotNull { variant ->
            val url = listOf(text(variant, "main_url"), text(variant, "backup_url")).firstOrNull(::isAudioUrl) ?: return@mapNotNull null
            Triple(variant, url, variant.optJSONObject("video_meta")?.optInt("bitrate", 0) ?: 0)
        }.sortedBy { it.third }
        val selected = (if (quality == AudioQualityTier.Standard) candidates.firstOrNull() else candidates.lastOrNull()) ?: return null
        val duration = (model.optDouble("video_duration", 0.0) * 1000).toLong().takeIf { it > 0 }
        val expiry = player.optLong("expire_at", model.optLong("url_expire", 0)).takeIf { it > 0 }?.let { if (it < 10_000_000_000L) it * 1000 else it }
        return Audio(selected.second, selected.third, text(selected.first.optJSONObject("video_meta"), "vtype").takeIf(String::isNotBlank),
            duration, expiry, player.optBoolean("is_preview") || (duration != null && fullDurationMs != null && duration + 2000 < fullDurationMs))
    }

    fun isAudioUrl(value: String): Boolean {
        val url = value.toHttpUrlOrNull() ?: return false
        return url.isHttps && url.username.isEmpty() && url.password.isEmpty() &&
            listOf("douyin.com", "qishui.com", "douyinvod.com", "bytedance.com", "bytedance.net", "byted.org", "bytecdn.cn", "bytecdntp.com", "snssdk.com")
                .any { url.host == it || url.host.endsWith(".$it") }
    }
}
