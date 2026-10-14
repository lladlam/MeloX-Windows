package melox.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.platform.Font as DesktopFont

private val extractedFonts = java.util.concurrent.ConcurrentHashMap<String, java.io.File>()

private fun loadFont(path: String, weight: FontWeight, axisWeight: Int = weight.weight): Font? {
    return try {
        val cl = Thread.currentThread().contextClassLoader
            ?: MeloXFontLoader::class.java.classLoader
            ?: return null
        val url = cl.getResource(path) ?: return null
        val file = try {
            java.io.File(url.toURI())
        } catch (e: Exception) {
            // All nine variable-weight instances must share the same extracted
            // 19 MB Android font, rather than create nine identical temp files.
            extractedFonts.computeIfAbsent(url.toExternalForm()) {
                val temp = java.io.File.createTempFile("melox_font_", ".ttf")
                try {
                    url.openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                    temp.deleteOnExit()
                    temp
                } catch (error: Exception) {
                    temp.delete()
                    throw error
                }
            }
        }
        DesktopFont(file = file, weight = weight, style = FontStyle.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(axisWeight)))
    } catch (e: Exception) {
        melox.platform.logError("MeloXFont", "Failed to load font $path: ${e.javaClass.simpleName}: ${e.message}")
        null
    }
}

private object MeloXFontLoader

// Pinned Android res/font/mi_lan_pro.xml: nine CSS weights; 800 and 900 both
// select axis 700. Windows Skia applies the wght axis to outlines but keeps
// default-axis advance widths for the *variable* font, so lyrics get heavy
// glyphs on light advances and letters overlap. The package therefore ships
// axis-frozen static instances (tools/generate_lanting_static.py) whose outlines
// and hmtx advances are baked per weight and validated to shape identically to
// the pinned variable font at each axis. The pinned variable font stays in the
// package unchanged as the hash-verified source.
private val LAN_TING_CSS_WEIGHTS = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900)
private fun lanTingStaticPath(cssWeight: Int): String =
    "fonts/mi_lan_pro_w${if (cssWeight == 900) 800 else cssWeight}.ttf"

private fun loadStaticFont(path: String, weight: FontWeight): Font? = try {
    val cl = Thread.currentThread().contextClassLoader
        ?: MeloXFontLoader::class.java.classLoader
        ?: return null
    val url = cl.getResource(path) ?: return null
    val file = try {
        java.io.File(url.toURI())
    } catch (e: Exception) {
        // All CSS entries share their per-instance file; never copy identical fonts.
        extractedFonts.computeIfAbsent(url.toExternalForm()) {
            val temp = java.io.File.createTempFile("melox_font_", ".ttf")
            try {
                url.openStream().use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                temp.deleteOnExit()
                temp
            } catch (error: Exception) {
                temp.delete()
                throw error
            }
        }
    }
    DesktopFont(file = file, weight = weight, style = FontStyle.Normal,
        variationSettings = FontVariation.Settings())
} catch (e: Exception) {
    melox.platform.logError("MeloXFont", "Failed to load static font $path: ${e.javaClass.simpleName}: ${e.message}")
    null
}

private val MiLanPro = run {
    val fonts = LAN_TING_CSS_WEIGHTS.mapNotNull { css -> loadStaticFont(lanTingStaticPath(css), FontWeight(css)) }
    check(fonts.size == LAN_TING_CSS_WEIGHTS.size) { "Android 小米兰亭 Pro 静态字重未完整加载" }
    melox.platform.logInfo("MeloXFont", "Android MiLan Pro static instances loaded: CSS=$LAN_TING_CSS_WEIGHTS " +
        "files=mi_lan_pro_w{100..800}.ttf (CSS 900 shares frozen axis-700 instance)")
    FontFamily(fonts)
}

private val sfFonts = listOf(FontWeight.Normal, FontWeight.Medium).mapNotNull { weight ->
    loadFont("fonts/sf_pro_subset.ttf", weight)
}
private val SFProSymbols = if (sfFonts.isNotEmpty()) FontFamily(sfFonts) else FontFamily.Default

private val sfTypefaces by lazy {
    val bytes = requireNotNull(MeloXFontLoader::class.java.getResourceAsStream("/fonts/sf_pro_subset.ttf")) {
        "SF Symbols font could not be loaded"
    }.use { it.readBytes() }
    org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
        val base = requireNotNull(org.jetbrains.skia.FontMgr.default.makeFromData(data))
        mapOf(FontWeight.Normal to base, FontWeight.Medium to
            base.makeClone(org.jetbrains.skia.FontVariation("wght", FontWeight.Medium.weight.toFloat())))
    }
}

internal fun meloXSfTypeface(weight: FontWeight): org.jetbrains.skia.Typeface =
    sfTypefaces.getValue(weight)

private val lyricTypefaces by lazy {
    LAN_TING_CSS_WEIGHTS.associate { css ->
        val path = "/${lanTingStaticPath(css)}"
        val bytes = requireNotNull(MeloXFontLoader::class.java.getResourceAsStream(path)) {
            "Missing static MiLan Pro resource $path"
        }.use { it.readBytes() }
        css to org.jetbrains.skia.Data.makeFromBytes(bytes).use { data ->
            requireNotNull(org.jetbrains.skia.FontMgr.default.makeFromData(data)) { "Cannot load $path" }
        }
    }.also { map ->
        check(map.size == LAN_TING_CSS_WEIGHTS.size)
        map.forEach { (css, typeface) ->
            // A static instance must expose no variation axes; otherwise the
            // Windows design-advance collapse could return.
            require(typeface.variationAxes.isNullOrEmpty()) { "MiLan Pro CSS $css is not axis-frozen" }
            val expectedStyleWeight = if (css == 900) 800 else css
            require(typeface.fontStyle.weight == expectedStyleWeight) {
                "MiLan Pro CSS $css reports style weight ${typeface.fontStyle.weight}, expected $expectedStyleWeight"
            }
        }
        val sample = map.getValue(400)
        melox.platform.logInfo("MeloXFont",
            "Native lyric static typefaces loaded: family=${sample.familyName}; css=$LAN_TING_CSS_WEIGHTS; variations=0")
    }
}

internal fun meloXLyricTypeface(weight: FontWeight): org.jetbrains.skia.Typeface =
    lyricTypefaces.getValue(weight.weight)

private val MaterialSymbols = run {
    val font = loadFont("fonts/material_symbols_rounded_filled_static.ttf", FontWeight.Normal)
    if (font != null) FontFamily(font) else FontFamily.Default
}

val MeloXLanTingProFontFamily = MiLanPro
val MeloXSFProFontFamily = SFProSymbols
val MeloXMaterialSymbolsFamily = MaterialSymbols

val LocalMeloXFontFamily = compositionLocalOf { MeloXLanTingProFontFamily }
