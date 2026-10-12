# Sol third-round implementation handoff

Pinned Android: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.
Starting Windows: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.

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

## Still required

1. AutoMix streaming-provider account acceptance and remaining settings/candidate/transport edge cases; physical Windows audible transitions and Android dynamic cover parity. Production dual PCM/tempo/EQ/Smart Queue/UI are now implemented and tested, but overall AutoMix remains **PARTIAL**.
2. Complete behavioral upstream-delta acceptance, including Library's complete source layout and App Shell combined backdrop/shared transitions.
3. Windows account/native DPAPI, physical speaker/device-switch/sleep, continuous audio and dual-track transitions. Current environment is Linux; do not convert controlled SourceDataLine tests into physical acceptance.
4. Current pinned Android reference screenshots/frames. Device enumeration returned zero. Artist/album and remaining pages still need same-content comparison; historical Settings SSIM is not a new complete parity result.
5. Remaining settings routes, Unified Search aggregation, live Cloud account/chooser acceptance, podcasts/library Provider acceptance, Classic/alternate lyric shell/settings and refraction/dynamic-tone verification.
6. Desktop SongRecognitionClient is an existing empty-result stub; the new Android ID fields do not solve its missing native recognition path.

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
- `evidence/automix-validation.json` and `automix-tests/`: latest 102 tests / 18 suites, zero failures/errors/skipped; source and Linux uber JAR hashes.
- `evidence/automix-ui-v3/`: 7 production UI scenes / 8 state assertions plus captured frames and compressed generated PCM. Explicit software endpoint label; requested/actual capture timing is recorded separately.
- `evidence/automix-pcm-readback.json`: independent consumed-PCM readback finds primary-only, actual 1000/4000Hz simultaneous overlap and incoming-only on one output; `automix-linux-packaged-decoder.json` verifies latest packaged decoder.
- `evidence/cloud-validation.json`, `cloud-tests/`: **108 tests / 19 suites**, zero failures/errors/skipped, including six encrypted-HTTP/real-file upload cases. `cloud-ui-v3/` has 3 genuine unauthenticated UI scenes / 7 refresh/retry/entry assertions; `cloud-linux-packaged-decoder.json` verifies its final Linux JAR.
- `evidence/content-layout-validation.json`, `content-layout-tests/`: **114 tests / 20 suites**, zero failures/errors/skipped. `content-layout-ui-final/` captures 15 genuine settings state scenes / 14 assertions plus 42 dynamic frames; startup reuses the earlier UI-written isolated preferences. Packaged Linux decoder checks also pass. These frames are not pinned Android dynamic parity.
- `evidence/content-layout-home-shortcut/`: 4 genuine production scenes / 7 assertions for Home Cloud entry with its dock placement disabled and Escape return. This replay verifies the App Shell root-switch focus fix; earlier settings source hashes and final artifact source hashes are kept separate.
- Physical Windows audio and current pinned Android pixel/motion capture remain BLOCKED. See `parity/3f877ea/AUTOMIX.md` for implementation and reproducible generated-source replay.
