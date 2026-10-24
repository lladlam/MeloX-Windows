package melox.lan

import java.io.IOException
import java.net.*
import java.util.concurrent.*
import javax.swing.SwingUtilities
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import melox.model.SearchSong
import melox.music.model.*
import melox.music.provider.PlaybackCapability
import melox.music.provider.MeloXLegacyUiBridge
import melox.playback.*
import melox.player.AudioPlayer
import org.json.JSONArray
import org.json.JSONObject

/** Process-owned LAN lifecycle, independent of either phone or desktop UI. */
object MeloXLanRuntime : LanHost {
    override val supportsBarrier = true
    val identity by lazy { LanIdentity() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow("Stopped")
    val status = _status.asStateFlow()
    @Volatile private var server: LanServer? = null
    @Volatile private var receiver: LanCastReceiver? = null
    @Volatile private var sender: LanCastSender? = null
    @Volatile private var connection: LanClient? = null
    private val connections = ConcurrentHashMap<String, LanClient>()
    internal val selectedClient get() = connection
    val connectedDevices get() = connections.values.filter { it.isConnected }.map { it.device }
    private val mutableGuest = MutableStateFlow(false)
    val guestState = mutableGuest.asStateFlow()
    private var guest: Boolean
        get() = mutableGuest.value
        set(value) { mutableGuest.value = value }
    private val applying = ThreadLocal.withInitial { false }
    private val overrides = ConcurrentHashMap<String, DesktopPlaybackRequest>()
    private var guestQueue: List<LanTrack> = emptyList()
    private var guestIndex = -1
    private var lastGuestSeekNanos = 0L
    @Volatile private var guestRelay: JSONObject? = null
    @Volatile private var guestSessionId = ""
    @Volatile private var guestGeneration = -1L
    @Volatile private var guestCommitted = false
    @Volatile private var guestLate = false
    private var guestCommit: Job? = null
    @Volatile var playbackRevision = 0L
        private set
    fun playbackChanged(newRound: Boolean = false, seek: Boolean = false) {
        if (newRound) playbackRevision++
        if (!applying.get()) MeloXLanTogetherCoordinator.changed(seek)
        if (melox.dlna.DlnaSession.active) scope.launch(Dispatchers.Main) {
            melox.dlna.DlnaSession.localTrackChanged(AudioPlayer.currentTrack.value?.let { "${it.id.source.storageValue}:${it.id.value}" })
        }
    }
    val address: String get() = (server?.address ?: lanAddress()).hostAddress
    val port: Int get() = server?.port ?: identity.port
    val isGuest get() = guest
    val isConnected get() = connection?.isConnected == true
    val isTogether get() = guest || MeloXLanTogetherCoordinator.active

    @Synchronized fun start() {
        if (!identity.enabled || server != null) return
        try {
            val s = LanServer(identity, this); s.start(); server = s
            _status.value = "${s.address.hostAddress}:${s.port}"
        } catch (error: Exception) { _status.value = error.message ?: "LAN unavailable" }
    }
    fun setEnabled(value: Boolean) { identity.enabled = value; if (value) start() else stop() }
    fun setPort(value: Int) { require(value in 1024..65526); stop(); identity.port = value; start() }
    @Synchronized fun stop() {
        endSession(); server?.close(); server = null; _status.value = "Stopped"
    }
    fun endSession() {
        resetPlaybackSession()
        connection = null; connections.values.toList().forEach { it.close() }; connections.clear()
        MeloXLanSession.connectionStopped()
    }
    private fun resetPlaybackSession() {
        guestCommit?.cancel(); guestCommit = null; guestCommitted = false
        AudioPlayer.setTogetherRate(1f)
        AudioPlayer.setTogetherBarrier(false)
        MeloXLanTogetherCoordinator.stop()
        LanPcmTap.muteLocalOutput = false
        guest = false
        sender?.close(); sender = null
        receiver?.close(); receiver = null
        if (overrides.isNotEmpty()) onPlayer { PlaybackCommands.playerController()?.pause() }
        overrides.clear(); guestQueue = emptyList(); guestIndex = -1; guestRelay = null
        MeloXLanSession.outputStopped(LanOutputMode.Cast)
        MeloXLanSession.outputStopped(LanOutputMode.Together)
    }
    override fun disconnected(): Unit = onPlayer {
        guestCommit?.cancel(); guestCommit = null; guestCommitted = false; guestSessionId = ""
        AudioPlayer.setTogetherRate(1f)
        AudioPlayer.setTogetherBarrier(false)
        guest = false; overrides.clear(); guestQueue = emptyList(); guestIndex = -1; guestRelay = null
        onPlayer { PlaybackCommands.playerController()?.pause() }
    }
    fun stopCastSender() {
        LanPcmTap.muteLocalOutput = false
        val old = sender; sender = null; old?.close()
    }
    fun stopHostTogether() {
        connection?.let { MeloXLanTogetherCoordinator.remove(it, false) }
    }
    fun stopGuestTogether() { server?.stopTogetherSession() }
    /** Stop the output mode, keeping the authenticated control connection. */
    fun useLocal() {
        val cast = sender != null
        val together = connection?.let { MeloXLanTogetherCoordinator.contains(it) } == true
        stopCastSender(); stopHostTogether()
        if (cast) remote("cast.stop")
        if (together) remote("together.stop")
    }
    internal suspend fun prepareLocalTogether(index: Int, position: Long, current: () -> Boolean): Boolean {
        onPlayer {
            if (!current()) return@onPlayer
            AudioPlayer.setTogetherBarrier(true)
            val player = PlaybackCommands.playerController() ?: return@onPlayer
            if (player.currentMediaItemIndex != index) player.seekToIndex(index, position)
            else { player.seekTo(position); player.prepare() }
        }
        val deadline = System.nanoTime() + 30_000_000_000L
        while (current() && System.nanoTime() < deadline) {
            if (AudioPlayer.state.value.error != null) return false
            if (AudioPlayer.isReady()) return true
            delay(10)
        }
        return false
    }
    internal fun commitLocalTogether(localWallMs: Long, playing: Boolean, current: () -> Boolean): Job = scope.launch {
        val delayMs = (localWallMs - System.currentTimeMillis()).coerceAtLeast(0)
        val deadline = System.nanoTime() + delayMs * 1_000_000
        while (current() && System.nanoTime() < deadline) delay(((deadline - System.nanoTime()) / 1_000_000).coerceAtLeast(1))
        onPlayer {
            if (!current()) return@onPlayer
            AudioPlayer.setTogetherBarrier(false)
            if (playing) PlaybackCommands.playerController()?.play() else PlaybackCommands.playerController()?.pause()
        }
    }
    override fun togetherStatus(): JSONObject? = if (guestSessionId.isNotBlank() && guest) JSONObject()
        .put("type", "event").put("event", "together.status").put("sessionId", guestSessionId)
        .put("generation", guestGeneration).put("state", state()) else null
    override fun cancelPreparation(sessionId: String, generation: Long): Unit = onPlayer {
        if (guestSessionId == sessionId && guestGeneration == generation) disconnected()
    }

    private fun relayRequests(info: JSONObject?, peer: InetAddress, tracks: List<LanTrack>): Map<String, DesktopPlaybackRequest> {
        if (info == null) return emptyMap()
        val base = URI(info.getString("baseUrl"))
        require(base.scheme == "http" && base.userInfo == null && InetAddress.getByName(base.host) == peer) { "invalid_relay" }
        require(base.port in 1024..65535 && (base.path.isNullOrEmpty() || base.path == "/")) { "invalid_relay" }
        val token = info.getString("token"); require(token.length in 8..512)
        return tracks.associate { key(it) to DesktopPlaybackRequest("${base.toString().trimEnd('/')}/audio?token=${URLEncoder.encode(token, "UTF-8")}&songId=${URLEncoder.encode(it.id, "UTF-8")}") }
    }
    private suspend fun prepareGuest(message: JSONObject, peer: InetAddress): JSONObject = withContext(Dispatchers.Main) {
        val id = message.optString("sessionId"); val gen = message.optLong("generation", -1)
        require(id.isNotBlank() && gen >= 0 && (id != guestSessionId || gen > guestGeneration)) { "stale_or_invalid_round" }
        val tracks = LanTrack.list(message.optJSONArray("queue")); val index = message.optInt("index")
        require(index in tracks.indices); tracks.forEach(::song)
        melox.dlna.DlnaSession.disconnect(false)
        // A new song/round replaces playback, never its authenticated control sockets.
        resetPlaybackSession(); guestSessionId = id; guestGeneration = gen; guest = true
        AudioPlayer.setTogetherBarrier(true)
        val current = { guest && guestSessionId == id && guestGeneration == gen }
        val requests = withContext(Dispatchers.IO) { relayRequests(message.optJSONObject("relay"), peer, tracks) }
        val resolved = withContext(Dispatchers.IO) { requests[key(tracks[index])] ?: resolve(tracks[index]) }
        if (!current()) return@withContext JSONObject().put("ok", false).put("error", "stale_round")
        overrides.putAll(requests)
        applyQueue(tracks, index, message.optLong("positionMs"), false, resolved)
        guestQueue = tracks; guestIndex = index; guestRelay = message.optJSONObject("relay")
        val deadline = System.nanoTime() + 30_000_000_000L
        while (current() && !AudioPlayer.isReady() && AudioPlayer.state.value.error == null && System.nanoTime() < deadline) delay(10)
        JSONObject().put("ok", current() && AudioPlayer.isReady()).put("sessionId", id).put("generation", gen)
    }
    private fun key(t: LanTrack) = "${t.source}:${t.id}"
    internal fun playbackOverride(song: SearchSong): DesktopPlaybackRequest? = overrides[key(fromSong(song))]

    fun fromSong(song: SearchSong): LanTrack {
        val track = song.providerTrack
        return LanTrack(track?.id?.source?.storageValue ?: "netease", track?.id?.value ?: song.id.toString(),
            song.name, song.artists, song.album, song.artworkUrl, song.durationMs)
    }
    override fun queue() = PlaybackCommands.queue.value.songs.map(::fromSong)
    override fun state(): JSONObject {
        val q = PlaybackCommands.queue.value; val song = q.songs.getOrNull(q.index)
        val track = song?.let(::fromSong)
        return JSONObject().put("playing", AudioPlayer.state.value.isPlaying)
            .put("ready", AudioPlayer.isReady()).put("ended", AudioPlayer.state.value.isFinished)
            .put("serverTimeMs", System.currentTimeMillis())
            .put("title", track?.title.orEmpty()).put("artist", track?.artist.orEmpty()).put("album", track?.album.orEmpty())
            .put("artworkUrl", track?.artworkUrl).put("trackId", track?.let(::key)).put("source", track?.source)
            .put("positionMs", AudioPlayer.getCurrentPositionMs()).put("durationMs", AudioPlayer.durationMs.value)
            .put("volume", (AudioPlayer.volumeLevel.value * 100).toInt()).put("index", q.index)
            .put("queueLength", q.songs.size).put("hasQueue", q.songs.isNotEmpty())
            .put("canNext", q.index < q.songs.lastIndex).put("canPrevious", q.index > 0)
    }
    internal fun <T> onPlayer(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            val old = applying.get(); applying.set(true)
            return try { block() } finally { applying.set(old) }
        }
        val task = FutureTask { onPlayer(block) }; SwingUtilities.invokeAndWait(task); return task.get()
    }
    fun forwardGuestControl(command: String, positionMs: Long? = null): Boolean {
        if (melox.dlna.DlnaSession.active && !applying.get()) {
            when (command) {
                "play", "pause" -> melox.dlna.DlnaSession.transport(command)
                "toggle" -> melox.dlna.DlnaSession.toggle()
                "seek" -> melox.dlna.DlnaSession.transport("seek", positionMs ?: 0)
                "next" -> melox.dlna.DlnaSession.next()
                "previous" -> melox.dlna.DlnaSession.previous()
                else -> return false
            }
            return true
        }
        if (!guest || applying.get()) return false
        return server?.sendTogetherControl(command, positionMs) == true
    }
    private fun control(command: String, message: JSONObject): Boolean = onPlayer {
        val p = PlaybackCommands.playerController()
        when (command) {
            "volume" -> { require(message.has("volume")); AudioPlayer.setVolume(message.getInt("volume").coerceIn(0, 100) / 100f); true }
            "play" -> { p?.play(); p != null }
            "pause" -> { p?.pause(); p != null }
            "toggle" -> { if (AudioPlayer.state.value.isPlaying) p?.pause() else p?.play(); p != null }
            "next" -> { p?.next(); p != null }
            "previous" -> { p?.previous(); p != null }
            "seek" -> { require(message.has("positionMs")); p?.seekTo(message.getLong("positionMs").coerceAtLeast(0)); p != null }
            else -> false
        }
    }
    private fun song(track: LanTrack): SearchSong {
        val aliases = mapOf("qq" to "qq_music", "youtube" to "youtube_music")
        val source = MusicSource.entries.firstOrNull { it.storageValue == (aliases[track.source] ?: track.source) }
            ?: throw IOException("unsupported_source")
        if (source == MusicSource.AppleMusic) throw IOException("unsupported_source")
        if (source == MusicSource.Netease) {
            val id = track.id.toLongOrNull()?.takeIf { it > 0 } ?: throw IOException("invalid_track")
            return SearchSong(id, track.title, track.artist, track.album, artworkUrl = track.artworkUrl, durationMs = track.durationMs)
        }
        return MeloXLegacyUiBridge.track(MusicTrack(MusicResourceId(source, track.id), track.title,
            listOf(MusicArtistRef(name = track.artist)), MusicAlbumRef(name = track.album), track.artworkUrl, track.durationMs))
    }
    internal fun resolve(track: LanTrack): DesktopPlaybackRequest {
        val s = song(track)
        if (s.providerTrack == null) {
            melox.download.MeloXDownloadStore.instance().localPlaybackString(s.id)?.let { return DesktopPlaybackRequest(it) }
            val r = NeteasePlaybackResolver(cookieProvider = { melox.music.provider.PlaybackAccountStore.neteaseCookie() })
                .resolveUri(NeteasePlaybackResolver.uriForSong(s.id))
            return DesktopPlaybackRequest(r.uri, r.headers)
        }
        ProviderPlaybackRuntime.initialize()
        val provider = ProviderPlaybackRuntime.registryOrNull()?.require(s.providerTrack.id.source) as? PlaybackCapability
            ?: throw IOException("unsupported_source")
        val result = runBlocking { provider.resolvePlayback(s.providerTrack, AudioQualityTier.Standard) }
        val playable = result as? PlaybackResolution.Playable ?: throw IOException("track_unavailable")
        return DesktopPlaybackRequest(playable.url, playable.requestHeaders)
    }
    private fun applyQueue(tracks: List<LanTrack>, index: Int, position: Long, playing: Boolean, resolved: DesktopPlaybackRequest?) {
        require(tracks.isNotEmpty() && index in tracks.indices)
        val songs = tracks.map(::song)
        if (resolved != null) overrides[key(tracks[index])] = resolved
        onPlayer {
            PlaybackCommands.playQueue(songs, songs[index].id, position.coerceAtLeast(0))
            if (!playing) PlaybackCommands.playerController()?.pause()
        }
        lastGuestSeekNanos = System.nanoTime()
    }
    override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
        require(identity.enabled)
        when (action) {
            "together.prepare" -> return runBlocking { prepareGuest(message, peer) }
            "together.commit" -> {
                return onPlayer {
                    if (!guest || message.optString("sessionId") != guestSessionId || message.optLong("generation") != guestGeneration || guestCommitted)
                        return@onPlayer JSONObject().put("ok", false).put("error", "stale_round")
                    val id = guestSessionId; val gen = guestGeneration
                    guestLate = message.optBoolean("late"); guestCommitted = true
                    guestCommit?.cancel()
                    guestCommit = commitLocalTogether(message.optLong("startAtMs") - message.optLong("clockOffsetMs"), message.optBoolean("playing")) {
                        guest && guestSessionId == id && guestGeneration == gen
                    }
                    JSONObject().put("ok", true)
                }
            }
            "control" -> return JSONObject().put("ok", control(message.optString("command"), message))
            "transfer" -> {
                val track = LanTrack.parse(message.getJSONObject("track"))
                val tracks = LanTrack.list(message.optJSONArray("queue")).ifEmpty { listOf(track) }
                val index = tracks.indexOfFirst { key(it) == key(track) }; require(index >= 0)
                tracks.forEach(::song) // Reject the whole unsupported queue before touching playback.
                val resolved = resolve(track)
                runBlocking { melox.dlna.DlnaSession.disconnect(false) }
                resetPlaybackSession(); applyQueue(tracks, index, message.optLong("positionMs"), true, resolved)
            }
            "cast.start" -> {
                val format = LanPcmFormat.parse(message.optJSONObject("format"))
                runBlocking { melox.dlna.DlnaSession.disconnect(false) }
                resetPlaybackSession()
                lateinit var cast: LanCastReceiver
                cast = LanCastReceiver(server?.address ?: lanAddress(), peer, format, onError = { _status.value = it }, onStopped = {
                    if (receiver === cast) { receiver = null; server?.castStopped() }
                })
                cast.start(); receiver = cast
                onPlayer { PlaybackCommands.playerController()?.pause() }
                return JSONObject().put("ok", true).put("castPort", cast.port).put("format", format.json())
            }
            "cast.stop" -> { receiver?.close(); receiver = null }
            "together.start" -> {
                guestSessionId = ""
                val tracks = LanTrack.list(message.optJSONArray("queue")); val index = message.optInt("index")
                require(index in tracks.indices); tracks.forEach(::song)
                val relayInfo = message.optJSONObject("relay")
                val requests = if (relayInfo == null) emptyMap() else {
                    val base = URI(relayInfo.getString("baseUrl"))
                    require(base.scheme == "http" && base.userInfo == null && InetAddress.getByName(base.host) == peer) { "invalid_relay" }
                    require(base.port in 1024..65535 && (base.path.isNullOrEmpty() || base.path == "/")) { "invalid_relay" }
                    val token = relayInfo.getString("token"); require(token.length in 8..512)
                    tracks.associate { key(it) to DesktopPlaybackRequest("${base.toString().trimEnd('/')}/audio?token=${URLEncoder.encode(token, "UTF-8")}&songId=${URLEncoder.encode(it.id, "UTF-8")}") }
                }
                val resolved = requests[key(tracks[index])] ?: resolve(tracks[index])
                runBlocking { melox.dlna.DlnaSession.disconnect(false) }
                resetPlaybackSession(); overrides.putAll(requests)
                applyQueue(tracks, index, message.optLong("positionMs"), message.optBoolean("playing"), resolved)
                guestQueue = tracks; guestIndex = index; guestRelay = relayInfo; guest = true
            }
            "together.state" -> {
                if (!guest) return JSONObject().put("ok", false)
                if (guestSessionId.isNotBlank() && (message.optString("sessionId") != guestSessionId ||
                    message.optLong("generation") != guestGeneration || !guestCommitted)) return JSONObject().put("ok", false)
                val s = message.optJSONObject("state") ?: message
                if (guestSessionId.isNotBlank() && !message.optBoolean("seek") && AudioPlayer.durationMs.value > 0 &&
                    AudioPlayer.getCurrentPositionMs() >= AudioPlayer.durationMs.value - 100) return JSONObject().put("ok", true)
                val updatedQueue = LanTrack.list(s.optJSONArray("queue"))
                val index = s.optInt("index", guestIndex)
                val position = togetherTargetPosition(s.optLong("positionMs").coerceAtLeast(0), s.optBoolean("playing") && s.optBoolean("ready", true),
                    s.optLong("serverTimeMs", message.optLong("serverTimeMs")), System.currentTimeMillis(), message.optLong("clockOffsetMs"))
                val playing = s.optBoolean("playing")
                if (updatedQueue.isNotEmpty() && updatedQueue != guestQueue) {
                    if (guestSessionId.isNotBlank()) return JSONObject().put("ok", false).put("error", "new_generation_required")
                    return request("together.start", JSONObject().put("queue", JSONArray(updatedQueue.map { it.json() }))
                        .put("index", index).put("positionMs", position).put("playing", playing).put("relay", guestRelay), peer)
                }
                if (index in guestQueue.indices && index != guestIndex) {
                    if (guestSessionId.isNotBlank()) return JSONObject().put("ok", false).put("error", "new_generation_required")
                    onPlayer { PlaybackCommands.playerController()?.seekToIndex(index, position) }; guestIndex = index
                    lastGuestSeekNanos = System.nanoTime()
                }
                onPlayer {
                    // The socket thread may have queued this before a newer prepare reserved Main.
                    if (!guest || guestSessionId.isNotBlank() && (message.optString("sessionId") != guestSessionId ||
                        message.optLong("generation") != guestGeneration || !guestCommitted)) return@onPlayer
                    val p = PlaybackCommands.playerController()
                    if (message.optBoolean("seek") || !guestLate && shouldCorrectTogetherPosition(AudioPlayer.isReady(), AudioPlayer.getCurrentPositionMs(), position,
                            (System.nanoTime() - lastGuestSeekNanos) / 1_000_000)) {
                        AudioPlayer.setTogetherRate(1f); p?.seekTo(position); lastGuestSeekNanos = System.nanoTime()
                    } else if (!guestLate && playing && AudioPlayer.isReady() && System.nanoTime() - lastGuestSeekNanos >= 3_000_000_000L) {
                        val error = position - AudioPlayer.getCurrentPositionMs()
                        AudioPlayer.setTogetherRate(if (kotlin.math.abs(error) <= 40) 1f else (1f + error / 5000f).coerceIn(.97f, 1.03f))
                    } else {
                        AudioPlayer.setTogetherRate(1f)
                    }
                    if (playing != AudioPlayer.state.value.isPlaying) { if (playing) p?.play() else p?.pause() }
                }
            }
            "together.stop" -> disconnected()
            else -> return JSONObject().put("ok", false).put("error", "unknown_action")
        }
        return JSONObject().put("ok", true)
    }
    fun connect(device: LanDevice, pin: String, event: (JSONObject) -> Unit, onDisconnected: () -> Unit = {}): JSONObject {
        require(identity.enabled)
        connections[device.id]?.takeIf { it.isConnected }?.let {
            if (connection !== it && sender != null) useLocal()
            connection = it
            return JSONObject().put("type", "auth_ok").put("deviceId", it.device.id)
        }
        lateinit var client: LanClient
        client = LanClient(device, identity, onEvent = { message ->
            MeloXLanTogetherCoordinator.event(client, message)
            if (connection === client) event(message)
        }, onRequest = { message ->
            request(message.optString("action"), message, InetAddress.getByName(device.address))
        }, onClose = {
            connections.entries.removeIf { it.value === client }
            MeloXLanSession.refreshConnections()
            MeloXLanTogetherCoordinator.remove(client)
            if (connection === client) {
                connection = null; stopHostTogether(); stopCastSender(); onDisconnected()
            }
        })
        val auth = client.connect(pin)
        if (auth.optString("type") == "auth_ok") {
            if (sender != null) useLocal()
            connection = client
            connections[auth.optString("deviceId").ifBlank { device.id }] = client
        }
        return auth
    }
    fun remote(action: String, fields: JSONObject = JSONObject()): JSONObject = checkNotNull(connection) { "Not connected" }.request(action, fields)
    fun transfer() {
        val tracks = queue(); val index = PlaybackCommands.queue.value.index
        require(index in tracks.indices) { "No track selected" }
        val response = remote("transfer", JSONObject().put("track", tracks[index].json())
            .put("queue", JSONArray(tracks.map { it.json() })).put("positionMs", AudioPlayer.getCurrentPositionMs()))
        check(response.optBoolean("ok")) { response.optString("error") }
        onPlayer { PlaybackCommands.playerController()?.pause() }
    }
    fun cast() {
        val client = checkNotNull(connection)
        val response = remote("cast.start", JSONObject().put("format", LanPcmFormat(AudioPlayer.PCM_SAMPLE_RATE, 2).json()))
        check(response.optBoolean("ok")) { response.optString("error") }
        val format = LanPcmFormat.parse(response.optJSONObject("format"))
        require(format == LanPcmFormat(AudioPlayer.PCM_SAMPLE_RATE, 2))
        stopCastSender()
        lateinit var created: LanCastSender
        created = LanCastSender(InetAddress.getByName(client.device.address), response.getInt("castPort")) {
            synchronized(created) {
            if (sender === created) {
                stopCastSender()
                MeloXLanSession.outputStopped(LanOutputMode.Cast)
                scope.launch { runCatching { client.request("cast.stop") } }
            }
            }
        }
        sender = created
        try { synchronized(created) {
            created.start(); check(created.isRunning) { "cast_connection_stopped" }; LanPcmTap.muteLocalOutput = true
        } }
        catch (error: Exception) { stopCastSender(); runCatching { client.request("cast.stop") }; throw error }
    }
    fun together(useRelay: Boolean) {
        runBlocking { MeloXLanTogetherCoordinator.join(checkNotNull(connection), useRelay) }
    }
}
