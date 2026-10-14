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

Fresh Windows native/package collection is pending. Android same-content
pixel/motion and physical Windows power acceptance remain **BLOCKED**.

The independently verified preceding advanced-lyrics distribution is exact
`d186e0c62941d2726c2184b8399d97c861af4195`; this unverified working batch is not
included in that package.
