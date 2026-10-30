package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import melox.settings.*
import melox.music.model.MusicSource
import melox.ui.glass.MeloXGlassTextField
import melox.ui.legal.pinnedString
import melox.account.NeteaseSessionStore
import melox.network.NeteaseMusicOperationsClient
import melox.network.parseNeteaseListenTogetherInvitation
import melox.playback.MeloXListenTogetherCoordinator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

/** Source layout retained; unavailable OS adapters are disabled instead of stored no-op enables. */
@Composable
internal fun SystemPlaybackSettings() {
    SettingsGlassGroup {
        SettingsToggleRow("settings_system_lyrics", "system_lyrics_enabled", false, enabled = false)
        SettingsChoice("settings_system_title_format", "system_lyrics_title_mode", MeloXSettingsRuntime.systemLyricTitleMode,
            listOf(MeloXSystemLyricTitleMode.LyricFirst to pinnedString("settings_system_title_lyric"),
                MeloXSystemLyricTitleMode.SongFirst to pinnedString("settings_system_title_song")), enabled = false) {}
        SettingsToggleRow("settings_lyric_notification", "lyrics_notifications_enabled", false, enabled = false)
        SettingsToggleRow("settings_hyperos_island", "hyperos_super_island_enabled", false, enabled = false)
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        listOf("next_line" to false, "progress" to true, "artwork" to true, "background_only" to false, "dismiss_paused" to true).forEach { (key, value) ->
            SettingsToggleRow("settings_notify_$key", "lyrics_notification_$key", value, enabled = false)
        }
    }
    listOf(Triple("title_template", "title_template", "{lyric}"), Triple("subtitle_template", "subtitle_template", "{song} · {artist}"),
        Triple("fallback", "fallback", "{song} · {artist}")).forEach { (title, key, fallback) ->
        Text(pinnedString("settings_notify_$title"), Modifier.padding(top = 8.dp, bottom = 6.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f))
        MeloXGlassTextField(MeloXSettingsPreferences.string("lyrics_notification_$key", fallback), onValueChange = {}, enabled = false,
            modifier = Modifier.fillMaxWidth().testTag("lyrics_notification_$key"))
        Spacer(Modifier.height(6.dp))
    }
    Text(pinnedString("settings_notify_variables"), Modifier.padding(bottom = 10.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f))
    SettingsActionButton(pinnedString("settings_notify_test"), Modifier.testTag("notification-test"), enabled = false) {}
    SettingsPlatformNote(pinnedString("settings_route_system_playback"))
}

@Composable
internal fun FloatingLyricsSettings() {
    SettingsGlassGroup {
        SettingsExternalToggleRow(pinnedString("settings_floating_show"), MeloXSettingsRuntime.floatingLyricsEnabled,
            "setting-floating_lyrics_enabled") { MeloXSettingsPreferences.setBoolean("floating_lyrics_enabled", it) }
        SettingsChoice("settings_floating_secondary", "floating_lyrics_secondary_mode", MeloXSettingsRuntime.floatingSecondaryMode,
            listOf("Auto" to "secondary_auto", "Translation" to "translation", "Romanization" to "romanization", "NextLine" to "next_line", "Hidden" to "hidden")
                .map { (key, label) -> MeloXSecondaryLyricMode.valueOf(key) to pinnedString("settings_floating_$label") }) {
            MeloXSettingsPreferences.setString("floating_lyrics_secondary_mode", it.name)
        }
        SettingsChoice("settings_floating_font_size", "floating_lyrics_font_size", MeloXSettingsRuntime.floatingFontSizeSp,
            listOf(14, 16, 18, 20, 24, 28).map { it to pinnedString("settings_unit_sp", it) }) {
            MeloXSettingsPreferences.setInt("floating_lyrics_font_size", it)
        }
        SettingsExternalToggleRow(pinnedString("settings_floating_contrast"), MeloXSettingsRuntime.floatingHighContrast,
            "setting-floating_lyrics_high_contrast") { MeloXSettingsPreferences.setBoolean("floating_lyrics_high_contrast", it) }
    }
}

@Composable
internal fun RecognitionSettings() {
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.DesktopSettingsButton(melox.ui.desktop.desktopString("ui.recognition.title"), Modifier.testTag("recognition-start")) {
            melox.ui.desktop.MeloXDesktopToolWindows.show(melox.ui.desktop.DesktopTool.Recognition)
        }
        return
    }
    val client = remember { melox.recognition.SongRecognitionClient() }
    val scope = rememberCoroutineScope()
    var duration by remember { mutableStateOf(MeloXSettingsPreferences.string("recognition_duration", "6").toIntOrNull() ?: 6) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(pinnedString("settings_recognition_ready")) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<melox.recognition.SongRecognitionClient.SongRecognitionResult>>(emptyList()) }
    var job by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(client) { onDispose { job?.cancel(); client.close() } }
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        listOf(3 to "fast", 6 to "recommended", 9 to "noisy").forEach { (value, label) ->
            SettingsChoiceRow(pinnedString("settings_recognition_seconds_$label"), duration == value, "recognition-duration-$value", enabled = !working) {
                duration = value
                MeloXSettingsPreferences.setString("recognition_duration", value.toString())
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    SettingsActionButton(pinnedString(if (working) "settings_recognition_stop" else "settings_recognition_start"), Modifier.testTag("recognition-start")) {
        if (working) job?.cancel() else {
            working = true; error = null
            if (duration != 0) results = emptyList()
            job = scope.launch {
                try {
                    if (duration == 0) {
                        status = pinnedString("settings_recognition_continuous")
                        while (isActive) {
                            val found = client.recognize(9)
                            results = (found + results).distinctBy { it.song.id }.take(100)
                        }
                    } else {
                        status = pinnedString("settings_recognition_listening", duration)
                        results = client.recognize(duration)
                        status = pinnedString(if (results.isEmpty()) "settings_recognition_none" else "settings_recognition_done")
                    }
                } catch (cancelled: CancellationException) {
                    status = pinnedString(if (results.isEmpty()) "settings_recognition_stopped" else "settings_recognition_stopped_kept")
                    throw cancelled
                } catch (failure: Exception) {
                    error = failure.message ?: pinnedString("settings_recognition_failed")
                    status = pinnedString("settings_recognition_incomplete")
                } finally { working = false }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (working) {
            androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
        }
        Text(status, Modifier.testTag("recognition-status"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f), fontSize = 13.sp)
    }
    error?.let { Text(it, Modifier.testTag("recognition-error"), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
    results.forEach { result ->
        melox.ui.foundation.MeloXIosListRow(title = result.song.name, subtitle = result.song.artists,
            modifier = Modifier.testTag("recognition-result-${result.song.id}"), onClick = {
                melox.playback.PlaybackCommands.playQueue(results.map { it.song }, result.song.id)
            })
    }
}

@Composable
internal fun ListenTogetherSettings() {
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.DesktopSettingsButton(melox.ui.desktop.desktopString("ui.listen_together.title"), Modifier.testTag("listen-together-open")) {
            melox.ui.desktop.MeloXDesktopToolWindows.show(melox.ui.desktop.DesktopTool.ListenTogether)
        }
        return
    }
    val state by MeloXListenTogetherCoordinator.state.collectAsState()
    val scope = rememberCoroutineScope()
    val ops = remember { NeteaseMusicOperationsClient(cookieProvider = { NeteaseSessionStore.readCookie() }) }
    var invitation by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { MeloXListenTogetherCoordinator.ensureStarted() }
    fun request(fallback: String, block: suspend (String) -> Unit) {
        if (busy) return
        busy = true; message = null
        val cookie = NeteaseSessionStore.readCookie()
        scope.launch {
            try { block(cookie) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: pinnedString(fallback) }
            finally { busy = false }
        }
    }
    SettingsInfoCard(pinnedString("settings_listen_info"))
    Spacer(Modifier.height(12.dp))
    val room = state.room
    if (room == null) {
        SettingsActionButton(pinnedString("settings_listen_start"), Modifier.testTag("listen-together-start"), enabled = !busy) {
            request("settings_listen_create_failed") { cookie ->
                val created = ops.createListenTogetherRoom()
                check(cookie == NeteaseSessionStore.readCookie()) { "账号已切换，请重新连接" }
                MeloXListenTogetherCoordinator.adoptRoom(created)
            }
        }
        Spacer(Modifier.height(10.dp))
        MeloXGlassTextField(invitation, onValueChange = { invitation = it }, enabled = !busy,
            modifier = Modifier.fillMaxWidth().testTag("listen-together-invitation"),
            placeholder = { Text(pinnedString("settings_listen_paste"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f)) })
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_listen_join"), Modifier.testTag("listen-together-join"), enabled = !busy) {
            val parsed = parseNeteaseListenTogetherInvitation(invitation)
            if (parsed == null) message = pinnedString("settings_listen_invite_invalid")
            else request("settings_listen_join_failed") { cookie ->
                val joined = ops.joinListenTogetherRoom(parsed.roomId, parsed.inviterId)
                check(cookie == NeteaseSessionStore.readCookie()) { "账号已切换，请重新连接" }
                MeloXListenTogetherCoordinator.adoptRoom(joined)
            }
        }
    } else {
        SettingsGlassGroup {
            melox.ui.foundation.MeloXIosListRow(title = pinnedString("settings_listen_room"), detail = room.id)
            melox.ui.foundation.MeloXIosListRow(title = pinnedString("settings_listen_members"), detail = pinnedString("settings_listen_member_count", room.users.size.coerceAtLeast(1)))
            room.users.forEach { melox.ui.foundation.MeloXIosListRow(title = it.name) }
            melox.ui.foundation.MeloXIosListRow(title = pinnedString("settings_listen_connection"), detail = pinnedString(when (state.phase) {
                MeloXListenTogetherCoordinator.Phase.Connected -> "settings_listen_synced"
                MeloXListenTogetherCoordinator.Phase.Reconnecting -> "settings_listen_reconnecting"
                MeloXListenTogetherCoordinator.Phase.Idle -> "settings_listen_resuming"
            }))
        }
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_listen_share"), Modifier.testTag("listen-together-share")) {
            try {
                val inviter = room.creatorId.ifBlank { room.users.firstOrNull()?.id.orEmpty() }
                val player = melox.playback.PlaybackCommands.playerController()
                val songId = player?.snapshot()?.getOrNull(player.currentMediaItemIndex)?.neteaseSongId ?: 0L
                val url = "https://st.music.163.com/listen-together/share/?songId=$songId&roomId=${java.net.URLEncoder.encode(room.id, "UTF-8")}&inviterId=${java.net.URLEncoder.encode(inviter, "UTF-8")}"
                java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(java.awt.datatransfer.StringSelection(url), null)
                message = "邀请链接已复制"
            } catch (failure: Exception) { message = failure.message ?: "分享失败" }
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton(pinnedString("settings_listen_end"), Modifier.testTag("listen-together-end"), enabled = !busy) {
            request("settings_listen_leave_failed") { cookie ->
                ops.endListenTogetherRoom(room.id)
                if (cookie == NeteaseSessionStore.readCookie()) MeloXListenTogetherCoordinator.clearRoom()
            }
        }
    }
    if (busy) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
    }
    (message ?: state.lastError)?.let { Text(it, Modifier.testTag("listen-together-message").padding(top = 12.dp), color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
}

@Composable
internal fun DeveloperSettings() {
    SettingsGlassGroup {
        SettingsExternalToggleRow("性能浮层", MeloXSettingsRuntime.performanceOverlayEnabled, "setting-developer_performance_overlay",
            subtitle = "显示应用帧时与 JVM 堆占用") {
            MeloXSettingsPreferences.setBoolean("developer_performance_overlay", it)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
internal fun ExperimentalSettings(source: MusicSource) {
    var confirm by remember { mutableStateOf<String?>(null) }
    AutoSyncSettings()
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup {
        SettingsExternalToggleRow("CLI 模式", melox.cli.DesktopCliRuntime.enabled, "setting-experimental_cli_enabled",
            subtitle = "允许当前电脑上的命令行查询状态、控制播放和搜索歌曲") {
            melox.cli.DesktopCliRuntime.setRequested(it)
        }
    }
    if (melox.cli.DesktopCliRuntime.enabled) SettingsInfoCard("运行：python melox_cli.py status\n支持 play / pause / next / previous / seek / queue / search / search-play。")
    melox.cli.DesktopCliRuntime.error?.let { Text(it, Modifier.testTag("cli-error").padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error) }
    Spacer(Modifier.height(10.dp))
    if (source == MusicSource.Bilibili) {
        SettingsGlassGroup { SettingsToggleRow("settings_bilibili_align", "bilibili_lyric_audio_alignment", false) }
        SettingsInfoCard(pinnedString("settings_bilibili_align_warning"))
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_bilibili_clear"), Modifier.testTag("bilibili-associations-clear")) { confirm = "bilibili" }
        Spacer(Modifier.height(10.dp))
    }
    val store = melox.music.provider.PlaybackAccountStore
    SettingsGlassGroup {
        // Separate playback credentials exist, but desktop login screens do not yet accept the playback slot.
        SettingsExternalToggleRow(pinnedString("settings_playback_second_account"), store.isEnabled(), "playback-second-account", enabled = false) {}
    }
    SettingsPlatformNote(pinnedString("settings_playback_second_account"))
    Spacer(Modifier.height(10.dp))
    if (!MeloXSettingsRuntime.automaticLyricSelectionEnabled) {
        SettingsInfoCard(pinnedString("settings_auto_lyrics_required"))
    } else {
        SettingsGlassGroup {
            SettingsExternalToggleRow(pinnedString("settings_lyric_binding"), MeloXSettingsRuntime.lyricStrongBindingEnabled, "experimental_lyric_strong_binding") {
                MeloXSettingsPreferences.setBoolean("experimental_lyric_strong_binding", it)
                if (!it) melox.lyrics.LyricBindingStore.clear()
            }
        }
        Spacer(Modifier.height(10.dp))
        SettingsDangerButton(pinnedString("settings_clear_lyric_bindings"), Modifier.testTag("lyric-bindings-clear")) { confirm = "lyrics" }
    }
    confirm?.let { target ->
        SettingsConfirmation(pinnedString(if (target == "lyrics") "settings_clear_lyric_bindings_title" else "settings_bilibili_clear_title"),
            pinnedString(if (target == "lyrics") "settings_clear_lyric_bindings_body" else "settings_bilibili_clear_body"), onDismiss = { confirm = null }) {
            if (target == "lyrics") melox.lyrics.LyricBindingStore.clear() else melox.provider.bilibili.BilibiliPlaybackAssociationStore.clear()
            confirm = null
        }
    }
}
