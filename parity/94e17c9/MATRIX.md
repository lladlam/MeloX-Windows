# Pinned source parity inventory

Android: `94e17c920bf554966584955afa2bd2ee92580a8b`
Windows base: `87ba7992b44c5e8c844a5db2a61f2149148eed26`

This is a conservative source inventory. Source identity is recorded separately and never promotes a row to MATCH. PARTIAL includes unverified implementations. MISSING means no mapped file/symbol, not proof that an equivalent cannot exist. Runtime, authenticated provider, Windows native, visual and animation acceptance are separate evidence.

Files: {'MISSING': 253, 'PARTIAL': 199}. Review hits: 46. Extracted Android design/animation lines: 2081.

## File matrix

| Android | Windows | Status |
| --- | --- | --- |
| `android/app/src/main/AndroidManifest.xml` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/MainActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/MeloXApplication.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/core/account/NeteaseAccountModels.kt` | `shared/src/commonMain/kotlin/melox/account/NeteaseAccountModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/account/NeteaseSessionStore.kt` | `shared/src/commonMain/kotlin/melox/account/NeteaseSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/account/SecureSessionPreferences.kt` | `shared/src/commonMain/kotlin/melox/account/SecureSessionPreferences.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/audio/MusicQuality.kt` | `shared/src/commonMain/kotlin/melox/audio/MusicQuality.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/audio/NeteaseQualityClient.kt` | `shared/src/commonMain/kotlin/melox/audio/NeteaseQualityClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/diagnostics/MeloXCrashStore.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/core/diagnostics/MeloXLogExporter.kt` | `shared/src/commonMain/kotlin/melox/diagnostics/MeloXLogExporter.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/download/MeloXDownloadStore.kt` | `shared/src/commonMain/kotlin/melox/download/MeloXDownloadStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/download/MeloXProviderDownloadStore.kt` | `shared/src/commonMain/kotlin/melox/download/MeloXProviderDownloadStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseDiscoveryModels.kt` | `shared/src/commonMain/kotlin/melox/library/NeteaseDiscoveryModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseHomeBlockParser.kt` | `shared/src/commonMain/kotlin/melox/library/NeteaseHomeBlockParser.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryCache.kt` | `shared/src/commonMain/kotlin/melox/library/NeteaseLibraryCache.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryClient.kt` | `shared/src/commonMain/kotlin/melox/library/NeteaseLibraryClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/library/NeteaseLibraryModels.kt` | `shared/src/commonMain/kotlin/melox/library/NeteaseLibraryModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/AmlldbLyricsClient.kt` | `shared/src/commonMain/kotlin/melox/lyrics/AmlldbLyricsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/KugouKrcLyricsParser.kt` | `shared/src/commonMain/kotlin/melox/lyrics/KugouKrcLyricsParser.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LrcLibLyricsClient.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LrcLibLyricsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LrcLyricsParser.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LrcLyricsParser.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LyricBindingStore.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LyricBindingStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LyricModels.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LyricModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LyricRomanizationAligner.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LyricRomanizationAligner.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/LyricTimelineProcessor.kt` | `shared/src/commonMain/kotlin/melox/lyrics/LyricTimelineProcessor.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/MeloXLyricScript.kt` | `shared/src/commonMain/kotlin/melox/lyrics/MeloXLyricScript.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/QQMusicMeiQrcDecoder.kt` | `shared/src/commonMain/kotlin/melox/lyrics/QQMusicMeiQrcDecoder.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/QQMusicQrcLyricsParser.kt` | `shared/src/commonMain/kotlin/melox/lyrics/QQMusicQrcLyricsParser.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/lyrics/TtmlLyricsParser.kt` | `shared/src/commonMain/kotlin/melox/lyrics/TtmlLyricsParser.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/model/SearchSong.kt` | `shared/src/commonMain/kotlin/melox/model/SearchSong.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/experience/MusicExperience.kt` | `shared/src/commonMain/kotlin/melox/music/experience/MusicExperience.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/model/MusicModels.kt` | `shared/src/commonMain/kotlin/melox/music/model/MusicModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/MeloXLegacyUiBridge.kt` | `shared/src/commonMain/kotlin/melox/music/provider/MeloXLegacyUiBridge.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/MeloXMusicProviders.kt` | `shared/src/commonMain/kotlin/melox/music/provider/MeloXMusicProviders.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/MusicProvider.kt` | `shared/src/commonMain/kotlin/melox/music/provider/MusicProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/MusicProviderSelectionStore.kt` | `shared/src/commonMain/kotlin/melox/music/provider/MusicProviderSelectionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/PlaybackAccountSlot.kt` | `shared/src/commonMain/kotlin/melox/music/provider/PlaybackAccountSlot.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/PlaybackAccountStore.kt` | `shared/src/commonMain/kotlin/melox/music/provider/PlaybackAccountStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/ProviderAccountManager.kt` | `shared/src/commonMain/kotlin/melox/music/provider/ProviderAccountManager.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/ProviderCollectionPaging.kt` | `shared/src/commonMain/kotlin/melox/music/provider/ProviderCollectionPaging.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/ThirdPartyMusicSourceConsentStore.kt` | `shared/src/commonMain/kotlin/melox/music/provider/ThirdPartyMusicSourceConsentStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/TrackAggregation.kt` | `shared/src/commonMain/kotlin/melox/music/provider/TrackAggregation.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/music/provider/UnifiedMusicService.kt` | `shared/src/commonMain/kotlin/melox/music/provider/UnifiedMusicService.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/MeloXGitHubRouting.kt` | `shared/src/commonMain/kotlin/melox/network/MeloXGitHubRouting.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/MeloXHttpClient.kt` | `shared/src/commonMain/kotlin/melox/network/MeloXHttpClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/MeloXNetworkAvailability.kt` | `shared/src/commonMain/kotlin/melox/network/MeloXNetworkAvailability.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseAccountDetailsClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseAccountDetailsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseAuthenticatedEapi.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseAuthenticatedEapi.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseAuthenticatedWeapi.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseAuthenticatedWeapi.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseClipboardLink.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseClipboardLink.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseCollectionDetailsClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseCollectionDetailsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseListenTogetherInvitation.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseListenTogetherInvitation.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseListenTogetherTransport.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseListenTogetherTransport.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseMusicOperationsClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseMusicOperationsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteasePhoneAuthClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteasePhoneAuthClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseSearchClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseSearchClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseSocialExtrasClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseSocialExtrasClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/network/NeteaseUniversalSearchClient.kt` | `shared/src/commonMain/kotlin/melox/network/NeteaseUniversalSearchClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/applemusic/AppleMusicApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/applemusic/AppleMusicApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/applemusic/AppleMusicSdkBridge.kt` | `shared/src/commonMain/kotlin/melox/provider/applemusic/AppleMusicSdkBridge.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/applemusic/AppleMusicSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/applemusic/AppleMusicSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliApiCache.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliApiCache.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliLyricAlignment.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliLyricAlignment.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliLyricOffsetStore.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliLyricOffsetStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliPlaybackAssociationStore.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliPlaybackAssociationStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliWbiSigner.kt` | `shared/src/commonMain/kotlin/melox/provider/bilibili/BilibiliWbiSigner.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/jellyfin/JellyfinApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/jellyfin/JellyfinApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/jellyfin/JellyfinModels.kt` | `shared/src/commonMain/kotlin/melox/provider/jellyfin/JellyfinModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/jellyfin/JellyfinProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/jellyfin/JellyfinProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/jellyfin/JellyfinSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/jellyfin/JellyfinSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouArtworkUrl.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouArtworkUrl.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouCatalogClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouCatalogClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouDiscoveryClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouDiscoveryClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouLoginClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouLoginClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouLyricsClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouLyricsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouPlaylistClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouPlaylistClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouPlaylistWriteClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouPlaylistWriteClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouRankingClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouRankingClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouRequestClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouRequestClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kugou/KugouSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/kugou/KugouSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoJsonNormalizer.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoJsonNormalizer.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoLyricsClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoLyricsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoPhoneAuthClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoPhoneAuthClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoPlaylistClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoPlaylistClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoRequestClient.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoRequestClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/kuwo/KuwoSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/local/LocalMediaScanner.kt` | `shared/src/commonMain/kotlin/melox/provider/local/LocalMediaScanner.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/local/LocalModels.kt` | `shared/src/commonMain/kotlin/melox/provider/local/LocalModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/local/LocalMusicRepository.kt` | `shared/src/commonMain/kotlin/melox/provider/local/LocalMusicRepository.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/local/LocalProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/local/LocalProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/local/LocalRecognitionCoordinator.kt` | `shared/src/commonMain/kotlin/melox/provider/local/LocalRecognitionCoordinator.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/lxuser/ChkszApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/lxuser/ChkszApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/lxuser/ChkszApiKeyStore.kt` | `shared/src/commonMain/kotlin/melox/provider/lxuser/ChkszApiKeyStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/lxuser/LxUserRuntime.kt` | `shared/src/commonMain/kotlin/melox/provider/lxuser/LxUserRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/lxuser/LxUserScriptMetadata.kt` | `shared/src/commonMain/kotlin/melox/provider/lxuser/LxUserScriptMetadata.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/lxuser/LxUserSourceStore.kt` | `shared/src/commonMain/kotlin/melox/provider/lxuser/LxUserSourceStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/netease/NeteaseProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/netease/NeteaseProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicCatalogClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicCatalogClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicFavoriteClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicFavoriteClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicPhoneAuthClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicPhoneAuthClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicPlaybackVkeyClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicPlaybackVkeyClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicPlaylistClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicPlaylistClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicQrLoginClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicQrLoginClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicRankingClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicRankingClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicRichLyricsClient.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicRichLyricsClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/qqmusic/QQMusicSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyApiClient.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyApiClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyClientConfig.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyClientConfig.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyLibrespotPlayback.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyLibrespotPlayback.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyOAuth.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyOAuth.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifySessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifySessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyTrackMatcher.kt` | `shared/src/commonMain/kotlin/melox/provider/spotify/SpotifyTrackMatcher.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/youtubemusic/YouTubeMusicProvider.kt` | `shared/src/commonMain/kotlin/melox/provider/youtubemusic/YouTubeMusicProvider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/provider/youtubemusic/YouTubeSessionStore.kt` | `shared/src/commonMain/kotlin/melox/provider/youtubemusic/YouTubeSessionStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/recognition/LocalAudioSegmentExtractor.kt` | `shared/src/commonMain/kotlin/melox/recognition/LocalAudioSegmentExtractor.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/recognition/SongRecognitionClient.kt` | `shared/src/commonMain/kotlin/melox/recognition/SongRecognitionClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/recommendation/LocalRecommendationEngine.kt` | `shared/src/commonMain/kotlin/melox/recommendation/LocalRecommendationEngine.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/recommendation/LocalRecommendationModels.kt` | `shared/src/commonMain/kotlin/melox/recommendation/LocalRecommendationModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/recommendation/LocalRecommendationStore.kt` | `shared/src/commonMain/kotlin/melox/recommendation/LocalRecommendationStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigClient.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigConsent.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigConsent.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigDefaults.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigDefaults.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigModels.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigModels.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigPolicy.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigPolicy.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigRuntime.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigStore.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigVerifier.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteConfigVerifier.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteNoticeStore.kt` | `shared/src/commonMain/kotlin/melox/remoteconfig/MeloXRemoteNoticeStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/core/update/MeloXUpdateClient.kt` | `shared/src/commonMain/kotlin/melox/update/MeloXUpdateClient.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/floating/MeloXFloatingLyricsService.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/lyricon/MeloXLyriconBridge.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/vivo/VivoAtomicIslandBridge.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/xiaomi/HyperOsFocusBridge.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/xiaomi/ShizukuKeepAliveService.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/xiaomi/ShizukuXmsfNetworkHelper.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/platform/xiaomi/XiaomiSuperIslandLyricService.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/ChkszPlaybackResolver.kt` | `shared/src/commonMain/kotlin/melox/playback/ChkszPlaybackResolver.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/CrossProviderPlaybackFallback.kt` | `shared/src/commonMain/kotlin/melox/playback/CrossProviderPlaybackFallback.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/LxUserPlaybackResolver.kt` | `shared/src/commonMain/kotlin/melox/playback/LxUserPlaybackResolver.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAudioAnalysisRuntime.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAudioAnalysisRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAudioAnalysisStore.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAudioAnalysisStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAudioReactiveRuntime.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAudioReactiveRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAutoMix.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAutoMix.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAutoMixAudioAnalyzer.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAutoMixAudioAnalyzer.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAutoMixEqualizerEnvelope.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXAutoMixEqualizerEnvelope.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXAutoMixTransitionRuntime.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXEqualizerController.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXEqualizerController.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXListenTogetherCoordinator.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXListenTogetherCoordinator.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXListenedTimeTracker.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXListenedTimeTracker.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXMediaCache.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXMediaCache.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXMediaPrefetcher.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXMediaPrefetcher.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackHistoryReporter.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXPlaybackHistoryReporter.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackModePreferences.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXPlaybackModePreferences.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackQueueStore.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXPlaybackQueueStore.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlaybackService.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXPlaybackService.kt`<br>`shared/src/commonMain/kotlin/melox/playback/PlaybackCommands.kt`<br>`shared/src/commonMain/kotlin/melox/player/AudioPlayer.kt`<br>`shared/src/commonMain/kotlin/melox/playback/MeloXDesktopAutoplay.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXPlayerTransitionState.kt` | `shared/src/commonMain/kotlin/melox/playback/MeloXPlayerTransitionState.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/MeloXSmartQueueBuilder.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/NeteasePlaybackResolver.kt` | `shared/src/commonMain/kotlin/melox/playback/NeteasePlaybackResolver.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/PlaybackCommands.kt` | `shared/src/commonMain/kotlin/melox/playback/PlaybackCommands.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/PlaybackTrackIdentity.kt` | `shared/src/commonMain/kotlin/melox/playback/PlaybackTrackIdentity.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/ProviderPlaybackCommands.kt` | `shared/src/commonMain/kotlin/melox/playback/ProviderPlaybackCommands.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/ProviderPlaybackQualityRuntime.kt` | `shared/src/commonMain/kotlin/melox/playback/ProviderPlaybackQualityRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/ProviderPlaybackResolver.kt` | `shared/src/commonMain/kotlin/melox/playback/ProviderPlaybackResolver.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/playback/ProviderPlaybackRuntime.kt` | `shared/src/commonMain/kotlin/melox/playback/ProviderPlaybackRuntime.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXApp.kt` | `desktop/src/desktopMain/kotlin/melox/ui/MeloXApp.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXChromeRuntime.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXLayout.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXPageTransitions.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXPerformanceOverlay.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/MeloXPinkCat.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/AppleMusicLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/BilibiliLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/KugouLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/KuwoLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/MeloXAccountActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/MeloXPhoneCodeLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/NeteaseLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/QQMusicLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/SpotifyLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/account/YouTubeLoginScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/animation/MeloXMotion.kt` | `desktop/src/desktopMain/kotlin/melox/ui/foundation/MeloXMotion.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/animation/MeloXMotionClock.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/cloud/MeloXCloudMusicScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/collection/MeloXArtistDetailScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/collection/MeloXCollectionDetailActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/discovery/MeloXDiscoveryScreens.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/HomeScreen.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/ExploreScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/BottomBarToneSampler.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXAdvancedGlassComponents.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXAdvancedGlassComponents.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXBackdropComponents.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXBackdropComponents.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXGlassSafety.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXGlassSheet.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXGlassSheet.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXGlassTokens.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXGlassTokens.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXIosComponents.kt` | `desktop/src/desktopMain/kotlin/melox/ui/foundation/MeloXComponents.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXLiquidSlider.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXLiquidSlider.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXNativeGlassComponents.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXNativeGlassComponents.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXPinnedListPage.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXPinnedListPage.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXSettingsDropdown.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/MeloXSettingsDropdown.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXSwipeActionRow.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXSwipeActionRow.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/MeloXSymbol.kt` | `desktop/src/desktopMain/kotlin/melox/ui/foundation/MeloXSymbol.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/liquidsliderdemo/DampedDragAnimation.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/liquidsliderdemo/DragGestureInspector.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/liquidsliderdemo/LiquidSlider.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/publicdemo/DragGestureInspector.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/publicdemo/LiquidDragAnimation.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/publicdemo/PublicDampedDragAnimation.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/publicdemo/PublicDampedDragAnimation.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/glass/publicdemo/PublicInteractiveHighlight.kt` | `desktop/src/desktopMain/kotlin/melox/ui/glass/publicdemo/PublicInteractiveHighlight.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/layout/MeloXAdaptiveLayout.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/legal/MeloXCloudControlConsentDialog.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/legal/MeloXLegalDocuments.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/legal/MeloXRemoteNoticeDialog.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/library/LibraryScreen.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXLibraryScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/library/MeloXBatchDownloadSheet.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/library/MeloXDetailPalette.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXDetailPalette.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/library/MeloXPlaylistActionsOverlay.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/messages/MessagesScreen.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MessagesScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/ArtworkDynamicPalette.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/BilibiliLyricOffset.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXAlternativeLyricsPanels.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXClassicNowPlayingScene.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXFlowingLightBackdrop.kt` | `desktop/src/desktopMain/kotlin/melox/ui/player/MeloXFlowingLightBackdrop.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsPanel.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXPlayerLyrics.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXGlyphLyricText.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXLyricCascade.kt`<br>`shared/src/commonMain/kotlin/melox/lyrics/MeloXLyricPresentation.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSMiniPlayer.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXMiniPlayer.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingScene.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXIOSPlayerScene.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXIOSLandscapePlayerScene.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingSharedHost.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXNowPlaying.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXIOSPlayerScene.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXIOSNowPlayingV2.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXNowPlaying.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXImmersivePlaybackEffect.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXListenTogetherInviteActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXLyricShareDialog.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXPlayerLyrics.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXLyricShareImage.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXPlayerLyrics.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXLyricsPanel.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingActionsSheet.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXNowPlayingCoreControls.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXNowPlaying.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXPlayerQuality.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerInsets.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerTransitionKeys.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerTransitionSpec.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXPlayerUi.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXProviderLyricsLoader.kt` | `shared/src/commonMain/kotlin/melox/lyrics/MeloXLyricsLoad.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXProviderSongActionsOverlay.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXQualitySelectionOverlay.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXPlayerQuality.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXQueuePanel.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXQueuePanel.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXSharedTransitions.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/player/MeloXSongActionsOverlay.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/MeloXSongActionsOverlay.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/podcast/MeloXPodcastScreens.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/MeloXProviderServicesActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/ProviderCatalogSearchScreen.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/ProviderDiscoveryScreens.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/ProviderServicesScreen.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/ProviderServicesScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/ProviderSettingsHub.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/provider/ProviderUiComponents.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/search/SearchScreen.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/SearchScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/settings/MeloXSettingsPreferences.kt` | `shared/src/commonMain/kotlin/melox/settings/MeloXSettingsPreferences.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt` | `desktop/src/desktopMain/kotlin/melox/ui/screens/SettingsScreen.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/sharing/MeloXNeteaseResourceShareActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/song/MeloXSongWikiActivity.kt` | — | MISSING |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/theme/MeloXMiSansFont.kt` | `desktop/src/desktopMain/kotlin/melox/ui/theme/MeloXFont.kt` | PARTIAL |
| `android/app/src/main/kotlin/com/lladlam/melox/ui/theme/MeloXTheme.kt` | `desktop/src/desktopMain/kotlin/melox/ui/theme/MeloXMaterialTheme.kt`<br>`desktop/src/desktopMain/kotlin/melox/ui/MeloXApp.kt` | PARTIAL |
| `android/app/src/main/res/drawable/ic_melox_brand_gear.xml` | — | MISSING |
| `android/app/src/main/res/drawable/melox_home_tab.xml` | — | MISSING |
| `android/app/src/main/res/font/material_symbols_rounded_filled.xml` | — | MISSING |
| `android/app/src/main/res/font/mi_lan_pro.xml` | — | MISSING |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | — | MISSING |
| `android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | — | MISSING |
| `android/app/src/main/res/values-ja/strings.xml` | — | MISSING |
| `android/app/src/main/res/values-zh-rCN/strings.xml` | — | MISSING |
| `android/app/src/main/res/values-zh-rTW/strings.xml` | — | MISSING |
| `android/app/src/main/res/values/ic_launcher_background.xml` | — | MISSING |
| `android/app/src/main/res/values/lyricon.xml` | — | MISSING |
| `android/app/src/main/res/values/strings.xml` | — | MISSING |
| `android/app/src/main/res/values/styles.xml` | — | MISSING |
| `android/app/src/main/res/xml/locales_config.xml` | — | MISSING |
| `android/app/src/main/res/xml/melox_file_paths.xml` | — | MISSING |
| `android/app/src/main/res/xml/network_security_config.xml` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/audio/MusicQualityRuntimeTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/audio/NeteaseQualityClientPolicyTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/KugouKrcLyricsParserTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/LrcLyricsParserTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/LyricTimelineProcessorTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/MeloXLyricScriptConverterTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/QQMusicQrcLyricsParserTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/TeyvatTtmlInspectionTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/lyrics/TtmlLyricsParserTest.kt` | `shared/src/commonTest/kotlin/melox/lyrics/TtmlLyricsParserTest.kt` | PARTIAL |
| `android/app/src/test/kotlin/com/lladlam/melox/core/music/MeloXLegacyUiBridgeTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/music/MusicProviderArchitectureTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/music/UnifiedMusicServiceTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/music/provider/PlaybackAccountStoreTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/music/provider/TrackAggregationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/MeloXGitHubRoutingTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/MeloXListenTogetherSnapshotTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/NeteaseClipboardLinkTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/NeteaseListenTogetherInvitationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/NeteasePhoneAuthClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/NeteasePlaylistWriteSpecTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/network/NeteaseSearchDurationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/bilibili/BilibiliWbiSignerTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/kugou/KugouArtworkUrlTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoJsonNormalizerTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoLyricsClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/kuwo/KuwoPhoneAuthClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/lxuser/LxUserSourceStoreTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicFavoriteClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicPhoneAuthClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicPlaybackVkeyClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicQrLoginClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicRichLyricsIntegrationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicSearchArtistParserTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/qqmusic/QQMusicSessionStoreTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyJsonMapperTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyOAuthTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/provider/spotify/SpotifyTrackMatcherTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigModelsTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigPolicyTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigSchedulingTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigThreadingTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteConfigVerifierTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/remoteconfig/MeloXRemoteNoticeStoreTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/core/update/MeloXUpdateClientTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/CrossProviderPlaybackFallbackResolverTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/MeloXAutoMixTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/MeloXAutoplayPolicyTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/MeloXEqualizerControllerTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/MeloXListenedTimeTrackerTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/MeloXLyricsPlaybackSafetyTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/playback/ProviderPlaybackQualityRuntimeTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/MeloXColdStartArchitectureTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/MeloXMeiScreenMigrationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/account/MeloXPhoneCodeLoginTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/discovery/MeloXHomeFullBleedTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/legal/MeloXCloudControlConsentDialogTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/legal/MeloXRemoteNoticeDeliveryTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/player/MeloXAutomaticLyricsSelectionTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/player/MeloXIOSLyricsInterludeFadeTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/player/MeloXPlayerInitializationTest.kt` | — | MISSING |
| `android/app/src/test/kotlin/com/lladlam/melox/ui/player/MeloXTextPVStyleTest.kt` | — | MISSING |
| `android/baselineprofile/src/main/AndroidManifest.xml` | — | MISSING |
| `android/baselineprofile/src/main/kotlin/com/lladlam/melox/baselineprofile/BaselineProfileGenerator.kt` | — | MISSING |
| `android/baselineprofile/src/main/kotlin/com/lladlam/melox/baselineprofile/MeloXJourneys.kt` | — | MISSING |
| `android/baselineprofile/src/main/kotlin/com/lladlam/melox/baselineprofile/MeloXMacrobenchmark.kt` | — | MISSING |
| `android/innertube/src/main/AndroidManifest.xml` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/InnerTube.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/NetworkConfig.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/YouTube.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/YouTubeConstants.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/AccountInfo.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/AutomixPreviewVideoRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Badges.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Button.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Context.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Continuation.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/ContinuationItemRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Endpoint.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/GridRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Icon.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MediaInfo.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Menu.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCardShelfRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicCarouselShelfRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicDescriptionShelfRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicEditablePlaylistDetailHeaderRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicMultiRowImageItemRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicMultiRowListItemRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicNavigationButtonRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicPlaylistShelfRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicQueueRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveHeaderRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicResponsiveListItemRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicShelfRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/MusicTwoRowItemRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/NavigationEndpoint.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistDeleteBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/PlaylistPanelVideoRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/ResponseContext.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/ReturnYouTubeDislikeResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Runs.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestions.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/SearchSuggestionsSectionRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/SectionListRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/SubscriptionButton.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Tabs.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/TasteProfile.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/ThumbnailRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/Thumbnails.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/TwoColumnBrowseResultsRenderer.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/UrlEndpoint.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/YTItem.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeChannel.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeClient.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeDataPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/YouTubeLocale.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/AccountMenuBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/BrowseBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/CreatePlaylistBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/EditPlaylistBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/FeedbackBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetQueueBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetSearchSuggestionsBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/GetTranscriptBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/LikeBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/NextBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/PlayerBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/SearchBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/body/SubscribeBody.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountMenuResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/AccountsListResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/AddItemYouTubePlaylistResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/BrowseResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/ContinuationResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/CreatePlaylistResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/EditPlaylistResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/FeedbackResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetQueueResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetSearchSuggestionsResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/GetTranscriptResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/ImageUploadResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/NextResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/PlayerResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/models/response/SearchResponse.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/AlbumPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsContinuationPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistItemsPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/ArtistPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/BrowseResult.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/ChartsPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/ExplorePage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/HistoryPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/HomePage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryAlbumsPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryContinuationPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/LibraryPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/MoodAndGenres.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/NewReleaseAlbumPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/NextPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/PageHelper.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistContinuationPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/PlaylistPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/PodcastPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/RelatedPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSuggestionPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/pages/SearchSummaryPage.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/strategy/ContentAwareFallbackStrategy.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/utils/Utils.kt` | — | MISSING |
| `android/innertube/src/main/kotlin/com/metrolist/innertube/utils/YouTubeUrlParser.kt` | — | MISSING |

## Provider capability matrix

Method declarations alone never qualify as MATCH.

| Provider | Capability | Android declares | Windows declares | Status |
| --- | --- | --- | --- | --- |
| Netease | song search | True | True | PARTIAL |
| Netease | playlist search | False | False | PARTIAL |
| Netease | album search | False | False | PARTIAL |
| Netease | artist search | False | False | PARTIAL |
| Netease | home/recommendations | False | False | PARTIAL |
| Netease | rankings | False | False | PARTIAL |
| Netease | playlist detail | False | False | PARTIAL |
| Netease | album detail | False | False | PARTIAL |
| Netease | artist detail | False | False | PARTIAL |
| Netease | playback | True | True | PARTIAL |
| Netease | lyrics | True | True | PARTIAL |
| Netease | favorite/unfavorite | False | False | PARTIAL |
| Netease | user playlists | False | False | PARTIAL |
| Netease | create playlist | False | False | PARTIAL |
| Netease | rename playlist | False | False | PARTIAL |
| Netease | delete playlist | False | False | PARTIAL |
| Netease | add track | False | False | PARTIAL |
| Netease | remove track | False | False | PARTIAL |
| Netease | reorder track | False | False | PARTIAL |
| Netease | saved albums | False | False | PARTIAL |
| Netease | followed artists | False | False | PARTIAL |
| Netease | download | False | False | PARTIAL |
| Netease | account summary | False | False | PARTIAL |
| Netease | login | — | — | PARTIAL |
| Netease | session | — | — | PARTIAL |
| Netease | fallback | — | — | PARTIAL |
| QQMusic | song search | True | True | PARTIAL |
| QQMusic | playlist search | True | True | PARTIAL |
| QQMusic | album search | True | True | PARTIAL |
| QQMusic | artist search | True | True | PARTIAL |
| QQMusic | home/recommendations | True | True | PARTIAL |
| QQMusic | rankings | True | True | PARTIAL |
| QQMusic | playlist detail | True | True | PARTIAL |
| QQMusic | album detail | True | True | PARTIAL |
| QQMusic | artist detail | True | True | PARTIAL |
| QQMusic | playback | True | True | PARTIAL |
| QQMusic | lyrics | True | True | PARTIAL |
| QQMusic | favorite/unfavorite | True | True | PARTIAL |
| QQMusic | user playlists | True | True | PARTIAL |
| QQMusic | create playlist | False | False | PARTIAL |
| QQMusic | rename playlist | False | False | PARTIAL |
| QQMusic | delete playlist | False | False | PARTIAL |
| QQMusic | add track | False | False | PARTIAL |
| QQMusic | remove track | False | False | PARTIAL |
| QQMusic | reorder track | False | False | PARTIAL |
| QQMusic | saved albums | False | False | PARTIAL |
| QQMusic | followed artists | False | False | PARTIAL |
| QQMusic | download | False | False | PARTIAL |
| QQMusic | account summary | True | True | PARTIAL |
| QQMusic | login | — | — | PARTIAL |
| QQMusic | session | — | — | PARTIAL |
| QQMusic | fallback | — | — | PARTIAL |
| Kugou | song search | True | True | PARTIAL |
| Kugou | playlist search | True | True | PARTIAL |
| Kugou | album search | True | True | PARTIAL |
| Kugou | artist search | True | True | PARTIAL |
| Kugou | home/recommendations | True | True | PARTIAL |
| Kugou | rankings | True | True | PARTIAL |
| Kugou | playlist detail | True | True | PARTIAL |
| Kugou | album detail | True | True | PARTIAL |
| Kugou | artist detail | True | True | PARTIAL |
| Kugou | playback | True | True | PARTIAL |
| Kugou | lyrics | True | True | PARTIAL |
| Kugou | favorite/unfavorite | False | False | PARTIAL |
| Kugou | user playlists | True | True | PARTIAL |
| Kugou | create playlist | False | False | PARTIAL |
| Kugou | rename playlist | False | False | PARTIAL |
| Kugou | delete playlist | False | False | PARTIAL |
| Kugou | add track | True | True | PARTIAL |
| Kugou | remove track | False | False | PARTIAL |
| Kugou | reorder track | False | False | PARTIAL |
| Kugou | saved albums | False | False | PARTIAL |
| Kugou | followed artists | False | False | PARTIAL |
| Kugou | download | False | False | PARTIAL |
| Kugou | account summary | True | True | PARTIAL |
| Kugou | login | — | — | PARTIAL |
| Kugou | session | — | — | PARTIAL |
| Kugou | fallback | — | — | PARTIAL |
| Kuwo | song search | True | True | PARTIAL |
| Kuwo | playlist search | False | False | PARTIAL |
| Kuwo | album search | False | False | PARTIAL |
| Kuwo | artist search | False | False | PARTIAL |
| Kuwo | home/recommendations | False | False | PARTIAL |
| Kuwo | rankings | False | False | PARTIAL |
| Kuwo | playlist detail | True | False | MISSING |
| Kuwo | album detail | False | False | PARTIAL |
| Kuwo | artist detail | False | False | PARTIAL |
| Kuwo | playback | True | True | PARTIAL |
| Kuwo | lyrics | True | False | MISSING |
| Kuwo | favorite/unfavorite | False | False | PARTIAL |
| Kuwo | user playlists | True | False | MISSING |
| Kuwo | create playlist | False | False | PARTIAL |
| Kuwo | rename playlist | False | False | PARTIAL |
| Kuwo | delete playlist | False | False | PARTIAL |
| Kuwo | add track | False | False | PARTIAL |
| Kuwo | remove track | False | False | PARTIAL |
| Kuwo | reorder track | False | False | PARTIAL |
| Kuwo | saved albums | False | False | PARTIAL |
| Kuwo | followed artists | False | False | PARTIAL |
| Kuwo | download | False | False | PARTIAL |
| Kuwo | account summary | True | False | MISSING |
| Kuwo | login | — | — | PARTIAL |
| Kuwo | session | — | — | PARTIAL |
| Kuwo | fallback | — | — | PARTIAL |
| Spotify | song search | True | True | PARTIAL |
| Spotify | playlist search | True | True | PARTIAL |
| Spotify | album search | True | True | PARTIAL |
| Spotify | artist search | True | True | PARTIAL |
| Spotify | home/recommendations | False | False | PARTIAL |
| Spotify | rankings | False | False | PARTIAL |
| Spotify | playlist detail | True | True | PARTIAL |
| Spotify | album detail | True | True | PARTIAL |
| Spotify | artist detail | True | True | PARTIAL |
| Spotify | playback | True | True | PARTIAL |
| Spotify | lyrics | True | True | PARTIAL |
| Spotify | favorite/unfavorite | True | True | PARTIAL |
| Spotify | user playlists | True | True | PARTIAL |
| Spotify | create playlist | True | True | PARTIAL |
| Spotify | rename playlist | True | True | PARTIAL |
| Spotify | delete playlist | True | True | PARTIAL |
| Spotify | add track | True | True | PARTIAL |
| Spotify | remove track | True | True | PARTIAL |
| Spotify | reorder track | True | True | PARTIAL |
| Spotify | saved albums | True | True | PARTIAL |
| Spotify | followed artists | True | True | PARTIAL |
| Spotify | download | True | True | PARTIAL |
| Spotify | account summary | True | True | PARTIAL |
| Spotify | login | — | — | PARTIAL |
| Spotify | session | — | — | PARTIAL |
| Spotify | fallback | — | — | PARTIAL |
| YouTubeMusic | song search | True | True | PARTIAL |
| YouTubeMusic | playlist search | True | True | PARTIAL |
| YouTubeMusic | album search | True | True | PARTIAL |
| YouTubeMusic | artist search | True | True | PARTIAL |
| YouTubeMusic | home/recommendations | True | True | PARTIAL |
| YouTubeMusic | rankings | True | True | PARTIAL |
| YouTubeMusic | playlist detail | True | True | PARTIAL |
| YouTubeMusic | album detail | True | True | PARTIAL |
| YouTubeMusic | artist detail | True | True | PARTIAL |
| YouTubeMusic | playback | True | True | PARTIAL |
| YouTubeMusic | lyrics | True | True | PARTIAL |
| YouTubeMusic | favorite/unfavorite | True | True | PARTIAL |
| YouTubeMusic | user playlists | True | True | PARTIAL |
| YouTubeMusic | create playlist | True | True | PARTIAL |
| YouTubeMusic | rename playlist | True | True | PARTIAL |
| YouTubeMusic | delete playlist | True | True | PARTIAL |
| YouTubeMusic | add track | True | True | PARTIAL |
| YouTubeMusic | remove track | True | True | PARTIAL |
| YouTubeMusic | reorder track | True | True | PARTIAL |
| YouTubeMusic | saved albums | True | True | PARTIAL |
| YouTubeMusic | followed artists | True | True | PARTIAL |
| YouTubeMusic | download | True | True | PARTIAL |
| YouTubeMusic | account summary | True | True | PARTIAL |
| YouTubeMusic | login | — | — | PARTIAL |
| YouTubeMusic | session | — | — | PARTIAL |
| YouTubeMusic | fallback | — | — | PARTIAL |
| Bilibili | song search | True | True | PARTIAL |
| Bilibili | playlist search | False | False | PARTIAL |
| Bilibili | album search | False | False | PARTIAL |
| Bilibili | artist search | False | False | PARTIAL |
| Bilibili | home/recommendations | False | False | PARTIAL |
| Bilibili | rankings | False | False | PARTIAL |
| Bilibili | playlist detail | True | False | MISSING |
| Bilibili | album detail | False | False | PARTIAL |
| Bilibili | artist detail | False | False | PARTIAL |
| Bilibili | playback | True | True | PARTIAL |
| Bilibili | lyrics | False | False | PARTIAL |
| Bilibili | favorite/unfavorite | False | False | PARTIAL |
| Bilibili | user playlists | True | False | MISSING |
| Bilibili | create playlist | False | False | PARTIAL |
| Bilibili | rename playlist | False | False | PARTIAL |
| Bilibili | delete playlist | False | False | PARTIAL |
| Bilibili | add track | False | False | PARTIAL |
| Bilibili | remove track | False | False | PARTIAL |
| Bilibili | reorder track | False | False | PARTIAL |
| Bilibili | saved albums | False | False | PARTIAL |
| Bilibili | followed artists | False | False | PARTIAL |
| Bilibili | download | False | False | PARTIAL |
| Bilibili | account summary | True | False | MISSING |
| Bilibili | login | — | — | PARTIAL |
| Bilibili | session | — | — | PARTIAL |
| Bilibili | fallback | — | — | PARTIAL |
| Jellyfin | song search | True | True | PARTIAL |
| Jellyfin | playlist search | True | False | MISSING |
| Jellyfin | album search | True | False | MISSING |
| Jellyfin | artist search | True | False | MISSING |
| Jellyfin | home/recommendations | False | False | PARTIAL |
| Jellyfin | rankings | False | False | PARTIAL |
| Jellyfin | playlist detail | True | False | MISSING |
| Jellyfin | album detail | True | False | MISSING |
| Jellyfin | artist detail | True | False | MISSING |
| Jellyfin | playback | True | True | PARTIAL |
| Jellyfin | lyrics | False | False | PARTIAL |
| Jellyfin | favorite/unfavorite | True | False | MISSING |
| Jellyfin | user playlists | True | False | MISSING |
| Jellyfin | create playlist | True | False | MISSING |
| Jellyfin | rename playlist | True | False | MISSING |
| Jellyfin | delete playlist | False | False | PARTIAL |
| Jellyfin | add track | True | False | MISSING |
| Jellyfin | remove track | True | False | MISSING |
| Jellyfin | reorder track | True | False | MISSING |
| Jellyfin | saved albums | False | False | PARTIAL |
| Jellyfin | followed artists | False | False | PARTIAL |
| Jellyfin | download | False | False | PARTIAL |
| Jellyfin | account summary | True | False | MISSING |
| Jellyfin | login | — | — | PARTIAL |
| Jellyfin | session | — | — | PARTIAL |
| Jellyfin | fallback | — | — | PARTIAL |
| Local | song search | True | True | PARTIAL |
| Local | playlist search | False | False | PARTIAL |
| Local | album search | False | False | PARTIAL |
| Local | artist search | False | False | PARTIAL |
| Local | home/recommendations | False | False | PARTIAL |
| Local | rankings | False | False | PARTIAL |
| Local | playlist detail | True | False | MISSING |
| Local | album detail | False | False | PARTIAL |
| Local | artist detail | False | False | PARTIAL |
| Local | playback | True | True | PARTIAL |
| Local | lyrics | True | False | MISSING |
| Local | favorite/unfavorite | True | False | MISSING |
| Local | user playlists | False | False | PARTIAL |
| Local | create playlist | True | False | MISSING |
| Local | rename playlist | True | False | MISSING |
| Local | delete playlist | True | False | MISSING |
| Local | add track | True | False | MISSING |
| Local | remove track | True | False | MISSING |
| Local | reorder track | True | False | MISSING |
| Local | saved albums | False | False | PARTIAL |
| Local | followed artists | False | False | PARTIAL |
| Local | download | False | False | PARTIAL |
| Local | account summary | False | False | PARTIAL |
| Local | login | — | — | PARTIAL |
| Local | session | — | — | PARTIAL |
| Local | fallback | — | — | PARTIAL |
| LxUser | song search | False | False | PARTIAL |
| LxUser | playlist search | False | False | PARTIAL |
| LxUser | album search | False | False | PARTIAL |
| LxUser | artist search | False | False | PARTIAL |
| LxUser | home/recommendations | False | False | PARTIAL |
| LxUser | rankings | False | False | PARTIAL |
| LxUser | playlist detail | False | False | PARTIAL |
| LxUser | album detail | False | False | PARTIAL |
| LxUser | artist detail | False | False | PARTIAL |
| LxUser | playback | False | False | PARTIAL |
| LxUser | lyrics | False | False | PARTIAL |
| LxUser | favorite/unfavorite | False | False | PARTIAL |
| LxUser | user playlists | False | False | PARTIAL |
| LxUser | create playlist | False | False | PARTIAL |
| LxUser | rename playlist | False | False | PARTIAL |
| LxUser | delete playlist | False | False | PARTIAL |
| LxUser | add track | False | False | PARTIAL |
| LxUser | remove track | False | False | PARTIAL |
| LxUser | reorder track | False | False | PARTIAL |
| LxUser | saved albums | False | False | PARTIAL |
| LxUser | followed artists | False | False | PARTIAL |
| LxUser | download | False | False | PARTIAL |
| LxUser | account summary | False | False | PARTIAL |
| LxUser | login | — | — | PARTIAL |
| LxUser | session | — | — | PARTIAL |
| LxUser | fallback | — | — | PARTIAL |
| AppleMusic | song search | False | False | PARTIAL |
| AppleMusic | playlist search | False | False | PARTIAL |
| AppleMusic | album search | False | False | PARTIAL |
| AppleMusic | artist search | False | False | PARTIAL |
| AppleMusic | home/recommendations | False | False | PARTIAL |
| AppleMusic | rankings | False | False | PARTIAL |
| AppleMusic | playlist detail | False | False | PARTIAL |
| AppleMusic | album detail | False | False | PARTIAL |
| AppleMusic | artist detail | False | False | PARTIAL |
| AppleMusic | playback | False | False | PARTIAL |
| AppleMusic | lyrics | False | False | PARTIAL |
| AppleMusic | favorite/unfavorite | False | False | PARTIAL |
| AppleMusic | user playlists | False | False | PARTIAL |
| AppleMusic | create playlist | False | False | PARTIAL |
| AppleMusic | rename playlist | False | False | PARTIAL |
| AppleMusic | delete playlist | False | False | PARTIAL |
| AppleMusic | add track | False | False | PARTIAL |
| AppleMusic | remove track | False | False | PARTIAL |
| AppleMusic | reorder track | False | False | PARTIAL |
| AppleMusic | saved albums | False | False | PARTIAL |
| AppleMusic | followed artists | False | False | PARTIAL |
| AppleMusic | download | False | False | PARTIAL |
| AppleMusic | account summary | False | False | PARTIAL |
| AppleMusic | login | — | — | PARTIAL |
| AppleMusic | session | — | — | PARTIAL |
| AppleMusic | fallback | — | — | PARTIAL |
