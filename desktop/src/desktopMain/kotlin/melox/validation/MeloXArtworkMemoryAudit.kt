package melox.validation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import melox.diagnostics.MeloXMemoryDiagnostics
import melox.ui.artwork.MeloXArtworkMemoryCache
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Validation-only generated pixels. Exercises the same cache used by the real UI.
 * No forced GC: native pixels must release on eviction/last lease release.
 */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { mkdirs() }
    val samples = JSONArray()
    val paint = Paint()
    repeat(8) { batch ->
        repeat(256) { index ->
            val image = ImageBitmap(512, 512)
            paint.color = Color(0xFF000000L or (batch * 256 + index).toLong())
            val canvas = Canvas(image)
            try {
                canvas.drawRect(Rect(0f, 0f, 512f, 512f), paint)
            } finally {
                canvas.nativeCanvas.close()
            }
            val key = "validation-pixels-${batch * 256 + index}"
            MeloXArtworkMemoryCache.publishAndAcquire(key, image).use { lease ->
                check(lease.value === image)
                MeloXArtworkMemoryCache.acquire(key)!!.use { check(it.value === image) }
            }
        }
        Thread.sleep(300)
        val stats = MeloXArtworkMemoryCache.stats()
        check(stats.entries == 32 && stats.retainedWeight == 64L * 1024 * 1024)
        val memory = MeloXMemoryDiagnostics.snapshot()
        check(memory.getValue("resident_bytes") > 0) { "OS process memory probe did not return resident bytes" }
        if (System.getProperty("os.name").startsWith("Windows")) check(memory.getValue("private_commit_bytes") > 0)
        samples.put(JSONObject(memory).put("batch", batch).put("images_loaded", (batch + 1) * 256)
            .put("cache_entries", stats.entries).put("cache_budgeted_bytes", stats.retainedWeight))
        println("batch=$batch ${MeloXMemoryDiagnostics.describe()} cache=$stats")
    }
    val warm = samples.getJSONObject(2)
    val last = samples.getJSONObject(7)
    val residentGrowth = last.getLong("resident_bytes") - warm.getLong("resident_bytes")
    val heapGrowth = last.getLong("heap_used_bytes") - warm.getLong("heap_used_bytes")
    check(residentGrowth <= 128L * 1024 * 1024) { "Post-warmup resident memory grew by $residentGrowth bytes" }
    check(heapGrowth <= 16L * 1024 * 1024) { "Post-warmup live heap grew by $heapGrowth bytes" }
    MeloXArtworkMemoryCache.clear()
    check(MeloXArtworkMemoryCache.stats().entries == 0)
    File(output, "audit.json").writeText(JSONObject()
        .put("status", "PASSED").put("validation_generated_pixels", true).put("forced_gc", false)
        .put("user_windows_playback_acceptance", "PENDING")
        .put("images_loaded", 2048).put("allocated_decoded_pixel_bytes", 2048L * 512 * 512 * 4)
        .put("post_warmup_resident_growth_bytes", residentGrowth).put("post_warmup_used_heap_growth_bytes", heapGrowth)
        .put("samples", samples).toString(2) + "\n")
    println("PASS: 2048 distinct decoded images; bounded retention and post-warmup memory plateau")
}
