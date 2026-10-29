package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Properties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import melox.autosync.MeloXAutoSyncModels
import melox.autosync.MeloXAutoSyncPack
import melox.settings.MeloXAppLanguage
import melox.settings.MeloXSettingsPreferences
import melox.settings.MeloXSettingsRuntime
import melox.ui.glass.MeloXGlassDialog

private object AutoSyncStrings {
    private fun load(suffix: String, defaults: Properties? = null) = Properties(defaults).apply {
        checkNotNull(AutoSyncStrings::class.java.classLoader.getResourceAsStream("parity/autosync$suffix.properties"))
            .reader(Charsets.UTF_8).use(::load)
    }
    private val base = load("")
    private val localized = mapOf("zh-rCN" to load("-zh-rCN", base), "zh-rTW" to load("-zh-rTW", base), "ja" to load("-ja", base))
    fun text(key: String, vararg args: Any): String {
        val locale = MeloXAppLanguage.locale
        val suffix = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-rTW" else "zh-rCN"
            "ja" -> "ja"
            else -> ""
        }
        val value = requireNotNull((localized[suffix] ?: base).getProperty(key))
        return if (args.isEmpty()) value else String.format(locale, value, *args)
    }
}

private enum class AutoSyncActionKind { Download, Precision, Standard, Remove }
private data class AutoSyncAction(val language: String, val pack: MeloXAutoSyncPack?, val kind: AutoSyncActionKind)
private fun modelLabel(language: String) = when (language) { "zh" -> "中文"; "en" -> "English"; "ja" -> "日本語"; else -> language }
private fun modelSize(bytes: Long) = String.format(MeloXAppLanguage.locale, "%.0f MB", bytes / 1024.0 / 1024.0)

@Composable
internal fun AutoSyncSettings() {
    val enabled = MeloXSettingsRuntime.autoSyncWordLyricsEnabled
    SettingsGlassGroup {
        SettingsExternalToggleRow(AutoSyncStrings.text("title"), enabled, "autosync-enabled", subtitle = AutoSyncStrings.text("note")) {
            MeloXSettingsPreferences.setBoolean("autosync_word_lyrics_enabled", it)
        }
    }
    if (enabled) AutoSyncModelPanel()
}

@Composable
private fun AutoSyncModelPanel() {
    val scope = rememberCoroutineScope()
    val revision by MeloXAutoSyncModels.revision.collectAsState()
    val progress = remember { MutableStateFlow(0) }
    val progressValue by progress.collectAsState()
    var packs by remember { mutableStateOf<List<MeloXAutoSyncPack>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<AutoSyncAction?>(null) }
    var job by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun refresh() {
        loading = true; error = null
        scope.launch {
            try { packs = MeloXAutoSyncModels.available() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
            finally { loading = false }
        }
    }
    fun run(action: AutoSyncAction) {
        busy = action.language; progress.value = 0; error = null
        job = scope.launch {
            try {
                when (action.kind) {
                    AutoSyncActionKind.Remove -> MeloXAutoSyncModels.remove(action.language)
                    else -> MeloXAutoSyncModels.install(checkNotNull(action.pack), action.kind == AutoSyncActionKind.Precision) {
                        progress.value = (it * 100).toInt().coerceIn(0, 100)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: failure.javaClass.simpleName }
            finally { busy = null; job = null }
        }
    }
    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }

    Spacer(Modifier.height(10.dp))
    SettingsInfoCard(AutoSyncStrings.text("audio_hint") + "\n" + AutoSyncStrings.text("beta"), Modifier.testTag("autosync-notice"))
    Text(AutoSyncStrings.text("models"), Modifier.padding(top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.titleSmall)
    if (loading) Text(AutoSyncStrings.text("loading"), Modifier.testTag("autosync-loading"), fontSize = 12.sp)
    error?.let { message ->
        Text(AutoSyncStrings.text("failed", message), Modifier.testTag("autosync-error").padding(vertical = 5.dp),
            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        if (packs.isEmpty()) SettingsActionButton(AutoSyncStrings.text("retry"), Modifier.testTag("autosync-retry"), enabled = !loading) { refresh() }
    }
    for (language in listOf("zh", "en", "ja")) {
        val pack = packs.firstOrNull { it.language == language }
        val installed = remember(revision, language) { MeloXAutoSyncModels.installed(language) }
        Spacer(Modifier.height(8.dp))
        SettingsGlassGroup {
            Column(Modifier.fillMaxWidth().testTag("autosync-model:$language").padding(12.dp)) {
                Text(modelLabel(language), style = MaterialTheme.typography.titleSmall)
                val status = when {
                    busy == language -> AutoSyncStrings.text("downloading", progressValue)
                    installed != null -> AutoSyncStrings.text("installed", "0.1.0-beta.4")
                    else -> AutoSyncStrings.text("not_installed")
                }
                val size = (installed?.bytes ?: pack?.bytes)?.let { " · ${modelSize(it)}" }.orEmpty()
                val variant = if (installed?.variant == "precision") " · ${AutoSyncStrings.text("precision_installed")}" else ""
                Text(status + size + variant, Modifier.padding(top = 3.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (language == "ja") Text(AutoSyncStrings.text("ja_unavailable"), Modifier.padding(top = 5.dp),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (busy == language) {
                    SettingsActionButton(AutoSyncStrings.text("cancel"), Modifier.testTag("autosync-cancel:$language")) { job?.cancel() }
                } else if (installed != null) {
                    SettingsActionButton(AutoSyncStrings.text("delete"), Modifier.testTag("autosync-delete:$language"), enabled = busy == null) {
                        confirmation = AutoSyncAction(language, pack, AutoSyncActionKind.Remove)
                    }
                } else {
                    SettingsActionButton(AutoSyncStrings.text("download"), Modifier.testTag("autosync-download:$language"), enabled = pack != null && busy == null) {
                        confirmation = AutoSyncAction(language, pack, AutoSyncActionKind.Download)
                    }
                }
                if (installed != null && pack?.precisionBytes != null) {
                    Text(AutoSyncStrings.text("precision"), Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    Text(AutoSyncStrings.text("precision_note", modelSize(pack.precisionBytes!!)), fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val precision = installed.variant == "precision"
                    SettingsActionButton(AutoSyncStrings.text(if (precision) "precision_revert" else "download"),
                        Modifier.testTag("autosync-precision:$language"), enabled = busy == null) {
                        confirmation = AutoSyncAction(language, pack, if (precision) AutoSyncActionKind.Standard else AutoSyncActionKind.Precision)
                    }
                }
            }
        }
    }
    confirmation?.let { action ->
        val pack = action.pack
        val bytes = when (action.kind) {
            AutoSyncActionKind.Precision -> pack?.precisionBytes
            else -> pack?.bytes
        }
        val message = if (action.kind == AutoSyncActionKind.Remove) AutoSyncStrings.text("delete_confirm", modelLabel(action.language))
            else AutoSyncStrings.text("confirm", modelSize(checkNotNull(bytes)))
        MeloXGlassDialog(true, onDismiss = { confirmation = null }, modifier = Modifier.testTag("autosync-confirm")) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(AutoSyncStrings.text("cancel"), Modifier.weight(1f).testTag("autosync-confirm-cancel")) { confirmation = null }
                SettingsActionButton(AutoSyncStrings.text(if (action.kind == AutoSyncActionKind.Remove) "delete" else "download"),
                    Modifier.weight(1f).testTag("autosync-confirm-accept")) { confirmation = null; run(action) }
            }
        }
    }
}
