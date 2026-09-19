package melox.provider.spotify

import melox.music.model.MusicTrack

data class SpotifyMatchScore(
    val candidate: MusicTrack,
    val score: Double,
)

object SpotifyTrackMatcher {
    fun rank(source: MusicTrack, candidates: List<MusicTrack>): List<SpotifyMatchScore> =
        candidates.map { SpotifyMatchScore(it, 0.0) }
    fun score(source: MusicTrack, candidate: MusicTrack): SpotifyMatchScore? =
        SpotifyMatchScore(candidate, 0.0)
}