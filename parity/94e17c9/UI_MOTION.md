# UI geometry and motion evidence

Source of truth: Android `94e17c920bf554966584955afa2bd2ee92580a8b`.
All rows remain **PARTIAL** until same-content visual and interaction acceptance.

| Android source / parameter | Desktop source | Synced value | Status |
| --- | --- | --- | --- |
| `MeloXApp.kt` ChromeExpandedSize / ChromeCompactSize | `foundation/MeloXChromeTokens.kt` | 57 / 46 dp | PARTIAL |
| ChromeCompactWidth / horizontal margin / search gap | `foundation/MeloXChromeTokens.kt` | 48 / 12 / 7 dp | PARTIAL |
| MiniPlayer height / artwork | `foundation/MeloXChromeTokens.kt` | 48 dp / 40→30 dp, radius 6 dp | PARTIAL |
| MiniPlayer controls / glyph | `screens/MeloXMiniPlayer.kt` | 30→36 dp / 18→22 dp, smoothStep(.20,.80) | PARTIAL |
| MiniPlayer title / artist / line height | `screens/MeloXMiniPlayer.kt` | 14 / 12 / 15 dp converted to sp | PARTIAL |
| MiniPlayer surface / content fade | `screens/MeloXMiniPlayer.kt` | smoothStep(.02,.48) / (.10,.58), expansion timeline | PARTIAL |
| MiniPlayer shared paused-artwork scale | `screens/MeloXMiniPlayer.kt` | 1→.74, smoothStep(.30,.88) | PARTIAL |
| `MeloXMotion.kt` bottom expand / collapse | `foundation/MeloXMotion.kt` | damping .755 / .70; stiffness 250; threshold .001 | PARTIAL |
| `MeloXMotion.kt` signed spring fraction / left share | `foundation/MeloXMotion.kt` / ChromeTokens | clamp [-.09,1.09] / 1:2 | PARTIAL |
| MiniPlayer reveal | `foundation/MeloXMotion.kt` | damping .66; stiffness 300 | PARTIAL |
| Player shared artwork / shell | `screens/MeloXPlayerTransition.kt` | 300ms / settings duration; FastOutSlowIn | PARTIAL |
| Tab label / old content / compact glyph | `navigation/MeloXBottomChrome.kt` | .00→.32 / .43→.72 / .52→.82 | PARTIAL |
| Tab droplet dimensions | `navigation/MeloXBottomChrome.kt` | navHeight−8; min(tabWidth×1.15+2,tabWidth+12) | PARTIAL |
| Tab droplet visibility | `navigation/MeloXBottomChrome.kt` | damping .7; stiffness 180 | PARTIAL |
| `MeloXBackdropComponents.kt` surface opacity / tint | `glass/MeloXBottomGlassTone.kt` | .52; white .12→black .10 | PARTIAL |
| Bottom glass page / hidden red tab capture | `glass/MeloXLayerBackdrop.kt` / BottomChrome | real GraphicsLayer crop and blur | PARTIAL |
| Android branded home / settings vectors | `resources/icons` | pinned XML→SVG geometry, origin hashes included | PARTIAL |
| SF / MiLan font assets | `resources/fonts` | SHA256 identical to pinned Android font files | PARTIAL |
| `MeloXTheme.kt` app-wide typography / shapes | `theme/MeloXMaterialTheme.kt` | same typography roles; radii 10/14/20/28/36 | PARTIAL |
| `res/font/mi_lan_pro.xml` weight axis mapping | `theme/MeloXFont.kt` | 100→150; 200→200; 300→250; 400→340; 500→400; 600→540; 700→630; 800/900→700 | PARTIAL |
| `MeloXSymbol.kt` SF glyph rendering | `foundation/MeloXSymbol.kt` | Skia ink-bounds centering; ss16=1; regular/medium weights; independent box/em sizes | PARTIAL |
| `MeloXBackdropComponents.kt` no-backdrop / frosted material | `glass/MeloXBackdropComponents.kt` | preserve explicit alpha; no artificial highlight/shadow in fallback; blur-only frosted branch | PARTIAL |
| `SearchScreen.kt` browse categories | `screens/SearchScreen.kt` | same 20 ids; podcast visibility setting; 96dp card / 14dp inset / bottom alignment / 18sp bold | PARTIAL |
| `SettingsScreen.kt` retained detail / page transition | `screens/SettingsScreen.kt` / `foundation/MeloXMotion.kt` | retained root scroll and exit content; enter 280ms from width; exit 220ms to width/4; fade + FastOutSlowIn | PARTIAL |
| `MeloXIOSNowPlayingScene.kt` resident alternate-page transition | `screens/MeloXIOSPlayerScene.kt` | unified spring .7/300; Artwork −300dp; alternate pages +400dp only against Artwork; Lyrics↔Queue scale .92 | PARTIAL |
| Alternate-page song header / list boundaries | `screens/MeloXIOSPlayerScene.kt` | 72dp header / 84dp text inset; Lyrics top 88dp; Queue top 80dp + bottom 279dp; header spring from 40dp | PARTIAL |
| `MeloXIOSNowPlayingSharedHost.kt` persistent cover | `screens/MeloXIOSPlayerScene.kt` | one artwork; 480ms FastOutSlowIn to 72dp header; radius 12dp; source full-size/center equations; shadow dp conversion | PARTIAL |
| Lyrics control conceal/reveal | `screens/MeloXIOSPlayerScene.kt` / `screens/MeloXPlayerLyrics.kt` | real configured hide delay; enter fade 220ms + slide 260ms; exit fade 180ms + slide 220ms; slide height/8; reveal area restricted to lower 279dp | PARTIAL |
| Plain background blur | `glass/MeloXBackdropComponents.kt` | sampled backdrop + optional surface tint; no added white gradient; player background recorded separately from sampling controls | PARTIAL |
| `MeloXIOSLyricsPanel.kt` focus / annotation parameters | `screens/MeloXPlayerLyrics.kt` | 34/40.8sp × font scale; 28dp × spacing; configured weight/focus position/follow delay; 4dp annotation gap; ≥13sp translated/romanized lines; source dim/blur equations | PARTIAL |
| `MeloXLyricShareDialog/Image.kt` selected-line sharing | `screens/MeloXPlayerLyrics.kt` | source selection layout; clipboard text / native save picker; 1080px image, 340px header, 86px line stride, 4096px cap, artwork/palette background | PARTIAL |
| `MeloXPlaybackService.kt` queue recovery / persistence | `shared/playback/PlaybackCommands.kt` / QueueStore | source 2000ms position interval, save-queue preference gate, paused startup restore, current state after asynchronous resolution | PARTIAL |

## Capture and compare

`./gradlew :desktop:captureUi -PcaptureArgs='--theme Light --start-tab Home --width 427 --height 924 --density 1 --output build/parity/desktop --steps settings:291,884;search:386,884'`

This renders the production `MeloXApp` with its actual provider. Capture preferences are
isolated under `desktop/build/parity/home` (override with `-PcaptureHome=...`). Theme and
starting tab are selected through real preferences. No preview songs or fabricated
provider responses are supplied. Coordinates are physical pixels. `name:escape` sends
Escape; `--frames-ms 0,70,140,220,280,400` saves transition samples and records actual
elapsed timestamps (render/PNG encoding can make a sample later than requested).

`python3 tools/visual_diff.py android.png desktop.png --scene settings-light --reference-crop x,y,w,h --desktop-crop x,y,w,h --normalize-srgb --reference-sha 94e17c920bf554966584955afa2bd2ee92580a8b --output build/parity/diff/settings`

The tool emits side-by-side, overlay, heatmap, raw difference and SSIM/MAE/RMSE metadata.
Cropping and density normalization are explicit and logged. Metrics never automatically qualify as MATCH.

## Saved screenshot evidence

The saved pinned Android captures in `build/parity/android/` are 1280×2772 at density
3.25. They contain an embedded **Display P3 Gamut with sRGB Transfer** ICC profile;
untagged Skia captures are sRGB. `--normalize-srgb` performs ICC conversion before
comparison and records each profile/assumption. For example, the Android chart-card
pixel converts from (214,86,137) to (231,74,138), within one level of the source sRGB
color (232,74,138). UI constants must stay tied to source rather than raw P3 values.

The 152px top crop is inferred from matching title ink rows (Android Settings
382–477px; Desktop 230–325px), rather than a new device inset measurement. Desktop
app-area captures now use 1280×2620 without image resizing. The Android Settings
capture also contains a transient clipboard toast and scrollbar; whole-image
scores include those differences. Home/Search feeds differ because both use live
provider data; whole-feed scores cannot establish same-content acceptance.

Verified run `build/parity/capture-light-v2.log`: production Home→Settings→Search→
Home→Settings→General→Settings→Appearance clicks, Desktop jar build successful.
SF glyphs are no longer clipped and fallback search fields retain the intended
subtle fill. Pixel evidence:

| Comparison | Crop after top inset removal | SSIM RGB / 11px uniform window | MAE | Status |
| --- | --- | --- | --- | --- |
| Settings v1 | 1280×2620 | 0.924541 | 5.801952 | PARTIAL |
| Settings v2 | 1280×2620 | 0.936654 | 3.740373 | PARTIAL |
| Settings v2 upper region, excludes toast/dock | 1280×1950 | 0.962144 | 1.850482 | PARTIAL |
| Settings v3, app-area viewport corrected to 1280×2620 | 1280×2620 | 0.943225 | 3.313570 | PARTIAL |

Metrics, crops, image hashes, overlays and heatmaps are in `build/parity/diff/`.
The separate shared test run reports **34 tests, 0 failures, 0 errors, 0 skipped**.
Linux offscreen evidence does not qualify Windows-native capability acceptance.

Latest build/click evidence: `build/parity/capture-light-v3.log` and
`build/parity/desktop-light-v3/capture.json`. Home→Settings→Search→Home and both
General/Appearance→Escape→Settings completed. The final back screenshots show
Settings rather than switching to Home. Registered back actions are scoped to
each tab so outgoing tab content cannot clear an incoming tab's action during
the cross-fade. Global player/message overlays retain first priority.

Dark runtime/motion evidence: `build/parity/capture-dark-motion.log` and
`build/parity/desktop-dark-motion/`. Captured General enter/exit samples at requested
0/70/140/220/280/400ms, and Dark Home/Search with live feeds. The exit images retain
the General heading and settings while revealing the actual root page underneath.
Home/Search typography inherits the theme's foreground. Actual timestamps are in
`capture.json`; some feed transition captures missed the requested time by hundreds
of milliseconds (Home 220ms request sampled at 1416ms). These frames confirm content
retention and settled rendering, **not** frame-accurate animation or smoothness
acceptance. No additional device operations were needed.

The missed Home samples exposed a real runtime defect: `MeloXArtworkImage` read
network streams and decoded bitmaps directly inside a Main-thread LaunchedEffect.
`screens/MeloXArtwork.kt` now performs both operations in `withContext(Dispatchers.IO)`,
uses 10s connect/read timeouts, publishes on Main only after cancellation checks,
and publishes an existing cache hit even if it arrived after initial composition.
Rebuild and the same Dark transition replay passed in
`build/parity/capture-dark-motion-v2.log`. The Home 220ms requested sample was taken
at 345ms in that run, compared with 1416ms previously. Rendering/encoding and live
data still affect sampling; this is evidence of a removed blocking call, not a
controlled frame-rate benchmark. Search/feed animation smoothness stays PARTIAL.

## Player migration validation

The controller regressions in `DesktopPlaybackStateTest` exercise pause/seek while
URL resolution is suspended, cancelled late resolvers, selected-entry reordering,
resolution failure/retry, and 2000ms position persistence/release. The shared suite
now has **70 tests across 12 suites, 0 failures/errors/skipped**. New scenarios
cover cancellable recommendation ownership, 15s retry/de-duplication, cross-provider
metadata gates, upcoming-only shuffle/manual ordering, and quality changes preserving
entry identity, paused state and a zero seek position. This is controller evidence,
not audio-device acceptance. Five presentation regressions cover repeated-word
identity, Latin word grouping, reduced-motion effects, authored vocal-end gap
detection and zero-duration provider data. The bundled decoder test now loads the
actual production binary and fails on missing bundles/LFS pointers; the previous
17-byte decoder fixture and its silent early-success branch have been removed.

`build/parity/player-live-v1.log` compiled successfully, but its filenames are **not
player acceptance evidence**: screenshot inspection and the new per-scene state
metadata show null media/empty queues. The attempted search returned actual NetEase
tracks; playback was silently blocked because the desktop connectivity check pinged
8.8.8.8 and treated ICMP failure as offline. `platform/MeloXNetwork.kt` now inspects
active non-loopback interfaces without a blocking remote probe. Network failures
remain real resolver errors; Windows connectivity equivalence remains PARTIAL.
The capture harness now rejects a failed/no-selection playback dispatch instead of
labelling unrelated root pages as player screenshots.

The player implementation remains PARTIAL: landscape/Skyline visual acceptance,
AutoMix's fourth queue-mode control and dual-deck audio, native output-switcher
acceptance, cascade/glow/long-tone pixel/motion acceptance, alternative lyric styles,
interlude/accompaniment visual acceptance, and same-content Android player pixel acceptance
still need work. Selected-line sharing uses a platform clipboard and native save
picker; Windows-native actions have not been accepted in the Linux headless run.

`build/parity/player-live-v2.log` and `desktop/build/parity/player-live-v2/` now
contain inspected production Artwork/Lyrics/Queue/Artwork/close captures, with an
actual five-song NetEase search queue (first item `netease:1330348068`). Lyrics and
cover are fetched from the real provider pipeline. The persistent cover is large
on Artwork and 72dp in the alternate-page header; Queue and Lyrics share that
header rather than recreating the cover. Transition frame requests 0/140/280/480ms
are logged with actual sampling times. No Android player reference capture exists,
so these are runtime/source geometry evidence, not pixel acceptance.

The real playback URL resolved at 128000bps, but Java Sound cannot open a compatible
PCM SourceDataLine in this environment. Every scene records the actual error and
`is_playing=false`; the capture does not substitute a silent/mock audio device.
`build/parity/player-stream-decode.json` separately records successful decoding of
that actual stream by the bundled Linux FFmpeg: exit 0, 529200 PCM bytes, 3 seconds
at 44100Hz/stereo, peak sample 18009. This verifies stream/decoder data, **not**
audible output or Windows playback. Both remain PARTIAL.

### Node-asserted player interactions (v5 / v6)

`desktop/build/parity/player-live-v5/` contains inspected production screenshots
and per-scene unmerged semantics for Lyrics controls hiding/reveal, song actions,
long-press selected-line sharing, overlay Back handling, queue reorder, shuffle,
repeat and selection. The source controls use 48dp page buttons, independent
0.86 press / 1.04 selection springs, a 15dp mode badge, a 50dp selector row with
32dp horizontal insets, and a 48dp quality-chip hit target clipped by the 26dp
time-label row. Queue surfaces retain source radii (28/22/12/6dp), 48dp cover
rows and the upcoming-only 52dp reorder threshold.

The v5 reorder actually exchanged `netease:1475596788` and `netease:1916691093`
while retaining index 0 and the selected entry ID. Disabling shuffle restored the
source base order. Repeat cycled OFF → ALL → ONE → OFF. Selection changed from
`netease:1330348068` (index 0) to `netease:2636494144` (index 1). These assertions
were checked against the real production tree and pointer input, not invoked
callbacks. The entire v5 run did **not** pass: the settings screenshot was still
entering at x=366 and its menu anchor was clipped offscreen. The harness now requires
at least three render passes and supports bounded UI readiness/full-viewport
assertions; wall-time waits alone can end before a nested transition begins in
the software renderer.

In `desktop/build/parity/player-live-v6/`, the playback settings panel reached the
full viewport, the real popup exposed all seven qualities, and selecting High
persisted `exhigh` and re-resolved the same track without changing index 0.
Starting with one real search result, enabling Autoplay called the pinned
NetEase similar-song recommendation pipeline, appended genuine NetEase identities,
and allowed selection of an appended entry. See `capture.json` for exact IDs and
positions. This verifies recommendation fetching/queue append, not natural-end
audible continuation. The v6 run stopped when Escape had no focused target after
the selected upcoming row disappeared; the app now gives the player a resident
focus target on reopen/track selection. Final focus/visual acceptance is recorded
in subsequent captures, not retroactively attributed to v6.

Screenshot inspection also found a desktop-only hover fill on the lyrics reveal
region and root-tab artwork leaking into share-button glass. The reveal target
now uses the pinned null indication, and the source opaque share scene uses its
own no-backdrop fallback rather than the tab's sampled layer. Settings and share
still require pinned Android same-content pixel acceptance.

### Passing portrait interaction chains (v7 / v8)

`player-live-v7` completed successfully. The whole Artwork → Lyrics → share →
Queue → next track → quality → Playback settings → High → Back → reopen Queue →
quality Back → player Back → Mini next → reopen → Back chain used physical input
and passed every recorded assertion. High persisted `exhigh`; selecting another
queue item retained the page and Escape remained available after the selected
upcoming row left the lazy list.

`player-live-v8` completed successfully. Clicking lyric line 2 sought to its
provider time; mouse-wheel input entered browsing, the follow delay ended browsing,
controls hid/revealed, and a physical vertical drag collapsed the real player.
Reopening retained the Artwork page. This run predates the glyph/cascade renderer;
its browsing assertion alone does not establish the restored line's anchor.

### Glyph/cascade and landscape implementation

`MeloXLyricPresentation`, `MeloXGlyphLyricText` and `MeloXLyricCascade` implement the
pinned renderer's character/word lift groups, Latin long-tone groups, reveal
attack/release, 320ms lift continuation, 550ms glow tail, 1.0625 maximum long-tone
scale, ordinary 1.0→1.2→1.0 bounce, source shake phases, quality-dependent feather
stops, 0.648 glow-mask alpha, concurrent-line focus, independent following-line
springs, interrupted-offset carry-over, focus scale springs, ruby/romanization,
600ms accompaniment entry/exit and reserved instrumental countdown geometry.
The position clock is sampled at the source quality/refresh-rate ceiling and is
read in Canvas; the paused/error state does not advance a fabricated clock.
MiLan native rasterization uses the same weight-axis mapping as Compose layout.
Unsupported native glyphs use Compose's font-resolution fallback.

`player-landscape-v1` completed successfully at 924×427, after compilation and
the 52-test suite. Inspected screenshots show the persistent cover in the 43%
pane, 24dp inter-pane gap, 64dp right song header and source-sized control/page
rows. Real input selected Lyrics, sought the first line, hid/revealed controls,
selected Queue, selected index 1 and completed Back/reopen/Back. Skyline remains
unimplemented. No Android player reference is available; renderer and landscape
status remain **PARTIAL**, including same-content pixel and animated-frame checks.

### Authored-word and restored-anchor replay (v12)

`player-live-v12` completed with 13 scenes and 15 passed assertions, now copied to
`evidence/player-live-v12/`. Its input uses `--play-id 186016`, fetching the actual
provider song detail for Jay Chou's 晴天; it does not rely on changing search rankings
or substitute a fabricated metadata/lyric fixture. The production lyric pipeline
returned `AmlL:Authored`, 62 lines with authored syllables, pseudo timing disabled.
Clicking line 2 sought to 36238ms. Both the initial seek and post-browsing return
verified the row's source focus anchor against the measured viewport. A physical
slider click sought to 49353ms and the inspected screenshot shows the native
glyph-reveal mask on the current Latin solfège line. Hide/reveal, drag collapse,
reopen and Back assertions also passed.

Earlier probes are not promoted to passing runs: v9's slider action was attempted
as the controls retracted, v10's live search selected a plain-LRC cover version,
and v11's slider position 26648ms was still before the first authored vocal cue.
v11 already verified authored data, seek and focus restoration. v12 moved the
slider into an actual vocal interval. These checks validate seeking/paused
presentation, not continuously audible karaoke, long-tone video or pixel parity.
The provider returned no playable Standard stream/fallback for this account in
v12; that real error remains in the evidence rather than being replaced by audio.

### Playback completion, seek and decoder-error lifecycle

Source anchors: `MeloXPlaybackService.kt:145–160` (error → downloaded/next/prepare),
`175–194` (ended/offline/recommendation handling), and `1590–1612` (provider-neutral
downloaded identity, forward candidates and repeat-all wrap).

`AudioPlayer` now freezes completion/failure callbacks in each actual play request;
the controller validates its generation and queue entry ID before applying events.
Queued end events are also invalidated by an intervening seek/pause. Releasing an
inactive controller cannot stop or overwrite the active controller's audio/queue.

FFmpeg uses `-xerror` and its actual exit status is read before deciding success.
Valid decoder EOF leaves the output open until `longFramePosition` has consumed
all accepted PCM frames. That wait honours pause, resume, seek and replacement.
Short device writes are accumulated correctly; pipe reads retain full PCM frames.
The final heard position is published before `isFinished` and the end callback.
Every seek increments a revision, including a seek back to the original 0ms;
closing the old output/process unblocks pending reads/writes so a seek can restart.
Bundled extraction is serialized to protect the shared `.part` path.

Content decoding failures follow the pinned service's next-item recovery and
prefer downloaded forward/wrapped entries offline, even when repeat-one is set.
At an exhausted tail the current entry is reprepared at its latest position with
the existing pause intent. This desktop recovery is bounded to one reprepare per
entry in the failure chain, and an explicit Play allows another attempt. That
bound and terminal device-wide Output/Configuration errors are deliberate platform
adaptations; they remain **PARTIAL**, not an exact Android error-loop match.
Resolver/account errors still expose the genuine provider error for explicit retry.

Natural-end recommendation completion can append and resume the next item, while
a user pause after the end prevents a late recommendation from restarting audio.
The existing 60s prefetch, 1s maintenance and 15s retry constants are preserved.

`DesktopAudioSessionTest` adds seven actual bundled-FFmpeg/process/PCM-loop tests:
tail drain with pause/resume, zero-origin seek restart, a truncated WAV which emits
PCM then exits nonzero, same-URI request replacement, and unavailable-output
classification, a zero-PCM successful seek to the known track end, and device-start
failure classification. Only `SourceDataLine` hardware is controlled in those tests; no
mock decoder is selected in the production path. Eleven additional controller
scenarios cover recovery/ownership/offline selection, exhausted repeat queues,
late recommendation handoff and user intent. The full shared suite has 70 passing
tests. This verifies process and controller semantics, not audible Windows output.
Final passing task/test metadata, case names, timings and the compiled source-file
hashes are stored in `evidence/audio-lifecycle-v3/test-results.json`.

`player-live-v13` passed 33 assertions across 22 scenes at 427×924, with the actual
five-track NetEase queue. The initial physical output failure was recorded as
`Output`, index 0 and `play_when_ready=false`; it did not consume the queue.
The physical Lyrics → seek → browse/follow anchor → hide/reveal → share/Back →
Queue/index 1 → quality → Playback settings/High → Back → reopen/Back → Mini next
(index 2) → reopen/Back chain completed successfully. The seek and queue captures
were visually inspected. Snapshots and exact sampling times are archived under
`evidence/player-live-v13/`. This replay includes the completion/error lifecycle
changes, and predates the subsequent known-end zero-PCM boundary regression.

A final actual FFmpeg probe confirmed that seeking to the 500ms end of a 500ms
WAV exits 0 with no PCM. The backend now treats that bounded known-end seek as a
normal end at 500ms; an empty decode away from the known end remains a content
failure. `audio-lifecycle-v3` completed successfully after this correction with
70 tests and `desktopJar`. The dedicated production-process regression covers
this boundary behavior.
