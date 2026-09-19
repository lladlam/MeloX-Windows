package melox.playback

import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.audio.MusicQualityRuntime
import melox.download.MeloXDownloadStore
import melox.model.SearchSong
import melox.network.MeloXNetworkAvailability
import melox.platform.logDebug
import melox.platform.logInfo
import melox.platform.logWarn
import java.io.IOException
import java.util.UUID

/**
 * Desktop playback commands. Replaces Media3/MediaController with a simple
 * in-process player bridge. The desktop player will use these commands directly.
 */
object PlaybackCommands {
    private const val TAG = "MeloXPlayback"
    const val QUEUE_ORIGIN_KEY = "melox.queue.origin"
    const val QUEUE_ORIGIN_BASE = "base"
    const val QUEUE_ORIGIN_MANUAL = "manual"
    const val QUEUE_ORIGINAL_INDEX_KEY = "melox.queue.original_index"
    const val QUEUE_ENTRY_ID_KEY = "melox.queue.entry_id"
    const val QUEUE_ORIGINAL_INDEX_UNSET = -1
    const val HEART_MODE_KEY = "melox.playback.heart_mode"

    @Volatile
    internal var activeController: Any? = null

    fun currentSongId(): Long? = (activeController as? MeloXDesktopPlayer)?.currentMediaItem?.toLongOrNull()

    fun playQueue(
        songs: List<SearchSong>,
        selectedSongId: Long,
        startPositionMs: Long = 0L,
        heartMode: Boolean = false,
        onFailure: ((Throwable) -> Unit)? = null,
    ) {
        val selectedProviderTrack = songs.firstOrNull { it.id == selectedSongId }?.providerTrack
        if (selectedProviderTrack != null) {
            val providerTracks = songs.mapNotNull(SearchSong::providerTrack)
            if (providerTracks.isEmpty()) return
            ProviderPlaybackCommands.playQueue(
                tracks = providerTracks,
                selectedTrackId = selectedProviderTrack.id,
                startPositionMs = startPositionMs,
                onFailure = onFailure,
            )
            return
        }

        val quality = MusicQualityPreferences.read()
        MusicQualityRuntime.selected = quality
        MusicQualityRuntime.clear()
        CrossProviderPlaybackRuntime.clear()
        val downloads = MeloXDownloadStore.instance()
        val offline = !MeloXNetworkAvailability.isOnline()
        val sourceStartIndex = songs.indexOfFirst { it.id == selectedSongId }
            .takeIf { it >= 0 } ?: 0
        val playable = songs.mapIndexed { index, song -> index to song }
            .let { indexed ->
                if (offline) indexed.filter { (_, song) -> downloads.contains(song.id) }
                else indexed
            }
        if (playable.isEmpty()) {
            onFailure?.invoke(IOException("离线状态下没有可播放的已下载歌曲"))
            return
        }
        val selectedPair = playable.firstOrNull { it.first == sourceStartIndex }
            ?: playable.firstOrNull { it.first > sourceStartIndex }
            ?: playable.first()
        val queue = playable.map { (_, song) -> song }
        val startIndex = queue.indexOfFirst { it.id == selectedPair.second.id }.coerceAtLeast(0)

        val player = MeloXDesktopPlayer()
        player.setMediaItems(queue, startIndex, startPositionMs)
        player.prepare()
        player.play()
        activeController = player

        logDebug(TAG, "Playback queue dispatched: size=${queue.size}, start=$startIndex, offline=$offline, quality=${quality.apiLevel}")
    }

    fun addToQueue(song: SearchSong) {
        val quality = MusicQualityPreferences.read()
        val controller = activeController as? MeloXDesktopPlayer ?: run {
            playQueue(listOf(song), song.id)
            return
        }
        val downloads = MeloXDownloadStore.instance()
        if (!MeloXNetworkAvailability.isOnline() && !downloads.contains(song.id)) return
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song)
    }

    fun playNext(song: SearchSong) {
        val quality = MusicQualityPreferences.read()
        val controller = activeController as? MeloXDesktopPlayer ?: run {
            playQueue(listOf(song), song.id)
            return
        }
        val downloads = MeloXDownloadStore.instance()
        if (!MeloXNetworkAvailability.isOnline() && !downloads.contains(song.id)) return
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song)
    }

    fun changeQuality(quality: MusicQuality) {
        MusicQualityPreferences.write(quality)
        MusicQualityRuntime.selected = quality
        MusicQualityRuntime.clear()
        CrossProviderPlaybackRuntime.clear()
    }

    fun setExplicitShuffle(player: Any, enabled: Boolean) {
        MeloXPlaybackModePreferences.setShuffle(enabled)
    }

    fun prioritizeManualQueue(player: Any) = Unit

    internal fun adoptController(controller: Any) {
        activeController = controller
    }

    internal fun mediaItemFor(
        song: SearchSong,
        quality: MusicQuality = MusicQualityRuntime.selected,
        queueOrigin: String = QUEUE_ORIGIN_BASE,
        originalIndex: Int = QUEUE_ORIGINAL_INDEX_UNSET,
        artworkOverride: String? = null,
        heartMode: Boolean = MeloXPlaybackModeRuntime.heartModeActive,
    ): Any = song
}

/**
 * Minimal desktop player bridge. Replaces Media3 MediaController with a simple
 * in-process player that the desktop UI can drive through PlaybackCommands.
 */
class MeloXDesktopPlayer {
    var currentMediaItemIndex: Int = -1
        private set
    var mediaItemCount: Int = 0
        private set
    var currentPosition: Long = 0L
        private set
    var playWhenReady: Boolean = false
        private set

    private val items = mutableListOf<SearchSong>()

    val currentMediaItem: String?
        get() = items.getOrNull(currentMediaItemIndex)?.id?.toString()

    fun setMediaItems(items: List<SearchSong>, startIndex: Int, startPositionMs: Long) {
        this.items.clear()
        this.items.addAll(items)
        mediaItemCount = items.size
        currentMediaItemIndex = startIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        currentPosition = startPositionMs
    }

    fun addMediaItem(index: Int, song: SearchSong) {
        items.add(index.coerceIn(0, items.size), song)
        mediaItemCount = items.size
    }

    fun prepare() {}
    fun play() { playWhenReady = true }
    fun pause() { playWhenReady = false }
    fun release() { items.clear(); mediaItemCount = 0; currentMediaItemIndex = -1 }
}