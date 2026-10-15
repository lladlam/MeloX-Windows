package melox.validation

import melox.ui.foundation.MeloXSymbol
import melox.ui.foundation.meloXActionIconSymbol
import melox.ui.theme.meloXSfTypeface
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontFeature
import org.jetbrains.skia.TextLine
import org.jetbrains.skia.shaper.ShapingOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Every pinned settings row icon must resolve to the same SF Symbol name the
 * pinned Android `MeloXActionIcon` resolves it to, and the glyph must really
 * exist in the bundled `sf_pro_subset.ttf` with visible ink. A missing code
 * point would otherwise render as tofu or a silent fallback glyph.
 */
fun main(args: Array<String>) {
    val manifestFile = File(args[0]).canonicalFile
    val outputFile = File(args[1]).canonicalFile
    val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
    check(manifest.getString("pinned_android") == "3f877eaffe30c82eb6d24eb3647702f5bc554dd3")
    val pinnedTokens = manifest.getJSONObject("action_icon_tokens")
    // Route leading icons plus the tab-layout ordering arrows, which pinned
    // Android also draws with SF Symbols rather than text arrows.
    val routeTokens = manifest.getJSONArray("settings_route_symbols")
    val required = buildList {
        for (index in 0 until routeTokens.length()) add(routeTokens.getString(index))
        add("↑")
        add("↓")
    }
    check(required.all { pinnedTokens.has(it) }) { "Pinned Android has no icon token for a settings control" }
    val samples = JSONArray()
    val shaping = ShapingOptions.DEFAULT.withFeatures(arrayOf(FontFeature("ss16", 1)))
    val font = Font(meloXSfTypeface(androidx.compose.ui.text.font.FontWeight.Normal), 22f).apply {
        isSubpixel = true
    }
    try {
        for (token in required) {
            val symbol = meloXActionIconSymbol(token)
            val expected = pinnedTokens.getString(token)
            check(symbol.sfName == expected) {
                "Settings token $token renders ${symbol.sfName} on desktop but pinned Android renders $expected"
            }
            var inkWidth = 0
            var inkHeight = 0
            if (symbol == MeloXSymbol.Settings) {
                // Both platforms replace the SF gear with the traced brand disc.
                checkNotNull(Thread.currentThread().contextClassLoader.getResource("icons/ic_melox_brand_gear.svg")) {
                    "Brand gear vector is missing for the settings token"
                }
            } else {
                val text = symbol.codePoints.joinToString("") { String(Character.toChars(it)) }
                TextLine.make(text, font, shaping).use { line ->
                    val blob = requireNotNull(line.textBlob) { "Settings token $token has no SF glyph: $symbol" }
                    val bounds = blob.tightBounds
                    inkWidth = bounds.width.toInt()
                    inkHeight = bounds.height.toInt()
                    check(inkWidth > 0 && inkHeight > 0) { "Settings token $token renders empty ink: $symbol" }
                }
            }
            samples.put(JSONObject()
                .put("token", token)
                .put("symbol", symbol.name)
                .put("sf_name", symbol.sfName)
                .put("pinned_android_sf_name", expected)
                .put("brand_override", symbol == MeloXSymbol.Settings)
                .put("ink_width", inkWidth).put("ink_height", inkHeight))
        }
    } finally {
        font.close()
    }
    outputFile.parentFile?.mkdirs()
    outputFile.writeText(JSONObject()
        .put("status", "PASSED")
        .put("pinned_android", manifest.getString("pinned_android"))
        .put("manifest", manifestFile.name)
        .put("icon_font", "fonts/sf_pro_subset.ttf (pinned, ss16)")
        .put("settings_route_icons", samples.length())
        .put("samples", samples).toString(2) + "\n", Charsets.UTF_8)
    println("PASS: ${samples.length()} pinned settings icons match Android SF Symbol names and render real ink")
}
