package melox.remoteconfig

import melox.platform.getPreferences
import java.time.LocalDate

object MeloXRemoteNoticeStore {
    private const val PreferencesName = "melox_remote_notices"

    private fun prefs() = getPreferences(PreferencesName)

    fun shouldShow(
        notice: MeloXRemoteNotice,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ): Boolean = shouldShowRemoteNotice(
        frequency = notice.frequency,
        shownOnce = prefs().getBoolean("once:${notice.id}", false),
        lastDailyEpochDay = prefs().getLong("daily:${notice.id}", Long.MIN_VALUE),
        todayEpochDay = todayEpochDay,
    )

    fun markShown(
        notice: MeloXRemoteNotice,
        todayEpochDay: Long = LocalDate.now().toEpochDay(),
    ) {
        when (notice.frequency) {
            "daily" -> prefs().putLong("daily:${notice.id}", todayEpochDay)
            else -> prefs().putBoolean("once:${notice.id}", true)
        }
    }
}

internal fun shouldShowRemoteNotice(
    frequency: String,
    shownOnce: Boolean,
    lastDailyEpochDay: Long,
    todayEpochDay: Long,
): Boolean = when (frequency) {
    "daily" -> lastDailyEpochDay != todayEpochDay
    else -> !shownOnce
}
