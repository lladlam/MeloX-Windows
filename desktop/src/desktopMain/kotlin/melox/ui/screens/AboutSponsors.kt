package melox.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.Properties
import melox.settings.MeloXAppLanguage

/** Android 0.7.1 strings are additive; existing pinned resource bytes stay intact. */
private object AboutSponsorStrings {
    private fun load(name: String, defaults: Properties? = null) = Properties(defaults).apply {
        checkNotNull(AboutSponsorStrings::class.java.classLoader.getResourceAsStream("parity/android-about-sponsors$name.properties"))
            .reader(Charsets.UTF_8).use(::load)
    }
    private val english = load("")
    private val localized = mapOf("zh-rCN" to load("-zh-rCN", english), "zh-rTW" to load("-zh-rTW", english),
        "ja" to load("-ja", english))

    fun get(key: String, locale: Locale): String {
        val suffix = when (locale.language) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-rTW" else "zh-rCN"
            "ja" -> "ja"
            else -> null
        }
        return checkNotNull((localized[suffix] ?: english).getProperty("settings_about_$key"))
    }
}

internal fun aboutSponsorString(key: String) = AboutSponsorStrings.get(key, MeloXAppLanguage.locale)

internal fun aboutSponsorRows(): List<Pair<String, String>> = listOf(
    "26d40" to "50", "aec4c" to "6", "bai" to "5", "zhongmo" to "8_88", "361ef" to "20", "f8b67" to "12_24",
).map { (name, amount) -> aboutSponsorString("sponsor_$name") to aboutSponsorString("sponsor_amount_$amount") }

@Composable
internal fun AboutSponsorsContent() {
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("about-sponsors")) {
        Text(aboutSponsorString("sponsors_title"), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        aboutSponsorRows().forEachIndexed { index, (name, amount) ->
            Row(Modifier.fillMaxWidth().padding(top = 9.dp).testTag("about-sponsor:$index"), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .78f))
                Text(amount, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f), fontSize = 13.sp)
            }
        }
        Text(aboutSponsorString("sponsors_note"), Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .46f),
            fontSize = 12.sp, lineHeight = 18.sp)
    }
}
