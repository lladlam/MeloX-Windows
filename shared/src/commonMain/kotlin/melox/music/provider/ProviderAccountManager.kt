package melox.music.provider

import melox.account.NeteaseSessionStore
import melox.music.model.MusicSource
import melox.provider.applemusic.AppleMusicSessionStore
import melox.provider.bilibili.BilibiliPlaybackAssociationStore
import melox.provider.bilibili.BilibiliSessionStore
import melox.provider.jellyfin.JellyfinSessionStore
import melox.provider.kugou.KugouSessionStore
import melox.provider.kuwo.KuwoSessionStore
import melox.provider.qqmusic.QQMusicSessionStore
import melox.provider.spotify.SpotifySessionStore
import melox.provider.youtubemusic.YouTubeSessionStore

class ProviderAccountManager(
    private val neteaseSessionStore: NeteaseSessionStore? = null,
) {
    data class AccountState(
        val source: MusicSource,
        val loggedIn: Boolean,
        val accountId: String? = null,
    )

    fun state(source: MusicSource): AccountState = when (source) {
        MusicSource.Netease -> {
            val cookie = neteaseSessionStore?.cookie ?: NeteaseSessionStore.readCookie()
            AccountState(source, cookie.isNotBlank(), neteaseSessionStore?.profile?.userId?.toString())
        }
        MusicSource.QQMusic -> QQMusicSessionStore.read().let { AccountState(source, it.isLoggedIn, it.uin.takeIf(String::isNotBlank)) }
        MusicSource.Kugou -> KugouSessionStore.read().let { AccountState(source, it.isLoggedIn, it.userId.takeIf { it > 0L }?.toString()) }
        MusicSource.Kuwo -> KuwoSessionStore.read().let { AccountState(source, it.isLoggedIn, it.userId.takeIf(String::isNotBlank)) }
        MusicSource.AppleMusic -> AppleMusicSessionStore.read().let { AccountState(source, it.isConfigured, it.storefront.ifBlank { null }) }
        MusicSource.Bilibili -> BilibiliSessionStore.read().let { AccountState(source, it.isLoggedIn, it.userId.takeIf(String::isNotBlank)) }
        MusicSource.Spotify -> SpotifySessionStore.read().let { AccountState(source, it.isLoggedIn, it.accountId.takeIf(String::isNotBlank)) }
        MusicSource.YouTubeMusic -> YouTubeSessionStore.read().let { AccountState(source, it.isLoggedIn, it.accountName.takeIf(String::isNotBlank)) }
        MusicSource.Jellyfin -> JellyfinSessionStore.read().let { AccountState(source, it.isLoggedIn, it.userName.takeIf(String::isNotBlank)) }
        MusicSource.Local -> AccountState(source, true, "local")
    }

    fun allStates(): List<AccountState> = MusicSource.entries.map(::state)

    fun logout(source: MusicSource) {
        when (source) {
            MusicSource.Netease -> (neteaseSessionStore ?: NeteaseSessionStore()).clear()
            MusicSource.QQMusic -> QQMusicSessionStore.clear(clearWebCookies = true)
            MusicSource.Kugou -> KugouSessionStore.clearLogin()
            MusicSource.Kuwo -> KuwoSessionStore.clear()
            MusicSource.AppleMusic -> AppleMusicSessionStore.clear()
            MusicSource.Bilibili -> BilibiliSessionStore.clear(clearWebCookies = true)
            MusicSource.Spotify -> SpotifySessionStore.clear()
            MusicSource.YouTubeMusic -> YouTubeSessionStore.clear()
            MusicSource.Jellyfin -> JellyfinSessionStore.clear()
            MusicSource.Local -> Unit
        }
    }

    fun prepareAccountSwitch(source: MusicSource) = logout(source)
}
