# Sol third-round migration — pinned Android 3f877ea

Android truth: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3` (fresh origin/main fetch).
Windows starting point: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.
Previous Android comparison point: `94e17c920bf554966584955afa2bd2ee92580a8b`.
The upstream delta contains **7 commits / 24 files**. Android's dirty local tree is not a source for this migration.

Historical `parity/94e17c9/` evidence is reference material, not new acceptance.

| Observable behavior | Android source | Windows target / correction | Evidence / limitation | Status |
|---|---|---|---|---|
| Broken encrypted sessions do not crash startup; rebuild or process memory | `core/account/SecureSessionPreferences.kt` | `platform/PreferenceFile.kt`: quarantine corrupt ciphertext, verified protected empty rebuild, process-only fallback; no plaintext writes | `SecurePreferenceFileTest`; Windows native DPAPI not available locally | PARTIAL |
| YouTube uses same recovered secure store | `core/provider/youtubemusic/YouTubeSessionStore.kt` | `provider/youtubemusic/YouTubeSessionStore.kt` routes through cached secure session abstraction | Compiled; authenticated account acceptance outstanding | PARTIAL |
| Album/artist identity follows songs through sources, queue, caches/downloads | `SearchSong`, legacy bridge, Netease search/library/home/social/detail parsers, queue, download store | Corresponding shared files; nullable keys preserve old records; search MusicTrack carries artist ID | `DesktopPlaybackStateTest.collectionIdsSurviveQueuePersistenceAndBecomePlaybackMetadata`; full real-source route replay being collected | PARTIAL |
| Explicit queue clear cannot resurrect old songs | `playback/MeloXPlaybackQueueStore.kt` | `MeloXPlaybackQueueStore.clear`, `MeloXDesktopPlayer.clearQueue` / empty install | Real controller regression tests include stale callbacks; startup-empty save remains protected | PARTIAL |
| Collection detail from full-player and row actions | `MeloXApp`, `MeloXSongActionsOverlay`, shared now-playing host/actions sheet/player UI | App Shell collection overlay + direct album/artist actions; no numeric cross-provider routing; provider queries use launch bus | Production physical-input replay required; album page remains visually different | PARTIAL |
| Artist hero / hot-song columns / classified albums / expanded grid / information / follow / nested back | `ui/collection/MeloXArtistDetailScreen.kt` | `screens/ArtistDetailScreen.kt`: source dimensions and structure, real API, 160ms back transforms, state/error/return layering | `evidence/artist-live-v7`: 7 production scenes / 8 assertions; real artist 6452 → album 147779282 → scroll-preserving back, info → Escape → root. Follow requires real account; no pinned Android pixels | PARTIAL |
| Playlist album artwork toggle and hidden search | `LibraryScreen`, `MeloXSettingsPreferences`, `SettingsScreen` | Runtime persistence + live settings + artwork rows + pull-at-top search/filter | Desktop playlist layout still differs; precise reveal gesture/animation not yet accepted | PARTIAL |
| Song-recognition identity additions | `core/recognition/SongRecognitionClient.kt` | Existing Windows recognition client is still an empty-result stub | Requires actual desktop fingerprint/capture implementation; no fabricated matches | MISSING |
| Search launch request and provider-aware routing | `ui/search/SearchScreen.kt` (`MeloXSearchLaunchBus`) | `navigation/MeloXSearchLaunchBus.kt`, App Shell, SearchScreen, album/artist fallback requests | Unified aggregation and provider account replay remain outstanding | PARTIAL |
| FFmpeg source and packaged executable integrity | `.github/workflows/build.yml`, decoder resources | Explicit LFS checkout; manifest size/SHA-256/x64 ELF+PE; source/JAR/app-image/final-ZIP native decode checks | Local integrity/PCM passed; new Windows CI and physical output tracked separately | PARTIAL |
| Real PCM beat/energy/FFT/audible/phrase analysis | `playback/MeloXAutoMixAudioAnalyzer.kt` | Real independent FFmpeg decoder + 11025Hz/1024 FFT/512 hop, signed PCM, source-keyed cache, cancellable native ownership | Generated 90/105/140 BPM, silence, FFT bands, malformed data and real WAV decode tests; no claim of production AutoMix | PARTIAL |
| Primary/incoming mixing, tempo, EQ, SmartQueue, dual-cover handoff | `MeloXPlaybackService`, `MeloXAutoMixTransitionRuntime`, `MeloXSmartQueueBuilder`, bass envelope | Analyzer is the first dependency; production currently remains single deck | Next implementation batch; not represented by logical-ID tests | MISSING |
| Windows sustained audible playback / AutoMix | Physical output requirement | `tools/windows_audio_acceptance.ps1` checks final app-image and captures explicit user observations | No Windows 10/11 device or audio hardware in this Linux session | BLOCKED |
| Android/Windows same-content pixel and dynamic frames | All named UI pages and player scenes | Production ImageComposeScene capture retained; old reference evidence preserved | Connected Android devices = 0; missing current pinned reference frames. No new MATCH from unrelated feed SSIM | BLOCKED |
| Remaining settings, Classic/alternate lyric shells, Glass refraction, cloud upload, podcast/provider acceptance | Source paths specified by task | Existing implementations retained; confirmed gaps require subsequent batches | Not accepted by build or old matrix inventory | PARTIAL |

## Evidence levels

1. Automated unit/state tests.
2. Linux native FFmpeg / PCM and offscreen production UI simulation.
3. Windows CI build / packaged native decoder (must reference actual run SHA).
4. Windows physical audible playback (currently BLOCKED).
5. Same-content pinned Android/Windows pixels and dynamic frame acceptance (currently BLOCKED).

Local batch results: **81 tests / 14 suites / 0 failures, errors or skipped**;
desktopJar + Linux uber JAR successful. Source and final Linux uber JAR were
verified independently (size/SHA-256/magic, native WAV/MP3/FLAC/AAC, required HTTP
authorization headers). See `evidence/upstream-validation.json` and native
decoder JSON. This is not Windows physical playback or Android pixel acceptance.

Each level is reported independently. No `MATCH` entry is introduced solely by compilation, an existing file, or unit-test success.
