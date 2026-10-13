package melox.legal

import melox.platform.getPreferences
import java.util.UUID
import kotlin.test.*

class OnboardingPersistenceTest {
    @Test fun cloudChoiceWaitsForCompletedConnectionAndSurvivesReopen() {
        val preferences = getPreferences("onboarding_test_${UUID.randomUUID()}")
        var choice = false
        try {
            val session = MeloXOnboardingSession(preferences, { choice }, { 123456L })
            assertEquals(MeloXOnboardingStage.Legal, session.stage)
            assertFailsWith<IllegalStateException> { session.finishConnection() }
            session.agreeLegal()
            assertEquals(MeloXOnboardingStage.Connect, session.stage)
            assertEquals(MELOX_LEGAL_VERSION, preferences.getString("legal_consent_version", null))
            assertEquals(123456L, preferences.getLong("legal_consent_at", 0))
            assertFalse(preferences.getBoolean("onboarding_completed", false))
            session.finishConnection()
            assertEquals(MeloXOnboardingStage.CloudControl, session.stage)
            assertEquals(MeloXOnboardingStage.CloudControl, MeloXOnboardingSession(preferences, { choice }).stage)
            assertFailsWith<IllegalStateException> { session.cloudChoiceRecorded() }
            choice = true
            session.cloudChoiceRecorded()
            assertEquals(MeloXOnboardingStage.Complete, session.stage)
            assertEquals(MeloXOnboardingStage.Complete, MeloXOnboardingSession(preferences, { choice }).stage)
        } finally { preferences.clear() }
    }

    @Test fun interruptedLegalOrConnectDoesNotMarkOnboardingCompleted() {
        val preferences = getPreferences("onboarding_test_${UUID.randomUUID()}")
        try {
            MeloXOnboardingSession(preferences, { true }).agreeLegal()
            assertEquals(MeloXOnboardingStage.Legal, MeloXOnboardingSession(preferences, { true }).stage)
            assertFalse(preferences.getBoolean("onboarding_completed", false))
        } finally { preferences.clear() }
    }

    @Test fun existingCompletedOnboardingKeepsHistoricalConsentAndOnlyAsksMissingCloudChoice() {
        val preferences = getPreferences("onboarding_test_${UUID.randomUUID()}")
        try {
            preferences.putValues(mapOf("onboarding_completed" to "true", "legal_consent_version" to "historical-version", "legal_consent_at" to "99"))
            assertEquals(MeloXOnboardingStage.CloudControl, MeloXOnboardingSession(preferences, { false }).stage)
            assertEquals(MeloXOnboardingStage.Complete, MeloXOnboardingSession(preferences, { true }).stage)
            assertEquals("historical-version", preferences.getString("legal_consent_version", null))
            assertEquals(99L, preferences.getLong("legal_consent_at", 0))
        } finally { preferences.clear() }
    }
}
