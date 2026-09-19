package melox.platform

import java.net.InetAddress
import java.net.NetworkInterface

actual fun isNetworkAvailable(): Boolean {
    return try {
        InetAddress.getByName("8.8.8.8").isReachable(3000)
    } catch (_: Exception) {
        false
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