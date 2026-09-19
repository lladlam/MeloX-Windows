package melox.recognition

import java.io.File

/**
 * Desktop audio segment extraction stub.
 *
 * Android decodes via MediaExtractor/MediaCodec. Desktop keeps the same
 * result contract; extraction lands with the real audio backend.
 */
internal object LocalAudioSegmentExtractor {
    suspend fun extract(
        uri: String,
        segmentStartMs: Long = 0L,
        segmentLengthMs: Long = 9_000L,
        onProgress: (Float) -> Unit = {},
    ): FloatArray = FloatArray(0)
}
