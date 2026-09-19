package melox.provider.spotify

import melox.account.SecureSessionPreferences
import melox.platform.getPreferences

data class SpotifySession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAtEpochMs: Long = 0L,
    val accountId: String = "",
) {
    val isLoggedIn: Boolean get() = accessToken.isNotBlank() || refreshToken.isNotBlank()
    fun needsRefresh(nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        accessToken.isBlank() || nowEpochMs >= expiresAtEpochMs - 60_000L

    override fun toString(): String =
        "SpotifySession(isLoggedIn=$isLoggedIn, expiresAtEpochMs=$expiresAtEpochMs, accountId=$accountId)"
}

data class SpotifyAuthorizationTransaction(
    val state: String,
    val codeVerifier: String,
    val createdAtEpochMs: Long,
)

object SpotifySessionStore {
    private const val PreferencesName = "melox_spotify_session"
    private const val AccessToken = "access_token"
    private const val RefreshToken = "refresh_token"
    private const val ExpiresAt = "expires_at"
    private const val AccountId = "account_id"
    private const val OAuthState = "oauth_state"
    private const val OAuthVerifier = "oauth_verifier"
    private const val OAuthCreatedAt = "oauth_created_at"
    private const val OAuthError = "oauth_error"

    private fun prefs() = SecureSessionPreferences.open(PreferencesName)

    fun read(): SpotifySession = prefs().let {
        SpotifySession(
            accessToken = it.getString(AccessToken, null).orEmpty(),
            refreshToken = it.getString(RefreshToken, null).orEmpty(),
            expiresAtEpochMs = it.getLong(ExpiresAt, 0L),
            accountId = it.getString(AccountId, null).orEmpty(),
        )
    }

    fun write(session: SpotifySession) {
        prefs().apply {
            putString(AccessToken, session.accessToken)
            putString(RefreshToken, session.refreshToken)
            putLong(ExpiresAt, session.expiresAtEpochMs)
            putString(AccountId, session.accountId)
        }
    }

    fun updateAccountId(accountId: String) {
        prefs().putString(AccountId, accountId)
    }

    fun saveTransaction(transaction: SpotifyAuthorizationTransaction) {
        val p = prefs()
        p.putString(OAuthState, transaction.state)
        p.putString(OAuthVerifier, transaction.codeVerifier)
        p.putLong(OAuthCreatedAt, transaction.createdAtEpochMs)
    }

    fun transaction(): SpotifyAuthorizationTransaction? = prefs().let {
        val state = it.getString(OAuthState, null).orEmpty()
        val verifier = it.getString(OAuthVerifier, null).orEmpty()
        if (state.isBlank() || verifier.isBlank()) null else SpotifyAuthorizationTransaction(
            state = state,
            codeVerifier = verifier,
            createdAtEpochMs = it.getLong(OAuthCreatedAt, 0L),
        )
    }

    fun clearTransaction() {
        val p = prefs()
        p.remove(OAuthState)
        p.remove(OAuthVerifier)
        p.remove(OAuthCreatedAt)
    }

    fun setOAuthError(message: String) {
        prefs().putString(OAuthError, message)
    }

    fun consumeOAuthError(): String? {
        val p = prefs()
        return p.getString(OAuthError, null)?.also { p.remove(OAuthError) }
    }

    fun clear() {
        prefs().clear()
    }
}
