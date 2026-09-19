package melox.playback

import melox.platform.logWarn
import java.io.IOException

/**
 * Desktop stub for the Android MediaExtractor/MediaCodec-based audio analysis.
 *
 * The desktop player uses a simple waveform analysis instead of the full
 * Android audio pipeline. AutoMix analysis is preserved as a no-op so the
 * transition planner still works with default analysis results.
 */
class MeloXAutoMixAudioAnalyzer {
    fun analyze(audioSessionId: Int): MeloXAutoMixAnalysis? {
        logWarn("MeloXAutoMix", "Audio analysis unavailable on desktop: no MediaExtractor/MediaCodec")
        return null
    }

    fun analyze(audioData: ByteArray, sampleRate: Int): MeloXAutoMixAnalysis? {
        // Simple energy-based analysis as a fallback
        if (audioData.isEmpty()) return null
        val energy = audioData.map { (it.toInt() and 0xFF) * (it.toInt() and 0xFF) }.average()
        return MeloXAutoMixAnalysis(
            bpm = 120.0,
            confidence = 0.0,
            firstAudibleMs = 0L,
            lastAudibleMs = null,
        )
    }
}