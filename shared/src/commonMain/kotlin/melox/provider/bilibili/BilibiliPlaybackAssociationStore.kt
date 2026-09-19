package melox.provider.bilibili

import melox.platform.getPreferences
import org.json.JSONObject

data class BilibiliPlaybackAssociation(
    val originalBvid: String,
    val originalCid: Long,
    val replacementBvid: String,
    val replacementCid: Long,
    val title: String,
    val consensusDurationMs: Long,
    val replacementCatalogDurationMs: Long?,
    val algorithmVersion: Int = BilibiliPlaybackAssociationStore.AlgorithmVersion,
)

object BilibiliPlaybackAssociationStore {
    const val AlgorithmVersion = 2
    private const val PreferencesName = "melox_bilibili_playback_associations"
    private const val RevisionKey = "__revision"

    private fun prefs() = getPreferences(PreferencesName)

    fun read(bvid: String, cid: Long): BilibiliPlaybackAssociation? {
        val raw = prefs().getString(key(bvid, cid), null) ?: return null
        return runCatching {
            JSONObject(raw).let { json ->
                if (json.optInt("algorithmVersion", 0) != AlgorithmVersion) return null
                BilibiliPlaybackAssociation(
                    bvid, cid, json.getString("replacementBvid"), json.getLong("replacementCid"),
                    json.optString("title"), json.optLong("consensusDurationMs"),
                    json.optLong("replacementCatalogDurationMs").takeIf { it > 0 }
                        ?: json.optLong("measuredDurationMs").takeIf { it > 0 },
                    json.optInt("algorithmVersion", AlgorithmVersion),
                )
            }
        }.getOrNull()
    }

    fun write(association: BilibiliPlaybackAssociation) {
        writeIfChanged(association)
    }

    fun writeIfChanged(association: BilibiliPlaybackAssociation): Boolean {
        val current = read(association.originalBvid, association.originalCid)
        if (current?.replacementBvid == association.replacementBvid &&
            current.replacementCid == association.replacementCid &&
            current.algorithmVersion == association.algorithmVersion
        ) return false
        val json = JSONObject()
            .put("replacementBvid", association.replacementBvid)
            .put("replacementCid", association.replacementCid)
            .put("title", association.title)
            .put("consensusDurationMs", association.consensusDurationMs)
            .put("replacementCatalogDurationMs", association.replacementCatalogDurationMs)
            .put("algorithmVersion", association.algorithmVersion)
        mutate { putString(key(association.originalBvid, association.originalCid), json.toString()) }
        return true
    }

    fun remove(bvid: String, cid: Long) = mutate { remove(key(bvid, cid)) }
    fun clear() = mutate { clear() }
    fun revision(): Long = prefs().getLong(RevisionKey, 0L)

    private fun mutate(mutation: MeloXMutator.() -> Unit) {
        val preferences = prefs()
        val mutator = MeloXMutator(preferences)
        mutator.mutation()
        preferences.putLong(RevisionKey, preferences.getLong(RevisionKey, 0L) + 1L)
    }

    /** Editor-like helper so migrated Android code keeps its mutation shape. */
    private class MeloXMutator(private val preferences: melox.platform.MeloXPreferences) {
        fun putString(key: String, value: String) { preferences.putString(key, value) }
        fun remove(key: String) { preferences.remove(key) }
        fun clear() { preferences.clear() }
    }

    private fun key(bvid: String, cid: Long) = "$bvid:$cid"
}
