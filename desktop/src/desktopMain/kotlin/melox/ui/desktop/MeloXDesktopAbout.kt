package melox.ui.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import melox.network.MeloXGitHubRouting
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** DesktopAboutView: original 600x320 horizontal layout, shipped Desktop logo. */
@Composable
internal fun MeloXDesktopAbout() {
    val version = System.getProperty("jpackage.app-version", "1.0.0")
    // Release resources carry the CI SHA; a source run keeps the short
    // platform label instead of slicing words out of a sentence.
    val commit = melox.MeloXBuildConfig.BUILD_COMMIT.substringBefore(' ').take(7)
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }
    var releaseUrl by remember { mutableStateOf<String?>(null) }
    fun browse(url: String) {
        runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }
            .onFailure { message = "ui.about.update_check_failed.title" to (it.message ?: desktopString("ui.error.unknown")) }
    }
    Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 52.dp, bottom = 28.dp).testTag("desktop-about"),
        horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource("branding/melox-windows.png"), null, Modifier.size(176.dp).testTag("desktop-about-logo"))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Text("MeloX", fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            Text(desktopFormat("ui.desktop.about.version_build", version, commit), Modifier.padding(top = 10.dp), fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(desktopPlatformString("about_developer"), Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(String.format(desktopPlatformString("about_copyright"), java.time.Year.now().value), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopSettingsButton(desktopString(if (checking) "ui.about.checking_updates" else "ui.about.check_updates"), Modifier.weight(1.03f).testTag("desktop-about-check"), !checking) {
                    checking = true
                    scope.launch {
                        try {
                            val release = desktopLatestRelease()
                            val latest = release.getString("tag_name")
                            val url = release.getString("html_url")
                            check(url.startsWith("https://github.com/lladlam/MeloX-Windows/releases/"))
                            if (latest.removePrefix("v") == version) message = "ui.about.up_to_date.title" to desktopFormat("ui.about.up_to_date.message", version)
                            else { releaseUrl = url; message = "ui.about.update_available.title" to desktopFormat("ui.desktop.about.update_available.message", version, latest) }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: Exception) { message = "ui.about.update_check_failed.title" to (failure.message ?: desktopString("ui.error.unknown")) }
                        finally { checking = false }
                    }
                }
                DesktopSettingsButton(desktopString("ui.about.github"), Modifier.weight(1.02f).testTag("desktop-about-github")) { browse("https://github.com/lladlam/MeloX-Windows") }
                DesktopSettingsButton(desktopString("ui.legal.projects_licenses.title"), Modifier.weight(1.12f).testTag("desktop-about-licenses")) { MeloXDesktopToolWindows.show(DesktopTool.Licenses) }
            }
        }
    }
    message?.let { (title, text) -> DesktopMessageDialog(title, text, { message = null; releaseUrl = null },
        confirmKey = if (releaseUrl == null) "ui.common.ok" else "ui.about.open_release_page",
        onConfirm = releaseUrl?.let { url -> { browse(url); message = null; releaseUrl = null } }) }
}

private suspend fun desktopLatestRelease(): JSONObject = withContext(Dispatchers.IO) {
    val routing = MeloXGitHubRouting()
    var failure: Exception? = null
    for (route in routing.candidates()) try {
        val request = Request.Builder().url(routing.routedUrl(route, "https://api.github.com/repos/lladlam/MeloX-Windows/releases?per_page=20"))
            .header("Accept", "application/vnd.github+json").header("User-Agent", "MeloX-Windows").build()
        val releases = routing.client(route).newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub HTTP ${response.code}" }; JSONArray(requireNotNull(response.body).string())
        }
        return@withContext (0 until releases.length()).map { releases.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") && !it.optBoolean("prerelease") }
            ?: error(desktopString("ui.about.update_check_failed.title"))
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (error: Exception) { failure = error }
    throw failure ?: java.io.IOException("No GitHub route")
}

/** Project-reference links are real upstream documents, not shortened invented license text. */
@Composable
internal fun MeloXDesktopProjectLicenses() {
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDocument by remember { mutableStateOf<Int?>(null) }
    val documents = listOf(
        Triple("melox", "MeloX", "https://github.com/youshen2/MeloX/blob/master/LICENSE"),
        Triple("yesplaymusic", "YesPlayMusic", "https://github.com/qier222/YesPlayMusic/blob/main/LICENSE"),
        Triple("netease_api", desktopString("ui.legal.netease_audio_fingerprint"), "https://github.com/neteasecloudmusicapienhanced/api-enhanced"),
        Triple("pv_tool", "PV Tool", "https://github.com/DanteAlighieri13210914/pv-tool"),
        Triple("source_han_serif", "Source Han Serif CN", "https://openfontlicense.org/open-font-license-official-text/"),
        Triple("beatnet", "BeatNet", "https://github.com/mjhydri/BeatNet"))
    fun open(url: String) { runCatching { java.awt.Desktop.getDesktop().browse(java.net.URI(url)) }.onFailure { error = it.message ?: desktopString("ui.error.unknown") } }
    if (selectedDocument != null) {
        val (key, _, url) = documents[requireNotNull(selectedDocument)]
        val resourceNames = when (key) { "netease_api" -> listOf("NeteaseAudioFingerprint-NOTICE.txt", "NeteaseAPI-LICENSE.txt"); "beatnet" -> listOf("BeatNet-NOTICE.md"); else -> emptyList() }
        val contents = resourceNames.mapNotNull { name -> object {}.javaClass.getResourceAsStream("/parity/legal/$name")?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                DesktopDialogButton("ui.common.back", tag = "desktop-legal-back") { selectedDocument = null }
                Text(desktopString("ui.legal.document.$key.title"), Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.SemiBold)
                DesktopDialogButton("ui.legal.view_source") { open(url) }
            }
            SelectionContainer(Modifier.fillMaxSize().desktopVerticalScroll().padding(24.dp)) { Text(contents.takeIf { it.isNotEmpty() }?.joinToString("\n\n──────────\n\n") ?: desktopString("ui.legal.document.$key.fallback")) }
        }
    } else {
    Column(Modifier.fillMaxSize().desktopVerticalScroll().padding(24.dp).testTag("desktop-project-licenses"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(desktopString("ui.legal.section.important"), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(desktopString("ui.legal.pv_tool.noncommercial.title"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
        Text(desktopString("ui.legal.pv_tool.noncommercial.message"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(desktopString("ui.legal.pv_tool.gpl_notice"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(desktopString("ui.legal.section.licenses"), fontWeight = FontWeight.SemiBold)
        documents.forEachIndexed { index, (_, title, _) ->
            DesktopSettingsButton(title, Modifier.testTag("desktop-legal-document:$index")) { selectedDocument = index }
        }
        Text(desktopString("ui.legal.section.references"), fontWeight = FontWeight.SemiBold)
        listOf("jayfunc/BetterLyrics" to "better_lyrics", "WXRIW/Lyricify-Lyrics-Helper" to "lyricify", "qier222/YesPlayMusic" to "yesplaymusic",
            "neteasecloudmusicapienhanced/api-enhanced" to "netease_api", "DanteAlighieri13210914/pv-tool" to "pv_tool", "mjhydri/BeatNet" to "beatnet").forEach { (project, key) ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { DesktopSettingsButton(project) { open("https://github.com/$project") }; Text(desktopString("ui.legal.reference.$key"), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(desktopString("ui.legal.references.footer"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(desktopString("ui.legal.section.disclaimer"), fontWeight = FontWeight.SemiBold)
        Text(desktopString("ui.legal.unofficial_disclaimer"), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    }
    error?.let { DesktopMessageDialog("ui.library.error.title", it, { error = null }) }
}
