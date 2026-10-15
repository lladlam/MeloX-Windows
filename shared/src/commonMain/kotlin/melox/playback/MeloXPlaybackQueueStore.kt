package melox.playback

import melox.model.SearchSong
import melox.music.model.MusicAlbumRef
import melox.music.model.MusicArtistRef
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.platform.getPreferences
import org.json.JSONArray
import org.json.JSONObject
import melox.settings.MeloXSettingsPreferences

internal data class MeloXPersistedQueue(
    val items: List<QueueEntry>,
    val index: Int,
    val positionMs: Long,
    val playWhenReady: Boolean,
)

internal object MeloXPlaybackQueueStore {
    private const val PREFS = "melox_playback_queue"
    private const val QUEUE = "queue"
    private const val INDEX = "index"
    private const val POSITION = "position"
    private const val PLAY_WHEN_READY = "playWhenReady"

    fun save(player: MeloXDesktopPlayer) {
        if (!MeloXSettingsPreferences.boolean("playback_save_queue", true)) return
        val preferences = getPreferences(PREFS)
        if (player.mediaItemCount == 0) {
            // Restoring is asynchronous on desktop. Startup must not erase the
            // saved queue; explicit clearQueue performs the new upstream clear.
            return
        }
        val array = JSONArray()
        player.entries().forEach { entry ->
            val song = entry.song
            val track = song.providerTrack
            array.put(JSONObject().apply {
                put("id", song.id.toString())
                put("uri", playbackUri(entry))
                put("title", song.name)
                put("artist", song.artists)
                put("album", song.album)
                put("albumId", song.albumId ?: JSONObject.NULL)
                put("artistId", song.artistId ?: JSONObject.NULL)
                put("artwork", song.artworkUrl)
                put("origin", entry.origin)
                put("originalIndex", entry.originalIndex)
                put("entryId", entry.entryId)
                put("durationMs", song.durationMs)
                if (track != null) {
                    put("providerSource", track.id.source.storageValue)
                    put("providerId", track.id.value)
                    put("providerArtists", JSONArray(track.artists.map { it.name }))
                    (track.providerMetadata as? ProviderTrackMetadata.YouTube)?.let {
                        put("youtubeSetVideoId", it.setVideoId)
                    }
                    (track.providerMetadata as? ProviderTrackMetadata.Local)?.let {
                        put("localContentUri", it.contentUri)
                        put("localFileKey", it.fileKey)
                    }
                    (track.providerMetadata as? ProviderTrackMetadata.Jellyfin)?.let {
                        put("jellyfinServer", it.serverIdentity)
                        put("jellyfinPlaylist", it.playlistId)
                        put("jellyfinEntry", it.playlistEntryId)
                    }
                }
            })
        }
        preferences.putValues(mapOf(QUEUE to array.toString(), INDEX to player.currentMediaItemIndex.coerceAtLeast(0).toString(),
            POSITION to player.currentPosition.coerceAtLeast(0L).toString(), PLAY_WHEN_READY to player.playWhenReady.toString()))
    }

    fun clear() {
        getPreferences(PREFS).putValues(mapOf(QUEUE to null, INDEX to null, POSITION to null, PLAY_WHEN_READY to null))
    }

    fun savePosition(index: Int, positionMs: Long) {
        if (!MeloXSettingsPreferences.boolean("playback_save_queue", true)) return
        getPreferences(PREFS).putValues(
            mapOf(INDEX to index.coerceAtLeast(0).toString(), POSITION to positionMs.coerceAtLeast(0L).toString()),
        )
    }

    fun read(): MeloXPersistedQueue? {
        val prefs = getPreferences(PREFS)
        val raw = prefs.getString(QUEUE, null) ?: return null
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val items = buildList {
            for (index in 0 until array.length()) {
                val value = array.optJSONObject(index) ?: continue
                val id = value.optString("id").toLongOrNull() ?: continue
                val providerSource = value.optString("providerSource").takeIf(String::isNotBlank)
                val providerId = value.optString("providerId").takeIf(String::isNotBlank)
                val providerTrack = if (providerSource != null && providerId != null) {
                    val source = MusicSource.fromStorageValue(providerSource)
                    val names = value.optJSONArray("providerArtists")
                    val artists = buildList {
                        if (names != null) {
                            for (artistIndex in 0 until names.length()) {
                                names.optString(artistIndex).takeIf(String::isNotBlank)?.let {
                                    add(MusicArtistRef(name = it))
                                }
                            }
                        }
                        if (isEmpty()) {
                            value.optString("artist").takeIf(String::isNotBlank)?.let {
                                add(MusicArtistRef(name = it))
                            }
                        }
                    }
                    MusicTrack(
                        id = MusicResourceId(source, providerId),
                        title = value.optString("title"),
                        artists = artists,
                        album = value.optString("album").takeIf(String::isNotBlank)?.let { MusicAlbumRef(name = it) },
                        artworkUrl = value.optString("artwork").takeIf(String::isNotBlank),
                        durationMs = value.optLong("durationMs", 0L).takeIf { it > 0L },
                        providerMetadata = if (source == MusicSource.YouTubeMusic) ProviderTrackMetadata.YouTube(
                            providerId, value.optString("youtubeSetVideoId").takeIf { it.isNotBlank() && it != "null" },
                        ) else if (source == MusicSource.Jellyfin && value.optString("jellyfinServer").isNotBlank()) ProviderTrackMetadata.Jellyfin(
                            value.optString("jellyfinServer"),
                            value.optString("jellyfinPlaylist").takeIf { it.isNotBlank() && it != "null" },
                            value.optString("jellyfinEntry").takeIf { it.isNotBlank() && it != "null" },
                        ) else if (source == MusicSource.Local && value.optString("localContentUri").isNotBlank()) ProviderTrackMetadata.Local(
                            value.getString("localContentUri"), value.optString("localFileKey", providerId),
                        ) else ProviderTrackMetadata.Empty,
                    )
                } else {
                    null
                }
                add(
                    QueueEntry(
                        song = SearchSong(
                            id = id,
                            name = value.optString("title"),
                            artists = value.optString("artist"),
                            album = value.optString("album"),
                            albumId = value.optLong("albumId").takeIf { it > 0L },
                            artistId = value.optLong("artistId").takeIf { it > 0L },
                            artworkUrl = value.optString("artwork").takeIf(String::isNotBlank),
                            durationMs = value.optLong("durationMs", 0L),
                            providerTrack = providerTrack,
                        ),
                        origin = value.optString("origin").ifBlank { PlaybackCommands.QUEUE_ORIGIN_BASE },
                        originalIndex = value.optInt("originalIndex", PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET),
                        entryId = value.optString("entryId").ifBlank { java.util.UUID.randomUUID().toString() },
                    ),
                )
            }
        }
        return items.takeIf { it.isNotEmpty() }?.let {
            MeloXPersistedQueue(
                items = it,
                index = prefs.getInt(INDEX, 0).coerceIn(it.indices),
                positionMs = prefs.getLong(POSITION, 0L).coerceAtLeast(0L),
                playWhenReady = prefs.getBoolean(PLAY_WHEN_READY, false),
            )
        }
    }

    private fun playbackUri(entry: QueueEntry): String {
        val track = entry.song.providerTrack
        return if (track != null) {
            ProviderPlaybackResolver.uriForTrack(track, melox.audio.MusicQualityRuntime.selected.toCommonTier())
        } else {
            NeteasePlaybackResolver.uriForSong(
                songId = entry.song.id,
                title = entry.song.name,
                artist = entry.song.artists,
                durationMs = entry.song.durationMs,
            )
        }
    }
}
