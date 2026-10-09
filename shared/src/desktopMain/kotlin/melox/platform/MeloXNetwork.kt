package melox.platform

import java.net.NetworkInterface

actual fun isNetworkAvailable(): Boolean {
    return try {
        // Android checks the active network, not an unrelated host's ICMP policy.
        // A proxy/VPN may serve HTTP while blocking ping; never block Main for 3s.
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return true
        interfaces.asSequence().any { iface ->
            iface.isUp && !iface.isLoopback && iface.inetAddresses.asSequence().any { address ->
                !address.isLoopbackAddress && !address.isAnyLocalAddress && !address.isLinkLocalAddress
            }
        }
    } catch (_: Exception) {
        true // Same optimistic fallback as Android when connectivity services are unavailable.
    }
}

actual fun isWifiConnected(): Boolean {
    return try {
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (iface.isUp && iface.isLoopback.not()) {
                val name = iface.displayName.lowercase()
                if (name.contains("wifi") || name.contains("wlan") || name.contains("wi-fi")) {
                    return true
                }
            }
        }
        false
    } catch (_: Exception) {
        false
    }
}
