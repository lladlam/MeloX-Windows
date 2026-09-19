package melox.recognition

import melox.model.SearchSong

/**
 * Desktop song recognition stub.
 *
 * Android captures microphone audio through AudioRecord and WebView AMLL
 * fingerprinting. Desktop recognition is metadata-based (see
 * LocalRecognitionCoordinator); this stub keeps the API surface for the UI.
 */
class SongRecognitionClient : AutoCloseable {
    data class SongRecognitionResult(
        val song: SearchSong,
        val score: Float,
    )

    suspend fun recognize(durationSeconds: Int): List<SongRecognitionResult> = emptyList()

    suspend fun recognizeLocal(uri: String, durationMs: Long, segmentStartMs: Long? = null): List<SongRecognitionResult> = emptyList()

    override fun close() = Unit
}
