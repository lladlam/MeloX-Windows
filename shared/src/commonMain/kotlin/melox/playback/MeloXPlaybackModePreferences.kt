package melox.playback

import melox.platform.getPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MeloXPlaybackModeRuntime {
    var heartModeActive by mutableStateOf(false)
        internal set
    var shuffleEnabled by mutableStateOf(false)
        internal set
    var autoplayEnabled by mutableStateOf(false)
        internal set
    var autoMixEnabled by mutableStateOf(false)
        internal set
}

object MeloXPlaybackModePreferences {
    private const val NAME = "melox_playback_modes"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_AUTOPLAY = "autoplay"
    private const val KEY_AUTOMIX = "auto_mix"

    private fun prefs() = getPreferences(NAME)

    /** Loads the process snapshot once so playback hot paths never poll preferences. */
    fun initialize() {
        val preferences = prefs()
        MeloXPlaybackModeRuntime.shuffleEnabled = preferences.getBoolean(KEY_SHUFFLE, false)
        MeloXPlaybackModeRuntime.autoplayEnabled = preferences.getBoolean(KEY_AUTOPLAY, false)
        MeloXPlaybackModeRuntime.autoMixEnabled = preferences.getBoolean(KEY_AUTOMIX, false)
    }

    fun shuffle(): Boolean = prefs().getBoolean(KEY_SHUFFLE, false)

    fun autoplay(): Boolean = prefs().getBoolean(KEY_AUTOPLAY, false)

    fun autoMix(): Boolean = prefs().getBoolean(KEY_AUTOMIX, false)

    fun setShuffle(enabled: Boolean) {
        MeloXPlaybackModeRuntime.shuffleEnabled = enabled
        prefs().putBoolean(KEY_SHUFFLE, enabled)
    }

    fun setAutoplay(enabled: Boolean) {
        MeloXPlaybackModeRuntime.autoplayEnabled = enabled
        prefs().putBoolean(KEY_AUTOPLAY, enabled)
    }

    fun setAutoMix(enabled: Boolean) {
        MeloXPlaybackModeRuntime.autoMixEnabled = enabled
        prefs().putBoolean(KEY_AUTOMIX, enabled)
    }

    fun setAutoMixString(key: String, value: String) = prefs().putString(key, value)
    fun setAutoMixInt(key: String, value: Int) = prefs().putInt(key, value)
    fun setAutoMixLong(key: String, value: Long) = prefs().putLong(key, value)
    fun setAutoMixBoolean(key: String, value: Boolean) = prefs().putBoolean(key, value)
    fun setAutoMixFloat(key: String, value: Float) = prefs().putFloat(key, value)

    fun reset() {
        MeloXPlaybackModeRuntime.shuffleEnabled = false
        MeloXPlaybackModeRuntime.autoplayEnabled = false
        MeloXPlaybackModeRuntime.autoMixEnabled = false
        prefs().clear()
    }
}