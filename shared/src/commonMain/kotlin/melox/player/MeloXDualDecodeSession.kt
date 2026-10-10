package melox.player

import kotlinx.coroutines.*
import melox.playback.MeloXAutoMixPlan
import melox.playback.MeloXAutoMixFadeCurve
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.SourceDataLine

internal data class MeloXDeckSource(val uri: String, val headers: Map<String, String> = emptyMap(), val startMs: Long = 0,
    val identity: String = uri, val durationMs: Long = 0)
internal data class MeloXDualClock(val deck: Int, val positionMs: Long, val renderedFrames: Long, val mixProgress: Double,
    val source: MeloXDeckSource? = null, val incoming: MeloXDeckSource? = null, val incomingPositionMs: Long = 0)
internal data class MeloXDualPcmResult(val primaryFrames: Long, val incomingFrames: Long, val overlapFrames: Long, val writtenFrames: Long, val renderedFrames: Long, val clippedSamples: Long)

/** Continuous single output with dynamic native incoming preload and independently owned decoder groups.
 * The initial plan constructor also supports deterministic offline PCM integration tests.
 * All clocks and handoffs are driven by rendered frames; decoder read-ahead cannot advance metadata.
 */
@OptIn(InternalCoroutinesApi::class)
internal class MeloXDualDecodeSession(
    private val primarySource: MeloXDeckSource,
    private val incomingSource: MeloXDeckSource? = null,
    private val plan: MeloXAutoMixPlan = MeloXAutoMixPlan(0, 0),
    private val curve: MeloXAutoMixFadeCurve = MeloXAutoMixFadeCurve.EqualPower,
    private val outputFactory: () -> SourceDataLine,
    private val bassSwap: Boolean = true,
    private val onClock: (MeloXDualClock) -> Unit = {},
    private val onHandoff: () -> Unit = {},
    private val onFallback: (Throwable) -> Unit = {},
    private val onReady: () -> Unit = {},
) : Closeable {
    private class Prepared(val source: MeloXDeckSource, val plan: MeloXAutoMixPlan, val curve: MeloXAutoMixFadeCurve,
        val incoming: MeloXBufferedPcmDeck, val replacement: MeloXBufferedPcmDeck?) : Closeable {
        override fun close() { incoming.close(); replacement?.close() }
    }
    private data class Segment(val frame: Long, val source: MeloXDeckSource, val positionMs: Long,
        val startRate: Float = 1f, val endRate: Float = 1f, val durationMs: Long = 0)
    private data class Mix(val startFrame: Long, val prepared: Prepared)
    private val closed = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val revision = AtomicLong()
    private val selection = AtomicReference(primarySource)
    private val prepared = AtomicReference<Prepared?>(null)
    private val active = AtomicReference<Prepared?>(null)
    private val decks = ConcurrentHashMap.newKeySet<MeloXBufferedPcmDeck>()
    private val output = AtomicReference<SourceDataLine?>(null)
    private val lock = Any()
    private var clock = MeloXDualClock(0, primarySource.startMs, 0, 0.0, primarySource)
    private val running = AtomicBoolean(false)
    private val pendingHandoff = AtomicBoolean(false)
    @Volatile var volume: Float = 1f
    @Volatile private var preparing = false

    fun hasMix() = prepared.get() != null || active.get() != null || preparing || pendingHandoff.get()
    fun pause() { paused.set(true); output.get()?.let { runCatching { it.stop() } } }
    fun resume() { paused.set(false); output.get()?.let { runCatching { it.start() } } }
    fun clock(): MeloXDualClock = synchronized(lock) { clock }
    fun seek(positionMs: Long) = replace((clock().source ?: primarySource).copy(startMs = positionMs.coerceAtLeast(0)))
    fun next(): Boolean {
        val heard = clock()
        val next = heard.incoming ?: return false
        replace(next.copy(startMs = heard.incomingPositionMs))
        return true
    }
    private fun replace(target: MeloXDeckSource) {
        synchronized(lock) {
            if (closed.get()) return
            selection.set(target); revision.incrementAndGet()
            clock = MeloXDualClock(if (target.identity == primarySource.identity) 0 else 1, target.startMs, 0, 0.0, target)
        }
        prepared.getAndSet(null)?.close(); active.getAndSet(null)?.close()
        pendingHandoff.set(false)
        releaseResources()
    }
    suspend fun prepareIncoming(next: MeloXDeckSource, plan: MeloXAutoMixPlan, curve: MeloXAutoMixFadeCurve): Boolean = withContext(Dispatchers.IO) {
        if (!plan.performsTransition || closed.get() || hasMix()) return@withContext false
        val token = revision.get()
        preparing = true
        var incoming: MeloXBufferedPcmDeck? = null; var replacement: MeloXBufferedPcmDeck? = null
        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
            incoming?.close(); replacement?.close()
        }
        try {
            incoming = openDeck(next.copy(startMs = plan.incomingStartMs), MeloXPcmTempo.filter(plan.incomingStartRate, plan.incomingEndRate, plan.durationMs))
            incoming.awaitReady()
            if (plan.outgoingStartRate != 1f || plan.outgoingEndRate != 1f) {
                replacement = openDeck((clock().source ?: primarySource).copy(startMs = plan.outgoingStartMs),
                    MeloXPcmTempo.filter(plan.outgoingStartRate, plan.outgoingEndRate, plan.durationMs))
                replacement.awaitReady()
            }
            val result = Prepared(next, plan, curve, incoming, replacement)
            if (closed.get() || token != revision.get() || !prepared.compareAndSet(null, result)) { result.close(); false }
            else true
        } catch (failure: Throwable) { incoming?.close(); replacement?.close(); throw failure }
        finally { preparing = false; cancellation.dispose() }
    }
    private fun openDeck(source: MeloXDeckSource, filters: String? = null): MeloXBufferedPcmDeck =
        MeloXBufferedPcmDeck(source, filters).also { decks.add(it); if (closed.get()) it.close() }

    suspend fun run(): MeloXDualPcmResult = withContext(Dispatchers.IO) {
        check(running.compareAndSet(false, true)) { "A PCM session can only run once" }
        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { close() }
        var primaryFrames = 0L; var incomingFrames = 0L; var overlapFrames = 0L; var totalWritten = 0L; var totalRendered = 0L; var clipped = 0L
        try {
            if (incomingSource != null && plan.performsTransition) {
                try { prepareIncoming(incomingSource, plan, curve) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { onFallback(failure) }
            }
            while (!closed.get()) {
                ensureActive()
                val version = revision.get(); val selected = selection.get()
                var a: MeloXBufferedPcmDeck? = null; var line: SourceDataLine? = null; var monitor: Job? = null
                var written = 0L
                val publishedWritten = AtomicLong()
                val segments = mutableListOf(Segment(0, selected, selected.startMs))
                val mixes = mutableListOf<Mix>()
                val publishLock = Any()
                var handedOff = -1L
                val mixer = MeloXPcmMix(bassSwap)
                fun publish() = synchronized(publishLock) {
                    if (closed.get() || revision.get() != version) return
                    val endpoint = line ?: return
                    val heard = endpoint.longFramePosition.coerceIn(0, publishedWritten.get())
                    val segment: Segment; val mix: Mix?
                    synchronized(lock) {
                        segment = segments.lastOrNull { it.frame <= heard } ?: segments.first()
                        mix = mixes.lastOrNull { it.startFrame <= heard }
                    }
                    val elapsed = (heard - segment.frame) * 1000.0 / AudioPlayer.PCM_SAMPLE_RATE
                    val position = segment.positionMs + MeloXPcmTempo.contentMs(elapsed, segment.startRate, segment.endRate, segment.durationMs).toLong()
                    val elapsedMix = mix?.let { (heard - it.startFrame) * 1000.0 / AudioPlayer.PCM_SAMPLE_RATE } ?: 0.0
                    val p = mix?.let { (elapsedMix / it.prepared.plan.durationMs).coerceIn(0.0, 1.0) } ?: 0.0
                    val next = mix?.prepared?.source
                    val nextPosition = mix?.let { it.prepared.plan.incomingStartMs + MeloXPcmTempo.contentMs(elapsedMix,
                        it.prepared.plan.incomingStartRate, it.prepared.plan.incomingEndRate, it.prepared.plan.durationMs).toLong() } ?: 0
                    val value = MeloXDualClock(if (segment.source.identity == primarySource.identity) 0 else 1,
                        position, heard, p, segment.source, if (p < 1) next else null, nextPosition)
                    var handoff = false
                    synchronized(lock) {
                        if (closed.get() || revision.get() != version) return
                        clock = value
                        if (mix != null && p >= 1 && handedOff != mix.startFrame) {
                            handedOff = mix.startFrame; handoff = true; pendingHandoff.set(false)
                            mixes.removeAll { it.startFrame < mix.startFrame }
                            segments.removeAll { it.frame < segment.frame }
                        }
                    }
                    onClock(value)
                    if (handoff) onHandoff()
                }
                try {
                    a = openDeck(selected)
                    line = try { outputFactory() } catch (failure: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Output, failure) }
                    output.set(line)
                    if (closed.get() || revision.get() != version) continue
                    val blockA = ByteArray(3528); val blockB = ByteArray(3528)
                    var started = false; var mixing: Prepared? = null; var mixStart = -1L; var mixed = 0L
                    monitor = launch { while (isActive && !closed.get() && revision.get() == version) { publish(); delay(20) } }
                    while (!closed.get() && revision.get() == version) {
                        while (paused.get() && !closed.get() && revision.get() == version) delay(10)
                        if (closed.get() || revision.get() != version) break
                        val pending = prepared.get()
                        val segment = synchronized(lock) { segments.last() }
                        val position = segment.positionMs + MeloXPcmTempo.contentMs((written - segment.frame) * 1000.0 / AudioPlayer.PCM_SAMPLE_RATE,
                            segment.startRate, segment.endRate, segment.durationMs).toLong()
                        if (mixing == null && pending != null && position >= pending.plan.outgoingStartMs) {
                            if (position > pending.plan.outgoingStartMs + 40) {
                                if (prepared.compareAndSet(pending, null)) { pending.close(); onFallback(IOException("AutoMix plan arrived after decode boundary")) }
                            } else if (prepared.compareAndSet(pending, null)) {
                                mixing = pending; active.set(pending); pendingHandoff.set(true); mixStart = written; mixed = 0
                                pending.replacement?.let { a?.close(); a = it }
                                synchronized(lock) {
                                    segments += Segment(written, segment.source, position, pending.plan.outgoingStartRate, pending.plan.outgoingEndRate, pending.plan.durationMs)
                                    mixes += Mix(written, pending)
                                }
                            }
                        }
                        var length = blockA.size
                        if (mixing == null && pending != null) {
                            val untilStart = (pending.plan.outgoingStartMs - position) * AudioPlayer.PCM_SAMPLE_RATE / 1000
                            if (untilStart > 0) length = minOf(length, (untilStart * 4).toInt())
                        }
                        mixing?.let { length = minOf(length, ((it.plan.durationMs * AudioPlayer.PCM_SAMPLE_RATE / 1000 - mixed) * 4).toInt()) }
                        var count = requireNotNull(a).read(blockA, length)
                        val currentMix = mixing
                        if (currentMix != null) {
                            try {
                                // A native short primary tail may be padded only while the real incoming is ready.
                                if (count < length) blockA.fill(0, count, length)
                                val received = currentMix.incoming.read(blockB, length, wait = false)
                                if (received != length) throw IOException("Incoming audio ended during AutoMix")
                                primaryFrames += count / 4; incomingFrames += received / 4
                                count = length
                                mixer.mix(blockA, blockB, count, mixed, currentMix.plan.durationMs * AudioPlayer.PCM_SAMPLE_RATE / 1000, currentMix.curve)
                                mixed += count / 4; overlapFrames += count / 4
                            } catch (failure: Exception) {
                                currentMix.close(); active.compareAndSet(currentMix, null); mixing = null; pendingHandoff.set(false)
                                a?.close()
                                val resumePosition = position
                                a = openDeck(segment.source.copy(startMs = resumePosition))
                                synchronized(lock) { mixes.removeAll { it.startFrame == mixStart }; segments += Segment(written, segment.source, resumePosition) }
                                onFallback(failure)
                                continue
                            }
                        } else if (segment.source.identity == primarySource.identity) primaryFrames += count / 4 else incomingFrames += count / 4
                        if (closed.get() || revision.get() != version) break
                        if (count == 0) {
                            if (!started && !(selected.durationMs > 0 && selected.startMs >= selected.durationMs)) throw IOException("Decoder emitted no PCM")
                            while (!closed.get() && revision.get() == version && line.longFramePosition < written) {
                                if (paused.get()) line.stop() else if (!line.isRunning) line.start()
                                publish(); delay(10)
                            }
                            publish()
                            totalWritten += written; totalRendered += line.longFramePosition.coerceIn(0, written); clipped += mixer.clippedSamples
                            if (!closed.get() && revision.get() == version) {
                                if (!started) synchronized(lock) { clock = clock.copy(positionMs = selected.startMs) }
                                return@withContext MeloXDualPcmResult(primaryFrames, incomingFrames, overlapFrames, totalWritten, totalRendered, clipped)
                            }
                            break
                        }
                        while (paused.get() && !closed.get() && revision.get() == version) delay(10)
                        if (closed.get() || revision.get() != version) break
                        if (!started) { started = true; onReady() }
                        if (!line.isRunning) try { line.start() } catch (failure: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Output, failure) }
                        MeloXPcmMix.scale(blockA, count, volume)
                        var offset = 0
                        while (offset < count && !closed.get() && revision.get() == version) {
                            val accepted = try { line.write(blockA, offset, count - offset) } catch (failure: Exception) { throw AudioPlaybackFailure(AudioFailureKind.Output, failure) }
                            if (accepted <= 0 || accepted % 4 != 0) throw AudioPlaybackFailure(AudioFailureKind.Output, IOException("Output stopped accepting aligned PCM"))
                            offset += accepted; written += accepted / 4; publishedWritten.set(written)
                        }
                        if (currentMix != null && mixing != null && mixed >= currentMix.plan.durationMs * AudioPlayer.PCM_SAMPLE_RATE / 1000) {
                            a?.close(); a = currentMix.incoming
                            currentMix.replacement?.close(); active.compareAndSet(currentMix, null); mixing = null
                            decks.removeIf { it.isClosed() }
                            val incomingPosition = currentMix.plan.incomingStartMs + MeloXPcmTempo.contentMs(currentMix.plan.durationMs.toDouble(),
                                currentMix.plan.incomingStartRate, currentMix.plan.incomingEndRate, currentMix.plan.durationMs).toLong()
                            synchronized(lock) { segments += Segment(written, currentMix.source, incomingPosition) }
                        }
                        publish()
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { if (!closed.get() && revision.get() == version) throw failure }
                finally {
                    withContext(NonCancellable) { monitor?.cancelAndJoin() }
                    line?.let { output.compareAndSet(it, null); runCatching { it.stop(); it.close() } }
                    a?.close()
                    decks.forEach { it.close() }; decks.clear()
                }
            }
            MeloXDualPcmResult(primaryFrames, incomingFrames, overlapFrames, totalWritten, totalRendered, clipped)
        } finally { cancellation.dispose(); releaseResources() }
    }
    private fun releaseResources() {
        output.getAndSet(null)?.let { runCatching { it.stop(); it.close() } }
        decks.forEach { it.close() }; decks.clear()
    }
    override fun close() {
        synchronized(lock) { closed.set(true); revision.incrementAndGet() }
        prepared.getAndSet(null)?.close(); active.getAndSet(null)?.close()
        releaseResources()
    }
}
