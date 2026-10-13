package melox.legal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import melox.platform.MeloXPreferences
import melox.platform.getPreferences
import melox.remoteconfig.MeloXRemoteConfigConsent

const val MELOX_LEGAL_VERSION = "1.2-2026-08-27"

enum class MeloXOnboardingStage { Legal, Connect, CloudControl, Complete }

/** Android 3f877ea's first-launch ordering, preserving its existing persistent keys. */
class MeloXOnboardingSession(
    private val preferences: MeloXPreferences = getPreferences("melox_app_settings"),
    private val cloudChoiceMade: () -> Boolean = MeloXRemoteConfigConsent::choiceMade,
    private val now: () -> Long = System::currentTimeMillis,
) {
    var stage by mutableStateOf(
        if (preferences.getBoolean("onboarding_completed", false)) afterConnection() else MeloXOnboardingStage.Legal,
    )
        private set

    fun agreeLegal() {
        check(stage == MeloXOnboardingStage.Legal)
        preferences.putValues(mapOf("legal_consent_version" to MELOX_LEGAL_VERSION, "legal_consent_at" to now().toString()))
        stage = MeloXOnboardingStage.Connect
    }

    fun finishConnection() {
        check(stage == MeloXOnboardingStage.Connect)
        preferences.putBoolean("onboarding_completed", true)
        stage = afterConnection()
    }

    /** Called only after the actual consent/runtime action has persisted its choice. */
    fun cloudChoiceRecorded() {
        check(stage == MeloXOnboardingStage.CloudControl && cloudChoiceMade())
        stage = MeloXOnboardingStage.Complete
    }

    private fun afterConnection() = if (cloudChoiceMade()) MeloXOnboardingStage.Complete else MeloXOnboardingStage.CloudControl
}
