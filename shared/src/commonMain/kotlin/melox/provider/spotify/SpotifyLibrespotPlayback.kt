package melox.provider.spotify

import melox.music.model.AudioQualityTier

/**
 * Desktop Spotify playback stub.
 *
 * Android drives librespot-android for DRM-protected streams. The desktop port
 * defers real Spotify playback until a JVM librespot binding is integrated;
 * preview URLs (30s) remain playable through the plain HTTP path.
 */
object SpotifyLibrespotPlayback {
    fun available(): Boolean = false

    fun resolveStream(
        session: SpotifySession,
        trackId: String,
        quality: AudioQualityTier,
    ): String? = null
}
