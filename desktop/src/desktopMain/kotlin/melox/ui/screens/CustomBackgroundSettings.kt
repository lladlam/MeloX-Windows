package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import melox.settings.*
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable
internal fun CustomBackgroundSettings() {
    val scope = rememberCoroutineScope()
    val store = remember { MeloXCustomBackgroundStore() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    Text("选择的图片会复制到 MeloX 私有目录，最长边限制为 1280 像素；原图保留。", Modifier.padding(vertical = 8.dp))
    Text(if (MeloXSettingsRuntime.customBackgroundPath.isBlank()) "尚未选择图片，暂用歌曲封面" else "已保存自定义背景",
        Modifier.testTag("custom-background-status"))
    SettingsActionButton(if (busy) "正在导入…" else "选择背景图片", Modifier.testTag("custom-background-import"), enabled = !busy) {
        busy = true; message = null
        scope.launch {
            try {
                val selected = chooseBackgroundImage() ?: return@launch
                val imported = withContext(Dispatchers.IO) { store.importImage(selected) }
                ensureActive()
                MeloXSettingsPreferences.setString("player_custom_background_path", imported.absolutePath)
                withContext(Dispatchers.IO) { store.prune(imported) }
                message = "背景图片已更新"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { message = failure.message ?: "图片导入失败" }
            finally { busy = false }
        }
    }
    if (MeloXSettingsRuntime.customBackgroundPath.isNotBlank()) {
        SettingsActionButton("移除自定义背景", Modifier.testTag("custom-background-clear"), enabled = !busy) {
            MeloXSettingsPreferences.setString("player_custom_background_path", "")
            MeloXSettingsPreferences.setString("player_background_mode", MeloXPlayerBackgroundMode.BlurredArtwork.name)
            scope.launch(Dispatchers.IO) { store.prune(null) }
        }
    }
    message?.let { Text(it, Modifier.testTag("custom-background-message")) }
}

private suspend fun chooseBackgroundImage(): java.io.File? = suspendCancellableCoroutine { continuation ->
    val chooser = java.util.concurrent.atomic.AtomicReference<JFileChooser?>()
    continuation.invokeOnCancellation { SwingUtilities.invokeLater { chooser.get()?.cancelSelection() } }
    SwingUtilities.invokeLater {
        if (!continuation.isActive) return@invokeLater
        try {
            val picker = JFileChooser().apply {
                dialogTitle = "选择播放器背景图片"
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = FileNameExtensionFilter("图片", "png", "jpg", "jpeg", "bmp", "gif")
                isMultiSelectionEnabled = false
            }
            chooser.set(picker)
            if (!continuation.isActive) return@invokeLater
            val result = picker.showOpenDialog(null)
            if (continuation.isActive) continuation.resume(if (result == JFileChooser.APPROVE_OPTION) picker.selectedFile else null)
        } catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
    }
}
