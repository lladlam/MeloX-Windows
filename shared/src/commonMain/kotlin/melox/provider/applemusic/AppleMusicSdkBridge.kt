package melox.provider.applemusic

/**
 * Desktop Apple Music SDK bridge stub.
 *
 * Android reflects into the MusicKit auth/playback SDKs when they are bundled.
 * Desktop has no MusicKit SDK; Apple Music stays catalogue-config-only until an
 * official desktop integration exists.
 */
object AppleMusicSdkBridge {
    fun isAuthenticationSdkAvailable(): Boolean = false
    fun isPlaybackSdkAvailable(): Boolean = false
    fun extractMusicUserToken(data: Any?): String? = null
    fun playCatalogQueue(
        catalogIds: List<String>,
        startIndex: Int,
        musicUserToken: String,
    ): Boolean = false
    fun releasePlayback() = Unit
}
