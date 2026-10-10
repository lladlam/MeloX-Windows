package melox.playback

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import melox.player.FfmpegPcmDecoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.*

/** Real s16le PCM analysis. FFT/onset/beat/phrase equations follow Android 3f877ea. */
@OptIn(InternalCoroutinesApi::class)
class MeloXAutoMixAudioAnalyzer {
    private val permits = Semaphore(2)
    private val cache = object : LinkedHashMap<String, MeloXAutoMixTrackAnalysis>(16, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MeloXAutoMixTrackAnalysis>?): Boolean = size > 16
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<MeloXAutoMixTrackAnalysis>>()
    private val store = MeloXAudioAnalysisStore()

    suspend fun analyze(
        identity: String,
        uri: String,
        headers: Map<String, String> = emptyMap(),
        windowStartMs: Long = 0,
        windowDurationMs: Long = Long.MAX_VALUE,
    ): MeloXAutoMixTrackAnalysis = withContext(Dispatchers.IO) {
        require(windowStartMs >= 0 && windowDurationMs > 0)
        // Source and authorization context are hashed by the persistent store.
        // A changed file/quality/URL must not reuse a different recording's beats.
        val local = java.io.File(uri)
        val revision = if (local.isFile) "${local.length()}:${local.lastModified()}" else ""
        val key = "pcm-v3|$identity|$uri|$revision|${headers.toSortedMap()}|$windowStartMs|$windowDurationMs"
        synchronized(cache) { cache[key] }?.let { return@withContext it }
        if (MeloXAudioAnalysisPreferences.persistentEnabled()) store.get(key)?.let { return@withContext it }
        val pending = CompletableDeferred<MeloXAutoMixTrackAnalysis>()
        inFlight.putIfAbsent(key, pending)?.let { return@withContext it.await() }
        try {
            val result = permits.withPermit {
                val accumulator = PcmFeatures(windowStartMs)
                FfmpegPcmDecoder(uri, headers, windowStartMs, windowDurationMs).use { decoder ->
                    // Cancellation closes native pipes even when a network read blocks.
                    val cancellation = coroutineContext.job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { decoder.close() }
                    try {
                        val buffer = ByteArray(16_384)
                        while (true) {
                            ensureActive()
                            val count = decoder.read(buffer)
                            if (count == 0) break
                            accumulator.consume(buffer, count, 44_100, 2)
                        }
                        decoder.verifyExit()
                    } finally { cancellation.dispose() }
                }
                accumulator.finish()
            }
            synchronized(cache) { cache[key] = result }
            if (MeloXAudioAnalysisPreferences.persistentEnabled()) store.put(key, result)
            pending.complete(result)
            result
        } catch (failure: Throwable) { pending.completeExceptionally(failure); throw failure }
        finally { inFlight.remove(key, pending) }
    }

    fun analyzePcm(audioData: ByteArray, sampleRate: Int, channels: Int = 2, timelineOffsetMs: Long = 0): MeloXAutoMixTrackAnalysis {
        require(sampleRate > 0 && channels > 0 && audioData.size % (2 * channels) == 0)
        return PcmFeatures(timelineOffsetMs).apply { consume(audioData, audioData.size, sampleRate, channels) }.finish()
    }

    /** Compatibility entry consumes signed, little-endian, stereo 16-bit samples. */
    fun analyze(audioData: ByteArray, sampleRate: Int): MeloXAutoMixAnalysis = analyzePcm(audioData, sampleRate).plannerAnalysis()
    fun clear() { synchronized(cache) { cache.clear() }; store.clear() }

    private class PcmFeatures(private val offset: Long) {
        private val window = FloatArray(FFT_SIZE)
        private var count = 0
        private var phase = 0
        private var samples = 0L
        private var previousEnergy = 0f
        private val real = FloatArray(FFT_SIZE)
        private val imaginary = FloatArray(FFT_SIZE)
        private val previousSpectrum = FloatArray(FFT_SIZE / 2)
        private val frames = ArrayList<MeloXAutoMixFrame>()

        fun consume(bytes: ByteArray, size: Int, rate: Int, channels: Int) {
            require(size % (2 * channels) == 0)
            val input = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.LITTLE_ENDIAN)
            while (input.remaining() >= channels * 2) {
                var mono = 0f
                repeat(channels) { mono += input.short / 32768f }
                mono /= channels
                phase += TARGET_RATE
                while (phase >= rate) {
                    phase -= rate
                    window[count++] = mono; samples++
                    if (count == FFT_SIZE) {
                        calculate()
                        window.copyInto(window, 0, HOP_SIZE, FFT_SIZE)
                        count = FFT_SIZE - HOP_SIZE
                    }
                }
            }
        }
        private fun calculate() {
            imaginary.fill(0f)
            var squares = 0.0
            for (i in window.indices) {
                squares += window[i] * window[i]
                real[i] = window[i] * (.5f - .5f * cos(2.0 * PI * i / (FFT_SIZE - 1)).toFloat())
            }
            fft(real, imaginary)
            var low = 0.0; var mid = 0.0; var high = 0.0; var novelty = 0.0; var magnitudeSum = 0.0
            for (bin in previousSpectrum.indices) {
                val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
                novelty += max(magnitude - previousSpectrum[bin], 0f)
                previousSpectrum[bin] = magnitude
                magnitudeSum += magnitude
                when (bin * TARGET_RATE / FFT_SIZE) { in 0..249 -> low += magnitude; in 250..2499 -> mid += magnitude; else -> high += magnitude }
            }
            val total = (low + mid + high).coerceAtLeast(1e-8)
            val energy = sqrt(squares / FFT_SIZE).toFloat()
            val flux = (novelty / magnitudeSum.coerceAtLeast(1e-6)).toFloat()
            val onset = max(energy - previousEnergy, 0f) * .62f + flux * .38f
            frames += MeloXAutoMixFrame(offset + (samples - FFT_SIZE).coerceAtLeast(0) * 1000 / TARGET_RATE,
                energy, (low / total).toFloat(), (mid / total).toFloat(), (high / total).toFloat(), flux, onset)
            previousEnergy = energy
        }
        fun finish(): MeloXAutoMixTrackAnalysis {
            require(frames.size >= 32) { "AutoMix: decoded audio is too short" }
            val energyScale = percentile(frames.map { it.energy }).coerceAtLeast(1e-5f)
            val noveltyScale = percentile(frames.map { it.novelty }).coerceAtLeast(1e-5f)
            val onsetScale = percentile(frames.map { it.onset }).coerceAtLeast(1e-5f)
            val normalized = frames.map { it.copy(energy = (it.energy / energyScale).coerceIn(0f, 1.5f),
                novelty = (it.novelty / noveltyScale).coerceIn(0f, 1.5f), onset = (it.onset / onsetScale).coerceIn(0f, 1.5f)) }
            val audible = normalized.filter { it.energy >= .035f }
            // Silence is not a 120 BPM track and must not generate fictitious beats.
            if (audible.isEmpty()) return MeloXAutoMixTrackAnalysis(0.0, 0.0, offset, offset,
                longArrayOf(), longArrayOf(), longArrayOf(), normalized)
            val first = audible.first().timeMs
            val last = audible.last().timeMs + HOP_MS
            val tempo = estimateTempoSignal(normalized.map { (it.onset * .8f + it.novelty * .2f).toDouble() }, TARGET_RATE.toDouble() / HOP_SIZE)
            val beats = if (tempo.first > 0) beatTimeline(normalized, tempo.first) else longArrayOf()
            val phase = (0..3).maxByOrNull { candidate -> beats.indices.filter { it % 4 == candidate }.sumOf { index ->
                val frame = frameAt(normalized, beats[index]); frame.onset.toDouble() + frame.lowRatio * .18
            } } ?: 0
            val downbeats = beats.filterIndexed { index, _ -> index % 4 == phase }.toLongArray()
            val phrases = buildList {
                add(first)
                downbeats.filterIndexed { index, _ -> index % 4 == 0 }.forEach { base ->
                    val best = normalized.filter { abs(it.timeMs - base) <= 350 }.maxByOrNull { it.novelty * .6f + it.onset * .4f }
                    val time = best?.timeMs ?: base
                    if (time in (first + 500)..(last - 500)) add(time)
                }
                add(last)
            }.distinct().sorted().toLongArray()
            return MeloXAutoMixTrackAnalysis(tempo.first, tempo.second, first, last, beats, downbeats, phrases, normalized)
        }
    }
    companion object {
        private const val TARGET_RATE = 11_025
        private const val FFT_SIZE = 1024
        private const val HOP_SIZE = 512
        private const val HOP_MS = HOP_SIZE * 1000L / TARGET_RATE
        private fun percentile(values: List<Float>): Float = values.sorted()[(values.lastIndex * .95f).toInt()]
        private fun frameAt(frames: List<MeloXAutoMixFrame>, time: Long): MeloXAutoMixFrame {
            val index = frames.binarySearchBy(time) { it.timeMs }
            return frames[if (index >= 0) index else (-index - 1).coerceIn(frames.indices)]
        }
        internal fun estimateTempoSignal(signal: List<Double>, frameRate: Double): Pair<Double, Double> {
            if (signal.size < 32) return 0.0 to 0.0
            val mean = signal.average()
            val centered = signal.map { it - mean }
            val minLag = (frameRate * 60 / 190).toInt().coerceAtLeast(2)
            val maxLag = (frameRate * 60 / 65).toInt().coerceAtMost(centered.size / 2)
            if (maxLag < minLag || centered.sumOf { it * it } < 1e-12) return 0.0 to 0.0
            val scores = (minLag..maxLag).map { lag ->
                var score = 0.0; var energy = 0.0; var delayed = 0.0
                for (index in lag until centered.size) {
                    score += centered[index] * centered[index - lag]
                    energy += centered[index] * centered[index]; delayed += centered[index - lag] * centered[index - lag]
                }
                lag to if (sqrt(energy * delayed) > 1e-12) score / sqrt(energy * delayed) else 0.0
            }
            val best = scores.maxBy { it.second }
            var bpm = 60 * frameRate / best.first
            while (bpm < 80) bpm *= 2
            while (bpm > 170) bpm /= 2
            val background = scores.map { it.second }.sorted().let { it[it.size / 2] }
            val peak = best.second.coerceIn(0.0, 1.0)
            val prominence = if (peak > background) ((peak - background) / (1 - background).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0) else 0.0
            return bpm to (peak * .7 + prominence * .3).coerceIn(0.0, 1.0)
        }
        private fun beatTimeline(frames: List<MeloXAutoMixFrame>, bpm: Double): LongArray {
            val period = (TARGET_RATE.toDouble() / HOP_SIZE * 60 / bpm).toInt().coerceAtLeast(2)
            val phase = (0 until period).maxByOrNull { offset -> (offset until frames.size step period).sumOf { frames[it].onset + frames[it].novelty * .3 } } ?: 0
            return buildList {
                var time = frames[phase.coerceIn(frames.indices)].timeMs.toDouble()
                while (time <= frames.last().timeMs + HOP_MS) { add(time.toLong()); time += 60000 / bpm }
            }.toLongArray()
        }
        private fun fft(real: FloatArray, imaginary: FloatArray) {
            var target = 0
            for (index in 1 until real.size) {
                var bit = real.size shr 1
                while (target and bit != 0) { target = target xor bit; bit = bit shr 1 }
                target = target xor bit
                if (index < target) {
                    val r = real[index]; real[index] = real[target]; real[target] = r
                    val i = imaginary[index]; imaginary[index] = imaginary[target]; imaginary[target] = i
                }
            }
            var length = 2
            while (length <= real.size) {
                val angle = -2 * PI / length
                val wr = cos(angle).toFloat(); val wi = sin(angle).toFloat()
                for (start in real.indices step length) {
                    var r = 1f; var i = 0f
                    for (offset in 0 until length / 2) {
                        val even = start + offset; val odd = even + length / 2
                        val or = real[odd] * r - imaginary[odd] * i; val oi = real[odd] * i + imaginary[odd] * r
                        real[odd] = real[even] - or; imaginary[odd] = imaginary[even] - oi
                        real[even] += or; imaginary[even] += oi
                        val next = r * wr - i * wi; i = r * wi + i * wr; r = next
                    }
                }
                length = length shl 1
            }
        }
    }
}
