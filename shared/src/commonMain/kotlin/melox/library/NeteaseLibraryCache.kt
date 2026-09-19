package melox.library

import melox.platform.meloXDataDir
import melox.model.SearchSong
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Persistent metadata cache with one network refresh per cold-start process. */
class NeteaseLibraryCache {
    private val directory = File(meloXDataDir(), "netease_library_cache")

    suspend fun loadSnapshot(userId: Long): NeteaseLibrarySnapshot? = readJson(
        file = File(directory, "library_$userId.json"),
        decode = ::decodeSnapshot,
    )

    suspend fun saveSnapshot(userId: Long, snapshot: NeteaseLibrarySnapshot) {
        writeJson(File(directory, "library_$userId.json")) { encodeSnapshot(snapshot) }
    }

    suspend fun loadPlaylistDetail(playlistId: Long): NeteasePlaylistDetail? = readJson(
        file = File(directory, "playlist_$playlistId.json"),
        decode = ::decodePlaylistDetail,
    )

    suspend fun savePlaylistDetail(playlistId: Long, detail: NeteasePlaylistDetail) {
        writeJson(File(directory, "playlist_$playlistId.json")) { encodePlaylistDetail(detail) }
    }

    suspend fun loadHomeContent(cacheKey: String): NeteaseHomeContent? = readJson(File(directory, "home_${safeCacheKey(cacheKey)}.json")) { value ->
        NeteaseHomeContent(
            playlists = decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()),
            newSongs = decodeSongs(value.optJSONArray("newSongs") ?: JSONArray()),
            recentlyTrending = decodeSongs(value.optJSONArray("recentlyTrending") ?: JSONArray()),
            tailoredSongs = decodeSongs(value.optJSONArray("tailoredSongs") ?: JSONArray()),
            chartPlaylists = decodePlaylists(value.optJSONArray("chartPlaylists") ?: JSONArray()),
            radarPlaylists = decodePlaylists(value.optJSONArray("radarPlaylists") ?: JSONArray()),
            personalPlaylists = decodePlaylists(value.optJSONArray("personalPlaylists") ?: JSONArray()),
            regionalSongs = decodeSongs(value.optJSONArray("regionalSongs") ?: JSONArray()),
            roamingSongs = decodeSongs(value.optJSONArray("roamingSongs") ?: JSONArray()),
            similarSongs = decodeSongs(value.optJSONArray("similarSongs") ?: JSONArray()),
            podcasts = decodeHomePodcasts(value.optJSONArray("podcasts") ?: JSONArray()),
        )
    }
    suspend fun saveHomeContent(cacheKey: String, content: NeteaseHomeContent) {
        writeJson(File(directory, "home_${safeCacheKey(cacheKey)}.json")) { JSONObject()
            .put("playlists", encodePlaylists(content.playlists)).put("newSongs", encodeSongs(content.newSongs))
            .put("recentlyTrending", encodeSongs(content.recentlyTrending)).put("tailoredSongs", encodeSongs(content.tailoredSongs))
            .put("chartPlaylists", encodePlaylists(content.chartPlaylists))
            .put("radarPlaylists", encodePlaylists(content.radarPlaylists)).put("personalPlaylists", encodePlaylists(content.personalPlaylists))
            .put("regionalSongs", encodeSongs(content.regionalSongs)).put("roamingSongs", encodeSongs(content.roamingSongs))
            .put("similarSongs", encodeSongs(content.similarSongs)).put("podcasts", encodeHomePodcasts(content.podcasts)) }
    }

    suspend fun loadExplore(category: String): List<NeteasePlaylistSummary>? = readJson(
        File(directory, "explore_${category.hashCode()}.json"),
    ) { value -> decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()) }

    suspend fun saveExplore(category: String, playlists: List<NeteasePlaylistSummary>) {
        writeJson(
            File(directory, "explore_${category.hashCode()}.json"),
        ) { JSONObject().put("playlists", encodePlaylists(playlists)) }
    }

    private suspend fun <T> readJson(file: File, decode: (JSONObject) -> T): T? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!file.isFile) return@runCatching null
                if (System.currentTimeMillis() - file.lastModified() > MAX_CACHE_AGE_MS) {
                    file.delete()
                    return@runCatching null
                }
                decode(JSONObject(file.readText()))
            }.getOrNull()
        }

    private suspend fun writeJson(file: File, value: () -> JSONObject) = withContext(Dispatchers.IO) {
        directory.mkdirs()
        val encoded = value().toString()
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(encoded)
        if (!temporary.renameTo(file)) {
            file.writeText(encoded)
            temporary.delete()
        }
        pruneCache()
    }

    private fun pruneCache() {
        val files = directory.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
            .sortedByDescending(File::lastModified)
        var retainedBytes = 0L
        files.forEachIndexed { index, cached ->
            retainedBytes += cached.length()
            if (index >= MAX_CACHE_FILES || retainedBytes > MAX_CACHE_BYTES) cached.delete()
        }
    }

    companion object {
        private val refreshedLibraries = mutableSetOf<Long>()
        private val refreshedPlaylists = mutableSetOf<Long>()
        private val refreshedHomes = mutableSetOf<String>()
        private val refreshedExplore = mutableSetOf<String>()

        /** Returns true only for the first automatic refresh in this app process. */
        @Synchronized
        fun beginLibraryColdStartRefresh(userId: Long): Boolean =
            boundedAdd(refreshedLibraries, userId)

        /** Each opened playlist is refreshed at most once in this app process. */
        @Synchronized
        fun beginPlaylistColdStartRefresh(playlistId: Long): Boolean =
            boundedAdd(refreshedPlaylists, playlistId)

        @Synchronized
        fun beginHomeColdStartRefresh(cacheKey: String): Boolean = boundedAdd(refreshedHomes, cacheKey)

        @Synchronized
        fun beginExploreColdStartRefresh(category: String): Boolean = boundedAdd(refreshedExplore, category)

        private fun <T> boundedAdd(values: MutableSet<T>, value: T): Boolean {
            if (!values.add(value)) return false
            while (values.size > MAX_REFRESH_KEYS) values.remove(values.first())
            return true
        }

        private const val MAX_CACHE_FILES = 128
        private const val MAX_CACHE_BYTES = 32L * 1024L * 1024L
        private const val MAX_CACHE_AGE_MS = 30L * 24L * 60L * 60L * 1_000L
        private const val MAX_REFRESH_KEYS = 256
    }
}

private fun encodeSnapshot(value: NeteaseLibrarySnapshot) = JSONObject().put("playlists", encodePlaylists(value.playlists)).put("likedSongs", encodeSongs(value.likedSongs)).put("recentSongs", encodeSongs(value.recentSongs)).put("likedPlaylistId", value.likedPlaylistId)
private fun decodeSnapshot(value: JSONObject) = NeteaseLibrarySnapshot(decodePlaylists(value.optJSONArray("playlists") ?: JSONArray()), decodeSongs(value.optJSONArray("likedSongs") ?: JSONArray()), decodeSongs(value.optJSONArray("recentSongs") ?: JSONArray()), value.optLong("likedPlaylistId", -1L).takeIf { it > 0L })

private fun encodePlaylistDetail(value: NeteasePlaylistDetail) = JSONObject()
    .put("summary", encodePlaylist(value.summary))
    .put("songs", encodeSongs(value.songs))

private fun decodePlaylistDetail(value: JSONObject): NeteasePlaylistDetail {
    val summary = decodePlaylist(value.getJSONObject("summary"))
    return NeteasePlaylistDetail(
        summary = summary,
        songs = decodeSongs(value.optJSONArray("songs") ?: JSONArray()),
    )
}

private fun encodePlaylists(values: List<NeteasePlaylistSummary>) = JSONArray().apply {
    values.forEach { put(encodePlaylist(it)) }
}

private fun decodePlaylists(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        values.optJSONObject(index)?.let { add(decodePlaylist(it)) }
    }
}

private fun encodePlaylist(value: NeteasePlaylistSummary) = JSONObject()
    .put("id", value.id)
    .put("name", value.name)
    .put("coverUrl", value.coverUrl)
    .put("trackCount", value.trackCount)
    .put("creatorName", value.creatorName)
    .put("creatorUserId", value.creatorUserId)
    .put("playCount", value.playCount)
    .put("description", value.description)

private fun decodePlaylist(value: JSONObject) = NeteasePlaylistSummary(
    id = value.getLong("id"),
    name = value.optString("name"),
    coverUrl = value.optNullableString("coverUrl"),
    trackCount = value.optInt("trackCount"),
    creatorName = value.optString("creatorName"),
    creatorUserId = value.optLong("creatorUserId", -1L).takeIf { it > 0L },
    playCount = value.optLong("playCount"),
    description = value.optNullableString("description"),
)

private fun encodeSongs(values: List<SearchSong>) = JSONArray().apply {
    values.forEach { song ->
        put(
            JSONObject()
                .put("id", song.id)
                .put("name", song.name)
                .put("artists", song.artists)
                .put("album", song.album)
                .put("artworkUrl", song.artworkUrl)
                .put("durationMs", song.durationMs),
        )
    }
}

private fun decodeSongs(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        val song = values.optJSONObject(index) ?: continue
        add(
            SearchSong(
                id = song.getLong("id"),
                name = song.optString("name"),
                artists = song.optString("artists"),
                album = song.optString("album"),
                artworkUrl = song.optNullableString("artworkUrl"),
                durationMs = song.optLong("durationMs"),
            ),
        )
    }
}

private fun encodeHomePodcasts(values: List<NeteaseHomePodcast>) = JSONArray().apply {
    values.forEach { podcast ->
        put(
            JSONObject()
                .put("id", podcast.id)
                .put("name", podcast.name)
                .put("artworkUrl", podcast.artworkUrl)
                .put("programId", podcast.programId ?: JSONObject.NULL)
                .put("playbackSong", podcast.playbackSong?.let { encodeSongs(listOf(it)).optJSONObject(0) } ?: JSONObject.NULL),
        )
    }
}
private fun decodeHomePodcasts(values: JSONArray) = buildList {
    for (index in 0 until values.length()) {
        val value = values.optJSONObject(index) ?: continue
        val id = value.optLong("id", -1L)
        if (id <= 0L) continue
        val song = value.optJSONObject("playbackSong")?.let { decodeSongs(JSONArray().put(it)).firstOrNull() }
        add(
            NeteaseHomePodcast(
                id,
                value.optString("name").ifBlank { "播客节目" },
                value.optNullableString("artworkUrl"),
                value.optLong("programId", 0L).takeIf { it > 0L },
                song,
            ),
        )
    }
}
private fun safeCacheKey(value: String): String = value.hashCode().toUInt().toString(16)
private fun JSONObject.optNullableString(name: String): String? = if (isNull(name)) null else optString(name).takeIf(String::isNotBlank)
