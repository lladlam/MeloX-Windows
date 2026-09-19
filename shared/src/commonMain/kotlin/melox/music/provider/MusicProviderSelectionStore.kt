package melox.music.provider

import melox.MeloXBuildConfig
import melox.platform.MeloXPreferences
import melox.platform.getPreferences
import melox.music.model.MusicSource

/**
 * Provider selection is local-only. Cross-provider aggregation is deliberately
 * opt-in and defaults to disabled. Even after the global experiment switch is
 * enabled, the source whitelist defaults to the current provider only; MeloX
 * never silently fans a request out to every installed provider.
 */
object MusicProviderSelectionStore {
    private const val PreferencesName = "melox_music_providers"
    private const val KeySelectedSource = "selected_source"
    private const val KeyUnifiedEnabled = "unified_enabled"
    private const val KeyAutomaticFallback = "automatic_source_fallback"
    private const val KeyUnifiedSources = "unified_sources"

    private fun prefs(): MeloXPreferences = getPreferences(PreferencesName)

    /** Providers that need build-time credentials remain internal until configured. */
    fun visibleSources(): List<MusicSource> =
        MusicSource.entries.filter { source ->
            source != MusicSource.AppleMusic &&
                source != MusicSource.Kuwo &&
                (source != MusicSource.Spotify || MeloXBuildConfig.SPOTIFY_CLIENT_ID.isNotBlank())
        }

    fun selectedSource(): MusicSource =
        prefs().getString(KeySelectedSource, null)
            ?.let { MusicSource.fromStorageValue(it) }
            ?.takeIf { it in visibleSources() }
            ?: MusicSource.Netease

    fun setSelectedSource(source: MusicSource) {
        prefs().putString(KeySelectedSource, source.storageValue)
    }

    fun unifiedEnabled(): Boolean = prefs().getBoolean(KeyUnifiedEnabled, false)

    fun setUnifiedEnabled(enabled: Boolean) {
        val preferences = prefs()
        preferences.putBoolean(KeyUnifiedEnabled, enabled)
        if (enabled && !preferences.contains(KeyUnifiedSources)) {
            preferences.putStringSet(
                KeyUnifiedSources,
                setOf(selectedSource().storageValue),
            )
        }
        if (!enabled) {
            // Automatic fallback must never remain active when aggregation is off.
            preferences.putBoolean(KeyAutomaticFallback, false)
        }
    }

    fun automaticFallbackEnabled(): Boolean =
        unifiedEnabled() && prefs().getBoolean(KeyAutomaticFallback, false)

    fun setAutomaticFallbackEnabled(enabled: Boolean) {
        val safeEnabled = enabled && unifiedEnabled()
        prefs().putBoolean(KeyAutomaticFallback, safeEnabled)
    }

    fun unifiedSources(): Set<MusicSource> {
        val preferences = prefs()
        val raw = preferences.getStringSet(KeyUnifiedSources, null)
        val parsed = raw?.mapNotNullTo(linkedSetOf()) { value ->
            visibleSources().firstOrNull { it.storageValue == value }
        }.orEmpty()
        return parsed.ifEmpty { linkedSetOf(selectedSource()) }
    }

    fun setUnifiedSources(sources: Set<MusicSource>) {
        // An empty whitelist is normalized back to the current provider so a
        // single switch can never accidentally turn into an "all providers" request.
        val safeSources = sources.ifEmpty { setOf(selectedSource()) }
        prefs().putStringSet(KeyUnifiedSources, safeSources.mapTo(linkedSetOf()) { it.storageValue })
    }

    fun setUnifiedSourceEnabled(source: MusicSource, enabled: Boolean): Set<MusicSource> {
        val updated = unifiedSources().toMutableSet().apply {
            if (enabled) add(source) else remove(source)
        }
        val normalized = updated.ifEmpty { mutableSetOf(selectedSource()) }
        setUnifiedSources(normalized)
        return normalized.toSet()
    }
}
