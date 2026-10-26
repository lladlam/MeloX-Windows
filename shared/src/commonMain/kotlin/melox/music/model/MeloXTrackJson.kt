package melox.music.model

import org.json.JSONArray
import org.json.JSONObject

/** Full provider identity for app-owned collections. Credentials and resolved stream URLs are not stored. */
internal object MeloXTrackJson {
    fun encode(track: MusicTrack): JSONObject = JSONObject().apply {
        put("source", track.id.source.storageValue); put("id", track.id.value); put("title", track.title)
        put("artists", JSONArray().apply { track.artists.forEach { artist -> put(JSONObject().apply {
            put("name", artist.name); artist.id?.let { put("source", it.source.storageValue); put("id", it.value) }
        }) } })
        track.album?.let { album -> put("album", JSONObject().apply {
            put("name", album.name); put("artwork", album.artworkUrl)
            album.id?.let { put("source", it.source.storageValue); put("id", it.value) }
        }) }
        put("artwork", track.artworkUrl); put("durationMs", track.durationMs)
        put("availability", track.availability.name); put("bestQuality", track.bestQuality?.name); put("bestBitrate", track.bestBitrate)
        put("metadata", JSONObject().apply {
            when (val meta = track.providerMetadata) {
                ProviderTrackMetadata.Empty -> put("type", "empty")
                is ProviderTrackMetadata.Netease -> { put("type", "netease"); put("numericId", meta.numericId) }
                is ProviderTrackMetadata.QQMusic -> { put("type", "qq"); put("songMid", meta.songMid); put("mediaMid", meta.mediaMid); put("numericSongId", meta.numericSongId) }
                is ProviderTrackMetadata.Kugou -> { put("type", "kugou"); put("hash", meta.hash); put("albumAudioId", meta.albumAudioId); put("albumId", meta.albumId) }
                is ProviderTrackMetadata.Kuwo -> { put("type", "kuwo"); put("mid", meta.mid) }
                is ProviderTrackMetadata.AppleMusic -> { put("type", "apple"); put("catalogId", meta.catalogId); put("storefront", meta.storefront); put("previewUrl", meta.previewUrl) }
                is ProviderTrackMetadata.Bilibili -> { put("type", "bilibili"); put("bvid", meta.bvid); put("cid", meta.cid); put("aid", meta.aid); put("page", meta.page) }
                is ProviderTrackMetadata.Spotify -> { put("type", "spotify"); put("trackId", meta.trackId); put("isrc", meta.isrc) }
                is ProviderTrackMetadata.YouTube -> { put("type", "youtube"); put("videoId", meta.videoId); put("setVideoId", meta.setVideoId) }
                is ProviderTrackMetadata.Local -> { put("type", "local"); put("contentUri", meta.contentUri); put("fileKey", meta.fileKey) }
                is ProviderTrackMetadata.Jellyfin -> { put("type", "jellyfin"); put("serverIdentity", meta.serverIdentity); put("playlistId", meta.playlistId); put("playlistEntryId", meta.playlistEntryId) }
            }
        })
    }

    private fun JSONObject.text(key: String) = opt(key)?.takeUnless { it == JSONObject.NULL }?.toString()?.takeIf(String::isNotBlank)
    private fun JSONObject.resource(): MusicResourceId? {
        val source = MusicSource.entries.firstOrNull { it.storageValue == text("source") } ?: return null
        return text("id")?.let { MusicResourceId(source, it) }
    }

    fun decode(value: JSONObject): MusicTrack? {
        val id = value.resource() ?: return null
        val title = value.text("title") ?: return null
        val artists = value.optJSONArray("artists")?.let { array -> (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            item.text("name")?.let { MusicArtistRef(item.resource(), it) }
        } }.orEmpty()
        val album = value.optJSONObject("album")?.let { item -> item.text("name")?.let {
            MusicAlbumRef(item.resource(), it, item.text("artwork"))
        } }
        return MusicTrack(id, title, artists, album, value.text("artwork") ?: album?.artworkUrl,
            value.optLong("durationMs").takeIf { it > 0 },
            TrackAvailability.entries.firstOrNull { it.name == value.text("availability") } ?: TrackAvailability.Unknown,
            decodeMetadata(value.optJSONObject("metadata")),
            AudioQualityTier.entries.firstOrNull { it.name == value.text("bestQuality") },
            value.optInt("bestBitrate").takeIf { it > 0 })
    }

    private fun decodeMetadata(v: JSONObject?): ProviderTrackMetadata {
        v ?: return ProviderTrackMetadata.Empty
        return when (v.text("type")) {
            "netease" -> v.optLong("numericId").takeIf { it > 0 }?.let { ProviderTrackMetadata.Netease(it) }
            "qq" -> v.text("songMid")?.let { ProviderTrackMetadata.QQMusic(it, v.text("mediaMid"), v.optLong("numericSongId").takeIf { n -> n > 0 }) }
            "kugou" -> v.text("hash")?.let { ProviderTrackMetadata.Kugou(it, v.optLong("albumAudioId").takeIf { n -> n > 0 }, v.text("albumId")) }
            "kuwo" -> v.optLong("mid").takeIf { it > 0 }?.let { ProviderTrackMetadata.Kuwo(it) }
            "apple" -> v.text("catalogId")?.let { ProviderTrackMetadata.AppleMusic(it, v.text("storefront") ?: "us", v.text("previewUrl")) }
            "bilibili" -> v.text("bvid")?.let { bvid -> v.optLong("cid").takeIf { it > 0 }?.let { cid ->
                ProviderTrackMetadata.Bilibili(bvid, cid, v.optLong("aid").takeIf { it > 0 }, v.optInt("page", 1).coerceAtLeast(1))
            } }
            "spotify" -> v.text("trackId")?.let { ProviderTrackMetadata.Spotify(it, v.text("isrc")) }
            "youtube" -> v.text("videoId")?.let { ProviderTrackMetadata.YouTube(it, v.text("setVideoId")) }
            "local" -> v.text("contentUri")?.let { uri -> v.text("fileKey")?.let { ProviderTrackMetadata.Local(uri, it) } }
            "jellyfin" -> v.text("serverIdentity")?.let { ProviderTrackMetadata.Jellyfin(it, v.text("playlistId"), v.text("playlistEntryId")) }
            else -> null
        } ?: ProviderTrackMetadata.Empty
    }
}
