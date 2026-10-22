package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import melox.ui.input.BasicTextField
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.account.rememberNeteaseSessionStore
import melox.network.MeloXMessageContact
import melox.network.MeloXPrivateMessage
import melox.network.MeloXPrivateMessageResource
import melox.network.MeloXPrivateMessageResourceKind
import melox.network.NeteaseMusicOperationsClient
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.navigation.MeloXCollectionLaunchBus
import melox.ui.navigation.MeloXCollectionTarget
import melox.ui.theme.MeloXDesktopTextFontFamily
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class DesktopMessagePhase { Loading, Loaded, Failed }

/** DesktopMessagesView: 270–410pt conversation list, 64pt thread header, real send/history. */
@Composable
internal fun MeloXDesktopMessages(onLogin: () -> Unit) {
    val session = rememberNeteaseSessionStore()
    val revision by NeteaseSessionStore.revision.collectAsState()
    val loggedIn = remember(revision) { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    val ops = remember(revision) { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    val scope = rememberCoroutineScope()
    val conversationsScroll = rememberLazyListState()
    var conversations by remember(revision) { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
    var selected by remember { mutableStateOf<MeloXMessageContact?>(null) }
    var phase by remember(revision) { mutableStateOf(DesktopMessagePhase.Loading) }
    var error by remember(revision) { mutableStateOf<String?>(null) }
    var composing by remember { mutableStateOf(false) }
    val userId = session.profile?.userId ?: 0L

    suspend fun load() {
        if (!loggedIn) { conversations = emptyList(); phase = DesktopMessagePhase.Loaded; return }
        phase = DesktopMessagePhase.Loading; error = null
        try {
            conversations = ops.privateMessageConversations(userId)
            phase = DesktopMessagePhase.Loaded
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.desktop.messages.load_failed"); phase = DesktopMessagePhase.Failed }
    }
    LaunchedEffect(revision, loggedIn, userId) { load() }
    CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily,
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
        if (!loggedIn) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-messages-login"),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
                MeloXSymbolIcon(MeloXSymbol.Messages, size = 52, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f))
                Text(desktopString("ui.account.login_required"), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(desktopString("ui.desktop.messages.login_message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopDialogButton("ui.common.login", prominent = true, onClick = onLogin)
            }
            return@CompositionLocalProvider
        }
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("desktop-messages")) {
            Column(Modifier.width(330.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surface.copy(alpha = .55f)).testTag("desktop-message-list")) {
                Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(desktopString("ui.desktop.messages.conversations"), Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(conversations.size.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    DesktopIconButton(MeloXSymbol.Refresh, "desktop-messages-refresh", enabled = phase != DesktopMessagePhase.Loading) { scope.launch { load() } }
                    DesktopIconButton(MeloXSymbol.Badge, "desktop-messages-compose") { composing = true }
                }
                HorizontalDivider(thickness = .5.dp)
                Box(Modifier.weight(1f)) {
                    when {
                        phase == DesktopMessagePhase.Loading && conversations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                        error != null && conversations.isEmpty() -> DesktopMessageUnavailable(
                            desktopString("ui.desktop.messages.load_failed"), error!!, onRetry = { scope.launch { load() } })
                        conversations.isEmpty() -> DesktopMessageUnavailable(
                            desktopString("ui.messages.no_messages"), desktopString("ui.messages.no_messages.message"))
                        else -> LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(conversationsScroll), state = conversationsScroll) {
                            items(conversations, key = { it.id }) { contact ->
                                DesktopConversationRow(contact, contact.id == selected?.id) {
                                    selected = contact.copy(unreadCount = 0)
                                    val index = conversations.indexOfFirst { it.id == contact.id }
                                    if (index >= 0) conversations = conversations.toMutableList().also { it[index] = it[index].copy(unreadCount = 0) }
                                }
                            }
                        }
                    }
                }
            }
            Box(Modifier.width(.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            if (selected != null) {
                DesktopConversationPane(requireNotNull(selected), userId, ops, onBack = { selected = null })
            } else {
                DesktopMessageUnavailable(desktopString("ui.desktop.messages.select_conversation"),
                    desktopString("ui.desktop.messages.select_conversation.message"))
            }
        }
    }
    if (composing) DesktopNewMessageSheet(conversations, { composing = false }) { contact ->
        composing = false
        selected = contact
    }
}

@Composable
private fun DesktopIconButton(symbol: MeloXSymbol, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .testTag(tag), contentAlignment = Alignment.Center) {
        MeloXSymbolIcon(symbol, size = 14, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) .8f else .3f))
    }
}

@Composable
private fun DesktopConversationRow(contact: MeloXMessageContact, active: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)
        .background(if (active) Color(0xFF0A84FF).copy(alpha = .14f) else Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 8.dp).testTag("desktop-message-contact:${contact.id}"),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        melox.ui.screens.MeloXArtworkImage(contact.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(44.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.name, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (contact.latestMessageTimeMs > 0) Text(messageTime(contact.latestMessageTimeMs), fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(contact.latestMessage ?: contact.signature, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (contact.unreadCount > 0) Box(Modifier.clip(CircleShape).background(Color(0xFFFF3B30))
                    .padding(horizontal = 6.dp, vertical = 2.dp)) {
                    Text(contact.unreadCount.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DesktopConversationPane(contact: MeloXMessageContact, userId: Long, ops: NeteaseMusicOperationsClient, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var messages by remember(contact.id) { mutableStateOf<List<MeloXPrivateMessage>>(emptyList()) }
    var draft by remember(contact.id) { mutableStateOf("") }
    var loading by remember(contact.id) { mutableStateOf(true) }
    var error by remember(contact.id) { mutableStateOf<String?>(null) }
    var sending by remember(contact.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()
    suspend fun load() {
        loading = true; error = null
        try { messages = ops.privateMessageHistory(contact.id); loading = false }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { error = failure.message ?: desktopString("ui.desktop.messages.conversation_failed"); loading = false }
    }
    LaunchedEffect(contact.id) { load() }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || sending) return
        sending = true; error = null
        scope.launch {
            try { ops.sendPrivateText(text, contact.id); draft = ""; load() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.messages.send_failed") }
            finally { sending = false }
        }
    }
    Column(Modifier.fillMaxSize().testTag("desktop-message-thread")) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            melox.ui.screens.MeloXArtworkImage(contact.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(38.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(contact.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                if (contact.signature.isNotBlank()) Text(contact.signature, fontSize = 11.sp, maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DesktopIconButton(MeloXSymbol.Refresh, "desktop-message-thread-refresh", !loading) { scope.launch { load() } }
        }
        HorizontalDivider(thickness = .5.dp)
        Box(Modifier.weight(1f)) {
            when {
                loading && messages.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                error != null && messages.isEmpty() -> DesktopMessageUnavailable(
                    desktopString("ui.desktop.messages.conversation_failed"), error!!, onRetry = { scope.launch { load() } })
                else -> LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(listState), state = listState, contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(messages, key = { it.id }) { message -> DesktopMessageBubble(message, message.fromUserId == userId) }
                }
            }
        }
        HorizontalDivider(thickness = .5.dp)
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BasicTextField(draft, { draft = it }, modifier = Modifier.weight(1f).heightIn(min = 38.dp, max = 110.dp)
                .clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .055f))
                .padding(10.dp).testTag("desktop-message-input"),
                textStyle = LocalTextStyle.current,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                decorationBox = { inner -> Box { if (draft.isEmpty()) Text(desktopFormat("ui.desktop.messages.input_to", contact.name),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f)); inner() } })
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (draft.isNotBlank() && !sending) Color(0xFFFF3B30) else MaterialTheme.colorScheme.onSurface.copy(alpha = .12f))
                .clickable(enabled = draft.isNotBlank() && !sending, onClick = ::send).testTag("desktop-message-send"), contentAlignment = Alignment.Center) {
                if (sending) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                else MeloXSymbolIcon(MeloXSymbol.Send, size = 14, color = Color.White)
            }
        }
        error?.takeIf { messages.isNotEmpty() }?.let { Text(it, Modifier.padding(horizontal = 18.dp, vertical = 4.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    }
}

@Composable
private fun DesktopMessageBubble(message: MeloXPrivateMessage, outgoing: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        if (outgoing) Spacer(Modifier.weight(.16f))
        Column(Modifier.weight(.84f, fill = false).widthIn(max = 460.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            message.resource?.let { resource -> DesktopMessageResource(resource) }
            if (message.text.isNotBlank()) {
                SelectionText(message.text, Modifier.clip(RoundedCornerShape(13.dp))
                    .background(if (outgoing) Color(0xFF0A84FF).copy(alpha = .18f) else MaterialTheme.colorScheme.onSurface.copy(alpha = .09f))
                    .padding(horizontal = 12.dp, vertical = 9.dp))
            }
            Text(messageTime(message.timeMs, timeOnly = true), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
        }
        if (!outgoing) Spacer(Modifier.weight(.16f))
    }
}

@Composable
private fun SelectionText(value: String, modifier: Modifier) {
    androidx.compose.foundation.text.selection.SelectionContainer(modifier) {
        Text(value, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun DesktopMessageResource(resource: MeloXPrivateMessageResource) {
    Row(Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .05f))
        .clickable {
            when (resource.kind) {
                MeloXPrivateMessageResourceKind.Song -> MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Song(resource.id))
                MeloXPrivateMessageResourceKind.Album -> MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Album(resource.id, resource.title))
                MeloXPrivateMessageResourceKind.Playlist -> MeloXCollectionLaunchBus.open(MeloXCollectionTarget.Playlist(resource.id, resource.title))
            }
        }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        melox.ui.screens.MeloXArtworkImage(resource.artworkUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(desktopString("ui.desktop.messages.resource.${resource.kind.name.lowercase()}"), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(resource.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DesktopNewMessageSheet(contacts: List<MeloXMessageContact>, onDismiss: () -> Unit, onPick: (MeloXMessageContact) -> Unit) {
    var query by remember { mutableStateOf("") }
    val scroll = rememberLazyListState()
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.size(420.dp, 520.dp).testTag("desktop-message-compose"), shape = RoundedCornerShape(10.dp)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(desktopString("ui.messages.start_private_message"), Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    DesktopDialogButton("ui.common.cancel", onClick = onDismiss)
                }
                HorizontalDivider(thickness = .5.dp)
                BasicTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(14.dp)
                    .clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .06f))
                    .padding(horizontal = 10.dp, vertical = 8.dp).testTag("desktop-message-compose-search"),
                    textStyle = LocalTextStyle.current,
                    decorationBox = { inner -> Box { if (query.isEmpty()) Text(desktopString("ui.messages.search_following"),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f)); inner() } })
                val filtered = remember(query, contacts) {
                    if (query.isBlank()) contacts else contacts.filter { it.name.contains(query.trim(), ignoreCase = true) }
                }
                if (filtered.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(desktopString("ui.messages.no_messages"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else LazyColumn(Modifier.fillMaxSize().desktopPromotedDragScroll(scroll), state = scroll) {
                    items(filtered, key = { it.id }) { contact ->
                        DesktopConversationRow(contact, false) { onPick(contact) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopMessageUnavailable(title: String, message: String, onRetry: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically)) {
        MeloXSymbolIcon(MeloXSymbol.Messages, size = 44, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) DesktopDialogButton("ui.common.retry", onClick = onRetry)
    }
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private fun messageTime(milliseconds: Long, timeOnly: Boolean = false): String {
    val instant = Instant.ofEpochMilli(milliseconds)
    val date = instant.atZone(ZoneId.systemDefault())
    val today = java.time.LocalDate.now()
    return when {
        timeOnly || date.toLocalDate() == today -> date.format(timeFormatter)
        else -> date.format(dateFormatter)
    }
}
