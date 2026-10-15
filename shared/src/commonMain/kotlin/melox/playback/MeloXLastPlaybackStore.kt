package melox.playback

import melox.model.SearchSong
import melox.platform.getPreferences
import melox.settings.MeloXSettingsPreferences
import org.json.JSONObject

/**
 * Pinned single-song fallback (`playback_remember_last_song`).
 *
 * Android keeps this record separate from the saved queue on purpose: the single
 * song is only restored when queue persistence is off, so turning queue saving off
 * never silently re-enables a full queue, and turning this off never erases the queue.
 */
internal object MeloXLastPlaybackStore {
    private const val PREFS = "melox_last_playback"
    private const val ENTRY = "entry"
    private const val POSITION = "position"
    private const val EnabledKey = "playback_remember_last_song"

    fun save(player: MeloXDesktopPlayer) {
        if (!MeloXSettingsPreferences.boolean(EnabledKey, true)) return
        val entry = player.entries().getOrNull(player.currentMediaItemIndex) ?: return
        getPreferences(PREFS).putValues(mapOf(
            ENTRY to MeloXPlaybackQueueStore.songToJson(entry.song).toString(),
            POSITION to player.currentPosition.coerceAtLeast(0L).toString(),
        ))
    }

    fun savePosition(positionMs: Long) {
        if (!MeloXSettingsPreferences.boolean(EnabledKey, true)) return
        val preferences = getPreferences(PREFS)
        if (preferences.getString(ENTRY, null) == null) return
        preferences.putValues(mapOf(POSITION to positionMs.coerceAtLeast(0L).toString()))
    }

    fun read(): Pair<SearchSong, Long>? {
        val preferences = getPreferences(PREFS)
        val raw = preferences.getString(ENTRY, null) ?: return null
        val song = runCatching { JSONObject(raw) }
            .getOrNull()
            ?.let { MeloXPlaybackQueueStore.songFromJson(it) }
            ?: return null
        return song to preferences.getLong(POSITION, 0L).coerceAtLeast(0L)
    }

    fun clear() {
        getPreferences(PREFS).putValues(mapOf(ENTRY to null, POSITION to null))
    }
}
