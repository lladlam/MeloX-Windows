package melox.cli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.dlna.DlnaSession
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.SearchCapability
import melox.playback.MeloXPlaybackOutput
import melox.playback.PlaybackCommands
import melox.playback.ProviderPlaybackCommands
import org.json.JSONArray
import org.json.JSONObject

/** Uses the real provider registry and the same main-thread controller as the UI. */
internal object DesktopCliCommands {
    suspend fun execute(request: JSONObject, active: () -> Boolean): JSONObject {
        val command = request.optString("command", "status")
        require(command in setOf("status", "queue", "play", "pause", "toggle", "next", "previous", "stop",
            "seek", "volume", "shuffle", "repeat", "search", "search-play")) { "未知命令：$command" }
        val tracks = if (command == "search" || command == "search-play") search(request) else emptyList()
        return withContext(Dispatchers.Main) {
            check(active()) { "CLI 已关闭" }
            if (command == "search") return@withContext JSONObject().put("tracks", JSONArray().apply {
                tracks.forEachIndexed { index, track ->
                    put(JSONObject().put("index", index).put("source", track.id.source.storageValue)
                        .put("id", track.id.value).put("title", track.title).put("artist", track.artistText))
                }
            })
            val player = PlaybackCommands.playerController()
            val value = request.optString("value", "")
            when (command) {
                "play" -> requireNotNull(player) { "播放队列为空" }.play()
                "pause" -> requireNotNull(player) { "播放队列为空" }.pause()
                "toggle" -> requireNotNull(player) { "播放队列为空" }.togglePlay()
                "next" -> requireNotNull(player) { "播放队列为空" }.next()
                "previous" -> requireNotNull(player) { "播放队列为空" }.previous()
                "stop" -> {
                    if (DlnaSession.active) DlnaSession.transport("stop")
                    else {
                        check(!melox.lan.MeloXLanRuntime.isTogether) { "一起听期间不支持 stop，请使用 pause" }
                        requireNotNull(player) { "播放队列为空" }.stop()
                    }
                }
                "seek" -> {
                    val position = value.toLongOrNull()
                    require(position != null && position >= 0) { "seek 需要非负毫秒数" }
                    requireNotNull(player) { "播放队列为空" }.seekTo(position)
                }
                "volume" -> {
                    val volume = value.toFloatOrNull()
                    require(volume != null && volume.isFinite() && volume in 0f..1f) { "volume 范围为 0–1" }
                    requireNotNull(player) { "播放队列为空" }.setVolume(volume)
                }
                "shuffle" -> {
                    require(value == "on" || value == "off") { "shuffle 使用 on/off" }
                    requireNotNull(player) { "播放队列为空" }.setShuffleEnabled(value == "on")
                }
                "repeat" -> {
                    val mode = when (value) {
                        "off" -> PlaybackCommands.REPEAT_OFF
                        "one" -> PlaybackCommands.REPEAT_ONE
                        "all" -> PlaybackCommands.REPEAT_ALL
                        else -> error("repeat 使用 off/one/all")
                    }
                    requireNotNull(player) { "播放队列为空" }.setRepeatMode(mode)
                }
                "search-play" -> {
                    val index = if (value.isEmpty()) 0 else value.toIntOrNull()
                    require(index != null && index in tracks.indices) { "搜索结果为空或 index 超出范围" }
                    ProviderPlaybackCommands.playQueue(tracks, tracks[index].id, selectedQueueIndex = index)
                }
            }
            status(command)
        }
    }

    private suspend fun search(request: JSONObject): List<MusicTrack> = withContext(Dispatchers.IO) {
        val sourceName = request.optString("source", "netease")
        val source = MusicSource.entries.firstOrNull { it.storageValue == sourceName } ?: error("未知音源：$sourceName")
        val query = request.optString("query", "").trim()
        require(query.isNotEmpty()) { "query 不能为空" }
        val provider = MeloXMusicProviders.create()[source] as? SearchCapability ?: error("该音源不支持搜索")
        provider.searchSongs(query, pageSize = 10).items
    }

    private fun status(command: String): JSONObject {
        val player = PlaybackCommands.playerController()
        val songs = player?.snapshot().orEmpty()
        val selected = songs.getOrNull(player?.currentMediaItemIndex ?: -1)
        val state = if (DlnaSession.active) MeloXPlaybackOutput.state.value else melox.player.AudioPlayer.state.value
        val remote = DlnaSession.state.value
        val playing = if (DlnaSession.active) remote.playing else state.isPlaying
        fun mediaId(index: Int) = songs[index].providerTrack?.id?.value ?: songs[index].id.toString()
        return JSONObject().put("command", command).put("playing", playing)
            .put("playWhenReady", if (DlnaSession.active) remote.playing else player?.playWhenReady ?: false)
            .put("state", when { state.isLoading -> 2; state.isFinished -> 4; selected != null -> 3; else -> 1 })
            .put("mediaId", selected?.providerTrack?.id?.value ?: selected?.id?.toString().orEmpty())
            .put("title", selected?.name.orEmpty()).put("artist", selected?.artists.orEmpty())
            .put("positionMs", if (DlnaSession.active) remote.displayedPosition() else melox.player.AudioPlayer.getCurrentPositionMs())
            .put("durationMs", (if (DlnaSession.active) remote.durationMs else melox.player.AudioPlayer.getDurationMs()).takeIf { it > 0 } ?: -1)
            .put("volume", if (DlnaSession.active) remote.volume ?: JSONObject.NULL else melox.player.AudioPlayer.volumeLevel.value)
            .put("shuffle", player?.shuffleEnabled?.value ?: false)
            .put("repeat", player?.repeatMode?.value ?: PlaybackCommands.REPEAT_OFF)
            .apply { if (command == "queue") put("queue", JSONArray().apply {
                songs.forEachIndexed { index, song -> put(JSONObject().put("index", index).put("mediaId", mediaId(index)).put("title", song.name)) }
            }) }
    }
}
