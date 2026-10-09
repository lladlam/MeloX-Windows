package melox.lyrics

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** LrcLib fallback for catalogues without native synchronized lyrics (Android main). */
class LrcLibLyricsClient(
    private val httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    suspend fun lyrics(title: String, artist: String, durationMs: Long): LyricsDocument =
        withContext(Dispatchers.IO) {
            val cleaned = title.substringBefore(" (").substringBefore("（").trim().ifBlank { title }
            val body = get(url(cleaned, artist, durationMs))
                ?: get(url(cleaned, artist, durationMs = null))
                ?: return@withContext LyricsDocument(emptyList())
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext LyricsDocument(emptyList())
            json.optString("syncedLyrics").takeIf(String::isNotBlank)?.let(::parseLrc)
                ?: json.optString("plainLyrics").takeIf(String::isNotBlank)?.let { plain ->
                    LyricsDocument(
                        lines = plain.lines().filter(String::isNotBlank).map { LyricLine(timeMs = 0L, text = it.trim()) },
                        source = LyricSource.Local,
                        quality = LyricQuality.Fallback,
                        pseudoTimingAllowed = false,
                    )
                } ?: LyricsDocument(emptyList())
        }

    private fun url(title: String, artist: String, durationMs: Long?): String = buildString {
        append("https://lrclib.net/api/get?track_name=")
        append(URLEncoder.encode(title, Charsets.UTF_8.name()))
        append("&artist_name=")
        append(URLEncoder.encode(artist, Charsets.UTF_8.name()))
        if (durationMs != null && durationMs > 0) append("&duration=").append(durationMs / 1000)
    }

    private fun get(url: String): String? = runCatching {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    /** Preserve timed blank lines: they mark instrumental gaps. */
    internal fun parseLrc(raw: String): LyricsDocument {
        val lines = raw.lines().mapNotNull { line ->
            val match = LRC_LINE.find(line) ?: return@mapNotNull null
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLong()
            LyricLine(
                timeMs = minutes * 60_000 + seconds * 1_000 + fraction,
                text = match.groupValues[4].trim(),
                timingKind = LyricTimingKind.LineSynchronized,
            )
        }
        return LyricsDocument(
            lines = lines,
            source = LyricSource.Local,
            quality = if (lines.isEmpty()) LyricQuality.Fallback else LyricQuality.LineSynchronized,
            pseudoTimingAllowed = false,
        )
    }

    private companion object {
        val LRC_LINE = Regex("\\[(\\d{1,2}):(\\d{2})[.:](\\d{2,3})](.*)")
        const val USER_AGENT = "MeloX (https://github.com/lladlam/MeloX-Windows)"
    }
}
