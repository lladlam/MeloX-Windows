package melox.lan

import java.net.InetAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import melox.player.AudioPlayer
import melox.playback.PlaybackCommands
import org.json.JSONArray
import org.json.JSONObject

/** Android revision-2 rounds. All round decisions use Main; socket/resolve work uses IO. */
internal object MeloXLanTogetherCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private class Peer(val client: LanClient, val provideAudio: Boolean) {
        var offset = 0L
        var calibrated = 0L
        var ready = false
        var late = false
        var state = JSONObject()
        var relay: LanAudioRelay? = null
    }
    private val peers = ConcurrentHashMap<String, Peer>()
    @Volatile private var generation = 0L
    private var sessionId = ""
    private var queueKey = ""
    private var preparing = false
    private var localPending = false
    private var playIntent = false
    private var round: Job? = null
    private var monitor: Job? = null
    private var ending: Job? = null
    private var explicitSeek = false
    private var completedGeneration = -1L
    val active get() = peers.isNotEmpty()
    fun contains(client: LanClient) = peers[client.device.id]?.client === client

    suspend fun join(client: LanClient, provideAudio: Boolean) = withContext(Dispatchers.Main) {
        require(MeloXLanRuntime.queue().isNotEmpty()) { "No track selected" }
        if (peers.isEmpty()) sessionId = UUID.randomUUID().toString()
        peers.put(client.device.id, Peer(client, provideAudio))?.relay?.close()
        queueKey = ""
        if (monitor == null) monitor = scope.launch { while (isActive && active) { update(); delay(200) } }
        update()
    }
    fun changed(seek: Boolean = false) {
        if (!active) return
        scope.launch { explicitSeek = explicitSeek || seek; update() }
    }
    fun event(client: LanClient, event: JSONObject) {
        scope.launch {
            val peer = peers[client.device.id]?.takeIf { it.client === client } ?: return@launch
            when (event.optString("event")) {
                "together.stopped" -> remove(client, false)
                "state" -> peer.state = event.optJSONObject("state") ?: JSONObject()
                "together.status" -> if (event.optString("sessionId") == sessionId && event.optLong("generation") == generation)
                    peer.state = event.optJSONObject("state") ?: JSONObject()
            }
        }
    }
    fun remove(client: LanClient, notify: Boolean = true) {
        scope.launch {
            val peer = peers[client.device.id]?.takeIf { it.client === client } ?: return@launch
            peers.remove(client.device.id, peer); peer.relay?.close()
            if (MeloXLanRuntime.selectedClient === client) MeloXLanSession.outputStopped(LanOutputMode.Together)
            if (notify && client.isConnected) withContext(Dispatchers.IO) { runCatching { client.request("together.stop") } }
            if (peers.isEmpty()) stop(false)
            MeloXLanSession.refreshConnections()
        }
    }
    fun stop(notify: Boolean = true): Unit = MeloXLanRuntime.onPlayer {
        val releasePreparingPlayer = (preparing || localPending) && playIntent && PlaybackCommands.playerController()?.playWhenReady == true
        generation++
        round?.cancel(); round = null; monitor?.cancel(); monitor = null; ending?.cancel(); ending = null
        val old = peers.values.toList(); peers.clear(); queueKey = ""; preparing = false
        AudioPlayer.setTogetherBarrier(false)
        localPending = false
        if (releasePreparingPlayer) PlaybackCommands.playerController()?.play()
        old.forEach { peer ->
            peer.relay?.close()
            if (notify && peer.client.isConnected) scope.launch(Dispatchers.IO) { runCatching { peer.client.request("together.stop") } }
        }
    }
    private fun update() {
        if (!active) return
        val queue = MeloXLanRuntime.queue(); if (queue.isEmpty()) return
        val state = MeloXLanRuntime.state()
        val key = queue.joinToString("|") { "${it.source}:${it.id}" } + "/${state.optInt("index")}/${state.optString("trackId")}" +
            "/${MeloXLanRuntime.playbackRevision}"
        if (key != queueKey) {
            state.put("playing", PlaybackCommands.playerController()?.playWhenReady == true)
            queueKey = key; round?.cancel(); ending?.cancel(); ending = null
            val current = ++generation; preparing = true
            round = scope.launch { prepare(current, queue, state) }
            return
        }
        if (preparing || ending != null) return
        if (localPending) state.put("playing", playIntent)
        val seek = explicitSeek; explicitSeek = false
        peers.values.filter { it.ready && it.client.isConnected }.forEach { peer ->
            if (System.nanoTime() - peer.calibrated > 30_000_000_000L) {
                peer.calibrated = System.nanoTime()
                scope.launch { runCatching { peer.offset = calibrate(peer.client) } }
            }
            val event = JSONObject().put("event", "together.state").put("sessionId", sessionId).put("generation", generation)
                .put("state", JSONObject(state.toString()).put("queue", JSONArray(queue.map { it.json() })))
                .put("clockOffsetMs", -peer.offset).put("late", peer.late).put("seek", seek)
            scope.launch(Dispatchers.IO) { runCatching { peer.client.event(event) }.onFailure { remove(peer.client, false) } }
        }
    }
    private suspend fun calibrate(client: LanClient): Long = withContext(Dispatchers.IO) {
        var best = Long.MAX_VALUE; var offset = 0L
        repeat(3) {
            val wall = System.currentTimeMillis(); val start = System.nanoTime()
            val response = client.request("ping", JSONObject().put("t0", wall))
            val elapsed = System.nanoTime() - start
            if (response.optBoolean("ok") && elapsed < best) {
                best = elapsed; offset = response.getLong("serverTimeMs") - (wall + System.currentTimeMillis()) / 2
            }
        }
        offset
    }
    private suspend fun prepare(current: Long, queue: List<LanTrack>, state: JSONObject) = coroutineScope {
        val started = System.nanoTime(); val index = state.optInt("index")
        val position = state.optLong("positionMs"); val playing = state.optBoolean("playing")
        AudioPlayer.setTogetherBarrier(true)
        localPending = true; playIntent = playing
        var localReady = false; var committed = false
        launch {
            localReady = MeloXLanRuntime.prepareLocalTogether(index, position) { generation == current }
            if (localReady && committed && generation == current) {
                localPending = false
                MeloXLanRuntime.commitLocalTogether(System.currentTimeMillis(), playing) { generation == current }
            }
        }
        peers.values.toList().forEach { peer ->
            peer.ready = false; peer.late = false
            launch {
                try {
                    peer.offset = calibrate(peer.client); peer.calibrated = System.nanoTime()
                    peer.relay?.close()
                    peer.relay = if (peer.provideAudio) withContext(Dispatchers.IO) {
                        LanAudioRelay(lanAddress(), InetAddress.getByName(peer.client.device.address), queue, MeloXLanRuntime::resolve)
                    } else null
                    val payload = JSONObject().put("sessionId", sessionId).put("generation", current).put("queueRevision", queueKey)
                        .put("queue", JSONArray(queue.map { it.json() })).put("index", index).put("trackId", state.optString("trackId"))
                        .put("positionMs", position).put("playing", playing)
                    peer.relay?.let { payload.put("relay", it.description()) }
                    val barrier = LAN_TOGETHER_BARRIER in peer.client.capabilities
                    val response = withContext(Dispatchers.IO) { peer.client.request(if (barrier) "together.prepare" else "together.start", payload, 35000) }
                    if (generation != current || peers[peer.client.device.id] !== peer) return@launch
                    peer.ready = response.optBoolean("ok") && (!barrier || response.optString("sessionId") == sessionId && response.optLong("generation") == current)
                    if (!barrier) melox.platform.logInfo("MeloXTogether", "Legacy peer: no READY barrier")
                    if (!peer.ready) { remove(peer.client); return@launch }
                    if (committed) { peer.late = true; commit(peer, current, System.currentTimeMillis(), position, playing) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { remove(peer.client) }
            }
        }
        while (generation == current && System.nanoTime() - started < 3_000_000_000L &&
            (!localReady || peers.values.any { !it.ready && it.client.isConnected })) delay(10)
        if (generation != current) return@coroutineScope
        committed = true; val at = System.currentTimeMillis() + 200
        peers.values.filter { it.ready }.forEach { it.late = !localReady; commit(it, current, at, position, playing) }
        if (localReady) { localPending = false; MeloXLanRuntime.commitLocalTogether(at, playing) { generation == current } }
        delay(220)
        if (generation == current) preparing = false
    }
    private fun commit(peer: Peer, current: Long, at: Long, position: Long, playing: Boolean) {
        if (LAN_TOGETHER_BARRIER !in peer.client.capabilities) return
        scope.launch(Dispatchers.IO) {
            runCatching { peer.client.event(JSONObject().put("event", "together.commit").put("sessionId", sessionId)
                .put("generation", current).put("startAtMs", at).put("positionMs", position).put("playing", playing)
                .put("clockOffsetMs", -peer.offset).put("late", peer.late)) }
        }
    }
    fun ended(advance: () -> Unit): Boolean {
        if (!active) return false
        scope.launch {
            if (!active || preparing || ending != null || completedGeneration == generation) return@launch
            val current = generation
            ending = scope.launch(start = CoroutineStart.LAZY) {
                val host = AudioPlayer.getCurrentPositionMs()
                val currentPeers = peers.values.filter { it.ready && it.client.isConnected }
                val wait = currentPeers.all { peer -> host - togetherTargetPosition(peer.state.optLong("positionMs"),
                    peer.state.optBoolean("playing") && peer.state.optBoolean("ready"), peer.state.optLong("serverTimeMs"),
                    System.currentTimeMillis(), peer.offset) <= 5000 }
                if (wait) {
                    val deadline = System.nanoTime() + 5_000_000_000L
                    while (generation == current && System.nanoTime() < deadline && peers.values.any {
                        it.ready && it.client.isConnected && !it.state.optBoolean("ended") }) delay(20)
                }
                if (generation == current) { completedGeneration = current; advance() }
                ending = null
            }
            ending?.start()
        }
        return true
    }
}

internal fun togetherTargetPosition(position: Long, playing: Boolean, sampledAt: Long, now: Long, offset: Long): Long =
    position + if (playing && sampledAt > 0) (now + offset - sampledAt).coerceIn(0, 2000) else 0
