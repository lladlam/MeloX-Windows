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

## Still required

1. Production primary/incoming PCM engine, tempo ramps, real EQ envelope, Smart Queue and actual UI handoff. Analyzer completion is **not** AutoMix completion.
2. Complete behavioral upstream-delta acceptance, including Library's complete source layout and App Shell combined backdrop/shared transitions.
3. Windows account/native DPAPI, physical speaker/device-switch/sleep, continuous audio and dual-track transitions. Current environment is Linux; do not convert controlled SourceDataLine tests into physical acceptance.
4. Current pinned Android reference screenshots/frames. Device enumeration returned zero. Artist/album and remaining pages still need same-content comparison; historical Settings SSIM is not a new complete parity result.
5. Remaining settings routes, Unified Search aggregation, Cloud upload, podcasts/library Provider acceptance, Classic/alternate lyric shell/settings and refraction/dynamic-tone verification.
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
- Windows CI evidence will be attached to its exact pushed commit/run; physical Windows audio and current pinned Android pixel capture remain BLOCKED.
