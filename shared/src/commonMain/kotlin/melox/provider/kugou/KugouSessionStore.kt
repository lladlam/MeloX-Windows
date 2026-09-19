package melox.provider.kugou

import melox.platform.getPreferences
import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class KugouSession(
    val token: String,
    val userId: Long,
    val vipToken: String,
    val vipType: Int,
    val dfid: String,
    val mid: String,
    val guid: String,
    val dev: String,
    val mac: String,
    val webGl: String,
) {
    val isLoggedIn: Boolean
        get() = token.isNotBlank() && userId > 0L

    fun asCookieMap(): Map<String, String> = buildMap {
        put("KUGOU_API_MID", mid)
        put("KUGOU_API_GUID", guid)
        put("KUGOU_API_DEV", dev)
        put("KUGOU_API_MAC", mac)
        put("KUGOU_API_WEBGL", webGl)
        if (dfid.isNotBlank()) put("dfid", dfid)
        if (token.isNotBlank()) put("token", token)
        if (userId > 0L) put("userid", userId.toString())
        if (vipToken.isNotBlank()) put("vip_token", vipToken)
        put("vip_type", vipType.toString())
    }
}

/**
 * Keeps Kugou's device identity separate from its user login state. Clearing an
 * account therefore does not silently turn the phone into a new Kugou device.
 */
object KugouSessionStore {
    private const val PreferencesName = "melox_kugou_session"
    private const val PlaybackPreferencesName = "melox_kugou_playback_session"
    private const val Token = "token"
    private const val UserId = "userid"
    private const val VipToken = "vip_token"
    private const val VipType = "vip_type"
    private const val Dfid = "dfid"
    private const val Guid = "guid"
    private const val Mid = "mid"
    private const val Dev = "dev"
    private const val Mac = "mac"
    private const val WebGl = "webgl"

    private fun prefs(playback: Boolean) =
        getPreferences(if (playback) PlaybackPreferencesName else PreferencesName)

    fun read(playback: Boolean = false): KugouSession {
        val preferences = prefs(playback)
        val identity = if (playback) read(false).let { DeviceIdentity(it.guid, it.mid, it.dev, it.mac, it.webGl) }
        else ensureIdentity()
        return KugouSession(
            token = preferences.getString(Token, "").orEmpty(),
            userId = preferences.getLong(UserId, 0L),
            vipToken = preferences.getString(VipToken, "").orEmpty(),
            vipType = preferences.getInt(VipType, 0),
            dfid = preferences.getString(Dfid, "-").orEmpty().ifBlank { "-" },
            mid = identity.mid,
            guid = identity.guid,
            dev = identity.dev,
            mac = identity.mac,
            webGl = identity.webGl,
        )
    }

    fun updateLogin(
        token: String,
        userId: Long,
        vipToken: String = "",
        vipType: Int = 0,
        dfid: String? = null,
        playback: Boolean = false,
    ): KugouSession {
        prefs(playback).apply {
            putString(Token, token)
            putLong(UserId, userId)
            putString(VipToken, vipToken)
            putInt(VipType, vipType)
            dfid?.takeIf(String::isNotBlank)?.let { putString(Dfid, it) }
        }
        return read(playback)
    }

    fun updateDfid(value: String) {
        if (value.isBlank()) return
        prefs(false).putString(Dfid, value)
    }

    fun clearLogin(playback: Boolean = false) {
        prefs(playback).apply {
            remove(Token)
            remove(UserId)
            remove(VipToken)
            remove(VipType)
        }
    }

    private fun ensureIdentity(): DeviceIdentity {
        val preferences = prefs(false)
        val existingGuid = preferences.getString(Guid, null)
        val guid = existingGuid?.takeIf(String::isNotBlank) ?: md5Hex(UUID.randomUUID().toString())
        val mid = preferences.getString(Mid, null)?.takeIf(String::isNotBlank)
            ?: calculateMid(guid)
        val dev = preferences.getString(Dev, null)?.takeIf(String::isNotBlank)
            ?: randomUppercase(10)
        val mac = preferences.getString(Mac, null)?.takeIf(String::isNotBlank)
            ?: "02:00:00:00:00:00"
        val webGl = preferences.getString(WebGl, null)?.takeIf(String::isNotBlank)
            ?: BigInteger(64, SecureRandom()).toString()
        preferences.apply {
            putString(Guid, guid)
            putString(Mid, mid)
            putString(Dev, dev)
            putString(Mac, mac)
            putString(WebGl, webGl)
        }
        return DeviceIdentity(guid, mid, dev, mac, webGl)
    }

    private fun calculateMid(guid: String): String =
        BigInteger(md5Hex(guid), 16).toString(10)

    private fun md5Hex(value: String): String =
        MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun randomUppercase(length: Int): String {
        val alphabet = "1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) }
        }
    }

    private data class DeviceIdentity(
        val guid: String,
        val mid: String,
        val dev: String,
        val mac: String,
        val webGl: String,
    )
}