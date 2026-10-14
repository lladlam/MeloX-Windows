# Advanced lyrics settings migration

Acceptance source remains Android `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`,
`SettingsScreen.kt:1700–1897`. Its 60 canonical keys had only nine desktop UI
exposures in the previous inspected inventory. This batch exposes all source
groups and options through the production lyrics settings route.

## Scope

- Quality/style, automatic selection, word highlighting, ordinary LRC pseudo
  timing, seek/share, interlude countdown, follow delay and reduced motion.
- Translation/romanization visibility, size, brightness and focused/all-line scope.
- Lyric lead time, word lead switch, refresh cap, font size/weight and lift mode.
- Long-tone detection/glow/strength/threshold, line spacing and depth blur.
- Focus scale, inactive brightness, controls auto-hide and scroll-hide threshold.
- Position/scale springs, duration, lead, elasticity and gradients.
- Highlight width/falloff; focus position, distance/hidden blur, dim amount;
  trail timing, catch-up/settle and snap threshold.
- The existing eighteen Text PV styles, intensity/speed and reset remain
  available conditionally without a duplicate top-level style selector.

Canonical setters/persistence and existing real player/render consumers are
retained. `lyrics_translation_display_mode` now actually controls annotation
visibility; hidden focused-only annotations reserve their row slot. The shared
visibility rule also covers the existing romanization scope. Pinned MiLan bytes,
axis-frozen font metrics, row-mask compositor, PCM writer and resource lease
fixes are retained.

## Source provenance and default discrepancy

The 89 additional default strings are exact values from the pure pinned Git
XML blob, hash checked by `lyrics-settings-source-audit.json`. Their properties
file is UTF-8 with LF pinned by `.gitattributes`. Source resource verification
now includes seven immutable resources.

The pinned Android UI passes `true` as the absent-preference pseudo-timing
toggle default, but the pinned Android **runtime initializes/reloads it as
false**. Desktop retains the actual existing runtime default (false) and shows
that real value; it does not show a true switch backed by false behavior or
silently change existing playback defaults. This source discrepancy is not
declared an Android pixel MATCH.

## Pointer replay follow-ups

- A fresh onboarded capture home still has remote-config consent. The replay
  rejects the real modal by mouse input before touching underlying settings.
- Pointer down/up occur on separate frames; animated menu targets must settle
  before their coordinates are used. Semantics identify geometry/assert state;
  click/toggle/slider values are not injected through semantic actions/setters.
- Slider endpoints stay inside the exclusive hit-test right edge, permitting
  exact stepped minima/maxima without out-of-bounds presses.
- The complete page exceeds the old 80-wheel-event reveal budget. Returning to
  the style selector from its final slider needs a longer bound. Small wheel
  movements and a settled-visibility confirmation avoid fling overshoot; the
  footer range still includes the last slider at the real scroll limit.
- The first style row centers at 112dp, below a 74dp header. The old 18%-of-height
  top guard rejected it even at the top scroll limit (140px guard on a 780px
  capture). A density-based header guard permits the actual first row.
- Style selection is tagged by `lyrics_style`, like the other canonical rows,
  rather than tying the replay to an English title.

`tools/capture_lyrics_settings.py controls HOME OUTPUT` uses a fresh isolated
home and changes all 60 keys through production mouse/wheel input. It asserts
both real preferences and current runtime values, then reloads genuine saved
preferences. `reload` runs in a **separate JVM** against the same home and
checks all values again. `tools/capture_lyrics_behavior.py` changes translation
scope/font/motion via settings and returns to real Tiny Giant provider/FFmpeg
playback; software PCM is explicitly labelled and recording stays local.

Linux production verification is now complete for all controls: **60 keys / 151
control scenes**, **two separate-process reload scenes**, **501 passing
assertions**. The evidence verifier rejects the prior truncated replay rather
than accepting a partial key set.

Real Tiny Giant verification passes **173 scenes / 129 dynamic frames / 55
assertions**: translation off→zero rows, focused→one row, all→multiple rows;
Regular / 112% font settings and reduced motion selected by pointer; real
FFmpeg non-silent consumed PCM; actual controls auto-hide and pointer reveal;
subsequent pause; paused lyric anchor geometry. The mini-player overlap at
175% density is handled by physically revealing the root settings row first;
the replay waits for genuine auto-hide before clicking its reveal overlay.

Reports and selected screenshots are under `evidence/advanced-lyrics-settings/`.
Source hashes and all 89 strings are independently checked against pure pinned
Git blobs; all 60 keys have production consumer references (source evidence,
not a behavioral equivalence claim for every visual equation).

Linux regression/package gates pass: **203 tests / 39 suites / zero failures,
errors or skipped**, **60 row-mask cases / zero difference**, seven exact pinned
resource hashes and native final-JAR FFmpeg MP3/FLAC/M4A plus authorized HTTP
PCM decode. The accepted axis-frozen font bytes and pause-safe PCM writer are
retained. Windows now runs the full controls + new-process reload verifier as
an enforced workflow gate before packaging. Independent Windows delivery is
pending until the exact feature revision passes and is independently collected.
There is no pinned Android same-content image/motion capture available; these
checks do not establish Android MATCH or user-machine Windows acceptance.

The unresolved 549→1431 MB report remains tracked in `WINDOWS_MEMORY.md`.
The two original advanced-lyrics stashes are retained as recovery checkpoints.

## Windows regression interruption

Feature run `38587692142` at `9e1fe56a76b23fa9322960bf8c8afbda3e51d623`
**failed**: 203 tests / 39 suites / one AutoMix integration failure. Lyrics
pointer gate was not reached and no distribution was published. The downloaded
test XML locates the failure at incoming heard-position assertion (line 193);
the coroutine method's line 186 in the step log is not the failing assertion.

`next()` previously selected/prepared the incoming track with zero position,
then issued a second seek to its already-heard overlap position. Resolution
could start inline or on another thread before that seek, opening a zero-start
decoder and racing the new seek. A deterministic immediate fixture resolver
with real bundled FFmpeg/PCM reproduces **expected 1200ms / actual 0ms start**.
The fix passes the heard position into selection before preparation, with a
single controller start. The new regression also checks previous returns to
primary at zero. AutoMix cancellation may reopen an outgoing endpoint before
selection closes it; endpoint count is not used as a proxy for the incoming
start position. The failed run and before-fix
reproduction remain under `evidence/advanced-lyrics-settings/windows-failed-9e1fe56/`.

After the position-before-prepare fix, Linux passes **204 tests / 39 suites /
zero failures, errors or skipped**, including the original seven AutoMix
integration scenarios and the deterministic inline-resolution regression.
Fresh Windows CI/package collection is required for this follow-up revision.
