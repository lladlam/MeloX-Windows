package melox.playback

import melox.model.SearchSong
import melox.platform.getPreferences
import melox.settings.MeloXSettingsPreferences
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LastPlaybackStoreTest {
    private fun song(id: Long, name: String) = SearchSong(
        id = id, name = name, artists = "Native artist", album = "Native album",
        artworkUrl = null, durationMs = 180_000L,
    )

    @BeforeTest
    fun reset() {
        getPreferences("melox_last_playback").clear()
        getPreferences("melox_playback_queue").clear()
        MeloXSettingsPreferences.setBoolean("playback_save_queue", true)
        MeloXSettingsPreferences.setBoolean("playback_remember_last_song", true)
    }

    @AfterTest
    fun cleanup() {
        PlaybackCommands.playerController()?.clearQueue()
        reset()
    }

    @Test
    fun savedSongKeepsLegacyIdentityAndPositionAndHonoursTheSwitch() {
        val player = MeloXDesktopPlayer()
        player.setMediaItems(listOf(song(-4_123_456_789L, "Bridge song")), 0, 12_345L)
        MeloXLastPlaybackStore.save(player)
        val saved = assertNotNull(MeloXLastPlaybackStore.read())
        // Provider-backed songs keep their negative legacy hash; it must survive persistence.
        assertEquals(-4_123_456_789L, saved.first.id)
        assertEquals("Bridge song", saved.first.name)
        assertEquals("Native artist", saved.first.artists)
        assertEquals(180_000L, saved.first.durationMs)
        assertEquals(12_345L, saved.second)

        MeloXSettingsPreferences.setBoolean("playback_remember_last_song", false)
        player.setMediaItems(listOf(song(77L, "Ignored song")), 0, 0L)
        MeloXLastPlaybackStore.save(player)
        assertEquals("Bridge song", assertNotNull(MeloXLastPlaybackStore.read()).first.name)

        MeloXSettingsPreferences.setBoolean("playback_remember_last_song", true)
        MeloXLastPlaybackStore.clear()
        assertNull(MeloXLastPlaybackStore.read())
    }

    @Test
    fun lastSongIsOnlyRestoredWhenQueuePersistenceIsOff() {
        PlaybackCommands.adoptController(MeloXDesktopPlayer())
        val queuePlayer = MeloXDesktopPlayer()
        queuePlayer.setMediaItems(listOf(song(1L, "Queue one"), song(2L, "Queue two")), 1, 500L)
        MeloXLastPlaybackStore.save(queuePlayer)
        MeloXPlaybackQueueStore.save(queuePlayer)

        MeloXSettingsPreferences.setBoolean("playback_save_queue", false)
        PlaybackCommands.restoreLastQueue()
        val fallback = assertNotNull(PlaybackCommands.playerController())
        assertEquals(1, fallback.mediaItemCount)
        assertEquals(false, fallback.playWhenReady)
        // The remembered position is restored with the single song.
        assertEquals(500L, fallback.currentPosition)

        MeloXSettingsPreferences.setBoolean("playback_save_queue", true)
        // Replacing the controller may persist the fallback's own one-item queue,
        // so re-assert the two-item queue before exercising the queue branch.
        PlaybackCommands.adoptController(MeloXDesktopPlayer())
        MeloXPlaybackQueueStore.save(queuePlayer)
        PlaybackCommands.restoreLastQueue()
        val queue = assertNotNull(PlaybackCommands.playerController())
        assertEquals(2, queue.mediaItemCount)
        assertEquals(1, queue.currentMediaItemIndex)

        MeloXSettingsPreferences.setBoolean("playback_save_queue", false)
        MeloXSettingsPreferences.setBoolean("playback_remember_last_song", false)
        MeloXLastPlaybackStore.clear()
        PlaybackCommands.playerController()?.clearQueue()
        PlaybackCommands.restoreLastQueue()
        val empty = assertNotNull(PlaybackCommands.playerController())
        assertEquals(0, empty.mediaItemCount)
        assertFalse(empty.playWhenReady)
    }
}
