package melox.playback

import melox.platform.logInfo
import melox.platform.logWarn

/**
 * Desktop playback service stub.
 *
 * The Android version is a MediaSessionService that exposes Media3 to
 * MediaController bindings. On desktop the player runs in-process, so this
 * class is a lightweight lifecycle holder that mirrors the Android service
 * API for compatibility with PlaybackCommands and ProviderPlaybackCommands.
 */
class MeloXPlaybackService {
    private var player: MeloXDesktopPlayer? = null

    fun onCreate() {
        logInfo("MeloXPlaybackService", "Desktop playback service created")
    }

    fun onDestroy() {
        player?.release()
        player = null
        logInfo("MeloXPlaybackService", "Desktop playback service destroyed")
    }

    fun getPlayer(): MeloXDesktopPlayer? = player

    fun setPlayer(newPlayer: MeloXDesktopPlayer) {
        player?.release()
        player = newPlayer
    }
}