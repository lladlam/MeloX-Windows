package melox.music.provider

import melox.music.model.AudioQualityTier
import melox.music.model.MusicSource
import melox.music.model.MusicTrack
import melox.music.model.TrackAvailability
import melox.music.model.audioQualityForBitrate
import melox.music.model.rank
import kotlin.math.abs

enum class TrackVersionKind { Studio, Live, Remix, Dj, Instrumental, Cover, Acoustic, Unknown }

data class UnifiedTrackKey(
    val title: String,
    val artist: String,
    val durationBucket: Long?,
    val version: TrackVersionKind,
)

data class AggregatedTrackCandidate(
    val track: MusicTrack,
    val identityScore: Int,
    val versionScore: Int,
    val qualityScore: Int,
    val availabilityScore: Int,
    val totalScore: Int,
    val reason: String,
)

data class AggregatedTrack(
    val key: UnifiedTrackKey,
    val candidates: List<AggregatedTrackCandidate>,
) {
    val recommendation: AggregatedTrackCandidate? get() = candidates.maxWithOrNull(
        compareBy<AggregatedTrackCandidate> { availabilityPriority(it.track.availability) }.thenBy { it.totalScore })
}

private fun availabilityPriority(availability: TrackAvailability): Int = when (availability) {
    TrackAvailability.Playable -> 5
    TrackAvailability.Unknown -> 4
    TrackAvailability.PreviewOnly -> 3
    TrackAvailability.LoginRequired, TrackAvailability.SubscriptionRequired -> 2
    TrackAvailability.RegionRestricted, TrackAvailability.CopyrightRestricted, TrackAvailability.Unavailable -> 1
}

object TrackAggregation {
    private val punctuation = Regex("[\\p{Punct}\\s]+")
    private val artistSeparators = Regex("[/,、，&｜\\s]+")
    private const val DurationToleranceMs = 10_000L
    private val versionTokens = listOf(
        TrackVersionKind.Live to listOf("live", "现场", "演唱会", "巡演"),
        TrackVersionKind.Remix to listOf("remix", "混音", "重混"),
        TrackVersionKind.Dj to listOf("dj", "club", "慢摇"),
        TrackVersionKind.Instrumental to listOf("instrumental", "伴奏", "纯音乐", "karaoke"),
        TrackVersionKind.Cover to listOf("cover", "翻唱"),
        TrackVersionKind.Acoustic to listOf("acoustic", "不插电", "木吉他"),
    )

    fun normalize(value: String): String = value.lowercase().replace(punctuation, "")

    fun normalizeArtist(value: String): String = value.lowercase()
        .split(artistSeparators).map { it.replace(punctuation, "") }
        .filter { it.isNotBlank() && it !in setOf("feat", "ft", "featuring", "with") }
        .distinct().sorted().joinToString("")

    fun versionOf(track: MusicTrack): TrackVersionKind {
        val value = normalize(listOf(track.title, track.album?.name.orEmpty()).joinToString(" "))
        return versionTokens.firstOrNull { (_, tokens) -> tokens.any { value.contains(normalize(it)) } }?.first
            ?: TrackVersionKind.Studio
    }

    fun keyOf(track: MusicTrack): UnifiedTrackKey = UnifiedTrackKey(
        title = normalize(track.title),
        artist = normalizeArtist(track.artistText),
        durationBucket = null,
        version = versionOf(track),
    )

    fun aggregate(tracks: List<MusicTrack>): List<AggregatedTrack> = tracks
        .groupBy(::keyOf)
        .flatMap { (baseKey, grouped) ->
            clusterByDuration(grouped).map { cluster ->
                val duration = cluster.mapNotNull { it.durationMs?.takeIf { ms -> ms > 0L } }
                    .takeIf { it.isNotEmpty() }?.average()?.toLong()
                val key = baseKey.copy(durationBucket = duration)
                AggregatedTrack(key, cluster.map { score(it, key) }.sortedByDescending { it.totalScore })
            }
        }
        .sortedByDescending { it.recommendation?.totalScore ?: 0 }

    private fun clusterByDuration(tracks: List<MusicTrack>): List<List<MusicTrack>> {
        val clusters = mutableListOf<MutableList<MusicTrack>>()
        tracks.filter { (it.durationMs ?: 0L) > 0L }.sortedBy { it.durationMs }.forEach { track ->
            val duration = track.durationMs ?: return@forEach
            val cluster = clusters.firstOrNull { group ->
                abs(group.mapNotNull { it.durationMs?.takeIf { ms -> ms > 0L } }.average() - duration) <= DurationToleranceMs
            }
            if (cluster == null) clusters.add(mutableListOf(track)) else cluster.add(track)
        }
        val unknown = tracks.filter { (it.durationMs ?: 0L) <= 0L }
        if (unknown.isNotEmpty()) {
            if (clusters.isEmpty()) clusters.add(mutableListOf())
            clusters.first().addAll(unknown)
        }
        return clusters
    }

    private fun score(track: MusicTrack, key: UnifiedTrackKey): AggregatedTrackCandidate {
        val identity = 30
        val version = if (versionOf(track) == key.version) 25 else 0
        val tier = track.bestQuality ?: audioQualityForBitrate(track.bestBitrate)
        val bitrate = track.bestBitrate?.takeIf { it > 0 } ?: when (tier) {
            AudioQualityTier.Standard -> 128_000
            AudioQualityTier.High -> 320_000
            AudioQualityTier.Lossless -> 1_000_000
            else -> 1_411_200
        }
        val quality = tier?.let { it.rank() * 10_000 + (bitrate / 1000).coerceIn(0, 2000) } ?: 0
        val availability = when (track.availability) {
            TrackAvailability.Playable -> 20
            TrackAvailability.Unknown -> 10
            TrackAvailability.PreviewOnly -> 6
            TrackAvailability.LoginRequired, TrackAvailability.SubscriptionRequired -> 2
            else -> 0
        }
        val sourceBonus = when (track.id.source) {
            MusicSource.QQMusic -> 2
            MusicSource.Netease -> 1
            MusicSource.Kugou -> 1
            MusicSource.Qishui -> 0
            MusicSource.Kuwo -> 1
            MusicSource.AppleMusic -> 3
            MusicSource.Bilibili -> 0
            // Spotify is playable only after a strict cross-provider match. A
            // native playable result must remain the aggregation recommendation.
            MusicSource.Spotify -> -1
            MusicSource.YouTubeMusic -> 0
            MusicSource.Jellyfin -> 0
            MusicSource.Local -> 0
        }
        return AggregatedTrackCandidate(track, identity, version, quality, availability,
            identity + version + quality + availability + sourceBonus,
            "版本 ${key.version} · ${track.id.source.displayName} · ${tier?.name ?: "音质未知"}")
    }

    fun similarity(left: MusicTrack, right: MusicTrack): Float {
        if (versionOf(left) != versionOf(right)) return 0f
        var score = 0f
        if (normalize(left.title) == normalize(right.title)) score += .45f
        if (normalizeArtist(left.artistText) == normalizeArtist(right.artistText)) score += .35f
        val durationDelta = abs((left.durationMs ?: 0L) - (right.durationMs ?: 0L))
        if (durationDelta <= 2_000L) score += .20f
        return score.coerceIn(0f, 1f)
    }
}
