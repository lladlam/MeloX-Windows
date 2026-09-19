package melox.provider.bilibili

import melox.platform.getPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableIntStateOf
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object BilibiliLyricOffsetStore {
    const val MinOffsetMs = -5_000
    const val MaxOffsetMs = 5_000
    private const val PreferencesName = "melox_bilibili_lyric_offsets"
    private val states = ConcurrentHashMap<String, androidx.compose.runtime.MutableIntState>()

    fun normalizeOffset(offsetMs: Int): Int = offsetMs.coerceIn(MinOffsetMs, MaxOffsetMs)

    fun preferenceKey(resourceValue: String): String = "track_" + sha256(resourceValue)

    fun read(resourceValue: String): Int = normalizeOffset(
        getPreferences(PreferencesName).getInt(preferenceKey(resourceValue), 0),
    )

    fun state(resourceValue: String): State<Int> {
        val key = preferenceKey(resourceValue)
        return states.getOrPut(key) { mutableIntStateOf(read(resourceValue)) }
    }

    fun write(resourceValue: String, offsetMs: Int) {
        val normalized = normalizeOffset(offsetMs)
        val key = preferenceKey(resourceValue)
        val prefs = getPreferences(PreferencesName)
        if (normalized == 0) prefs.remove(key) else prefs.putInt(key, normalized)
        states.getOrPut(key) { mutableIntStateOf(normalized) }.intValue = normalized
    }

    fun effectiveAdvance(globalAdvanceMs: Int, trackOffsetMs: Int): Long =
        globalAdvanceMs.toLong() + normalizeOffset(trackOffsetMs)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
