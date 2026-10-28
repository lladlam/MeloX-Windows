package melox.provider.kugou

import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import melox.platform.getPreferences

data class KugouLiteCheckInState(
    val autoEnabled: Boolean = true,
    val successDay: String? = null,
    val upgradedDay: String? = null,
    val attemptDay: String? = null,
    val attemptAtMs: Long = 0L,
    val message: String? = null,
)

data class KugouLiteCheckInResult(val day: String, val claimed: Boolean, val upgraded: Boolean, val message: String)

/** One switch, but separate daily outcomes for each real account. */
object KugouLiteCheckInStore {
    private fun prefs() = getPreferences("melox_kugou_lite_checkin")
    private fun key(userId: Long, name: String) = "${userId}_$name"
    fun read(userId: Long = KugouSessionStore.readLite().userId): KugouLiteCheckInState {
        val p = prefs()
        return KugouLiteCheckInState(p.getBoolean("auto_enabled", true), p.getString(key(userId, "success_day"), null),
            p.getString(key(userId, "upgraded_day"), null), p.getString(key(userId, "attempt_day"), null),
            p.getLong(key(userId, "attempt_at"), 0L), p.getString(key(userId, "message"), null))
    }
    fun setAutoEnabled(enabled: Boolean) = prefs().putBoolean("auto_enabled", enabled)
    internal fun record(userId: Long, result: KugouLiteCheckInResult) {
        val values = mutableMapOf<String, String?>(key(userId, "attempt_day") to result.day,
            key(userId, "attempt_at") to System.currentTimeMillis().toString(), key(userId, "message") to result.message)
        if (result.claimed) values[key(userId, "success_day")] = result.day
        if (result.upgraded) values[key(userId, "upgraded_day")] = result.day
        prefs().putValues(values)
    }
}

/** Android's two reward requests, serialized and rate-limited independently per account. */
object KugouLiteDailyCheckIn {
    private val mutex = Mutex()
    private const val AUTO_RETRY_COOLDOWN_MS = 10 * 60 * 1000L

    suspend fun run(force: Boolean = false, requireAutoEnabled: Boolean = true): KugouLiteCheckInResult? = mutex.withLock {
        val session = KugouSessionStore.readLite()
        val state = KugouLiteCheckInStore.read(session.userId)
        val day = LocalDate.now().toString()
        if (!shouldRun(state, session.isLoggedIn, day, System.currentTimeMillis(), force, requireAutoEnabled)) return@withLock null
        fun current(): KugouSession = KugouSessionStore.readLite().also {
            check(sameSession(session, it)) { "概念版账号已变化，请重试" }
        }
        val provider = KugouLiteProvider(sessionProvider = ::current,
            persistAuth = { expected, auth -> check(KugouSessionStore.updateLiteEntitlements(expected = expected, auth = auth)) { "概念版账号已变化，请重试" } },
            persistDfid = { expected, dfid -> check(KugouSessionStore.updateLiteDfid(dfid, expected = expected)) { "概念版账号已变化，请重试" } },
            persistVip = { expected, vip -> check(KugouSessionStore.updateLiteEntitlements(expected = expected, vipType = vip.vipType,
                vipExpiresAtEpochSeconds = vip.expiresAtEpochSeconds, superVip = vip.superVip,
                vipActive = vip.isVip, freeListenEligible = vip.freeListen)) { "概念版账号已变化，请重试" } })
        val result = withContext(Dispatchers.IO) {
            claimRewards(provider, LocalDate.parse(day), state) {
                // Do not send another account's second reward if the first was in flight during a switch.
                check(sameSession(session, KugouSessionStore.readLite())) { "概念版账号已变化，请重试" }
            }
        }
        check(sameSession(session, KugouSessionStore.readLite())) { "概念版账号已变化，请重试" }
        KugouLiteCheckInStore.record(session.userId, result)
        try { provider.vipStatus(force = true) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { }
        result
    }

    private fun sameSession(a: KugouSession, b: KugouSession) = a.userId == b.userId && a.token == b.token
    internal suspend fun claimRewards(provider: KugouLiteProvider, day: LocalDate,
                                      state: KugouLiteCheckInState = KugouLiteCheckInState(),
                                      verifySession: () -> Unit): KugouLiteCheckInResult {
        verifySession()
        val claim = if (state.successDay == day.toString()) RewardOutcome(true, "畅听 VIP今日已领取")
            else reward("畅听 VIP") { provider.claimDailyVip(day) }
        verifySession()
        val upgrade = if (state.upgradedDay == day.toString()) RewardOutcome(true, "概念版 VIP今日已领取")
            else reward("概念版 VIP") { provider.upgradeDailyVip() }
        verifySession()
        return KugouLiteCheckInResult(day.toString(), claim.completed, upgrade.completed,
            "今日签到：${claim.message}；${upgrade.message}。")
    }
    internal fun shouldRun(state: KugouLiteCheckInState, loggedIn: Boolean, day: String, nowMs: Long,
                           force: Boolean, requireAutoEnabled: Boolean): Boolean {
        if (!loggedIn || (requireAutoEnabled && !state.autoEnabled)) return false
        if (force) return true
        if (state.successDay == day && state.upgradedDay == day) return false
        if (state.attemptDay == day && nowMs - state.attemptAtMs < AUTO_RETRY_COOLDOWN_MS) return false
        return true
    }

    private suspend fun reward(label: String, action: suspend () -> Unit): RewardOutcome = try {
        action(); RewardOutcome(true, "${label}已领取")
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: KugouApiException) {
        when (error.errorCode) {
            131001 -> RewardOutcome(true, "${label}今日已领取")
            20028 -> RewardOutcome(false, "${label}当前账号暂时无法领取")
            else -> RewardOutcome(false, "$label 领取失败：${error.message ?: "未知错误"}")
        }
    } catch (error: Exception) { RewardOutcome(false, "$label 领取失败：${error.message ?: "网络异常"}") }

    private data class RewardOutcome(val completed: Boolean, val message: String)
}
