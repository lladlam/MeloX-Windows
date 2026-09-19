package melox.remoteconfig

import melox.music.model.MusicSource

object MeloXRemoteConfigPolicy {
    fun activeConfig(): MeloXRemoteConfig = effectiveConfig(
        consentEnabled = MeloXRemoteConfigConsent.enabled(),
        status = MeloXRemoteConfigRuntime.status.value,
    )

    fun capabilityEnabled(capability: String): Boolean =
        capability !in activeConfig().disabledCapabilities

    fun providerPlaybackEnabled(source: MusicSource): Boolean = providerPlaybackEnabled(
        config = activeConfig(),
        source = source,
    )

    internal fun effectiveConfig(
        consentEnabled: Boolean,
        status: MeloXRemoteConfigStatus,
    ): MeloXRemoteConfig = if (consentEnabled && status.source == MeloXRemoteConfigSource.VerifiedRemote) {
        status.config
    } else {
        MeloXRemoteConfigDefaults.Config
    }

    internal fun providerPlaybackEnabled(config: MeloXRemoteConfig, source: MusicSource): Boolean = when (source) {
        MusicSource.QQMusic -> "qq_playback" !in config.disabledCapabilities
        MusicSource.Kugou -> "kugou_playback" !in config.disabledCapabilities
        MusicSource.Kuwo -> "kuwo_playback" !in config.disabledCapabilities
        MusicSource.Bilibili -> "bilibili_playback" !in config.disabledCapabilities
        else -> true
    }
}
