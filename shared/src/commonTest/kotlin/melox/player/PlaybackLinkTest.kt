package melox.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import melox.model.SearchSong
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.playback.MeloXDesktopPlayer
import melox.playback.PlaybackCommands
import melox.settings.MeloXSettingsRuntime

class PlaybackLinkTest {
    @Test
    fun decoderArgsMatchTheProcessCommand() {
        AudioPlayer.decoderCommand = listOf("/opt/melox/ffmpeg")
        val args = AudioPlayer.decoderArgsFor(
            "https://music.example/a.mp3",
            mapOf("Referer" to "https://music.163.com", "Cookie" to "MUSIC_U=1"),
            30_000L,
        )
        assertEquals("/opt/melox/ffmpeg", args.first())
        assertTrue(args.contains("-ss"))
        assertTrue(args.contains("30.000"))
        assertTrue(args.contains("-headers"))
        val headers = args[args.indexOf("-headers") + 1]
        assertTrue(headers.contains("Referer: https://music.163.com"))
        assertTrue(headers.contains("Cookie: MUSIC_U=1"))
        assertTrue(args.contains("-f") && args[args.indexOf("-f") + 1] == "s16le")
        assertTrue(args.contains("https://music.example/a.mp3"))
    }

    @Test
    fun positionIncludesDecoderOffset() {
        assertEquals(30_000L, AudioPlayer.positionMs(30_000L, 0L))
        assertEquals(31_000L, AudioPlayer.positionMs(30_000L, AudioPlayer.PCM_SAMPLE_RATE.toLong()))
        assertEquals(15_000L, AudioPlayer.positionMs(15_000L, 0L))
    }

    @Test
    fun playNextPrecedesManualQueueAndManualQueuePrecedesBaseTracks() {
        val player = MeloXDesktopPlayer()
        player.setMediaItems(listOf(song(1), song(2)), 0, 0L)
        PlaybackCommands.adoptController(player)
        PlaybackCommands.addToQueue(song(3, provider = true))
        PlaybackCommands.playNext(song(4, provider = true))
        assertEquals(listOf(1L, 4L, 3L, 2L), player.snapshot().map { it.id })
    }

    @Test
    fun providerTrackSurvivesQueueItem() {
        val track = MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, "qq-9"),
            title = "qq",
            artists = emptyList(),
        )
        val player = MeloXDesktopPlayer()
        player.setMediaItems(listOf(SearchSong(0L, "qq", "", "", null, providerTrack = track)), 0, 0L)
        assertEquals(track, player.snapshot().single().providerTrack)
        assertFalse(player.snapshot().single().providerTrack?.id?.source == MusicSource.Netease)
    }

    @Test
    fun classicQueueKeepsPlainNeteaseSongsAndDoesNotRestartCurrent() {
        val songs = listOf(song(1), song(2), song(3))
        val player = MeloXDesktopPlayer()
        player.setMediaItems(songs, 1, 12_000L)
        PlaybackCommands.adoptController(player)
        PlaybackCommands.publishQueue(player)
        val track = melox.music.model.MusicTrack(
            id = melox.music.model.MusicResourceId(melox.music.model.MusicSource.Netease, "2"),
            title = "s2",
            artists = emptyList(),
        )
        val queued = PlaybackCommands.queue.value
        val index = melox.playback.classicQueueIndex(queued.songs, queued.index, track)
        assertEquals(1, index)
        assertEquals(listOf(1L, 2L, 3L), queued.songs.map { it.id })
        player.seekToIndex(1)
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(12_000L, player.currentPosition)
        player.seekToIndex(0)
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(0L, player.currentPosition)
        val qq = melox.music.model.MusicTrack(
            id = melox.music.model.MusicResourceId(melox.music.model.MusicSource.QQMusic, "qq-9"),
            title = "qq",
            artists = emptyList(),
        )
        val qqSong = SearchSong(0L, "qq", "", "", null, providerTrack = qq)
        assertTrue(qqSong.providerTrack?.id?.source != melox.music.model.MusicSource.Netease)
        assertTrue((qqSong.id.takeIf { it > 0L && qqSong.providerTrack?.id?.source == melox.music.model.MusicSource.Netease }) == null)
    }

    @Test
    fun miniBarsFollowPcmEnergyAndIdleIsNotFixedPulse() {
        val runtime = melox.playback.MeloXAudioReactiveRuntime
        runtime.clear()
        val idle = runtime.sample("song")
        val idleBars = melox.playback.miniBarFractions(false, idle)
        assertTrue(idleBars.all { it <= 0.10f })
        assertFalse(idleBars.any { kotlin.math.abs(it - 0.6f) < 0.01f })
        val loud = ShortArray(200) { 16000 }
        runtime.publishPcm("song", loud, loud.size, 1f)
        val loudSample = runtime.sample("song")
        val quiet = ShortArray(200) { 1000 }
        runtime.publishPcm("song", quiet, quiet.size, 1f)
        val quietSample = runtime.sample("song")
        assertTrue(loudSample.energy > quietSample.energy)
        val loudBars = melox.playback.miniBarFractions(true, loudSample)
        val quietBars = melox.playback.miniBarFractions(true, quietSample)
        assertTrue(loudBars[1] > quietBars[1])
        val analysis = melox.playback.MeloXAutoMixTrackAnalysis(
            bpm = 120.0,
            confidence = 1.0,
            firstAudibleMs = 0L,
            lastAudibleMs = 4_000L,
            beatTimesMs = longArrayOf(0L, 2_000L),
            downbeatTimesMs = longArrayOf(2_000L),
            phraseBoundariesMs = longArrayOf(0L),
            frames = listOf(
                melox.playback.MeloXAutoMixFrame(0L, 0.1f, 0f, 0f, 0f, 0f, 0f),
                melox.playback.MeloXAutoMixFrame(2_000L, 0.9f, 0f, 0f, 0f, 0f, 0f),
            ),
        )
        runtime.attach("song", analysis)
        runtime.publish("song", 0L, true)
        runtime.publishPcm("song", quiet, quiet.size, 1f)
        val atStart = runtime.sample("song")
        runtime.publishPosition("song", 2_000L)
        val later = runtime.sample("song")
        assertTrue(atStart.energy != later.energy || atStart.beat != later.beat || atStart.downbeat != later.downbeat)
        runtime.publish("song", 0L, false)
        val paused = melox.playback.miniBarFractions(false, runtime.sample("song"))
        assertEquals(idleBars, paused)
        runtime.clear()
    }

    @Test
    fun previousRestartsOnlyWhenSettingEnabled() {
        val player = MeloXDesktopPlayer()
        player.setMediaItems(listOf(song(1), song(2)), 1, 6_000L)
        PlaybackCommands.adoptController(player)
        MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = false
        player.previous()
        assertEquals(0, player.currentMediaItemIndex)
        player.setMediaItems(listOf(song(1), song(2)), 1, 6_000L)
        MeloXSettingsRuntime.previousRestartsAfterFiveSeconds = true
        player.previous()
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals(0L, player.currentPosition)
    }

    @Test
    fun locatorUsesContextClassLoaderAndReportsMissingBundle() {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val expected = if (os.contains("win")) "decoder/ffmpeg.exe" else "decoder/ffmpeg"
        assertEquals(expected, FfmpegLocator.resourceName())
        assertFalse(FfmpegLocator.resourceName().endsWith(".exe") && !os.contains("win"))
        assertTrue(FfmpegLocator.command("decoder/not-bundled").isEmpty())
        val previous = Thread.currentThread().contextClassLoader
        val root = java.io.File.createTempFile("melox-dec", "")
        root.delete()
        root.mkdirs()
        val folder = java.io.File(root, "decoder")
        folder.mkdirs()
        java.io.File(folder, "ffmpeg-test").writeText("#!/bin/sh\nexit 0\n")
        val loader = java.net.URLClassLoader(arrayOf(root.toURI().toURL()), null)
        Thread.currentThread().contextClassLoader = loader
        try {
            val located = FfmpegLocator.locate("decoder/ffmpeg-test")
            assertTrue(located is FfmpegLocation.Bundled)
            assertTrue((located as FfmpegLocation.Bundled).file.canExecute())
            assertFalse(located.file.name.endsWith(".exe"))
        } finally {
            Thread.currentThread().contextClassLoader = previous
            root.deleteRecursively()
        }
        assertEquals(15_000L, AudioPlayer.displayedPosition(15_000L, 30_000L, AudioPlayer.PCM_SAMPLE_RATE.toLong() * 5))
        val written = AudioPlayer.PCM_SAMPLE_RATE.toLong() * 5
        val buffered = AudioPlayer.PCM_SAMPLE_RATE.toLong()
        val shown = AudioPlayer.displayedPosition(-1L, 0L, written, buffered)
        val writtenPosition = AudioPlayer.positionMs(0L, written)
        assertTrue(shown < writtenPosition)
        assertEquals(writtenPosition - 1_000L, shown)
    }

    @Test
    fun bundledDecoderEmitsPcmAndIsResolvedAgain() {
        val name = FfmpegLocator.resourceName()
        val loader = javaClass.classLoader
        val resource = loader?.getResource(name)
        val bytes = requireNotNull(resource) { "Missing production decoder $name; run git lfs pull" }.openStream().use { it.readBytes() }
        assertTrue(bytes.size >= 1_048_576, "Production decoder is an LFS pointer or invalid fixture: $name")
        val root = java.io.File.createTempFile("melox-pcm", "")
        root.delete()
        root.mkdirs()
        val folder = java.io.File(root, "decoder")
        folder.mkdirs()
        val binary = java.io.File(folder, java.io.File(name).name)
        binary.writeBytes(bytes)
        binary.setExecutable(true)
        val mp3 = java.io.File(root, "tone.mp3")
        mp3.writeBytes(java.util.Base64.getDecoder().decode(SILENCE_MP3_BASE64))
        val previous = Thread.currentThread().contextClassLoader
        val empty = java.net.URLClassLoader(emptyArray(), null)
        Thread.currentThread().contextClassLoader = empty
        try {
            assertTrue(FfmpegLocator.command(name).isEmpty())
        } finally {
            Thread.currentThread().contextClassLoader = previous
        }
        val bundled = java.net.URLClassLoader(arrayOf(root.toURI().toURL()), null)
        Thread.currentThread().contextClassLoader = bundled
        try {
            val command = FfmpegLocator.command(name)
            assertEquals(1, command.size)
            assertFalse(command.single() == "ffmpeg")
            val args = command + listOf(
                "-hide_banner", "-loglevel", "error", "-nostdin",
                "-i", mp3.absolutePath,
                "-f", "s16le", "-acodec", "pcm_s16le", "-ar", "44100", "-ac", "2",
                "pipe:1",
            )
            val process = ProcessBuilder(args).redirectErrorStream(false).start()
            val pcm = process.inputStream.readBytes()
            val err = process.errorStream.readBytes()
            val code = process.waitFor()
            assertTrue(pcm.isNotEmpty(), "exit=$code stderr=${err.decodeToString()}")
            assertEquals(0, code)
            val again = FfmpegLocator.command(name)
            assertEquals(1, again.size)
            assertTrue(java.io.File(again.single()).canExecute())
        } finally {
            Thread.currentThread().contextClassLoader = previous
            root.deleteRecursively()
        }
    }

    private fun song(id: Long, provider: Boolean = false) = SearchSong(
        id,
        "s$id",
        "a",
        "",
        null,
        providerTrack = if (!provider) null else MusicTrack(
            id = MusicResourceId(MusicSource.QQMusic, id.toString()),
            title = "s$id",
            artists = emptyList(),
        ),
    )

    private companion object {
        const val SILENCE_MP3_BASE64 =
            "SUQzBAAAAAAAIlRTU0UAAAAOAAADTGF2ZjYzLjcuMTAwAAAAAAAAAAAAAAD/+1BkAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABJbmZvAAAADwAAAAkAAAR4AEVFRUVFRUVFRUVFXV1dXV1dXV1dXV10dHR0dHR0dHR0dIuLi4uLi4uLi4uLoqKioqKioqKioqK6urq6urq6urq6utHR0dHR0dHR0dHR6Ojo6Ojo6Ojo6Oj//////////////wAAAABMYXZjNjMuMTQAAAAAAAAAAAAAAAAkA8wAAAAAAAAEePtisboAAAAAAAAAAAAAAAAAAAAA//sQZAAP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAETEFNRTQuMSAoYWxwaGEgMClVVVVVVVVVVVVVVVVVVVVVTEFNRTQuMSAoYWxwaGEgMClVVVVVVVVVVVVVVVVVVVVVVVX/+xBkIg/wAABpAAAACAAADSAAAAEAAAGkAAAAIAAANIAAAARVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVMQU1FNC4xIChhbHBoYSAwKVVVVVVVVVVVVVVVVVVVVVVVVf/7EGRED/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//sQZGYP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAEVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVX/+xBkiA/wAABpAAAACAAADSAAAAEAAAGkAAAAIAAANIAAAARVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7EGSqD/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//sQZMwP8AAAaQAAAAgAAA0gAAABAAABpAAAACAAADSAAAAEVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVX/+xBk3Y/wAABpAAAACAAADSAAAAEAAAGkAAAAIAAANIAAAARVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7EGTdj/AAAGkAAAAIAAANIAAAAQAAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV"
    }
}
