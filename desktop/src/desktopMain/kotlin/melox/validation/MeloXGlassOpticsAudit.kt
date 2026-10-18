package melox.validation

import java.io.File
import melox.ui.glass.*
import org.jetbrains.skia.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.system.measureNanoTime
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.kyant.capsule.ContinuousRoundedRectangle
import androidx.compose.ui.unit.dp

/** Real Skia raster output and deterministic ownership counts, not an Android pixel comparison. */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { parentFile.mkdirs() }
    val width = 256; val height = 160
    val base = MeloXGlassFilterKey(160f, 64f, 48f, 2f, listOf(32f, 32f, 32f, 32f), MeloXGlassOptics())
    val geometry = JSONArray()
    for ((name, shape) in listOf("capsule" to MeloXShapes.capsule, "compact" to MeloXShapes.compact,
        "card" to MeloXShapes.card, "largeCard" to MeloXShapes.largeCard, "sheet" to MeloXShapes.sheet)) {
        for (density in listOf(1f, 1.25f, 2f)) {
            val size = Size(240f * density, 80f * density)
            val outline = shape.createOutline(size, LayoutDirection.Ltr, Density(density))
            val radii = shape.glassCornerRadii(size, LayoutDirection.Ltr, Density(density), outline)
            check(radii.size == 4 && radii.all { it.isFinite() && it >= 0f }) { "$name has no usable lens radii" }
            if (name != "sheet") check(outline is Outline.Generic) { "$name lost its continuous geometry" }
            geometry.put(JSONObject().put("shape", name).put("density", density).put("radii", JSONArray(radii)))
        }
    }
    val uneven = ContinuousRoundedRectangle(topStart = 4.dp, topEnd = 8.dp, bottomEnd = 12.dp, bottomStart = 16.dp)
    check(uneven.glassCornerRadii(Size(240f, 80f), LayoutDirection.Rtl, Density(1f), null) == listOf(8f, 4f, 16f, 12f))
    Surface.makeRasterN32Premul(width, height).use { source ->
        Paint().use { paint ->
            for (y in 0 until height step 8) for (x in 0 until width step 8) {
                paint.color = if ((x / 8 + y / 8) % 2 == 0) 0xff2563bd.toInt() else 0xffe9c742.toInt()
                source.canvas.drawRect(Rect.makeXYWH(x.toFloat(), y.toFloat(), 8f, 8f), paint)
            }
        }
        source.makeImageSnapshot().use { input ->
            fun render(filter: ImageFilter?): IntArray = Surface.makeRasterN32Premul(width, height).use { target ->
                Paint().use { paint -> paint.imageFilter = filter; target.canvas.drawImage(input, 0f, 0f, paint) }
                target.makeImageSnapshot().use { image -> Bitmap().use { bitmap ->
                    check(bitmap.allocN32Pixels(width, height)); check(image.readPixels(bitmap))
                    IntArray(width * height) { bitmap.getColor(it % width, it / width) }
                } }
            }
            val owner = MeloXGlassFilterOwner()
            val timing = JSONArray()
            try {
                owner.obtain(base.copy(blur = 0f))
                check(owner.nativeFilter == null && owner.creations == 0) { "No-effects sampling allocated a chain" }
                owner.obtain(base)
                val baseline = ImageFilter.makeBlur(2f, 2f, FilterTileMode.CLAMP).use { render(it) }
                check(baseline.contentEquals(render(owner.nativeFilter))) { "Cached blur changed pixels" }
                val original = render(null)
                check(!original.contentEquals(baseline)) { "Blur was inert" }
                owner.obtain(base.copy(width = 0f, optics = MeloXGlassOptics(12f, 24f, dispersion = true)))
                check(baseline.contentEquals(render(owner.nativeFilter))) { "Zero-size lens evaluated invalid sampling coordinates" }
                val lensKey = base.copy(optics = MeloXGlassOptics(12f, 24f))
                owner.obtain(lensKey)
                val refracted = render(owner.nativeFilter)
                owner.obtain(lensKey.copy(optics = lensKey.optics.copy(dispersion = true)))
                val dispersed = render(owner.nativeFilter)
                check(!refracted.contentEquals(baseline)) { "Lens was inert" }
                check(!refracted.contentEquals(dispersed)) { "Chromatic dispersion was inert" }
                owner.obtain(base.copy(optics = MeloXGlassOptics(vibrant = true)))
                check(!baseline.contentEquals(render(owner.nativeFilter))) { "Vibrancy was inert" }
                val opaque = dispersed.withIndex().filter { (i, _) -> i % width in 80..175 && i / width in 64..95 }
                check(opaque.all { (_, pixel) -> pixel ushr 24 >= 250 }) { "Center sampling produced transparency" }
                owner.obtain(base.copy(optics = MeloXGlassOptics(0f, 0f, dispersion = true)))
                check(baseline.contentEquals(render(owner.nativeFilter))) { "Zero lens must be pixel-identical to no lens" }

                // Fixed-input raster benchmark includes Skia's filter-result reuse;
                // it does not isolate filter construction or measure changing app frames.
                val frames = 600
                Surface.makeRasterN32Premul(width, height).use { target -> Paint().use { paint ->
                    repeat(30) { owner.obtain(base); paint.imageFilter = owner.nativeFilter; target.canvas.drawImage(input, 0f, 0f, paint) }
                    val before = owner.creations
                    val cachedNs = measureNanoTime {
                        repeat(frames) { owner.obtain(base); paint.imageFilter = owner.nativeFilter; target.canvas.drawImage(input, 0f, 0f, paint) }
                    }
                    check(owner.creations == before) { "Stable frames recreated a native filter" }
                    val uncachedNs = measureNanoTime {
                        repeat(frames) { ImageFilter.makeBlur(2f, 2f, FilterTileMode.CLAMP).use {
                            paint.imageFilter = it; target.canvas.drawImage(input, 0f, 0f, paint)
                        } }
                    }
                    paint.imageFilter = null
                    timing.put(JSONObject().put("frames", frames).put("cached_ns", cachedNs).put("uncached_ns", uncachedNs)
                        .put("cached_filter_creations", owner.creations - before).put("uncached_filter_creations", frames))
                } }
            } finally { owner.close() }
            check(owner.closed)
            check(safeGlassProgress(Float.NaN) == 0f && safeGlassProgress(Float.POSITIVE_INFINITY) == 0f)
            check(safeGlassProgress(1.2f) == 1f && safeGlassProgress(-.2f) == 0f)
            check(owner.creations == owner.releases) { "Not every owned filter was released" }
            check(runCatching { owner.obtain(base) }.isFailure) { "Disposed sampler accepted work" }
            repeat(64) {
                val changing = MeloXGlassFilterOwner()
                try {
                    repeat(16) { frame ->
                        changing.obtain(base.copy(optics = MeloXGlassOptics(frame.toFloat(), frame.toFloat(), dispersion = true)))
                        render(changing.nativeFilter)
                    }
                    changing.obtain(base.copy(blur = 0f))
                    check(changing.nativeFilter == null)
                } finally { changing.close() }
                changing.close() // Idempotent disposal after an effects-to-plain transition.
                check(changing.creations == changing.releases)
            }
            output.writeText(JSONObject().put("status", "PASSED").put("os", System.getProperty("os.name"))
                .put("blur_cached_pixels_identical", true).put("zero_lens_pixels_identical", true)
                .put("lens_changes_background_pixels", true).put("dispersion_changes_pixels", true)
                .put("vibrancy_changes_pixels", true).put("geometry", geometry)
                .put("ownership_stress", "64 owners x 16 parameter changes; plain transition and double close")
                .put("benchmark_scope", "fixed-input raster including Skia filter-result cache; not isolated construction cost")
                .put("owned_creations", owner.creations).put("owned_releases", owner.releases)
                .put("cache_hits", owner.cacheHits).put("timing", timing)
                .put("android_pixels", "PENDING: requires pinned reference frames")
                .put("production_frame_time", "PENDING: raster microbenchmark is not app FPS")
                .put("windows_549_to_1431_memory", "UNRESOLVED").toString(2) + "\n")
            println("PASS: real blur/lens/dispersion pixels, stable-filter reuse, complete disposal")
        }
    }
    auditMeloXGlassScene(File(output.parentFile, "glass-scene"))
}
