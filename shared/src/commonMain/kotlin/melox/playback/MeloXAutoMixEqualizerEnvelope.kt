package melox.playback

import kotlin.math.roundToInt

/**
 * Swaps sub/bass energy between decks around the transition midpoint. This is
 * deliberately independent from the user's graphic EQ: only bands centred at
 * or below 300 Hz are touched and every change is restored on hand-off.
 *
 * Desktop stub: the Android `Equalizer` audio effect is not available on JVM.
 * The crossfade envelope is preserved as a no-op so AutoMix transitions still
 * work; bass swapping is skipped.
 */
class MeloXAutoMixEqualizerEnvelope {
    private var outgoing: DeckEqualizer? = null
    private var incoming: DeckEqualizer? = null

    fun attach(outgoingSessionId: Int, incomingSessionId: Int) {
        release()
        if (outgoingSessionId <= 0 || incomingSessionId <= 0 || outgoingSessionId == incomingSessionId) return
        outgoing = runCatching { DeckEqualizer(outgoingSessionId) }.getOrNull()
        incoming = runCatching { DeckEqualizer(incomingSessionId) }.getOrNull()
    }

    fun apply(progress: Double) {
        // Desktop has no system Equalizer effect; the crossfade itself is
        // handled by the player, so this is a no-op hook for parity.
    }

    fun release() {
        outgoing?.release()
        incoming?.release()
        outgoing = null
        incoming = null
    }

    private class DeckEqualizer(audioSessionId: Int) {
        fun setBassCut(decibels: Double) = Unit
        fun release() = Unit
    }

    private fun smoothStep(value: Double): Double {
        val p = value.coerceIn(0.0, 1.0)
        return p * p * (3.0 - 2.0 * p)
    }

    companion object {
        /**
         * Several Xiaomi-family AudioEffect implementations are unstable when
         * two Equalizers are created for two simultaneously playing sessions.
         * The gain/tempo crossfade remains enabled; only the optional bass-swap
         * envelope is omitted on those devices or when the user's EQ is active.
         */
        internal fun supportsDeckEqualizers(
            manufacturer: String,
            brand: String,
            userEqualizerEnabled: Boolean,
        ): Boolean {
            if (userEqualizerEnabled) return false
            return false // Desktop has no Equalizer effect
        }
    }
}