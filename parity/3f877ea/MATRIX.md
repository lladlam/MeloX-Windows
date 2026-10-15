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
| Song-recognition identity additions | `core/recognition/SongRecognitionClient.kt` | Real Java Sound/FFmpeg PCM, byte-identical pinned AFP JS/WASM via isolated V8 and actual NetEase match protocol; Local three-segment acoustic recognition | COMPLETION_LOG.md; real WASM signal/isolate and FFmpeg→match protocol tests pass. Live hit/Windows microphone not yet accepted | PARTIAL |
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
| All 60 canonical advanced lyrics settings and visible renderer consumers | Pinned `SettingsScreen.kt:1700–1897`, lyrics preferences/runtime and iOS panel | Production AdvancedLyricsSettings route, exact strings/choices and existing-key persistence; translation scope now consumes its real setting | ADVANCED_LYRICS_SETTINGS.md; Linux/Windows 60 pointer keys / separate-JVM reload / 501 assertions and real Tiny Giant 173 scenes / 129 frames / 55 assertions. Windows `d186e0c` run 38592934356 passes all gates and independent package/PCM verification. Android pixels remain blocked | PARTIAL |
| Skyline 17 settings, source reset and native keep-awake scopes | Pinned `SettingsScreen.kt:1091–1178`, `MeloXIOSNowPlayingSharedHost.kt:129–145` | Existing real renderer consumers; 13-key reset with preserved flags/count; four awake scopes; dedicated kernel32 owner thread and foreground/iconify/disposal release | SKYLINE_SETTINGS_POWER.md; Linux/Windows 70 control scenes / 342 assertions across four JVMs, real Tiny Giant 217 scenes / 162 frames / 69 assertions and 0px geometry restore. Windows run 38653215039 at f3e3a83 and independent native/package collection pass; physical power/Android pixels remain blocked | PARTIAL |
| Complete song action sheet, native favorite state and local playlist writes | `ui/player/MeloXSongActionsOverlay.kt`, native providers | Source modal/pages/SF actions, actual download/playlist/comment/social/wiki/rank clients, Local persisted writes, state-read capability; unavailable origins disabled | COMPLETION_LOG.md; 68 physical-input/reload scenes / 63 assertions, full lifecycle exit and independent generated PCM readback pass; live authenticated actions and pinned frames pending | PARTIAL |
| Background sleep timer and ListenTogether | Pinned playback coordinator/player/settings | Persistent process-owned deadline, actual pause; real room/status/command/playlist/heartbeat protocol and queue synchronization with echo/account guards | COMPLETION_LOG.md; state-machine tests and timer set/persist/restart/cancel + missing-account Together physical replay pass; live two-account sync outstanding | PARTIAL |
| General system font consumer | `ui/theme/MeloXMiSansFont.kt` | Same `system_font` chooses Default vs pinned MiLan in Material and glass typography; enabled setting | COMPLETION_LOG.md; physical change/actual typography consumer, independent reload and reset pass | PARTIAL |
| Native multi-provider catalogs (Kuwo / Bilibili / Apple Music) | `core/provider/kuwo/*`, `bilibili/*`, `applemusic/*` | Kuwo search/playback/lyrics/playlists; Bilibili WBI search + favourite folders + DASH auth playback; Apple official REST catalog/library/lyrics with validated tokens; Apple declares no Playback capability | COMPLETION_LOG.md; 9 new protocol regressions plus full 230-test suite pass. Live authorized accounts, real music playback and pinned frames pending | PARTIAL |

## Evidence levels

Latest independently verified Windows distribution:
**d4fb9de70acc5849b7220de157cd8084eb965b30**, run **38721634269 / SUCCESS**,
artifacts **11967094846 / 11967064773**. **230 tests / 47 suites / zero failures,
errors or skipped** (aggregated from the downloaded verification artifact's XML by
`tools/record_ci_evidence.py`). Delivery ZIP **320,734,877 bytes**, SHA-256
`3499f59b2dfcdd9d0a6f982093e378a1382214b52209fd20925b0f2646e3096a`.
Independently re-downloaded and verified with `tools/verify_delivery_artifact.py`:
357 ZIP entries CRC-pass, stamped revision equals the run head SHA, pinned resources
and packaged native decoder bytes/PE magic pass. Compared with the previous verified
delivery, this one additionally removes the dead `MeloXMediaPrefetcher` that returned
fabricated empty audio files. Evidence: `evidence/windows-ci-d4fb9de/`.
Physical Windows execution/audible/DPAPI/device behaviour, whole-process user-memory
plateau and pinned Android pixel/motion acceptance remain pending/blocked.

Previously latest independently verified Windows distribution:
**a0c8b79a59a54c5e963a0b8c15496ac7f6de9ce0**, run **38716111866 / SUCCESS**,
artifacts **11963836414 / 11964570793**. **230 tests / 47 suites / zero failures,
errors or skipped** (aggregated from the downloaded verification artifact's XML).
Delivery ZIP **320,739,222 bytes**, SHA-256
`23774c898aad1f761e1eb413bad41764c8d1e21e6ab07a2a781e470ee5651d02`.
Independently re-downloaded and verified with `tools/verify_delivery_artifact.py`:
357 ZIP entries CRC-pass, stamped revision equals the run head SHA, pinned resources
and packaged native decoder bytes/PE magic pass. This distribution adds the real
song-action sheet with native favorite state and Local playlist writes, background
sleep timer, process-level ListenTogether protocol runtime, real acoustic recognition
(pinned AFP JS/WASM over microphone/file PCM), native Kuwo/Bilibili/Apple catalogs,
system-font consumer and a real local-segment decoder. See `COMPLETION_LOG.md`.
Physical Windows execution/audible/DPAPI/device behaviour, whole-process user-memory
plateau and pinned Android pixel/motion acceptance remain pending/blocked.

Earlier independently verified Windows distribution:
**f3e3a83d63d0d12c36723aee974ebf3dc2a530d1**, run **38653215039 / SUCCESS**,
artifact **11936083521**. **206 tests / 40 suites / zero failures**, all 17 Skyline
keys + four awake scopes/source reset / 342 assertions, prior 60 lyrics keys /
501 assertions, real Windows power-request ownership, eight pinned resources,
eight fonts, glyph/font/EQ/cache-memory audits and all packaging layers pass.
Final ZIP **308,020,756 bytes**, SHA-256
`0cc5fb610ed0dea45a323f5763ce05cdf055cbdbec9b3c12295e85a05f5ffe0a`.
See `SKYLINE_SETTINGS_POWER.md`. Physical user-memory/power/accounts/audio and
pinned Android pixel/motion acceptance remain pending/blocked.

Preceding independently verified Windows distribution:
**d186e0c62941d2726c2184b8399d97c861af4195**, run **38592934356 / SUCCESS**,
artifact **11909196579**. **204 tests / 39 suites / zero failures**, 60-key mouse
replay + separate-JVM reload / 501 assertions; seven pinned resources, eight
axis-frozen fonts, 60 row-mask and 120 font cases, real EQ PCM and no-forced-GC
2GiB image stress pass. Final ZIP **307,984,559 bytes**, SHA-256
`a97b4b0d89861634b12a35ea38be2b1277134a06cba1a8f59d6d9cb7b8bc8f37`.
See `ADVANCED_LYRICS_SETTINGS.md`. Package verification is not user-machine
memory/audio/DPAPI or pinned Android pixel/motion acceptance.

Historical verified Windows package: **47542d8401ed626e7fff87444499729a0f3c369b**,
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
Their overall parity remains **PARTIAL**; the later advanced-lyrics batch now
passes 60-key input/persistence and selected real rendering consumers, while
additional player/Skyline settings, full search animations and provider-account
acceptance remain outstanding. Tested
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
