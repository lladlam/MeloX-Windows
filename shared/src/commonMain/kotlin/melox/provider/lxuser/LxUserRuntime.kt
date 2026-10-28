package melox.provider.lxuser

import com.caoccao.javet.annotations.V8Function
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import com.caoccao.javet.values.reference.V8ValueObject
import java.io.IOException
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import okhttp3.Authenticator
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

/** A V8 isolate owned by one thread, with no Node or application-account bridge. */
class LxUserRuntime(private val httpClient: OkHttpClient = isolatedClient()) : AutoCloseable {
    private val owner = Executors.newSingleThreadExecutor { Thread(it, "melox-lx-v8").apply { isDaemon = true } }
    private val closed = AtomicBoolean(false)
    private val calls = ConcurrentHashMap<Int, Call>()
    private val responses = ConcurrentLinkedQueue<PendingResponse>()
    private val wake = Object()
    private lateinit var runtime: V8Runtime
    private lateinit var hostBridge: LxHostBridge
    private lateinit var boundBridge: V8ValueObject
    private var loaded = false
    private var declarations: Map<String, Map<String, Any?>> = emptyMap()

    private data class PendingResponse(val id: Int, val error: String?, val response: Map<String, Any?>?, val body: Any?)

    init {
        try {
            ownerCall {
                runtime = V8Host.getV8Instance().createV8Runtime<V8Runtime>()
                runtime.setNearHeapLimitCallback { current, _ ->
                    runtime.terminateExecution()
                    if (current > Long.MAX_VALUE / 2) Long.MAX_VALUE else current * 2
                }
                boundBridge = runtime.createV8ValueObject()
                hostBridge = LxHostBridge(::request, ::cancel, ::crypto)
                boundBridge.bind(hostBridge)
                runtime.globalObject.set("__lxNative", boundBridge)
                val prelude = requireNotNull(javaClass.classLoader.getResourceAsStream("LxUser/lx-prelude.js")) {
                    "Missing LX runtime resource"
                }.bufferedReader(Charsets.UTF_8).use { it.readText() }
                runtime.getGuard(5_000).use { runtime.getExecutor(prelude).executeVoid() }
                runtime.allowEval(false)
            }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun load(script: LxUserScript) = ownerCall {
        if (loaded) return@ownerCall
        require(script.source.toByteArray(Charsets.UTF_8).size <= 9_000_000) { "音乐源脚本过大" }
        val metadata = LxUserScriptMetadata.parse(script.source)
        runtime.getGuard(10_000).use {
            evaluate("__lxInfo(${json(mapOf("name" to metadata.name.orEmpty(), "version" to metadata.version.orEmpty(),
                "author" to metadata.author.orEmpty(), "description" to metadata.description.orEmpty(),
                "homepage" to metadata.homepage.orEmpty(), "rawScript" to script.source))})")
            evaluate(script.source)
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            do {
                pump()
                if (runtime.getExecutor("__lxReady()").executeBoolean()) break
                waitForWork(10)
            } while (System.nanoTime() < deadline)
            declarations = jsonObject(runtime.getExecutor("__lxDeclarations()").executeString())
                .mapValues { (_, value) -> (value as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty() }
            loaded = true
        }
    }

    fun callAction(action: String, vararg args: Any?): Any? {
        require(action in setOf("musicUrl", "lyric", "pic")) { "Unsupported LX action" }
        val input = args.firstOrNull() as? Map<*, *> ?: emptyMap<Any?, Any?>()
        try {
            return ownerCall {
                check(loaded) { "LX script is not loaded" }
                val source = input["source"]?.toString() ?: "kw"
                check(supportsSourceOnOwner(source) && supportsActionOnOwner(source, action)) { "LX source does not declare $action" }
                runtime.getGuard(20_000).use {
                    evaluate("__lxStart(${JSONObject.quote(action)},${json(input)})")
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
                    while (System.nanoTime() < deadline) {
                        check(!closed.get()) { "LX runtime is closed" }
                        pump()
                        val state = jsonObject(runtime.getExecutor("__lxResult()").executeString())
                        if (state["done"] == true) {
                            state["error"]?.let { throw IllegalStateException(it.toString()) }
                            return@ownerCall state["value"]
                        }
                        waitForWork(10)
                    }
                    throw TimeoutException("LX script action timed out")
                }
            }
        } catch (error: Throwable) {
            if (error is TimeoutException || error is InterruptedException || error.javaClass.simpleName.contains("Terminated")) close()
            throw error
        }
    }

    fun supportsSource(source: String): Boolean = ownerCall { supportsSourceOnOwner(source) }
    fun supportsAction(source: String, action: String): Boolean = ownerCall { supportsActionOnOwner(source, action) }
    private fun supportsSourceOnOwner(source: String) = declarations.isEmpty() || declarations.containsKey(source)
    private fun supportsActionOnOwner(source: String, action: String): Boolean {
        val declared = declarations.values.any { (it["actions"] as? List<*>)?.isNotEmpty() == true }
        return !declared || action in (declarations[source]?.get("actions") as? List<*>).orEmpty()
    }
    fun qualityFor(source: String, requestedQuality: String): String = ownerCall {
        val supported = (declarations[source]?.get("qualitys") as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (supported.isEmpty() || requestedQuality in supported) return@ownerCall requestedQuality
        val index = QUALITY_ORDER.indexOf(requestedQuality).takeIf { it >= 0 } ?: QUALITY_ORDER.lastIndex
        QUALITY_ORDER.asReversed().firstOrNull { it in supported && QUALITY_ORDER.indexOf(it) <= index }
            ?: supported.firstOrNull() ?: requestedQuality
    }

    private fun pump() {
        while (true) {
            val item = responses.poll() ?: break
            evaluate("__lxDeliver(${item.id},${json(item.error)},${json(item.response)},${json(item.body)})")
        }
        evaluate("__lxTick(${System.currentTimeMillis()})")
        runtime.await()
    }
    private fun evaluate(source: String) { runtime.getExecutor(source).executeVoid() }
    private fun waitForWork(ms: Long) = synchronized(wake) {
        if (!closed.get() && responses.isEmpty()) wake.wait(ms)
    }
    private fun enqueue(item: PendingResponse) {
        if (closed.get()) return
        responses.add(item)
        synchronized(wake) { wake.notifyAll() }
    }

    private fun request(id: Int, url: String, optionsJson: String) {
        try {
            check(!closed.get())
            require(optionsJson.length <= MAX_BODY_BYTES) { "LX request too large" }
            val options = JSONObject(optionsJson)
            val method = options.optString("method", "GET").uppercase(Locale.ROOT)
            require(method in setOf("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE")) { "Unsupported request method" }
            val headers = options.optJSONObject("headers") ?: JSONObject()
            if (!headers.keySet().any { it.equals("Accept", true) }) headers.put("Accept", "application/json")
            if (!headers.keySet().any { it.equals("User-Agent", true) }) headers.put("User-Agent", USER_AGENT)
            val form = options.optJSONObject("form")
            val formData = options.optJSONObject("formData")
            val body = options.opt("body")
            if (method == "POST" && formData == null && !headers.keySet().any { it.equals("Content-Type", true) }) {
                headers.put("Content-Type", if (form != null) "application/x-www-form-urlencoded" else "application/json")
            }
            val contentType = headers.keySet().firstOrNull { it.equals("Content-Type", true) }?.let { headers.optString(it) }.orEmpty()
            val builder = Request.Builder().url(url)
            headers.keys().forEach { name ->
                // OkHttp must supply the actual boundary generated by MultipartBody.
                if (!headers.isNull(name) && !(formData != null && name.equals("Content-Type", true) &&
                    contentType.startsWith("multipart/form-data", true))) builder.addHeader(name, headers.get(name).toString())
            }
            if (method != "GET" && method != "HEAD") {
                val requestBody = when {
                    form != null -> form.keys().asSequence().joinToString("&") { key ->
                        java.net.URLEncoder.encode(key, Charsets.UTF_8) + "=" + java.net.URLEncoder.encode(form.optString(key), Charsets.UTF_8)
                    }.toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    formData != null -> MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                        formData.keys().forEach { key ->
                            val value = formData.opt(key)
                            if (value is JSONArray) addFormDataPart(key, "blob", bytes(value).toRequestBody("application/octet-stream".toMediaType()))
                            else if (value != null && value != JSONObject.NULL) addFormDataPart(key, value.toString())
                        }
                    }.build()
                    body is JSONArray -> bytes(body).toRequestBody(contentType.toMediaTypeOrNull())
                    body is JSONObject -> body.toString().toRequestBody(contentType.toMediaTypeOrNull())
                    body is String -> body.toRequestBody(contentType.toMediaTypeOrNull())
                    else -> ByteArray(0).toRequestBody(contentType.toMediaTypeOrNull())
                }
                builder.method(method, requestBody)
            } else builder.method(method, null)
            val timeout = options.optLong("timeout", 13_000).coerceIn(1_000, 60_000)
            val call = httpClient.newBuilder().callTimeout(timeout, TimeUnit.MILLISECONDS).build().newCall(builder.build())
            calls[id] = call
            if (closed.get()) { calls.remove(id); call.cancel(); return }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (calls.remove(id, call) && !call.isCanceled()) enqueue(PendingResponse(id, e.message ?: "request failed", null, null))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            require(it.body.contentLength() <= MAX_BODY_BYTES && !it.body.source().request(MAX_BODY_BYTES + 1L)) { "LX response too large" }
                            val data = it.body.bytes()
                            val resultBody: Any? = if (options.optBoolean("binary")) data.map { b -> b.toInt() and 255 }
                                else parseBody(data.toString(Charsets.UTF_8))
                            val result = mapOf("statusCode" to it.code, "statusMessage" to it.message,
                                "headers" to flattenLxResponseHeaders(it.headers.toMultimap()), "body" to resultBody,
                                "url" to it.request.url.toString(), "ok" to it.isSuccessful)
                            if (calls.remove(id, call) && !call.isCanceled()) enqueue(PendingResponse(id, null, result, resultBody))
                        }
                    } catch (error: Exception) {
                        if (calls.remove(id, call) && !call.isCanceled()) enqueue(PendingResponse(id, error.message ?: "request failed", null, null))
                    }
                }
            })
        } catch (error: Exception) {
            enqueue(PendingResponse(id, error.message ?: "request failed", null, null))
        }
    }
    private fun cancel(id: Int) { calls.remove(id)?.cancel() }

    private fun <T> ownerCall(block: () -> T): T {
        check(!closed.get()) { "LX runtime is closed" }
        val future = owner.submit<T> { check(!closed.get()) { "LX runtime is closed" }; block() }
        return try { future.get() } catch (error: ExecutionException) { throw error.cause ?: error }
        catch (error: InterruptedException) { future.cancel(true); Thread.currentThread().interrupt(); throw error }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        calls.values.forEach(Call::cancel)
        calls.clear()
        synchronized(wake) { wake.notifyAll() }
        val cleanup = owner.submit {
            try {
                responses.clear()
                if (::boundBridge.isInitialized) {
                    if (::hostBridge.isInitialized) boundBridge.unbind(hostBridge)
                    boundBridge.close()
                }
            } finally {
                if (::runtime.isInitialized) {
                    try { runtime.lowMemoryNotification() } finally { runtime.close() }
                }
            }
        }
        // shutdown() preserves the queued owner-thread cleanup if the caller was
        // interrupted; shutdownNow() would discard it and leak the isolate.
        owner.shutdown()
        try { cleanup.get(22, TimeUnit.SECONDS) }
        catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        catch (error: TimeoutException) { owner.shutdownNow(); throw error }
    }

    companion object {
        private const val MAX_BODY_BYTES = 8 * 1024 * 1024
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"
        private val QUALITY_ORDER = listOf("128k", "320k", "flac", "flac24bit")
        private val sessions = mutableMapOf<String, Pair<String, LxUserRuntime>>()
        @Synchronized fun session(id: String, source: String): LxUserRuntime {
            sessions[id]?.let { (body, runtime) ->
                if (body == source && !runtime.closed.get()) return runtime
                runtime.close()
            }
            val runtime = LxUserRuntime()
            try { runtime.load(LxUserScript(source)); sessions[id] = source to runtime }
            catch (error: Throwable) { runtime.close(); sessions.remove(id); throw error }
            return runtime
        }
        @Synchronized fun evict(id: String) { sessions.remove(id)?.second?.close() }
        @Synchronized fun evictAll() { sessions.values.forEach { it.second.close() }; sessions.clear() }
        internal fun isolatedClient(base: OkHttpClient = OkHttpClient()): OkHttpClient = base.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES).authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE).cache(null)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val url = chain.request().url
                chain.proceed(chain.request().newBuilder().tag(LxOrigin::class.java, LxOrigin(url.scheme, url.host, url.port)).build())
            }.addNetworkInterceptor { chain ->
                val request = chain.request()
                val origin = request.tag(LxOrigin::class.java)
                val current = request.url
                val next = request.newBuilder()
                if (origin != null && origin != LxOrigin(current.scheme, current.host, current.port)) {
                    request.headers.names().filter { name -> SENSITIVE.any { it in name.lowercase(Locale.ROOT) } }.forEach(next::removeHeader)
                    val host = if (':' in current.host) "[${current.host}]" else current.host
                    val defaultPort = if (current.scheme == "https") 443 else 80
                    next.header("Host", if (current.port == defaultPort) host else "$host:${current.port}")
                }
                chain.proceed(next.build())
            }.build()
        private val SENSITIVE = listOf("auth", "cookie", "credential", "key", "password", "secret", "session", "signature", "token")
    }
}

private data class LxOrigin(val scheme: String, val host: String, val port: Int)

/** Public for Javet reflection; scripts only receive these three annotated methods. */
class LxHostBridge internal constructor(
    private val requestFunction: (Int, String, String) -> Unit,
    private val cancelFunction: (Int) -> Unit,
    private val cryptoFunction: (String, String) -> String,
) {
    @V8Function fun request(id: Int, url: String, options: String) = requestFunction(id, url, options)
    @V8Function fun cancel(id: Int) = cancelFunction(id)
    @V8Function fun crypto(operation: String, args: String): String = cryptoFunction(operation, args)
}

private fun json(value: Any?): String = JSONObject.valueToString(JSONObject.wrap(value))
private fun jsonObject(value: String): Map<String, Any?> = JSONObject(value).toMap()
private fun bytes(value: Any?, encoding: String? = null): ByteArray = when (value) {
    is JSONArray -> (0 until value.length()).map { value.optInt(it).toByte() }.toByteArray()
    is String -> when (encoding?.lowercase(Locale.ROOT)) {
        "base64" -> Base64.getMimeDecoder().decode(value)
        "hex" -> value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        else -> value.toByteArray(Charsets.UTF_8)
    }
    else -> ByteArray(0)
}
private fun lxCryptoBytes(value: Any?): ByteArray = when (value) {
    is String -> value.toByteArray(Charsets.UTF_8)
    else -> bytes(value).toString(Charsets.ISO_8859_1).toByteArray(Charsets.UTF_8)
}
private fun byteList(data: ByteArray) = data.map { it.toInt() and 255 }
private fun crypto(operation: String, args: String): String {
    require(args.length <= 8 * 1024 * 1024) { "LX crypto input too large" }
    val params = JSONArray(args)
    val result: Any? = when (operation) {
        "md5" -> MessageDigest.getInstance("MD5").digest(params.optString(0).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
        "randomBytes" -> byteList(ByteArray(params.optInt(0).coerceIn(0, 65_536)).also(SecureRandom()::nextBytes))
        "from" -> byteList(bytes(params.opt(0), params.optString(1)))
        "toString" -> bytes(params.opt(0)).let { data -> when (params.optString(1)) {
            "hex" -> data.joinToString("") { "%02x".format(it.toInt() and 255) }
            "base64" -> Base64.getEncoder().encodeToString(lxCryptoBytes(params.opt(0)))
            "binary" -> byteList(data)
            else -> data.toString(Charsets.UTF_8)
        } }
        "aesEncrypt" -> {
            val mode = params.optString(1)
            val transformation = when (mode) { "aes-128-cbc" -> "AES/CBC/PKCS5Padding"; "aes-128-ecb" -> "AES/ECB/NoPadding"; else -> error("Unsupported AES mode: $mode") }
            val cipher = Cipher.getInstance(transformation)
            val key = SecretKeySpec(lxCryptoBytes(params.opt(2)), "AES")
            if (mode == "aes-128-cbc") cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(lxCryptoBytes(params.opt(3)).copyOf(16)))
            else cipher.init(Cipher.ENCRYPT_MODE, key)
            byteList(cipher.doFinal(lxCryptoBytes(params.opt(0))))
        }
        "rsaEncrypt" -> {
            val text = params.optString(1).replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
            val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getMimeDecoder().decode(text)))
            byteList(Cipher.getInstance("RSA/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(lxCryptoBytes(params.opt(0))))
        }
        else -> error("Unknown LX crypto operation")
    }
    return json(result)
}
private fun parseBody(body: String): Any? = try {
    val result = JSONArray("[${body.trim()}]").opt(0)
    when (result) {
        is JSONObject -> result.toMap().let { value ->
            val nested = value["data"]
            val additions = if (value["url"] != null) emptyMap() else when (nested) {
                is Map<*, *> -> nested.entries.associate { it.key.toString() to it.value }.filterKeys { it !in value }
                is String -> if (nested.startsWith("http")) mapOf("url" to nested) else emptyMap()
                else -> emptyMap()
            }
            value + additions
        }
        is JSONArray -> result.toList()
        JSONObject.NULL -> null
        null -> body
        else -> result
    }
} catch (_: Exception) { body }
