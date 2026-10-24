package melox.dlna

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class DlnaService(val type: String, val controlUrl: String)
data class DlnaDevice(val id: String, val name: String, val manufacturer: String, val model: String,
    val location: String, val transport: DlnaService, val rendering: DlnaService?)
internal data class DlnaMediaSource(val uri: String, val headers: Map<String, String>, val title: String,
    val artist: String, val album: String, val artwork: String?, val durationMs: Long, val positionMs: Long,
    val mediaId: String, val mimeType: String)

internal object DlnaXml {
    fun escape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
    fun parse(xml: String): Element {
        require(xml.length <= 512 * 1024 && !xml.contains("<!DOCTYPE", true)) { "Invalid device XML" }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            isXIncludeAware = false; isExpandEntityReferences = false
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))).documentElement
    }
    private fun children(element: Element, name: String): List<Element> = buildList {
        val nodes = element.childNodes
        for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let {
            if ((it.localName ?: it.tagName.substringAfter(':')) == name) add(it)
        }
    }
    private fun text(element: Element, name: String) = children(element, name).firstOrNull()?.textContent?.trim().orEmpty()
    fun fields(xml: String): Map<String, String> = buildMap {
        fun visit(element: Element) {
            val nodes = element.childNodes
            if ((0 until nodes.length).none { nodes.item(it) is Element }) put(element.localName ?: element.tagName.substringAfter(':'), element.textContent.trim())
            for (i in 0 until nodes.length) (nodes.item(i) as? Element)?.let(::visit)
        }
        visit(parse(xml))
    }
    fun devices(xml: String, location: String): List<DlnaDevice> {
        val root = parse(xml); val base = URI(text(root, "URLBase").ifBlank { location }); val result = mutableListOf<DlnaDevice>()
        fun visit(device: Element) {
            val services = children(device, "serviceList").flatMap { children(it, "service") }.mapNotNull {
                text(it, "controlURL").takeIf(String::isNotBlank)?.let { control -> DlnaService(text(it, "serviceType"), base.resolve(control).toString()) }
            }
            services.firstOrNull { it.type.startsWith("urn:schemas-upnp-org:service:AVTransport:") }?.let { transport ->
                result += DlnaDevice(text(device, "UDN").ifBlank { transport.controlUrl }, text(device, "friendlyName").ifBlank { "DLNA" },
                    text(device, "manufacturer"), text(device, "modelName"), location, transport,
                    services.firstOrNull { it.type.startsWith("urn:schemas-upnp-org:service:RenderingControl:") })
            }
            children(device, "deviceList").flatMap { children(it, "device") }.forEach(::visit)
        }
        children(root, "device").forEach(::visit)
        return result.distinctBy { it.id }
    }
}

/** Renderer requests remain local and never follow redirects to another endpoint. */
internal object DlnaHttp {
    data class Response(val code: Int, val body: String)
    fun request(url: String, action: String? = null, body: String = ""): Response {
        val uri = URI(url)
        require(uri.scheme == "http" && uri.userInfo == null) { "Renderer must expose a LAN HTTP endpoint" }
        val address = InetAddress.getByName(uri.host)
        require(address.isSiteLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress) { "Renderer address is not local" }
        val connection = uri.toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 3000; connection.readTimeout = 4000; connection.instanceFollowRedirects = false
            if (action != null) {
                require(!action.contains('\r') && !action.contains('\n'))
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                connection.setRequestProperty("SOAPACTION", "\"$action\"")
                val bytes = body.toByteArray(Charsets.UTF_8); connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val bytes = (if (code >= 400) connection.errorStream else connection.inputStream)?.use { it.readNBytes(512 * 1024 + 1) } ?: ByteArray(0)
            require(bytes.size <= 512 * 1024) { "Renderer response is too large" }
            return Response(code, bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
}

internal object DlnaController {
    fun call(service: DlnaService, method: String, arguments: Map<String, String> = emptyMap()): Map<String, String> {
        require(method.matches(Regex("[A-Za-z]+")) && arguments.keys.all { it.matches(Regex("[A-Za-z]+")) })
        val body = """<?xml version="1.0" encoding="utf-8"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/"><s:Body><u:$method xmlns:u="${DlnaXml.escape(service.type)}"><InstanceID>0</InstanceID>""" +
            arguments.entries.joinToString("") { (key, value) -> "<$key>${DlnaXml.escape(value)}</$key>" } + "</u:$method></s:Body></s:Envelope>"
        val response = DlnaHttp.request(service.controlUrl, "${service.type}#$method", body)
        val fields = if (response.body.isBlank()) emptyMap() else DlnaXml.fields(response.body)
        check(response.code in 200..299 && "errorCode" !in fields) { "${fields["errorDescription"] ?: "DLNA $method failed"} (${fields["errorCode"] ?: response.code})" }
        return fields
    }
    fun time(ms: Long): String { val seconds = ms.coerceAtLeast(0) / 1000; return "%02d:%02d:%02d".format(java.util.Locale.ROOT, seconds / 3600, seconds / 60 % 60, seconds % 60) }
    fun millis(value: String?): Long {
        val parts = value?.split(':') ?: return 0; if (parts.size != 3) return 0
        return (((parts[0].toLongOrNull() ?: 0) * 3600 + (parts[1].toLongOrNull() ?: 0) * 60) * 1000 + ((parts[2].toDoubleOrNull() ?: 0.0) * 1000).toLong()).coerceAtLeast(0)
    }
    fun metadata(source: DlnaMediaSource, url: String) =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="0" parentID="-1" restricted="1"><dc:title>${DlnaXml.escape(source.title)}</dc:title><upnp:artist>${DlnaXml.escape(source.artist)}</upnp:artist><upnp:album>${DlnaXml.escape(source.album)}</upnp:album><upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
            (source.artwork?.let { "<upnp:albumArtURI>${DlnaXml.escape(it)}</upnp:albumArtURI>" } ?: "") +
            "<res protocolInfo=\"http-get:*:${DlnaXml.escape(source.mimeType)}:*\" duration=\"${time(source.durationMs)}\">${DlnaXml.escape(url)}</res></item></DIDL-Lite>"
}
