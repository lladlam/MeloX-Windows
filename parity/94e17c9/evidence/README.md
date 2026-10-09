# Production replay evidence

These files are copied from completed production `ImageComposeScene` replays.
Each directory contains screenshots, unmerged semantics and the original
`capture.json` with real provider IDs, positions, physical-input steps, timing
measurements and assertion results.

| Run | Viewport / density | Scenes | Passed assertions | Scope |
| --- | --- | ---: | ---: | --- |
| `player-live-v7` | 427×924 / 1 | 21 | 28 | Player/share/queue/quality/settings/Back/Mini chain |
| `player-live-v8` | 427×924 / 1 | 12 | 11 | Lyric seek/browsing/auto-hide/drag collapse/reopen |
| `player-landscape-v1` | 924×427 / 1 | 11 | 13 | Landscape player/lyrics/queue/selection/Back/reopen |
| `player-live-v12` | 427×924 / 1 | 13 | 15 | Authored-word renderer/seek/restored focus anchor/slider/collapse |
| `player-live-v13` | 427×924 / 1 | 22 | 33 | Output-error ownership/full lyrics/share/queue/quality/settings/Back/Mini chain |

All five Gradle replay invocations exited successfully. v7/v8 predate the native
glyph/cascade renderer; landscape-v1 and v12 include it. Neither filename identity nor
passing interaction assertions establishes Android pixel or audible-output
equivalence. No pinned Android player screenshot is available. The recorded
audio-output/resolution errors and `is_playing=false` are genuine.

v13 also records `error_kind` and `play_when_ready`: the physical device's genuine
Output failure leaves queue index 0 selected and playback intent false. A manual
queue selection and quality change then retain the selected identity/page.

`audio-lifecycle-v3/test-results.json` records the final 70-test, 12-suite shared
regression pass, case names, actual suite/case durations and source SHA-256 hashes.
Its seven audio-session tests run the actual shipped FFmpeg and PCM loop with
controlled physical-device endpoints. This includes draining the tail, zero-origin
seek, successful known-end seek, nonzero decoder exits after PCM, same-URI request
ownership and two device-error cases. The source files can be checked against the
stored hashes to identify the tested backend version.

Account/preference files and signed stream URLs are not part of these snapshots.
For source constants, open issues and reference-image alignment, see
[`../UI_MOTION.md`](../UI_MOTION.md).
