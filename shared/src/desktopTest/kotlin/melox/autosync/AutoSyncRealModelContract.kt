package melox.autosync

import java.io.File
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import melox.lyrics.LrcLyricsParser
import melox.lyrics.LyricTimingKind
import melox.model.SearchSong
import melox.music.model.MusicResourceId
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.ProviderTrackMetadata
import melox.network.MeloXGitHubRouting
import melox.network.MeloXGitHubSource
import melox.playback.DesktopPlaybackRequest
import melox.playback.MeloXDesktopPlayer
import melox.playback.MeloXPlaybackModePreferences
import melox.playback.PlaybackCommands
import melox.player.AudioPlayer
import melox.settings.MeloXSettingsPreferences
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.source
import kotlin.math.abs

/**
 * Opt-in executable for the published model bytes and the project's own PCM
 * vectors. No weights are committed, and this never reads the user's library.
 * Expected alignments were independently produced by the AutoSync Python CTC
 * and CIF references from the same 16-bit WAVs and beta.4 model assets.
 */
object AutoSyncRealModelContract {
    @JvmStatic fun main(args: Array<String>) = runBlocking<Unit> {
        require(args.size == 1 && args[0].isNotBlank()) { "Set -PautoSyncFixtures to a directory of beta.4 assets and contract WAVs" }
        val fixtures = File(args[0])
        val indexFile = File(fixtures, "android-models.json")
        check(sha256(indexFile) == AutoSyncModelRepository.INDEX_SHA256) { "Wrong model index" }
        val waveHashes = mapOf(
            "en" to "aefb710704aa6df5e66f343fc506e7b04585a5cf932f1cdc078a10325ed37dbb",
            "ja" to "b029d9656cdccf5db9e304defef005f91c3ebacbaf1e50c5f2d6441e598573b0",
            "zh" to "ee25fe598a920c896b6bb86b57f0c79409b4186c24745fd1d1d9de8db64f2c2e",
        )
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val name = chain.request().url.pathSegments.last()
            val asset = File(fixtures, name)
            check(asset.isFile) { "Missing beta.4 fixture: $name" }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(asset.source().buffer().asResponseBody(contentLength = asset.length())).build()
        }.build()
        val root = Files.createTempDirectory("melox-autosync-contract-").toFile()
        // All production stores, playback state and preferences live in this invocation's home.
        System.setProperty("user.home", File(root, "home").apply { mkdirs() }.absolutePath)
        val routing = MeloXGitHubRouting(client)
        val previous = routing.selectedSource()
        routing.selectSource(MeloXGitHubSource.GitHubDoh)
        try {
            val repository = AutoSyncModelRepository(File(root, "home/.melox/autosync/models"), routing)
            val index = repository.fetchIndex()
            for (language in listOf("en", "ja", "zh")) {
                val wav = File(fixtures, "${language}_vector.wav")
                check(sha256(wav) == waveHashes.getValue(language)) { "Wrong $language waveform" }
                repository.install(index.packs.getValue(language))
                val model = checkNotNull(repository.installed(language))
                OnnxAlignmentEngine.open(model).use { engine ->
                    val pcm = pcm(wav)
                    when (language) {
                        "en" -> checkCtc(engine, pcm, listOf(11, 5, 15, 15, 8),
                            listOf(3 to 3, 8 to 8, 23 to 23, 63 to 63, 69 to 69), -23.99066960066557)
                        "ja" -> checkCtc(engine, pcm, listOf(46, 11, 4, 17, 5, 46),
                            listOf(112 to 112, 140 to 140, 143 to 143, 146 to 146, 147 to 147, 148 to 148), -3.3208488352101995)
                        "zh" -> {
                            val spans = engine.chinese(pcm, 5).map { it.startMs to it.endMs }
                            check(spans == listOf(410L to 650L, 830L to 1070L, 1250L to 1490L,
                                2030L to 2270L, 2430L to 2670L)) { "Chinese reference mismatch: $spans" }
                        }
                    }
                }
                println("PASS: $language beta.4 verified install and real ONNX/Python-reference alignment")
            }
            verifyPlaybackIntegration(File(fixtures, "en_vector.wav"), "en")
            verifyPlaybackIntegration(File(fixtures, "zh_vector.wav"), "zh")
        } finally {
            routing.selectSource(previous)
            root.deleteRecursively() // This executable created this isolated directory.
        }
    }

    private suspend fun verifyPlaybackIntegration(wav: File, language: String) {
        val previousOutput = AudioPlayer.outputLineFactory
        val output = ControlledOutput()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val trackId = "autosync-contract-$language"
        val track = MusicTrack(MusicResourceId(MusicSource.Local, trackId), "Contract vector $language", emptyList(),
            durationMs = 3_000L, providerMetadata = ProviderTrackMetadata.Local(wav.toURI().toString(), trackId))
        val song = SearchSong(1, track.title, "generated", "fixture", null, durationMs = 3_000L, providerTrack = track)
        MeloXPlaybackModePreferences.reset()
        MeloXSettingsPreferences.setBoolean("autosync_word_lyrics_enabled", true)
        AudioPlayer.outputLineFactory = { output.line }
        val player = MeloXDesktopPlayer({ DesktopPlaybackRequest(wav.absolutePath) }, { start ->
            AudioPlayer.play(start.request.uri, start.track, start.request.headers, start.positionMs, start.durationMs,
                start.playWhenReady, start.onEnded, start.onFailure)
        }, scope, Dispatchers.IO, {}, { _, _ -> })
        try {
            PlaybackCommands.adoptController(player)
            player.setMediaItems(listOf(song), 0, 0)
            player.prepare(); player.play(); PlaybackCommands.publishQueue(player)
            withTimeout(30_000) {
                while (output.accepted.get() == 0L) {
                    check(AudioPlayer.state.value.error == null) { "Playback failed: ${AudioPlayer.state.value.error}" }
                    delay(20)
                }
            }
            val lyric = if (language == "en") "HELLO" else "你好世界啊"
            val parsed = LrcLyricsParser.parse("[00:00.00]$lyric\n[00:02.95]作曲：Somebody")
            val document = if (language == "en") parsed.copy(lines = listOf(parsed.lines[0],
                parsed.lines[0].copy(text = "WORLD", timingKind = LyricTimingKind.Precise), parsed.lines[1])) else parsed
            check(MeloXAutoSync.eligible(document))
            val aligned = checkNotNull(MeloXAutoSync.generate(track, document)) { "Real playback alignment returned no timeline" }
            val units = aligned.lines.first().syllables
            val actual = units.map { Triple(it.text, it.startTimeMs, it.endTimeMs) }
            val expected = if (language == "en") listOf(Triple("HELLO", 60L, 1400L)) else listOf(
                Triple("你", 410L, 650L), Triple("好", 830L, 1070L), Triple("世", 1250L, 1490L),
                Triple("界", 2030L, 2270L), Triple("啊", 2430L, 2670L))
            check(actual == expected) { "Unexpected $language playback alignment: $actual" }
            check(aligned.lines.drop(1) == document.lines.drop(1)) { "Untimed/credit lines must retain their original timing" }
            check(MeloXAutoSync.generate(track, document) == aligned) { "Stored timeline did not reload" }
            if (language == "en") verifySelectionChangeDiscardsInFlightGeneration(player, song, track)
            MeloXSettingsPreferences.setBoolean("autosync_word_lyrics_enabled", false)
            check(MeloXAutoSync.generate(track, document) == null) { "Disabled AutoSync must not generate" }
            println("PASS: real playback PCM -> FFmpeg -> installed $language ONNX -> LRC glyph timeline and cache")
        } finally {
            player.release(); scope.cancel(); AudioPlayer.stop(); AudioPlayer.outputLineFactory = previousOutput
            MeloXPlaybackModePreferences.reset()
        }
    }

    private suspend fun verifySelectionChangeDiscardsInFlightGeneration(
        player: MeloXDesktopPlayer, song: SearchSong, track: MusicTrack,
    ) = coroutineScope {
        val home = File(System.getProperty("user.home"), ".melox")
        val timeline = File(home, "autosync/timelines").listFiles()!!.single()
        val before = timeline.readBytes()
        val pcmDirectory = File(home, "cache/autosync-pcm")
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch(Dispatchers.IO) {
            MeloXAutoSyncModels.repository.withInstalled("en") { held.complete(Unit); release.await() }
        }
        try {
            withTimeout(30_000) { held.await() }
            val changedDocument = LrcLyricsParser.parse("[00:00.00]HELLO\n[00:02.94]作曲：Somebody")
            val pending = async(Dispatchers.IO) { MeloXAutoSync.generate(track, changedDocument) }
            withTimeout(30_000) {
                while (pcmDirectory.listFiles().isNullOrEmpty()) {
                    check(pending.isActive) { "Generation ended before the selection changed" }
                    delay(20)
                }
            }
            // Replacing the queue assigns a new entry identity, as a real track change does.
            player.setMediaItems(listOf(song), 0, 0)
            PlaybackCommands.publishQueue(player)
            release.complete(Unit)
            val stale = try { withTimeout(30_000) { pending.await() } } catch (cancelled: CancellationException) {
                if (cancelled is TimeoutCancellationException) throw cancelled
                null
            }
            check(stale == null && timeline.readBytes().contentEquals(before)) { "Retired generation published a timeline" }
            println("PASS: selection change discards in-flight real-PCM alignment and preserves cache")
        } finally {
            release.complete(Unit)
            holder.join()
        }
    }

    /** Holds the real software output open while inference runs, without claiming physical sound. */
    private class ControlledOutput {
        val accepted = AtomicLong()
        private val closed = AtomicBoolean(false)
        private val running = AtomicBoolean(false)
        private val gate = Object()
        val line = Proxy.newProxyInstance(SourceDataLine::class.java.classLoader, arrayOf(SourceDataLine::class.java)) { _, method, args ->
            when (method.name) {
                "write" -> synchronized(gate) {
                    while (!closed.get() && (accepted.get() >= 35_280 || !running.get())) gate.wait(10)
                    check(!closed.get())
                    val count = minOf(args!![2] as Int, 2048, (35_280 - accepted.get()).toInt())
                    accepted.addAndGet(count.toLong()); count
                }
                "getLongFramePosition" -> 0L
                "start" -> synchronized(gate) { running.set(true); gate.notifyAll(); null }
                "stop" -> { running.set(false); null }
                "close" -> synchronized(gate) { running.set(false); closed.set(true); gate.notifyAll(); null }
                "isRunning", "isActive" -> running.get()
                "isOpen" -> !closed.get()
                "getControl" -> throw IllegalArgumentException("No MASTER_GAIN")
                "toString" -> "AutoSyncContractOutput"
                else -> error("Unexpected output method ${method.name}")
            }
        } as SourceDataLine
    }

    private fun checkCtc(engine: OnnxAlignmentEngine, pcm: FloatArray, targets: List<Int>, expected: List<Pair<Int, Int>>, total: Double) {
        val result = engine.ctc(pcm, targets)
        check(result.feasible) { "Infeasible: ${result.reasons}" }
        val spans = result.spans.map { checkNotNull(it).let { span -> span.startFrame to span.endFrame } }
        check(spans == expected) { "CTC reference mismatch: $spans" }
        check(abs(result.totalLogProb - total) < 0.05) { "CTC log-probability mismatch: ${result.totalLogProb}" }
    }

    private fun pcm(file: File): FloatArray = AudioSystem.getAudioInputStream(file).use { audio ->
        val format = audio.format
        check(format.encoding == AudioFormat.Encoding.PCM_SIGNED && format.sampleSizeInBits == 16 &&
            format.channels == 1 && format.sampleRate == 16_000f && !format.isBigEndian)
        val bytes = audio.readAllBytes()
        check(bytes.size == 48_000 * 2)
        FloatArray(bytes.size / 2) { i ->
            (((bytes[2 * i].toInt() and 0xff) or (bytes[2 * i + 1].toInt() shl 8)).toShort().toInt() / 32768f)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
