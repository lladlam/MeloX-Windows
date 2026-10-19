package melox.playback

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin

/** Lightweight process bridge from the playback analyser to Compose visuals. */
object MeloXAudioReactiveRuntime {
    @Volatile
    private var mediaId: String? = null

    @Volatile
    private var analysis: MeloXAutoMixTrackAnalysis? = null

    @Volatile
    private var positionMs: Long = 0L

    @Volatile
    private var playing: Boolean = false

    @Volatile
    private var pcmEnergy: Float? = null

    fun select(mediaId: String?) {
        if (this.mediaId == mediaId) return
        this.mediaId = mediaId
        analysis = null
        positionMs = 0L
        pcmEnergy = null
    }

    fun publish(mediaId: String?, positionMs: Long, isPlaying: Boolean) {
        select(mediaId)
        this.positionMs = positionMs.coerceAtLeast(0L)
        playing = isPlaying
        if (!isPlaying) pcmEnergy = null
    }

    fun publishPcm(mediaId: String?, samples: ShortArray, count: Int, volume: Float) {
        select(mediaId)
        if (count <= 0) return
        var sum = 0.0
        val limit = count.coerceAtMost(samples.size)
        for (index in 0 until limit) {
            val sample = samples[index] / 32768.0
            sum += sample * sample
        }
        val rms = kotlin.math.sqrt(sum / limit).toFloat() * volume.coerceIn(0f, 1f)
        pcmEnergy = (rms * 4f).coerceIn(0f, 1f)
        playing = true
    }

    fun publishPosition(mediaId: String?, positionMs: Long) {
        if (this.mediaId != mediaId) return
        this.positionMs = positionMs.coerceAtLeast(0L)
    }

    fun attach(mediaId: String, analysis: MeloXAutoMixTrackAnalysis) {
        if (this.mediaId == mediaId) this.analysis = analysis
    }

    fun clear() {
        mediaId = null
        analysis = null
        positionMs = 0L
        playing = false
        pcmEnergy = null
    }

    fun sample(expectedMediaId: String?): MeloXAudioReactiveSample {
        if (expectedMediaId == null || expectedMediaId != mediaId) return MeloXAudioReactiveSample.Idle
        val localAnalysis = analysis ?: run {
            val energy = pcmEnergy
            if (!playing || energy == null) return MeloXAudioReactiveSample.Idle
            return MeloXAudioReactiveSample(
                energy = energy,
                beat = energy,
                downbeat = energy * 0.5f,
                isPlaying = true,
            )
        }
        val time = positionMs
        val frame = localAnalysis.frameAt(time)
        val beat = pulseAt(localAnalysis.beatTimesMs, time, 230L)
        val downbeat = pulseAt(localAnalysis.downbeatTimesMs, time, 360L)
        val frameEnergy = frame?.energy ?: 0.18f
        val energy = pcmEnergy?.let { ((frameEnergy + it) * 0.5f) } ?: frameEnergy
        return MeloXAudioReactiveSample(
            energy = energy.coerceIn(0f, 1.25f),
            beat = beat,
            downbeat = downbeat,
            isPlaying = playing,
            hasAnalysis = true,
        )
    }

    internal fun pulseAt(events: LongArray, positionMs: Long, widthMs: Long): Float {
        if (events.isEmpty() || widthMs <= 0L) return 0f
        val insertion = events.binarySearch(positionMs).let { if (it >= 0) it else -it - 1 }
        var distance = Long.MAX_VALUE
        if (insertion in events.indices) distance = abs(events[insertion] - positionMs)
        if (insertion - 1 in events.indices) distance = minOf(distance, abs(events[insertion - 1] - positionMs))
        return (1f - distance.toFloat() / max(widthMs, 1L).toFloat()).coerceIn(0f, 1f)
    }
}

data class MeloXAudioReactiveSample(
    val energy: Float,
    val beat: Float,
    val downbeat: Float,
    val isPlaying: Boolean,
    val hasAnalysis: Boolean = false,
) {
    companion object {
        val Idle = MeloXAudioReactiveSample(0.18f, 0f, 0f, false)
    }
}

fun miniBarFractions(isPlaying: Boolean, sample: MeloXAudioReactiveSample): List<Float> {
    val energy = if (isPlaying && sample.isPlaying) sample.energy.coerceIn(0.08f, 1f) else 0.10f
    val beat = if (isPlaying && sample.isPlaying) sample.beat else 0f
    val downbeat = if (isPlaying && sample.isPlaying) sample.downbeat else 0f
    return listOf(
        energy * (0.58f + downbeat * 0.30f),
        energy * (0.86f + beat * 0.22f),
        energy * (0.46f + downbeat * 0.42f),
        energy * (0.72f + beat * 0.28f),
    )
}
