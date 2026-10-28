package melox.playback

import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.audio.MusicQualityRuntime
import melox.music.model.AudioQualityTier
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.model.SearchSong
import java.util.UUID

/** Queue entry point for QQ/Kugou and future mixed-provider results. */
object ProviderPlaybackCommands {
    fun playQueue(
        tracks: List<MusicTrack>,
        selectedTrackId: MusicResourceId,
        startPositionMs: Long = 0L,
        selectedQueueIndex: Int? = null,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        if (tracks.isEmpty()) return
        ProviderPlaybackRuntime.initialize()
        val neteaseQuality = MusicQualityPreferences.read()
        MusicQualityRuntime.selected = neteaseQuality
        val qualityTier = neteaseQuality.toCommonTier()
        val startIndex = selectedQueueIndex?.takeIf { it in tracks.indices && tracks[it].id == selectedTrackId }
            ?: tracks.indexOfFirst { it.id == selectedTrackId }.coerceAtLeast(0)
        val items = tracks.mapIndexed { index, track ->
            track.toMediaItem(
                neteaseQuality = neteaseQuality,
                qualityTier = qualityTier,
                originalIndex = index,
            )
        }
        val player = (PlaybackCommands.activeController) as? MeloXDesktopPlayer ?: run {
            val newPlayer = MeloXDesktopPlayer()
            PlaybackCommands.adoptController(newPlayer)
            newPlayer
        }
        player.onFailure = onFailure
        player.setMediaItems(
            tracks.map { track -> melox.music.provider.MeloXLegacyUiBridge.track(track) },
            startIndex,
            startPositionMs,
        )
        player.prepare()
        player.play()
        PlaybackCommands.publishQueue(player)
    }

    internal fun mediaItemFor(
        track: MusicTrack,
        neteaseQuality: MusicQuality = MusicQuality.Standard,
        qualityTier: AudioQualityTier = neteaseQuality.toCommonTier(),
        queueOrigin: String = PlaybackCommands.QUEUE_ORIGIN_BASE,
        originalIndex: Int = PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET,
    ): Any = track.toMediaItem(neteaseQuality, qualityTier, queueOrigin, originalIndex)

    private fun MusicTrack.toMediaItem(
        neteaseQuality: MusicQuality,
        qualityTier: AudioQualityTier,
        queueOrigin: String = PlaybackCommands.QUEUE_ORIGIN_BASE,
        originalIndex: Int,
    ): Any = this
}

internal fun MusicQuality.toCommonTier(): AudioQualityTier = when (this) {
    MusicQuality.Standard -> AudioQualityTier.Standard
    MusicQuality.High -> AudioQualityTier.High
    MusicQuality.Lossless -> AudioQualityTier.Lossless
    MusicQuality.HiResolution -> AudioQualityTier.HiResolution
    MusicQuality.HighDefinitionSurround,
    MusicQuality.ImmersiveSurround -> AudioQualityTier.Immersive
    MusicQuality.UltraClearMaster -> AudioQualityTier.Master
}

internal fun normalizedQueueDurationMs(durationMs: Long?): Long = durationMs?.coerceAtLeast(0L) ?: 0L
