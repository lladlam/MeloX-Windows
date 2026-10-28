package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.swing.Swing
import melox.network.*
import melox.settings.MeloXSettingsPreferences
import melox.ui.legal.pinnedString
import melox.ui.glass.MeloXGlassDialog
import java.net.URI
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

internal fun settingsGithubSource(source: MeloXGitHubSource) = pinnedString("settings_about_source_" + when (source) {
    MeloXGitHubSource.Auto -> "auto"; MeloXGitHubSource.GitHubDoh -> "doh"; MeloXGitHubSource.GhFast -> "ghfast"
    MeloXGitHubSource.GhProxy -> "ghproxy"; MeloXGitHubSource.GhProxyOrg -> "ghproxy_org"
})

@Composable
internal fun PinnedAboutSettings() {
    val routing = remember { MeloXGitHubRouting() }
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf(routing.selectedSource()) }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var exportInfo by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var release by remember { mutableStateOf<Pair<String, String>?>(null) }
    var dev by remember { mutableStateOf<JSONObject?>(null) }
    val version = System.getProperty("jpackage.app-version", "1.0.0")
    val commit = melox.MeloXBuildConfig.BUILD_COMMIT
    fun browse(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(URI(url)) }.onFailure { status = it.message ?: pinnedString("settings_about_open_link_failed") }
    }
    suspend fun github(path: String): String = withContext(Dispatchers.IO) {
        var failure: Exception? = null
        for (route in routing.candidates()) {
            try {
                val request = Request.Builder().url(routing.routedUrl(route, "https://api.github.com/repos/lladlam/MeloX-Windows/$path"))
                    .header("Accept", "application/vnd.github+json").header("User-Agent", "MeloX-Windows").build()
                return@withContext routing.client(route).newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "GitHub HTTP ${response.code}" }
                    requireNotNull(response.body).string()
                }
            } catch (e: Exception) { failure = e }
        }
        throw failure ?: java.io.IOException("No GitHub route")
    }
    if (melox.settings.MeloXSettingsRuntime.desktopLayoutEnabled) {
        melox.ui.desktop.DesktopSettingsButton("MeloX", Modifier.testTag("about-build")) { melox.ui.desktop.MeloXDesktopToolWindows.show(melox.ui.desktop.DesktopTool.About) }
    } else SettingsGlassGroup {
        Column(Modifier.padding(18.dp).testTag("about-build")) {
            Text("MeloX Windows", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(pinnedString("settings_about_version", version), Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
            Text(pinnedString("settings_about_maintainer"), Modifier.padding(top = 14.dp), fontWeight = FontWeight.SemiBold)
            Text(pinnedString("settings_about_upstream"), Modifier.padding(top = 5.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        }
    }
    Spacer(Modifier.height(10.dp))
    SettingsGlassGroup { AboutSponsorsContent() }
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup { SettingsToggleRow("settings_about_auto_update", "update_auto_check", true, enabled = false) }
    Spacer(Modifier.height(20.dp))
    SettingsGlassGroup {
        SettingsChoice("settings_about_github_source", "github_source", source, MeloXGitHubSource.entries.map { it to settingsGithubSource(it) }) {
            source = it; routing.selectSource(it)
        }
    }
    Text(routing.effectiveRoute()?.takeIf { source == MeloXGitHubSource.Auto }?.let {
        pinnedString("settings_about_source_selected", settingsGithubSource(it.source), it.latencyMs)
    } ?: pinnedString("settings_about_source_auto_note"), Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        fontSize = 12.sp, lineHeight = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
    SettingsActionButton(pinnedString(if (checking) "settings_about_checking" else "settings_about_check_update"), Modifier.testTag("about-check-release"), enabled = !checking) {
        scope.launch {
            checking = true
            runCatching {
                val releases = JSONArray(github("releases?per_page=20"))
                (0 until releases.length()).map { releases.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
                    ?: error(pinnedString("settings_about_update_failed"))
            }.onSuccess {
                val tag = it.getString("tag_name")
                val url = it.getString("html_url")
                check(url.startsWith("https://github.com/lladlam/MeloX-Windows/releases/"))
                release = tag to url
                status = pinnedString("settings_about_new_version", tag, it.optString("name", tag))
            }.onFailure { status = it.message ?: pinnedString("settings_about_update_failed") }
            checking = false
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(pinnedString("settings_about_dev_commit", commit.take(7)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .42f))
    SettingsActionButton(pinnedString("settings_about_check_dev"), Modifier.testTag("about-check-dev"), enabled = !checking) {
        scope.launch {
            checking = true
            runCatching { JSONObject(github("commits/main")) }.onSuccess {
                dev = it
                status = if (it.getString("sha") == commit) pinnedString("settings_about_dev_current", commit.take(7))
                else pinnedString("settings_about_dev_new", it.getString("sha").take(7), it.getJSONObject("commit").getString("message"))
            }.onFailure { status = it.message ?: pinnedString("settings_about_dev_failed") }
            checking = false
        }
    }
    dev?.takeIf { it.optString("sha") != commit }?.let {
        Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_about_download_dev")) { browse("https://github.com/lladlam/MeloX-Windows/actions/workflows/build.yml") }
        Text(pinnedString("settings_about_dev_detail", it.getString("sha").take(7), it.getJSONObject("commit").getJSONObject("author").optString("name"),
            it.getJSONObject("commit").getString("message")), Modifier.padding(top = 8.dp), fontSize = 12.sp, lineHeight = 18.sp)
    }
    status?.let { Text(it, Modifier.padding(top = 10.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)) }
    release?.let { (tag, url) ->
        Spacer(Modifier.height(10.dp)); SettingsActionButton(pinnedString("settings_about_open_release", tag)) { browse(url) }
    }
    Spacer(Modifier.height(24.dp))
    SettingsActionButton(pinnedString("settings_about_restore_player"), Modifier.testTag("about-restore-player")) {
        MeloXSettingsPreferences.resetRecommendedPlayerSettings(); status = pinnedString("settings_about_restored")
    }
    Spacer(Modifier.height(14.dp))
    SettingsGlassGroup {
        Column(Modifier.padding(16.dp)) {
            Text(pinnedString("settings_about_licenses_title"), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(pinnedString("settings_about_licenses_body"), Modifier.padding(top = 10.dp), fontSize = 13.sp, lineHeight = 20.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .62f))
        }
    }
    Spacer(Modifier.height(14.dp))
    listOf("github" to "https://github.com/lladlam/MeloX-Windows", "ios" to "https://github.com/youshen2/MeloX",
        "upstream_licenses" to "https://github.com/youshen2/MeloX/blob/main/MeloX/Features/Legal/ProjectLicensesView.swift",
        "qq_group" to "https://qm.qq.com/q/wbhFQxj7mo", "sponsor" to "https://ifdian.net/a/lladlam").forEachIndexed { index, (label, url) ->
        if (index > 0) Spacer(Modifier.height(10.dp))
        SettingsActionButton(pinnedString("settings_about_$label"), Modifier.testTag("about-open-$label")) { browse(url) }
    }
    Spacer(Modifier.height(10.dp))
    SettingsActionButton(pinnedString(if (exporting) "settings_about_exporting" else "settings_about_export"), Modifier.testTag("about-export-logs"), enabled = !exporting) { exportInfo = true }
    if (exportInfo) MeloXGlassDialog(true, onDismiss = { exportInfo = false }, modifier = Modifier.testTag("about-log-export-info")) {
        Text(pinnedString("settings_about_export_title"), style = MaterialTheme.typography.titleMedium)
        Text(pinnedString("settings_about_export_body"), Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 19.sp)
        Text("${System.getProperty("os.name")} ${System.getProperty("os.version")} · ${System.getProperty("os.arch")}", Modifier.padding(top = 14.dp), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsActionButton(pinnedString("action_cancel"), Modifier.weight(1f).testTag("about-export-cancel")) { exportInfo = false }
            SettingsActionButton(pinnedString("settings_about_choose_location"), Modifier.weight(1f)) {
                exportInfo = false; exporting = true
                scope.launch {
                    runCatching {
                        val file = withContext(Dispatchers.Swing) {
                            val chooser = javax.swing.JFileChooser().apply { selectedFile = java.io.File("MeloX-logs-${System.currentTimeMillis()}.txt") }
                            if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
                        }
                        file?.let { target -> withContext(Dispatchers.IO) { melox.diagnostics.MeloXLogExporter.exportRecentLogs(target) } }
                    }.onSuccess { it?.let { status = pinnedString("settings_about_exported", it.lineCount) } }
                        .onFailure { status = it.message ?: pinnedString("settings_about_export_failed") }
                    exporting = false
                }
            }
        }
    }
    SettingsPlatformNote(pinnedString("settings_about_auto_update"))
}
