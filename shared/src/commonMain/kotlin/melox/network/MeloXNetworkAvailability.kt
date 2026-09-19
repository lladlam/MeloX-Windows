package melox.network

import melox.platform.isNetworkAvailable

object MeloXNetworkAvailability {
    fun isOnline(): Boolean = isNetworkAvailable()
}
