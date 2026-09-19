package melox.network

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class MeloXListenTogetherInvitation(
    val roomId: String,
    val inviterId: String,
    val songId: Long?,
)

/**
 * Parses both legacy and current NetEase Listen Together invitation formats.
 *
 * NetEase currently emits at least these variants:
 * - /listen-together/share/?roomId=...&inviterId=...
 * - /listen-together/multishare/index.html?roomId=...&inviterUid=...
 *
 * The clipboard can also contain surrounding text or HTML-escaped ampersands,
 * so parsing intentionally scans the whole text instead of depending on one
 * fixed URL path.
 */
fun parseNeteaseListenTogetherInvitation(text: String): MeloXListenTogetherInvitation? {
    val normalized = text.trim().replace("&amp;", "&", ignoreCase = true)
    if (normalized.isBlank()) return null

    val values = mutableMapOf<String, String>()
    QUERY_PARAMETER.findAll(normalized).forEach { match ->
        val key = match.groupValues[1].lowercase()
        val value = decode(match.groupValues[2]).trim()
        if (value.isNotBlank() && key !in values) values[key] = value
    }

    val roomId = values["roomid"]?.takeIf(String::isNotBlank) ?: return null
    val inviterId = values["inviterid"]
        ?.takeIf(String::isNotBlank)
        ?: values["inviteruid"]?.takeIf(String::isNotBlank)
        ?: return null

    return MeloXListenTogetherInvitation(
        roomId = roomId,
        inviterId = inviterId,
        songId = values["songid"]?.toLongOrNull(),
    )
}

private fun decode(value: String): String = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrDefault(value)

private val QUERY_PARAMETER = Regex(
    pattern = "(?i)(?:[?&]|^)(roomId|inviterId|inviterUid|songId)=([^&#\\s)\\]]+)",
)
