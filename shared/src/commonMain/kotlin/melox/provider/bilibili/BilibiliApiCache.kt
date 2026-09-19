package melox.provider.bilibili

import melox.platform.meloXCacheDir
import java.io.File
import java.security.MessageDigest
import java.util.LinkedHashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class BilibiliApiCache private constructor() {
    data class Policy(val memoryTtlMs: Long, val diskTtlMs: Long? = null)
    private data class Entry(val value: String, val storedAtMs: Long, val expiresAtMs: Long)
    private data class MixinEntry(val value: String, val expiresAtMs: Long)

    private val directory = File(meloXCacheDir(), "bilibili_metadata")
    private val lock = Any()
    private val memory = object : LinkedHashMap<String, Entry>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > MaxMemoryEntries
    }
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<JSONObject>>()
    private val mixinKeys = ConcurrentHashMap<String, MixinEntry>()

    fun getOrLoad(key: String, policy: Policy, loader: () -> JSONObject): JSONObject {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            memory[key]?.takeIf { now < it.expiresAtMs }?.let { return JSONObject(it.value) }
            memory.remove(key)
        }
        policy.diskTtlMs?.let { diskTtl ->
            readDisk(key, now, diskTtl)?.let { value ->
                synchronized(lock) { memory[key] = Entry(value, now, now + policy.memoryTtlMs) }
                return JSONObject(value)
            }
        }
        val pending = CompletableFuture<JSONObject>()
        val existing = inFlight.putIfAbsent(key, pending)
        if (existing != null) return JSONObject(existing.get().toString())
        return try {
            val loaded = loader()
            require(loaded.optInt("code", -1) == 0) { "Only successful Bilibili responses are cacheable" }
            val value = loaded.toString()
            synchronized(lock) { memory[key] = Entry(value, now, now + policy.memoryTtlMs) }
            policy.diskTtlMs?.let { writeDisk(key, value, now) }
            JSONObject(value).also { pending.complete(JSONObject(value)) }
        } catch (error: Throwable) {
            pending.completeExceptionally(error)
            throw error
        } finally {
            inFlight.remove(key, pending)
        }
    }

    fun mixinKey(scope: String, loader: () -> String): String {
        val now = System.currentTimeMillis()
        mixinKeys[scope]?.takeIf { now < it.expiresAtMs }?.let { return it.value }
        return synchronized(mixinKeys) {
            mixinKeys[scope]?.takeIf { now < it.expiresAtMs }?.value ?: loader().also {
                mixinKeys[scope] = MixinEntry(it, now + MixinTtlMs)
            }
        }
    }

    fun invalidateMixin(scope: String) { mixinKeys.remove(scope) }

    private fun readDisk(key: String, now: Long, ttlMs: Long): String? = runCatching {
        val json = JSONObject(fileFor(key).readText())
        val storedAt = json.getLong("storedAtMs")
        if (now - storedAt > ttlMs) return@runCatching null
        json.getJSONObject("payload").toString()
    }.getOrNull()

    private fun writeDisk(key: String, value: String, now: Long) = runCatching {
        directory.mkdirs()
        val target = fileFor(key)
        val temp = File(directory, target.name + ".tmp")
        temp.writeText(JSONObject().put("storedAtMs", now).put("payload", JSONObject(value)).toString())
        if (!temp.renameTo(target)) {
            target.delete()
            temp.renameTo(target)
        }
    }

    private fun fileFor(key: String) = File(directory, sha256(key) + ".json")

    companion object {
        const val MaxMemoryEntries = 200
        const val MixinTtlMs = 5_000L
        fun shared(): BilibiliApiCache = BilibiliApiCache()

        fun cacheKey(scope: String, operation: String, params: Map<String, String>): String =
            "$scope|$operation|" + params.toSortedMap().entries.joinToString("&") { "${it.key}=${it.value}" }

        fun normalizeSearchQuery(value: String): String = value.trim().lowercase().replace(Regex("\\s+"), " ")

        fun isFresh(storedAtMs: Long, ttlMs: Long, nowMs: Long): Boolean =
            ttlMs > 0 && nowMs >= storedAtMs && nowMs - storedAtMs < ttlMs

        fun playbackExpiry(url: String, nowMs: Long = System.currentTimeMillis()): Long {
            val deadline = Regex("(?:[?&](?:deadline|expires)=)(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull()
            val parsed = deadline?.times(1_000L)?.minus(30_000L)
            return parsed?.takeIf { it > nowMs } ?: nowMs + 90_000L
        }

        private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
