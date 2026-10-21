package melox.ui.navigation

/** One-shot provider selection when opening Route.Login from a Desktop row. */
object MeloXLoginRequestBus {
    var pendingProviderId: String? = null

    fun request(providerId: String) {
        pendingProviderId = providerId
    }

    fun consume(): String? = pendingProviderId.also { pendingProviderId = null }
}
