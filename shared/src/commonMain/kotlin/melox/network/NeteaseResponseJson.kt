package melox.network

import kotlinx.serialization.json.Json
import org.json.JSONObject

/** Android JSONObject accepts duplicate provider keys (last value wins).
 * org.json on desktop rejects these, including real artist responses that
 * repeat `followed`. Normalize the parsed object map, retaining JSON numbers,
 * strings and nested arrays; do not rewrite the payload with regular expressions.
 */
internal fun parseNeteaseResponseJson(raw: String): JSONObject = JSONObject(Json.parseToJsonElement(raw).toString())
