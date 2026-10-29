package melox.autosync

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** One native CPU session at a time. PCM and scores stay on the local device. */
internal class OnnxAlignmentEngine private constructor(
    val manifest: AutoSyncModelManifest,
    private val environment: OrtEnvironment,
    private val session: OrtSession,
) : AutoCloseable {
    private var buffer: FloatBuffer = allocate(1)

    @Synchronized fun ctc(pcm: FloatArray, targets: List<Int>): CtcForcedAligner.Alignment {
        require(manifest.tokenCountName == null && pcm.isNotEmpty())
        if (targets.isEmpty()) return CtcForcedAligner.align(emptyArray(), emptyList(), manifest.blankId)
        audio(pcm).use { tensor -> session.run(mapOf(manifest.inputName to tensor)).use { result ->
            val logProb = result.get("log_probs").orElse(null) as? OnnxTensor
            val logits = result.get("logits").orElse(null) as? OnnxTensor
            val output = logProb ?: logits ?: error("ONNX model exposes no CTC scores")
            val shape = output.info.shape
            require(shape.size == 3 && shape[0] == 1L && shape[1] in 1..20_000 && shape[2] in 1..100_000)
            val frames = shape[1].toInt()
            val symbols = shape[2].toInt()
            require(frames.toLong() * symbols <= 20_000_000L)
            val flat = FloatArray(frames * symbols)
            output.floatBuffer.get(flat)
            val rows = Array(frames) { frame ->
                val start = frame * symbols
                if (logProb != null) DoubleArray(symbols) { flat[start + it].toDouble() }
                else CtcForcedAligner.logSoftmax(flat.copyOfRange(start, start + symbols))
            }
            return CtcForcedAligner.align(rows, targets, manifest.blankId)
        } }
    }

    @Synchronized fun chinese(pcm: FloatArray, tokenCount: Int): List<ZhCifPostprocess.TokenSpan> {
        require(pcm.isNotEmpty() && tokenCount in 1..4_096)
        val countName = requireNotNull(manifest.tokenCountName) { "Chinese model requires token count" }
        audio(pcm).use { samples ->
            OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(tokenCount.toLong() + 1)), longArrayOf(1)).use { count ->
                session.run(mapOf(manifest.inputName to samples, countName to count)).use { result ->
                    fun vector(name: String): FloatArray {
                        val value = result.get(name).orElse(null) as? OnnxTensor ?: error("Missing $name")
                        val size = value.info.shape.fold(1L) { total, dimension -> Math.multiplyExact(total, dimension) }
                        require(size in 1..1_000_000)
                        return FloatArray(size.toInt()).also { value.floatBuffer.get(it) }
                    }
                    return ZhCifPostprocess.tokens(vector("us_alphas"), vector("us_peaks"), tokenCount)
                }
            }
        }
    }

    private fun audio(samples: FloatArray): OnnxTensor {
        require(samples.size <= 16_000 * 15) { "AutoSync input window too long" }
        if (buffer.capacity() < samples.size) buffer = allocate(samples.size)
        buffer.clear(); buffer.put(samples); buffer.rewind(); buffer.limit(samples.size)
        return OnnxTensor.createTensor(environment, buffer, longArrayOf(1, samples.size.toLong()))
    }

    override fun close() = session.close()

    companion object {
        private fun allocate(count: Int): FloatBuffer = ByteBuffer.allocateDirect(count * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        suspend fun open(model: AutoSyncModelRepository.InstalledModel): OnnxAlignmentEngine {
            require(model.verify()) { "AutoSync model integrity check failed" }
            val manifest = AutoSyncModelManifest.read(model)
            val environment = OrtEnvironment.getEnvironment()
            val session = OrtSession.SessionOptions().use { options ->
                options.setIntraOpNumThreads(2)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                environment.createSession(model.model.absolutePath, options)
            }
            return OnnxAlignmentEngine(manifest, environment, session)
        }
    }
}
