package melox.playback

import melox.account.NeteaseSessionStore
import melox.music.model.MusicSource
import melox.music.provider.MeloXMusicProviders
import melox.music.provider.PlaybackAccountStore
import melox.music.provider.MusicProviderRegistry
import melox.provider.applemusic.AppleMusicSessionStore
import melox.provider.kugou.KugouSessionStore
import melox.provider.qqmusic.QQMusicSessionStore
import melox.provider.bilibili.BilibiliSessionStore
import melox.provider.spotify.SpotifySessionStore
import java.security.MessageDigest

/**
 * Process-local bridge used by the desktop playback resolver. No
 * credentials are embedded into track URIs.
 */
object ProviderPlaybackRuntime {
    @Volatile
    private var currentRegistry: MusicProviderRegistry? = null

    fun initialize() {
        if (currentRegistry != null) return
        synchronized(this) {
            if (currentRegistry != null) return
            currentRegistry = MeloXMusicProviders.createPlayback()
        }
    }

    fun registryOrNull(): MusicProviderRegistry? = currentRegistry

    fun authKey(source: MusicSource): String {
        val credential = when (source) {
            MusicSource.Netease -> PlaybackAccountStore.neteaseCookie()
            MusicSource.QQMusic -> PlaybackAccountStore.qqSession().cookie
            MusicSource.Kugou -> PlaybackAccountStore.kugouSession().let { session ->
                listOf(session.userId, session.token, session.vipToken, session.dfid).joinToString("|")
            }
            MusicSource.AppleMusic -> AppleMusicSessionStore.read().let { session ->
                listOf(session.storefront).joinToString("|")
            }
            MusicSource.Bilibili -> BilibiliSessionStore.read().cookie
            MusicSource.Spotify -> SpotifySessionStore.read().let { session ->
                listOf(session.accountId).joinToString("|")
            }
            MusicSource.Kuwo -> ""
            MusicSource.YouTubeMusic -> ""
            MusicSource.Jellyfin -> melox.provider.jellyfin.JellyfinSessionStore.read().let { session ->
                listOf(session.userName).joinToString("|")
            }
            MusicSource.Local -> "local"
        }
        return MessageDigest.getInstance("SHA-256").digest(credential.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}