package melox.ui.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import melox.account.NeteaseSessionStore
import melox.model.SearchSong
import melox.network.NeteaseMusicOperationsClient
import melox.network.parseNeteaseListenTogetherInvitation
import melox.playback.MeloXListenTogetherCoordinator
import melox.playback.PlaybackCommands
import melox.recognition.SongRecognitionClient
import melox.settings.MeloXSettingsPreferences
import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.MeloXSymbolIcon
import melox.ui.screens.MeloXArtworkImage
import melox.ui.theme.MeloXDesktopTextFontFamily

internal enum class DesktopTool { Recognition, ListenTogether, About, Licenses }
internal object MeloXDesktopToolWindows {
    var active by mutableStateOf<DesktopTool?>(null)
    fun show(tool: DesktopTool) { active = tool }
    fun dismiss() { active = null }
}

@Composable
internal fun MeloXDesktopToolHost(onLogin: () -> Unit) {
    val tool = MeloXDesktopToolWindows.active ?: return
    Dialog(onDismissRequest = MeloXDesktopToolWindows::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalTextStyle provides androidx.compose.ui.text.TextStyle(fontFamily = MeloXDesktopTextFontFamily, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)) {
            Surface(Modifier.size(when (tool) { DesktopTool.Recognition -> 700.dp; DesktopTool.ListenTogether -> 620.dp; DesktopTool.About -> 600.dp; DesktopTool.Licenses -> 680.dp },
                when (tool) { DesktopTool.Recognition -> 660.dp; DesktopTool.ListenTogether -> 620.dp; DesktopTool.About -> 320.dp; DesktopTool.Licenses -> 680.dp })
                .testTag("desktop-tool:${tool.name}"), shape = RoundedCornerShape(10.dp)) {
                if (tool == DesktopTool.About) Box {
                    MeloXDesktopAbout()
                    Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { DesktopDialogButton("ui.common.close", tag = "desktop-about-close", onClick = MeloXDesktopToolWindows::dismiss) }
                } else Column {
                    Row(Modifier.fillMaxWidth().padding(if (tool == DesktopTool.Recognition) 24.dp else 26.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(desktopString(when (tool) { DesktopTool.Recognition -> "ui.recognition.title"; DesktopTool.ListenTogether -> "ui.listen_together.title"; else -> "ui.legal.projects_licenses.title" }), Modifier.weight(1f), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        DesktopDialogButton("ui.common.done", onClick = MeloXDesktopToolWindows::dismiss)
                    }
                    HorizontalDivider(thickness = .5.dp)
                    when (tool) { DesktopTool.Recognition -> DesktopRecognitionContent(); DesktopTool.ListenTogether -> DesktopTogetherContent(onLogin); DesktopTool.Licenses -> MeloXDesktopProjectLicenses(); else -> Unit }
                }
            }
        }
    }
}

/** DesktopSongRecognitionView, backed by the existing recorder and real matching endpoint. */
@Composable
private fun DesktopRecognitionContent() {
    val client = remember { SongRecognitionClient() }
    val scope = rememberCoroutineScope()
    var duration by remember { mutableIntStateOf(MeloXSettingsPreferences.string("recognition_duration", "6").toIntOrNull() ?: 6) }
    var job by remember { mutableStateOf<Job?>(null) }
    var working by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SongRecognitionClient.SongRecognitionResult>>(emptyList()) }
    var selected by remember { mutableStateOf<SearchSong?>(null) }
    var anchor by remember { mutableStateOf<IntOffset?>(null) }
    DisposableEffect(client) { onDispose { job?.cancel(); client.close() } }
    fun start() {
        if (working) return
        working = true; attempted = true; error = null; results = emptyList()
        job = scope.launch {
            try {
                do {
                    val found = client.recognize(if (duration == 0) 9 else duration)
                    results = (found + results).distinctBy { it.song.id }.take(100)
                } while (duration == 0 && isActive)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.unknown") }
            finally { working = false }
        }
    }
    when {
        results.isNotEmpty() -> LazyColumn(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { Text(desktopString("ui.recognition.results"), fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            itemsIndexed(results, key = { _, value -> value.song.id }) { index, value ->
                DesktopCollectionTrackRow(index + 1, value.song, false, false, false,
                    { PlaybackCommands.playQueue(results.map { it.song }, value.song.id) }, { anchor = it; selected = value.song }, showsArtwork = true)
            }
            item { DesktopDialogButton(if (working) "ui.recognition.stop" else "ui.recognition.restart") { if (working) job?.cancel() else start() } }
        }
        else -> Column(Modifier.fillMaxSize().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.weight(1f))
            MeloXSymbolIcon(if (working) MeloXSymbol.Waveform else MeloXSymbol.Mic, size = if (working) 76 else 88, color = Color.Red)
            Text(desktopString(when { working -> "ui.recognition.listening"; error != null -> "ui.recognition.failed"; attempted -> "ui.recognition.no_match"; else -> "ui.desktop.recognition.ready.title" }), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(error ?: if (working) desktopString("ui.desktop.recognition.listening_message") else if (duration == 0) desktopString("ui.desktop.recognition.ready.continuous") else desktopFormat("ui.desktop.recognition.ready.limited", "$duration s"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!working) DesktopSettingsPicker(desktopString("ui.settings.content.recognition_duration"), duration,
                listOf(3, 6, 9, 0).map { it to if (it == 0) desktopString("ui.settings.recognition.duration.continuous") else desktopFormat("ui.common.seconds", it) }, "desktop-recognition-duration") {
                duration = it; MeloXSettingsPreferences.setString("recognition_duration", it.toString())
            }
            DesktopDialogButton(if (working) "ui.recognition.stop" else "ui.recognition.start", tag = "desktop-recognition-start", prominent = true) { if (working) job?.cancel() else start() }
            Spacer(Modifier.weight(1f))
        }
    }
    selected?.let { MeloXDesktopSongMenu(it, { selected = null }, anchor) }
}

/** DesktopListenTogetherView: no account or room is fabricated for the ready state. */
@Composable
private fun DesktopTogetherContent(onLogin: () -> Unit) {
    val state by MeloXListenTogetherCoordinator.state.collectAsState()
    val revision by NeteaseSessionStore.revision.collectAsState()
    val loggedIn = remember(revision) { NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()) }
    val scope = rememberCoroutineScope()
    val ops = remember { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var invitation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmsLeaving by remember { mutableStateOf(false) }
    val queue by PlaybackCommands.queue.collectAsState()
    val song = queue.songs.getOrNull(queue.index)
    LaunchedEffect(Unit) { MeloXListenTogetherCoordinator.ensureStarted() }
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        val cookie = NeteaseSessionStore.readCookie()
        scope.launch {
            try { check(cookie == NeteaseSessionStore.readCookie()); block() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: desktopString("ui.error.netease_operation_incomplete") }
            finally { busy = false }
        }
    }
    val room = state.room
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = if (room == null) 48.dp else 26.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(if (room == null) 24.dp else 22.dp)) {
        if (room == null) {
            item { Box(Modifier.fillMaxWidth().padding(top = 38.dp), contentAlignment = Alignment.Center) { MeloXSymbolIcon(MeloXSymbol.Messages, size = 56, color = Color.Red) } }
            item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(desktopString("ui.listen_together.welcome.title"), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(desktopString("ui.desktop.listen_together.welcome.message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            if (loggedIn) {
                item { DesktopDialogButton("ui.listen_together.start", !busy && song != null, "desktop-together-create", prominent = true) {
                    val cookie = NeteaseSessionStore.readCookie()
                    operation { val created = ops.createListenTogetherRoom(); check(cookie == NeteaseSessionStore.readCookie()); MeloXListenTogetherCoordinator.adoptRoom(created) }
                } }
                if (song == null) item { Text(desktopString("ui.error.listen_together.song_required"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .045f)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(desktopString("ui.listen_together.join_room"), fontWeight = FontWeight.Bold)
                        BasicTextField(invitation, { invitation = it }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("desktop-together-invitation"),
                            textStyle = LocalTextStyle.current, decorationBox = { inner -> Box { if (invitation.isEmpty()) Text(desktopString("ui.listen_together.paste_invitation"), color = MaterialTheme.colorScheme.onSurfaceVariant); inner() } })
                        DesktopDialogButton("ui.listen_together.join_room", !busy && invitation.isNotBlank(), "desktop-together-join") {
                            operation {
                                val cookie = NeteaseSessionStore.readCookie()
                                val parsed = requireNotNull(parseNeteaseListenTogetherInvitation(invitation)) { desktopString("ui.error.listen_together.missing_room_id") }
                                val joined = ops.joinListenTogetherRoom(parsed.roomId, parsed.inviterId)
                                check(cookie == NeteaseSessionStore.readCookie())
                                MeloXListenTogetherCoordinator.adoptRoom(joined)
                            }
                        }
                    }
                }
            } else item { Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(desktopString("ui.listen_together.login_required"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(desktopString("ui.listen_together.login_message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                DesktopDialogButton("ui.common.login", tag = "desktop-together-login") { MeloXDesktopToolWindows.dismiss(); onLogin() }
            } }
        } else {
            item { Text(state.phase.name, fontWeight = FontWeight.Bold) }
            song?.let { current -> item { Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MeloXArtworkImage(current.artworkUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(desktopString("ui.listen_together.syncing")); Text(current.name, fontSize = 20.sp, fontWeight = FontWeight.Bold); Text(current.artists) }
            } } }
            item { Text(desktopString("ui.listen_together.room_members"), fontWeight = FontWeight.Bold) }
            itemsIndexed(room.users, key = { _, user -> user.id }) { _, user -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MeloXArtworkImage(user.avatarUrl, MaterialTheme.colorScheme.surfaceVariant, Modifier.size(40.dp).clip(CircleShape))
                Text(user.name)
                if (user.id == room.creatorId) Text(desktopString("ui.listen_together.host"), color = Color.Red, fontSize = 11.sp)
            } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopDialogButton("ui.listen_together.invite_friends", !busy, "desktop-together-invite", prominent = true) {
                    operation {
                        val url = "https://st.music.163.com/listen-together/share/?songId=${song?.neteaseSongId ?: 0}&roomId=${java.net.URLEncoder.encode(room.id, "UTF-8")}&inviterId=${java.net.URLEncoder.encode(room.creatorId, "UTF-8")}"
                        java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null)
                    }
                }
                DesktopDialogButton("ui.listen_together.leave", !busy, "desktop-together-leave") { confirmsLeaving = true }
            } }
        }
        if (busy) item { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) }
        state.lastError?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
    }
    error?.let { DesktopMessageDialog("ui.listen_together.error.title", it, { error = null }) }
    if (confirmsLeaving && room != null) DesktopMessageDialog("ui.listen_together.leave.confirmation", "", { confirmsLeaving = false },
        confirmKey = "ui.listen_together.leave", onConfirm = { confirmsLeaving = false; operation { ops.endListenTogetherRoom(room.id); MeloXListenTogetherCoordinator.clearRoom() } })
}
