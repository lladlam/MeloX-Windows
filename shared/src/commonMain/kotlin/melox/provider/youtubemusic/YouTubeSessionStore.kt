package melox.provider.youtubemusic

import melox.platform.getSecurePreferences as getPreferences
import java.security.MessageDigest
import com.metrolist.innertube.YouTube

/** Square-compatible YouTube Music session: cookie plus visitor/data-sync context. */
data class YouTubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val accountName: String = "",
) {
    val isLoggedIn: Boolean get() = cookie.contains("SAPISID=")

    override fun toString(): String = "YouTubeSession(isLoggedIn=$isLoggedIn, accountName=$accountName)"
}

object YouTubeSessionStore {
    private const val PreferencesName = "melox_youtube_music_session"
    private const val Cookie = "cookie"
    private const val VisitorData = "visitor_data"
    private const val DataSyncId = "data_sync_id"
    private const val PageId = "page_id"
    private const val AccountName = "account_name"

    @Volatile private var appliedFingerprint: String? = null

    private fun preferences() = getPreferences(PreferencesName)

    fun read(): YouTubeSession = preferences().let {
        YouTubeSession(
            cookie = it.getString(Cookie, "").orEmpty(),
            visitorData = it.getString(VisitorData, "").orEmpty(),
            dataSyncId = it.getString(DataSyncId, "").orEmpty(),
            pageId = it.getString(PageId, "").orEmpty(),
            accountName = it.getString(AccountName, "").orEmpty(),
        )
    }

    fun apply(): YouTubeSession = read().also(::applyToInnerTube)

    fun write(session: YouTubeSession) {
        preferences().putValues(mapOf(Cookie to session.cookie, VisitorData to session.visitorData,
            DataSyncId to session.dataSyncId, PageId to session.pageId, AccountName to session.accountName))
        applyToInnerTube(session)
    }

    fun clear() {
        preferences().clear()
        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null
        YouTube.pageId = null
        appliedFingerprint = null
    }

    fun authFingerprint(): String = fingerprintOf(read())

    @Synchronized private fun applyToInnerTube(session: YouTubeSession) {
        val fingerprint = fingerprintOf(session)
        if (fingerprint == appliedFingerprint) return
        YouTube.cookie = session.cookie.takeIf(String::isNotBlank)
        YouTube.visitorData = session.visitorData.takeIf(String::isNotBlank)
        YouTube.dataSyncId = session.dataSyncId.takeIf(String::isNotBlank)
        YouTube.pageId = session.pageId.takeIf(String::isNotBlank)
        appliedFingerprint = fingerprint
    }

    private fun fingerprintOf(session: YouTubeSession): String {
        val raw = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
