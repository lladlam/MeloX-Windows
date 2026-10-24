package melox.validation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import melox.ui.MeloXApp
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

/** Render the production app and replay physical-coordinate clicks, without an X session. */
@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class)
fun main(args: Array<String>) = runBlocking {
    fun option(name: String, default: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) } ?: default
    option("--locale", "").takeIf(String::isNotBlank)?.let { java.util.Locale.setDefault(java.util.Locale.forLanguageTag(it)) }
    org.jetbrains.skia.impl.Stats.enabled = "--skia-stats" in args
    val width = option("--width", "427").toInt()
    val height = option("--height", "924").toInt()
    val density = option("--density", "1").toFloat()
    val waitMs = option("--wait-ms", "3500").toLong()
    val output = File(option("--output", "build/parity/desktop"))
    output.mkdirs()
    val pcmOutput = if ("--capture-pcm" in args) MeloXPcmCaptureOutput(output) else null
    if (pcmOutput != null) {
        // Keep controlled endpoints confined to the explicit validation entry point.
        melox.player.AudioPlayer::class.java.getDeclaredField("outputLineFactory").apply { isAccessible = true }
            .set(null, { pcmOutput.open() })
    }
    // Windows CreateProcess encodes the child command line with sun.jnu.encoding,
    // so pinned non-ASCII labels must travel through UTF-8 files, not argv.
    val stepsFile = option("--steps-file", "")
    val steps = if (stepsFile.isNotBlank()) File(stepsFile).readLines(Charsets.UTF_8).filter { it.isNotBlank() }
        else option("--steps", "").split(';').filter { it.isNotBlank() }
    val frameOffsets = option("--frames-ms", "").split(',').filter { it.isNotBlank() }.map { it.toLong() }
    require(frameOffsets.all { it >= 0 } && frameOffsets == frameOffsets.distinct().sorted())
    val frameEvidence = JSONArray()
    val inputEvidence = JSONArray()
    val sceneEvidence = JSONArray()
    val expectations = if (option("--expect-file", "").isNotBlank()) {
        File(option("--expect-file", "")).readLines(Charsets.UTF_8).filter(String::isNotBlank).associate { entry ->
            val (step, assertions) = entry.split('=', limit = 2)
            step to assertions.split(',')
        }
    } else option("--expect", "").split(';').filter(String::isNotBlank).associate { entry ->
        val (step, assertions) = entry.split('=', limit = 2)
        step to assertions.split(',')
    }
    withContext(Dispatchers.Main) {
        melox.settings.MeloXSettingsPreferences.initialize()
        option("--source", "").takeIf(String::isNotBlank)?.let { value ->
            melox.music.provider.MusicProviderSelectionStore.setSelectedSource(melox.music.model.MusicSource.valueOf(value))
        }
        option("--unified-sources", "").takeIf(String::isNotBlank)?.let { value ->
            melox.music.provider.MusicProviderSelectionStore.setUnifiedSources(value.split(',').map { melox.music.model.MusicSource.valueOf(it) }.toSet())
            melox.music.provider.MusicProviderSelectionStore.setUnifiedEnabled(true)
        }
        option("--scan-local-dir", "").takeIf(String::isNotBlank)?.let { path ->
            val directory = File(path).canonicalFile
            require(directory.isDirectory) { "Local validation source directory is missing" }
            val repository = melox.provider.local.LocalMusicRepository()
            repository.addScanRoot(melox.provider.local.LocalScanRoot(directory.path, 0))
            val scanned = melox.provider.local.LocalMediaScanner(repository).scanAll()
            check(scanned.isNotEmpty()) { "Production Local scanner found no real audio files" }
            println("Production Local scanner indexed ${scanned.size} real files")
            option("--local-lyrics-file", "").takeIf(String::isNotBlank)?.let { lyricPath ->
                val lyricFile = File(lyricPath)
                require(lyricFile.isFile)
                val document = if (lyricFile.extension.lowercase() in listOf("ttml", "xml")) melox.lyrics.TtmlLyricsParser.parse(lyricFile.readText())
                    else melox.lyrics.NeteaseLyricParser.parse(yrc = "", lrc = lyricFile.readText())
                check(document.lines.isNotEmpty())
                // Explicit generated-file validation input into the genuine persistent Local lyrics path.
                scanned.forEach { check(repository.updateLyrics(it.fileKey, document.copy(source = melox.lyrics.LyricSource.Local))) }
            }
        }
        val theme = option("--theme", "")
        val startTab = option("--start-tab", "Home")
        require(melox.ui.navigation.AppTab.entries.any { it.name == startTab })
        if ("--enable-start-tab" in args) {
            require(startTab in listOf("Cloud", "Downloads", "Podcasts"))
            melox.settings.MeloXSettingsPreferences.setBoolean("placement_${startTab.lowercase()}_tab", true)
        }
        melox.settings.MeloXSettingsPreferences.setString("general_last_tab", startTab)
        melox.settings.MeloXSettingsPreferences.setString("general_default_tab", startTab)
        if (theme.isNotBlank()) {
            require(theme in listOf("Light", "Dark", "System"))
            melox.settings.MeloXSettingsPreferences.setString("theme_mode", theme)
        }
        val playQuery = option("--play-query", "")
        option("--search-query", "").takeIf(String::isNotBlank)?.let {
            melox.ui.navigation.MeloXSearchLaunchBus.post(it, melox.network.MeloXSearchKind.Songs)
        }
        option("--artist-id", "").takeIf(String::isNotBlank)?.toLong()?.let {
            melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Artist(it))
        }
        option("--album-id", "").takeIf(String::isNotBlank)?.toLong()?.let {
            melox.ui.navigation.MeloXCollectionLaunchBus.open(melox.ui.navigation.MeloXCollectionTarget.Album(it))
        }
        val playId = option("--play-id", "").takeIf(String::isNotBlank)?.toLong()
        option("--local-wavs", "").takeIf(String::isNotBlank)?.let { paths ->
            val tracks = paths.split(',').map { path ->
                val file = File(path).canonicalFile
                val duration = javax.sound.sampled.AudioSystem.getAudioInputStream(file).use { it.frameLength * 1000 / it.format.frameRate.toLong() }
                val key = melox.provider.local.LocalMusicRepository().tracks().firstOrNull { it.contentUri == file.toURI().toString() }?.fileKey
                    ?: java.security.MessageDigest.getInstance("SHA-256").digest(file.path.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
                melox.music.model.MusicTrack(melox.music.model.MusicResourceId(melox.music.model.MusicSource.Local, key), file.nameWithoutExtension,
                    listOf(melox.music.model.MusicArtistRef(name = "Generated PCM fixture")), durationMs = duration,
                    artworkUrl = File(file.parentFile, "${file.nameWithoutExtension}.png").takeIf(File::isFile)?.toURI()?.toString(),
                    providerMetadata = melox.music.model.ProviderTrackMetadata.Local(file.toURI().toString(), key))
            }
            melox.playback.MeloXPlaybackModePreferences.setAutoMix("--automix" in args)
            melox.playback.MeloXPlaybackModePreferences.setAutoMixString("automix_mode", "Fixed")
            // Browsing source and playing track source are independent in the app.
            melox.music.provider.MusicProviderSelectionStore.setSelectedSource(
                melox.music.model.MusicSource.valueOf(option("--source", "Local")))
            melox.playback.ProviderPlaybackCommands.playQueue(tracks, tracks.first().id)
        }
        if (playQuery.isNotBlank() || playId != null) {
            val client = melox.network.NeteaseSearchClient()
            val results = if (playId != null) listOf(requireNotNull(melox.network.NeteaseUniversalSearchClient().songDetail(playId)) {
                "Provider did not return the real track $playId"
            }) else client.searchSongs(playQuery, limit = option("--queue-limit", "5").toInt().coerceIn(1, 30))
            val songs = client.ensureArtwork(results)
            check(songs.isNotEmpty()) { "No real provider results for $playQuery" }
            val selected = songs[option("--play-index", "0").toInt().coerceIn(songs.indices)]
            var dispatchFailure: Throwable? = null
            melox.playback.PlaybackCommands.playQueue(songs, selected.id, onFailure = { dispatchFailure = it })
            dispatchFailure?.let { throw it }
            check(melox.player.AudioPlayer.currentTrack.value?.id?.value == selected.id.toString()) { "Playback command did not select the real track" }
            println("Live playback queue: ${songs.size} tracks, selected Netease:${selected.id} ${selected.name}")
        }
        val scene = ImageComposeScene(width, height, Density(density), coroutineContext = coroutineContext) { MeloXApp() }
        val start = System.nanoTime()
        var renderClock = start
        val controlledClock = "--controlled-animation-clock" in args
        suspend fun settle(durationMs: Long) {
            val end = System.nanoTime() + durationMs * 1_000_000L
            val animationEnd = renderClock + durationMs * 1_000_000L
            var frames = 0
            do {
                renderClock = if (controlledClock) renderClock + 16_000_000L
                    else maxOf(renderClock + 16_000_000L, System.nanoTime())
                scene.render(renderClock - start).close()
                delay(if (controlledClock) 1L else 16L)
                frames += 1
            } while (frames < 3 || if (controlledClock) renderClock < animationEnd else System.nanoTime() < end)
        }
        data class PlaybackCheckpoint(val track: melox.music.model.MusicResourceId, val position: Long, val outputCount: Int)
        val playbackCheckpoints = mutableMapOf<String, PlaybackCheckpoint>()
        fun assertionMatches(assertion: String): Boolean {
            val (kind, expected) = assertion.split(':', limit = 2)
            val semantics = scene.captureSemantics()
            return when (kind) {
                "page" -> semantics.tagged("now-playing")?.config?.getOrNull(SemanticsProperties.StateDescription) == expected
                "state" -> semantics.tagged(expected.substringBeforeLast(':'))?.config?.getOrNull(SemanticsProperties.StateDescription) == expected.substringAfterLast(':')
                "onboarded" -> melox.settings.MeloXSettingsPreferences.boolean("onboarding_completed") == expected.toBooleanStrict()
                "legal-consent" -> melox.settings.MeloXSettingsPreferences.string("legal_consent_version") == expected
                "cloud-choice" -> melox.remoteconfig.MeloXRemoteConfigConsent.choiceMade() == expected.toBooleanStrict()
                "cloud-enabled" -> melox.remoteconfig.MeloXRemoteConfigConsent.enabled() == expected.toBooleanStrict()
                "personalization" -> melox.recommendation.LocalRecommendationStore.hasPersonalizationConsent() == expected.toBooleanStrict()
                "algorithm" -> melox.recommendation.LocalRecommendationStore.isAlgorithmEnabled() == expected.toBooleanStrict()
                "index" -> melox.playback.PlaybackCommands.queue.value.index == expected.toInt()
                "tag" -> semantics.tagged(expected) != null
                "text" -> semantics.any { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == expected } == true }
                "disabled" -> semantics.tagged(expected)?.config?.contains(SemanticsProperties.Disabled) == true
                "enabled" -> semantics.tagged(expected)?.config?.contains(SemanticsProperties.Disabled) == false
                "full-tag" -> semantics.tagged(expected)?.boundsInWindow?.let { it.width >= width * .97f && it.height >= height * .97f } == true
                "in-half" -> expected.split(':', limit = 2).let { (tag, half) ->
                    semantics.tagged(tag)?.boundsInWindow?.let { bounds ->
                        !bounds.isEmpty && if (half == "right") bounds.left >= width / 2f - 1f else bounds.right <= width / 2f + 1f
                    } == true
                }
                "chrome-placement" -> expected.split(':', limit = 2).let { (tag, name) ->
                    val position = melox.settings.MeloXSplitChromePosition.valueOf(name)
                    semantics.tagged(tag)?.boundsInWindow?.let { bounds ->
                        !bounds.isEmpty && (if (position.right) bounds.left >= width / 2f else bounds.right <= width / 2f) &&
                            when (position.vertical) {
                                -1 -> bounds.top < 24f * density
                                0 -> kotlin.math.abs(bounds.center.y - height / 2f) < 80f * density
                                else -> bounds.bottom > height - 24f * density
                            }
                    } == true
                }
                "absent" -> semantics.tagged(expected) == null
                "selected" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.Selected) == true
                "unselected" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.Selected) == false
                "checked" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
                "unchecked" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.Off
                "toggle-position" -> expected.split(':', limit = 2).let { (tag, value) ->
                    semantics.tagged(tag)?.config?.getOrNull(melox.ui.glass.MeloXToggleVisualProgressKey)
                        ?.let { kotlin.math.abs(it - value.toFloat()) <= .01f } == true
                }
                "dock-selection" -> {
                    val tab = semantics.tagged("root-tab:$expected")?.boundsInWindow
                    val lens = semantics.tagged("bottom-tab-selection")?.boundsInWindow
                    tab != null && lens != null && !tab.isEmpty && !lens.isEmpty &&
                        kotlin.math.abs(lens.center.x - tab.center.x) <= 1.5f * density &&
                        kotlin.math.abs(lens.center.y - tab.center.y) <= 1.5f * density &&
                        kotlin.math.abs(lens.width - minOf((tab.width + 8f * density) * 1.15f + 2f * density, tab.width + 20f * density)) <= 2f * density
                }
                "quality" -> melox.audio.MusicQualityPreferences.read().apiLevel == expected
                "eq-enabled" -> melox.playback.MeloXEqualizerSettings.current.value.enabled == expected.toBooleanStrict()
                "eq-preset" -> melox.playback.MeloXEqualizerSettings.current.value.preset == expected
                "automix-mode" -> melox.playback.MeloXAutoMixSettings.read().mode.name == expected
                "automix-duration-ms" -> melox.playback.MeloXAutoMixSettings.read().fixedDurationMs == expected.toLong()
                "smart-queue-analysis-limit" -> melox.playback.MeloXAutoMixSettings.read().smartQueueAnalysisLimit == expected.toInt()
                "lyric-language" -> melox.settings.MeloXSettingsRuntime.lyricLanguage == expected
                "lyric-preference" -> melox.lyrics.requestedLyricPreference().cacheKey() == expected
                "download-lyrics" -> melox.platform.getPreferences("melox_downloads").getBoolean("download_lyrics", true) == expected.toBooleanStrict()
                "player-shell" -> melox.settings.MeloXSettingsRuntime.playerShell.name == expected
                "pv-style" -> melox.settings.MeloXSettingsRuntime.textPVStyle.name == expected
                "pv-speed" -> melox.settings.MeloXSettingsRuntime.textPVAnimationSpeed == expected.toFloat()
                "pv-intensity" -> melox.settings.MeloXSettingsRuntime.textPVMotionIntensity == expected.toFloat()
                "pref-bool" -> expected.split(':', limit = 2).let { (key, value) -> melox.settings.MeloXSettingsPreferences.boolean(key, !value.toBooleanStrict()) == value.toBooleanStrict() }
                "pref-int" -> expected.split(':', limit = 2).let { (key, value) -> melox.settings.MeloXSettingsPreferences.int(key, Int.MIN_VALUE) == value.toInt() }
                "pref-float" -> expected.split(':', limit = 2).let { (key, value) -> kotlin.math.abs(melox.settings.MeloXSettingsPreferences.float(key, Float.NaN) - value.toFloat()) < .001f }
                "pref-string" -> expected.split(':', limit = 2).let { (key, value) -> melox.settings.MeloXSettingsPreferences.string(key) == value }
                "runtime" -> expected.split(':', limit = 2).let { (property, value) ->
                    val getter = "get" + property.replaceFirstChar(Char::uppercaseChar)
                    val actual = melox.settings.MeloXSettingsRuntime::class.java.getMethod(getter).invoke(melox.settings.MeloXSettingsRuntime)
                    if (actual is Number) kotlin.math.abs(actual.toDouble() - value.toDouble()) < .001 else actual.toString() == value
                }
                "translation-count" -> semantics.count { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("lyric-translation-") == true } == expected.toInt()
                "translation-min" -> semantics.count { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("lyric-translation-") == true } >= expected.toInt()
                "eq-preamp" -> melox.playback.MeloXEqualizerSettings.current.value.preampDb == expected.toFloat()
                "eq-band" -> expected.split(':').let { (index, gain) -> melox.playback.MeloXEqualizerSettings.current.value.gainsDb[index.toInt()] == gain.toFloat() }
                "queue-min" -> melox.playback.PlaybackCommands.queue.value.songs.size >= expected.toInt()
                "tag-prefix" -> semantics.any { it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(expected) == true && !it.boundsInWindow.isEmpty }
                "position-min" -> melox.player.AudioPlayer.positionMs.value >= expected.toLong()
                "volume" -> kotlin.math.abs(melox.player.AudioPlayer.volumeLevel.value - expected.toFloat()) < .025f
                "playback-checkpoint" -> melox.player.AudioPlayer.currentTrack.value?.id?.let { id ->
                    if (pcmOutput == null || !melox.player.AudioPlayer.state.value.isPlaying) false else {
                        playbackCheckpoints[expected] = PlaybackCheckpoint(id, melox.player.AudioPlayer.positionMs.value, pcmOutput.evidence().size)
                        true
                    }
                } ?: false
                "playback-continuity" -> playbackCheckpoints[expected]?.let { checkpoint ->
                    melox.player.AudioPlayer.currentTrack.value?.id == checkpoint.track &&
                        melox.player.AudioPlayer.state.value.isPlaying &&
                        melox.player.AudioPlayer.positionMs.value >= checkpoint.position &&
                        pcmOutput?.evidence()?.size == checkpoint.outputCount
                } == true
                "no-playback-error" -> (melox.player.AudioPlayer.state.value.error == null) == expected.toBooleanStrict()
                "repeat" -> melox.playback.PlaybackCommands.playerController()?.repeatMode?.value == expected.toInt()
                "seek-line" -> semantics.tagged("lyric-line-$expected")?.config?.getOrNull(melox.ui.screens.MeloXLyricTimeMsKey)?.let {
                    melox.player.AudioPlayer.positionMs.value == it
                } == true
                "browsing" -> semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricBrowsingKey) == expected.toBooleanStrict()
                "word-lines-min" -> (semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricWordLinesKey) ?: 0) >= expected.toInt()
                "play-ready" -> melox.playback.PlaybackCommands.playerController()?.playWhenReady == expected.toBooleanStrict()
                "playing" -> melox.player.AudioPlayer.state.value.isPlaying == expected.toBooleanStrict()
                "output-playing" -> melox.playback.MeloXPlaybackOutput.state.value.isPlaying == expected.toBooleanStrict()
                "output-volume" -> kotlin.math.abs(melox.playback.MeloXPlaybackOutput.volumeLevel.value - expected.toFloat()) < .05f
                "sleep-timer" -> (melox.playback.MeloXSleepTimer.endEpochMs.value > System.currentTimeMillis()) == expected.toBooleanStrict()
                "ui-font" -> (melox.ui.theme.MeloXMaterialTypography.bodyLarge.fontFamily == androidx.compose.ui.text.font.FontFamily.Default) == (expected == "system")
                "local-playlist-size" -> melox.provider.local.LocalMusicRepository().playlists()
                    .firstOrNull { it.name == expected.substringBeforeLast(':') }?.trackKeys?.size == expected.substringAfterLast(':').toInt()
                "local-favorite" -> melox.player.AudioPlayer.currentTrack.value?.let { track ->
                    track.id.source == melox.music.model.MusicSource.Local &&
                        melox.provider.local.LocalMusicRepository().track(track.id.value)?.isFavorite == expected.toBooleanStrict()
                } == true
                "title" -> melox.player.AudioPlayer.currentTrack.value?.title == expected
                "track" -> melox.player.AudioPlayer.currentTrack.value?.id?.let { "${it.source.storageValue}:${it.value}" } == expected
                "error-kind" -> melox.player.AudioPlayer.state.value.errorKind?.name == expected
                "mix-active" -> melox.playback.MeloXAutoMixTransitionRuntime.state.value.active == expected.toBooleanStrict()
                "transition-ui" -> melox.settings.MeloXSettingsRuntime.transitionUiEnabled == expected.toBooleanStrict()
                "focus-anchor" -> {
                    val line = semantics.tagged("lyric-line-$expected")?.boundsInWindow
                    val panel = semantics.tagged("player-lyrics")?.boundsInWindow
                    val focus = melox.settings.MeloXSettingsRuntime.lyricFocusPosition
                    line != null && panel != null && kotlin.math.abs(line.top + line.height * focus -
                        (panel.top + panel.height * focus)) <= 8f * density
                }
                else -> error("Unknown capture assertion $kind")
            }
        }
        suspend fun awaitUi(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
            val deadline = System.nanoTime() + timeoutMs * 1_000_000L
            while (!condition() && System.nanoTime() < deadline) settle(16)
        }
        fun capture(name: String, timestamp: Long? = null) {
            renderClock = timestamp?.coerceAtLeast(renderClock + 1L)
                ?: if (controlledClock) renderClock + 1_000_000L else maxOf(renderClock + 1_000_000L, System.nanoTime())
            scene.render(renderClock - start).use { image ->
                image.encodeToData(EncodedImageFormat.PNG)?.use { data -> File(output, "$name.png").writeBytes(data.bytes) }
            }
            println("Captured $name: ${width}x$height density=$density")
            val track = melox.player.AudioPlayer.currentTrack.value
            val state = melox.player.AudioPlayer.state.value
            val queue = melox.playback.PlaybackCommands.queue.value
            val semantics = scene.captureSemantics()
            val page = semantics.tagged("now-playing")?.config?.getOrNull(SemanticsProperties.StateDescription)
            File(output, "$name.semantics.json").writeText(semantics.toEvidence().toString(2))
            sceneEvidence.put(JSONObject().put("file", "$name.png")
                .put("memory", JSONObject(melox.diagnostics.MeloXMemoryDiagnostics.snapshot()))
                .put("skia", JSONObject()
                    .put("font_cache_bytes", org.jetbrains.skia.Graphics.fontCacheUsed)
                    .put("resource_cache_bytes", org.jetbrains.skia.Graphics.resourceCacheTotalUsed)
                    .put("live_objects", if (org.jetbrains.skia.impl.Stats.enabled) JSONObject(org.jetbrains.skia.impl.Stats.allocated) else JSONObject.NULL))
                .put("artwork_cache", melox.ui.artwork.MeloXArtworkMemoryCache.stats().let {
                    JSONObject().put("entries", it.entries).put("budgeted_bytes", it.retainedWeight).put("budget_bytes", it.maxWeight)
                })
                .put("captured_elapsed_ms", (System.nanoTime() - start) / 1_000_000.0)
                .put("pointer_mode", if (melox.ui.navigation.MeloXPointerMode.touch) "Touch" else "Mouse")
                .put("touch_keyboard_requests", melox.platform.MeloXWindowsTouchKeyboard.requestCount.get())
                .put("page", page ?: JSONObject.NULL)
                .put("controls_visible", semantics.tagged("player-controls") != null)
                .put("track", track?.id?.let { "${it.source.storageValue}:${it.value}" } ?: JSONObject.NULL)
                .put("title", track?.title ?: JSONObject.NULL)
                .put("is_playing", state.isPlaying).put("is_loading", state.isLoading).put("is_paused", state.isPaused)
                .put("automix_status", melox.playback.MeloXAutoMixTransitionRuntime.state.value.status)
                .put("automix_progress", melox.playback.MeloXAutoMixTransitionRuntime.state.value.progress)
                .put("automix_active", melox.playback.MeloXAutoMixTransitionRuntime.state.value.active)
                .put("error", state.error ?: JSONObject.NULL)
                .put("error_kind", state.errorKind?.name ?: JSONObject.NULL)
                .put("play_when_ready", melox.playback.PlaybackCommands.playerController()?.playWhenReady ?: false)
                .put("position_ms", melox.player.AudioPlayer.positionMs.value)
                .put("duration_ms", melox.player.AudioPlayer.durationMs.value)
                .put("queue_index", queue.index).put("queue_count", queue.songs.size))
            sceneEvidence.getJSONObject(sceneEvidence.length() - 1)
                .put("queue_tracks", JSONArray(queue.songs.map { song -> song.providerTrack?.id?.let {
                    "${it.source.storageValue}:${it.value}"
                } ?: "netease:${song.id}" }))
                .put("queue_entry_ids", JSONArray(queue.entryIds))
                .put("selected_quality", melox.audio.MusicQualityPreferences.read().apiLevel)
                 .put("repeat_mode", melox.playback.PlaybackCommands.playerController()?.repeatMode?.value)
                 .put("onboarding_completed", melox.settings.MeloXSettingsPreferences.boolean("onboarding_completed"))
                 .put("legal_consent_version", melox.settings.MeloXSettingsPreferences.string("legal_consent_version"))
                 .put("cloud_choice_made", melox.remoteconfig.MeloXRemoteConfigConsent.choiceMade())
                 .put("cloud_enabled", melox.remoteconfig.MeloXRemoteConfigConsent.enabled())
                 .put("personalization_consent", melox.recommendation.LocalRecommendationStore.hasPersonalizationConsent())
                .put("shuffle_enabled", melox.playback.PlaybackCommands.playerController()?.shuffleEnabled?.value)
                .put("autoplay_enabled", melox.playback.MeloXPlaybackModeRuntime.autoplayEnabled)
                .put("equalizer", melox.playback.MeloXEqualizerSettings.current.value.let { eq -> JSONObject()
                    .put("enabled", eq.enabled).put("preset", eq.preset).put("preamp_db", eq.preampDb).put("bands_db", JSONArray(eq.gainsDb)) })
                .put("lyric_style", melox.settings.MeloXSettingsRuntime.lyricsStyle.name)
                .put("text_pv_style", melox.settings.MeloXSettingsRuntime.textPVStyle.name)
                .put("text_pv_speed", melox.settings.MeloXSettingsRuntime.textPVAnimationSpeed)
                .put("text_pv_intensity", melox.settings.MeloXSettingsRuntime.textPVMotionIntensity)
                .put("lyric_document", semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricDocumentKey) ?: JSONObject.NULL)
                .put("lyric_word_lines", semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricWordLinesKey) ?: JSONObject.NULL)
        }
        fun assertScene(name: String) {
            val assertions = expectations[name].orEmpty()
            sceneEvidence.getJSONObject(sceneEvidence.length() - 1)
                .put("assertions", JSONArray(assertions.map { assertion -> JSONObject()
                    .put("expected", assertion).put("passed", assertionMatches(assertion)) }))
            assertions.forEach { assertion ->
                check(assertionMatches(assertion)) { "Production UI assertion failed at $name: $assertion" }
                println("Verified $name: $assertion")
            }
        }
        try {
            settle(option("--startup-wait-ms", waitMs.toString()).toLong())
            expectations["00-${startTab.lowercase()}"]?.let { assertions -> awaitUi { assertions.all(::assertionMatches) } }
            capture("00-${startTab.lowercase()}")
            assertScene("00-${startTab.lowercase()}")
            val savedPoints = mutableMapOf<String, Offset>()
            steps.forEachIndexed { index, step ->
                val (name, coordinates) = step.split(':', limit = 2)
                val eventStart = System.nanoTime()
                if (coordinates.startsWith("remember-point@")) {
                    val tag = coordinates.substringAfter('@')
                    val node = checkNotNull(scene.captureSemantics().tagged(tag)) { "No production anchor $tag" }
                    savedPoints[name] = node.boundsInWindow.center
                } else if (coordinates.startsWith("tap-saved@")) {
                    val point = checkNotNull(savedPoints[coordinates.substringAfter('@')])
                    inputEvidence.put(JSONObject().put("step", name).put("type", "Mouse").put("action", "tap-saved-coordinate")
                        .put("position", JSONArray(listOf(point.x, point.y))))
                    scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                    settle(32)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
                } else if (coordinates == "reload-settings") {
                    // Re-read genuine canonical preferences; no validation values are injected.
                    melox.settings.MeloXSettingsRuntime.initialize(force = true)
                } else if (coordinates == "escape") {
                    // Synthetic input is confined to this version-pinned capture harness.
                    scene.sendKeyEvent(KeyEvent(key = Key.Escape, type = KeyEventType.KeyDown))
                    check(scene.sendKeyEvent(KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp))) { "Escape was not handled at $name" }
                } else if (coordinates == "select-all") {
                    scene.sendKeyEvent(KeyEvent(key = Key.A, type = KeyEventType.KeyDown, isCtrlPressed = true))
                    scene.sendKeyEvent(KeyEvent(key = Key.A, type = KeyEventType.KeyUp, isCtrlPressed = true))
                    inputEvidence.put(JSONObject().put("step", name).put("type", "Keyboard").put("action", "Ctrl+A"))
                } else if (coordinates.startsWith("type@")) {
                    val keyboard = java.awt.Canvas()
                    coordinates.substringAfter('@').forEach { character ->
                        val typed = java.awt.event.KeyEvent(keyboard, java.awt.event.KeyEvent.KEY_TYPED,
                            System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_UNDEFINED, character)
                        // Compose Desktop models AWT KEY_TYPED as an Unknown key
                        // event carrying the UTF-16 code point; text fields consume it.
                        scene.sendKeyEvent(KeyEvent(key = Key.Unknown, type = KeyEventType.Unknown,
                            codePoint = character.code, nativeEvent = typed))
                        settle(16)
                    }
                } else if (coordinates.startsWith("tap@") || coordinates.startsWith("tap-first@") || coordinates.startsWith("hold-tag@") ||
                    coordinates.startsWith("touch-tap@") || coordinates.startsWith("touch-tap-first@") ||
                    coordinates.startsWith("right-click@") || coordinates.startsWith("right-click-first@")) {
                    val requested = coordinates.substringAfter('@')
                    val touch = coordinates.startsWith("touch-")
                    val secondary = coordinates.startsWith("right-click")
                    val prefix = coordinates.startsWith("tap-first@") || coordinates.startsWith("touch-tap-first@") || coordinates.startsWith("right-click-first@")
                    fun matchingTag() = if (!prefix) requested else scene.captureSemantics().firstOrNull {
                        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(requested) == true && !it.boundsInWindow.isEmpty &&
                            it.config.contains(SemanticsActions.OnClick)
                    }?.config?.getOrNull(SemanticsProperties.TestTag)
                    awaitUi { matchingTag()?.let { scene.captureSemantics().tagged(it) != null } == true }
                    val tag = checkNotNull(matchingTag()) { "No visible clickable production result for $requested" }
                    awaitUi { scene.captureSemantics().tagged(tag) != null }
                    val node = requireNotNull(scene.captureSemantics().tagged(tag)) { "No visible production node $tag at $name" }
                    check(!node.config.contains(SemanticsProperties.Disabled)) { "$tag is disabled at $name" }
                    val hold = coordinates.startsWith("hold-tag@")
                    fun pointerTarget(candidate: SemanticsNode): SemanticsNode? =
                        if (candidate.config.contains(if (hold) SemanticsActions.OnLongClick else SemanticsActions.OnClick)) candidate
                        else candidate.children.firstNotNullOfOrNull(::pointerTarget)
                    var target = checkNotNull(pointerTarget(node)) { "$tag has no matching pointer action" }
                    check(!target.config.contains(SemanticsProperties.Disabled)) { "$tag pointer target is disabled at $name" }
                    // Dropdown items and page routes animate after their semantics
                    // appear. A real mouse must hit the final target, not the
                    // clipped bounds from the first spring frame.
                    var stableFrames = 0
                    var previousBounds = target.boundsInWindow
                    awaitUi(timeoutMs = 15_000L) {
                        val current = scene.captureSemantics().tagged(tag)?.let(::pointerTarget)
                        if (current == null || current.boundsInWindow.isEmpty) {
                            stableFrames = 0
                            false
                        } else {
                            val b = current.boundsInWindow
                            val drift = maxOf(kotlin.math.abs(b.left - previousBounds.left),
                                kotlin.math.abs(b.top - previousBounds.top), kotlin.math.abs(b.right - previousBounds.right),
                                kotlin.math.abs(b.bottom - previousBounds.bottom))
                            stableFrames = if (drift < .25f * density) stableFrames + 1 else 0
                            previousBounds = b
                            target = current
                            stableFrames >= 4
                        }
                    }
                    check(stableFrames >= 4) { "$tag did not settle before physical click at $name: stableFrames=$stableFrames bounds=$previousBounds" }
                    val point = target.boundsInWindow.center
                    println("Physical click $name: $tag at $point")
                    // A desktop mouse reaches the target before pressing; render
                    // that move so popup disposal and new hit-test paths settle.
                    // Keep down/up on separate frames for pointer coroutines.
                    val pointerType = if (touch) PointerType.Touch else PointerType.Mouse
                    inputEvidence.put(JSONObject().put("step", name).put("type", if (touch) "Touch" else "Mouse")
                        .put("action", if (secondary) "right-click" else "tap").put("x", point.x).put("y", point.y))
                    if (!touch) { scene.sendPointerEvent(PointerEventType.Move, point); settle(16) }
                    scene.sendPointerEvent(PointerEventType.Press, point, type = pointerType, button = if (touch) null else if (secondary) PointerButton.Secondary else PointerButton.Primary)
                    settle(if (hold) 700 else 32)
                    scene.sendPointerEvent(PointerEventType.Release, point, type = pointerType, button = if (touch) null else if (secondary) PointerButton.Secondary else PointerButton.Primary)
                } else if (coordinates.startsWith("touch-drag@") || coordinates.startsWith("touch-edge@") || coordinates.startsWith("mouse-drag@")) {
                    val (tag, dx, dy) = coordinates.substringAfter('@').split(',')
                    val bounds = checkNotNull(scene.captureSemantics().tagged(tag)) { "Missing touch target $tag" }.boundsInWindow
                    val point = if (coordinates.startsWith("touch-edge@")) Offset(bounds.left + 12 * density, bounds.center.y) else bounds.center
                    val delta = Offset(dx.toFloat() * density, dy.toFloat() * density)
                    val pointerType = if (coordinates.startsWith("mouse-drag@")) PointerType.Mouse else PointerType.Touch
                    inputEvidence.put(JSONObject().put("step", name).put("type", pointerType.toString()).put("action", "drag")
                        .put("start", JSONArray(listOf(point.x, point.y))).put("delta", JSONArray(listOf(delta.x, delta.y))))
                    scene.sendPointerEvent(PointerEventType.Press, point, type = pointerType, button = if (pointerType == PointerType.Mouse) PointerButton.Primary else null)
                    settle(32)
                    for (frame in 1..24) {
                        scene.sendPointerEvent(PointerEventType.Move, point + delta * (frame / 24f), type = pointerType)
                        settle(16)
                    }
                    // Let velocity settle before lift so this checks distance/direction, not a fling.
                    settle(160)
                    scene.sendPointerEvent(PointerEventType.Release, point + delta, type = pointerType, button = if (pointerType == PointerType.Mouse) PointerButton.Primary else null)
                } else if (coordinates.startsWith("hover@")) {
                    val tag = coordinates.substringAfter('@')
                    awaitUi { scene.captureSemantics().tagged(tag) != null }
                    val node = checkNotNull(scene.captureSemantics().tagged(tag))
                    scene.sendPointerEvent(PointerEventType.Move, node.boundsInWindow.center)
                } else if (coordinates.startsWith("reveal@") || coordinates.startsWith("reveal-edge@")) {
                    val revealArgs = coordinates.substringAfter('@').split(',')
                    val tag = revealArgs[0]
                    val ownerTag = revealArgs.getOrNull(1)
                    val initialDirection = if (revealArgs.getOrNull(2) == "up") -1f else 1f
                    var revealed = false
                    // The first settings row centers at 112dp below its 74dp
                    // header. A viewport-percentage guard (18% = 140px here)
                    // made it impossible to reveal that row at the top limit.
                    val edge = coordinates.startsWith("reveal-edge@")
                    val topGuard = (if (edge) 16f else 80f) * density
                    val bottomGuard = height - (if (edge) 16f else 170f) * density
                    for (attempt in 0 until 400) {
                        val node = scene.captureSemantics().lastOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
                        // Window bounds are authoritative for scroll visibility;
                        // an offscreen child in verticalScroll clips to empty.
                        // positionInWindow still carries its unclipped position,
                        // which is needed to choose the scroll direction.
                        val rawCenterY = node?.let { it.positionInWindow.y + it.size.height / 2f }
                        val centerY = node?.takeUnless { it.boundsInWindow.isEmpty }?.boundsInWindow?.center?.y
                        if (centerY != null && centerY in topGuard..bottomGuard) {
                            // A wheel animation may pass through the target and
                            // keep moving. Confirm visibility after settling.
                            settle(240)
                            val settled = scene.captureSemantics().tagged(tag)
                            val y = settled?.takeUnless { it.boundsInWindow.isEmpty }?.boundsInWindow?.center?.y
                            if (y != null && y in topGuard..bottomGuard) {
                                revealed = true
                                break
                            }
                        }
                        val direction = if (rawCenterY == null) initialDirection else if (rawCenterY < topGuard) -1f else 1f
                        // Traverse long production settings pages by mouse wheel,
                        // with enough distance to return from their last slider.
                        // Aim inside the owning pane. Window center is the divider
                        // in list-detail mode and would scroll the other page.
                        val ownerBounds = ownerTag?.let { scene.captureSemantics().tagged(it)?.boundsInWindow }
                        val centerX = ownerBounds?.center?.x ?: node?.let { it.positionInWindow.x + it.size.width / 2f } ?: width * .5f
                        val wheelY = ownerBounds?.center?.y ?: height * .55f
                        scene.sendPointerEvent(PointerEventType.Scroll, Offset(centerX.coerceIn(1f, width - 1f), wheelY), scrollDelta = Offset(0f, direction * 3f))
                        settle(240)
                    }
                    // Wheel inertia can carry the target back out after the
                    // loop's last check. Let it stop, then accept a target that
                    // is genuinely visible instead of scrolling it away again.
                    for (attempt in 0 until 12) {
                        if (revealed) break
                        settle(200)
                        val settled = scene.captureSemantics().tagged(tag)
                        val y = settled?.takeUnless { it.boundsInWindow.isEmpty }?.boundsInWindow?.center?.y
                        if (y != null && y in topGuard..bottomGuard) revealed = true
                    }
                    check(revealed) { "Could not reveal production node $tag using mouse wheel at $name" }
                    // A screenshot between reveal and click can exceed the
                    // production lyric follow delay. Keep this pointer gesture
                    // atomic while still locating and hitting the actual node.
                    if (revealArgs.getOrNull(3) == "tap") {
                        val node = checkNotNull(scene.captureSemantics().tagged(tag))
                        fun clickable(candidate: SemanticsNode): SemanticsNode? =
                            if (candidate.config.contains(SemanticsActions.OnClick)) candidate
                            else candidate.children.firstNotNullOfOrNull(::clickable)
                        val target = checkNotNull(clickable(node))
                        check(!target.config.contains(SemanticsProperties.Disabled))
                        check(!target.boundsInWindow.isEmpty)
                        val point = target.boundsInWindow.center
                        scene.sendPointerEvent(PointerEventType.Move, point)
                        scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                        scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
                    }
                } else if (coordinates.startsWith("wait@")) {
                    settle(coordinates.removePrefix("wait@").toLong())
                } else if (coordinates.startsWith("text@")) {
                    val (tag, text) = coordinates.substringAfter('@').split(',', limit = 2)
                    val node = requireNotNull(scene.captureSemantics().tagged(tag)) { "Missing editable production node $tag" }
                    fun edit(candidate: SemanticsNode): Boolean =
                        candidate.config.getOrNull(SemanticsActions.SetText)?.action?.invoke(AnnotatedString(text))
                            ?: candidate.children.any(::edit)
                    check(edit(node)) { "Production field $tag has no enabled editable child" }
                } else if (coordinates.startsWith("scroll@") || coordinates.startsWith("scroll-x@")) {
                    val (x, y, delta) = coordinates.substringAfter('@').split(',').map(String::toFloat)
                    scene.sendPointerEvent(PointerEventType.Scroll, Offset(x, y),
                        scrollDelta = if (coordinates.startsWith("scroll-x@")) Offset(delta, 0f) else Offset(0f, delta))
                } else if (coordinates.startsWith("slider@")) {
                    val (tag, fraction) = coordinates.substringAfter('@').split(',')
                    val bounds = requireNotNull(scene.captureSemantics().tagged(tag)) { "Missing slider node $tag" }.boundsInWindow
                    // Right/bottom bounds are exclusive in desktop hit testing.
                    // Stay just inside for endpoint selections; stepped sliders
                    // still quantize to their exact minimum/maximum.
                    val x = (bounds.left + bounds.width * fraction.toFloat().coerceIn(0f, 1f))
                        .coerceIn(bounds.left + .5f * density, bounds.right - .5f * density)
                    val point = Offset(x, bounds.center.y)
                    scene.sendPointerEvent(PointerEventType.Move, point)
                    settle(16)
                    scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                    settle(32)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
                } else if (coordinates.startsWith("hold@")) {
                    val (x, y) = coordinates.removePrefix("hold@").split(',').map(String::toFloat)
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), button = PointerButton.Primary)
                    settle(700)
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary)
                } else if (coordinates.startsWith("drag@") || coordinates.startsWith("hold-drag@") || coordinates.startsWith("hold-drag-between@")) {
                    val between = coordinates.startsWith("hold-drag-between@")
                    val hold = between || coordinates.startsWith("hold-drag@")
                    val (x, y, endX, endY) = if (between) {
                        val (from, to) = coordinates.substringAfter('@').split(',')
                        val nodes = scene.captureSemantics()
                        val start = requireNotNull(nodes.tagged(from)) { "Missing drag source $from" }.boundsInWindow.center
                        val end = requireNotNull(nodes.tagged(to)) { "Missing drag destination $to" }.boundsInWindow.center
                        println("Physical drag $name: $from at $start -> $to at $end")
                        listOf(start.x, start.y, end.x, end.y)
                    } else if (hold) {
                        val (tag, dx, dy) = coordinates.substringAfter('@').split(',')
                        val center = requireNotNull(scene.captureSemantics().tagged(tag)) { "Missing drag node $tag" }.boundsInWindow.center
                        listOf(center.x, center.y, center.x + dx.toFloat(), center.y + dy.toFloat())
                    } else coordinates.removePrefix("drag@").split(',').map(String::toFloat)
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), button = PointerButton.Primary)
                    if (hold) settle(700)
                    for (frame in 1..20) {
                        val fraction = frame / 20f
                        scene.sendPointerEvent(PointerEventType.Move, Offset(x + (endX - x) * fraction, y + (endY - y) * fraction))
                        settle(16)
                    }
                    scene.sendPointerEvent(PointerEventType.Release, Offset(endX, endY), button = PointerButton.Primary)
                } else {
                    val (x, y) = coordinates.split(',').map { it.toFloat() }
                    val point = Offset(x, y)
                    scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
                }
                // Pointer-target stabilization above can take seconds. Sample the
                // animation on an explicit frame clock: scene.render takes real
                // wall time in software, so wall-clock sleeps would overshoot the
                // requested offsets by hundreds of ms.
                val frameBase = if (controlledClock) renderClock + 1_000_000L else maxOf(renderClock + 1_000_000L, System.nanoTime())
                frameOffsets.forEach { offset ->
                    val target = frameBase + offset * 1_000_000L
                    val frameName = "${(index + 1).toString().padStart(2, '0')}-$name-frame-$offset"
                    val elapsedMs = (target - frameBase) / 1_000_000.0
                    capture(frameName, target)
                    frameEvidence.put(JSONObject().put("file", "$frameName.png").put("requested_ms", offset).put("actual_ms", elapsedMs))
                }
                settle(waitMs)
                val assertions = expectations[name].orEmpty()
                if (assertions.isNotEmpty()) awaitUi(timeoutMs = option("--assert-timeout-ms", "5000").toLong()) {
                    assertions.all(::assertionMatches)
                }
                capture("${(index + 1).toString().padStart(2, '0')}-$name")
                sceneEvidence.getJSONObject(sceneEvidence.length() - 1)
                    .put("action_elapsed_ms", (System.nanoTime() - eventStart) / 1_000_000.0)
                assertScene(name)
            }
        } catch (failure: Throwable) {
            runCatching { capture("failure") }
            throw failure
        } finally {
            melox.playback.PlaybackCommands.playerController()?.release()
            File(output, "capture.json").writeText(JSONObject()
                .put("renderer", "production ImageComposeScene")
                .put("animation_clock", if (controlledClock) "controlled scene time" else "wall clock with explicit frame samples")
                .put("audio_endpoint", if (pcmOutput == null) "Java Sound physical output" else "VALIDATION_SOFTWARE_PCM_OUTPUT")
                .put("physical_audible_acceptance", "BLOCKED")
                .put("pcm_outputs", JSONArray(pcmOutput?.evidence().orEmpty()))
                .put("width", width).put("height", height).put("density", density)
                .put("theme", theme).put("locale", java.util.Locale.getDefault().toLanguageTag())
                .put("home", System.getProperty("user.home"))
                .put("start_tab", startTab)
                .put("enabled_start_tab", "--enable-start-tab" in args)
                .put("play_id", playId ?: JSONObject.NULL)
                .put("play_query", playQuery).put("scenes", sceneEvidence)
                .put("steps", JSONArray(steps)).put("inputs", inputEvidence).put("frames", frameEvidence).toString(2))
            scene.close()
            // This process is finished. Real async lyric/artwork HTTP workers can
            // otherwise keep JavaExec alive after every scene/assertion was saved.
            melox.network.MeloXHttpClient.shared.let { http ->
                http.dispatcher.cancelAll()
                http.connectionPool.evictAll()
                http.dispatcher.executorService.shutdown()
            }
        }
    }
}
