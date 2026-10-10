package melox.playback

/** Source bass exchange envelope, applied by the PCM mixer's actual per-channel RBJ shelves. */
class MeloXAutoMixEqualizerEnvelope {
    var outgoingCutDb = 0.0
        private set
    var incomingCutDb = 0.0
        private set
    fun apply(progress: Double) {
        val p = progress.coerceIn(0.0, 1.0)
        outgoingCutDb = -12 * smoothStep((p - .24) / .38)
        incomingCutDb = -12 * (1 - smoothStep((p - .38) / .38))
    }
    fun release() { outgoingCutDb = 0.0; incomingCutDb = 0.0 }
    private fun smoothStep(value: Double) = value.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
}
