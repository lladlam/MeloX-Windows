package melox.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import melox.model.SearchSong
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.platform.getPreferences
import melox.settings.MeloXSettingsPreferences
import melox.player.AudioPlayer
import melox.player.AudioFailureKind
import melox.player.AudioPlaybackFailure
import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import kotlin.test.*

/** Regression scenarios use the real controller state machine, without an audio device. */
@OptIn(ExperimentalCoroutinesApi::class)
class DesktopPlaybackStateTest {
    private fun song(id: Long) = SearchSong(id, "song-$id", "artist", "album", null, durationMs = 180_000)

    private fun TestScope.player(
        resolve: suspend (QueueEntry) -> DesktopPlaybackRequest,
        starts: MutableList<DesktopPlaybackStart> = mutableListOf(),
        positions: MutableList<Pair<Int, Long>> = mutableListOf(),
        recommendationSource: DesktopAutoplaySource = DesktopAutoplaySource(),
        online: () -> Boolean = { true },
        downloaded: (SearchSong) -> Boolean = { false },
    ) = MeloXDesktopPlayer(resolve, { starts += it }, backgroundScope,
        StandardTestDispatcher(testScheduler), {}, { index, position -> positions += index to position },
        recommendationSource, online, downloaded)

    @BeforeTest fun resetModes() { MeloXPlaybackModePreferences.reset() }

    @Test fun startupQueueRestoreDoesNotResolveOrStartAudioUntilPlayAndRetainsSeek() = runTest {
        var resolutions = 0
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ resolutions++; DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        // Use the public startup entry point, with the actual persisted queue.
        player.setMediaItems(listOf(song(1), song(2)), 1, 42_000)
        MeloXPlaybackQueueStore.save(player)
        player.restore(emptyList(), 0, 0)
        MeloXSettingsPreferences.setBoolean("playback_save_queue", true)
        PlaybackCommands.restoreLastQueue()
        advanceTimeBy(10_000); runCurrent()
        assertEquals(0, resolutions)
        assertTrue(starts.isEmpty())
        assertEquals("2", AudioPlayer.currentTrack.value?.id?.value)
        assertEquals(42_000L, AudioPlayer.getCurrentPositionMs())
        assertFalse(AudioPlayer.state.value.isLoading)
        assertTrue(AudioPlayer.state.value.isPaused)
        player.seekTo(65_000)
        player.play(); runCurrent()
        assertEquals(1, resolutions)
        assertEquals(65_000L, starts.single().positionMs)
        assertTrue(starts.single().playWhenReady)
        assertEquals("2", starts.single().track.id.value)
    }

    @Test fun stopCancelsResolutionKeepsQueueAndResumesOnlyOnPlay() = runTest {
        val pending = CompletableDeferred<DesktopPlaybackRequest>()
        var resolutions = 0
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ if (++resolutions == 1) pending.await() else DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 1, 42_000)
        player.prepare(); player.play(); runCurrent()
        player.stop()
        pending.complete(DesktopPlaybackRequest("stale")); runCurrent()
        assertTrue(starts.isEmpty())
        assertFalse(player.playWhenReady)
        assertEquals(2, player.mediaItemCount)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(42_000L, AudioPlayer.getCurrentPositionMs())
        player.play(); runCurrent()
        assertEquals(2, resolutions)
        assertEquals(42_000L, starts.single().positionMs)
        assertTrue(starts.single().playWhenReady)
    }

    @Test fun collectionIdsSurviveQueuePersistenceAndBecomePlaybackMetadata() = runTest {
        val player = player({ DesktopPlaybackRequest("unused") })
        PlaybackCommands.adoptController(player)
        val selected = song(1).copy(albumId = 32311, artistId = 6452)
        player.setMediaItems(listOf(selected), 0, 42_000)
        MeloXPlaybackQueueStore.save(player)
        val saved = assertNotNull(MeloXPlaybackQueueStore.read())
        assertEquals(32311L, saved.items.single().song.albumId)
        assertEquals(6452L, saved.items.single().song.artistId)
        player.restore(saved.items, 0, saved.positionMs)
        player.prepare()
        assertEquals("32311", AudioPlayer.currentTrack.value?.album?.id?.value)
        assertEquals("6452", AudioPlayer.currentTrack.value?.artists?.single()?.id?.value)
    }

    @Test fun jellyfinServerAndDuplicateMembershipIdsSurviveQueuePersistence() = runTest {
        val player = player({ DesktopPlaybackRequest("unused") })
        PlaybackCommands.adoptController(player)
        val tracks = listOf("entry-one", "entry-two").map { entry ->
            MusicTrack(MusicResourceId(MusicSource.Jellyfin, "same-audio"), "generated", emptyList(),
                providerMetadata = ProviderTrackMetadata.Jellyfin("server-one", "playlist-one", entry))
        }
        player.setMediaItems(tracks.mapIndexed { index, track -> song(index.toLong() + 1).copy(providerTrack = track) }, 1, 2400)
        MeloXPlaybackQueueStore.save(player)
        val saved = assertNotNull(MeloXPlaybackQueueStore.read())
        assertEquals(tracks.map { it.providerMetadata }, saved.items.map { it.song.providerTrack!!.providerMetadata })
        assertEquals(1, saved.index)
        assertEquals(2400L, saved.positionMs)
    }

    @Test fun clickingARepeatedProviderEntryStartsThatQueuePosition() = runTest {
        val player = player({ DesktopPlaybackRequest("resolved") })
        PlaybackCommands.adoptController(player)
        val tracks = listOf("first", "second").map { entry ->
            MusicTrack(MusicResourceId(MusicSource.YouTubeMusic, "same-video"), "Video", emptyList(),
                providerMetadata = ProviderTrackMetadata.YouTube("same-video", entry))
        }
        val songs = tracks.map(melox.music.provider.MeloXLegacyUiBridge::track)
        PlaybackCommands.playQueue(songs, songs[1].id, selectedQueueIndex = 1)
        runCurrent()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("second", (player.snapshot()[1].providerTrack?.providerMetadata as ProviderTrackMetadata.YouTube).setVideoId)
    }

    @Test fun localContentLocationSurvivesQueuePersistenceForDeferredPlayback() = runTest {
        val player = player({ DesktopPlaybackRequest("unused") })
        PlaybackCommands.adoptController(player)
        val metadata = ProviderTrackMetadata.Local("file:///C:/Music/%E6%AD%8C%20%2B.wav", "local-key")
        val track = MusicTrack(MusicResourceId(MusicSource.Local, "local-key"), "local", emptyList(), providerMetadata = metadata)
        player.setMediaItems(listOf(song(1).copy(providerTrack = track)), 0, 4200)
        MeloXPlaybackQueueStore.save(player)
        val restored = assertNotNull(MeloXPlaybackQueueStore.read())
        assertEquals(metadata, restored.items.single().song.providerTrack?.providerMetadata)
        assertEquals(4200L, restored.positionMs)
    }

    @Test fun explicitClearStopsAudioAndCannotRestoreStaleQueueOrAcceptLateCallbacks() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("unused") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.prepare(); player.play(); runCurrent()
        MeloXPlaybackQueueStore.save(player)
        player.clearQueue()
        starts.single().onEnded()
        starts.single().onFailure(AudioPlaybackFailure(AudioFailureKind.Content, java.io.IOException("old")))
        runCurrent()
        assertNull(MeloXPlaybackQueueStore.read())
        assertNull(AudioPlayer.currentTrack.value)
        assertEquals(-1, PlaybackCommands.queue.value.index)
        assertTrue(PlaybackCommands.queue.value.songs.isEmpty())
        assertFalse(player.playWhenReady)
        assertEquals(1, starts.size)
    }

    @AfterTest fun cleanup() {
        PlaybackCommands.playerController()?.release()
        PlaybackCommands.activeController = null
        AudioPlayer.stop()
    }

    @Test fun pauseAndSeekDuringResolutionAreAppliedToTheDecoderStart() = runTest {
        val resolved = CompletableDeferred<DesktopPlaybackRequest>()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ resolved.await() }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        assertEquals("1", AudioPlayer.currentTrack.value?.id?.value)
        assertTrue(AudioPlayer.state.value.isLoading)
        player.seekTo(42_000)
        player.pause()
        resolved.complete(DesktopPlaybackRequest("resolved-1"))
        runCurrent()
        val start = starts.single()
        assertEquals(42_000L, start.positionMs)
        assertFalse(start.playWhenReady)
        assertFalse(player.playWhenReady)
        assertEquals(42_000L, player.currentPosition)
    }

    @Test fun aLateCancelledResolverCannotStartThePreviousTrack() = runTest {
        val first = CompletableDeferred<DesktopPlaybackRequest>()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ entry ->
            if (entry.song.id == 1L) withContext(NonCancellable) { first.await() }
            else DesktopPlaybackRequest("resolved-2")
        }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        player.next()
        runCurrent()
        first.complete(DesktopPlaybackRequest("stale-1"))
        runCurrent()
        assertEquals(listOf("2"), starts.map { it.track.id.value })
        assertEquals("2", AudioPlayer.currentTrack.value?.id?.value)
        assertNull(AudioPlayer.state.value.error)
    }

    @Test fun queueReorderDuringResolutionKeepsTheSelectedEntryPlayable() = runTest {
        val resolved = CompletableDeferred<DesktopPlaybackRequest>()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ resolved.await() }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2), song(3)), 1, 0)
        player.prepare()
        runCurrent()
        player.move(0, 2)
        assertEquals(0, player.currentMediaItemIndex)
        resolved.complete(DesktopPlaybackRequest("resolved-2"))
        runCurrent()
        assertEquals("2", starts.single().track.id.value)
    }

    @Test fun resolutionFailurePublishesTheNewTrackAndCanBeRetried() = runTest {
        var attempts = 0
        val starts = mutableListOf<DesktopPlaybackStart>()
        val errors = mutableListOf<Throwable>()
        AudioPlayer.selectTrack(MusicTrack(MusicResourceId(MusicSource.Netease, "old"), "old", emptyList()))
        val player = player({
            if (++attempts == 1) throw java.io.IOException("provider unavailable")
            DesktopPlaybackRequest("retry")
        }, starts)
        player.onFailure = { errors += it }
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(9)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        assertEquals("9", AudioPlayer.currentTrack.value?.id?.value)
        assertEquals("provider unavailable", AudioPlayer.state.value.error)
        assertFalse(AudioPlayer.state.value.isLoading)
        assertFalse(player.playWhenReady)
        assertEquals(1, errors.size)
        player.togglePlay()
        runCurrent()
        assertEquals("9", starts.single().track.id.value)
        assertTrue(starts.single().playWhenReady)
    }

    @Test fun periodicPositionUsesTheAndroidTwoSecondIntervalAndStopsOnRelease() = runTest {
        val positions = mutableListOf<Pair<Int, Long>>()
        val pending = CompletableDeferred<DesktopPlaybackRequest>()
        val player = player({ pending.await() }, positions = positions)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 1, 17_000)
        player.prepare()
        runCurrent()
        advanceTimeBy(1_999)
        runCurrent()
        assertTrue(positions.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1 to 17_000L), positions)
        player.seekTo(0)
        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(1 to 0L, positions.last())
        player.release()
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(2, positions.size)
    }

    @Test fun persistedProviderQueueRestoresIndexPositionIdentityAndStartsPaused() = runTest {
        val store = getPreferences("melox_playback_queue")
        store.clear()
        MeloXSettingsPreferences.setBoolean("playback_save_queue", true)
        val youtube = MusicTrack(MusicResourceId(MusicSource.YouTubeMusic, "video-9"), "video", emptyList(),
            durationMs = 180_000, providerMetadata = ProviderTrackMetadata.YouTube("video-9", "playlist-entry-9"))
        val writer = player({ DesktopPlaybackRequest("resolved") })
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            writer.setMediaItems(listOf(song(1), SearchSong(0, "video", "artist", "album", null, durationMs = 180_000,
                providerTrack = youtube)), 1, 17_000)
            writer.prepare()
            writer.play()
            val expectedIds = writer.entries().map { it.entryId }
            MeloXPlaybackQueueStore.save(writer)
            writer.release()
            val starts = mutableListOf<DesktopPlaybackStart>()
            val reader = player({ DesktopPlaybackRequest("restored") }, starts)
            PlaybackCommands.adoptController(reader)
            PlaybackCommands.restoreLastQueue()
            runCurrent()
            assertEquals(1, reader.currentMediaItemIndex)
            assertEquals(17_000L, reader.currentPosition)
            assertEquals(expectedIds, reader.entries().map { it.entryId })
            assertEquals(youtube.id, reader.snapshot()[1].providerTrack?.id)
            assertEquals("playlist-entry-9", (reader.snapshot()[1].providerTrack?.providerMetadata as? ProviderTrackMetadata.YouTube)?.setVideoId)
            assertFalse(reader.playWhenReady)
            assertTrue(starts.isEmpty(), "Paused startup must not allocate a decoder")
            assertEquals(17_000L, AudioPlayer.getCurrentPositionMs())
            assertTrue(AudioPlayer.state.value.isPaused)
            // A second initialization must not overwrite a live queue/seek.
            reader.seekTo(0)
            PlaybackCommands.restoreLastQueue()
            assertEquals(0L, reader.currentPosition)
            reader.play(); runCurrent()
            assertTrue(starts.single().playWhenReady)
            assertEquals(0L, starts.single().positionMs)
            assertEquals(youtube.id, starts.single().track.id)
            reader.release()
        } finally {
            writer.release()
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun repeatAllRestartsASingleItemInsteadOfLeavingItFinished() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1)), 0, 9_000)
        player.setRepeatMode(PlaybackCommands.REPEAT_ALL)
        player.prepare()
        player.play()
        runCurrent()
        starts.last().onEnded()
        runCurrent()
        assertEquals(listOf(9_000L, 0L), starts.map { it.positionMs })
        assertTrue(starts.last().playWhenReady)
    }

    @Test fun shuffleOnlyReordersTheFutureAndKeepsManualEntriesAheadOfBaseTracks() = runTest {
        val pending = CompletableDeferred<DesktopPlaybackRequest>()
        MeloXPlaybackModePreferences.setShuffle(false)
        val player = player({ pending.await() })
        PlaybackCommands.adoptController(player)
        player.setMediaItems((1L..6L).map(::song), 2, 21_000)
        player.prepare()
        player.addMediaItem(player.mediaItemCount, song(99), PlaybackCommands.QUEUE_ORIGIN_MANUAL)
        val head = player.entries().take(3)
        val selectedId = AudioPlayer.currentTrack.value?.id
        try {
            player.setShuffleEnabled(true)
            assertEquals(head, player.entries().take(3))
            assertEquals(2, player.currentMediaItemIndex)
            assertEquals(99L, player.snapshot()[3].id)
            assertEquals(selectedId, AudioPlayer.currentTrack.value?.id)
            assertEquals(21_000L, AudioPlayer.getCurrentPositionMs())
            player.setShuffleEnabled(false)
            assertEquals(listOf(1L, 2L, 3L, 99L, 4L, 5L, 6L), player.snapshot().map { it.id })
            assertEquals(head, player.entries().take(3))
        } finally { MeloXPlaybackModePreferences.setShuffle(false) }
    }

    @Test fun qualityChangePreservesQueueEntryIdentityAndPausedZeroPosition() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 1, 12_000)
        player.prepare()
        runCurrent()
        player.pause()
        player.seekTo(0)
        val entries = player.entries()
        val quality = MusicQualityPreferences.read()
        try {
            PlaybackCommands.changeQuality(MusicQuality.High)
            runCurrent()
            assertEquals(entries, player.entries())
            assertEquals(1, player.currentMediaItemIndex)
            assertEquals(0L, starts.last().positionMs)
            assertFalse(starts.last().playWhenReady)
        } finally { MusicQualityPreferences.write(quality) }
    }

    private fun mediaFailure(kind: AudioFailureKind = AudioFailureKind.Content) =
        AudioPlaybackFailure(kind, java.io.IOException("decoder failure"))

    @Test fun decoderFailureSkipsToNextEntryEvenInRepeatOneMode() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.setRepeatMode(PlaybackCommands.REPEAT_ONE)
        player.prepare()
        player.play()
        runCurrent()
        starts.single().onFailure(mediaFailure())
        runCurrent()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(listOf("1", "2"), starts.map { it.track.id.value })
        assertTrue(starts.last().playWhenReady)
        assertEquals(0L, starts.last().positionMs)
    }

    @Test fun tailRecoveryRepreparesPausedPositionOnceAndExplicitPlayCanRetryAgain() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val errors = mutableListOf<Throwable>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        player.onFailure = { errors += it }
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1)), 0, 0)
        player.prepare()
        runCurrent()
        player.seekTo(39_000)
        player.pause()
        starts.single().onFailure(mediaFailure())
        runCurrent()
        assertEquals(39_000L, starts.last().positionMs)
        assertFalse(starts.last().playWhenReady)
        assertTrue(errors.isEmpty())
        starts.last().onFailure(mediaFailure())
        runCurrent()
        assertEquals(2, starts.size)
        assertEquals(1, errors.size)
        assertFalse(AudioPlayer.state.value.isFinished)
        assertEquals("decoder failure", AudioPlayer.state.value.error)
        player.play()
        runCurrent()
        assertEquals(3, starts.size)
        assertEquals(39_000L, starts.last().positionMs)
        assertTrue(starts.last().playWhenReady)
    }

    @Test fun missingOutputDoesNotConsumeTheWholeQueue() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val errors = mutableListOf<Throwable>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        player.onFailure = { errors += it }
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2), song(3)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        starts.single().onFailure(mediaFailure(AudioFailureKind.Output))
        runCurrent()
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(1, starts.size)
        assertFalse(player.playWhenReady)
        assertEquals(AudioFailureKind.Output, (errors.single() as AudioPlaybackFailure).kind)
    }

    @Test fun offlineFailureAndNaturalEndPreferDownloadedFutureEntries() = runTest {
        for (error in listOf(true, false)) {
            val starts = mutableListOf<DesktopPlaybackStart>()
            val player = player({ DesktopPlaybackRequest("resolved") }, starts,
                online = { false }, downloaded = { it.id == 3L })
            PlaybackCommands.adoptController(player)
            player.setMediaItems((1L..4L).map(::song), 0, 0)
            player.prepare()
            player.play()
            runCurrent()
            if (error) starts.single().onFailure(mediaFailure()) else starts.single().onEnded()
            runCurrent()
            assertEquals(2, player.currentMediaItemIndex)
            assertEquals(listOf("1", "3"), starts.map { it.track.id.value })
            assertEquals(listOf(1L, 2L, 3L, 4L), player.snapshot().map { it.id })
            assertTrue(starts.last().playWhenReady)
            player.release()
        }
    }

    @Test fun callbacksFromAnOlderPlayOfTheSameTrackCannotAdvanceOrFailTheNewSelection() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(1), song(2)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        val old = starts.single()
        player.seekToIndex(1)
        runCurrent()
        old.onEnded()
        old.onFailure(mediaFailure())
        runCurrent()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(2, starts.size)
        assertNull(AudioPlayer.state.value.error)
        player.release()
        starts.last().onEnded()
        starts.last().onFailure(mediaFailure())
        runCurrent()
        assertEquals(-1, player.currentMediaItemIndex)
        assertNull(AudioPlayer.currentTrack.value)
    }

    @Test fun userPauseBeforeQueuedEndDispatchPreventsAutomaticAdvance() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        starts.single().onEnded()
        player.pause()
        runCurrent()
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(1, starts.size)
        assertFalse(player.playWhenReady)
    }

    @Test fun userSeekBeforeQueuedEndDispatchKeepsTheCurrentTrackAndNewPosition() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.prepare()
        player.play()
        runCurrent()
        starts.single().onEnded()
        player.seekTo(25_000)
        runCurrent()
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(25_000L, player.currentPosition)
        assertEquals(1, starts.size)
        assertTrue(player.playWhenReady)
    }

    @Test fun releasingAnOldControllerCannotStopTheNewControllersSelectedAudio() = runTest {
        val first = player({ DesktopPlaybackRequest("first") })
        PlaybackCommands.adoptController(first)
        first.setMediaItems(listOf(song(1)), 0, 0)
        first.prepare()
        runCurrent()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val second = player({ DesktopPlaybackRequest("second") }, starts)
        PlaybackCommands.adoptController(second)
        second.setMediaItems(listOf(song(2)), 0, 19_000)
        second.prepare()
        runCurrent()
        first.release()
        assertEquals("2", AudioPlayer.currentTrack.value?.id?.value)
        assertEquals(19_000L, AudioPlayer.getCurrentPositionMs())
        assertSame(second, PlaybackCommands.playerController())
        assertTrue(AudioPlayer.state.value.isLoading)
    }

    @Test fun exhaustedRepeatAllQueueStopsInsteadOfSpinningOnBrokenEntries() = runTest {
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts)
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1), song(2)), 0, 0)
        player.setRepeatMode(PlaybackCommands.REPEAT_ALL)
        player.prepare()
        player.play()
        runCurrent()
        repeat(3) {
            starts.last().onFailure(mediaFailure())
            runCurrent()
        }
        assertEquals(listOf("1", "2", "2"), starts.map { it.track.id.value })
        assertFalse(player.playWhenReady)
        assertNotNull(AudioPlayer.state.value.error)
    }

    @Test fun recommendationArrivingAfterNaturalEndAppendsAndResumesTheNewEntry() = runTest {
        val recommendations = CompletableDeferred<List<SearchSong>>()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts,
            recommendationSource = object : DesktopAutoplaySource() {
                override suspend fun recommendations(song: SearchSong) = recommendations.await()
            })
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1)), 0, 0)
        player.prepare()
        player.play()
        player.setAutoplayEnabled(true)
        runCurrent()
        starts.single().onEnded()
        runCurrent()
        assertFalse(player.playWhenReady)
        recommendations.complete(listOf(song(2), song(3)))
        runCurrent()
        assertEquals(listOf(1L, 2L, 3L), player.snapshot().map { it.id })
        assertEquals(1, player.currentMediaItemIndex)
        assertTrue(starts.last().playWhenReady)
        assertEquals("2", starts.last().track.id.value)
    }

    @Test fun explicitPauseAfterNaturalEndPreventsLateRecommendationsFromRestartingAudio() = runTest {
        val recommendations = CompletableDeferred<List<SearchSong>>()
        val starts = mutableListOf<DesktopPlaybackStart>()
        val player = player({ DesktopPlaybackRequest("resolved") }, starts,
            recommendationSource = object : DesktopAutoplaySource() {
                override suspend fun recommendations(song: SearchSong) = recommendations.await()
            })
        PlaybackCommands.adoptController(player)
        player.setMediaItems(listOf(song(1)), 0, 0)
        player.prepare()
        player.play()
        player.setAutoplayEnabled(true)
        runCurrent()
        starts.single().onEnded()
        runCurrent()
        player.pause()
        recommendations.complete(listOf(song(2)))
        runCurrent()
        assertEquals(listOf(1L, 2L), player.snapshot().map { it.id })
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(1, starts.size)
        assertFalse(player.playWhenReady)
    }
}
