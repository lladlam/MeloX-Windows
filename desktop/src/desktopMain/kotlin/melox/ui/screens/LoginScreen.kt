@file:Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")

package melox.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseSessionStore
import melox.network.NeteasePhoneAuthClient
import melox.provider.kugou.KugouLoginClient
import melox.provider.kugou.KugouQrLoginState
import melox.provider.kugou.KugouSessionStore
import melox.provider.qqmusic.QQMusicQrLoginClient
import melox.provider.qqmusic.QQMusicQrLoginMethod
import melox.provider.qqmusic.QQMusicQrLoginState
import melox.provider.qqmusic.QQMusicSessionStore
import melox.ui.navigation.MeloXNavState
import melox.ui.theme.MeloXColors
import melox.ui.theme.MeloXLanTingProFontFamily
import java.awt.Cursor
import org.jetbrains.skia.Image

// ── Provider definitions ──

private data class LoginProvider(
    val id: String,
    val name: String,
    val color: Color,
    val loginMethod: String,
)

private val allProviders = listOf(
    LoginProvider("netease", "网易云音乐", Color(0xFFFF2442), "手机号登录 / 扫码登录"),
    LoginProvider("qq", "QQ音乐", Color(0xFF12B7F5), "扫码登录"),
    LoginProvider("kugou", "酷狗音乐", Color(0xFF2CA2F9), "扫码登录"),
    LoginProvider("kuwo", "酷我音乐", Color(0xFFFF6600), "手机号登录"),
    LoginProvider("bilibili", "哔哩哔哩", Color(0xFFFB7299), "WebView登录"),
    LoginProvider("spotify", "Spotify", Color(0xFF1DB954), "OAuth登录"),
    LoginProvider("youtubemusic", "YouTube Music", Color(0xFFFF0000), "Google登录"),
    LoginProvider("applemusic", "Apple Music", Color(0xFFFC3C44), "Token输入"),
    LoginProvider("jellyfin", "Jellyfin", Color(0xFF00A4DC), "服务器地址 + 凭据"),
    LoginProvider("local", "本地音乐", Color(0xFF888888), "文件夹选择"),
)

// ── Login state per provider ──

@Stable
private class LoginScreenState {
    val loggedInProviders = mutableStateMapOf<String, Boolean>()
    val usernames = mutableStateMapOf<String, String>()
    var dialogTarget by mutableStateOf<LoginProvider?>(null)
    var dialogInput by mutableStateOf("")
    var dialogPassword by mutableStateOf("")
    var dialogLoading by mutableStateOf(false)
    var dialogMessage by mutableStateOf<String?>(null)
    var dialogTab by mutableStateOf(0)
    var dialogServerUrl by mutableStateOf("")

    fun isLoggedIn(id: String): Boolean = loggedInProviders[id] == true
    fun username(id: String): String = usernames[id] ?: ""
}

// ── Top bar ──

@Composable
private fun LoginTopBar(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(MeloXColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "账号",
            color = MeloXColors.TextPrimary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .hoverable(remember { MutableInteractionSource() })
                .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        ) {
            Text(
                text = "‹",
                color = MeloXColors.TextPrimary,
                fontSize = 22.sp,
            )
        }
    }
}

// ── Provider card ──

@Composable
private fun ProviderCard(
    provider: LoginProvider,
    isLoggedIn: Boolean,
    username: String,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        shape = RoundedCornerShape(14.dp),
        color = if (isHovered) MeloXColors.CardBackgroundHover else MeloXColors.CardBackground,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar circle
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(provider.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = provider.name.first().toString(),
                    color = Color.White,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Name + status
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = provider.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isLoggedIn) {
                        if (username.isNotEmpty()) "已登录 - $username" else "已登录"
                    } else {
                        provider.loginMethod
                    },
                    color = if (isLoggedIn) MeloXColors.Success else MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action button
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable {
                        if (isLoggedIn) onLogoutClick() else onLoginClick()
                    }
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
                shape = RoundedCornerShape(20.dp),
                color = if (isLoggedIn) MeloXColors.SurfaceVariant else provider.color.copy(alpha = 0.15f),
            ) {
                Text(
                    text = if (isLoggedIn) "退出" else "登录",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = if (isLoggedIn) MeloXColors.TextSecondary else provider.color,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ── Login dialog ──

@Composable
private fun LoginDialog(
    provider: LoginProvider,
    state: LoginScreenState,
    onDismiss: () -> Unit,
    onLoginSuccess: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!state.dialogLoading) onDismiss()
        },
        containerColor = MeloXColors.Surface,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(provider.color),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = provider.name.first().toString(),
                        color = Color.White,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = provider.name,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (provider.id) {
                    "netease" -> NeteaseLoginForm(state, onLoginSuccess)
                    "qq", "kugou" -> QRCodeLoginForm(provider, state, onLoginSuccess)
                    "kuwo" -> PhoneCodeLoginForm(provider, state, onLoginSuccess)
                    "bilibili" -> WebViewLoginForm(provider, state, onLoginSuccess)
                    "spotify" -> OAuthLoginForm(provider, state, onLoginSuccess)
                    "youtubemusic" -> OAuthLoginForm(provider, state, onLoginSuccess)
                    "applemusic" -> TokenInputForm(provider, state, onLoginSuccess)
                    "jellyfin" -> ServerLoginForm(provider, state, onLoginSuccess)
                    "local" -> LocalFolderForm(state, onLoginSuccess)
                    else -> GenericLoginForm(provider, state, onLoginSuccess)
                }

                // Loading indicator
                if (state.dialogLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = provider.color,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "登录中...",
                            color = MeloXColors.TextSecondary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }

                // Message feedback
                state.dialogMessage?.let { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (msg.contains("成功")) {
                            MeloXColors.Success.copy(alpha = 0.12f)
                        } else {
                            MeloXColors.Error.copy(alpha = 0.12f)
                        },
                    ) {
                        Text(
                            text = msg,
                            modifier = Modifier.padding(12.dp),
                            color = if (msg.contains("成功")) MeloXColors.Success else MeloXColors.Error,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.dialogLoading,
            ) {
                Text(
                    text = "关闭",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                )
            }
        },
    )
}

// ── Netease: tabs for phone / QR ──

@Composable
private fun NeteaseLoginForm(state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column {
        // Tab row
        TabRow(
            selectedTabIndex = state.dialogTab,
            containerColor = MeloXColors.SurfaceVariant,
            contentColor = MeloXColors.Primary,
            indicator = {},
        ) {
            Tab(
                selected = state.dialogTab == 0,
                onClick = { state.dialogTab = 0 },
                text = {
                    Text(
                        text = "手机号登录",
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 13.sp,
                    )
                },
            )
            Tab(
                selected = state.dialogTab == 1,
                onClick = { state.dialogTab = 1 },
                text = {
                    Text(
                        text = "扫码登录",
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 13.sp,
                    )
                },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.dialogTab == 0) {
            // Phone login
            OutlinedTextField(
                value = state.dialogInput,
                onValueChange = { state.dialogInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("手机号", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeloXColors.Primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MeloXColors.Outline,
                    focusedContainerColor = MeloXColors.SurfaceVariant,
                    unfocusedContainerColor = MeloXColors.SurfaceVariant,
                    cursorColor = MeloXColors.Primary,
                    focusedTextColor = MeloXColors.TextPrimary,
                    unfocusedTextColor = MeloXColors.TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = state.dialogPassword,
                onValueChange = { state.dialogPassword = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("验证码", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MeloXColors.Primary.copy(alpha = 0.5f),
                    unfocusedBorderColor = MeloXColors.Outline,
                    focusedContainerColor = MeloXColors.SurfaceVariant,
                    unfocusedContainerColor = MeloXColors.SurfaceVariant,
                    cursorColor = MeloXColors.Primary,
                    focusedTextColor = MeloXColors.TextPrimary,
                    unfocusedTextColor = MeloXColors.TextPrimary,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = MeloXLanTingProFontFamily,
                ),
            )
            Spacer(modifier = Modifier.height(8.dp))
            val scope = rememberCoroutineScope()
            val authClient = remember { NeteasePhoneAuthClient() }
            LoginButton(
                text = "获取验证码",
                color = Color(0xFFFF2442),
                enabled = state.dialogInput.isNotBlank() && !state.dialogLoading,
            ) {
                val phone = state.dialogInput.trim()
                state.dialogLoading = true
                state.dialogMessage = null
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { authClient.sendCode("86", phone) }
                    }
                    state.dialogLoading = false
                    state.dialogMessage = result.exceptionOrNull()?.message ?: "验证码已发送"
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LoginButton(
                color = Color(0xFFFF2442),
                enabled = state.dialogInput.isNotBlank() &&
                    state.dialogPassword.isNotBlank() &&
                    !state.dialogLoading,
            ) {
                val phone = state.dialogInput.trim()
                val code = state.dialogPassword.trim()
                state.dialogLoading = true
                state.dialogMessage = null
                scope.launch {
                    val result = runCatching {
                        val cookie = withContext(Dispatchers.IO) { authClient.login("86", phone, code) }
                        NeteaseSessionStore().acceptAuthenticatedCookie(cookie).getOrThrow()
                        cookie
                    }
                    state.dialogLoading = false
                    result.onSuccess { cookie ->
                        onLoginSuccess(cookie)
                    }.onFailure { error ->
                        state.dialogMessage = error.message ?: "登录失败"
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MeloXColors.SurfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "网易云请使用手机验证码登录",
                        color = MeloXColors.TextSecondary,
                        fontFamily = MeloXLanTingProFontFamily,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { state.dialogTab = 0 }) {
                        Text(
                            text = "手机号登录",
                            color = MeloXColors.Primary,
                            fontFamily = MeloXLanTingProFontFamily,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// ── QR code login form ──

@Composable
private fun QRCodeLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    when (provider.id) {
        "qq" -> QqQrLoginPane(state, onLoginSuccess)
        "kugou" -> KugouQrLoginPane(state, onLoginSuccess)
        else -> Text(
            text = "该来源没有扫码登录",
            color = MeloXColors.TextSecondary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun QqQrLoginPane(state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var attempt by remember { mutableIntStateOf(0) }
    var qrImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码") }

    LaunchedEffect(attempt) {
        qrImage = null
        statusText = "正在获取二维码"
        val session = runCatching {
            withContext(Dispatchers.IO) {
                QQMusicQrLoginClient().createSession(QQMusicQrLoginMethod.QQ)
            }
        }.getOrElse { error ->
            state.dialogMessage = error.message ?: "获取二维码失败"
            statusText = "获取二维码失败"
            return@LaunchedEffect
        }
        qrImage = runCatching {
            Image.makeFromEncoded(session.imageBytes).use { it.toComposeImageBitmap() }
        }.getOrElse { error ->
            state.dialogMessage = error.message ?: "二维码图片无法显示"
            statusText = "二维码图片无法显示"
            return@LaunchedEffect
        }
        statusText = "请使用 QQ 扫描"
        val client = QQMusicQrLoginClient()
        while (isActive) {
            delay(2000)
            val result = runCatching { client.checkSession(session) }.getOrElse { error ->
                state.dialogMessage = error.message ?: "查询扫码状态失败"
                null
            } ?: continue
            when (result) {
                QQMusicQrLoginState.Waiting -> statusText = "等待扫码"
                QQMusicQrLoginState.Scanned -> statusText = "已扫码，请在手机上确认"
                QQMusicQrLoginState.Expired -> {
                    state.dialogMessage = "二维码已过期"
                    statusText = "二维码已过期"
                    return@LaunchedEffect
                }
                QQMusicQrLoginState.Rejected -> {
                    state.dialogMessage = "已拒绝登录"
                    statusText = "已拒绝登录"
                    return@LaunchedEffect
                }
                is QQMusicQrLoginState.Authorized -> {
                    val saved = runCatching { QQMusicSessionStore.write(result.cookie) }
                    saved.onFailure { error ->
                        state.dialogMessage = error.message ?: "保存登录态失败"
                        return@LaunchedEffect
                    }
                    onLoginSuccess(saved.getOrNull()?.uin?.ifBlank { result.cookie } ?: result.cookie)
                    return@LaunchedEffect
                }
            }
        }
    }

    QrStatusColumn(statusText = statusText) {
        qrImage?.let { bitmap ->
            Image(
                bitmap = bitmap,
                contentDescription = "QQ 登录二维码",
                modifier = Modifier.size(180.dp),
            )
        }
        if (statusText == "二维码已过期" || statusText == "获取二维码失败") {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { state.dialogMessage = null; attempt++ }) {
                Text(
                    text = "重试",
                    color = MeloXColors.Primary,
                    fontFamily = MeloXLanTingProFontFamily,
                )
            }
        }
    }
}

@Composable
private fun KugouQrLoginPane(state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    var attempt by remember { mutableIntStateOf(0) }
    var qrUrl by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码") }

    LaunchedEffect(attempt) {
        qrUrl = null
        statusText = "正在获取二维码"
        val client = KugouLoginClient(sessionProvider = { KugouSessionStore.read() })
        val session = runCatching {
            withContext(Dispatchers.IO) { client.createQrSession() }
        }.getOrElse { error ->
            state.dialogMessage = error.message ?: "获取二维码失败"
            statusText = "获取二维码失败"
            return@LaunchedEffect
        }
        qrUrl = session.qrContentUrl
        statusText = "用酷狗 App 打开此链接"
        while (isActive) {
            delay(2000)
            val result = runCatching { client.checkQrSession(session.key) }.getOrElse { error ->
                state.dialogMessage = error.message ?: "查询扫码状态失败"
                null
            } ?: continue
            when (result) {
                KugouQrLoginState.Waiting -> statusText = "等待扫码"
                KugouQrLoginState.Scanned -> statusText = "已扫码，请在手机上确认"
                KugouQrLoginState.Expired -> {
                    state.dialogMessage = "二维码已过期"
                    statusText = "二维码已过期"
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Authorized -> {
                    KugouSessionStore.updateLogin(
                        token = result.token,
                        userId = result.userId,
                        vipToken = result.vipToken,
                        vipType = result.vipType,
                    )
                    onLoginSuccess(result.token)
                    return@LaunchedEffect
                }
                is KugouQrLoginState.Unknown -> statusText = "未知状态 ${result.status}"
            }
        }
    }

    QrStatusColumn(statusText = statusText) {
        qrUrl?.let { url ->
            SelectionContainer {
                Text(
                    text = url,
                    color = MeloXColors.TextPrimary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "用酷狗 App 打开此链接",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (statusText == "二维码已过期" || statusText == "获取二维码失败") {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { state.dialogMessage = null; attempt++ }) {
                Text(
                    text = "重试",
                    color = MeloXColors.Primary,
                    fontFamily = MeloXLanTingProFontFamily,
                )
            }
        }
    }
}

@Composable
private fun QrStatusColumn(statusText: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeloXColors.SurfaceVariant)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, content = content)
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = statusText,
            color = MeloXColors.TextSecondary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Phone code login form ──

@Composable
private fun PhoneCodeLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = state.dialogInput,
            onValueChange = { state.dialogInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("手机号", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dialogPassword,
            onValueChange = { state.dialogPassword = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("验证码", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (provider.id == "netease") {
            val scope = rememberCoroutineScope()
            val authClient = remember { NeteasePhoneAuthClient() }
            LoginButton(
                text = "获取验证码",
                color = provider.color,
                enabled = state.dialogInput.isNotBlank() && !state.dialogLoading,
            ) {
                val phone = state.dialogInput.trim()
                state.dialogLoading = true
                state.dialogMessage = null
                scope.launch {
                    val result = runCatching {
                        withContext(Dispatchers.IO) { authClient.sendCode("86", phone) }
                    }
                    state.dialogLoading = false
                    state.dialogMessage = result.exceptionOrNull()?.message ?: "验证码已发送"
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            LoginButton(
                color = provider.color,
                enabled = state.dialogInput.isNotBlank() &&
                    state.dialogPassword.isNotBlank() &&
                    !state.dialogLoading,
            ) {
                val phone = state.dialogInput.trim()
                val code = state.dialogPassword.trim()
                state.dialogLoading = true
                state.dialogMessage = null
                scope.launch {
                    val result = runCatching {
                        val cookie = withContext(Dispatchers.IO) { authClient.login("86", phone, code) }
                        NeteaseSessionStore().acceptAuthenticatedCookie(cookie).getOrThrow()
                        cookie
                    }
                    state.dialogLoading = false
                    result.onSuccess { cookie ->
                        onLoginSuccess(cookie)
                    }.onFailure { error ->
                        state.dialogMessage = error.message ?: "登录失败"
                    }
                }
            }
        } else {
            Text(
                text = "该来源的手机验证码登录尚未接入",
                color = MeloXColors.TextSecondary,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 13.sp,
            )
        }
    }
}

// ── WebView login form ──

@Composable
private fun WebViewLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeloXColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "WebView登录窗口",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击下方按钮打开登录页面",
                    color = MeloXColors.TextTertiary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = provider.color,
            enabled = !state.dialogLoading,
        ) {
                state.dialogLoading = true
                state.dialogMessage = null
                state.dialogLoading = false
                state.dialogMessage = "桌面版没有内置网页登录，请到该来源的官方客户端登录后再粘贴 Cookie"
        }
    }
}

// ── OAuth login form ──

@Composable
private fun OAuthLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeloXColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "即将跳转至${provider.name}授权页面",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = provider.color,
            enabled = !state.dialogLoading,
        ) {
                state.dialogLoading = true
                state.dialogMessage = null
                state.dialogLoading = false
                state.dialogMessage = "桌面版没有${provider.name}的授权跳转，不会假装登录成功"
        }
    }
}

// ── Token input form ──

@Composable
private fun TokenInputForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column {
        Text(
            text = "输入您的Apple Music Token",
            color = MeloXColors.TextSecondary,
            fontFamily = MeloXLanTingProFontFamily,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dialogInput,
            onValueChange = { state.dialogInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Developer Token", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dialogPassword,
            onValueChange = { state.dialogPassword = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("Music User Token", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = provider.color,
            enabled = state.dialogInput.isNotBlank() && !state.dialogLoading,
        ) {
            state.dialogLoading = true
            state.dialogMessage = null
            state.dialogLoading = false
            state.dialogMessage = "该来源的登录尚未接入，不会假装登录成功"
        }
    }
}

// ── Server login form (Jellyfin) ──

@Composable
private fun ServerLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = state.dialogServerUrl,
            onValueChange = { state.dialogServerUrl = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("服务器地址", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dialogInput,
            onValueChange = { state.dialogInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("用户名", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = state.dialogPassword,
            onValueChange = { state.dialogPassword = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("密码", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = provider.color,
            enabled = state.dialogServerUrl.isNotBlank() &&
                    state.dialogInput.isNotBlank() &&
                    state.dialogPassword.isNotBlank() &&
                    !state.dialogLoading,
        ) {
            state.dialogLoading = true
            state.dialogMessage = null
            state.dialogLoading = false
            state.dialogMessage = "Jellyfin 服务器登录尚未接入，不会假装登录成功"
        }
    }
}

// ── Local folder form ──

@Composable
private fun LocalFolderForm(state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MeloXColors.SurfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📁", fontSize = 40.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "选择本地音乐文件夹",
                    color = MeloXColors.TextSecondary,
                    fontFamily = MeloXLanTingProFontFamily,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = Color(0xFF888888),
            enabled = !state.dialogLoading,
        ) {
            state.dialogLoading = true
            state.dialogMessage = null
            CoroutineScope(Dispatchers.IO).launch {
                delay(1000)
                withContext(Dispatchers.Main) {
                    state.dialogLoading = false
                    state.dialogMessage = "本地音乐已启用"
                    onLoginSuccess("本地")
                }
            }
        }
    }
}

// ── Generic login form ──

@Composable
private fun GenericLoginForm(provider: LoginProvider, state: LoginScreenState, onLoginSuccess: (String) -> Unit) {
    Column {
        OutlinedTextField(
            value = state.dialogInput,
            onValueChange = { state.dialogInput = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("用户名 / 手机号", color = MeloXColors.TextTertiary, fontFamily = MeloXLanTingProFontFamily, fontSize = 14.sp)
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = provider.color.copy(alpha = 0.5f),
                unfocusedBorderColor = MeloXColors.Outline,
                focusedContainerColor = MeloXColors.SurfaceVariant,
                unfocusedContainerColor = MeloXColors.SurfaceVariant,
                cursorColor = provider.color,
                focusedTextColor = MeloXColors.TextPrimary,
                unfocusedTextColor = MeloXColors.TextPrimary,
            ),
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = MeloXLanTingProFontFamily,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))
        LoginButton(
            color = provider.color,
            enabled = state.dialogInput.isNotBlank() && !state.dialogLoading,
        ) {
            state.dialogLoading = true
            state.dialogMessage = null
            state.dialogLoading = false
            state.dialogMessage = "该来源的登录尚未接入，不会假装登录成功"
        }
    }
}

// ── Shared login button ──

@Composable
private fun LoginButton(
    color: Color,
    enabled: Boolean,
    text: String = "登录",
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.HAND_CURSOR))),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) color else color.copy(alpha = 0.4f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = Color.White,
                fontFamily = MeloXLanTingProFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// ── Main LoginScreen composable ──

@Composable
fun LoginScreen(navState: MeloXNavState) {
    val state = remember { LoginScreenState() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MeloXColors.Background),
    ) {
        LoginTopBar(onBack = { navState.goBack() })

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(allProviders) { provider ->
                ProviderCard(
                    provider = provider,
                    isLoggedIn = state.isLoggedIn(provider.id),
                    username = state.username(provider.id),
                    onLoginClick = {
                        state.dialogTarget = provider
                        state.dialogInput = ""
                        state.dialogPassword = ""
                        state.dialogServerUrl = ""
                        state.dialogMessage = null
                        state.dialogTab = 0
                    },
                    onLogoutClick = {
                        state.loggedInProviders[provider.id] = false
                        state.usernames.remove(provider.id)
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Login dialog
    state.dialogTarget?.let { provider ->
        LoginDialog(
            provider = provider,
            state = state,
            onDismiss = { state.dialogTarget = null },
            onLoginSuccess = { username ->
                state.loggedInProviders[provider.id] = true
                state.usernames[provider.id] = username
                state.dialogTarget = null
            },
        )
    }
}
