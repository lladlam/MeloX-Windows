package melox.ui.navigation

/** One-shot provider selection when opening Route.Login from a Desktop row. */
object MeloXLoginRequestBus {
    var pendingProviderId: String? = null

    fun request(providerId: String) {
        pendingProviderId = providerId
    }

    fun request(source: melox.music.model.MusicSource) = request(when (source) {
        melox.music.model.MusicSource.QQMusic -> "qq"
        melox.music.model.MusicSource.YouTubeMusic -> "youtubemusic"
        melox.music.model.MusicSource.AppleMusic -> "applemusic"
        else -> source.storageValue
    })

    fun consume(): String? = pendingProviderId.also { pendingProviderId = null }
}
