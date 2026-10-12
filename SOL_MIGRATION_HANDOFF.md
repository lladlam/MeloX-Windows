# Sol third-round implementation handoff

Pinned Android: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.
Starting Windows: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.
Follow-up upstream fetch (2026-10-12): `febb4c0534787e040a89df8457e630d067f44b80`, 9 further commits / 154 changed files; closing fetch adds 3 commits / 28 files at `f14a1c1f5400664d07575a113469f41ee3b70572`. Both `evidence/upstream-intake-*.json` inventories use pure commit exports. AutoSync/model alignment, Smart Queue quota and signed hot-update text/scripts/colors/rules plus APK deltas remain unimplemented increments; the pinned matrix is not implicitly upgraded.

## Landed in this batch

- Fault-tolerant DPAPI preference open/recovery, ciphertext quarantine, verified rebuild and memory fallback; YouTube uses recovered session abstraction.
- Nullable album/artist IDs in song model, real Netease source parsers, shared bridge, queue/cache/download records, and selected playback metadata.
- Explicit queue clearing and late-callback isolation regression.
- Real artist-detail UI/API path, album classification/grid, info/follow, nested album and source-sized back transform; global collection routes and song-action identity navigation.
- Retained search-launch bus, provider-aware fallback queries and production search wiring.
- Persistent playlist artwork and pull-reveal search settings; actual playlist filter/actions, subscription/download dispatch.
- LFS-aware CI and source/JAR/app-image/final ZIP decoder integrity/native checks; generated format/authorized HTTP fixtures; physical Windows acceptance runner.
- Real signed PCM / FFT / onset / tempo / audible-boundary / beat / phrase analysis and native FFmpeg session; BPM/silence/band/real-decode tests.
- Production continuous primary/incoming FFmpeg PCM mixing on one output; bounded native preload, rendered-clock promotion/drain, pitch-preserving tempo ramp, source bass exchange via real RBJ low shelf, software volume/mute and full original lifecycle regressions through the streaming path.
- Controller scored/fallback planning, real Provider/download resolution, real analyzed Smart Queue, transition runtime, persistent source settings and queue AutoMix button; paired-cover split and handoff. Fixed the full-cover shadow artifact found in the actual screenshot replay.
- Real Local provider readable-file playback/indexed search, encoded URI/metadata and identity-preserving provider bridge; no Local masquerading as online music.
- Cloud upload desktop picker, genuine FFmpeg metadata/MD5, source upload-check/token/LBS/NOS/info/pub protocol, successful refresh, actual byte progress and cancellation/snapshot cleanup. Six encrypted-HTTP/real-file protocol integration tests; live account/Windows chooser acceptance still blocked.
- Content layout: dedicated wired TabLayout settings route, dock/home reorder and placement controls, existing-key per-provider Library page persistence/default/visibility, and home module shortcut reachability independent of dock placement. Launch/full-swipe settings exposed; Messages deep-link misinformation removed. See `parity/3f877ea/CONTENT_LAYOUT.md`.
- Smart Queue regression discovered during this batch: disabling now restores analysis-failed unconsumed original tracks too; selection and disable queue mutation are serialized. A direct failed-analysis regression protects this alongside real PCM candidate selection.
- Remote compatibility: first-frame runtime startup, minimized-window periodic gating/resume refresh, real Settings route/consent/withdrawal/cache clear, DNSPod DoH, bounded owned HTTP, atomic cache and anti-rollback retention/invalid-ETag repair. Actual subsequent login/playback/fallback gates and once/daily notices are wired; local fallback opt-in stays independent. See `parity/3f877ea/REMOTE_CONFIG.md`.
- Glass Toggle now renders confirmed controlled state after a pending confirmation/rejected change, rather than leaving a green resting knob after rejection. Drag feedback remains transient and theme tone is observed.
- Unified song Search now consumes the existing global opt-in/approved source set, observes Settings changes, performs actual concurrent provider aggregation with inline partial failures and plays original resource IDs. Obsolete-query cancellation, duplicate/foreign identities and single-source whitelist semantics are corrected. See `UNIFIED_SEARCH.md`; complete provider/account/action/catalog/pixel acceptance remains partial.
- Bounded incoming PCM scheduling recovery: the failed Unified Windows run's immediate-poll fallback is fixed with a monotonic 100ms total mixed-block wait budget. Exact `50bd383` Windows run `38222348899` passes 130 tests and native final-package checks; the failed run stays archived. See `PCM_PRELOAD_RECOVERY.md`.
- Jellyfin now uses actual entered-server desktop authentication, stable protected-session AES key, user-scoped catalogs/Library and native favorite/playlist writes. Original server/item/duplicate membership IDs survive queues and resolver URIs. Header-auth universal playback/artwork remain token-free in persisted models; account-specific resolver/image caches and revision-driven Library refresh prevent stale account reuse. Unknown stream quality remains visibly server-selected. See `JELLYFIN.md` for protocol/file/native-PCM evidence and live-server limitations.

## Still required

1. AutoMix streaming-provider account acceptance and remaining settings/candidate/transport edge cases; physical Windows audible transitions and Android dynamic cover parity. Production dual PCM/tempo/EQ/Smart Queue/UI are now implemented and tested, but overall AutoMix remains **PARTIAL**.
2. Complete behavioral upstream-delta acceptance, including Library's complete source layout and App Shell combined backdrop/shared transitions.
3. Windows account/native DPAPI, physical speaker/device-switch/sleep, continuous audio and dual-track transitions. Current environment is Linux; do not convert controlled SourceDataLine tests into physical acceptance.
4. Current pinned Android reference screenshots/frames. Device enumeration returned zero. Artist/album and remaining pages still need same-content comparison; historical Settings SSIM is not a new complete parity result.
5. Remaining settings routes, Unified Search authenticated-provider/catalog/download/alternate-candidate acceptance, live Cloud account/chooser acceptance, podcasts/library Provider acceptance, Classic/alternate lyric shell/settings and refraction/dynamic-tone verification.
6. Desktop SongRecognitionClient is an existing empty-result stub; the new Android ID fields do not solve its missing native recognition path.
7. Independently migrate and accept the newly fetched `3f877ea..febb4c0` increment after completing the pinned batch. Preserve actual models/providers and signed-config scope; do not label pre-AutoSync frames/tests as new upstream parity.
8. Jellyfin real user-server acceptance: authenticate and reopen the exact Windows distribution, read/search real Library, inspect authenticated cover, play native universal audio, favorite and create/rename/add/duplicate-remove/move on an authorized server. Client protocol fixtures and Linux protected-file tests do not prove native Windows DPAPI, server-version compatibility or audible output. Old unpersisted-random-key ciphertext cannot be recovered; keep it until successful genuine re-login.

## Execute physical Windows acceptance

Download the CI artifact for the actual new commit; extract its app-image.

```powershell
pwsh tools/windows_audio_acceptance.ps1 -AppImage C:\MeloX\MeloX-Desktop -EvidenceDirectory C:\MeloX-evidence
```

The runner verifies final packaged FFmpeg, enumerates Windows sound devices, opens the real launcher and records explicit PASS/FAIL/BLOCKED/NOT_RUN observations. Supply generated/public fixtures and a real authorized account locally. Keep personal audio and credentials out of public evidence. Publish only sanitized logs and synthetic loopback metrics.

Detailed observable-state matrix and evidence: `parity/3f877ea/`.

## Verified batch evidence

- `parity/3f877ea/evidence/upstream-validation.json`: 81 automated tests / 14 suites, zero failures/errors/skipped, source digests and final Linux uber-JAR SHA-256.
- `parity/3f877ea/evidence/artist-live-v7/`: 7 production UI scenes / 8 physical-input assertions; real artist and album API content, info/album return and preserved artist scroll.
- `source-decoder.json`, `linux-packaged-decoder.json`: source resources and final JAR native WAV/MP3/FLAC/AAC/required-header HTTP decode.
- Initial error-only/tag-only artist capture was rejected. Actual screenshots found duplicate `followed` JSON keys and modal Escape focus failure; both were repaired and replayed.
- `evidence/windows-ci-9328f78/`: exact run 38019093278 passed 81 tests and native source/JAR/app-image/final ZIP decoder checks. That run predates production AutoMix changes.
- `evidence/windows-ci-3ac97e0/`: production AutoMix run 38025529598 passed **102 tests / 18 suites** and native source/JAR/app-image/final ZIP verification; downloadable Windows artifact is attached to that exact run.
- `evidence/windows-ci-d1426b5/`: Cloud run 38034442939 passed **108 tests / 19 suites** plus native source/JAR/app-image/final ZIP checks. Exact-SHA downloadable artifact is attached to that run.
- `evidence/windows-ci-693f3d2/`: content-layout run 38197452357 passed **114 tests / 20 suites** plus native source/JAR/app-image/final ZIP checks. Exact-SHA downloadable `MeloX-Desktop-Windows` artifact is attached to that run.
- `evidence/windows-ci-5f2d149/`: remote compatibility run 38200697967 passed **124 tests / 21 suites** and native source/JAR/app-image/final ZIP checks; downloadable exact-SHA Windows artifact is attached to that run.
- `evidence/windows-ci-50bd383/`: incoming PCM recovery run 38222348899 passed **130 tests / 22 suites** and native source/JAR/app-image/final ZIP checks. Unified run 38202345808 at 275032b failed the old immediate-poll two-deck test; its failure is retained in `windows-ci-275032b-failed/`, not labelled a success.
- `evidence/automix-validation.json` and `automix-tests/`: latest 102 tests / 18 suites, zero failures/errors/skipped; source and Linux uber JAR hashes.
- `evidence/automix-ui-v3/`: 7 production UI scenes / 8 state assertions plus captured frames and compressed generated PCM. Explicit software endpoint label; requested/actual capture timing is recorded separately.
- `evidence/automix-pcm-readback.json`: independent consumed-PCM readback finds primary-only, actual 1000/4000Hz simultaneous overlap and incoming-only on one output; `automix-linux-packaged-decoder.json` verifies latest packaged decoder.
- `evidence/cloud-validation.json`, `cloud-tests/`: **108 tests / 19 suites**, zero failures/errors/skipped, including six encrypted-HTTP/real-file upload cases. `cloud-ui-v3/` has 3 genuine unauthenticated UI scenes / 7 refresh/retry/entry assertions; `cloud-linux-packaged-decoder.json` verifies its final Linux JAR.
- `evidence/content-layout-validation.json`, `content-layout-tests/`: **114 tests / 20 suites**, zero failures/errors/skipped. `content-layout-ui-final/` captures 15 genuine settings state scenes / 14 assertions plus 42 dynamic frames; startup reuses the earlier UI-written isolated preferences. Packaged Linux decoder checks also pass. These frames are not pinned Android dynamic parity.
- `evidence/content-layout-home-shortcut/`: 4 genuine production scenes / 7 assertions for Home Cloud entry with its dock placement disabled and Escape return. This replay verifies the App Shell root-switch focus fix; earlier settings source hashes and final artifact source hashes are kept separate.
- `evidence/remote-config-tests/`: **124 tests / 21 suites**, zero failures/errors/skipped, including ten native P-256/actual HTTP/cache/cancellation/policy/foreground/login-breaker regressions. No production signing key, URL or account transport is replaced by test fixtures.
- `evidence/remote-config-ui-dark/`: 20 production state scenes / 25 assertions / 57 dynamic frames. Both actual knob progress and controlled switch state are verified after consent rejection, fallback cancellation, acceptance and withdrawal. The final Linux uber JAR and its decoder pass native format/authorized HTTP verification; SHA/source hashes are in `remote-config-validation.json`.
- `evidence/remote-config-ui-light-live/`: 15 production scenes / 14 assertions; actual public signed config **v6** through GhFast accepted by the production pinned key. Once notice, manual refresh, clear, withdrawal and Escape return pass with no signed-in account. Public-server 304 and the other network routes are not inferred from this replay.
- `evidence/unified-search-tests/`: **130 tests / 22 suites**, zero failures/errors/skipped, including six concurrency/timeout/cancellation/native-identity/capability regressions. Local UI: 6 scenes / 11 assertions / 15 frames and independently verified native 1000Hz PCM. Services scope UI: 13 scenes / 13 assertions. Final Linux package native decode passes; source/JAR/generated-fixture hashes are in `unified-search-validation.json`.
- `evidence/jellyfin-tests/`: **144 tests / 24 suites**, zero failures/errors/skipped. Ten actual-HTTP/native authorized-PCM/provider-resolver cases, three protected-file/key regressions and duplicate membership queue persistence are added. `jellyfin-ui-final/`: 15 production state scenes / 12 assertions / 42 frames for missing Library account, real editable password-masked login, URL/TCP errors and close/reopen. Final Linux JAR and LFS/native format/header decode verified; hashes and separate live-server/DPAPI/pixel limitations in `jellyfin-validation.json`.
- Physical Windows audio and current pinned Android pixel/motion capture remain BLOCKED. See `parity/3f877ea/AUTOMIX.md` for implementation and reproducible generated-source replay.
