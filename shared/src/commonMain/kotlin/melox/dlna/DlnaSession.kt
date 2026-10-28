package melox.dlna

import java.net.InetAddress
import java.net.URI
import java.net.DatagramSocket
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import melox.lan.*
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer

internal fun dlnaMimeType(source: String): String {
    // Native Windows paths are not URIs (backslashes/spaces and drive letters).
    // Keep the original path when URI parsing fails or produces no path.
    val path = runCatching { URI(source).path }.getOrNull() ?: source
    return when (path.substringAfterLast('.').lowercase()) {
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "m4a", "aac" -> "audio/mp4"
        "ogg", "opus" -> "audio/ogg"
        else -> "audio/mpeg"
    }
}

data class DlnaSnapshot(val device: DlnaDevice? = null, val busy: Boolean = false, val playing: Boolean = false,
    val positionMs: Long = 0, val durationMs: Long = 0, val volume: Float? = null, val error: String? = null,
    val sampledNanos: Long = System.nanoTime()) {
    fun displayedPosition() = (positionMs + if (playing) ((System.nanoTime() - sampledNanos) / 1_000_000).coerceIn(0, 3000) else 0)
        .coerceAtMost(durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE)
}

/** Process-owned real UPnP renderer session; a PLAYING response is required before pausing local audio. */
object DlnaSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val commands = Mutex()
    private val mutable = MutableStateFlow(DlnaSnapshot())
    val state = mutable.asStateFlow()
    val active get() = mutable.value.device != null
    private var source: DlnaMediaSource? = null
    private var requestedId: String? = null
    private var proxy: LanAudioRelay? = null
    private var polling: Job? = null
    private var switching: Job? = null
    private var desiredVolume: Float? = null
    private var settingVolume: Job? = null

    suspend fun cast(target: DlnaDevice): Unit = MeloXOutputArbiter.operations.withLock {
        withContext(Dispatchers.Main) { commands.withLock {
            mutable.update { it.copy(busy = true, error = null) }
            try {
                val media = currentSource(); requestedId = media.mediaId
                withContext(Dispatchers.IO) { MeloXLanRuntime.useLocal() }
                MeloXLanTogetherCoordinator.stop()
                MeloXLanSession.outputStopped(LanOutputMode.Cast); MeloXLanSession.outputStopped(LanOutputMode.Together)
                if (MeloXLanRuntime.isGuest) withContext(Dispatchers.IO) { MeloXLanRuntime.stopGuestTogether() }
                if (mutable.value.device?.id != target.id) closeCurrent()
                install(target, media)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message ?: "DLNA 投放失败") }; throw failure }
            finally {
                mutable.update { it.copy(busy = false) }
                if (active) localTrackChanged(AudioPlayer.currentTrack.value?.let { "${it.id.source.storageValue}:${it.id.value}" })
            }
        } }
    }
    private suspend fun currentSource(): DlnaMediaSource {
        val q = PlaybackCommands.queue.value
        val track = MeloXLanRuntime.fromSong(q.songs.getOrNull(q.index) ?: error("当前没有可投放的歌曲"))
        val request = PlaybackCommands.playerController()?.playbackSourceForOutput()
            ?: withContext(Dispatchers.IO) { MeloXLanRuntime.resolve(track) }
        val mime = dlnaMimeType(request.uri)
        return DlnaMediaSource(request.uri, request.headers, track.title, track.artist, track.album, track.artworkUrl,
            track.durationMs, AudioPlayer.getCurrentPositionMs(), "${track.source}:${track.id}", mime)
    }
    private suspend fun install(target: DlnaDevice, media: DlnaMediaSource) {
        var nextProxy: LanAudioRelay? = null
        val direct = media.uri.startsWith("http") && media.headers.isEmpty()
        try {
            suspend fun start(useProxy: Boolean) {
                val url = if (useProxy) withContext(Dispatchers.IO) {
                    nextProxy?.close()
                    val renderer = InetAddress.getByName(URI(target.transport.controlUrl).host)
                    val bind = DatagramSocket().use { it.connect(renderer, 1900); it.localAddress }
                    val extension = when (media.mimeType) { "audio/wav" -> "wav"; "audio/flac" -> "flac"; "audio/mp4" -> "m4a"; else -> "mp3" }
                    val grant = LanAudioRelay(bind, renderer, listOf(LanTrack("dlna", media.mediaId, media.title, media.artist)),
                        { melox.playback.DesktopPlaybackRequest(media.uri, media.headers) },
                        LanRelayPolicy(media.mimeType, "/cast/${UUID.randomUUID()}.$extension", Long.MAX_VALUE, Long.MAX_VALUE, true))
                    nextProxy = grant
                    grant.resourceUrl(media.mediaId)
                } else media.uri
                withContext(Dispatchers.IO) {
                    runCatching { DlnaController.call(target.transport, "Stop") }
                    DlnaController.call(target.transport, "SetAVTransportURI", mapOf("CurrentURI" to url, "CurrentURIMetaData" to DlnaController.metadata(media, url)))
                }
                delay(350)
                withContext(Dispatchers.IO) { DlnaController.call(target.transport, "Play", mapOf("Speed" to "1")) }
                var confirmed = false
                repeat(6) { if (!confirmed) { delay(300); confirmed = withContext(Dispatchers.IO) {
                    DlnaController.call(target.transport, "GetTransportInfo")["CurrentTransportState"] == "PLAYING"
                } } }
                check(confirmed) { "设备未开始播放，可能不支持此音频格式或音频地址" }
            }
            try { start(!direct) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (direct) start(true) else throw failure }
            proxy?.close(); proxy = nextProxy; nextProxy = null; source = media
            mutable.value = DlnaSnapshot(target, busy = true, playing = true, durationMs = media.durationMs)
            MeloXLanRuntime.onPlayer { PlaybackCommands.playerController()?.pause() }
            if (media.positionMs > 1000) withContext(Dispatchers.IO) {
                runCatching { DlnaController.call(target.transport, "Seek", mapOf("Unit" to "REL_TIME", "Target" to DlnaController.time(media.positionMs))) }
                    .onSuccess { mutable.update { it.copy(positionMs = media.positionMs, sampledNanos = System.nanoTime()) } }
            }
            polling?.cancel(); polling = scope.launch { poll(target) }
            melox.platform.logInfo("MeloXDlna", "Renderer PLAYING confirmed; proxy=${proxy != null}")
        } finally { nextProxy?.close() }
    }
    private suspend fun poll(target: DlnaDevice) {
        var failures = 0
        while (mutable.value.device?.id == target.id) {
            delay(2000)
            try { commands.withLock {
                proxy?.failure?.let { error(it) }
                val before = mutable.value
                val values = withContext(Dispatchers.IO) { DlnaController.call(target.transport, "GetTransportInfo") to DlnaController.call(target.transport, "GetPositionInfo") }
                val volume = target.rendering?.let { service -> withContext(Dispatchers.IO) {
                    runCatching { DlnaController.call(service, "GetVolume", mapOf("Channel" to "Master"))["CurrentVolume"]?.toFloatOrNull()?.div(100f)?.coerceIn(0f, 1f) }.getOrNull()
                } }
                val position = DlnaController.millis(values.second["RelTime"])
                val duration = DlnaController.millis(values.second["TrackDuration"]).takeIf { it > 0 } ?: before.durationMs
                val playing = values.first["CurrentTransportState"] == "PLAYING"
                mutable.update { it.copy(playing = playing, positionMs = position, durationMs = duration, volume = volume, sampledNanos = System.nanoTime()) }
                failures = 0
                if (before.playing && !playing && values.first["CurrentTransportState"] == "STOPPED" && duration > 0 && position >= duration - 3000) next()
            } } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (++failures >= 3) {
                mutable.update { it.copy(error = "设备连接已断开：${failure.message.orEmpty()}") }
                scope.launch { disconnect() }; return
            } }
        }
    }
    fun localTrackChanged(id: String?) {
        if (!active || mutable.value.busy || id == null || id == source?.mediaId || id == requestedId) return
        requestedId = id
        MeloXLanRuntime.onPlayer { PlaybackCommands.playerController()?.pause() }
        switching?.cancel(); switching = scope.launch { mutable.value.device?.let { runCatching { cast(it) } } }
    }
    fun toggle() = transport(if (mutable.value.playing) "pause" else "play")
    fun transport(command: String, positionMs: Long = 0) = scope.launch {
        commands.withLock {
            val target = mutable.value.device ?: return@withLock
            try {
                val before = mutable.value
                withContext(Dispatchers.IO) { when (command) {
                    "play" -> DlnaController.call(target.transport, "Play", mapOf("Speed" to "1"))
                    "pause" -> DlnaController.call(target.transport, "Pause")
                    "stop" -> DlnaController.call(target.transport, "Stop")
                    "seek" -> DlnaController.call(target.transport, "Seek", mapOf("Unit" to "REL_TIME", "Target" to DlnaController.time(positionMs)))
                } }
                mutable.update { it.copy(positionMs = if (command == "seek") positionMs else before.displayedPosition(),
                    playing = when (command) { "play" -> true; "pause", "stop" -> false; else -> it.playing }, sampledNanos = System.nanoTime()) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
        }
    }
    fun next() { if (active) MeloXLanRuntime.onPlayer { PlaybackCommands.playerController()?.next() } }
    fun previous() {
        if (!active) return
        if (mutable.value.displayedPosition() >= 5000) transport("seek", 0)
        else MeloXLanRuntime.onPlayer { PlaybackCommands.playerController()?.let { it.seekToIndex((it.currentMediaItemIndex - 1).coerceAtLeast(0)) } }
    }
    fun changeVolume(value: Float) {
        if (mutable.value.device?.rendering == null) return
        desiredVolume = value.coerceIn(0f, 1f)
        if (settingVolume?.isActive == true) return
        settingVolume = scope.launch { while (desiredVolume != null) {
            val next = desiredVolume ?: break; desiredVolume = null
            commands.withLock { mutable.value.device?.rendering?.let { service ->
                try { withContext(Dispatchers.IO) { DlnaController.call(service, "SetVolume", mapOf("Channel" to "Master", "DesiredVolume" to (next * 100).toInt().toString())) }
                    mutable.update { it.copy(volume = next) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { mutable.update { it.copy(error = failure.message) } }
            } }; delay(100)
        } }
    }
    suspend fun disconnect(resumeLocal: Boolean = true) = withContext(Dispatchers.Main) {
        switching?.cancel(); switching = null
        commands.withLock {
        val before = mutable.value; closeCurrent()
        if (resumeLocal && before.device != null) MeloXLanRuntime.onPlayer {
            PlaybackCommands.playerController()?.let { it.seekTo(before.displayedPosition()); if (before.playing) it.play() }
        }
    } }
    private suspend fun closeCurrent() {
        polling?.cancel(); polling = null; settingVolume?.cancel(); settingVolume = null; desiredVolume = null
        val old = mutable.value.device; source = null; requestedId = null
        mutable.update { DlnaSnapshot(busy = it.busy, error = it.error) }
        if (old != null) withContext(Dispatchers.IO) { runCatching { DlnaController.call(old.transport, "Stop") } }
        proxy?.close(); proxy = null
    }
}
