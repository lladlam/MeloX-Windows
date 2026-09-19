package melox.provider.bilibili

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object BilibiliWbiSigner {
    private val MixinKeyTable = intArrayOf(
        46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,
        12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,
        57,62,11,36,20,34,44,52,
    )
    private val Forbidden = Regex("[!'()*]")

    fun mixinKey(imgUrl: String, subUrl: String): String {
        val source = fileStem(imgUrl) + fileStem(subUrl)
        return buildString {
            MixinKeyTable.forEach { index -> source.getOrNull(index)?.let(::append) }
        }.take(32)
    }

    fun sign(params: Map<String, String>, mixinKey: String, timestampSeconds: Long): Map<String, String> {
        val unsigned = params.toMutableMap().apply {
            remove("w_rid")
            put("wts", timestampSeconds.toString())
            putIfAbsent("web_location", "1550101")
        }
        val query = unsigned.toSortedMap().entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value.replace(Forbidden, ""))}"
        }
        return unsigned + ("w_rid" to md5(query + mixinKey))
    }

    private fun fileStem(url: String): String = url.substringAfterLast('/').substringBefore('.')
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
    private fun md5(value: String): String = MessageDigest.getInstance("MD5").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
