package melox.network

import melox.account.NeteaseSessionStore
import melox.library.NeteasePlaylistSummary
import melox.model.SearchSong
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

data class MeloXAlbumSummary(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val artistText: String,
    val type: String?,
    val publishTime: Long? = null,
    val size: Int? = null,
)

data class MeloXAlbumDetail(
    val album: MeloXAlbumSummary,
    val description: String?,
    val songs: List<SearchSong>,
    val subscribed: Boolean?,
)

data class MeloXArtistDetail(
    val id: Long,
    val name: String,
    val artworkUrl: String?,
    val coverUrl: String?,
    val aliases: List<String>,
    val description: String?,
    val musicSize: Int,
    val albumSize: Int,
    val mvSize: Int,
    val followed: Boolean?,
    val hotSongs: List<SearchSong>,
    val albums: List<MeloXAlbumSummary>,
)

data class MeloXLibraryArtistSummary(val id: Long, val name: String, val artworkUrl: String?, val aliases: List<String>)

class NeteaseCollectionDetailsClient(
    cookieProvider: () -> String,
    httpClient: OkHttpClient = MeloXHttpClient.shared,
) {
    private val cookieProvider = cookieProvider
    private val eapi = NeteaseAuthenticatedEapi(cookieProvider, httpClient)
    private val weapi = NeteaseAuthenticatedWeapi(cookieProvider, httpClient)

    suspend fun subscribedAlbums(): List<MeloXAlbumSummary> = withContext(Dispatchers.IO) {
        val source = subscribedCollection("/api/album/sublist").optJSONArray("data") ?: JSONArray()
        buildList { for (index in 0 until source.length()) parseAlbum(source.optJSONObject(index))?.let(::add) }
    }

    suspend fun subscribedArtists(): List<MeloXLibraryArtistSummary> = withContext(Dispatchers.IO) {
        val source = subscribedCollection("/api/artist/sublist").optJSONArray("data") ?: JSONArray()
        buildList { for (index in 0 until source.length()) {
            val artist = source.optJSONObject(index) ?: continue
            val id = artist.optLong("id").takeIf { it > 0 } ?: continue
            val aliases = artist.optJSONArray("alias") ?: JSONArray()
            add(MeloXLibraryArtistSummary(id, artist.optString("name"), secure(artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") }),
                buildList { for (i in 0 until aliases.length()) aliases.optString(i).takeIf(String::isNotBlank)?.let(::add) }))
        } }
    }

    private fun subscribedCollection(path: String): JSONObject {
        check(NeteaseSessionStore.containsMusicU(cookieProvider())) { "请先登录网易云音乐" }
        val data = JSONObject().put("limit", 2000).put("offset", 0).put("total", true)
        return try { weapi.post(path, data) }
        catch (failure: IOException) {
            if (!failure.message.orEmpty().contains("空响应")) throw failure
            eapi.post(path, data, authenticated = true)
        }
    }

    /** DesktopDiscoveryView's toplists section: NeteaseAPI.toplists(). */
    suspend fun toplists(): List<NeteasePlaylistSummary> = withContext(Dispatchers.IO) {
        val response = eapi.post("/api/toplist", JSONObject(), authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()))
        val list = response.optJSONArray("list") ?: JSONArray()
        buildList {
            for (index in 0 until list.length()) {
                val value = list.optJSONObject(index) ?: continue
                val id = value.optLong("id", -1L)
                if (id <= 0L) continue
                add(NeteasePlaylistSummary(
                    id = id,
                    name = value.optString("name").ifBlank { "排行榜" },
                    coverUrl = secure(value.optString("coverImgUrl").takeIf(String::isNotBlank)
                        ?: value.optString("picUrl").takeIf(String::isNotBlank)),
                    trackCount = value.optInt("trackCount", 0),
                    creatorName = value.optJSONObject("creator")?.optString("nickname").orEmpty(),
                    playCount = value.optLong("playCount", 0L),
                    description = value.optString("description").takeIf(String::isNotBlank),
                    copywriter = value.optString("updateFrequency").takeIf(String::isNotBlank),
                ))
            }
        }
    }

    /** DesktopDiscoveryView's circular popular-artist grid: NeteaseAPI.topArtists(). */
    suspend fun topArtists(limit: Int = 18): List<MeloXLibraryArtistSummary> = withContext(Dispatchers.IO) {
        val response = eapi.post("/api/toplist/artist", JSONObject().put("limit", limit.coerceIn(1, 100)),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()))
        val artists = response.optJSONObject("list")?.optJSONArray("artists") ?: JSONArray()
        buildList {
            for (index in 0 until artists.length()) {
                val artist = artists.optJSONObject(index) ?: continue
                val id = artist.optLong("id").takeIf { it > 0 } ?: continue
                val aliases = artist.optJSONArray("alias") ?: artist.optJSONArray("transNames") ?: JSONArray()
                add(MeloXLibraryArtistSummary(id, artist.optString("name"),
                    secure(artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") }),
                    buildList { for (i in 0 until aliases.length()) aliases.optString(i).takeIf(String::isNotBlank)?.let(::add) }))
            }
        }
    }

    suspend fun newAlbums(limit: Int = 16, area: String = "ALL"): List<MeloXAlbumSummary> = withContext(Dispatchers.IO) {
        val response = eapi.post("/api/album/new", JSONObject().put("limit", limit).put("offset", 0).put("total", true).put("area", area),
            authenticated = NeteaseSessionStore.containsMusicU(cookieProvider()))
        val values = response.optJSONArray("albums") ?: JSONArray()
        (0 until values.length()).mapNotNull { parseAlbum(values.optJSONObject(it)) }
    }

    suspend fun albumDetail(id: Long): MeloXAlbumDetail = withContext(Dispatchers.IO) {
        val logged = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi.post("/api/v1/album/$id", authenticated = logged)
        val value = response.optJSONObject("album") ?: throw IOException("网易云没有返回专辑信息")
        val album = parseAlbum(value) ?: throw IOException("无法解析专辑")
        val songs = parseSongs(response.optJSONArray("songs"))
        val subscribed = if (logged) runCatching {
            val data = JSONObject().put("id", id)
            val dynamic = try {
                weapi.post("/api/album/detail/dynamic", data)
            } catch (error: IOException) {
                if (!error.message.orEmpty().contains("空响应")) throw error
                eapi.post("/api/album/detail/dynamic", data)
            }
            dynamic.optBoolean("isSub", false)
        }.getOrNull() else null
        MeloXAlbumDetail(
            album = album,
            description = value.optString("description").takeIf(String::isNotBlank)
                ?: value.optString("briefDesc").takeIf(String::isNotBlank),
            songs = songs,
            subscribed = subscribed,
        )
    }

    suspend fun setAlbumSubscribed(id: Long, subscribed: Boolean) = withContext(Dispatchers.IO) {
        val path = if (subscribed) "/api/album/sub" else "/api/album/unsub"
        val data = JSONObject().put("id", id)
        try {
            weapi.post(path, data)
        } catch (error: IOException) {
            if (!error.message.orEmpty().contains("空响应")) throw error
            eapi.post(path, data)
        }
        Unit
    }

    suspend fun artistDetail(id: Long): MeloXArtistDetail = withContext(Dispatchers.IO) {
        val logged = NeteaseSessionStore.containsMusicU(cookieProvider())
        val songsResponse = eapi.post("/api/v1/artist/$id", authenticated = logged)
        val legacyArtist = songsResponse.optJSONObject("artist")
            ?: throw IOException("网易云没有返回歌手信息")
        val headData = runCatching {
            eapi.post(
                "/api/artist/head/info/get",
                JSONObject().put("id", id.toString()),
                logged,
            ).optJSONObject("data")
        }.getOrNull()
        val richArtist = headData?.optJSONObject("artist")
        val artist = richArtist ?: legacyArtist
        val albumsResponse = eapi.post(
            "/api/artist/albums/$id",
            JSONObject().put("limit", 100).put("offset", 0).put("total", true),
            logged,
        )
        val aliasesJson = artist.optJSONArray("alias") ?: artist.optJSONArray("transNames") ?: JSONArray()
        val aliases = buildList {
            for (index in 0 until aliasesJson.length()) {
                aliasesJson.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
        val albumsJson = albumsResponse.optJSONArray("hotAlbums") ?: JSONArray()
        val albums = buildList {
            for (index in 0 until albumsJson.length()) parseAlbum(albumsJson.optJSONObject(index))?.let(::add)
        }
        MeloXArtistDetail(
            id = artist.optLong("id", id),
            name = artist.optString("name").ifBlank { "未知歌手" },
            artworkUrl = secure(
                artist.optString("avatar").takeIf(String::isNotBlank)
                    ?: artist.optString("picUrl").takeIf(String::isNotBlank)
                    ?: artist.optString("img1v1Url").takeIf(String::isNotBlank),
            ),
            coverUrl = secure(
                artist.optString("cover").takeIf(String::isNotBlank)
                    ?: headData?.optJSONObject("user")?.optString("backgroundUrl")?.takeIf(String::isNotBlank)
                    ?: artist.optString("picUrl").takeIf(String::isNotBlank),
            ),
            aliases = aliases,
            description = artist.optString("briefDesc").takeIf(String::isNotBlank),
            musicSize = artist.optInt("musicSize", songsResponse.optJSONArray("hotSongs")?.length() ?: 0),
            albumSize = artist.optInt("albumSize", albums.size),
            mvSize = artist.optInt("mvSize", 0),
            followed = headData?.optJSONObject("user")?.takeIf { it.has("followed") }?.optBoolean("followed"),
            hotSongs = parseSongs(songsResponse.optJSONArray("hotSongs")),
            albums = albums,
        )
    }

    suspend fun setArtistFollowed(id: Long, followed: Boolean) = withContext(Dispatchers.IO) {
        val path = if (followed) "/api/artist/sub" else "/api/artist/unsub"
        eapi.post(
            path,
            JSONObject().put("artistId", id).put("artistIds", "[$id]"),
            authenticated = true,
        )
        Unit
    }

    suspend fun artistSongs(id: Long, limit: Int, offset: Int): List<SearchSong> = withContext(Dispatchers.IO) {
        val logged = NeteaseSessionStore.containsMusicU(cookieProvider())
        val response = eapi.post(
            "/api/v1/artist/songs",
            JSONObject().put("id", id).put("order", "hot")
                .put("limit", limit).put("offset", offset).put("total", true),
            logged,
        )
        val songs = response.optJSONArray("songs") ?: JSONArray()
        parseSongs(songs)
    }

    private fun parseSongs(values: JSONArray?): List<SearchSong> = buildList {
        val songs = values ?: JSONArray()
        for (index in 0 until songs.length()) parseSong(songs.optJSONObject(index))?.let(::add)
    }

    private fun parseAlbum(value: JSONObject?): MeloXAlbumSummary? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistsJson = value.optJSONArray("artists") ?: value.optJSONArray("ar") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsJson.length()) {
                artistsJson.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ")
        return MeloXAlbumSummary(
            id = id,
            name = value.optString("name").ifBlank { "未命名专辑" },
            artworkUrl = secure(
                value.optString("picUrl").takeIf(String::isNotBlank)
                    ?: value.optString("blurPicUrl").takeIf(String::isNotBlank),
            ),
            artistText = artists.ifBlank {
                value.optJSONObject("artist")?.optString("name").orEmpty().ifBlank { "未知歌手" }
            },
            type = value.optString("type").takeIf(String::isNotBlank),
            publishTime = value.optLong("publishTime").takeIf { it > 0 },
            size = value.optInt("size", -1).takeIf { it >= 0 },
        )
    }

    private fun parseSong(value: JSONObject?): SearchSong? {
        value ?: return null
        val id = value.optLong("id", -1L)
        if (id <= 0L) return null
        val artistsJson = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsJson.length()) {
                artistsJson.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
            }
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            albumId = album?.optLong("id")?.takeIf { it > 0L },
            artistId = artistsJson.optJSONObject(0)?.optLong("id")?.takeIf { it > 0L },
            artworkUrl = secure(
                album?.optString("picUrl")?.takeIf(String::isNotBlank)
                    ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank),
            ),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
            trackNumber = value.optInt("no").takeIf { it > 0 },
            disc = value.optString("cd").takeIf(String::isNotBlank),
        )
    }

    private fun secure(value: String?): String? = value?.let {
        if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it
    }
}
