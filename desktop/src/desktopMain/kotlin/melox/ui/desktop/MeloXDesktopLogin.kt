package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.account.NeteaseOfficialCookieReader
import melox.account.NeteaseOfficialProfile
import melox.account.NeteaseSessionStore
import melox.network.NeteasePhoneSignInFacade
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.legal.pinnedString
import melox.ui.theme.MeloXDesktopTextFontFamily

private enum class DesktopLoginMethod { Phone, ClientCookie }

/**
 * Windows equivalent of DesktopLoginView's 880x680 direct-connection sheet.
 * macOS embeds WKWebView; Windows has no shipped browser engine, so this sheet
 * uses the real NetEase SMS login or the official client's encrypted cookie.
 */
@Composable
internal fun MeloXDesktopLogin(onCancel: () -> Unit, onLoggedIn: (String) -> Unit) {
    var method by remember { mutableStateOf(DesktopLoginMethod.Phone) }
    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf<List<NeteaseOfficialProfile>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val auth = remember { NeteasePhoneSignInFacade() }
    val reader = remember { NeteaseOfficialCookieReader() }

    suspend fun complete(cookie: String) {
        val account = NeteaseSessionStore().acceptAuthenticatedCookie(cookie).getOrThrow()
        onLoggedIn(account.nickname)
    }
    Dialog(onDismissRequest = { if (!busy) onCancel() },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Surface(Modifier.size((880.dp).coerceAtMost(maxWidth), (680.dp).coerceAtMost(maxHeight))
            .testTag("desktop-login"), shape = RoundedCornerShape(10.dp)) {
            CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(
                fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
                Column {
                    Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                        DesktopSettingsButton(desktopString("ui.common.cancel"), Modifier.testTag("desktop-login-cancel"), onClick = onCancel)
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(desktopString("ui.account.login_netease"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(desktopString("ui.desktop.login.direct_connection"), fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                    HorizontalDivider(thickness = .5.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.Center) {
                        Row(Modifier.width(360.dp).height(28.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .07f)).padding(2.dp)) {
                            listOf(DesktopLoginMethod.Phone to desktopPlatformString("login_phone"),
                                DesktopLoginMethod.ClientCookie to desktopPlatformString("login_client_cookie")).forEach { (value, key) ->
                                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                    .background(if (method == value) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable(enabled = !busy) { method = value; message = null }
                                    .testTag("desktop-login-method:${value.name}"), contentAlignment = Alignment.Center) {
                                    Text(key, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                    Box(Modifier.fillMaxSize().padding(horizontal = 120.dp, vertical = 34.dp)) {
                        if (method == DesktopLoginMethod.Phone) {
                            Column(Modifier.fillMaxWidth().widthIn(max = 420.dp).align(Alignment.TopCenter),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MaloXSymbolCentered(MeloXSymbol.Person, 54)
                                Text(pinnedString("account_netease_phone_desc"), Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                DesktopLoginField(phone, { phone = it.filter(Char::isDigit) }, pinnedString("account_phone"),
                                    "desktop-login-phone", enabled = !busy)
                                DesktopLoginField(code, { code = it.filter(Char::isDigit) }, pinnedString("account_code"),
                                    "desktop-login-code", enabled = !busy, password = true,
                                    onDone = {
                                        if (phone.isNotBlank() && code.isNotBlank()) scope.launch {
                                            busy = true; message = null
                                            try { complete(auth.login(phone, code)) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (failure: Exception) { message = failure.message ?: failure.javaClass.simpleName }
                                            finally { busy = false }
                                        }
                                    })
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    DesktopSettingsButton(desktopPlatformString("login_send_code"), Modifier.weight(1f).testTag("desktop-login-send"),
                                        enabled = !busy && phone.length >= 6) {
                                        scope.launch {
                                            message = null
                                            try { auth.sendCode(phone); message = pinnedString("account_code_sent", "+86", phone.trim()) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (failure: Exception) { message = failure.message ?: failure.javaClass.simpleName }
                                        }
                                    }
                                    DesktopSettingsButton(desktopString("ui.common.login"), Modifier.weight(1f).testTag("desktop-login-submit"),
                                        enabled = !busy && phone.isNotBlank() && code.isNotBlank()) {
                                        scope.launch {
                                            busy = true; message = null
                                            try { complete(auth.login(phone, code)) }
                                            catch (cancelled: CancellationException) { throw cancelled }
                                            catch (failure: Exception) { message = failure.message ?: failure.javaClass.simpleName }
                                            finally { busy = false }
                                        }
                                    }
                                }
                                message?.let { Text(it, Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth().widthIn(max = 460.dp).align(Alignment.TopCenter),
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                MaloXSymbolCentered(MeloXSymbol.Drive, 54)
                                Text(if (reader.isSupported) desktopPlatformString("login_client_message")
                                    else desktopPlatformString("login_client_windows_only"), Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                profiles.forEach { profile ->
                                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
                                        .clickable(enabled = !busy) { selectedProfile = profile.name }
                                        .padding(12.dp).testTag("desktop-login-profile:${profile.name}"),
                                        verticalAlignment = Alignment.CenterVertically) {
                                        RadioButton(selected = selectedProfile == profile.name, enabled = !busy,
                                            onClick = { selectedProfile = profile.name })
                                        Text(profile.name, Modifier.padding(start = 8.dp))
                                    }
                                }
                                DesktopSettingsButton(if (profiles.isEmpty()) desktopPlatformString("login_find_client")
                                    else desktopPlatformString("login_import_client"),
                                    Modifier.fillMaxWidth().testTag("desktop-login-import"),
                                    enabled = !busy && reader.isSupported) {
                                    scope.launch {
                                        busy = true; message = null
                                        try {
                                            val available = withContext(Dispatchers.IO) { reader.findProfiles() }
                                            if (available.isEmpty()) {
                                                message = desktopPlatformString("login_client_not_found")
                                            } else {
                                                profiles = available
                                                val target = available.firstOrNull { it.name == selectedProfile }
                                                    ?: available.singleOrNull()
                                                if (target == null) message = desktopPlatformString("login_choose_profile")
                                                else complete(withContext(Dispatchers.IO) { reader.readCookie(target) })
                                            }
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (failure: Exception) { message = failure.message ?: failure.javaClass.simpleName }
                                        finally { busy = false }
                                    }
                                }
                                message?.let { Text(it, Modifier.align(Alignment.CenterHorizontally),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MaloXSymbolCentered(symbol: MeloXSymbol, size: Int) {
    Box(Modifier.fillMaxWidth().padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = size, color = Color(0xFFFF3B30).copy(alpha = .75f))
    }
}

@Composable
private fun DesktopLoginField(value: String, onChange: (String) -> Unit, placeholder: String, tag: String,
    enabled: Boolean, password: Boolean = false, onDone: () -> Unit = {}) {
    BasicTextField(value, onChange, enabled = enabled, singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        textStyle = LocalTextStyle.current,
        modifier = Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f))
            .border(.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .18f), RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp).testTag(tag),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) Text(placeholder, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
                inner()
            }
        })
}
