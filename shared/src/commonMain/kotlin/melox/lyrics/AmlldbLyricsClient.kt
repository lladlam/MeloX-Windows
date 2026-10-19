package melox.lyrics

import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Android main's official API + raw mirror race, with cancellable JVM HTTP. */
class AmlldbLyricsClient(
    httpClient: OkHttpClient = melox.network.MeloXHttpClient.shared,
) {
    private val httpClient = httpClient.newBuilder().callTimeout(12, TimeUnit.SECONDS).build()

    suspend fun lyrics(neteaseSongId: Long, script: MeloXLyricScript = MeloXLyricScript.Original): LyricsDocument =
        lyrics(AmlldbLyricQuery.Netease(neteaseSongId), script)

    suspend fun lyrics(query: AmlldbLyricQuery, script: MeloXLyricScript = MeloXLyricScript.Original): LyricsDocument =
        lyrics(query, MeloXLyricPreference(null, script))

    suspend fun lyrics(neteaseSongId: Long, preference: MeloXLyricPreference): LyricsDocument =
        lyrics(AmlldbLyricQuery.Netease(neteaseSongId), preference)

    suspend fun lyrics(query: AmlldbLyricQuery, preference: MeloXLyricPreference): LyricsDocument =
        coroutineScope {
            val requests = buildList {
                query.officialParameter()?.let { (name, value) ->
                    add(Request.Builder().url("https://api.amll.dev/api/v1/lyrics/get".toHttpUrl()
                        .newBuilder().addQueryParameter(name, value).build())
                        .header("Accept", "application/json").build() to true)
                }
                query.rawPaths().forEach { path ->
                    RawMirrors.forEach { mirror ->
                        add(Request.Builder().url(mirror + path)
                            .header("Accept", "application/xml,text/xml,*/*").build() to false)
                    }
                }
            }
            if (requests.isEmpty()) return@coroutineScope LyricsDocument(emptyList())
            val results = Channel<LyricsDocument?>(requests.size)
            val jobs = requests.map { (request, official) ->
                launch {
                    val document = try {
                        val body = httpClient.newCall(request.newBuilder().header("User-Agent", "MeloX-Windows").build()).text()
                        val ttml = if (official) body?.let { JSONObject(it).optJSONObject("data")?.optString("lyrics") } else body
                        ttml?.takeIf { it.contains("http://www.w3.org/ns/ttml") }
                            ?.let { TtmlLyricsParser.parse(it, preference) }?.takeIf { it.lines.isNotEmpty() }
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                    } catch (_: Exception) { null }
                    results.send(document)
                }
            }
            try {
                repeat(requests.size) {
                    results.receive()?.let { return@coroutineScope it }
                }
                LyricsDocument(emptyList())
            } finally {
                jobs.forEach { it.cancel() }
                results.cancel()
            }
        }

    private suspend fun Call.text(): String? = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.use { if (it.isSuccessful) it.body?.string() else null }
                    if (continuation.isActive) continuation.resume(body)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }

    private companion object {
        val RawMirrors = listOf(
            "https://amll.mirror.dimeta.top/api/db/",
            "https://amll-ttml-db.gbclstudio.cn/",
            "https://raw.githubusercontent.com/amll-dev/amll-ttml-db/refs/heads/main/",
            "https://amll-ttml-db.stevexmh.net/",
        )
    }
}

sealed interface AmlldbLyricQuery {
    data class Netease(val songId: Long) : AmlldbLyricQuery
    data class QQ(val songId: String) : AmlldbLyricQuery
    data class Spotify(val trackId: String) : AmlldbLyricQuery
    data class AppleMusic(val songId: String) : AmlldbLyricQuery

    fun officialParameter(): Pair<String, String>? = when (this) {
        is Netease -> "ncmMusicId" to songId.toString()
        is QQ -> songId.takeIf(String::isNotBlank)?.let { "qqMusicId" to it }
        is Spotify -> trackId.takeIf(String::isNotBlank)?.let { "spotifyId" to it }
        is AppleMusic -> songId.takeIf(String::isNotBlank)?.let { "appleMusicId" to it }
    }

    fun bindingValue(): String = when (this) {
        is Netease -> songId.toString()
        is QQ -> "qq:$songId"
        is Spotify -> "spotify:$trackId"
        is AppleMusic -> "apple:$songId"
    }

    fun rawPaths(): List<String> = when (this) {
        is Netease -> listOf("ncm-lyrics/$songId.ttml")
        is QQ -> songId.takeIf(String::isNotBlank)?.let { listOf("qq-lyrics/$it.ttml", "qq-lyrics/$it.qrc") }.orEmpty()
        is Spotify -> trackId.takeIf(String::isNotBlank)?.let { listOf("spotify-lyrics/$it.ttml") }.orEmpty()
        is AppleMusic -> songId.takeIf(String::isNotBlank)?.let { listOf("am-lyrics/$it.ttml") }.orEmpty()
    }

    companion object {
        fun fromBinding(value: String): AmlldbLyricQuery? {
            value.toLongOrNull()?.let { return Netease(it) }
            val id = value.substringAfter(':', "").takeIf(String::isNotBlank) ?: return null
            return when (value.substringBefore(':')) {
                "qq" -> QQ(id)
                "spotify" -> Spotify(id)
                "apple" -> AppleMusic(id)
                else -> null
            }
        }
    }
}
