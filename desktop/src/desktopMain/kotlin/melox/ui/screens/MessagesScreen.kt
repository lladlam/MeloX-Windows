package melox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.account.NeteaseSessionStore
import melox.network.MeloXMessageContact
import melox.network.NeteaseMusicOperationsClient
import melox.network.NeteaseSearchClient
import melox.ui.foundation.MeloXIosListRow
import melox.ui.foundation.MeloXIosTopBar
import melox.ui.foundation.MeloXSymbol
import melox.ui.theme.MeloXColors

@Composable
fun MessagesScreen() {
    val loggedIn = remember { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    var contacts by remember { mutableStateOf<List<MeloXMessageContact>>(emptyList()) }
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
    Column(Modifier.fillMaxSize().background(MeloXColors.Background).padding(top = 18.dp)) {
        MeloXIosTopBar(title = "私信")
        when {
            !loggedIn -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("登录网易云音乐后查看联系人", color = MeloXColors.OnSurfaceVariant)
            }
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MeloXColors.Primary)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error.orEmpty(), color = MeloXColors.Error)
            }
            contacts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有联系人", color = MeloXColors.OnSurfaceVariant)
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
                items(contacts, key = { it.id }) { contact ->
                    MeloXIosListRow(
                        title = contact.name,
                        subtitle = contact.latestMessage ?: contact.signature,
                        leadingIcon = MeloXSymbol.Person,
                        showChevron = false,
                    )
                }
            }
        }
    }
}
