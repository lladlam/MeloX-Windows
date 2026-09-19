package melox.library

import melox.model.SearchSong
import org.json.JSONArray
import org.json.JSONObject

internal data class NeteaseParsedHomeBlocks(
    val recommendedPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val recentlyTrending: List<SearchSong> = emptyList(),
    val tailoredSongs: List<SearchSong> = emptyList(),
    val chartPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val personalPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val radarPlaylists: List<NeteasePlaylistSummary> = emptyList(),
    val regionalSongs: List<SearchSong> = emptyList(),
    val roamingSongs: List<SearchSong> = emptyList(),
    val likedSongRecommendations: List<SearchSong> = emptyList(),
    val podcasts: List<NeteaseHomePodcast> = emptyList(),
)

internal object NeteaseHomeBlockParser {
    fun parse(response: JSONObject): NeteaseParsedHomeBlocks {
        val blocks = response.optJSONObject("data")?.optJSONArray("blocks") ?: JSONArray()
        var recommended = emptyList<NeteasePlaylistSummary>()
        var recent = emptyList<SearchSong>()
        var tailored = emptyList<SearchSong>()
        var charts = emptyList<NeteasePlaylistSummary>()
        var personal = emptyList<NeteasePlaylistSummary>()
        var radar = emptyList<NeteasePlaylistSummary>()
        var regional = emptyList<SearchSong>()
        var roaming = emptyList<SearchSong>()
        var likedRecommendations = emptyList<SearchSong>()
        var podcasts = emptyList<NeteaseHomePodcast>()

        for (index in 0 until blocks.length()) {
            val block = blocks.optJSONObject(index) ?: continue
            val code = block.optString("blockCode").uppercase()
            val title = normalizedTitle(block)
            val resources = resources(block)
            val blockPlaylists = resources.mapNotNull(::parsePlaylist).distinctBy(NeteasePlaylistSummary::id)
            val blockSongs = resources.mapNotNull(::parseSong).distinctBy(SearchSong::id)
            val blockPodcasts = resources.mapNotNull(::parsePodcast).distinctBy(NeteaseHomePodcast::id)

            when {
                (code == "HOMEPAGE_BLOCK_PLAYLIST_RCMD" || title == "推荐歌单") && recommended.isEmpty() && blockPlaylists.isNotEmpty() -> recommended = blockPlaylists
                title.contains("近期云村热播") && recent.isEmpty() && blockSongs.isNotEmpty() -> recent = blockSongs
                (code.contains("TOPLIST") || code.contains("RANK") || title == "排行榜") && charts.isEmpty() && blockPlaylists.isNotEmpty() -> charts = blockPlaylists
                (code == "HOMEPAGE_BLOCK_MGC_PLAYLIST" || title.contains("雷达歌单")) && radar.isEmpty() && blockPlaylists.isNotEmpty() -> radar = blockPlaylists
                title.contains("最近的热门歌曲") && regional.isEmpty() && blockSongs.isNotEmpty() -> regional = blockSongs
                title.contains("从你喜欢的歌开始漫游") && roaming.isEmpty() && blockSongs.isNotEmpty() -> roaming = blockSongs
                title.contains("根据你喜爱的歌曲推荐") && likedRecommendations.isEmpty() && blockSongs.isNotEmpty() -> likedRecommendations = blockSongs
                (code == "HOMEPAGE_VOICELIST_RCMD" || title.contains("根据你听过的热门节目推荐")) && podcasts.isEmpty() && blockPodcasts.isNotEmpty() -> podcasts = blockPodcasts
                title.endsWith("的歌单") && title != "推荐歌单" && personal.isEmpty() && blockPlaylists.isNotEmpty() -> personal = blockPlaylists
                title.startsWith("根据") && title.endsWith("为你推荐") && tailored.isEmpty() && blockSongs.isNotEmpty() -> tailored = blockSongs
                code == "HOMEPAGE_BLOCK_STYLE_RCMD" && recent.isEmpty() && blockSongs.isNotEmpty() -> recent = blockSongs
            }
        }
        return NeteaseParsedHomeBlocks(recommended, recent, tailored, charts, personal, radar, regional, roaming, likedRecommendations, podcasts)
    }

    private fun resources(block: JSONObject): List<JSONObject> = buildList {
        val direct = block.optJSONArray("resources") ?: JSONArray()
        for (index in 0 until direct.length()) direct.optJSONObject(index)?.let(::add)
        val creatives = block.optJSONArray("creatives") ?: JSONArray()
        for (creativeIndex in 0 until creatives.length()) {
            val values = creatives.optJSONObject(creativeIndex)?.optJSONArray("resources") ?: JSONArray()
            for (index in 0 until values.length()) values.optJSONObject(index)?.let(::add)
        }
    }

    private fun normalizedTitle(block: JSONObject): String {
        val ui = block.optJSONObject("uiElement")
        val raw = ui?.optJSONObject("subTitle")?.optString("title").orEmpty().ifBlank {
            ui?.optJSONObject("mainTitle")?.optString("title").orEmpty()
        }.ifBlank {
            val creatives = block.optJSONArray("creatives") ?: JSONArray()
            var found = ""
            for (index in 0 until creatives.length()) {
                found = creatives.optJSONObject(index)?.optJSONObject("uiElement")?.optJSONObject("mainTitle")?.optString("title").orEmpty()
                if (found.isNotBlank()) break
            }
            found
        }
        return raw.filterNot(Char::isWhitespace)
    }

    private fun parsePlaylist(value: JSONObject): NeteasePlaylistSummary? {
        val type = value.optString("resourceType").lowercase()
        if (type !in setOf("list", "playlist")) return null
        val id = longValue(value, "resourceId") ?: return null
        val ui = value.optJSONObject("uiElement")
        val ext = value.optJSONObject("resourceExtInfo")
        return NeteasePlaylistSummary(
            id = id,
            name = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "未命名歌单" },
            coverUrl = secure(ui?.optJSONObject("image")?.optString("imageUrl")?.takeIf(String::isNotBlank)),
            trackCount = ext?.optInt("trackCount", 0)?.coerceAtLeast(0) ?: 0,
            creatorName = ui?.optJSONObject("subTitle")?.optString("title").orEmpty(),
            playCount = ext?.optLong("playCount", 0L)?.coerceAtLeast(0L) ?: 0L,
            description = ui?.optJSONObject("subTitle")?.optString("title")?.takeIf(String::isNotBlank),
        )
    }

    private fun parseSong(value: JSONObject): SearchSong? {
        val ext = value.optJSONObject("resourceExtInfo")
        val full = ext?.optJSONObject("songData") ?: ext?.optJSONObject("song")
        if (full != null) return parseFullSong(full)
        if (!value.optString("resourceType").equals("song", true)) return null
        val id = longValue(value, "resourceId") ?: return null
        val ui = value.optJSONObject("uiElement")
        val artistsArray = ext?.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsArray.length()) artistsArray.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" / ")
        val title = ui?.optJSONObject("mainTitle")?.optString("title").orEmpty().ifBlank { "未知歌曲" }
        return SearchSong(id, title, artists.ifBlank { "未知歌手" }, "", secure(ui?.optJSONObject("image")?.optString("imageUrl")?.takeIf(String::isNotBlank)), 0L)
    }

    private fun parseFullSong(value: JSONObject): SearchSong? {
        val id = longValue(value, "id") ?: return null
        val artistsArray = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray()
        val artists = buildList {
            for (index in 0 until artistsArray.length()) artistsArray.optJSONObject(index)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" / ")
        val album = value.optJSONObject("al") ?: value.optJSONObject("album")
        return SearchSong(
            id = id,
            name = value.optString("name").ifBlank { "未知歌曲" },
            artists = artists.ifBlank { "未知歌手" },
            album = album?.optString("name").orEmpty(),
            artworkUrl = secure(album?.optString("picUrl")?.takeIf(String::isNotBlank) ?: album?.optString("blurPicUrl")?.takeIf(String::isNotBlank)),
            durationMs = value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L),
        )
    }

    private fun parsePodcast(value: JSONObject): NeteaseHomePodcast? {
        val type = value.optString("resourceType").lowercase()
        if (type !in setOf("voice", "program", "dj_program")) return null
        val ui = value.optJSONObject("uiElement")
        val program = value.optJSONObject("resourceExtInfo")?.optJSONObject("djProgram")
        val radio = program?.optJSONObject("radio")
        val radioId = radio?.let { longValue(it, "id") } ?: return null
        val programId = program.let { longValue(it, "id") }
        val programName = program.optString("name")
            .ifBlank { ui?.optJSONObject("mainTitle")?.optString("title").orEmpty() }
            .ifBlank { radio.optString("name") }
            .ifBlank { "播客节目" }
        val playbackSong = program.optJSONObject("mainSong")?.let(::parseSong)?.copy(
            name = programName,
            artists = program.optJSONObject("dj")?.optString("nickname").orEmpty().ifBlank { radio.optString("name") },
            album = radio.optString("name"),
            artworkUrl = secure(program.optString("coverUrl").takeIf(String::isNotBlank) ?: radio.optString("picUrl").takeIf(String::isNotBlank)),
        )
        return NeteaseHomePodcast(
            id = radioId,
            name = programName,
            artworkUrl = secure(program.optString("coverUrl").takeIf(String::isNotBlank) ?: radio.optString("picUrl").takeIf(String::isNotBlank) ?: ui?.optJSONObject("image")?.optString("imageUrl")?.takeIf(String::isNotBlank)),
            programId = programId,
            playbackSong = playbackSong,
        )
    }

    private fun longValue(value: JSONObject, key: String): Long? = when (val raw = value.opt(key)) {
        is Number -> raw.toLong().takeIf { it > 0L }
        is String -> raw.toLongOrNull()?.takeIf { it > 0L }
        else -> null
    }
    private fun secure(value: String?): String? = value?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
}
