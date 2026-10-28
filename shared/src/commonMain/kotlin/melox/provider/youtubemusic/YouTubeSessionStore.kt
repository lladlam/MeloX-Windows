package melox.provider.youtubemusic

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.utils.YouTubeAuth
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import melox.account.SecureSessionPreferences
import melox.platform.MeloXPreferences
import melox.settings.MeloXAppLanguage

/** Secure local account context. Cookie contents are intentionally omitted from logs. */
data class YouTubeSession(
    val cookie: String = "",
    val visitorData: String = "",
    val dataSyncId: String = "",
    val pageId: String = "",
    val accountName: String = "",
    val authUser: String = "0",
) {
    val isLoggedIn: Boolean get() = YouTubeAuth.hasAuthCookie(cookie)
    override fun toString(): String = "YouTubeSession(isLoggedIn=$isLoggedIn, accountName=$accountName)"
}

data class YouTubeChannel(
    val name: String,
    val handle: String?,
    val thumbnailUrl: String?,
    val pageId: String?,
    val dataSyncId: String?,
    val selected: Boolean,
)

internal interface YouTubeAccountBackend {
    fun apply(session: YouTubeSession, force: Boolean = false)
    suspend fun accountInfo(session: YouTubeSession): AccountInfo
    suspend fun accounts(session: YouTubeSession): List<YouTubeChannel>
}

internal object InnerTubeAccountBackend : YouTubeAccountBackend {
    @Volatile private var appliedFingerprint: String? = null
    private val accountApi by lazy { YouTubeAccountApi() }

    @Synchronized override fun apply(session: YouTubeSession, force: Boolean) {
        // App language is independent of JVM locale and can change without a login change.
        YouTube.locale = YouTubeLocale.fromLocale(MeloXAppLanguage.locale)
        val fingerprint = fingerprintOf(session)
        if (!force && appliedFingerprint == fingerprint) return
        YouTube.cookie = session.cookie.takeIf(String::isNotBlank)
        YouTube.visitorData = session.visitorData.takeIf(String::isNotBlank)
        YouTube.dataSyncId = session.dataSyncId.takeIf(String::isNotBlank)
        YouTube.pageId = session.pageId.takeIf(String::isNotBlank)
        YouTube.authUser = session.authUser
        appliedFingerprint = fingerprint
    }

    override suspend fun accountInfo(session: YouTubeSession): AccountInfo = accountApi.accountInfo(session)
    override suspend fun accounts(session: YouTubeSession): List<YouTubeChannel> = accountApi.accounts(session)
}

private fun fingerprintOf(session: YouTubeSession): String {
    val raw = listOf(session.cookie, session.visitorData, session.dataSyncId, session.pageId, session.authUser).joinToString("\u0000")
    return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/** Captures both identity and generation so an A→B→A switch cannot revive stale results. */
internal data class YouTubeRequestSession(val session: YouTubeSession, val revision: Long) {
    val fingerprint: String = fingerprintOf(session)
}

/** Account operations are serialized; do not overwrite an account changed during network IO. */
internal class YouTubeAccountController(
    private val prefs: MeloXPreferences,
    private val backend: YouTubeAccountBackend,
) {
    private val accountMutex = Mutex()
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private val mutableExpired = MutableStateFlow(false)
    val expired = mutableExpired.asStateFlow()

    @Synchronized fun read(): YouTubeSession = YouTubeSession(
        cookie = prefs.getString("cookie", "").orEmpty(),
        visitorData = prefs.getString("visitor_data", "").orEmpty(),
        dataSyncId = prefs.getString("data_sync_id", "").orEmpty(),
        pageId = prefs.getString("page_id", "").orEmpty(),
        accountName = prefs.getString("account_name", "").orEmpty(),
        authUser = prefs.getString("auth_user", "0").orEmpty().ifBlank { "0" },
    )

    @Synchronized fun apply(force: Boolean = false): YouTubeSession = read().also { backend.apply(it, force) }
    @Synchronized private fun snapshot(apply: Boolean = false): Pair<YouTubeSession, Long> =
        (if (apply) apply() else read()) to mutableRevision.value

    @Synchronized fun requestSession(): YouTubeRequestSession = YouTubeRequestSession(apply(), mutableRevision.value)

    @Synchronized fun requireCurrent(request: YouTubeRequestSession) {
        check(mutableRevision.value == request.revision && read() == request.session) {
            "YouTube Music 账号已更改，请重新加载"
        }
    }

    @Synchronized fun write(session: YouTubeSession) {
        val previous = read()
        prefs.putValues(mapOf(
            "cookie" to session.cookie, "visitor_data" to session.visitorData,
            "data_sync_id" to session.dataSyncId, "page_id" to session.pageId,
            "account_name" to session.accountName, "auth_user" to session.authUser,
        ))
        backend.apply(session)
        mutableExpired.value = false
        if (previous != session) mutableRevision.value++
    }

    @Synchronized fun clear() {
        prefs.clear()
        backend.apply(YouTubeSession(), force = true)
        mutableExpired.value = false
        mutableRevision.value++
    }

    fun authFingerprint(): String = fingerprintOf(read())

    suspend fun revalidate(): Result<Unit> = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            val (saved, revision) = snapshot(apply = true)
            if (!saved.isLoggedIn) return@withLock Result.success(Unit)
            try {
                val info = backend.accountInfo(saved)
                currentCoroutineContext().ensureActive()
                synchronized(this@YouTubeAccountController) {
                    if (read() == saved && mutableRevision.value == revision) {
                        mutableExpired.value = false
                        if (info.name != saved.accountName) write(saved.copy(accountName = info.name))
                    }
                }
                Result.success(Unit)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                synchronized(this@YouTubeAccountController) {
                    if (read() == saved && mutableRevision.value == revision) {
                        val status = (failure as? YouTubeAccountHttpException)?.statusCode
                        if (status == 401 || status == 403) mutableExpired.value = true
                    }
                }
                Result.failure(failure)
            }
        }
    }

    suspend fun channels(): List<YouTubeChannel> = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            val (saved, revision) = snapshot(apply = true)
            check(saved.isLoggedIn) { "请先登录 YouTube Music" }
            val channels = backend.accounts(saved)
            currentCoroutineContext().ensureActive()
            synchronized(this@YouTubeAccountController) {
                check(read() == saved && mutableRevision.value == revision) { "YouTube Music 账号已更改，请重新读取频道" }
            }
            channels
        }
    }

    suspend fun useChannel(channel: YouTubeChannel) = withContext(Dispatchers.IO) {
        accountMutex.withLock {
            val (saved, revision) = snapshot(apply = true)
            check(saved.isLoggedIn) { "请先登录 YouTube Music" }
            val candidate = saved.copy(pageId = channel.pageId.orEmpty(), dataSyncId = channel.dataSyncId.orEmpty(), accountName = channel.name)
            val info = backend.accountInfo(candidate)
            currentCoroutineContext().ensureActive()
            synchronized(this@YouTubeAccountController) {
                check(read() == saved && mutableRevision.value == revision) { "YouTube Music 账号已更改，请重新选择频道" }
                write(candidate.copy(accountName = info.name))
            }
        }
    }

    suspend fun validateAndSave(candidate: YouTubeSession): String = withContext(Dispatchers.IO) {
        require(candidate.isLoggedIn) { "Cookie 缺少有效的 YouTube 登录凭据" }
        require(candidate.authUser.isNotEmpty() && candidate.authUser.all(Char::isDigit)) { "AuthUser 必须是数字" }
        accountMutex.withLock {
            val (saved, revision) = snapshot()
            val info = backend.accountInfo(candidate)
            currentCoroutineContext().ensureActive()
            synchronized(this@YouTubeAccountController) {
                check(read() == saved && mutableRevision.value == revision) { "YouTube Music 账号已更改，请重新登录" }
                write(candidate.copy(accountName = info.name))
            }
            info.name
        }
    }
}

object YouTubeSessionStore {
    private val controller = YouTubeAccountController(SecureSessionPreferences.open("melox_youtube_music_session"), InnerTubeAccountBackend)
    val revision = controller.revision
    val expired = controller.expired
    fun read() = controller.read()
    fun apply(force: Boolean = false) = controller.apply(force)
    fun write(session: YouTubeSession) = controller.write(session)
    fun clear() = controller.clear()
    fun authFingerprint() = controller.authFingerprint()
    internal fun requestSession() = controller.requestSession()
    internal fun requireCurrent(request: YouTubeRequestSession) = controller.requireCurrent(request)
    suspend fun revalidate() = controller.revalidate()
    suspend fun channels() = controller.channels()
    suspend fun useChannel(channel: YouTubeChannel) = controller.useChannel(channel)
    suspend fun validateAndSave(candidate: YouTubeSession) = controller.validateAndSave(candidate)
}
