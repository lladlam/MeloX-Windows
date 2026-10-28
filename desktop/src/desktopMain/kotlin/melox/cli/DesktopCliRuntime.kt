package melox.cli

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import melox.platform.meloXDataDir
import melox.settings.MeloXSettingsPreferences
import java.nio.file.Paths

/** Owned by the application composition, including while its window is hidden in the tray. */
internal object DesktopCliRuntime {
    private var server: DesktopCliServer? = null
    var enabled by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun attach() {
        if (MeloXSettingsPreferences.boolean("experimental_cli_enabled", false)) enable(persist = false)
    }

    fun setRequested(value: Boolean) {
        if (value) enable(persist = true)
        else {
            enabled = false
            server?.close()
            server = null
            error = null
            MeloXSettingsPreferences.setBoolean("experimental_cli_enabled", false)
        }
    }

    private fun enable(persist: Boolean) {
        if (server != null) return
        try {
            server = DesktopCliServer.start(Paths.get(meloXDataDir())) { request ->
                DesktopCliCommands.execute(request) { enabled }
            }
            enabled = true
            error = null
            if (persist) MeloXSettingsPreferences.setBoolean("experimental_cli_enabled", true)
        } catch (failure: Exception) {
            server?.close()
            server = null
            enabled = false
            error = failure.message ?: "CLI 启动失败"
            if (persist) MeloXSettingsPreferences.setBoolean("experimental_cli_enabled", false)
        }
    }

    fun detach() {
        enabled = false
        server?.close()
        server = null
    }
}
