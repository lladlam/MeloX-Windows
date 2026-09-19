package melox.music.provider

import melox.platform.getPreferences
import melox.account.NeteaseSessionStore
import melox.provider.kugou.KugouSession
import melox.provider.kugou.KugouSessionStore
import melox.provider.kuwo.KuwoSession
import melox.provider.kuwo.KuwoSessionStore
import melox.provider.qqmusic.QQMusicSession
import melox.provider.qqmusic.QQMusicSessionStore
import melox.provider.youtubemusic.YouTubeSession
import melox.provider.youtubemusic.YouTubeSessionStore

object PlaybackAccountStore {
    private const val PreferencesName = "melox_playback_account"
    private const val Enabled = "enabled"

    private fun prefs() = getPreferences(PreferencesName)

    fun isEnabled(): Boolean = prefs().getBoolean(Enabled, false)

    fun setEnabled(enabled: Boolean) {
        prefs().putBoolean(Enabled, enabled)
        if (!enabled) clear()
    }

    fun neteaseCookie(): String = selectPlaybackSession(
        isEnabled(), NeteaseSessionStore.readPlaybackCookie(), NeteaseSessionStore.readCookie(), NeteaseSessionStore::containsMusicU,
    )

    fun qqSession(): QQMusicSession = selectPlaybackSession(
        isEnabled(), QQMusicSessionStore.read(playback = true), QQMusicSessionStore.read(), QQMusicSession::isLoggedIn,
    )

    fun kugouSession(): KugouSession = selectPlaybackSession(
        isEnabled(), KugouSessionStore.read(playback = true), KugouSessionStore.read(), KugouSession::isLoggedIn,
    )

    fun kuwoSession(): KuwoSession = selectPlaybackSession(
        isEnabled(), KuwoSessionStore.read(playback = true), KuwoSessionStore.read(), KuwoSession::isLoggedIn,
    )

    fun youtubeSession(): YouTubeSession = selectPlaybackSession(
        isEnabled(), YouTubeSessionStore.read(), YouTubeSessionStore.read(), YouTubeSession::isLoggedIn,
    )

    fun clear() {
        NeteaseSessionStore.clearPlayback()
        QQMusicSessionStore.clear(clearWebCookies = false, playback = true)
        KugouSessionStore.clearLogin(playback = true)
        KuwoSessionStore.clear(playback = true)
    }
}

internal fun <T> selectPlaybackSession(enabled: Boolean, playback: T, main: T, isValid: (T) -> Boolean): T =
    playback.takeIf { enabled && isValid(it) } ?: main
