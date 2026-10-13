# Search, player and real output — pinned 3f877ea

User-reported anonymous playback repair is tracked in [`PLAYBACK_BUGFIX.md`](PLAYBACK_BUGFIX.md).
Latest fix/package **0cd3883**, Windows run **38383921705 / SUCCESS**:
**174 tests / 33 suites**, real ordinary/unified anonymous song 139774 software
PCM, native Windows EQ and four-layer decoder/resource checks all pass.
[Download fixed Windows package](https://github.com/lladlam/MeloX-Windows/actions/runs/38383921705/artifacts/11805120983).
The old accepted package below predates that repair; its passing decoder tests
did not cover song metadata containing spaces in the production song URI.

**Previous corrected Windows distribution verified:** package commit
`47542d8401ed626e7fff87444499729a0f3c369b`, run **38372169489**, **SUCCESS**,
**166 tests / 31 suites / zero failures, errors or skipped**. Source/JAR/app-image/
final ZIP native decode, EQ PCM and all five pinned-resource byte checks pass.
Downloaded GitHub artifact digest, final package's Windows PE/x64 decoder and
resource bytes independently verify in `evidence/windows-ci-47542d8/`.
Final ZIP: **264,503,115 bytes**, SHA-256
`d79fd2e357efee6bcbc1f26f936afa89346f73e2ac4008c1fd0e316a0c0ba403`.
[Download exact-SHA Windows artifact](https://github.com/lladlam/MeloX-Windows/actions/runs/38372169489/artifacts/11800221356).

Acceptance source: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`, pure export.
The existing player, decoder, Provider bridge, protected accounts, authored lyrics
and persistent data are retained. Overall parity is **PARTIAL**, with no Android
pixel/motion `MATCH` introduced.

## Implementation

- NetEase Songs uses pinned `NeteaseSearchClient.ensureArtwork(searchSongs(query))`.
  The old universal call hid parsing/network errors as empty results; real public
  “周杰伦” results now load. Owned calls cancel actual sockets when replaced.
  Universal pagination uses `/api/search/get`, type 1, tolerant optional metadata
  and real service errors. Native artist/album IDs survive unified results.
- Search Users opens actual profile/rankings/playlists; Podcasts opens genuine
  podcast detail; playlist results use the complete existing detail route.
  Provider backing identities remain intact. History clear, feature/scope gates,
  category cancellation, account revisions, real favorite writes and swipe
  queue/download dispatch are wired. Foreign numeric IDs cannot reach NetEase
  favorite/comments APIs through `SearchSong.neteaseSongId`.
- Classic uses the same functional CoreControls as Apple, with pinned adaptive
  layout and quality/actions on each page. EVA, TextPV and explicit landscape
  Skyline consume production provider-first/AMLL lyrics and real output clock.
  TextPV does not synthesize word timing. All 18 visual specifications match pinned
  source field-for-field; every style has a production Settings/player replay.
- Per-Bilibili-resource offset reaches standard/EVA/TextPV/Skyline and is exposed
  in song actions with pinned ±5000ms/100ms settings. Live Bilibili acceptance is
  outstanding. Settings/player/search retain ordinary Escape focus after conditional
  controls disappear; rejected focus/pointer-target runs remain archived.
- Real user Equalizer reaches single PCM and post-mix dual PCM: stereo S16LE,
  actual input preamp, ten RBJ octave-spaced peaks, independent channel histories,
  final saturation after volume and a 20ms two-bank parameter morph. DSP is
  recreated per output/seek and released. AutoMix bass exchange is gated while
  user EQ is enabled. This desktop implementation is not proof of Android's
  device-specific audiofx hardware response equivalence.
- EQ uses canonical pinned `melox_app_settings` keys, per-key read-only fallback
  to old `melox_playback` values, and canonical reset defaults masking retained
  legacy values. Production UI exposes enable, eight presets, Custom ten bands
  and preamp (±12dB/0.5dB) with actual StateFlow updates.
- Local Provider exposes genuine cached/recognized lyrics. Rescan preserves
  favorite, recognized metadata/artwork and authored cache. Explicit favorite
  writes no longer get overwritten by the previous value.

## Verified evidence

`evidence/search-player-validation.json` records final source/JAR digests and
separate acceptance levels. Final Linux JAR SHA-256:
`038815dd488e74cbb125a2e79e184bb73d59e15be791c64fbce9189ea7b5cd28`.

- **166 tests / 31 suites / zero failures, errors or skipped**. Raw XML in
  `search-player-tests/`: encrypted HTTP/sparse metadata/service errors/owned socket
  cancellation/native identity, ten EQ center gains/stereo isolation/bypass/clipping/
  parameter morph, canonical/legacy/reset, genuine native single/dual output,
  Local lyrics/recognition/favorite persistence.
- **9 production replays / 258 state scenes / 747 dynamic frames / 215 assertions**.
  These are `ImageComposeScene`, not physical desktop windows. Requested/actual
  frame times are retained. Sequential working-tree captures and final source
  digests are labelled separately; older captures are not final-package pixels.
- `search-player-equalizer-readback.json` independently verifies captured native
  PCM in `search-player-native-pcm/`: +6/-6dB single preamp and seek rebuild in both
  AutoMix modes; 1000Hz band attenuation on primary/mixed overlap while 4000Hz
  incoming runs on that same dual output. Retained audio is generated, and the
  controlled software endpoint does not prove physical audibility.
- `search-player-source-decoder.json` and `search-player-linux-packaged-decoder.json`:
  LFS size/hash/magic, native WAV/MP3/FLAC/AAC and required-header HTTP decode pass.
  Final JAR's four legal files remain byte-identical to pinned assets; all **316**
  included source-default strings match pinned `values/strings.xml`.

| Replay | Verified scope | Limitation |
|---|---|---|
| `search-player-eq-ui` | enable → Rock → Custom → band5 +6dB → preamp -6dB → disable → back | UI/DSP evidence are separate |
| `search-player-search-ui` | public songs/scopes, album, actual profile, history clear | authenticated/provider acceptance outstanding |
| `search-player-search-catalog-ui` | actual artist/playlist and back; podcast detail/error | programs returned **-462 login required**, not accepted |
| `search-player-search-play-unavailable-ui` | original online ID, long-press, queue/play-next, quality and genuine error | tested song **185670** has no playable non-preview source; online playback **BLOCKED** |
| `search-player-classic-ui` | Classic selection, real Local transport, artwork/lyrics/queue, quality/actions and close | hardware/reference missing |
| `search-player-lyrics-ui` | public lyric load, EVA/Skyline and exit | online playback unavailable; not clock/audibility evidence |
| `search-player-eva-local-ui` | generated Local output, authored cache, advancing clock, EVA/Skyline and exit | software output only |
| `search-player-textpv-ui` | real authored TTML/output, pause, Skyline Escape, styles/reset | software output only |
| `search-player-textpv-all-ui` | all 18 styles through production Settings/player; authored document retained | Android pixel/motion reference unavailable |

Empty-Songs, pointer-target, focus and compilation failures remain in
`*-rejected-*`, `*-compile-failed`, `*-programs-blocked` and `*-source-blocked`.
The unavailable replay intentionally verifies an error, not successful playback.

## Reproduce

```sh
python tools/generate_pcm_fixtures.py /tmp/opencode/melox-search-player-fixtures
python tools/capture_search_player.py textpv-all /path/to/isolated-onboarded-home
python tools/verify_native_equalizer.py shared/build/parity/test-home/native-pcm-evidence --output /path/to/equalizer-pcm.json
```

Set `MELOX_GRADLE` to Gradle 9.5.1; optional `MELOX_FIXTURES` and
`MELOX_CAPTURE_JVMARGS` select local fixtures/JVM/proxy configuration. Use a
separate home with genuine completed onboarding. Each replay keeps UTF-8
one-argument-per-line `arguments.txt`, command, log, semantics, PNG frames,
assertions and compressed generated PCM. Reveal is actual mouse-wheel input;
click/long-click targets actual production nodes.

## Remaining

Windows feature run **38368910608** at exact **a07e1be97ea166ec886249ceaa307deb6fc87c2d**
completed successfully: **166 tests / 31 suites**, real native EQ PCM, and native
source/JAR/app-image/final ZIP format/authorized HTTP checks. Downloaded artifact
GitHub digest matches; Windows PE/x64 decoder and feature classes are present.
However, independent package inspection **rejects its pinned resource bytes**:
Windows checkout converted the four legal files and default strings to CRLF.
Their normalized text is unchanged. `windows-ci-a07e1be/` preserves raw reports,
independent EQ readback and `rejected-final-zip-pinned-resources.json`.

Follow-up `.gitattributes` forces LF for those resources; the new
`tools/verify_pinned_resources.py` checks five immutable resource digests at
source/JAR/app-image/final ZIP stages. It rejects the downloaded a07e1be package
and passes source/final Linux JAR. The corrected Windows distribution passes
exact-SHA run **38372169489** at **47542d8**, including independent downloaded
resource-byte/PE/EQ checks. The rejected a07e1be report remains historical.
Next player inventory is `NEXT_SEARCH_PLAYER_GAPS.md`.

Exact feature/package-SHA Windows CI is recorded above and separately archived. Physical
Windows audio/SMTC/device switch/sleep/native real-account DPAPI, authorized
Provider reads/writes, tested online-song playback and podcast program reads are
**BLOCKED**. Connected pinned Android reference devices = zero; same-content
pixels/dynamic frames are **BLOCKED**.

Continue full Search shared-element/back animations, Provider catalog/action
details, advanced player/lyric controls, system keep-awake, remaining Settings,
Provider Library/Podcasts, Glass refraction/tone and genuine recognition. The 17
independently inventoried post-pinned commits do not change the acceptance base.
