package melox.account

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Synthetic account protocol results confined to tests; exercises the production session store. */
class NeteaseAccountLifecycleTest {
    private val profile = NeteaseAccountProfile(7, "Synthetic lifecycle account", null, null, null)

    @Test fun cancellationPropagatesAndReleasesLoadingStateWithoutSaving() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<NeteaseAccountProfile>()
        val store = NeteaseSessionStore(accountLoader = { entered.complete(Unit); blocked.await() })
        store.clear()
        val login = launch { store.acceptAuthenticatedCookie("MUSIC_U=synthetic-cancel") }
        entered.await()
        assertTrue(store.isRefreshing)
        login.cancelAndJoin()
        assertFalse(store.isRefreshing)
        assertFalse(store.isLoggedIn)
        assertNull(store.errorMessage)
    }

    @Test fun retainedSettingsSessionReloadsAfterSiblingLoginAndLogout() = runBlocking {
        val loginStore = NeteaseSessionStore(accountLoader = { profile })
        loginStore.clear()
        val retained = NeteaseSessionStore(accountLoader = { profile })
        try {
            val revision = NeteaseSessionStore.revision.value
            assertTrue(loginStore.acceptAuthenticatedCookie("MUSIC_U=synthetic-login").isSuccess)
            assertTrue(NeteaseSessionStore.revision.value > revision)
            retained.reloadFromStorage()
            assertTrue(retained.isLoggedIn)
            retained.refreshProfile()
            assertEquals(profile, retained.profile)
            loginStore.clear()
            retained.reloadFromStorage()
            assertFalse(retained.isLoggedIn)
            assertNull(retained.profile)
        } finally { loginStore.clear() }
    }

    @Test fun delayedProfileCannotRestoreAccountAfterLogout() = runBlocking {
        val loginStore = NeteaseSessionStore(accountLoader = { profile })
        loginStore.clear()
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<NeteaseAccountProfile>()
        try {
            loginStore.acceptAuthenticatedCookie("MUSIC_U=synthetic-old").getOrThrow()
            val retained = NeteaseSessionStore(accountLoader = { entered.complete(Unit); response.await() })
            val refresh = async { retained.refreshProfile() }
            entered.await()
            loginStore.clear()
            response.complete(profile)
            refresh.await()
            assertNull(retained.profile)
            retained.reloadFromStorage()
            assertFalse(retained.isLoggedIn)
            assertFalse(retained.isRefreshing)
        } finally { loginStore.clear() }
    }
}
