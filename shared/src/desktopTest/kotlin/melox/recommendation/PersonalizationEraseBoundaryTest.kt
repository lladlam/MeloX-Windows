package melox.recommendation

import kotlinx.coroutines.runBlocking
import melox.account.NeteaseAccountProfile
import melox.account.NeteaseSessionStore
import kotlin.test.*

class PersonalizationEraseBoundaryTest {
    @Test fun confirmedEraseClearsLearnedDataButPreservesAlgorithmPreferenceAndAccount() = runBlocking {
        val account = NeteaseSessionStore(accountLoader = { NeteaseAccountProfile(9, "Synthetic erase boundary", null, null, null) })
        account.clear()
        try {
            account.acceptAuthenticatedCookie("MUSIC_U=synthetic-erase-boundary").getOrThrow()
            LocalRecommendationStore.setAlgorithmEnabled(true)
            LocalRecommendationStore.setPersonalizationConsent(true)
            LocalRecommendationStore.setConsentAt(77L)
            LocalRecommendationStore.recordPlayback("fixture-key", "Generated audio", "Synthetic artist", completed = true)
            LocalRecommendationStore.writeModelMetadata(LocalModelMetadata(sampleCount = 1, trackCount = 1, trainedAtMs = 77))
            assertTrue(LocalRecommendationStore.readFeatures().isNotEmpty())
            assertTrue(LocalRecommendationStore.preferenceVector().any { it != 0f })
            LocalRecommendationEngine.revokePersonalizationAndClear()
            assertFalse(LocalRecommendationStore.hasPersonalizationConsent())
            assertEquals(0L, LocalRecommendationStore.consentAt())
            assertTrue(LocalRecommendationStore.isAlgorithmEnabled())
            assertTrue(LocalRecommendationStore.readFeatures().isEmpty())
            assertTrue(LocalRecommendationStore.readRecommendations().isEmpty())
            assertTrue(LocalRecommendationStore.readCandidateTracks().isEmpty())
            assertTrue(LocalRecommendationStore.preferenceVector().all { it == 0f })
            assertEquals(0, LocalRecommendationStore.readModelMetadata().sampleCount)
            assertTrue(NeteaseSessionStore.containsMusicU(NeteaseSessionStore.readCookie()))
        } finally {
            account.clear()
            LocalRecommendationEngine.revokePersonalizationAndClear()
            LocalRecommendationStore.setAlgorithmEnabled(false)
        }
    }
}
