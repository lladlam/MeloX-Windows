package melox.provider.jellyfin

import melox.platform.getSecurePreferences as getPreferences
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Preserve the existing IV+ciphertext session format; its stable AES key lives inside the same DPAPI store. */
object JellyfinSessionStore {
    private val mutableRevision = MutableStateFlow(0L)
    val revision = mutableRevision.asStateFlow()
    private fun preferences() = getPreferences("melox_jellyfin_session")
    private val persistence = JellyfinSessionPersistence(
        { key -> preferences().getString(key, null) },
        { values -> preferences().putValues(values) },
    )

    @Synchronized fun read(): JellyfinSession = persistence.read()
    @Synchronized fun write(session: JellyfinSession) {
        persistence.write(session)
        mutableRevision.value++
    }
    @Synchronized fun clear() {
        persistence.clear()
        mutableRevision.value++
    }
}

/** Storage callbacks keep encryption/persistence regression checks independent of native DPAPI. */
internal class JellyfinSessionPersistence(
    private val get: (String) -> String?,
    private val put: (Map<String, String?>) -> Unit,
) {
    private val SessionKey = "encrypted_session"
    private val KeyAlias = "melox_jellyfin_session_key"

    @Synchronized fun read(): JellyfinSession {
        val encoded = get(SessionKey) ?: return JellyfinSession()
        return runCatching {
            val packed = Base64.getDecoder().decode(encoded)
            val iv = packed.copyOfRange(0, 12)
            val encrypted = packed.copyOfRange(12, packed.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            require(packed.size >= 28)
            // Old desktop builds generated a random, unpersisted key on every call.
            // Never fabricate a replacement key while attempting to read those bytes.
            val key = requireNotNull(storedKey())
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            val json = JSONObject(String(decrypted, Charsets.UTF_8))
            JellyfinSession(
                serverUrl = json.optString("serverUrl"),
                accessToken = json.optString("accessToken"),
                userId = json.optString("userId"),
                userName = json.optString("userName"),
                serverId = json.optString("serverId"),
            )
        }.getOrElse { JellyfinSession() }
    }

    @Synchronized fun write(session: JellyfinSession) {
        require(session.isLoggedIn) { "不能保存不完整的 Jellyfin 登录会话" }
        val json = JSONObject().apply {
            put("serverUrl", session.serverUrl)
            put("accessToken", session.accessToken)
            put("userId", session.userId)
            put("userName", session.userName)
            put("serverId", session.serverId)
        }.toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val key = storedKey() ?: javax.crypto.KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val iv = cipher.iv
        val packed = iv + encrypted
        put(mapOf(
            KeyAlias to Base64.getEncoder().encodeToString(key.encoded),
            SessionKey to Base64.getEncoder().encodeToString(packed),
        ))
        check(read() == session) { "Jellyfin 安全会话保存校验失败" }
    }

    @Synchronized fun clear() {
        put(mapOf(SessionKey to null, KeyAlias to null))
    }

    private fun storedKey(): javax.crypto.SecretKey? = get(KeyAlias)?.let {
        runCatching { Base64.getDecoder().decode(it).also { bytes -> require(bytes.size == 32) } }
            .getOrNull()?.let { bytes -> SecretKeySpec(bytes, "AES") }
    }
}
