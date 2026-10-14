# Skyline settings and native Windows keep-awake

Pinned Android remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`, policy
compatibility 20. Pure Git source hashes are in `evidence/skyline-settings-source-audit.json`:
`SettingsScreen.kt:1091–1178` and `MeloXIOSNowPlayingSharedHost.kt:129–145`.
This batch adds the existing pinned **SkylineLyrics settings route** under More.

## Controls and persistence

All **17 canonical Skyline keys** reach existing production consumers:

- Three toggles: enabled, artwork/song info, fullscreen Skyline keep-awake.
- Three choices: 0–4 ambient lines, 1–4 characters/group, 4/8/12/16/20/24 groups.
- Eleven stepped sliders: current font 36–84sp; scale 1–1.2; width .4–.82;
  next font 14–44sp; next opacity .2–.8; spacing 4–36dp; background font 24–72sp;
  background opacity .4–1.8; blur 0–2; tilt 0–20°; drift 0–2.
- Reset restores exactly the source's eleven float keys and two group-limit
  integer keys. It preserves enabled, song info, keep-awake and ambient line
  count. Screen-awake scope is also outside Skyline reset.

31 additional source-default strings match the pure pinned XML values exactly;
the immutable UTF-8/LF resource raises package resource verification to **eight**.
The existing source label says automatic landscape Skyline, but the pinned host
still enters fullscreen Skyline explicitly through its enabled landscape button;
the desktop retains that source behavior.

The source rendering consumers remain in `MeloXAlternativeLyricsPanels.kt`.
Tags identify real song-info/current/next/ambient nodes for geometry assertions;
settings values and lyric/player state are not injected into the production UI.
Existing word-row mask/font bytes, native audio and artwork ownership are retained.

## Real native Windows consumer

The former stored legacy keep-screen toggle is replaced by the pinned four
`player_screen_awake_mode` choices under Appearance: Disabled / Player / Lyrics /
HiddenLyricsInterface. Existing legacy default migration in runtime is retained.
For the iOS shared host, Skyline's keep-awake overrides the scope only while
fullscreen Skyline is visible. Paused playback does not clear a visible-view
scope, matching the source; closing/collapsing the player does.

The real desktop window provides focus/minimize state and one owned
`MeloXDisplayAwakeController`. `SetThreadExecutionState` runs on a dedicated
single native owner thread, setting `ES_CONTINUOUS | ES_DISPLAY_REQUIRED |
ES_SYSTEM_REQUIRED` while required and foreground. Losing focus/minimizing,
leaving a matching page/scope, collapsing the player, turning off the setting or
disposing the window clears the request on that same thread. Shutdown is
idempotent; late child disposal cannot reacquire a released owner. Repeated
stable recompositions do not call the API every frame. Native failures are
logged with active state retained accurately; no successful state is fabricated.
AWT focus/iconify listeners additionally gate requests directly, so a delayed
composition after minimize/focus loss cannot reacquire a background hold.

This Windows implementation holds automatic display/system idle only while the
visible foreground player asks for it. It does not use away mode or override
explicit power-button/lid/sleep actions. Windows screen-saver/lock policy is not
changed. API reference: <https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-setthreadexecutionstate>.

Offscreen production captures have no foreground OS window or native owner;
they cannot establish Windows power acceptance. Linux's native audit writes
**UNSUPPORTED / Windows acceptance BLOCKED**, rather than simulating success.

## Verification

`capture_skyline_settings.py controls|reload|reset|reload-reset HOME OUTPUT`
uses four separate JVMs. Controls change all 17 keys by pointer, exercise all
four awake scopes, and check canonical preferences + runtime. Reset checks all
13 restored keys and all preserved keys. Both modified and reset states are
checked in a new process. `verify_skyline_controls_capture.py ROOT --output …`
rejects missing/truncated captures or missing pointer keys.

`capture_skyline_behavior.py HOME OUTPUT` uses real Tiny Giant provider/FFmpeg
playback: song-info/ambient visibility, font change, enabled entry, source reset,
restored real lyrics and resumed Skyline playback. Personal PCM stays local.

Windows CI enforces the four-process replay plus `auditDisplayAwake`, which
calls the real kernel32 API, verifies returned previous thread flags across
acquire/release/reacquire/disposal, tests idempotence and blocks late reacquire.
An API ownership test still cannot substitute for physical Windows idle/sleep,
focus/minimize or the user's 549→1431MB memory retest.

Linux complete control verification passes **58 control + 3 reload + 6 reset +
3 reset-reload scenes / 342 assertions**. All 17 keys and all four awake scopes
were changed by production pointer input; both persisted states were checked
in new JVMs. Linux regression/package gates pass **206 tests / 40 suites / zero
failures, errors or skipped**, 60 row-mask cases / zero difference, all eight
pinned resources and final-JAR native MP3/FLAC/M4A/authorized-HTTP PCM decode.
Real Tiny Giant replay passes **217 scenes / 162 dynamic frames / 69 assertions**:
song-info and ambient nodes removed/restored, enabled entry hidden/restored,
84sp font changed actual paused line geometry, reset/54sp restored its anchor
with **0px error**; same real lyric text throughout, real FFmpeg non-silent
consumed PCM on two correctly released output endpoints. The replay physically
reveals auto-hidden landscape controls before resuming through Artwork.
Archived captures independently pass both verifiers.

Windows run **38653215039** at exact feature revision
**`f3e3a83d63d0d12c36723aee974ebf3dc2a530d1`** completes successfully. Every step
passes: real native display request ownership, Skyline controls/scope/reset and
new-JVM reloads, the prior full 60-key lyrics replay/reload, native EQ/cache
memory/font/glyph audits and all JAR/app-image/final-ZIP checks. Independent
download/package collection is **PASSED** on 2026-10-15:

- Artifact **`11936083521`**, final ZIP **308,020,756 bytes**, SHA-256
  **`0cc5fb610ed0dea45a323f5763ce05cdf055cbdbec9b3c12295e85a05f5ffe0a`**.
- Exact source revision remains **`f3e3a83d63d0d12c36723aee974ebf3dc2a530d1`**;
  later evidence-only commits do not relabel this distribution.
- Outer GitHub digest, ZIP CRC, launcher PE, FFmpeg PE/hash, Java Sound DLL,
  `java.management`, exact packaged revision, eight pinned resource hashes,
  all eight frozen font instances/metrics and ownership/consumer classes verify.
- Downloaded Windows reports: **206 tests / 40 suites / zero failures, errors
  or skipped**; 60 glyph row-mask cases / zero difference; 120 font cases / zero
  failures; real single/post-seek/dual primary/overlap/incoming EQ PCM passes.
- Independent downloaded Skyline verifier: **17 pointer keys + four awake scope
  choices**, exact 13-key reset, separate-JVM changed/reset persistence, **70
  scenes / 342 assertions**. Prior full lyrics batch: **60 keys / 501 assertions**.
- Real kernel32 samples all share owner thread **14**, with only **four native
  calls**. Previous state flags alternate `0x80000000` / `0x80000003`; repeated
  enable does not make an extra native call, backgrounding clears display/system
  idle holds, delayed UI enable cannot reacquire while backgrounded, foreground
  return reacquires, and disposal clears. Late released-owner requests cannot
  reacquire. This directly verifies native ownership, not physical idle/sleep.
- The uploaded Windows no-forced-GC artwork audit JSON passes and is downloaded
  directly; no log reconstruction is used in this run.

Download: <https://github.com/lladlam/MeloX-Windows/actions/runs/38653215039/artifacts/11936083521>.
Evidence: `evidence/skyline-settings-power/windows-ci-f3e3a83/`, including native
samples, independent reports, raw capture JSON, selected screenshots and
LF-normalized argument/log archives with original hash provenance. The verified
temporary distribution ZIP was deleted after successful collection.

Android same-content
pixel/motion and physical Windows power acceptance remain **BLOCKED**.

The independently verified preceding advanced-lyrics distribution is exact
`d186e0c62941d2726c2184b8399d97c861af4195`; Skyline/native power are included only
in the new `f3e3a83` package. The existing accepted font repair, pause-safe PCM
writer, shared artwork ownership/budget and memory diagnostics are retained.
The user's full-process 549→1431MB report remains unresolved pending their exact
Windows retest; native cache stress does not establish whole-player convergence.
