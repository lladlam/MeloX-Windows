package melox.provider.youtubemusic

import melox.platform.getPreferences
import java.security.MessageDigest

/** Square-compatible YouTube Music session: cookie plus visitor/data-sync context. */
data class YouTubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val accountName: String = "",
) {
    val isLoggedIn: Boolean get() = cookie.isNotBlank() && dataSyncId.isNotBlank()
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

    fun apply(): YouTubeSession = read().also { appliedFingerprint = fingerprintOf(it) }

    fun write(session: YouTubeSession) {
        preferences().apply {
            putString(Cookie, session.cookie)
            putString(VisitorData, session.visitorData)
            putString(DataSyncId, session.dataSyncId)
            putString(PageId, session.pageId)
            putString(AccountName, session.accountName)
        }
        appliedFingerprint = fingerprintOf(session)
    }

    fun clear() {
        preferences().clear()
        appliedFingerprint = null
    }

    fun authFingerprint(): String = fingerprintOf(read())

    private fun fingerprintOf(session: YouTubeSession): String {
        val raw = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
