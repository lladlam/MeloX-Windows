package melox.network

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

enum class MeloXListenTogetherCommandType(val wireValue: String) {
    Play("PLAY"),
    Pause("PAUSE"),
    Next("NEXT"),
    Previous("PREV"),
    GoTo("GOTO"),
    Progress("PROGRESS"),
}

data class MeloXListenTogetherSnapshot(
    val displaySongIds: List<Long>,
    val randomSongIds: List<Long>,
    val playMode: String?,
    val targetSongId: Long?,
    val formerSongId: Long?,
    val progressMs: Long,
    val isPlaying: Boolean,
    val commandUserId: String?,
    val clientSequence: Long,
    val serverSequence: Long,
) {
    val randomMode: Boolean
        get() = playMode?.uppercase()?.let { it.contains("RANDOM") || it.contains("SHUFFLE") } == true

    val playbackSongIds: List<Long>
        get() = if (randomMode && randomSongIds.isNotEmpty()) randomSongIds else displaySongIds
}

/** Complete Together transport mirroring MeloX's command and playlist protocol. */
class NeteaseListenTogetherTransport(
    cookieProvider: () -> String,
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    private val eapi = NeteaseAuthenticatedEapi(cookieProvider, httpClient)

    suspend fun playback(roomId: String): MeloXListenTogetherSnapshot = withContext(Dispatchers.IO) {
        val data = eapi.post(
            "/api/listen/together/sync/playlist/get",
            JSONObject().put("roomId", roomId),
        ).optJSONObject("data") ?: throw IOException("房间暂时没有播放数据")

        val playlist = data.optJSONObject("playlist") ?: JSONObject()
        val command = data.optJSONObject("playCommand") ?: JSONObject()
        val displayIds = parseSongList(playlist.opt("displayList"))
        val randomIds = parseSongList(playlist.opt("randomList"))
        val target = readIdentifier(command, "targetSongId", "songId")
        val former = readIdentifier(command, "formerSongId")
        val status = command.optString("playStatus").uppercase()

        MeloXListenTogetherSnapshot(
            displaySongIds = displayIds,
            randomSongIds = randomIds,
            playMode = playlist.optString("playMode").takeIf(String::isNotBlank),
            targetSongId = target,
            formerSongId = former,
            progressMs = readLong(command, "progress").coerceAtLeast(0L),
            isPlaying = status == "PLAY" || status == "PLAYING" || status.isBlank(),
            commandUserId = command.optString("userId").takeIf(String::isNotBlank),
            clientSequence = readLong(command, "clientSeq"),
            serverSequence = readLong(command, "serverSeq"),
        )
    }

    suspend fun reportPlaylist(
        roomId: String,
        userId: Long,
        version: Int,
        displaySongIds: List<Long>,
        randomSongIds: List<Long>,
    ) = withContext(Dispatchers.IO) {
        val display = displaySongIds.filter { it > 0L }.distinct()
        if (display.isEmpty()) return@withContext
        val random = randomSongIds.filter { it > 0L }.distinct().ifEmpty { display }
        val versionJson = JSONArray().put(JSONObject().put("userId", userId).put("version", version.coerceAtLeast(1)))
        val playlist = JSONObject()
            .put("commandType", "REPLACE")
            .put("version", versionJson)
            .put("anchorSongId", "")
            .put("anchorPosition", -1)
            .put("randomList", JSONArray(random.map(Long::toString)))
            .put("displayList", JSONArray(display.map(Long::toString)))

        val response = eapi.post(
            "/api/listen/together/sync/list/command/report",
            JSONObject().put("roomId", roomId).put("playlistParam", playlist.toString()),
        )
        validateAction(response, "同步一起听队列失败")
    }

    suspend fun reportCommand(
        roomId: String,
        commandType: MeloXListenTogetherCommandType,
        progressMs: Long,
        isPlaying: Boolean,
        formerSongId: Long?,
        targetSongId: Long,
        clientSequence: Int,
    ) = withContext(Dispatchers.IO) {
        if (targetSongId <= 0L) return@withContext
        val command = JSONObject()
            .put("commandType", commandType.wireValue)
            .put("progress", progressMs.coerceAtLeast(0L))
            .put("playStatus", if (isPlaying) "PLAY" else "PAUSE")
            .put("formerSongId", (formerSongId ?: -1L).toString())
            .put("targetSongId", targetSongId.toString())
            .put("clientSeq", clientSequence.coerceAtLeast(1))

        val response = eapi.post(
            "/api/listen/together/play/command/report",
            JSONObject().put("roomId", roomId).put("commandInfo", command.toString()),
        )
        validateAction(response, "同步一起听播放操作失败")
    }

    suspend fun heartbeat(
        roomId: String,
        songId: Long,
        isPlaying: Boolean,
        progressMs: Long,
    ) = withContext(Dispatchers.IO) {
        if (songId <= 0L) return@withContext
        val response = eapi.post(
            "/api/listen/together/heartbeat",
            JSONObject()
                .put("roomId", roomId)
                .put("songId", songId)
                .put("playStatus", if (isPlaying) "PLAY" else "PAUSE")
                .put("progress", progressMs.coerceAtLeast(0L)),
        )
        validateAction(response, "一起听心跳失败")
    }

    private fun validateAction(response: JSONObject, fallback: String) {
        val data = response.optJSONObject("data")
        if (data?.has("result") == true && !data.optBoolean("result", true)) {
            throw IOException(response.optString("message").ifBlank { fallback })
        }
        if (data?.has("success") == true && !data.optBoolean("success", true)) {
            throw IOException(response.optString("message").ifBlank { fallback })
        }
    }

    private fun parseSongList(value: Any?): List<Long> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("result") ?: JSONArray()
            else -> JSONArray()
        }
        val seen = linkedSetOf<Long>()
        for (index in 0 until array.length()) {
            val id = when (val item = array.opt(index)) {
                is Number -> item.toLong()
                is String -> item.toLongOrNull()
                is JSONObject -> readIdentifier(item, "id", "songId")
                else -> null
            }
            if (id != null && id > 0L) seen += id
        }
        return seen.toList()
    }

    private fun readIdentifier(value: JSONObject, vararg keys: String): Long? {
        keys.forEach { key ->
            val raw = value.opt(key)
            val parsed = when (raw) {
                is Number -> raw.toLong()
                is String -> raw.toLongOrNull()
                else -> null
            }
            if (parsed != null && parsed > 0L) return parsed
        }
        return null
    }

    private fun readLong(value: JSONObject, key: String): Long = when (val raw = value.opt(key)) {
        is Number -> raw.toLong()
        is String -> raw.toLongOrNull() ?: 0L
        else -> 0L
    }
}
