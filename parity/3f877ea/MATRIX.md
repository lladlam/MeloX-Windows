# Sol third-round migration — pinned Android 3f877ea

Locked Android acceptance truth: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3` (initial origin/main fetch).
Windows starting point: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.
Previous Android comparison point: `94e17c920bf554966584955afa2bd2ee92580a8b`.
The initial upstream delta contains **7 commits / 24 files**. Android's dirty local tree is not a source for this migration.

The 2026-10-12 follow-up fetch found origin/main at `febb4c0534787e040a89df8457e630d067f44b80`, an additional **9 commits / 154 files** after this pinned acceptance source. Raw file/commit intake and pure-export hashes are retained in `evidence/upstream-intake-febb4c0.json`; `UPSTREAM_INTAKE.md` records the reviewed major functional/schema/platform delta. AutoSync/model alignment, Smart Queue analysis budget and signed hot-update text overrides in that new source are **not yet migrated or accepted**; evidence below continues to refer to `3f877ea`.

Closing fetch: `f14a1c1f5400664d07575a113469f41ee3b70572`, another **3 commits / 28 files** (signed scripts, theme colors, matching rules/strategies and incremental APK update). Separately reviewed/exported in `UPSTREAM_INTAKE.md` / `evidence/upstream-intake-f14a1c1.json`; **not migrated/accepted**, compatibility stays 20.

Settings/legal closing fetch: `5b52a92022a5943e238ffc5d3121c536b2f29e5c`, **5 more commits / 24 files** with rename detection, resource-backed localization/translation folder, podcast localization and explicit/system TTML translation language selection. Pure export and independent intake in `UPSTREAM_INTAKE.md` / `upstream-intake-5b52a92.json`; **17 commits after pinned, none implicitly migrated/accepted**.

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
| FFmpeg source and packaged executable integrity | `.github/workflows/build.yml`, decoder resources | Explicit LFS checkout; manifest size/SHA-256/x64 ELF+PE; source/JAR/app-image/final-ZIP native decode checks | Windows Settings/legal run 38341304307 at 2181ba4 passed 151 tests plus source/JAR/app-image/final-ZIP native checks; evidence/windows-ci-2181ba4. Failed 275032b run retained with bounded PCM correction | PLATFORM_EQUIVALENT |
| Real PCM beat/energy/FFT/audible/phrase analysis | `playback/MeloXAutoMixAudioAnalyzer.kt` | Real independent FFmpeg decoder + 11025Hz/1024 FFT/512 hop, signed PCM, source-keyed cache, cancellable native ownership | Generated 90/105/140 BPM, silence, FFT bands, malformed data and real WAV decode tests; no claim of production AutoMix | PARTIAL |
| Primary/incoming mixing, tempo, EQ, SmartQueue, dual-cover handoff | `MeloXPlaybackService`, `MeloXAutoMixTransitionRuntime`, `MeloXSmartQueueBuilder`, bass envelope | Production AudioPlayer continuous dual-native-decode/PCM mix; controller scored/fallback plans/preload; native pitch-preserving tempo; real RBJ bass exchange; analyzed candidate queue; clock-driven dual covers and settings/buttons | AUTOMIX.md; real two-frequency kernel/controller tests and independent capture PCM readback; 7 production UI scenes / 8 assertions. Source cover-shadow artifact fixed from screenshot. Authenticated streams/Windows hardware/Android pixels outstanding | PARTIAL |
| Local source playback and search preserve file identity | `core/provider/local/LocalProvider.kt`, provider resolver | Real indexed Local search and readable-file resolution; encoded track metadata/URI, no online fallback; bridge preserves collection identity | Production Local provider/controller AutoMix tests, Unicode/plus/ampersand path regression and generated-source UI replay | PARTIAL |
| Cloud file upload, cancellation and refresh | `NeteaseUniversalSearchClient.uploadCloudSong`, `ui/cloud/MeloXCloudMusicScreen` | Actual desktop file picker and FFmpeg metadata/MD5; upload-check/token/LBS/NOS streaming PUT/info fallback/pub; request ownership, snapshot cleanup and successful refresh; embedded Library cloud actions | CLOUD_UPLOAD.md; six real-file/encrypted-HTTP protocol fixture tests. Desktop chooser/live authenticated remote upload not available in this environment | PARTIAL |
| Settings TabLayout, persisted Library page, ordered home blocks and reachable shortcuts | `ui/settings/SettingsScreen.kt:2588–2737`, `ui/library/LibraryScreen.kt:227–241,374–389`, `MeloXDiscoveryScreens.kt:338–346` | Dedicated business-wired TabLayout route, existing keys, shared layout policy, saved source-specific Library pages, feature/placement gates and configured default; QuickActions joins saved home order; shortcuts reach enabled modules without dock placement | CONTENT_LAYOUT.md; persisted-state regression and production settings replay. Authenticated Library and pinned Android pixels remain blocked | PARTIAL |
| Signed compatibility config, consent/withdrawal, real DoH and subsequent-request policy | `core/remoteconfig/*`, `core/network/MeloXGitHubRouting`, `MeloXApp`, `ContentSettings`, login screens and PlaybackService | Runtime lifecycle and real RemoteConfig route; fixed URLs/key, actual DNSPod DoH, cancellable/bounded HTTP and atomic cache/rollback; production login/playback/local-choice fallback gates, notices and full source consent reused after onboarding and in Legal | REMOTE_CONFIG.md, SETTINGS_LEGAL.md; real P-256/HTTP/cache/cancellation/policy regressions plus genuine onboarding/reopen/UI choices. Windows lifecycle and pinned Android pixels outstanding | PARTIAL |
| Settings root, full legal documents, onboarding and on-device privacy consent/erasure | `MeloXApp.kt:958–1008`, `ui/legal/*`, `ui/settings/SettingsScreen.kt:213–239,543–760` | Existing-key persistent ordering, actual app exit/NetEase form/cloud runtime; byte-identical documents and source-default strings; collapsing detail/fade shader with acyclic recording; ten-second consent and joined/background-safe erasure; live account revisions | SETTINGS_LEGAL.md; 151 total tests / 27 suites and 56 production scenes / 81 assertions / 150 frames, real generated dual PCM and final Linux JAR decode. Source's dormant Privacy route is explicitly made reachable; remaining root inventory/details/model/pixels are not accepted | PARTIAL |
| Jellyfin login/session, catalogs/Library, universal playback and native playlist membership writes | `core/provider/jellyfin/*` | Actual desktop entered-server authentication, stable AES key in unchanged DPAPI store, owned/bounded HTTP, real capabilities/endpoints, native server/item/entry identities, token-free models and header-auth account-isolated artwork/playback caches; server-selected unknown quality | JELLYFIN.md; actual HTTP/protected-file/native authorized PCM and queue regressions; production missing-account/login-error replay. Live authorized server, Windows DPAPI/audio and pinned pixels outstanding | PARTIAL |
| Windows sustained audible playback / AutoMix | Physical output requirement | `tools/windows_audio_acceptance.ps1` checks final app-image and captures explicit user observations | No Windows 10/11 device or audio hardware in this Linux session | BLOCKED |
| Android/Windows same-content pixel and dynamic frames | All named UI pages and player scenes | Production ImageComposeScene capture retained; old reference evidence preserved | Connected Android devices = 0; missing current pinned reference frames. No new MATCH from unrelated feed SSIM | BLOCKED |
| Remaining settings, Classic/alternate lyric shells, Glass refraction, podcast/provider acceptance | Source paths specified by task | Existing implementations retained; confirmed gaps require subsequent batches | Not accepted by build or old matrix inventory | PARTIAL |

## Evidence levels

Latest accepted Windows package: **47542d8401ed626e7fff87444499729a0f3c369b**,
run **38372169489**, **SUCCESS**, **166 tests / 31 suites / zero failures,
errors or skipped**. Native EQ and all four decoder stages pass; immutable five-
resource byte checks pass at source/JAR/app-image/final ZIP. Downloaded artifact
digest, final Windows PE decoder, resource bytes and actual native EQ PCM are
independently verified in `windows-ci-47542d8/`. Final ZIP SHA-256
`d79fd2e357efee6bcbc1f26f936afa89346f73e2ac4008c1fd0e316a0c0ba403`.
This supersedes a07e1be's CRLF resource rejection for package acceptance only;
physical/native real-account and Android pixel/motion levels remain BLOCKED.

Windows feature `a07e1be`, run `38368910608`: CI **SUCCESS**, **166 tests / 31
suites**, native EQ and source/JAR/app-image/final ZIP format/header decode pass.
Downloaded artifact digest and Windows PE decoder verify independently. Its
pinned resource bytes are **REJECTED** for Windows checkout CRLF conversion;
normalized text is unchanged. See `windows-ci-a07e1be/`. Explicit LF attributes
and immutable five-resource packaging checks now require a corrected exact-SHA
Windows package; CI success alone did not establish this resource condition.

Search/player/output: `SEARCH_PLAYER_PLAYBACK.md`, `search-player-validation.json`,
`search-player-tests/`, `search-player-native-pcm/` and independent
`search-player-equalizer-readback.json`. **166 tests / 31 suites / zero failures,
errors or skipped**, **9 production replays / 258 states / 747 frames / 215
assertions**, all 18 TextPV styles. Real single/post-mix dual user EQ, native
Songs/artwork/operation IDs, profile/catalog routing, adaptive Classic controls,
EVA/TextPV/Skyline, observed Bilibili offsets and Local persistence are implemented.
Their overall parity remains **PARTIAL**; advanced player/lyrics controls, full
search animations and provider-account acceptance remain outstanding. Tested
online song has no playable non-preview source, and podcast programs require
login (-462); neither is successful playback/catalog acceptance. New Windows CI
must reference the published feature SHA. Hardware/native accounts and pinned
Android pixels/motion remain separately **BLOCKED**.

1. Automated unit/state tests.
2. Linux native FFmpeg / PCM and offscreen production UI simulation.
3. Windows CI build / packaged native decoder (must reference actual run SHA).
4. Windows physical audible playback (currently BLOCKED).
5. Same-content pinned Android/Windows pixels and dynamic frame acceptance (currently BLOCKED).

Latest Settings/legal batch automated results: **151 tests / 27 suites / 0 failures, errors or skipped**;
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
Jellyfin: `jellyfin-validation.json`, `jellyfin-tests/`, `jellyfin-ui-final/`
(15 production state scenes / 12 assertions / 42 dynamic frames), actual
header-authorized universal-stream native PCM and final Linux package checks.
Previous verified Windows CI: exact Jellyfin commit
`337042a266b07e9c8b8bbe9c4e5740f48d038f29`, run `38224003236`,
**144 tests / 24 suites / zero failures, errors or skipped**, plus source/JAR/
app-image/final ZIP native decode. See `windows-ci-337042a/run.json`.
This is not Windows physical playback or Android pixel acceptance.

Settings/legal: `SETTINGS_LEGAL.md`, `settings-validation.json`, `settings-tests/`,
six `settings-*-ui/` replays (56 state scenes / 81 assertions / 150 frames),
independent `settings-pcm-readback.json` and final `settings-linux-packaged-decoder.json`.
Latest verified Windows CI: exact Settings/legal commit
`2181ba4f57e63ee13d3d16aac2c539bb09afab32`, run `38341304307`, **SUCCESS**,
**151 tests / 27 suites / zero failures, errors or skipped**. Source/JAR/app-image/
final ZIP native decode each pass; raw evidence and artifact metadata are in
`windows-ci-2181ba4/`. Physical sound/real account DPAPI and Android pixels remain blocked.

Each level is reported independently. No `MATCH` entry is introduced solely by compilation, an existing file, or unit-test success.
