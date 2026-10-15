package melox.recognition

import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.interop.V8Runtime
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

/** Runs Android's byte-identical AFP resources; each isolate/WASM heap is closed after extraction. */
internal object PinnedAudioFingerprint {
    private fun resource(name: String) = requireNotNull(javaClass.classLoader.getResourceAsStream("AudioFingerprint/$name")) {
        "缺少 pinned AudioFingerprint/$name"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }

    @Synchronized fun generate(samples: FloatArray): String {
        require(samples.size in 8_000..120_000 && samples.all { it.isFinite() })
        V8Host.getV8Instance().createV8Runtime<V8Runtime>().use { runtime ->
            runtime.getGuard(20_000).use {
                runtime.getExecutor(PRELUDE).executeVoid()
                runtime.getExecutor(resource("afp.wasm.js")).executeVoid()
                runtime.getExecutor(resource("afp.js")).executeVoid()
                runtime.getExecutor("globalThis.meloxFp = AudioFingerprintRuntime();").executeVoid()
                runtime.await()
                check(runtime.getExecutor("typeof meloxFp.ExtractQueryFP === 'function'").executeBoolean()) { "音频指纹运行时初始化失败" }
                runtime.createV8ValueArrayBuffer(samples.size * 4).use { pcm ->
                    pcm.byteBuffer.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(samples)
                    runtime.globalObject.set("meloxPcm", pcm)
                }
                val json = runtime.getExecutor("""
                    (() => {
                        const vector = meloxFp.ExtractQueryFP(meloxPcm);
                        try {
                            const bytes = [];
                            for (let i = 0; i < vector.size(); i++) bytes.push(vector.get(i));
                            return JSON.stringify(bytes);
                        } finally { if (typeof vector.delete === 'function') vector.delete(); }
                    })()
                """).executeString()
                val values = JSONArray(json)
                check(values.length() > 0) { "音频指纹为空" }
                return Base64.getEncoder().encodeToString(ByteArray(values.length()) { values.getInt(it).toByte() })
            }
        }
    }

    // Only environment adapters. The pinned JS and embedded WASM stay byte-identical.
    private val PRELUDE = """
        globalThis.console = {log(){}, warn(){}, error(){}};
        globalThis.exports = {};
        globalThis.require = function(name) {
            if (name === '../../util/logger.js') return {info(){}, warn(){}, error(){}};
            throw Error('Unsupported fingerprint module: ' + name);
        };
        globalThis.setInterval = function(){ return 0; };
        globalThis.clearInterval = function(){};
        globalThis.atob = function(text) {
            const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
            let bits = 0, count = 0, output = '';
            for (const char of text) {
                if (char === '=') break;
                const value = alphabet.indexOf(char);
                if (value < 0) continue;
                bits = (bits << 6) | value; count += 6;
                if (count >= 8) { count -= 8; output += String.fromCharCode((bits >> count) & 255); }
            }
            return output;
        };
        // Avoid an asynchronous native compilation worker outliving a closed V8 isolate.
        WebAssembly.instantiate = function(bytes, imports) {
            return Promise.resolve({instance: new WebAssembly.Instance(new WebAssembly.Module(bytes), imports)});
        };
    """.trimIndent()
}
