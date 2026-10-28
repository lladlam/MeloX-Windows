package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.music.model.MusicSource
import melox.music.provider.MeloXMusicProviders
import melox.provider.kugou.KugouLiteCheckInStore
import melox.provider.kugou.KugouLiteDailyCheckIn
import melox.provider.kugou.KugouLiteProvider
import melox.provider.kugou.KugouLiteVip
import melox.provider.kugou.KugouSessionStore
import melox.ui.glass.MeloXGlassDialog

/** Shared production actions presented by the native desktop and phone-style settings forms. */
@Composable
internal fun KugouLiteVipSettings(onOpenServices: () -> Unit) {
    val scope = rememberCoroutineScope()
    val provider = remember { MeloXMusicProviders.create()[MusicSource.KugouLite] as KugouLiteProvider }
    var session by remember { mutableStateOf(KugouSessionStore.readLite()) }
    var checkIn by remember { mutableStateOf(KugouLiteCheckInStore.read(session.userId)) }
    var vip by remember { mutableStateOf<KugouLiteVip?>(null) }
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    var busy by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh(manual: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val current = KugouSessionStore.readLite()
                if (current.userId != session.userId || current.token != session.token) vip = null
                session = current
                today = LocalDate.now().toString()
                if (session.isLoggedIn) {
                    val result = KugouLiteDailyCheckIn.run(force = manual, requireAutoEnabled = !manual)
                    checkIn = KugouLiteCheckInStore.read(session.userId)
                    message = result?.message ?: checkIn.message
                    vip = provider.vipStatus(force = true)
                } else {
                    checkIn = KugouLiteCheckInStore.read(session.userId)
                    vip = null
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Exception) { message = error.message ?: "会员信息刷新失败，请稍后重试" }
            finally { busy = false }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    SettingsGlassGroup {
        Text("酷狗概念版账号：" + if (session.isLoggedIn) session.userId.toString() else "未登录",
            Modifier.padding(12.dp).testTag("kugou-lite-account"))
        SettingsActionButton(if (session.isLoggedIn) "管理账号" else "登录概念版账号",
            Modifier.testTag("kugou-lite-open-services"), enabled = !busy, onClick = onOpenServices)
        val vipLabel = when {
            !session.isLoggedIn -> "未登录"
            vip == null -> "待刷新"
            vip?.superVip == true -> "超级 VIP"
            vip?.isVip == true -> "VIP 会员"
            vip?.freeListen == true -> "畅听权益"
            else -> "暂无会员权益"
        }
        Text("会员状态：$vipLabel", Modifier.padding(12.dp).testTag("kugou-lite-vip"))
        vip?.expiresAtEpochSeconds?.takeIf { it > 0L }?.let { expiry ->
            Text("会员有效期：" + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(expiry * 1_000L)),
                Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
    }
    Spacer(Modifier.height(18.dp))
    Text("每日签到", Modifier.padding(start = 8.dp, bottom = 8.dp), fontSize = 13.sp)
    SettingsGlassGroup {
        Text("今日签到：$today　" + when {
            !session.isLoggedIn -> "未登录"
            checkIn.successDay == today && checkIn.upgradedDay == today -> "已签到"
            checkIn.attemptDay == today -> "待重试"
            else -> "待签到"
        }, Modifier.padding(12.dp).testTag("kugou-lite-today"))
        Text("今日畅听 VIP：" + if (checkIn.successDay == today) "已领取" else "待领取", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        Text("今日概念版 VIP：" + if (checkIn.upgradedDay == today) "已升级" else "待升级", Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        SettingsExternalToggleRow("自动签到会员", checkIn.autoEnabled, "kugou-lite-auto", enabled = !busy,
            subtitle = "每天首次打开 MeloX 时自动领取畅听 VIP，并申请概念版 VIP 升级") { enabled ->
            KugouLiteCheckInStore.setAutoEnabled(enabled)
            checkIn = KugouLiteCheckInStore.read(session.userId)
            if (enabled && session.isLoggedIn) refresh()
        }
        SettingsActionButton(if (busy) "处理中…" else "签到领取畅听 + 概念版 VIP",
            Modifier.testTag("kugou-lite-checkin"), enabled = session.isLoggedIn && !busy) { confirm = true }
        Spacer(Modifier.height(8.dp))
        SettingsActionButton("刷新会员状态", Modifier.testTag("kugou-lite-refresh"), enabled = !busy) { refresh() }
        message?.let { Text(it, Modifier.padding(12.dp).testTag("kugou-lite-message"), fontSize = 13.sp) }
    }
    Text("每日额度、活动开放情况和账号资格以酷狗返回结果为准；自动签到失败后 10 分钟内不重复请求。",
        Modifier.padding(horizontal = 6.dp, vertical = 10.dp), fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    if (confirm) MeloXGlassDialog(visible = true, onDismiss = { confirm = false }) {
        Text("签到领会员", style = MaterialTheme.typography.titleLarge)
        Text("确认使用当前概念版账号领取今日畅听 VIP，并同时申请概念版 VIP 升级？", Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton("取消", Modifier.weight(1f).testTag("kugou-lite-confirm-cancel")) { confirm = false }
            SettingsActionButton("确认领取", Modifier.weight(1f).testTag("kugou-lite-confirm"), enabled = !busy) {
                confirm = false; refresh(manual = true)
            }
        }
    }
}
