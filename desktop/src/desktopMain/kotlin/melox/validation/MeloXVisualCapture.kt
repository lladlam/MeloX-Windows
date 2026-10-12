package melox.validation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
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
    val steps = option("--steps", "").split(';').filter { it.isNotBlank() }
    val frameOffsets = option("--frames-ms", "").split(',').filter { it.isNotBlank() }.map { it.toLong() }
    require(frameOffsets.all { it >= 0 } && frameOffsets == frameOffsets.distinct().sorted())
    val frameEvidence = JSONArray()
    val sceneEvidence = JSONArray()
    val expectations = option("--expect", "").split(';').filter(String::isNotBlank).associate { entry ->
        val (step, assertions) = entry.split('=', limit = 2)
        step to assertions.split(',')
    }
    withContext(Dispatchers.Main) {
        melox.settings.MeloXSettingsPreferences.initialize()
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
        val playId = option("--play-id", "").takeIf(String::isNotBlank)?.toLong()
        option("--local-wavs", "").takeIf(String::isNotBlank)?.let { paths ->
            val tracks = paths.split(',').map { path ->
                val file = File(path).canonicalFile
                val duration = javax.sound.sampled.AudioSystem.getAudioInputStream(file).use { it.frameLength * 1000 / it.format.frameRate.toLong() }
                val key = java.security.MessageDigest.getInstance("SHA-256").digest(file.path.toByteArray()).joinToString("") { "%02x".format(it) }.take(24)
                melox.music.model.MusicTrack(melox.music.model.MusicResourceId(melox.music.model.MusicSource.Local, key), file.nameWithoutExtension,
                    listOf(melox.music.model.MusicArtistRef(name = "Generated PCM fixture")), durationMs = duration,
                    artworkUrl = File(file.parentFile, "${file.nameWithoutExtension}.png").takeIf(File::isFile)?.toURI()?.toString(),
                    providerMetadata = melox.music.model.ProviderTrackMetadata.Local(file.toURI().toString(), key))
            }
            melox.playback.MeloXPlaybackModePreferences.setAutoMix("--automix" in args)
            melox.playback.MeloXPlaybackModePreferences.setAutoMixString("automix_mode", "Fixed")
            melox.music.provider.MusicProviderSelectionStore.setSelectedSource(melox.music.model.MusicSource.Local)
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
        suspend fun settle(durationMs: Long) {
            val end = System.nanoTime() + durationMs * 1_000_000L
            var frames = 0
            do {
                scene.render(System.nanoTime() - start).close()
                delay(16L)
                frames += 1
            } while (frames < 3 || System.nanoTime() < end)
        }
        fun assertionMatches(assertion: String): Boolean {
            val (kind, expected) = assertion.split(':', limit = 2)
            val semantics = scene.captureSemantics()
            return when (kind) {
                "page" -> semantics.tagged("now-playing")?.config?.getOrNull(SemanticsProperties.StateDescription) == expected
                "state" -> semantics.tagged(expected.substringBeforeLast(':'))?.config?.getOrNull(SemanticsProperties.StateDescription) == expected.substringAfterLast(':')
                "index" -> melox.playback.PlaybackCommands.queue.value.index == expected.toInt()
                "tag" -> semantics.tagged(expected) != null
                "full-tag" -> semantics.tagged(expected)?.boundsInWindow?.let { it.width >= width * .97f && it.height >= height * .97f } == true
                "absent" -> semantics.tagged(expected) == null
                "selected" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.Selected) == true
                "unselected" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.Selected) == false
                "checked" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.On
                "unchecked" -> semantics.tagged(expected)?.config?.getOrNull(SemanticsProperties.ToggleableState) == ToggleableState.Off
                "quality" -> melox.audio.MusicQualityPreferences.read().apiLevel == expected
                "queue-min" -> melox.playback.PlaybackCommands.queue.value.songs.size >= expected.toInt()
                "repeat" -> melox.playback.PlaybackCommands.playerController()?.repeatMode?.value == expected.toInt()
                "seek-line" -> semantics.tagged("lyric-line-$expected")?.config?.getOrNull(melox.ui.screens.MeloXLyricTimeMsKey)?.let {
                    melox.player.AudioPlayer.positionMs.value == it
                } == true
                "browsing" -> semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricBrowsingKey) == expected.toBooleanStrict()
                "word-lines-min" -> (semantics.tagged("player-lyrics")?.config?.getOrNull(melox.ui.screens.MeloXLyricWordLinesKey) ?: 0) >= expected.toInt()
                "play-ready" -> melox.playback.PlaybackCommands.playerController()?.playWhenReady == expected.toBooleanStrict()
                "error-kind" -> melox.player.AudioPlayer.state.value.errorKind?.name == expected
                "mix-active" -> melox.playback.MeloXAutoMixTransitionRuntime.state.value.active == expected.toBooleanStrict()
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
        suspend fun awaitUi(condition: () -> Boolean) {
            val deadline = System.nanoTime() + 5_000_000_000L
            while (!condition() && System.nanoTime() < deadline) settle(16)
        }
        fun capture(name: String) {
            scene.render(System.nanoTime() - start).use { image ->
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
                .put("captured_elapsed_ms", (System.nanoTime() - start) / 1_000_000.0)
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
                .put("shuffle_enabled", melox.playback.PlaybackCommands.playerController()?.shuffleEnabled?.value)
                .put("autoplay_enabled", melox.playback.MeloXPlaybackModeRuntime.autoplayEnabled)
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
            steps.forEachIndexed { index, step ->
                val (name, coordinates) = step.split(':', limit = 2)
                val eventStart = System.nanoTime()
                if (coordinates == "escape") {
                    // Synthetic input is confined to this version-pinned capture harness.
                    scene.sendKeyEvent(KeyEvent(key = Key.Escape, type = KeyEventType.KeyDown))
                    check(scene.sendKeyEvent(KeyEvent(key = Key.Escape, type = KeyEventType.KeyUp))) { "Escape was not handled at $name" }
                } else if (coordinates.startsWith("tap@") || coordinates.startsWith("hold-tag@")) {
                    val tag = coordinates.substringAfter('@')
                    awaitUi { scene.captureSemantics().tagged(tag) != null }
                    val node = requireNotNull(scene.captureSemantics().tagged(tag)) { "No visible production node $tag at $name" }
                    check(!node.config.contains(SemanticsProperties.Disabled)) { "$tag is disabled at $name" }
                    val hold = coordinates.startsWith("hold-tag@")
                    check(node.config.contains(if (hold) SemanticsActions.OnLongClick else SemanticsActions.OnClick)) { "$tag has no matching pointer action" }
                    val point = node.boundsInWindow.center
                    scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                    if (hold) settle(700)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
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
                    val point = Offset(bounds.left + bounds.width * fraction.toFloat().coerceIn(0f, 1f), bounds.center.y)
                    scene.sendPointerEvent(PointerEventType.Press, point, button = PointerButton.Primary)
                    scene.sendPointerEvent(PointerEventType.Release, point, button = PointerButton.Primary)
                } else if (coordinates.startsWith("hold@")) {
                    val (x, y) = coordinates.removePrefix("hold@").split(',').map(String::toFloat)
                    scene.sendPointerEvent(PointerEventType.Press, Offset(x, y), button = PointerButton.Primary)
                    settle(700)
                    scene.sendPointerEvent(PointerEventType.Release, Offset(x, y), button = PointerButton.Primary)
                } else if (coordinates.startsWith("drag@") || coordinates.startsWith("hold-drag@")) {
                    val hold = coordinates.startsWith("hold-drag@")
                    val (x, y, endX, endY) = if (hold) {
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
                frameOffsets.forEach { offset ->
                    val remainingMs = (eventStart + offset * 1_000_000L - System.nanoTime()) / 1_000_000L
                    if (remainingMs > 0) settle(remainingMs)
                    val frameName = "${(index + 1).toString().padStart(2, '0')}-$name-frame-$offset"
                    val elapsedMs = (System.nanoTime() - eventStart) / 1_000_000.0
                    capture(frameName)
                    frameEvidence.put(JSONObject().put("file", "$frameName.png").put("requested_ms", offset).put("actual_ms", elapsedMs))
                }
                settle(waitMs)
                val assertions = expectations[name].orEmpty()
                if (assertions.isNotEmpty()) awaitUi { assertions.all(::assertionMatches) }
                capture("${(index + 1).toString().padStart(2, '0')}-$name")
                sceneEvidence.getJSONObject(sceneEvidence.length() - 1)
                    .put("action_elapsed_ms", (System.nanoTime() - eventStart) / 1_000_000.0)
                assertScene(name)
            }
        } finally {
            melox.playback.PlaybackCommands.playerController()?.release()
            File(output, "capture.json").writeText(JSONObject()
                .put("renderer", "production ImageComposeScene")
                .put("audio_endpoint", if (pcmOutput == null) "Java Sound physical output" else "VALIDATION_SOFTWARE_PCM_OUTPUT")
                .put("physical_audible_acceptance", "BLOCKED")
                .put("pcm_outputs", JSONArray(pcmOutput?.evidence().orEmpty()))
                .put("width", width).put("height", height).put("density", density)
                .put("theme", theme).put("home", System.getProperty("user.home"))
                .put("start_tab", startTab)
                .put("enabled_start_tab", "--enable-start-tab" in args)
                .put("play_id", playId ?: JSONObject.NULL)
                .put("play_query", playQuery).put("scenes", sceneEvidence)
                .put("steps", JSONArray(steps)).put("frames", frameEvidence).toString(2))
            scene.close()
        }
    }
}
