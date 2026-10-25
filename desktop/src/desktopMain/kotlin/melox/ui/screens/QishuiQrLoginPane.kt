package melox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import melox.provider.qishui.QishuiApiClient
import melox.provider.qishui.QishuiSessionStore
import org.jetbrains.skia.Image
import kotlin.coroutines.coroutineContext

/** The same real QR flow serves both layouts; disposal cancels polling and prevents saving a stale login. */
@Composable
internal fun QishuiQrLoginPane(onLoginSuccess: (String) -> Unit) {
    val api = remember { QishuiApiClient({ QishuiSessionStore.read() }) }
    var attempt by remember { mutableIntStateOf(0) }
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var status by remember { mutableStateOf("正在获取二维码…") }
    var loading by remember { mutableStateOf(false) }
    val latestSuccess by rememberUpdatedState(onLoginSuccess)
    LaunchedEffect(attempt) {
        bitmap = null; loading = true; status = "正在获取二维码…"
        try {
            val qr = api.createQr()
            val bytes = if (qr.image.startsWith("data:image/")) Base64.getMimeDecoder().decode(qr.image.substringAfter(','))
            else {
                val link = qr.link.ifBlank { qr.image }
                check(link.isNotBlank()) { "汽水音乐未返回二维码内容" }
                val matrix = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 640, 640)
                val image = BufferedImage(640, 640, BufferedImage.TYPE_INT_RGB)
                for (y in 0 until 640) for (x in 0 until 640) image.setRGB(x, y, if (matrix[x, y]) 0 else 0xFFFFFF)
                ByteArrayOutputStream().use { stream -> ImageIO.write(image, "png", stream); stream.toByteArray() }
            }
            bitmap = Image.makeFromEncoded(bytes).use { it.toComposeImageBitmap() }
            loading = false; status = "请使用已登录的抖音 App 扫码，并在手机上确认"
            repeat(100) {
                delay(2000)
                val state = api.pollQr(qr.token)
                if (state.sessionId.isNotBlank()) {
                    val temporary = QishuiSessionStore.read().copy(sessionId = state.sessionId, cookie = state.cookie,
                        userId = "", nickname = "")
                    val profile = QishuiApiClient({ temporary }).profile() ?: error("汽水音乐没有返回账号资料")
                    coroutineContext.ensureActive()
                    QishuiSessionStore.save(state.sessionId, profile.id, profile.displayName, state.cookie)
                    latestSuccess(profile.displayName)
                    return@LaunchedEffect
                }
                when (state.status.lowercase()) {
                    "expired", "cancelled", "canceled", "refused" -> { status = "二维码已失效或已取消，请刷新后重试"; return@LaunchedEffect }
                    "scanned", "scaned" -> status = "已扫码，请在抖音 App 中确认登录"
                    "confirmed" -> status = "已确认，正在等待汽水音乐登录会话…"
                }
            }
            status = "二维码已超时，请刷新后重试"
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { status = failure.message ?: "汽水音乐登录失败，请重试" }
        finally { loading = false }
    }
    Column(Modifier.fillMaxWidth().testTag("qishui-login"), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        bitmap?.let { Image(it, "汽水音乐登录二维码", Modifier.size(200.dp).testTag("qishui-login-qr")) }
        if (loading) CircularProgressIndicator(Modifier.size(32.dp))
        Text(status, Modifier.testTag("qishui-login-status"))
        Text("请使用抖音 App 扫码；汽水音乐 App 扫码可能无法确认。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { attempt++ }, modifier = Modifier.testTag("qishui-login-refresh")) { Text("刷新二维码") }
    }
}
