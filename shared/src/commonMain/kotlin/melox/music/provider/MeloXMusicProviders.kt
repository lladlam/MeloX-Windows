package melox.music.provider

import melox.MeloXBuildConfig
import melox.account.NeteaseSessionStore
import melox.provider.applemusic.AppleMusicApiClient
import melox.provider.applemusic.AppleMusicSessionStore
import melox.provider.bilibili.BilibiliProvider
import melox.provider.bilibili.BilibiliSessionStore
import melox.provider.bilibili.BilibiliPlaybackAssociationStore
import melox.provider.bilibili.BilibiliApiCache
import melox.provider.jellyfin.JellyfinProvider
import melox.provider.jellyfin.JellyfinSessionStore
import melox.provider.kugou.KugouProvider
import melox.provider.kugou.KugouSessionStore
import melox.provider.kuwo.KuwoProvider
import melox.provider.kuwo.KuwoSessionStore
import melox.provider.lxuser.ChkszApiClient
import melox.provider.netease.NeteaseProvider
import melox.provider.local.LocalProvider
import melox.provider.qqmusic.QQMusicProvider
import melox.provider.qqmusic.QQMusicSessionStore
import melox.provider.spotify.SpotifyProvider
import melox.provider.youtubemusic.YouTubeMusicProvider
import melox.provider.youtubemusic.YouTubeSessionStore
import melox.network.MeloXHttpClient
import okhttp3.OkHttpClient

/** Creates provider instances that all read their authentication state locally. */
object MeloXMusicProviders {
    @Volatile
    private var sharedRegistry: MusicProviderRegistry? = null

    fun create(
        httpClient: OkHttpClient = MeloXHttpClient.shared,
    ): MusicProviderRegistry {
        if (httpClient !== MeloXHttpClient.shared) return buildRegistry(httpClient)
        return sharedRegistry ?: synchronized(this) {
            sharedRegistry ?: buildRegistry(httpClient).also { sharedRegistry = it }
        }
    }

    /** Registry reserved for URL resolution and quality probing. */
    fun createPlayback(
        httpClient: OkHttpClient = MeloXHttpClient.shared,
    ): MusicProviderRegistry {
        val nativeProviders = listOf<MusicProvider>(
            NeteaseProvider({ PlaybackAccountStore.neteaseCookie() }, httpClient),
            QQMusicProvider({ PlaybackAccountStore.qqSession() }, httpClient),
            KugouProvider({ PlaybackAccountStore.kugouSession() }, httpClient),
            KuwoProvider({ PlaybackAccountStore.kuwoSession() }, httpClient),
            AppleMusicApiClient({ AppleMusicSessionStore.read() }, httpClient),
            BilibiliProvider(
                { BilibiliSessionStore.read() }, httpClient,
                { bvid, cid -> BilibiliPlaybackAssociationStore.read(bvid, cid.toLongOrNull() ?: 0L) },
                BilibiliApiCache.shared(),
            ),
            JellyfinProvider({ JellyfinSessionStore.read() }, httpClient),
            YouTubeMusicProvider(httpClient),
            LocalProvider(),
        )
        return MusicProviderRegistry(
            nativeProviders + SpotifyProvider(
                null,
                MeloXBuildConfig.SPOTIFY_CLIENT_ID,
                playbackProviders = { nativeProviders },
            ),
        )
    }

    private fun buildRegistry(httpClient: OkHttpClient): MusicProviderRegistry {
        val nativeProviders = listOf<MusicProvider>(
            NeteaseProvider(
                cookieProvider = { NeteaseSessionStore.readCookie() },
                httpClient = httpClient,
            ),
            QQMusicProvider(
                sessionProvider = { QQMusicSessionStore.read() },
                httpClient = httpClient,
            ),
            KugouProvider(
                sessionProvider = { KugouSessionStore.read() },
                httpClient = httpClient,
            ),
            KuwoProvider({ KuwoSessionStore.read() }, httpClient),
            AppleMusicApiClient(
                sessionProvider = { AppleMusicSessionStore.read() },
                httpClient = httpClient,
            ),
            BilibiliProvider(
                sessionProvider = { BilibiliSessionStore.read() },
                httpClient = httpClient,
                associationProvider = { bvid, cid -> BilibiliPlaybackAssociationStore.read(bvid, cid.toLongOrNull() ?: 0L) },
                apiCache = BilibiliApiCache.shared(),
            ),
            JellyfinProvider({ JellyfinSessionStore.read() }, httpClient),
            YouTubeMusicProvider(httpClient),
            LocalProvider(),
        )
        return MusicProviderRegistry(
            nativeProviders + SpotifyProvider(
                null,
                MeloXBuildConfig.SPOTIFY_CLIENT_ID,
                playbackProviders = { nativeProviders },
            ),
        )
    }
}
