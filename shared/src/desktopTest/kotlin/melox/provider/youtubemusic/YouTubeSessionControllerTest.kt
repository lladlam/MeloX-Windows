package melox.provider.youtubemusic

import com.metrolist.innertube.models.AccountInfo
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeLocale
import com.metrolist.innertube.utils.YouTubeAuth
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import melox.platform.getPreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class YouTubeSessionControllerTest {
    private class Backend : YouTubeAccountBackend {
        var applied = YouTubeSession()
        var accountAction: suspend (YouTubeSession) -> AccountInfo = { AccountInfo("Channel", null, null, null) }
        var availableChannels = emptyList<YouTubeChannel>()
        override fun apply(session: YouTubeSession, force: Boolean) { applied = session }
        override suspend fun accountInfo(session: YouTubeSession) = accountAction(session)
        override suspend fun accounts(session: YouTubeSession) = availableChannels
    }

    private fun controller(backend: Backend) = YouTubeAccountController(
        getPreferences("youtube_session_test_${UUID.randomUUID()}"), backend,
    )

    @Test fun realAndroidAuthHeaderContractAndLocaleNormalization() {
        val headers = YouTubeAuth.headers("SAPISID=legacy;__Secure-3PAPISID=secure-three", 1700000000, "2", "brand")
        assertEquals("SAPISIDHASH 1700000000_875f2fdfb57f28b127540632a932b7ee76648920", headers["Authorization"])
        assertEquals("2", headers["X-Goog-AuthUser"])
        assertEquals("brand", headers["X-Goog-PageId"])
        assertFalse(YouTubeSession(cookie = "other=SAPISID=value").isLoggedIn)
        assertTrue(YouTubeSession(cookie = "__Secure-3PAPISID=secret").isLoggedIn)
        assertEquals("0", YouTubeAuth.headers("SAPISID=legacy", 1700000000, "undefined")["X-Goog-AuthUser"])
        assertEquals("US", YouTubeLocale.fromLocale(Locale.SIMPLIFIED_CHINESE).gl)
        assertEquals("zh-CN", YouTubeLocale.fromLocale(Locale.SIMPLIFIED_CHINESE).hl)
        assertEquals("JP", YouTubeLocale.fromLocale(Locale.JAPAN).gl)
        assertEquals("US", YouTubeClient.WEB_REMIX.toContext(YouTubeLocale("", "ja"), null, null).client.gl)
    }

    @Test fun validatedLoginAndChannelPersistFullContextAndFingerprint() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        val session = YouTubeSession("SAPISID=secret", "visitor", "sync", "page", authUser = "2")
        assertEquals("Channel", controller.validateAndSave(session))
        assertEquals(session.copy(accountName = "Channel"), controller.read())
        val fingerprint = controller.authFingerprint()
        val channel = YouTubeChannel("Brand", "@brand", null, "brand-page", "brand-sync", false)
        backend.availableChannels = listOf(channel)
        assertEquals(listOf(channel), controller.channels())
        backend.accountAction = { AccountInfo("Brand", null, null, null) }
        controller.useChannel(channel)
        assertEquals("brand-page", controller.read().pageId)
        assertEquals("brand-sync", controller.read().dataSyncId)
        assertEquals("2", controller.read().authUser)
        assertNotEquals(fingerprint, controller.authFingerprint())
        assertEquals(controller.read(), backend.applied)
        assertFalse(controller.read().toString().contains("secret"))
    }

    @Test fun rejectedLoginOrChannelRestoresExistingSessionWithoutPersistingCandidate() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        val existing = YouTubeSession(cookie = "SAPISID=old", accountName = "Old")
        controller.write(existing)
        backend.accountAction = { error("403 denied") }
        assertFailsWith<IllegalStateException> { controller.validateAndSave(YouTubeSession(cookie = "SAPISID=new")) }
        assertEquals(existing, controller.read())
        assertEquals(existing, backend.applied)
        assertFailsWith<IllegalStateException> {
            controller.useChannel(YouTubeChannel("Bad", null, null, "new-page", "new-sync", false))
        }
        assertEquals(existing, controller.read())
        assertEquals(existing, backend.applied)
    }

    @Test fun failedRevalidationReportsExpirationOnlyForAuthFailureAndRetainsCookie() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        val existing = YouTubeSession(cookie = "SAPISID=old")
        controller.write(existing)
        backend.accountAction = { throw YouTubeAccountHttpException(401) }
        assertTrue(controller.revalidate().isFailure)
        assertTrue(controller.expired.value)
        assertEquals(existing, controller.read())
        backend.accountAction = { error("network timeout") }
        assertTrue(controller.revalidate().isFailure)
        assertTrue(controller.expired.value)
        assertEquals(existing, controller.read())
    }

    @Test fun logoutDuringChannelValidationNeverRestoresOrOverwritesTheAccount() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        controller.write(YouTubeSession(cookie = "SAPISID=old"))
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        backend.accountAction = { started.complete(Unit); release.await(); AccountInfo("Brand", null, null, null) }
        val pending = async { runCatching { controller.useChannel(YouTubeChannel("Brand", null, null, "p", "d", false)) } }
        started.await()
        controller.clear()
        release.complete(Unit)
        assertTrue(pending.await().isFailure)
        assertFalse(controller.read().isLoggedIn)
        assertEquals(controller.read(), backend.applied)
    }

    @Test fun unverifiedCandidateNeverReplacesTheLiveAccountDuringNetworkValidation() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        val old = YouTubeSession(cookie = "SAPISID=old", pageId = "old-page")
        val candidate = YouTubeSession(cookie = "SAPISID=new", pageId = "new-page", authUser = "2")
        controller.write(old)
        val started = CompletableDeferred<YouTubeSession>()
        val release = CompletableDeferred<Unit>()
        backend.accountAction = { session -> started.complete(session); release.await(); AccountInfo("New", null, null, null) }
        val pending = async { controller.validateAndSave(candidate) }
        assertEquals(candidate, started.await())
        assertEquals(old, controller.read())
        assertEquals(old, backend.applied)
        release.complete(Unit)
        assertEquals("New", pending.await())
        assertEquals(candidate.copy(accountName = "New"), controller.read())
        assertEquals(controller.read(), backend.applied)
    }

    @Test fun accountGenerationRejectsResultsAfterSwitchingAwayAndBack() {
        val backend = Backend()
        val controller = controller(backend)
        val original = YouTubeSession(cookie = "SAPISID=first", pageId = "page")
        controller.write(original)
        val request = controller.requestSession()
        controller.write(YouTubeSession(cookie = "SAPISID=second"))
        controller.write(original)
        assertEquals(original, controller.read())
        assertFailsWith<IllegalStateException> { controller.requireCurrent(request) }
        controller.requireCurrent(controller.requestSession())
    }

    @Test fun cancelledRevalidationCannotPersistALateAccountName() = runBlocking {
        val backend = Backend()
        val controller = controller(backend)
        val saved = YouTubeSession(cookie = "SAPISID=old", accountName = "Old")
        controller.write(saved)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        backend.accountAction = {
            started.complete(Unit)
            withContext(NonCancellable) { release.await() }
            AccountInfo("Late", null, null, null)
        }
        val pending = async { controller.revalidate() }
        started.await()
        pending.cancel()
        release.complete(Unit)
        pending.join()
        assertEquals(saved, controller.read())
        assertEquals(saved, backend.applied)
    }
}
