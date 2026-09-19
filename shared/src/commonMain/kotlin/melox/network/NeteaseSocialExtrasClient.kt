package melox.network

import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

enum class MeloXUserPlayRecordPeriod(val apiValue: Int) { Week(1), AllTime(0) }
data class MeloXUserPlayRecord(val song: SearchSong, val playCount: Int, val score: Int?)
data class MeloXCommentRepliesPage(val ownerComment: MeloXMusicComment?, val replies: List<MeloXMusicComment>, val totalCount: Int, val hasMore: Boolean, val nextTime: Long)
data class MeloXCommentsPage(val hotComments: List<MeloXMusicComment>, val comments: List<MeloXMusicComment>, val totalCount: Int, val hasMore: Boolean, val nextOffset: Int, val beforeTime: Long)

class NeteaseSocialExtrasClient(cookieProvider: () -> String, httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared) {
    private val cookieProvider = cookieProvider
    private val eapi = NeteaseAuthenticatedEapi(cookieProvider, httpClient)
    private val weapi = NeteaseAuthenticatedWeapi(cookieProvider, httpClient)

    suspend fun userPlayRecords(userId: Long, period: MeloXUserPlayRecordPeriod): List<MeloXUserPlayRecord> = withContext(Dispatchers.IO) {
        val response = socialRead("/api/v1/play/record", JSONObject().put("uid", userId).put("type", period.apiValue), allowGuest = true)
        val values = when (period) { MeloXUserPlayRecordPeriod.Week -> response.optJSONArray("weekData"); MeloXUserPlayRecordPeriod.AllTime -> response.optJSONArray("allData") } ?: JSONArray()
        buildList {
            for (index in 0 until values.length()) { val value = values.optJSONObject(index) ?: continue; val song = parseSong(value.optJSONObject("song")) ?: continue
                add(MeloXUserPlayRecord(song, value.optInt("playCount", 0).coerceAtLeast(0), value.opt("score")?.let { if (it is Number) it.toInt() else it.toString().toIntOrNull() })) }
        }.sortedByDescending(MeloXUserPlayRecord::playCount)
    }

    suspend fun songComments(songId: Long, offset: Int = 0, beforeTime: Long = 0L, limit: Int = 20): MeloXCommentsPage = withContext(Dispatchers.IO) {
        val response = socialRead("/api/v1/resource/comments/R_SO_4_$songId", JSONObject().put("rid", songId).put("limit", limit.coerceIn(1, 100)).put("offset", offset.coerceAtLeast(0)).put("beforeTime", beforeTime), allowGuest = true)
        val hot = if (offset == 0) parseComments(response.optJSONArray("hotComments")) else emptyList(); val comments = parseComments(response.optJSONArray("comments"))
        val total = response.optInt("total", offset + comments.size).coerceAtLeast(offset + comments.size); val more = response.optBoolean("more", offset + comments.size < total) && comments.isNotEmpty()
        val raw = response.optJSONArray("comments"); val lastTime = raw?.takeIf { it.length() > 0 }?.optJSONObject(raw.length() - 1)?.optLong("time", beforeTime) ?: beforeTime
        MeloXCommentsPage(hot, comments, total, more, offset + comments.size, if (offset + comments.size >= 5_000) lastTime else 0L)
    }

    suspend fun songCommentReplies(songId: Long, parentCommentId: Long, time: Long = -1L, limit: Int = 20): MeloXCommentRepliesPage = withContext(Dispatchers.IO) {
        val response = socialRead("/api/resource/comment/floor/get", JSONObject().put("parentCommentId", parentCommentId).put("threadId", "R_SO_4_$songId").put("time", time).put("limit", limit.coerceIn(1, 100)), allowGuest = true)
        val data = response.optJSONObject("data") ?: JSONObject(); val owner = parseComment(data.optJSONObject("ownerComment"))?.first; val values = data.optJSONArray("comments") ?: JSONArray(); var nextTime = time
        val replies = buildList { for (index in 0 until values.length()) { val parsed = parseComment(values.optJSONObject(index)) ?: continue; add(parsed.first); if (parsed.second > 0L) nextTime = parsed.second } }
        MeloXCommentRepliesPage(owner, replies, data.optInt("totalCount", data.optInt("total", replies.size)).coerceAtLeast(replies.size), data.optBoolean("hasMore", false) && replies.isNotEmpty(), nextTime)
    }

    suspend fun sendSongToUser(songId: Long, userId: Long, message: String = "") = sendResourceToUser("song", songId, userId, message)
    suspend fun shareSongToTimeline(songId: Long, message: String = "") = shareResourceToTimeline("song", songId, message)
    suspend fun sendResourceToUser(resourceType: String, resourceId: Long, userId: Long, message: String = "") = withContext(Dispatchers.IO) {
        require(resourceType in setOf("song", "playlist", "album")) { "不支持的网易云资源类型" }
        eapi.post("/api/msg/private/send", JSONObject().put("id", resourceId).put("msg", message).put("type", resourceType).put("userIds", "[$userId]")); Unit
    }
    suspend fun shareResourceToTimeline(resourceType: String, resourceId: Long, message: String = "") = withContext(Dispatchers.IO) {
        require(resourceType in setOf("song", "playlist")) { "网易云音乐暂不支持将此类型的内容转发到动态" }
        eapi.post("/api/share/friends/resource", JSONObject().put("type", resourceType).put("msg", message).put("id", resourceId)); Unit
    }

    private fun socialRead(path: String, data: JSONObject, allowGuest: Boolean = false): JSONObject {
        val loggedIn = NeteaseSessionStore.containsMusicU(cookieProvider()); if (!loggedIn) { if (!allowGuest) throw IOException("请先登录网易云音乐"); return eapi.post(path, data, false) }
        return try { weapi.post(path, data) } catch (error: IOException) { if (!error.message.orEmpty().contains("空响应")) throw error; eapi.post(path, data) }
    }
    private fun parseComments(values: JSONArray?): List<MeloXMusicComment> = buildList { val source = values ?: JSONArray(); for (index in 0 until source.length()) parseComment(source.optJSONObject(index))?.first?.let(::add) }
    private fun parseComment(value: JSONObject?): Pair<MeloXMusicComment, Long>? { value ?: return null; val id = value.optLong("commentId", -1L); if (id <= 0L) return null; val user = value.optJSONObject("user"); val time = value.optLong("time", -1L)
        return MeloXMusicComment(id, user?.optString("nickname").orEmpty().ifBlank { "网易云用户" }, secure(user?.optString("avatarUrl")?.takeIf(String::isNotBlank)), value.optString("content").ifBlank { "…" }, value.optLong("likedCount", 0L), value.optString("timeStr"), value.optInt("replyCount", value.optJSONArray("beReplied")?.length() ?: 0).coerceAtLeast(0)) to time }
    private fun parseSong(value: JSONObject?): SearchSong? { value ?: return null; val id = value.optLong("id", -1L); if (id <= 0L) return null; val a = value.optJSONArray("ar") ?: value.optJSONArray("artists") ?: JSONArray(); val artists = buildList { for (i in 0 until a.length()) a.optJSONObject(i)?.optString("name")?.takeIf(String::isNotBlank)?.let(::add) }.joinToString(" / "); val album = value.optJSONObject("al") ?: value.optJSONObject("album"); return SearchSong(id, value.optString("name").ifBlank { "未知歌曲" }, artists.ifBlank { "未知歌手" }, album?.optString("name").orEmpty(), secure(album?.optString("picUrl")?.takeIf(String::isNotBlank)), value.optLong("dt", value.optLong("duration", 0L)).coerceAtLeast(0L)) }
    private fun secure(value: String?): String? = value?.let { if (it.startsWith("http://", true)) "https://${it.substringAfter("://")}" else it }
}
