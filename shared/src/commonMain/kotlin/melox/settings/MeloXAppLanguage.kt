package melox.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import melox.platform.getPreferences
import java.util.Locale

/** App-scoped language: changing MeloX never changes the host JVM/system locale. */
object MeloXAppLanguage {
    val supportedTags = listOf("system", "zh-CN", "zh-TW", "en-US", "ja-JP")
    var selectedTag by mutableStateOf("system")
        private set
    val locale: Locale get() = if (selectedTag == "system") Locale.getDefault() else Locale.forLanguageTag(selectedTag)

    fun initialize() {
        selectedTag = getPreferences("melox_app_settings").getString("application_language", "system")
            ?.takeIf { it in supportedTags } ?: "system"
    }

    fun select(tag: String) {
        require(tag in supportedTags)
        getPreferences("melox_app_settings").putString("application_language", tag)
        selectedTag = tag
    }
}
