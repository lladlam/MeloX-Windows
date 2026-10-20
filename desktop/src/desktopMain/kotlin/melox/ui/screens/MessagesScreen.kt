package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.network.MeloXMessageContact
import melox.network.MeloXPrivateMessage
import melox.network.NeteaseMusicOperationsClient
import melox.network.NeteaseSearchClient
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXSymbol
import melox.ui.glass.MeloXGlassTextField
import melox.ui.theme.MeloXColors
import melox.settings.MeloXSettingsRuntime

@Composable
fun MessagesScreen(onBack: (() -> Unit)? = null) {
    val loggedIn = remember { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    var contacts by remember { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var selected by remember { mutableStateOf<MeloXMessageContact?>(null) }
    var loading by remember { mutableStateOf(loggedIn) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(loggedIn) {
        if (!loggedIn) return@LaunchedEffect
        runCatching {
            val profile = NeteaseSearchClient(cookieProvider = { NeteaseSessionStore.readCookie() }).accountProfile()
            NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }).messageContacts(profile.userId)
        }.onSuccess { contacts = it }
            .onFailure { error = it.message ?: "私信加载失败" }
        loading = false
    }
    val open = selected
    if (MeloXSettingsRuntime.desktopLayoutEnabled) {
        Row(Modifier.fillMaxSize().background(MeloXColors.pageBackground()).testTag("desktop-messages")) {
            Column(Modifier.width(286.dp).fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = .68f))) {
                MeloXIosTopBar(title = "私信", onBack = onBack)
                when {
                    !loggedIn -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("登录网易云音乐后查看联系人", color = MeloXColors.pageOnSurfaceVariant())
                    }
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MeloXColors.Primary) }
                    error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(error.orEmpty(), color = MeloXColors.Error) }
                    contacts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有联系人", color = MeloXColors.pageOnSurfaceVariant()) }
                    else -> LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                        items(contacts, key = { it.id }) { contact ->
                            Box(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                MeloXIosListRow(
                                    title = contact.name,
                                    subtitle = contact.latestMessage ?: contact.signature,
                                    leadingIcon = MeloXSymbol.Person,
                                    showChevron = false,
                                    modifier = Modifier.background(if (open?.id == contact.id) androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Transparent),
                                    onClick = { selected = contact },
                                )
                            }
                        }
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)))
            if (open != null) Box(Modifier.weight(1f).fillMaxSize()) { MessageThread(contact = open, onBack = { selected = null }, desktop = true) }
            else Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("选择一个会话", color = MeloXColors.pageOnSurfaceVariant(), fontSize = 15.sp)
            }
        }
        return
    }
    if (open != null) {
        MessageThread(contact = open, onBack = { selected = null })
        return
    }
    Column(Modifier.fillMaxSize().background(MeloXColors.pageBackground()).padding(top = 18.dp)) {
        MeloXIosTopBar(title = "私信", onBack = onBack)
        when {
            !loggedIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("登录网易云音乐后查看联系人", color = MeloXColors.pageOnSurfaceVariant())
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MeloXColors.Primary)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error.orEmpty(), color = MeloXColors.Error)
            }
            contacts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有联系人", color = MeloXColors.pageOnSurfaceVariant())
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(contacts, key = { it.id }) { contact ->
                    MeloXIosListRow(
                        title = contact.name,
                        subtitle = contact.latestMessage ?: contact.signature,
                        leadingIcon = MeloXSymbol.Person,
                        onClick = { selected = contact },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageThread(contact: MeloXMessageContact, onBack: () -> Unit, desktop: Boolean = false) {
    val ops = remember { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    var history by remember(contact.id) { mutableStateOf<List<MeloXPrivateMessage>>(emptyList()) }
    var draft by remember(contact.id) { mutableStateOf("") }
    var error by remember(contact.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(contact.id) {
        runCatching { ops.privateMessageHistory(contact.id) }
            .onSuccess { history = it }
            .onFailure { error = it.message ?: "会话加载失败" }
    }
    Column(Modifier.fillMaxSize().background(MeloXColors.pageBackground()).padding(top = if (desktop) 0.dp else 18.dp)) {
        MeloXIosTopBar(title = contact.name, onBack = onBack)
        error?.let {
            Text(it, color = MeloXColors.Error, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = if (desktop) 34.dp else 20.dp, vertical = 12.dp)) {
            items(history, key = { it.id }) { message ->
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        message.text.ifBlank { message.resource?.title.orEmpty() },
                        color = MeloXColors.pageOnSurface(),
                        fontSize = if (desktop) 13.5.sp else 15.sp,
                        modifier = Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = if (desktop) 34.dp else 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            MeloXGlassTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("发送私信", color = MeloXColors.pageOnSurfaceVariant()) },
            )
            Text(
                "发送",
                color = MeloXColors.Primary,
                modifier = Modifier.padding(start = 12.dp).clickable(enabled = draft.isNotBlank()) {
                    val text = draft.trim()
                    if (text.isBlank()) return@clickable
                    scope.launch {
                        runCatching { ops.sendPrivateText(text, contact.id) }
                            .onSuccess {
                                draft = ""
                                history = runCatching { ops.privateMessageHistory(contact.id) }.getOrDefault(history)
                            }
                            .onFailure { error = it.message ?: "发送失败" }
                    }
                },
            )
        }
    }
}
