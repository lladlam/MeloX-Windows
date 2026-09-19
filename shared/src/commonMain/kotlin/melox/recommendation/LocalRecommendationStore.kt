package melox.recommendation

import melox.platform.getPreferences
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.MusicResourceId
import melox.music.model.MusicArtistRef
import melox.music.model.TrackAvailability
import androidx.compose.runtime.mutableStateOf
import org.json.JSONArray
import org.json.JSONObject

object LocalRecommendationStore {
    private const val PREFS = "local_recommendation"
    private const val TRACKS = "track_features"
    private const val PREFERENCE_VECTOR = "preference_vector"
    private const val PREFERENCE_UPDATED = "preference_updated_at"
    val progress = mutableStateOf(LocalAnalysisProgress())

    private fun prefs() = getPreferences(PREFS)

    fun isAlgorithmEnabled(): Boolean = prefs().getBoolean("algorithm_enabled", false)
    fun setAlgorithmEnabled(value: Boolean) = prefs().putBoolean("algorithm_enabled", value)
    fun hasPersonalizationConsent(): Boolean = prefs().getBoolean("personalization_consent", false)
    fun setPersonalizationConsent(value: Boolean) = prefs().putBoolean("personalization_consent", value)
    fun consentAt(): Long = prefs().getLong("personalization_consent_at", 0L)
    fun setConsentAt(value: Long) = prefs().putLong("personalization_consent_at", value)

    fun readFeatures(): List<LocalTrackFeatures> = runCatching {
        val array = JSONArray(prefs().getString(TRACKS, "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(LocalTrackFeatures(item.getString("trackKey"), item.getString("title"), item.getString("artist"), item.getInt("playCount"), item.getInt("completedCount"), item.getInt("skipCount"), item.getBoolean("liked"), item.getLong("lastPlayedAtMs"), item.optInt("sourceCount", 1), item.getString("versionKind").ifBlank { "Studio" }, item.optDouble("qualityPreference", 0.0).toFloat()))
            }
        }
    }.getOrDefault(emptyList())

    fun writeFeatures(features: List<LocalTrackFeatures>) {
        val array = JSONArray()
        features.forEach { item ->
            array.put(JSONObject().apply {
                put("trackKey", item.trackKey); put("title", item.title); put("artist", item.artist)
                put("playCount", item.playCount); put("completedCount", item.completedCount); put("skipCount", item.skipCount)
                put("liked", item.liked); put("lastPlayedAtMs", item.lastPlayedAtMs); put("sourceCount", item.sourceCount)
                put("versionKind", item.versionKind); put("qualityPreference", item.qualityPreference)
            })
        }
        prefs().putString(TRACKS, array.toString())
    }

    fun recordPlayback(trackKey: String, title: String, artist: String, completed: Boolean = false, skipped: Boolean = false) {
        val current = readFeatures().toMutableList()
        val index = current.indexOfFirst { it.trackKey == trackKey }
        val old = current.getOrNull(index) ?: LocalTrackFeatures(trackKey, title, artist)
        val updated = old.copy(
            playCount = old.playCount + if (!completed && !skipped) 1 else 0,
            completedCount = old.completedCount + if (completed) 1 else 0,
            skipCount = old.skipCount + if (skipped) 1 else 0,
            lastPlayedAtMs = System.currentTimeMillis(),
        )
        if (index >= 0) current[index] = updated else current += updated
        writeFeatures(current.takeLast(2_000))
        updatePreference(
            title = title,
            artist = artist,
            reward = when {
                skipped -> -1.5f
                completed -> 1.25f
                else -> .25f
            },
        )
    }

    fun writeRecommendations(items: List<LocalRecommendationItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject().apply { put("trackKey", item.trackKey); put("title", item.title); put("artist", item.artist); put("score", item.score); put("ruleScore", item.ruleScore); put("modelScore", item.modelScore); put("reason", item.reason) })
        }
        prefs().putString("recommendations", array.toString())
    }

    fun readRecommendations(): List<LocalRecommendationItem> = runCatching {
        val array = JSONArray(prefs().getString("recommendations", "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(LocalRecommendationItem(item.getString("trackKey"), item.getString("title"), item.getString("artist"), item.getDouble("score").toFloat(), item.getDouble("ruleScore").toFloat(), item.getDouble("modelScore").toFloat(), item.getString("reason")))
            }
        }
    }.getOrDefault(emptyList())

    fun writeCandidateTracks(tracks: List<MusicTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(JSONObject().apply {
                put("source", track.id.source.storageValue); put("id", track.id.value); put("title", track.title)
                put("artist", track.artistText); put("artwork", track.artworkUrl); put("durationMs", track.durationMs ?: 0L)
            })
        }
        prefs().putString("candidate_tracks", array.toString())
    }

    fun readCandidateTracks(): List<MusicTrack> = runCatching {
        val array = JSONArray(prefs().getString("candidate_tracks", "[]").orEmpty())
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val source = MusicSource.fromStorageValue(item.getString("source"))
                add(MusicTrack(
                    id = MusicResourceId(source, item.getString("id")),
                    title = item.getString("title"),
                    artists = listOf(MusicArtistRef(name = item.getString("artist"))),
                    artworkUrl = item.getString("artwork").takeIf(String::isNotBlank),
                    durationMs = item.getLong("durationMs").takeIf { it > 0L },
                    availability = TrackAvailability.Playable,
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun readRecommendedTracks(): List<MusicTrack> {
        val candidates = readCandidateTracks()
        val recommendations = readRecommendations()
        return recommendations.mapNotNull { recommendation ->
            candidates.firstOrNull { track ->
                track.title == recommendation.title && track.artistText == recommendation.artist
            }
        }
    }

    fun clearPersonalization() {
        prefs().apply {
            remove(TRACKS); remove(PREFERENCE_VECTOR); remove(PREFERENCE_UPDATED); remove("model_weights")
            remove("recommendations"); remove("candidate_tracks"); remove("model_metadata")
            remove("personalization_consent"); remove("personalization_consent_at")
        }
        progress.value = LocalAnalysisProgress()
    }

    fun preferenceVector(): FloatArray = runCatching {
        val array = JSONArray(prefs().getString(PREFERENCE_VECTOR, "[]").orEmpty())
        FloatArray(64) { index -> array.optDouble(index, 0.0).toFloat() }
    }.getOrDefault(FloatArray(64))

    fun updatePreference(title: String, artist: String, reward: Float) {
        val vector = preferenceVector()
        val now = System.currentTimeMillis()
        val previous = prefs().getLong(PREFERENCE_UPDATED, 0L)
        val days = if (previous <= 0L) 0.0 else ((now - previous).coerceAtLeast(0L) / 86_400_000.0)
        val decay = kotlin.math.exp(kotlin.math.ln(.98) * days).toFloat().coerceIn(.25f, 1f)
        val sample = featureVector(title, artist)
        vector.indices.forEach { index ->
            vector[index] = ((vector[index] * decay) + .08f * reward * sample[index]).coerceIn(-1f, 1f)
        }
        val array = JSONArray()
        vector.forEach(array::put)
        prefs().apply {
            putString(PREFERENCE_VECTOR, array.toString())
            putLong(PREFERENCE_UPDATED, now)
        }
    }

    fun featureVector(title: String, artist: String): FloatArray {
        val vector = FloatArray(64)
        (title.lowercase() + " " + artist.lowercase())
            .windowed(size = 2, step = 1, partialWindows = true)
            .forEach { token -> vector[(token.hashCode() and Int.MAX_VALUE) % vector.size] += 1f }
        val norm = kotlin.math.sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) vector.indices.forEach { vector[it] /= norm }
        return vector
    }

    fun writeModelMetadata(metadata: LocalModelMetadata) {
        prefs().putString("model_metadata", JSONObject().apply {
            put("version", metadata.version); put("sampleCount", metadata.sampleCount); put("trackCount", metadata.trackCount)
            put("trainedAtMs", metadata.trainedAtMs); put("backend", metadata.backend)
        }.toString())
    }

    fun readModelMetadata(): LocalModelMetadata = runCatching {
        val value = JSONObject(prefs().getString("model_metadata", "{}").orEmpty())
        LocalModelMetadata(value.optInt("version", 2), value.optInt("sampleCount"), value.optInt("trackCount"), value.optLong("trainedAtMs"), value.optString("backend", "本地线性模型"))
    }.getOrDefault(LocalModelMetadata())
}