package melox.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import melox.account.NeteaseSessionStore
import melox.library.NeteaseLibraryClient
import melox.model.SearchSong
import melox.network.*

internal interface TogetherBackend {
    suspend fun status(): MeloXListenTogetherRoom?
    suspend fun playback(roomId: String): MeloXListenTogetherSnapshot
    suspend fun userId(): Long
    suspend fun songs(ids: List<Long>): List<SearchSong>
    suspend fun command(roomId: String, type: MeloXListenTogetherCommandType, position: Long, playing: Boolean, former: Long?, target: Long, sequence: Int)
    suspend fun playlist(roomId: String, userId: Long, version: Int, display: List<Long>, random: List<Long>)
    suspend fun heartbeat(roomId: String, songId: Long, playing: Boolean, position: Long)
}

private class NeteaseTogetherBackend(private val cookie: () -> String) : TogetherBackend {
    private val ops = NeteaseMusicOperationsClient(cookie)
    private val transport = NeteaseListenTogetherTransport(cookie)
    private val account = NeteaseSearchClient(cookieProvider = cookie)
    private val library = NeteaseLibraryClient(cookie)
    override suspend fun status() = ops.listenTogetherRoomStatus()
    override suspend fun playback(roomId: String) = transport.playback(roomId)
    override suspend fun userId() = account.accountProfile().userId
    override suspend fun songs(ids: List<Long>) = withContext(Dispatchers.IO) { ids.chunked(100).flatMap(library::songDetailsBlocking) }
    override suspend fun command(roomId: String, type: MeloXListenTogetherCommandType, position: Long, playing: Boolean, former: Long?, target: Long, sequence: Int) =
        transport.reportCommand(roomId, type, position, playing, former, target, sequence)
    override suspend fun playlist(roomId: String, userId: Long, version: Int, display: List<Long>, random: List<Long>) =
        transport.reportPlaylist(roomId, userId, version, display, random)
    override suspend fun heartbeat(roomId: String, songId: Long, playing: Boolean, position: Long) =
        transport.heartbeat(roomId, songId, playing, position)
}

/** Application-owned session, never tied to the settings page or action-sheet lifetime. */
object MeloXListenTogetherCoordinator {
    enum class Phase { Idle, Connected, Reconnecting }
    data class State(
        val phase: Phase = Phase.Idle,
        val room: MeloXListenTogetherRoom? = null,
        val consecutiveFailures: Int = 0,
        val lastError: String? = null,
    )
    private val runtime by lazy {
        val cookie = { NeteaseSessionStore.readCookie() }
        ListenTogetherRuntime(CoroutineScope(SupervisorJob() + Dispatchers.Main), cookie, NeteaseTogetherBackend(cookie))
    }
    val state get() = runtime.state
    fun ensureStarted() { runtime.start() }
    fun adoptRoom(room: MeloXListenTogetherRoom) { ensureStarted(); runtime.adopt(room) }
    fun clearRoom() { runtime.clear() }
    fun shutdown() { runtime.stop() }
}

internal class ListenTogetherRuntime(
    private val scope: CoroutineScope,
    private val cookie: () -> String,
    private val backend: TogetherBackend,
    private val monotonicMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val mutableState = MutableStateFlow(MeloXListenTogetherCoordinator.State())
    val state = mutableState.asStateFlow()
    private var started = false
    private var monitorJob: Job? = null
    private var eventJob: Job? = null
    private var lastCookie: String? = null
    private var epoch = 0L
    private var cachedUserId: Long? = null
    private var sequence = 1
    private var version = 1
    private var firstSync = true
    private var ticks = 0
    private var suppressUntil = 0L
    private var lastQueue: String? = null
    private var lastCommand: String? = null
    private var lastLocalSong: Long? = null
    private var queueJob: Job? = null
    private val commandMutex = Mutex()
    private val playlistMutex = Mutex()
    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun start() {
        if (started) return
        started = true
        monitorJob = scope.launch { monitor() }
        eventJob = scope.launch { MeloXPlaybackEvents.flow.collect { localEvent(it) } }
    }

    fun stop() {
        monitorJob?.cancel(); monitorJob = null
        eventJob?.cancel(); eventJob = null
        reset()
        started = false
    }

    fun adopt(room: MeloXListenTogetherRoom) {
        reset()
        lastCookie = cookie()
        mutableState.value = MeloXListenTogetherCoordinator.State(MeloXListenTogetherCoordinator.Phase.Connected, room)
        lastLocalSong = PlaybackCommands.playerController()?.let { it.snapshot().getOrNull(it.currentMediaItemIndex)?.neteaseSongId }
        wake.trySend(Unit)
    }

    fun clear() { reset(); wake.trySend(Unit) }

    private fun reset() {
        epoch++
        cachedUserId = null
        sequence = 1; version = 1; ticks = 0; firstSync = true
        lastQueue = null; lastCommand = null; lastLocalSong = null
        suppressUntil = 0L
        queueJob?.cancel(); queueJob = null
        mutableState.value = MeloXListenTogetherCoordinator.State()
    }

    private fun valid(token: Long, session: String, roomId: String? = null) =
        epoch == token && cookie() == session && (roomId == null || state.value.room?.id == roomId)

    private suspend fun monitor() {
        while (currentCoroutineContext().isActive) {
            val session = cookie()
            if (session != lastCookie) { reset(); lastCookie = session }
            if (NeteaseSessionStore.containsMusicU(session)) {
                val token = epoch
                try {
                    if (state.value.room == null || ticks % 5 == 0) {
                        val latest = backend.status()
                        if (!valid(token, session)) continue
                        if (latest == null) {
                            if (state.value.room != null) reset()
                        } else if (state.value.room?.id != latest.id) {
                            adopt(latest)
                        } else mutableState.value = state.value.copy(room = latest)
                    }
                    state.value.room?.let { room ->
                        val activeToken = epoch
                        try {
                            val snapshot = backend.playback(room.id)
                            if (!valid(activeToken, session, room.id)) return@let
                            synchronize(room, snapshot, activeToken, session)
                            if (!valid(activeToken, session, room.id)) return@let
                            if (ticks % 5 == 0) heartbeat(room, activeToken, session)
                            mutableState.value = MeloXListenTogetherCoordinator.State(MeloXListenTogetherCoordinator.Phase.Connected, room)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { if (valid(activeToken, session, room.id)) failed(failure) }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (valid(token, session)) failed(failure) }
            } else if (state.value.room != null) reset()
            ticks++
            withTimeoutOrNull(if (state.value.room == null) 60_000L else 1_000L) { wake.receive() }
        }
    }

    private suspend fun userId(token: Long, session: String): Long? {
        cachedUserId?.let { return it }
        val loaded = backend.userId()
        if (!valid(token, session)) return null
        return loaded.takeIf { it > 0L }?.also { cachedUserId = it }
    }

    private suspend fun synchronize(room: MeloXListenTogetherRoom, snapshot: MeloXListenTogetherSnapshot, token: Long, session: String) {
        val self = userId(token, session) ?: return
        if (!valid(token, session, room.id)) return
        val active = PlaybackCommands.playerController() ?: MeloXDesktopPlayer().also { PlaybackCommands.adoptController(it) }
        if (firstSync && room.creatorId == self.toString() && active.snapshot().isNotEmpty()) {
            reportQueue(room, token, session)
            firstSync = false
            return
        }
        val queueSignature = "${snapshot.playMode}|${snapshot.displaySongIds}|${snapshot.randomSongIds}"
        if (firstSync || queueSignature != lastQueue) {
            val remote = snapshot.playbackSongIds
            if (remote.isNotEmpty() && remote != active.snapshot().mapNotNull { it.neteaseSongId }) {
                val loaded = backend.songs(remote).associateBy { it.neteaseSongId }
                if (!valid(token, session, room.id)) return
                val ordered = remote.mapNotNull(loaded::get)
                check(ordered.size == remote.size) { "一起听队列有歌曲未能读取，保留当前队列" }
                suppressUntil = monotonicMs() + 900L
                val index = ordered.indexOfFirst { it.neteaseSongId == snapshot.targetSongId }.coerceAtLeast(0)
                active.installSynchronizedQueue(ordered, index, snapshot.progressMs.coerceAtLeast(0L))
                active.prepare(resolveAudio = snapshot.isPlaying)
                if (snapshot.isPlaying) active.play() else active.pause()
                PlaybackCommands.publishQueue(active)
            }
            snapshot.playMode?.uppercase()?.let { mode ->
                // The remote random list already supplies the playback order.
                // Store the source mode without shuffling the concrete queue again.
                MeloXPlaybackModePreferences.setShuffle(snapshot.randomMode)
                active.setRepeatMode(when {
                    "ONE" in mode || "SINGLE" in mode -> PlaybackCommands.REPEAT_ONE
                    "LOOP" in mode || "ALL" in mode -> PlaybackCommands.REPEAT_ALL
                    else -> PlaybackCommands.REPEAT_OFF
                })
            }
            lastQueue = queueSignature
        }
        val signature = "${snapshot.serverSequence}:${snapshot.clientSequence}:${snapshot.commandUserId}:${snapshot.targetSongId}:${snapshot.progressMs}:${snapshot.isPlaying}"
        if (firstSync || snapshot.commandUserId != self.toString() && signature != lastCommand) {
            suppressUntil = monotonicMs() + 900L
            val target = active.snapshot().indexOfFirst { it.neteaseSongId == snapshot.targetSongId }
            if (target >= 0) {
                if (target != active.currentMediaItemIndex) active.seekToIndex(target, snapshot.progressMs)
                if (kotlin.math.abs(active.currentPosition - snapshot.progressMs) >= 1_200L) active.seekTo(snapshot.progressMs)
                lastLocalSong = snapshot.targetSongId
            }
            if (snapshot.isPlaying && !active.playWhenReady) active.play()
            if (!snapshot.isPlaying && active.playWhenReady) active.pause()
            lastCommand = signature
        }
        firstSync = false
    }

    private fun localEvent(event: MeloXPlaybackEvents.Event) {
        val room = state.value.room ?: return
        val former = lastLocalSong
        lastLocalSong = event.songId
        if (monotonicMs() < suppressUntil || event.songId == null) return
        val token = epoch; val session = cookie()
        if (session != lastCookie) return
        val type = when (event.kind) {
            MeloXPlaybackEvents.Kind.Play -> MeloXListenTogetherCommandType.Play
            MeloXPlaybackEvents.Kind.Pause -> MeloXListenTogetherCommandType.Pause
            MeloXPlaybackEvents.Kind.Seek -> MeloXListenTogetherCommandType.Progress
            MeloXPlaybackEvents.Kind.Queue -> if (former != event.songId) MeloXListenTogetherCommandType.GoTo else null
        }
        if (type != null) scope.launch {
            commandMutex.withLock {
                if (!valid(token, session, room.id)) return@withLock
                try { backend.command(room.id, type, event.positionMs, event.playWhenReady, former, event.songId, sequence++) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (valid(token, session, room.id)) failed(failure) }
            }
        }
        if (event.kind == MeloXPlaybackEvents.Kind.Queue) {
            queueJob?.cancel()
            queueJob = scope.launch {
                delay(350)
                try { if (valid(token, session, room.id)) reportQueue(room, token, session) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (valid(token, session, room.id)) failed(failure) }
            }
        }
    }

    private suspend fun reportQueue(room: MeloXListenTogetherRoom, token: Long, session: String) {
        val uid = userId(token, session) ?: return
        val active = PlaybackCommands.playerController() ?: return
        val entries = active.entries()
        // A mixed-provider queue cannot be represented by NetEase numeric IDs.
        if (entries.isEmpty() || entries.any { it.song.neteaseSongId == null }) return
        val actual = entries.mapNotNull { it.song.neteaseSongId }
        val shuffle = MeloXPlaybackModePreferences.shuffle()
        val display = if (shuffle) (entries.filter { it.originalIndex >= 0 }.sortedBy { it.originalIndex } +
            entries.filter { it.originalIndex < 0 }).mapNotNull { it.song.neteaseSongId } else actual
        playlistMutex.withLock {
            if (valid(token, session, room.id)) backend.playlist(room.id, uid, version++, display, actual)
        }
    }

    private suspend fun heartbeat(room: MeloXListenTogetherRoom, token: Long, session: String) {
        val active = PlaybackCommands.playerController() ?: return
        val song = active.snapshot().getOrNull(active.currentMediaItemIndex)?.neteaseSongId ?: return
        if (valid(token, session, room.id)) backend.heartbeat(room.id, song, active.playWhenReady, active.currentPosition)
    }

    private fun failed(failure: Exception) {
        if (state.value.room == null) return
        mutableState.value = state.value.copy(phase = MeloXListenTogetherCoordinator.Phase.Reconnecting,
            consecutiveFailures = (state.value.consecutiveFailures + 1).coerceAtMost(99), lastError = failure.message)
    }
}
