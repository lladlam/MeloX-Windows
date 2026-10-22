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
    val identity by lazy { LanIdentity() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow("Stopped")
    val status = _status.asStateFlow()
    @Volatile private var server: LanServer? = null
    @Volatile private var receiver: LanCastReceiver? = null
    @Volatile private var sender: LanCastSender? = null
    @Volatile private var connection: LanClient? = null
    @Volatile private var togetherJob: Job? = null
    @Volatile private var guest = false
    @Volatile private var relay: LanAudioRelay? = null
    private val applying = ThreadLocal.withInitial { false }
    private val overrides = ConcurrentHashMap<String, DesktopPlaybackRequest>()
    private var guestQueue: List<LanTrack> = emptyList()
    private var guestIndex = -1
    val address: String get() = (server?.address ?: lanAddress()).hostAddress
    val port: Int get() = server?.port ?: identity.port
    val isGuest get() = guest

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
        togetherJob?.cancel(); togetherJob = null
        guest = false
        sender?.close(); sender = null
        receiver?.close(); receiver = null
        val old = connection; connection = null; old?.close()
        relay?.close(); relay = null
        if (overrides.isNotEmpty()) onPlayer { PlaybackCommands.playerController()?.pause() }
        overrides.clear(); guestQueue = emptyList(); guestIndex = -1
    }
    override fun disconnected() {
        guest = false; overrides.clear(); guestQueue = emptyList(); guestIndex = -1
        onPlayer { PlaybackCommands.playerController()?.pause() }
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
            .put("title", track?.title.orEmpty()).put("artist", track?.artist.orEmpty()).put("album", track?.album.orEmpty())
            .put("artworkUrl", track?.artworkUrl).put("trackId", track?.let(::key)).put("source", track?.source)
            .put("positionMs", AudioPlayer.getCurrentPositionMs()).put("durationMs", AudioPlayer.durationMs.value)
            .put("volume", (AudioPlayer.volumeLevel.value * 100).toInt()).put("index", q.index)
            .put("queueLength", q.songs.size).put("hasQueue", q.songs.isNotEmpty())
            .put("canNext", q.index < q.songs.lastIndex).put("canPrevious", q.index > 0)
    }
    private fun <T> onPlayer(block: () -> T): T {
        if (SwingUtilities.isEventDispatchThread()) {
            val old = applying.get(); applying.set(true)
            return try { block() } finally { applying.set(old) }
        }
        val task = FutureTask { onPlayer(block) }; SwingUtilities.invokeAndWait(task); return task.get()
    }
    fun forwardGuestControl(command: String, positionMs: Long? = null): Boolean {
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
    }
    override fun request(action: String, message: JSONObject, peer: InetAddress): JSONObject {
        require(identity.enabled)
        when (action) {
            "control" -> return JSONObject().put("ok", control(message.optString("command"), message))
            "transfer" -> {
                val track = LanTrack.parse(message.getJSONObject("track"))
                val tracks = LanTrack.list(message.optJSONArray("queue")).ifEmpty { listOf(track) }
                val index = tracks.indexOfFirst { key(it) == key(track) }; require(index >= 0)
                tracks.forEach(::song) // Reject the whole unsupported queue before touching playback.
                val resolved = resolve(track)
                endSession(); applyQueue(tracks, index, message.optLong("positionMs"), true, resolved)
            }
            "cast.start" -> {
                val format = LanPcmFormat.parse(message.optJSONObject("format"))
                receiver?.close()
                val cast = LanCastReceiver(server?.address ?: lanAddress(), peer, format, onError = { _status.value = it })
                cast.start(); receiver = cast
                onPlayer { PlaybackCommands.playerController()?.pause() }
                return JSONObject().put("ok", true).put("castPort", cast.port).put("format", format.json())
            }
            "cast.stop" -> { receiver?.close(); receiver = null }
            "together.start" -> {
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
                endSession(); overrides.putAll(requests)
                applyQueue(tracks, index, message.optLong("positionMs"), message.optBoolean("playing"), resolved)
                guestQueue = tracks; guestIndex = index; guest = true
            }
            "together.state" -> {
                if (!guest) return JSONObject().put("ok", false)
                val s = message.optJSONObject("state") ?: message
                val index = s.optInt("index", guestIndex)
                val position = s.optLong("positionMs").coerceAtLeast(0)
                val playing = s.optBoolean("playing")
                if (index in guestQueue.indices && index != guestIndex) {
                    onPlayer { PlaybackCommands.playerController()?.seekToIndex(index, position) }; guestIndex = index
                }
                onPlayer {
                    val p = PlaybackCommands.playerController()
                    if (kotlin.math.abs(AudioPlayer.getCurrentPositionMs() - position) > 400) p?.seekTo(position)
                    if (playing != AudioPlayer.state.value.isPlaying) { if (playing) p?.play() else p?.pause() }
                }
            }
            "together.stop" -> disconnected()
            else -> return JSONObject().put("ok", false).put("error", "unknown_action")
        }
        return JSONObject().put("ok", true)
    }
    fun connect(device: LanDevice, pin: String, event: (JSONObject) -> Unit): JSONObject {
        require(identity.enabled)
        endSession()
        val client = LanClient(device, identity, event, onRequest = { message ->
            request(message.optString("action"), message, InetAddress.getByName(device.address))
        }, onClose = {
            togetherJob?.cancel(); relay?.close(); sender?.close()
        })
        val auth = client.connect(pin)
        if (auth.optString("type") == "auth_ok") connection = client
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
        sender?.close(); sender = LanCastSender(InetAddress.getByName(client.device.address), response.getInt("castPort")) {
            scope.launch { runCatching { client.request("cast.stop") } }
        }.also { it.start() }
    }
    fun together(useRelay: Boolean) {
        val client = checkNotNull(connection)
        val tracks = queue(); val index = PlaybackCommands.queue.value.index
        require(index in tracks.indices) { "No track selected" }
        val fields = JSONObject().put("queue", JSONArray(tracks.map { it.json() })).put("index", index)
            .put("positionMs", AudioPlayer.getCurrentPositionMs()).put("playing", AudioPlayer.state.value.isPlaying)
        if (useRelay) {
            relay?.close()
            val proxy = LanAudioRelay(lanAddress(), InetAddress.getByName(client.device.address), tracks, ::resolve)
            relay = proxy
            fields.put("relay", proxy.description())
        }
        val result = remote("together.start", fields)
        if (!result.optBoolean("ok")) { relay?.close(); relay = null; throw IOException(result.optString("error")) }
        togetherJob?.cancel()
        togetherJob = scope.launch {
            try { while (isActive) {
                client.event(JSONObject().put("event", "together.state").put("state", state().put("queue", JSONArray(queue().map { it.json() })).put("serverTimeMs", System.currentTimeMillis())))
                delay(1000)
            } } finally { relay?.close(); relay = null }
        }
    }
}
