package melox.playback

import melox.audio.MusicQuality
import melox.audio.MusicQualityPreferences
import melox.audio.MusicQualityRuntime
import melox.download.MeloXDownloadStore
import melox.model.SearchSong
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.provider.PlaybackAccountStore
import melox.music.provider.ThirdPartyMusicSourceConsentStore
import melox.network.MeloXNetworkAvailability
import melox.platform.logDebug
import melox.platform.logError
import melox.player.AudioPlayer
import melox.player.AudioFailureKind
import melox.player.AudioPlaybackFailure
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import melox.settings.MeloXSettingsPreferences

/**
 * Desktop playback commands. Replaces Media3/MediaController with a simple
 * in-process player bridge. The desktop player will use these commands directly.
 */
fun classicQueueIndex(songs: List<SearchSong>, currentIndex: Int, track: MusicTrack): Int? {
    fun matches(song: SearchSong): Boolean {
        val provider = song.providerTrack
        if (provider?.id == track.id) return true
        return track.id.source == MusicSource.Netease &&
            track.id.value == song.id.toString() &&
            (provider == null || provider.id.source == MusicSource.Netease)
    }
    if (currentIndex in songs.indices && matches(songs[currentIndex])) return currentIndex
    return songs.indexOfFirst(::matches).takeIf { it >= 0 }
}

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

    fun playerController(): MeloXDesktopPlayer? = activeController as? MeloXDesktopPlayer

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
        player.persistSnapshot()
        MeloXPlaybackEvents.publish(player, MeloXPlaybackEvents.Kind.Queue)
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
        activeController = player
        player.onFailure = onFailure
        player.setMediaItems(queue, startIndex, startPositionMs, origins, originalIndexes)
        player.prepare()
        player.play()
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
        controller.addMediaItem(controller.mediaItemCount, song, QUEUE_ORIGIN_MANUAL)
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
        val entries = player.entries()
        if (entries.isEmpty()) return
        val currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
        val currentPosition = AudioPlayer.getCurrentPositionMs().coerceAtLeast(0L)
        val shouldResume = player.playWhenReady
        player.restore(
            items = entries,
            startIndex = currentIndex.coerceIn(0, entries.lastIndex),
            startPositionMs = currentPosition,
        )
        player.prepare()
        if (shouldResume) player.play()
        publishQueue(player)
    }

    fun setExplicitShuffle(player: Any, enabled: Boolean) {
        (player as? MeloXDesktopPlayer)?.setShuffleEnabled(enabled)
            ?: MeloXPlaybackModePreferences.setShuffle(enabled)
    }

    fun prioritizeManualQueue(player: Any) {
        (player as? MeloXDesktopPlayer)?.prioritizeManual()
    }

    fun restoreLastQueue() {
        if (!MeloXSettingsPreferences.boolean("playback_save_queue", true)) return
        if (playerController()?.mediaItemCount?.let { it > 0 } == true) return
        val persisted = MeloXPlaybackQueueStore.read() ?: return
        val player = activeController as? MeloXDesktopPlayer ?: MeloXDesktopPlayer()
        activeController = player
        player.restore(
            items = persisted.items,
            startIndex = persisted.index,
            startPositionMs = persisted.positionMs,
        )
        player.prepare(resolveAudio = false)
        player.pause()
        publishQueue(player)
    }

    internal fun adoptController(controller: Any) {
        (activeController as? MeloXDesktopPlayer)?.takeIf { it !== controller }?.release()
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
internal data class DesktopPlaybackRequest(val uri: String, val headers: Map<String, String> = emptyMap())

internal data class DesktopPlaybackStart(
    val request: DesktopPlaybackRequest,
    val track: MusicTrack,
    val positionMs: Long,
    val durationMs: Long,
    val playWhenReady: Boolean,
    val onEnded: () -> Unit,
    val onFailure: (AudioPlaybackFailure) -> Unit,
)

private fun isDesktopSongDownloaded(song: SearchSong): Boolean {
    val id = song.providerTrack?.id
    if (id?.source == MusicSource.Local) {
        val location = (song.providerTrack?.providerMetadata as? melox.music.model.ProviderTrackMetadata.Local)?.contentUri
        return location?.let { runCatching {
            val file = if (it.startsWith("file:")) java.io.File(java.net.URI(it)) else java.io.File(it)
            file.isFile && file.canRead()
        }.getOrDefault(false) } ?: false
    }
    if (id == null || id.source == MusicSource.Netease) {
        val songId = id?.value?.toLongOrNull() ?: song.id
        if (MeloXDownloadStore.instance().contains(songId)) return true
    }
    return id != null && melox.download.MeloXProviderDownloadStore.get().isDownloaded(id)
}

class MeloXDesktopPlayer internal constructor(
    private val resolutionOverride: (suspend (QueueEntry) -> DesktopPlaybackRequest)?,
    private val startPlayback: (DesktopPlaybackStart) -> Unit,
    private val scope: CoroutineScope,
    private val resolutionDispatcher: CoroutineDispatcher,
    private val saveSnapshot: (MeloXDesktopPlayer) -> Unit,
    private val savePosition: (Int, Long) -> Unit,
    recommendationSource: DesktopAutoplaySource = DesktopAutoplaySource(),
    private val isOnline: () -> Boolean = MeloXNetworkAvailability::isOnline,
    private val isDownloaded: (SearchSong) -> Boolean = ::isDesktopSongDownloaded,
) {
    constructor() : this(null, { start ->
        AudioPlayer.play(start.request.uri, start.track, start.request.headers, start.positionMs,
            start.durationMs, playWhenReady = start.playWhenReady, onEnded = start.onEnded, onFailure = start.onFailure)
    }, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate), Dispatchers.IO,
        MeloXPlaybackQueueStore::save, MeloXPlaybackQueueStore::savePosition)

    var onFailure: ((Throwable) -> Unit)? = null
    var currentMediaItemIndex: Int = -1
        private set
    var mediaItemCount: Int = 0
        private set
    var currentPosition: Long = 0L
        private set
    var playWhenReady: Boolean = false
        private set

    private val items = mutableListOf<QueueEntry>()
    private var resolveJob: Job? = null
    private var mixJob: Job? = null
    private var smartJob: Job? = null
    private var mixAttemptFor: String? = null
    private var currentResolved: DesktopPlaybackRequest? = null
    @Volatile private var playingEntryId: String? = null
    private val autoMixAnalyzer = MeloXAutoMixAudioAnalyzer()
    fun clearAnalysisCache() { autoMixAnalyzer.clear() }
    private val smartQueue = MeloXSmartQueueBuilder()
    @Volatile private var generation = 0
    @Volatile private var actionRevision = 0
    private var pendingSeekMs: Long = 0L
    private var volume: Float = 1f
    private val failedEntries = mutableSetOf<String>()
    private val repreparedEntries = mutableSetOf<String>()
    private var awaitingAutoplay = false
    private val positionJob: Job
    private val maintenanceJob: Job
    private val chkszPlayback = ChkszPlaybackResolver()
    private val lxUserPlayback = LxUserPlaybackResolver()
    private val neteaseResolver = NeteasePlaybackResolver(
        cookieProvider = { PlaybackAccountStore.neteaseCookie() },
        localSourceProvider = { songId -> MeloXDownloadStore.instance().localPlaybackString(songId) },
        providerPlaybackEnabled = melox.remoteconfig.MeloXRemoteConfigPolicy::providerPlaybackEnabled,
        chkszPlayback = chkszPlayback,
        lxUserPlayback = lxUserPlayback,
        thirdPartySourcesEnabled = ThirdPartyMusicSourceConsentStore::enabled,
        thirdPartyOnlyForMembership = ThirdPartyMusicSourceConsentStore::membershipFallbackOnly,
        crossProviderFallback = CrossProviderPlaybackFallbackResolver(
            enabledProvider = { CrossProviderPlaybackPreferences.enabled() && melox.remoteconfig.MeloXRemoteConfigPolicy.capabilityEnabled("cross_provider_fallback") },
            registryProvider = { ProviderPlaybackRuntime.initialize(); ProviderPlaybackRuntime.registryOrNull() },
            cacheIdentityProvider = { CrossProviderPlaybackFallbackResolver.EligibleSources
                .sortedBy { it.storageValue }.joinToString(":") { ProviderPlaybackRuntime.authKey(it) } },
            fallbackConfigProvider = { melox.remoteconfig.MeloXRemoteConfigPolicy.activeConfig().fallback },
            providerPlaybackEnabled = melox.remoteconfig.MeloXRemoteConfigPolicy::providerPlaybackEnabled,
            eventLogger = { message -> melox.platform.logInfo("MeloXPlayback", "Cross-provider fallback: $message") },
        ),
    )
    private var providerResolver: ProviderPlaybackResolver? = null
    private val autoplay = DesktopAutoplayCoordinator(scope, recommendationSource,
        enabled = {
            PlaybackCommands.playerController() == this && MeloXPlaybackModeRuntime.autoplayEnabled &&
                !MeloXPlaybackModePreferences.smartQueue() && isOnline()
        },
        current = { items.getOrNull(currentMediaItemIndex) },
        append = { recommendations ->
            val existing = items.map { entry ->
                entry.song.providerTrack?.id ?: melox.music.model.MusicResourceId(MusicSource.Netease, entry.song.id.toString())
            }.toSet()
            val additions = recommendations.distinctBy(SearchSong::id)
                .filterNot { melox.music.model.MusicResourceId(MusicSource.Netease, it.id.toString()) in existing }.take(20)
            if (additions.isEmpty()) throw IOException("无尽播放没有返回新的歌曲")
            additions.forEach { addMediaItem(items.size, it, PlaybackCommands.QUEUE_ORIGIN_BASE) }
            prioritizeManual()
            PlaybackCommands.publishQueue(this)
        },
        resumeEnded = {
            if (awaitingAutoplay && currentMediaItemIndex < items.lastIndex) {
                playWhenReady = true
                selectIndex(currentMediaItemIndex + 1, automatic = true)
            }
        },
    )

    private val _repeatMode = MutableStateFlow(PlaybackCommands.REPEAT_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(MeloXPlaybackModePreferences.shuffle())
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    val currentMediaItem: String?
        get() = items.getOrNull(currentMediaItemIndex)?.song?.id?.toString()

    init {
        AudioPlayer.setVolume(volume)
        positionJob = scope.launch {
            while (isActive) {
                delay(2_000)
                if (PlaybackCommands.playerController() == this@MeloXDesktopPlayer && items.isNotEmpty()) {
                    if (AudioPlayer.currentTrack.value != null) {
                        currentPosition = AudioPlayer.getCurrentPositionMs().coerceAtLeast(0L)
                    }
                    savePosition(currentMediaItemIndex, currentPosition)
                }
            }
        }
        maintenanceJob = scope.launch {
            while (isActive) {
                delay(1_000)
                if (PlaybackCommands.playerController() == this@MeloXDesktopPlayer) maintainAutoMix()
                if (currentMediaItemIndex == items.lastIndex && items.isNotEmpty()) {
                    val duration = AudioPlayer.getDurationMs()
                    val preloadMs = if (MeloXPlaybackModeRuntime.autoMixEnabled) {
                        maxOf(DesktopAutoplayCoordinator.PreloadMs, MeloXAutoMixSettings.read().preloadLeadMs)
                    } else DesktopAutoplayCoordinator.PreloadMs
                    if (duration > 0L && duration - AudioPlayer.getCurrentPositionMs() <= preloadMs) autoplay.ensure()
                }
            }
        }
    }

    internal fun persistSnapshot() {
        // A queue can be installed before prepare; don't overwrite its restored position.
        val entry = items.getOrNull(currentMediaItemIndex)
        val selected = AudioPlayer.currentTrack.value
        if (entry != null && selected != null && classicQueueIndex(listOf(entry.song), 0, selected) == 0) {
            currentPosition = AudioPlayer.getCurrentPositionMs().coerceAtLeast(0L)
        }
        saveSnapshot(this)
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
        generation += 1
        resolveJob?.cancel()
        cancelMix(); smartJob?.cancel(); smartQueue.reset()
        autoplay.cancel()
        resetFailureRecovery()
        awaitingAutoplay = false
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
        if (this.items.isNotEmpty() && MeloXPlaybackModeRuntime.smartQueueEnabled) {
            val selected = smartQueue.begin(this.items.toList(), currentMediaItemIndex)
            this.items.clear(); this.items += selected
            currentMediaItemIndex = 0; mediaItemCount = 1
        }
        if (_shuffleEnabled.value) reorderFuture(shuffle = true)
        if (this.items.isEmpty()) {
            if (PlaybackCommands.playerController() == this) AudioPlayer.stop()
            playWhenReady = false
            MeloXPlaybackQueueStore.clear()
        }
    }

    fun addMediaItem(index: Int, song: SearchSong) {
        addMediaItem(index, song, PlaybackCommands.QUEUE_ORIGIN_BASE)
    }

    fun addMediaItem(index: Int, song: SearchSong, origin: String) {
        cancelMix()
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
        if (origin == PlaybackCommands.QUEUE_ORIGIN_MANUAL) prioritizeManual()
    }

    internal fun restore(items: List<QueueEntry>, startIndex: Int, startPositionMs: Long) {
        generation += 1
        resolveJob?.cancel()
        cancelMix(); smartJob?.cancel(); smartQueue.reset()
        autoplay.cancel()
        resetFailureRecovery()
        awaitingAutoplay = false
        this.items.clear()
        this.items.addAll(items)
        mediaItemCount = this.items.size
        currentMediaItemIndex = if (this.items.isEmpty()) -1 else startIndex.coerceIn(0, this.items.lastIndex)
        currentPosition = startPositionMs.coerceAtLeast(0L)
        pendingSeekMs = currentPosition
        playWhenReady = false
    }

    /** Together supplies a concrete order; do not run local Smart Queue/shuffle over it. */
    internal fun installSynchronizedQueue(songs: List<SearchSong>, index: Int, positionMs: Long) {
        _shuffleEnabled.value = false
        restore(songs.mapIndexed { original, song ->
            QueueEntry(song, PlaybackCommands.QUEUE_ORIGIN_BASE, original, UUID.randomUUID().toString())
        }, index, positionMs)
    }

    private var deferredRestore = false

    fun prepare(resolveAudio: Boolean = true) {
        val entry = items.getOrNull(currentMediaItemIndex) ?: return
        val token = ++generation
        resolveJob?.cancel()
        cancelMix()
        awaitingAutoplay = false
        val song = entry.song
        val track = trackFor(song)
        AudioPlayer.selectTrack(track, pendingSeekMs, song.durationMs, loading = resolveAudio)
        deferredRestore = !resolveAudio
        // A restored queue is paused. Publish its song/position immediately, but
        // defer provider URL resolution, FFmpeg/PCM buffers and recommendations
        // until the user actually presses Play.
        if (!resolveAudio) return
        autoplay.selectionChanged()
        if (currentMediaItemIndex >= items.lastIndex) autoplay.ensure()
        if (!playWhenReady) AudioPlayer.pause()
        resolveJob = scope.launch {
            val resolved = try {
                resolveRequest(entry)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (token != generation || items.getOrNull(currentMediaItemIndex)?.entryId != entry.entryId) return@launch
                logError("MeloXPlayback", "resolve failed: ${melox.diagnostics.MeloXProcessLogs.describe(error)}")
                playWhenReady = false
                AudioPlayer.reportFailure(error)
                persistSnapshot()
                onFailure?.invoke(error)
                return@launch
            }
            if (token != generation || items.getOrNull(currentMediaItemIndex)?.entryId != entry.entryId) return@launch
            currentResolved = resolved
            playingEntryId = entry.entryId
            val startAt = pendingSeekMs
            startPlayback(DesktopPlaybackStart(resolved, track, startAt, song.durationMs, playWhenReady,
                onEnded = { onNaturalEnd(token, eventEntryId(entry), actionRevision) },
                onFailure = { onAudioFailure(token, eventEntryId(entry), it) }))
            pendingSeekMs = 0L
            currentPosition = startAt
            if (MeloXPlaybackModeRuntime.smartQueueEnabled) analyzeSmartCandidates(entry, resolved)
        }
    }

    private fun eventEntryId(initial: QueueEntry): String = playingEntryId ?: initial.entryId

    private fun trackFor(song: SearchSong): MusicTrack = song.providerTrack ?: MusicTrack(
            id = melox.music.model.MusicResourceId(MusicSource.Netease, song.id.toString()),
            title = song.name,
            artists = listOf(melox.music.model.MusicArtistRef(name = song.artists,
                id = song.artistId?.let { melox.music.model.MusicResourceId(MusicSource.Netease, it.toString()) })),
            album = song.album.takeIf { it.isNotBlank() }?.let { melox.music.model.MusicAlbumRef(name = it,
                id = song.albumId?.let { id -> melox.music.model.MusicResourceId(MusicSource.Netease, id.toString()) }) },
            artworkUrl = song.artworkUrl,
            durationMs = song.durationMs.takeIf { it > 0L },
        )

    private suspend fun resolveRequest(entry: QueueEntry) = withContext(resolutionDispatcher) { resolutionOverride?.invoke(entry) ?: resolve(entry) }

    private fun cancelMix() {
        mixJob?.cancel(); mixJob = null; mixAttemptFor = null
        if (PlaybackCommands.playerController() == this) AudioPlayer.cancelAutoMix()
    }

    private fun maintainAutoMix() {
        if (!MeloXPlaybackModeRuntime.autoMixEnabled || _repeatMode.value == PlaybackCommands.REPEAT_ONE) {
            if (AudioPlayer.hasAutoMix() || mixJob?.isActive == true) cancelMix()
            return
        }
        if (!playWhenReady || !AudioPlayer.isReady() || AudioPlayer.hasAutoMix() || mixJob?.isActive == true) return
        val source = items.getOrNull(currentMediaItemIndex) ?: return
        val target = items.getOrNull(currentMediaItemIndex + 1) ?: return
        if (mixAttemptFor == source.entryId || !isOnline() && !isDownloaded(target.song)) return
        if (!AudioPlayer.supportsAutoMix()) {
            MeloXAutoMixTransitionRuntime.status("waiting", "AutoMix 将从下一首开始使用双解码输出")
            return
        }
        val settings = MeloXAutoMixSettings.read()
        val duration = AudioPlayer.getDurationMs()
        if (duration <= 0 || duration - AudioPlayer.getCurrentPositionMs() > settings.preloadLeadMs) return
        val resolvedSource = currentResolved ?: return
        val token = generation; val action = actionRevision
        mixAttemptFor = source.entryId
        MeloXAutoMixTransitionRuntime.status("analyzing")
        mixJob = scope.launch {
            try {
                val incomingRequest = resolveRequest(target)
                val analyses = if (settings.mode == MeloXAutoMixMode.Smart && (settings.analyzeStreaming ||
                    !resolvedSource.uri.startsWith("http") && !incomingRequest.uri.startsWith("http"))) {
                    try {
                        withTimeout(20_000) { coroutineScope {
                            val a = async { autoMixAnalyzer.analyze(source.entryId, resolvedSource.uri, resolvedSource.headers) }
                            val b = async { autoMixAnalyzer.analyze(target.entryId, incomingRequest.uri, incomingRequest.headers) }
                            a.await() to b.await()
                        } }
                    } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) { null }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } else null
                val smart = analyses?.let { MeloXAutoMixTransitionScorer.plan(settings, it.first, it.second) }
                val fallback = MeloXAutoMixPlanner.plan(settings.copy(mode = if (settings.mode == MeloXAutoMixMode.Fixed) MeloXAutoMixMode.Fixed else MeloXAutoMixMode.Smart), duration)
                val selectedPlan = smart ?: fallback.copy(outgoingStartMs = (duration - fallback.durationMs - fallback.outgoingEndOffsetMs).coerceAtLeast(0))
                if (!selectedPlan.performsTransition) { MeloXAutoMixTransitionRuntime.cancel("分析未满足阈值，正常播放"); return@launch }
                if (token != generation || action != actionRevision || items.getOrNull(currentMediaItemIndex + 1)?.entryId != target.entryId) return@launch
                if (selectedPlan.outgoingStartMs <= AudioPlayer.getCurrentPositionMs() + 250) { MeloXAutoMixTransitionRuntime.cancel("过渡准备过晚，正常播放"); return@launch }
                AudioPlayer.prepareAutoMix(trackFor(source.song), trackFor(target.song), incomingRequest.uri, incomingRequest.headers, selectedPlan, settings.fadeCurve) { position ->
                    if (token == generation) playingEntryId = target.entryId
                    scope.launch {
                        if (token != generation || !playWhenReady) return@launch
                        val index = items.indexOfFirst { it.entryId == target.entryId }
                        if (index < 0) return@launch
                        currentMediaItemIndex = index; currentPosition = position; currentResolved = incomingRequest
                        mixAttemptFor = null; pendingSeekMs = 0
                        autoplay.selectionChanged(); ensureSmartNext()
                        PlaybackCommands.publishQueue(this@MeloXDesktopPlayer)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { if (token == generation) MeloXAutoMixTransitionRuntime.cancel(failure.message ?: "AutoMix 预加载失败，正常播放") }
        }
    }

    private fun analyzeSmartCandidates(source: QueueEntry, resolved: DesktopPlaybackRequest) {
        ensureSmartNext()
        if (smartJob?.isActive == true || smartQueue.snapshot().items.isEmpty()) return
        val snapshot = smartQueue.snapshot()
        smartJob = scope.launch {
            suspend fun analyze(entry: QueueEntry, request: DesktopPlaybackRequest) {
                try {
                    if (!MeloXAutoMixSettings.read().analyzeStreaming && request.uri.startsWith("http")) throw IOException("未启用流媒体分析")
                    val analysis = withTimeout(20_000) { autoMixAnalyzer.analyze(entry.entryId, request.uri, request.headers) }
                    smartQueue.record(snapshot.generation, entry, analysis)
                } catch (cancelled: kotlinx.coroutines.TimeoutCancellationException) { smartQueue.failure(snapshot.generation, entry) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { smartQueue.failure(snapshot.generation, entry) }
                ensureSmartNext()
            }
            analyze(source, resolved)
            // Limit URL resolution as well as native analysis; never launch a decoder per playlist item at once.
            val permits = kotlinx.coroutines.sync.Semaphore(2)
            coroutineScope { snapshot.items.map { entry -> async {
                permits.acquire()
                try { analyze(entry, resolveRequest(entry)) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { smartQueue.failure(snapshot.generation, entry) }
                finally { permits.release() }
            } }.awaitAll() }
            ensureSmartNext(emergency = true)
        }
    }

    private val smartQueueMutationLock = Any()

    private fun ensureSmartNext(emergency: Boolean = false) = synchronized(smartQueueMutationLock) {
        if (!MeloXPlaybackModeRuntime.smartQueueEnabled || currentMediaItemIndex < items.lastIndex) return@synchronized
        val source = items.getOrNull(currentMediaItemIndex) ?: return@synchronized
        val snapshot = smartQueue.snapshot()
        val match = smartQueue.best(snapshot.generation, source) ?: if (emergency) smartQueue.emergency(snapshot.generation) else null
        val selected = match?.let { smartQueue.consume(snapshot.generation, it) } ?: return@synchronized
        items += selected; mediaItemCount = items.size
        PlaybackCommands.publishQueue(this)
        if (awaitingAutoplay) { playWhenReady = true; selectIndex(currentMediaItemIndex + 1, automatic = true) }
    }

    fun setSmartQueueEnabled(enabled: Boolean) = synchronized(smartQueueMutationLock) {
        cancelMix(); smartJob?.cancel()
        MeloXPlaybackModePreferences.setSmartQueue(enabled)
        if (enabled && currentMediaItemIndex in items.indices) {
            val head = items.take(currentMediaItemIndex + 1)
            smartQueue.begin(items.drop(currentMediaItemIndex), 0)
            items.clear(); items += head; mediaItemCount = items.size
            val current = items[currentMediaItemIndex]
            currentResolved?.let { analyzeSmartCandidates(current, it) }
        } else { items += smartQueue.takeRemaining(); mediaItemCount = items.size }
        PlaybackCommands.publishQueue(this)
    }

    fun play() {
        playWhenReady = true
        MeloXPlaybackEvents.publish(this, MeloXPlaybackEvents.Kind.Play)
        if (deferredRestore) {
            prepare()
            persistSnapshot()
            return
        }
        val ended = AudioPlayer.state.value.isFinished || AudioPlayer.state.value.error != null
        if (ended) {
            resetFailureRecovery()
            if (AudioPlayer.state.value.error != null) pendingSeekMs = AudioPlayer.getCurrentPositionMs()
        }
        if (!ended && AudioPlayer.currentTrack.value != null) {
            AudioPlayer.resume()
        }
        if (resolveJob?.isActive != true && (AudioPlayer.currentTrack.value == null || ended)) {
            prepare()
        }
        persistSnapshot()
    }

    fun pause() {
        actionRevision += 1
        playWhenReady = false
        awaitingAutoplay = false
        currentPosition = AudioPlayer.getCurrentPositionMs()
        MeloXPlaybackEvents.publish(this, MeloXPlaybackEvents.Kind.Pause)
        AudioPlayer.pause()
        cancelMix()
        persistSnapshot()
    }

    fun togglePlay() {
        if (playWhenReady && AudioPlayer.state.value.error == null && !AudioPlayer.state.value.isFinished) pause() else play()
    }

    fun release() {
        val active = PlaybackCommands.playerController() == this
        if (active) persistSnapshot()
        generation += 1
        resolveJob?.cancel()
        cancelMix(); smartJob?.cancel(); smartQueue.reset()
        positionJob.cancel()
        maintenanceJob.cancel()
        autoplay.cancel()
        resetFailureRecovery()
        awaitingAutoplay = false
        if (active) AudioPlayer.stop()
        items.clear()
        mediaItemCount = 0
        currentMediaItemIndex = -1
        currentPosition = 0L
        playWhenReady = false
        pendingSeekMs = 0L
        if (PlaybackCommands.playerController() == this) PlaybackCommands.activeController = null
    }

    fun clearQueue() {
        generation += 1
        resolveJob?.cancel()
        cancelMix(); smartJob?.cancel(); smartQueue.reset()
        autoplay.cancel()
        resetFailureRecovery()
        awaitingAutoplay = false
        if (PlaybackCommands.playerController() == this) AudioPlayer.stop()
        items.clear()
        mediaItemCount = 0
        currentMediaItemIndex = -1
        currentPosition = 0L
        pendingSeekMs = 0L
        playWhenReady = false
        MeloXPlaybackQueueStore.clear()
        PlaybackCommands.publishQueue(this)
    }

    fun next() {
        if (items.isEmpty()) return
        val nextIndex = when {
            currentMediaItemIndex < items.lastIndex -> currentMediaItemIndex + 1
            _repeatMode.value == PlaybackCommands.REPEAT_ALL -> 0
            else -> return
        }
        val incomingPosition = AudioPlayer.incomingMixPosition()
        // Install the heard position before prepare can resolve/start the new
        // track. A second seek after selection races fast/inline resolution,
        // briefly opening at zero or letting that start overwrite the seek.
        selectIndex(nextIndex, automatic = false, startPositionMs = incomingPosition ?: 0L)
    }

    fun previous() {
        val position = AudioPlayer.getCurrentPositionMs().takeIf { it > 0L } ?: currentPosition
        if (melox.settings.MeloXSettingsRuntime.previousRestartsAfterFiveSeconds && position > 5000L) {
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
        actionRevision += 1
        mixJob?.cancel(); mixAttemptFor = null
        awaitingAutoplay = false
        val target = ms.coerceAtLeast(0L)
        currentPosition = target
        pendingSeekMs = target
        AudioPlayer.seekTo(target)
        MeloXPlaybackEvents.publish(this, MeloXPlaybackEvents.Kind.Seek)
        persistSnapshot()
    }

    fun seekToIndex(index: Int, startPositionMs: Long = 0L) {
        selectIndex(index, automatic = false, startPositionMs = startPositionMs)
    }

    private fun selectIndex(index: Int, automatic: Boolean, startPositionMs: Long = 0L) {
        if (index !in items.indices) return
        if (index == currentMediaItemIndex) return
        if (!automatic) resetFailureRecovery()
        currentMediaItemIndex = index
        currentPosition = startPositionMs.coerceAtLeast(0L)
        pendingSeekMs = currentPosition
        prepare()
        PlaybackCommands.publishQueue(this)
    }

    fun removeAt(index: Int) {
        if (index !in items.indices || items.size <= 1) return
        cancelMix()
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
        cancelMix()
        val currentId = items.getOrNull(currentMediaItemIndex)?.entryId
        val entry = items.removeAt(from)
        items.add(to, entry)
        currentMediaItemIndex = items.indexOfFirst { it.entryId == currentId }.coerceAtLeast(0)
        PlaybackCommands.publishQueue(this)
    }

    fun setRepeatMode(mode: Int) {
        cancelMix()
        _repeatMode.value = mode.coerceIn(PlaybackCommands.REPEAT_OFF, PlaybackCommands.REPEAT_ALL)
    }

    fun cycleRepeatMode() {
        setRepeatMode(when (_repeatMode.value) {
            PlaybackCommands.REPEAT_OFF -> PlaybackCommands.REPEAT_ALL
            PlaybackCommands.REPEAT_ALL -> PlaybackCommands.REPEAT_ONE
            else -> PlaybackCommands.REPEAT_OFF
        })
    }

    fun setAutoplayEnabled(enabled: Boolean) {
        MeloXPlaybackModePreferences.setAutoplay(enabled)
        if (!enabled) autoplay.cancel()
        else if (currentMediaItemIndex >= items.lastIndex) autoplay.ensure()
    }

    fun setAutoMixEnabled(enabled: Boolean) {
        cancelMix()
        MeloXPlaybackModePreferences.setAutoMix(enabled)
        if (enabled) maintainAutoMix()
    }

    fun setVolume(level: Float) {
        volume = level.coerceIn(0f, 1f)
        AudioPlayer.setVolume(volume)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        cancelMix()
        _shuffleEnabled.value = enabled
        MeloXPlaybackModePreferences.setShuffle(enabled)
        if (items.size <= 1) return
        reorderFuture(shuffle = enabled)
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

    private fun resetFailureRecovery() {
        failedEntries.clear()
        repreparedEntries.clear()
    }

    private fun ownsEvent(token: Int, entryId: String): Boolean =
        token == generation && PlaybackCommands.playerController() == this &&
            items.getOrNull(currentMediaItemIndex)?.entryId == entryId

    /** The pinned service skips media errors, preferring downloaded entries offline. */
    private fun onAudioFailure(token: Int, entryId: String, failure: AudioPlaybackFailure) {
        scope.launch {
            if (!ownsEvent(token, entryId)) return@launch
            logError("MeloXPlayback", "decoder failed (${failure.kind}): ${melox.diagnostics.MeloXProcessLogs.describe(failure)}")
            currentPosition = AudioPlayer.getCurrentPositionMs().coerceAtLeast(0L)
            if (failure.kind == AudioFailureKind.Content) {
                failedEntries += entryId
                val downloaded = if (!isOnline()) nextDownloadedIndex() else null
                val next = downloaded ?: when {
                    currentMediaItemIndex < items.lastIndex -> currentMediaItemIndex + 1
                    _repeatMode.value == PlaybackCommands.REPEAT_ALL && items.isNotEmpty() -> 0
                    else -> null
                }
                if (next != null && items[next].entryId !in failedEntries) {
                    playWhenReady = true
                    selectIndex(next, automatic = true)
                    return@launch
                }
                // Source's final-item prepare recovery, bounded for a permanently
                // broken file/device so an exhausted repeat queue cannot spin.
                if (repreparedEntries.add(entryId)) {
                    pendingSeekMs = currentPosition
                    prepare()
                    return@launch
                }
            }
            // A missing desktop output/decoder is device-wide: expose the genuine
            // error without consuming every queue entry to repeat the same failure.
            playWhenReady = false
            awaitingAutoplay = false
            AudioPlayer.reportFailure(failure)
            persistSnapshot()
            onFailure?.invoke(failure)
        }
    }

    private fun nextDownloadedIndex(): Int? {
        val forward = ((currentMediaItemIndex + 1)..items.lastIndex).toList()
        val wrapped = if (_repeatMode.value == PlaybackCommands.REPEAT_ALL) (0 until currentMediaItemIndex).toList() else emptyList()
        return (forward + wrapped).firstOrNull { isDownloaded(items[it].song) && items[it].entryId !in failedEntries }
    }

    private fun onNaturalEnd(token: Int, entryId: String, action: Int) {
        scope.launch {
            if (!ownsEvent(token, entryId) || action != actionRevision) return@launch
            resetFailureRecovery()
            if (!playWhenReady) {
                persistSnapshot()
                return@launch
            }
            if (_repeatMode.value != PlaybackCommands.REPEAT_ONE && !isOnline()) {
                val next = nextDownloadedIndex()
                if (next != null) {
                    playWhenReady = true
                    selectIndex(next, automatic = true)
                } else pause()
                return@launch
            }
            when (_repeatMode.value) {
                PlaybackCommands.REPEAT_ONE -> {
                    pendingSeekMs = 0L
                    currentPosition = 0L
                    prepare()
                }
                PlaybackCommands.REPEAT_ALL -> {
                    val nextIndex = if (currentMediaItemIndex < items.lastIndex) currentMediaItemIndex + 1 else 0
                    if (items.size == 1) {
                        pendingSeekMs = 0L
                        currentPosition = 0L
                        prepare()
                    } else if (items.isNotEmpty()) selectIndex(nextIndex, automatic = true)
                }
                else -> {
                    if (currentMediaItemIndex < items.lastIndex) {
                        selectIndex(currentMediaItemIndex + 1, automatic = true)
                    } else {
                        playWhenReady = false
                        awaitingAutoplay = true
                        ensureSmartNext(emergency = smartJob?.isActive != true)
                        currentPosition = AudioPlayer.getCurrentPositionMs()
                        persistSnapshot()
                        autoplay.ensure()
                    }
                }
            }
        }
    }

    private fun reorderFuture(shuffle: Boolean) {
        if (currentMediaItemIndex !in items.indices) return
        val start = currentMediaItemIndex + 1
        val future = items.drop(start)
        val manual = future.filter { it.origin == PlaybackCommands.QUEUE_ORIGIN_MANUAL }
        val base = future.filter { it.origin != PlaybackCommands.QUEUE_ORIGIN_MANUAL }
        val orderedBase = if (shuffle) base.shuffled() else base.sortedWith(
            compareBy<QueueEntry> { it.originalIndex.takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
                .thenBy { it.song.providerTrack?.id?.value ?: it.song.id.toString() },
        )
        val head = items.take(start)
        items.clear()
        items.addAll(head)
        items.addAll(manual)
        items.addAll(orderedBase)
    }

    private fun resolve(entry: QueueEntry): DesktopPlaybackRequest {
        val song = entry.song
        val track = song.providerTrack
        song.neteaseSongId?.let { nativeId ->
            MeloXDownloadStore.instance().localPlaybackString(nativeId)?.let { return DesktopPlaybackRequest(it) }
        }
        if (track != null) {
            ProviderPlaybackRuntime.initialize()
            val registry = ProviderPlaybackRuntime.registryOrNull()
                ?: throw IOException("provider registry unavailable")
            val resolver = providerResolver ?: ProviderPlaybackResolver(
                neteaseResolver = neteaseResolver,
                providers = registry,
                authKeyProvider = ProviderPlaybackRuntime::authKey,
                providerPlaybackEnabled = melox.remoteconfig.MeloXRemoteConfigPolicy::providerPlaybackEnabled,
                chkszPlayback = chkszPlayback,
                lxUserPlayback = lxUserPlayback,
                thirdPartySourcesEnabled = ThirdPartyMusicSourceConsentStore::enabled,
                thirdPartyOnlyForMembership = ThirdPartyMusicSourceConsentStore::membershipFallbackOnly,
            ).also { providerResolver = it }
            val uri = ProviderPlaybackResolver.uriForTrack(track, MusicQualityRuntime.selected.toCommonTier())
            val request = resolver.resolveProviderRequest(uri)
            return DesktopPlaybackRequest(request.uri, request.headers)
        }
        val local = MeloXDownloadStore.instance().localPlaybackString(song.id)
        if (local != null) return DesktopPlaybackRequest(local)
        val uri = NeteasePlaybackResolver.uriForSong(
            songId = song.id,
            quality = MusicQualityRuntime.selected,
            title = song.name,
            artist = song.artists,
            durationMs = song.durationMs,
        )
        val request = neteaseResolver.resolveUri(uri)
        return DesktopPlaybackRequest(request.uri, request.headers)
    }
}
