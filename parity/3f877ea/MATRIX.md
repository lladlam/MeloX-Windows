# Sol third-round migration — pinned Android 3f877ea

Locked Android acceptance truth: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3` (initial origin/main fetch).
Windows starting point: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.
Previous Android comparison point: `94e17c920bf554966584955afa2bd2ee92580a8b`.
The initial upstream delta contains **7 commits / 24 files**. Android's dirty local tree is not a source for this migration.

The 2026-10-12 follow-up fetch found origin/main at `febb4c0534787e040a89df8457e630d067f44b80`, an additional **9 commits / 154 files** after this pinned acceptance source. Raw file/commit intake and pure-export hashes are retained in `evidence/upstream-intake-febb4c0.json`; `UPSTREAM_INTAKE.md` records the reviewed major functional/schema/platform delta. AutoSync/model alignment, Smart Queue analysis budget and signed hot-update text overrides in that new source are **not yet migrated or accepted**; evidence below continues to refer to `3f877ea`.

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
| Search launch request and provider-aware/approved unified search | `ui/search/SearchScreen.kt` (`MeloXSearchLaunchBus`, UnifiedMusicService) | App Shell launch bus, existing-key observable approval scope, actual concurrent aggregation/partial errors/native identity and provider playback; cancellation is retained | UNIFIED_SEARCH.md; account/catalog/download/alternate-candidate acceptance and pinned pixels remain outstanding | PARTIAL |
| FFmpeg source and packaged executable integrity | `.github/workflows/build.yml`, decoder resources | Explicit LFS checkout; manifest size/SHA-256/x64 ELF+PE; source/JAR/app-image/final-ZIP native decode checks | Windows run 38200697967 at 5f2d149 passed 124 tests plus source/JAR/app-image/final-ZIP native checks; evidence/windows-ci-5f2d149. Previous runs remain archived | PLATFORM_EQUIVALENT |
| Real PCM beat/energy/FFT/audible/phrase analysis | `playback/MeloXAutoMixAudioAnalyzer.kt` | Real independent FFmpeg decoder + 11025Hz/1024 FFT/512 hop, signed PCM, source-keyed cache, cancellable native ownership | Generated 90/105/140 BPM, silence, FFT bands, malformed data and real WAV decode tests; no claim of production AutoMix | PARTIAL |
| Primary/incoming mixing, tempo, EQ, SmartQueue, dual-cover handoff | `MeloXPlaybackService`, `MeloXAutoMixTransitionRuntime`, `MeloXSmartQueueBuilder`, bass envelope | Production AudioPlayer continuous dual-native-decode/PCM mix; controller scored/fallback plans/preload; native pitch-preserving tempo; real RBJ bass exchange; analyzed candidate queue; clock-driven dual covers and settings/buttons | AUTOMIX.md; real two-frequency kernel/controller tests and independent capture PCM readback; 7 production UI scenes / 8 assertions. Source cover-shadow artifact fixed from screenshot. Authenticated streams/Windows hardware/Android pixels outstanding | PARTIAL |
| Local source playback and search preserve file identity | `core/provider/local/LocalProvider.kt`, provider resolver | Real indexed Local search and readable-file resolution; encoded track metadata/URI, no online fallback; bridge preserves collection identity | Production Local provider/controller AutoMix tests, Unicode/plus/ampersand path regression and generated-source UI replay | PARTIAL |
| Cloud file upload, cancellation and refresh | `NeteaseUniversalSearchClient.uploadCloudSong`, `ui/cloud/MeloXCloudMusicScreen` | Actual desktop file picker and FFmpeg metadata/MD5; upload-check/token/LBS/NOS streaming PUT/info fallback/pub; request ownership, snapshot cleanup and successful refresh; embedded Library cloud actions | CLOUD_UPLOAD.md; six real-file/encrypted-HTTP protocol fixture tests. Desktop chooser/live authenticated remote upload not available in this environment | PARTIAL |
| Settings TabLayout, persisted Library page, ordered home blocks and reachable shortcuts | `ui/settings/SettingsScreen.kt:2588–2737`, `ui/library/LibraryScreen.kt:227–241,374–389`, `MeloXDiscoveryScreens.kt:338–346` | Dedicated business-wired TabLayout route, existing keys, shared layout policy, saved source-specific Library pages, feature/placement gates and configured default; QuickActions joins saved home order; shortcuts reach enabled modules without dock placement | CONTENT_LAYOUT.md; persisted-state regression and production settings replay. Authenticated Library and pinned Android pixels remain blocked | PARTIAL |
| Signed compatibility config, consent/withdrawal, real DoH and subsequent-request policy | `core/remoteconfig/*`, `core/network/MeloXGitHubRouting`, `MeloXApp`, `ContentSettings`, login screens and PlaybackService | Runtime lifecycle and real RemoteConfig route; fixed URLs/key, actual DNSPod DoH, cancellable/bounded HTTP and atomic cache/rollback; production login/playback/local-choice fallback gates and persisted notices; controlled-toggle confirmation fix | REMOTE_CONFIG.md; ten real P-256/HTTP/cache/cancellation/policy regressions. Windows lifecycle and pinned Android reference pixels outstanding; post-onboarding/Privacy integration remains partial | PARTIAL |
| Windows sustained audible playback / AutoMix | Physical output requirement | `tools/windows_audio_acceptance.ps1` checks final app-image and captures explicit user observations | No Windows 10/11 device or audio hardware in this Linux session | BLOCKED |
| Android/Windows same-content pixel and dynamic frames | All named UI pages and player scenes | Production ImageComposeScene capture retained; old reference evidence preserved | Connected Android devices = 0; missing current pinned reference frames. No new MATCH from unrelated feed SSIM | BLOCKED |
| Remaining settings, Classic/alternate lyric shells, Glass refraction, podcast/provider acceptance | Source paths specified by task | Existing implementations retained; confirmed gaps require subsequent batches | Not accepted by build or old matrix inventory | PARTIAL |

## Evidence levels

1. Automated unit/state tests.
2. Linux native FFmpeg / PCM and offscreen production UI simulation.
3. Windows CI build / packaged native decoder (must reference actual run SHA).
4. Windows physical audible playback (currently BLOCKED).
5. Same-content pinned Android/Windows pixels and dynamic frame acceptance (currently BLOCKED).

Latest Unified Search batch automated results: **130 tests / 22 suites / 0 failures, errors or skipped**;
desktopJar + Linux uber JAR successful. Source and final Linux uber JAR were
verified independently (size/SHA-256/magic, native WAV/MP3/FLAC/AAC, required HTTP
authorization headers). See `evidence/upstream-validation.json` and native
decoder JSON. Latest implementation: `evidence/automix-validation.json`,
`automix-ui-v3/`, `automix-pcm-readback.json`, and
`automix-linux-packaged-decoder.json`. Cloud: `cloud-validation.json`,
`cloud-ui-v3/` (3 scenes / 7 missing-login refresh/retry assertions),
`cloud-tests/` and `cloud-linux-packaged-decoder.json`.
Content layout: `content-layout-validation.json`, `content-layout-tests/`,
`content-layout-ui-final/` (15 state scenes / 14 assertions / 42 dynamic frame
captures with requested and actual times) and `content-layout-linux-packaged-decoder.json`.
`content-layout-home-shortcut/` adds 4 scenes / 7 assertions with genuine Home
entry, hidden dock placement, missing-login Cloud and Escape-return behavior.
Remote compatibility protocol regressions: `remote-config-tests/`.
`remote-config-validation.json`, `remote-config-ui-dark/`: 20 state scenes /
25 assertions / 57 dynamic frames, including actual resting-toggle visual
progress after rejection/cancellation. `remote-config-linux-packaged-decoder.json`
verifies the final packaged Linux decoder.
`remote-config-ui-light-live/`: 15 scenes / 14 assertions against the actual
public GhFast-routed signed config v6 and the production pinned key, including
once notice, manual refresh, clear, withdrawal and return. This proves real
public config retrieval, not authenticated Provider acceptance or all-route reachability.
Unified Search: `unified-search-validation.json`, `unified-search-tests/`,
`unified-search-local-ui/` (6 scenes / 11 assertions / 15 frames),
`unified-search-scope-ui/` (13 scenes / 13 assertions), real generated PCM
readback and final Linux package verification. See `UNIFIED_SEARCH.md`.
Previous 81/102/108/114/124-test batches remain archived.
This is not Windows physical playback or Android pixel acceptance.

Each level is reported independently. No `MATCH` entry is introduced solely by compilation, an existing file, or unit-test success.
