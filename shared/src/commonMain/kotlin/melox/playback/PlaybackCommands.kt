package melox.playback

import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.audio.MusicQualityRuntime
import melox.download.MeloXDownloadStore
import melox.model.SearchSong
import melox.music.model.MusicTrack
import melox.music.provider.PlaybackAccountStore
import melox.network.MeloXNetworkAvailability
import melox.platform.logDebug
import melox.platform.logError
import melox.player.AudioPlayer
import java.io.IOException
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Desktop playback commands. Replaces Media3/MediaController with a simple
 * in-process player bridge. The desktop player will use these commands directly.
 */
data class PlaybackQueueSnapshot(
    val songs: List<SearchSong>,
    val index: Int,
    val origins: List<String> = emptyList(),
    val entryIds: List<String> = emptyList(),
)

data class QueueEntry(
    val song: SearchSong,
    val origin: String,
    val originalIndex: Int,
    val entryId: String,
)

object PlaybackCommands {
    private const val TAG = "MeloXPlayback"
    const val QUEUE_ORIGIN_KEY = "melox.queue.origin"
    const val QUEUE_ORIGIN_BASE = "base"
    const val QUEUE_ORIGIN_MANUAL = "manual"
    const val QUEUE_ORIGINAL_INDEX_KEY = "melox.queue.original_index"
    const val QUEUE_ENTRY_ID_KEY = "melox.queue.entry_id"
    const val QUEUE_ORIGINAL_INDEX_UNSET = -1
    const val HEART_MODE_KEY = "melox.playback.heart_mode"

    const val REPEAT_OFF = 0
    const val REPEAT_ONE = 1
    const val REPEAT_ALL = 2

    @Volatile
    internal var activeController: Any? = null

    private val _queue = MutableStateFlow(PlaybackQueueSnapshot(emptyList(), -1))
    val queue: StateFlow<PlaybackQueueSnapshot> = _queue.asStateFlow()

    internal fun publishQueue(player: MeloXDesktopPlayer) {
        val entries = player.entries()
        _queue.value = PlaybackQueueSnapshot(
            songs = entries.map { it.song },
            index = player.currentMediaItemIndex,
            origins = entries.map { it.origin },
            entryIds = entries.map { it.entryId },
        )
        MeloXPlaybackQueueStore.save(player)
    }

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
        val origins = List(queue.size) { QUEUE_ORIGIN_BASE }
        val originalIndexes = playable.map { it.first }

        val existing = activeController as? MeloXDesktopPlayer
        val player = existing ?: MeloXDesktopPlayer()
        player.setMediaItems(queue, startIndex, startPositionMs, origins, originalIndexes)
        player.prepare()
        player.play()
        activeController = player
        publishQueue(player)

        logDebug(TAG, "Playback queue dispatched: size=${queue.size}, start=$startIndex, offline=$offline, quality=${quality.apiLevel}")
    }

    fun addToQueue(song: SearchSong) {
        val controller = activeController as? MeloXDesktopPlayer ?: run {
            playQueue(listOf(song), song.id)
            return
        }
        val downloads = MeloXDownloadStore.instance()
        if (song.providerTrack == null && !MeloXNetworkAvailability.isOnline() && !downloads.contains(song.id)) return
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song, QUEUE_ORIGIN_MANUAL)
        publishQueue(controller)
    }

    fun playNext(song: SearchSong) {
        val controller = activeController as? MeloXDesktopPlayer ?: run {
            playQueue(listOf(song), song.id)
            return
        }
        val downloads = MeloXDownloadStore.instance()
        if (song.providerTrack == null && !MeloXNetworkAvailability.isOnline() && !downloads.contains(song.id)) return
        val insertion = (controller.currentMediaItemIndex + 1).coerceIn(0, controller.mediaItemCount)
        controller.addMediaItem(insertion, song, QUEUE_ORIGIN_MANUAL)
        publishQueue(controller)
    }

    fun changeQuality(quality: MusicQuality) {
        MusicQualityPreferences.write(quality)
        MusicQualityRuntime.selected = quality
        MusicQualityRuntime.clear()
        CrossProviderPlaybackRuntime.clear()
        val player = activeController as? MeloXDesktopPlayer ?: return
        if (player.playWhenReady) {
            player.prepare()
            player.play()
        }
    }

    fun setExplicitShuffle(player: Any, enabled: Boolean) {
        (player as? MeloXDesktopPlayer)?.setShuffleEnabled(enabled)
            ?: MeloXPlaybackModePreferences.setShuffle(enabled)
    }

    fun prioritizeManualQueue(player: Any) {
        (player as? MeloXDesktopPlayer)?.prioritizeManual()
    }

    fun restoreLastQueue() {
        val persisted = MeloXPlaybackQueueStore.read() ?: return
        val player = activeController as? MeloXDesktopPlayer ?: MeloXDesktopPlayer()
        player.restore(
            items = persisted.items,
            startIndex = persisted.index,
            startPositionMs = persisted.positionMs,
        )
        player.prepare()
        if (persisted.playWhenReady) player.play()
        activeController = player
        publishQueue(player)
    }

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
 * Sequential desktop player. Resolves the current queue item and drives [AudioPlayer].
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

    private val items = mutableListOf<QueueEntry>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resolveJob: Job? = null
    private var generation = 0
    private var pendingSeekMs: Long = 0L
    private var volume: Float = 1f
    private val neteaseResolver = NeteasePlaybackResolver(
        cookieProvider = { PlaybackAccountStore.neteaseCookie() },
        localSourceProvider = { songId -> MeloXDownloadStore.instance().localPlaybackString(songId) },
    )
    private var providerResolver: ProviderPlaybackResolver? = null

    private val _repeatMode = MutableStateFlow(PlaybackCommands.REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(MeloXPlaybackModePreferences.shuffle())
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    val currentMediaItem: String?
        get() = items.getOrNull(currentMediaItemIndex)?.song?.id?.toString()

    init {
        AudioPlayer.onTrackEnded = { onNaturalEnd() }
        AudioPlayer.setVolume(volume)
    }

    fun snapshot(): List<SearchSong> = items.map { it.song }

    internal fun entries(): List<QueueEntry> = items.toList()

    fun setMediaItems(items: List<SearchSong>, startIndex: Int, startPositionMs: Long) {
        setMediaItems(items, startIndex, startPositionMs, emptyList(), emptyList())
    }

    fun setMediaItems(
        items: List<SearchSong>,
        startIndex: Int,
        startPositionMs: Long,
        origins: List<String>,
        originalIndexes: List<Int>,
    ) {
        this.items.clear()
        items.forEachIndexed { index, song ->
            this.items.add(
                QueueEntry(
                    song = song,
                    origin = origins.getOrElse(index) { PlaybackCommands.QUEUE_ORIGIN_BASE },
                    originalIndex = originalIndexes.getOrElse(index) { index },
                    entryId = UUID.randomUUID().toString(),
                ),
            )
        }
        mediaItemCount = this.items.size
        currentMediaItemIndex = if (this.items.isEmpty()) -1 else startIndex.coerceIn(0, this.items.lastIndex)
        currentPosition = startPositionMs.coerceAtLeast(0L)
        pendingSeekMs = currentPosition
        if (_shuffleEnabled.value) applyShuffle(keepCurrentFirst = true)
    }

    fun addMediaItem(index: Int, song: SearchSong) {
        addMediaItem(index, song, PlaybackCommands.QUEUE_ORIGIN_BASE)
    }

    fun addMediaItem(index: Int, song: SearchSong, origin: String) {
        val insertion = index.coerceIn(0, items.size)
        items.add(
            insertion,
            QueueEntry(
                song = song,
                origin = origin,
                originalIndex = PlaybackCommands.QUEUE_ORIGINAL_INDEX_UNSET,
                entryId = UUID.randomUUID().toString(),
            ),
        )
        mediaItemCount = items.size
        if (currentMediaItemIndex >= insertion && currentMediaItemIndex >= 0) {
            currentMediaItemIndex += 1
        } else if (currentMediaItemIndex < 0 && items.isNotEmpty()) {
            currentMediaItemIndex = 0
        }
    }

    internal fun restore(items: List<QueueEntry>, startIndex: Int, startPositionMs: Long) {
        this.items.clear()
        this.items.addAll(items)
        mediaItemCount = this.items.size
        currentMediaItemIndex = if (this.items.isEmpty()) -1 else startIndex.coerceIn(0, this.items.lastIndex)
        currentPosition = startPositionMs.coerceAtLeast(0L)
        pendingSeekMs = currentPosition
        playWhenReady = false
    }

    fun prepare() {
        val index = currentMediaItemIndex
        val entry = items.getOrNull(index) ?: return
        val token = ++generation
        val startAt = pendingSeekMs
        resolveJob?.cancel()
        resolveJob = scope.launch {
            val resolved = runCatching {
                withContext(Dispatchers.IO) { resolve(entry) }
            }.getOrElse { error ->
                logError("MeloXPlayback", "resolve failed: ${error.message}")
                null
            } ?: return@launch
            if (token != generation || currentMediaItemIndex != index) return@launch
            val song = entry.song
            val track = song.providerTrack ?: MusicTrack(
                id = melox.music.model.MusicResourceId(
                    melox.music.model.MusicSource.Netease,
                    song.id.toString(),
                ),
                title = song.name,
                artists = listOf(melox.music.model.MusicArtistRef(name = song.artists)),
                album = song.album.takeIf { it.isNotBlank() }?.let { melox.music.model.MusicAlbumRef(name = it) },
                artworkUrl = song.artworkUrl,
                durationMs = song.durationMs.takeIf { it > 0L },
            )
            AudioPlayer.play(resolved.uri, track, resolved.headers)
            if (startAt > 0L) AudioPlayer.seekTo(startAt)
            pendingSeekMs = 0L
            currentPosition = startAt
        }
    }

    fun play() {
        playWhenReady = true
        if (AudioPlayer.getDurationMs() > 0L && !AudioPlayer.state.value.isPlaying) {
            AudioPlayer.resume()
        } else if (resolveJob?.isActive != true && AudioPlayer.currentTrack.value == null) {
            prepare()
        }
    }

    fun pause() {
        playWhenReady = false
        currentPosition = AudioPlayer.getCurrentPositionMs()
        AudioPlayer.pause()
    }

    fun togglePlay() {
        if (playWhenReady && AudioPlayer.state.value.isPlaying) pause() else play()
    }

    fun release() {
        generation += 1
        resolveJob?.cancel()
        AudioPlayer.stop()
        items.clear()
        mediaItemCount = 0
        currentMediaItemIndex = -1
        currentPosition = 0L
        playWhenReady = false
        pendingSeekMs = 0L
    }

    fun next() {
        if (items.isEmpty()) return
        val nextIndex = when {
            currentMediaItemIndex < items.lastIndex -> currentMediaItemIndex + 1
            _repeatMode.value == PlaybackCommands.REPEAT_ALL -> 0
            else -> return
        }
        seekToIndex(nextIndex)
    }

    fun previous() {
        val position = AudioPlayer.getCurrentPositionMs().takeIf { it > 0L } ?: currentPosition
        if (position > 5000L) {
            seekTo(0L)
            return
        }
        if (items.isEmpty()) return
        val previousIndex = when {
            currentMediaItemIndex > 0 -> currentMediaItemIndex - 1
            _repeatMode.value == PlaybackCommands.REPEAT_ALL -> items.lastIndex
            else -> {
                seekTo(0L)
                return
            }
        }
        seekToIndex(previousIndex)
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        currentPosition = target
        if (AudioPlayer.getDurationMs() > 0L) {
            AudioPlayer.seekTo(target)
        } else {
            pendingSeekMs = target
        }
    }

    fun seekToIndex(index: Int) {
        if (index !in items.indices) return
        currentMediaItemIndex = index
        currentPosition = 0L
        pendingSeekMs = 0L
        prepare()
        if (playWhenReady) play()
        PlaybackCommands.publishQueue(this)
    }

    fun removeAt(index: Int) {
        if (index !in items.indices || items.size <= 1) return
        val removingCurrent = index == currentMediaItemIndex
        items.removeAt(index)
        mediaItemCount = items.size
        when {
            removingCurrent -> {
                currentMediaItemIndex = index.coerceAtMost(items.lastIndex)
                currentPosition = 0L
                pendingSeekMs = 0L
                prepare()
                if (playWhenReady) play()
            }
            index < currentMediaItemIndex -> currentMediaItemIndex -= 1
        }
        PlaybackCommands.publishQueue(this)
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val currentId = items.getOrNull(currentMediaItemIndex)?.entryId
        val entry = items.removeAt(from)
        items.add(to, entry)
        currentMediaItemIndex = items.indexOfFirst { it.entryId == currentId }.coerceAtLeast(0)
        PlaybackCommands.publishQueue(this)
    }

    fun setRepeatMode(mode: Int) {
        _repeatMode.value = mode.coerceIn(PlaybackCommands.REPEAT_OFF, PlaybackCommands.REPEAT_ALL)
    }

    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        AudioPlayer.setVolume(volume)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        MeloXPlaybackModePreferences.setShuffle(enabled)
        if (items.size <= 1) return
        if (enabled) {
            applyShuffle(keepCurrentFirst = true)
        } else {
            restoreOriginalOrder()
        }
        PlaybackCommands.publishQueue(this)
    }

    fun prioritizeManual() {
        val current = currentMediaItemIndex
        if (current !in items.indices) return
        val future = items.drop(current + 1)
        val manual = future.filter { it.origin == PlaybackCommands.QUEUE_ORIGIN_MANUAL }
        val base = future.filter { it.origin != PlaybackCommands.QUEUE_ORIGIN_MANUAL }
        if (manual.isEmpty()) return
        val head = items.take(current + 1)
        items.clear()
        items.addAll(head)
        items.addAll(manual)
        items.addAll(base)
        mediaItemCount = items.size
    }

    private fun onNaturalEnd() {
        scope.launch {
            when (_repeatMode.value) {
                PlaybackCommands.REPEAT_ONE -> {
                    pendingSeekMs = 0L
                    currentPosition = 0L
                    prepare()
                    play()
                }
                PlaybackCommands.REPEAT_ALL -> {
                    val nextIndex = if (currentMediaItemIndex < items.lastIndex) currentMediaItemIndex + 1 else 0
                    if (items.isNotEmpty()) seekToIndex(nextIndex)
                }
                else -> {
                    if (currentMediaItemIndex < items.lastIndex) {
                        seekToIndex(currentMediaItemIndex + 1)
                    } else {
                        playWhenReady = false
                        currentPosition = 0L
                    }
                }
            }
        }
    }

    private fun applyShuffle(keepCurrentFirst: Boolean) {
        if (items.size <= 1) return
        val current = items.getOrNull(currentMediaItemIndex)
        val pool = items.toMutableList()
        if (keepCurrentFirst && current != null) pool.remove(current)
        fisherYates(pool)
        items.clear()
        if (keepCurrentFirst && current != null) items.add(current)
        items.addAll(pool)
        mediaItemCount = items.size
        currentMediaItemIndex = if (keepCurrentFirst && current != null) 0 else currentMediaItemIndex.coerceIn(0, items.lastIndex)
    }

    private fun fisherYates(list: MutableList<QueueEntry>) {
        for (i in list.lastIndex downTo 1) {
            val j = Random.nextInt(i + 1)
            val tmp = list[i]
            list[i] = list[j]
            list[j] = tmp
        }
    }

    private fun restoreOriginalOrder() {
        val currentId = items.getOrNull(currentMediaItemIndex)?.entryId
        val sorted = items.sortedWith(
            compareBy<QueueEntry> { entry ->
                entry.originalIndex.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }.thenBy { it.song.id },
        )
        items.clear()
        items.addAll(sorted)
        mediaItemCount = items.size
        currentMediaItemIndex = items.indexOfFirst { it.entryId == currentId }.takeIf { it >= 0 } ?: 0
    }

    private data class Resolved(val uri: String, val headers: Map<String, String>)

    private fun resolve(entry: QueueEntry): Resolved {
        val song = entry.song
        val track = song.providerTrack
        if (track != null) {
            ProviderPlaybackRuntime.initialize()
            val registry = ProviderPlaybackRuntime.registryOrNull()
                ?: throw IOException("provider registry unavailable")
            val resolver = providerResolver ?: ProviderPlaybackResolver(
                neteaseResolver = neteaseResolver,
                providers = registry,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
            ).also { providerResolver = it }
            val uri = ProviderPlaybackResolver.uriForTrack(track, MusicQualityRuntime.selected.toCommonTier())
            val request = resolver.resolveProviderRequest(uri)
            return Resolved(request.uri, request.headers)
        }
        val local = MeloXDownloadStore.instance().localPlaybackString(song.id)
        if (local != null) return Resolved(local, emptyMap())
        val uri = NeteasePlaybackResolver.uriForSong(
            songId = song.id,
            quality = MusicQualityRuntime.selected,
            title = song.name,
            artist = song.artists,
            durationMs = song.durationMs,
        )
        val request = neteaseResolver.resolveUri(uri)
        return Resolved(request.uri, request.headers)
    }
}
