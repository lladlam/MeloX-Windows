package melox.autosync

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import java.io.Closeable
import java.io.File

/** An owned, single-session OpenJTalk frontend, with UTF-8 on both sides. */
internal class JaOpenJtalk(library: File, dictionary: File) : Closeable {
    private interface Api : Library {
        fun ja_g2p_open(directory: String): Pointer?
        fun ja_g2p_close(handle: Pointer)
        fun ja_g2p_phonemes(handle: Pointer, text: String): Pointer?
        fun ja_g2p_kana(handle: Pointer, text: String): Pointer?
        fun ja_g2p_tokens(handle: Pointer, text: String): Pointer?
        fun ja_g2p_free(value: Pointer)
    }

    private val api = Native.load(library.absolutePath, Api::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
    private var handle: Pointer? = checkNotNull(api.ja_g2p_open(dictionary.absolutePath)) { "OpenJTalk dictionary is unavailable" }

    @Synchronized fun isOpen() = handle != null
    @Synchronized fun phonemes(text: String): List<String>? = call(text, api::ja_g2p_phonemes)?.split(' ')?.filter(String::isNotEmpty)
    @Synchronized fun kana(text: String): String? = call(text, api::ja_g2p_kana)
    @Synchronized fun tokens(text: String): List<Pair<String, String>>? = call(text, api::ja_g2p_tokens)?.lineSequence()
        ?.mapNotNull { line -> line.indexOf('\t').takeIf { it >= 0 }?.let { line.substring(0, it) to line.substring(it + 1) } }
        ?.toList()

    private fun call(text: String, action: (Pointer, String) -> Pointer?): String? {
        // JNA's C string cannot represent embedded NUL and the native frontend
        // intentionally bounds the UTF-8 input before normalization.
        if ('\u0000' in text || text.toByteArray(Charsets.UTF_8).size > 4096) return null
        val current = handle ?: return null
        val result = action(current, text) ?: return null
        return try { result.getString(0, "UTF-8") } finally { api.ja_g2p_free(result) }
    }

    @Synchronized override fun close() {
        val current = handle ?: return
        handle = null
        api.ja_g2p_close(current)
    }
}
