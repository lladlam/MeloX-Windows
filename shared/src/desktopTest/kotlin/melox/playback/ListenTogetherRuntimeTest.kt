package melox.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import melox.model.SearchSong
import melox.network.*
import kotlin.test.*

/** Coordinator ownership/state tests, not live account or physical-audio acceptance. */
@OptIn(ExperimentalCoroutinesApi::class)
class ListenTogetherRuntimeTest {
    private val room = MeloXListenTogetherRoom("room-one", "99", emptyList())
    private fun song(id: Long) = SearchSong(id, "track-$id", "artist", "album", null, 180_000)
    private fun snapshot(ids: List<Long> = listOf(1, 2), target: Long = 2, position: Long = 8_000) =
        MeloXListenTogetherSnapshot(ids, ids, "LOOP", target, 1, position, false, "99", 1, 1)
    private inner class Backend : TogetherBackend {
        var latest = snapshot()
        var failure = false
        var songRequest: (suspend () -> Unit)? = null
        val commands = mutableListOf<MeloXListenTogetherCommandType>()
        var heartbeats = 0
        var playlistReports = 0
        override suspend fun status() = room
        override suspend fun playback(roomId: String): MeloXListenTogetherSnapshot {
            if (failure) error("network unavailable")
            return latest
        }
        override suspend fun userId() = 7L
        override suspend fun songs(ids: List<Long>): List<SearchSong> { songRequest?.invoke(); return ids.map(::song) }
        override suspend fun command(roomId: String, type: MeloXListenTogetherCommandType, position: Long, playing: Boolean, former: Long?, target: Long, sequence: Int) { commands += type }
        override suspend fun playlist(roomId: String, userId: Long, version: Int, display: List<Long>, random: List<Long>) { playlistReports++ }
        override suspend fun heartbeat(roomId: String, songId: Long, playing: Boolean, position: Long) { heartbeats++ }
    }
    private fun TestScope.player() = MeloXDesktopPlayer(
        { DesktopPlaybackRequest("unit-test-not-audio") }, {}, backgroundScope,
        StandardTestDispatcher(testScheduler), {}, { _, _ -> },
    ).also { PlaybackCommands.adoptController(it) }

    @BeforeTest fun reset() { MeloXPlaybackModePreferences.reset() }
    @AfterTest fun release() { PlaybackCommands.playerController()?.release() }

    @Test fun remoteQueueAndSeekApplyToRealControllerWithoutEchoAndHeartbeatContinues() = runTest {
        val player = player()
        val backend = Backend()
        val runtime = ListenTogetherRuntime(backgroundScope, { "MUSIC_U=unit-session" }, backend, { testScheduler.currentTime })
        runtime.start(); runCurrent()
        assertEquals(listOf(1L, 2L), player.snapshot().map { it.id })
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(8_000L, player.currentPosition)
        assertFalse(player.playWhenReady)
        assertTrue(backend.commands.isEmpty())
        assertEquals(PlaybackCommands.REPEAT_ALL, player.repeatMode.value)
        advanceTimeBy(5_100); runCurrent()
        assertTrue(backend.heartbeats >= 1)
        assertTrue(backend.commands.isEmpty(), "Remote writes must not echo into local command reports")
        player.seekTo(12_000); runCurrent()
        assertEquals(listOf(MeloXListenTogetherCommandType.Progress), backend.commands)
        player.play(); runCurrent()
        player.pause(); runCurrent()
        assertEquals(listOf(MeloXListenTogetherCommandType.Progress, MeloXListenTogetherCommandType.Play, MeloXListenTogetherCommandType.Pause), backend.commands)
    }

    @Test fun logoutWhileSongLookupIsSuspendedCannotInstallOldAccountQueue() = runTest {
        val player = player()
        player.setMediaItems(listOf(song(40)), 0, 2_000)
        player.prepare(resolveAudio = false)
        val entered = CompletableDeferred<Unit>()
        val proceed = CompletableDeferred<Unit>()
        val backend = Backend().apply { songRequest = { entered.complete(Unit); proceed.await() } }
        var cookie = "MUSIC_U=unit-account-one"
        val runtime = ListenTogetherRuntime(backgroundScope, { cookie }, backend, { testScheduler.currentTime })
        runtime.start(); runCurrent(); entered.await()
        cookie = ""
        proceed.complete(Unit); runCurrent()
        assertEquals(listOf(40L), player.snapshot().map { it.id })
        advanceTimeBy(1_100); runCurrent()
        assertNull(runtime.state.value.room)
        assertTrue(backend.commands.isEmpty())
    }

    @Test fun failedSyncReportsReconnectingAndRecoversWithoutDiscardingRoom() = runTest {
        player()
        val backend = Backend()
        val runtime = ListenTogetherRuntime(backgroundScope, { "MUSIC_U=unit-session" }, backend, { testScheduler.currentTime })
        runtime.start(); runCurrent()
        backend.failure = true
        advanceTimeBy(1_100); runCurrent()
        assertEquals(MeloXListenTogetherCoordinator.Phase.Reconnecting, runtime.state.value.phase)
        assertEquals(room.id, runtime.state.value.room?.id)
        backend.failure = false
        advanceTimeBy(1_100); runCurrent()
        assertEquals(MeloXListenTogetherCoordinator.Phase.Connected, runtime.state.value.phase)
        assertEquals(0, runtime.state.value.consecutiveFailures)
    }

    @Test fun concreteRemoteOrderBypassesLocalShuffleAndSmartQueue() = runTest {
        MeloXPlaybackModePreferences.setShuffle(true)
        MeloXPlaybackModePreferences.setSmartQueue(true)
        val player = player()
        val backend = Backend().apply { latest = snapshot(ids = listOf(8, 5, 3), target = 5) }
        val runtime = ListenTogetherRuntime(backgroundScope, { "MUSIC_U=unit-session" }, backend, { testScheduler.currentTime })
        runtime.start(); runCurrent()
        assertEquals(listOf(8L, 5L, 3L), player.snapshot().map { it.id })
        assertEquals(1, player.currentMediaItemIndex)
        assertFalse(player.shuffleEnabled.value)
        MeloXPlaybackModePreferences.reset()
    }
}
