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

All four Gradle replay invocations exited successfully. v7/v8 predate the native
glyph/cascade renderer; landscape-v1 and v12 include it. Neither filename identity nor
passing interaction assertions establishes Android pixel or audible-output
equivalence. No pinned Android player screenshot is available. The recorded
audio-output/resolution errors and `is_playing=false` are genuine.

Account/preference files and signed stream URLs are not part of these snapshots.
For source constants, open issues and reference-image alignment, see
[`../UI_MOTION.md`](../UI_MOTION.md).
