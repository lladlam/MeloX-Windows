package melox.provider.jellyfin

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Server images retain token-free URLs in models, queues and caches. Credentials remain in request headers. */
object JellyfinArtworkAccess {
    fun requestHeaders(source: String, session: JellyfinSession = JellyfinSessionStore.read()): Map<String, String> {
        if (!session.isLoggedIn) return emptyMap()
        val url = source.toHttpUrlOrNull() ?: return emptyMap()
        val base = session.baseUrl.toHttpUrlOrNull() ?: return emptyMap()
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port || url.username.isNotBlank() || url.password.isNotBlank()) return emptyMap()
        val prefix = base.pathSegments.filter(String::isNotBlank)
        if (url.pathSegments.take(prefix.size) != prefix) return emptyMap()
        val path = url.pathSegments.drop(prefix.size)
        if (path.size != 4 || path[0] != "Items" || path[2] != "Images" || path[3] != "Primary") return emptyMap()
        return mapOf("X-Emby-Token" to session.accessToken)
    }

    fun cacheKey(source: String, session: JellyfinSession = JellyfinSessionStore.read()): String {
        val headers = requestHeaders(source, session)
        if (headers.isEmpty()) return source
        val hash = MessageDigest.getInstance("SHA-256").digest(headers.values.single().toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "$source#account:$hash"
    }

    fun openConnection(source: String, session: JellyfinSession = JellyfinSessionStore.read()) = URL(source).openConnection().apply {
        val headers = requestHeaders(source, session)
        headers.forEach(::setRequestProperty)
        if (headers.isNotEmpty() && this is HttpURLConnection) instanceFollowRedirects = false
    }

    fun clientFor(source: String, client: OkHttpClient, session: JellyfinSession = JellyfinSessionStore.read()): OkHttpClient =
        if (requestHeaders(source, session).isEmpty()) client else client.newBuilder().followRedirects(false).followSslRedirects(false).build()
}
