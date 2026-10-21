package melox.network

/**
 * Module-visible facade for the Desktop sign-in sheet. [NeteasePhoneAuthClient]
 * stays internal; the SMS flow itself is the same production implementation the
 * account screen uses.
 */
class NeteasePhoneSignInFacade {
    private val client = NeteasePhoneAuthClient()

    suspend fun sendCode(phone: String) = client.sendCode("86", phone.trim())

    suspend fun login(phone: String, code: String): String = client.login("86", phone.trim(), code.trim())
}
