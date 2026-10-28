package melox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import melox.music.provider.ThirdPartyMusicSourceConsentStore
import melox.provider.lxuser.ChkszApiKeyStore
import melox.provider.lxuser.LxUserRuntime
import melox.provider.lxuser.LxUserSourceStore
import melox.ui.desktop.LocalDesktopSettingsPresentation
import melox.ui.glass.MeloXGlassButton
import melox.ui.glass.MeloXGlassButtonStyle
import melox.ui.glass.MeloXGlassDialog
import melox.ui.legal.MeloXLegalDocument
import melox.ui.legal.MeloXLegalDocumentDialog
import melox.ui.legal.pinnedString
import okhttp3.OkHttpClient
import okhttp3.Request

private object LxUiStrings {
    private fun load(suffix: String, defaults: Properties? = null) = Properties(defaults).apply {
        checkNotNull(LxUiStrings::class.java.classLoader.getResourceAsStream("parity/lx-services$suffix.properties"))
            .reader(Charsets.UTF_8).use(::load)
    }
    private val base = load("")
    private val values = mapOf("zh-rCN" to load("-zh-rCN", base), "zh-rTW" to load("-zh-rTW", base), "ja" to load("-ja", base))
    fun string(key: String, vararg args: Any): String {
        val locale = melox.settings.MeloXAppLanguage.locale
        val suffix = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-rTW" else "zh-rCN"
            "ja" -> "ja"
            else -> ""
        }
        val text = requireNotNull((values[suffix] ?: base).getProperty(key))
        return if (args.isEmpty()) text else String.format(locale, text, *args)
    }
}

/** The same real source/consent store is presented in both production layouts. */
@Composable
internal fun LxServicesSettings(desktop: Boolean) {
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(ThirdPartyMusicSourceConsentStore.enabled()) }
    var membership by remember { mutableStateOf(ThirdPartyMusicSourceConsentStore.membershipFallbackOnly()) }
    var sources by remember { mutableStateOf(LxUserSourceStore.list()) }
    var keyConfigured by remember { mutableStateOf(ChkszApiKeyStore.read().isNotBlank()) }
    var consent by remember { mutableStateOf(false) }
    var terms by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun text(name: String, vararg args: Any) = LxUiStrings.string(name, *args)
    fun imported() { sources = LxUserSourceStore.list(); url = ""; showImport = false }

    CompositionLocalProvider(LocalDesktopSettingsPresentation provides desktop) {
        Column(Modifier.fillMaxWidth().testTag("lx-services"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsGlassGroup {
                SettingsExternalToggleRow(text("enable"), enabled, "lx-enabled", subtitle = text("enable_hint")) {
                    if (it) consent = true else {
                        ThirdPartyMusicSourceConsentStore.reject(); enabled = false
                        scope.launch(Dispatchers.IO) { LxUserRuntime.evictAll() }
                    }
                }
                if (enabled) SettingsExternalToggleRow(text("membership"), membership, "lx-membership", subtitle = text("membership_hint")) {
                    membership = it; ThirdPartyMusicSourceConsentStore.setMembershipFallbackOnly(it)
                }
            }
            if (enabled) {
                SettingsGlassGroup {
                    SettingsActionButton(text("terms"), Modifier.testTag("lx-terms")) { terms = true }
                    SettingsActionButton(text("chksz") + " · " + text(if (keyConfigured) "chksz_configured" else "chksz_missing"),
                        Modifier.testTag("lx-chksz")) { key = ChkszApiKeyStore.read(); showKey = true }
                }
                Text(text("added"), Modifier.padding(top = 12.dp), fontSize = if (desktop) 13.sp else 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SettingsGlassGroup {
                    SettingsActionButton(text("add"), Modifier.testTag("lx-add")) { error = null; showImport = true }
                    Text(text("add_hint"), Modifier.padding(horizontal = 12.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sources.forEach { source ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp).testTag("lx-source:${source.id}"),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name)
                                val detail = listOfNotNull(source.metadata.version?.let { "v$it" }, source.metadata.author,
                                    source.metadata.expirationTime?.let { text("expires", it) }).joinToString(" · ")
                                if (detail.isNotBlank()) Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(modifier = Modifier.testTag("lx-delete:${source.id}"), onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { LxUserSourceStore.remove(source.id) }
                                    sources = LxUserSourceStore.list()
                                }
                            }) { Text(text("delete")) }
                        }
                    }
                }
            }
        }
    }
    if (consent) MeloXGlassDialog(true, onDismiss = { consent = false }, modifier = Modifier.testTag("lx-consent")) {
        Text(pinnedString("legal_third_party_title"), style = MaterialTheme.typography.titleLarge)
        Text(pinnedString("legal_third_party_body"), Modifier.padding(top = 9.dp), fontSize = 14.sp, lineHeight = 21.sp)
        Text(pinnedString("legal_view_third_party"), Modifier.testTag("lx-consent-terms").clickable { terms = true }.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton({ consent = false }, Modifier.weight(1f).testTag("lx-consent-reject"), style = MeloXGlassButtonStyle.Plain) { Text(pinnedString("legal_disagree")) }
            MeloXGlassButton({ ThirdPartyMusicSourceConsentStore.accept(); enabled = true; consent = false },
                Modifier.weight(1f).testTag("lx-consent-accept"), style = MeloXGlassButtonStyle.BorderedProminent) { Text(pinnedString("legal_agree_enable")) }
        }
    }
    if (terms) MeloXLegalDocumentDialog(MeloXLegalDocument.ThirdPartyMusicSources) { terms = false }
    if (showImport) MeloXGlassDialog(true, onDismiss = { if (!importing) showImport = false }, modifier = Modifier.testTag("lx-import-dialog")) {
        Text(text("import_title"), style = MaterialTheme.typography.titleLarge)
        Text(text("import_hint"), Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 19.sp)
        MeloXGlassButton(onClick = {
            importing = true; error = null
            scope.launch {
                try {
                    val file = chooseLxFile() ?: return@launch
                    withContext(Dispatchers.IO) {
                        require(file.isFile && file.length() <= LxUserSourceStore.MaxScriptBytes) { "音乐源脚本过大（最大 9 MB）" }
                        LxUserSourceStore.importScript(file.readText(Charsets.UTF_8))
                    }
                    imported()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) { error = failure.message ?: text("import_failed") }
                finally { importing = false }
            }
        }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("lx-import-file"), enabled = !importing,
            style = MeloXGlassButtonStyle.BorderedProminent) { Text(text("import_local")) }
        OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("lx-import-url"),
            singleLine = true, placeholder = { Text("https://.../source.js") }, enabled = !importing)
        error?.let { Text(it, Modifier.testTag("lx-import-error").padding(top = 7.dp), color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton({ showImport = false }, Modifier.weight(1f).testTag("lx-import-cancel"), enabled = !importing,
                style = MeloXGlassButtonStyle.Plain) { Text(text("cancel")) }
            MeloXGlassButton(onClick = {
                importing = true; error = null
                scope.launch {
                    try {
                        val address = url.trim()
                        require(address.startsWith("http://") || address.startsWith("https://")) { text("invalid_http") }
                        withContext(Dispatchers.IO) { LxUserSourceStore.importScript(downloadLxSource(address)) }
                        imported()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) { error = failure.message ?: text("import_failed") }
                    finally { importing = false }
                }
            }, modifier = Modifier.weight(1f).testTag("lx-import-submit"), enabled = !importing && url.isNotBlank(),
                style = MeloXGlassButtonStyle.BorderedProminent) { Text(text(if (importing) "busy" else "import")) }
        }
    }
    if (showKey) MeloXGlassDialog(true, onDismiss = { showKey = false; key = "" }, modifier = Modifier.testTag("lx-chksz-dialog")) {
        Text(text("chksz_title"), style = MaterialTheme.typography.titleLarge)
        Text(text("chksz_body"), Modifier.padding(top = 8.dp), fontSize = 13.sp, lineHeight = 19.sp)
        OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth().padding(top = 10.dp).testTag("lx-chksz-key"),
            singleLine = true, visualTransformation = PasswordVisualTransformation(), placeholder = { Text(text("key_placeholder")) })
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MeloXGlassButton({ ChkszApiKeyStore.clear(); key = ""; keyConfigured = false; showKey = false },
                Modifier.weight(1f).testTag("lx-chksz-clear"), style = MeloXGlassButtonStyle.Plain) { Text(text("clear")) }
            MeloXGlassButton({ ChkszApiKeyStore.write(key.trim()); keyConfigured = key.isNotBlank(); key = ""; showKey = false },
                Modifier.weight(1f).testTag("lx-chksz-save"), style = MeloXGlassButtonStyle.BorderedProminent) { Text(text("save")) }
        }
    }
}

private val lxImportClient = OkHttpClient.Builder().callTimeout(20, TimeUnit.SECONDS).build()
private fun downloadLxSource(url: String): String = lxImportClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
    check(response.isSuccessful) { LxUiStrings.string("download_failed", response.code) }
    require(response.body.contentLength() <= LxUserSourceStore.MaxScriptBytes &&
        !response.body.source().request(LxUserSourceStore.MaxScriptBytes + 1)) { "音乐源脚本过大（最大 9 MB）" }
    response.body.string()
}

private suspend fun chooseLxFile(): File? = suspendCancellableCoroutine { continuation ->
    val chooser = AtomicReference<JFileChooser?>()
    continuation.invokeOnCancellation { SwingUtilities.invokeLater { chooser.get()?.cancelSelection() } }
    SwingUtilities.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val picker = JFileChooser().apply {
                dialogTitle = LxUiStrings.string("import_title")
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("JavaScript", "js")
                isMultiSelectionEnabled = false
            }
            chooser.set(picker)
            if (!continuation.isActive) return@invokeLater
            val result = picker.showOpenDialog(null)
            if (continuation.isActive) continuation.resume(if (result == JFileChooser.APPROVE_OPTION) picker.selectedFile else null)
        } catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
    }
}
