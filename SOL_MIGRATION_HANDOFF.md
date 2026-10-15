# Sol third-round implementation handoff

Pinned Android: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.
Starting Windows: `24eb7fffb7f5c01d17baca5b68674bc33b11be86`.
Follow-up upstream fetch (2026-10-12): `febb4c0534787e040a89df8457e630d067f44b80`, 9 further commits / 154 changed files; closing fetch adds 3 commits / 28 files at `f14a1c1f5400664d07575a113469f41ee3b70572`. Both `evidence/upstream-intake-*.json` inventories use pure commit exports. AutoSync/model alignment, Smart Queue quota and signed hot-update text/scripts/colors/rules plus APK deltas remain unimplemented increments; the pinned matrix is not implicitly upgraded.

Settings/legal closing fetch (2026-10-13): `5b52a92022a5943e238ffc5d3121c536b2f29e5c`, 5 additional commits / 24 files (rename-aware), 17 commits total after pinned. Resource-backed locale/translation-folder and podcast changes plus system/explicit TTML translation-language selection are independently reviewed/pure-exported in `upstream-intake-5b52a92.json`, **not migrated/accepted**. Continue using pinned 3f877ea and policy compatibility 20.

## Landed in this batch

### Settings pages pinned replica + cold-start memory (2026-10-15)

**No distribution has been issued from this batch yet.**

`tools/export_settings_page_resources.py` derives the settings contract from the pure pinned Git tree only:
**19 root routes / 3 sections / 797 keys / 4 resource locales** (en, zh-rCN, zh-rTW, ja), plus the
`parity/3f877ea/evidence/settings-pages-source-audit.json` source/resource SHA manifest. `pinnedString()`
now selects the bundle from `Locale.getDefault()` with default fallback, and
`tools/verify_pinned_resources.py` covers all four bundles byte-for-byte.

Every detail page now uses `MeloXPinnedListPage` (collapsed large title + 62dp toolbar) exactly like the
Android `SettingsDetailScreen`; the legacy per-page extra 20dp horizontal / 10dp top padding was removed.
Root sections/order and all 19 entries match the source (Privacy is no longer a root entry). General,
Appearance, Content, Playback, AutoMix, EQ, Storage, About, RemoteConfig and TabLayout text no longer
hard-code Chinese; Content areas are the source `全部/华语/欧美/日本/韩国`.

Controls whose OS backend does not exist on desktop (clipboard links, haptics, system font, immersive
playback, system notification lyrics, floating overlay, recognition, listen-together, auto-update
scheduler, playback second account, Bilibili A/V alignment) stay **disabled with an explicit
`settings-platform-availability` note**. They never persist an enable, so there is no "toggle works but
does nothing" state. Recognition/ListenTogether remain the pinned empty/stub backends and are not
presented as functional.

Verification on the **same JVM flags as the shipped package** (`-Xms16m -Xmx1g -XX:+UseSerialGC`):
4 locale route replays (19 routes each, physical mouse + real keyboard no-match search) + controls +
independent-JVM reload = **6 captures / 395 scenes / 316 assertions**; the pinned **60-key lyrics** and
**17-key Skyline + 4 awake scopes + exact reset** replays both re-pass on the new page header;
`:shared:desktopTest` **208 tests / 40 suites / 0 failures**; artwork memory and new
`:desktop:auditArtworkDecode` (48 codec/viewport cases incl. alpha and invalid input) pass.

Cold-start memory, measured with `tools/capture_startup_memory.py` on an isolated home (3-run medians,
same jar): restored queues no longer resolve audio or start a decoder before Play (`isLoading=false`,
`isPaused=true`, position preserved; unit tests assert zero resolution for 10s, and Local queue
`contentUri/fileKey` now persist). Artwork decodes at the physical Crop viewport instead of full
resolution with a 2-decode concurrency cap. Production `jpackage --java-options`, `captureUi` replays
and the cold-start audit share one flag set, moving first-frame resident **218.4 MB → 195.9 MB** and
heap committed **38.8 MB → 19.3 MB**. This is startup-only and does **not** resolve the post-playback
549→1431 MB growth, which is still being located. See `parity/3f877ea/SETTINGS_PAGES.md`.

Follow-up: settings icons are pinned SF Symbols drawn from the bundled `sf_pro_subset.ttf`. Two
desktop deviations were corrected against pinned Android: the `✉` Messages token now resolves to SF
`paperplane` (it was the `message` bubble) and the TabLayout `↑`/`↓` order controls are SF
`arrow.up.circle.fill` / `arrow.down.circle` instead of text arrows; an unmapped token now fails fast
instead of drawing a question mark. `:desktop:auditSettingsIcons` compares all 19 route tokens plus
the arrows with the token→SF-name table exported from the pinned Android Git tree and checks each
glyph really has ink (18 tokens PASS).

The Windows CI failure of the four-locale replay was not a UI defect: `CreateProcess` encodes the
child command line with `sun.jnu.encoding`, so pinned CJK labels arrived in the harness as `??`.
Steps and assertions now travel through UTF-8 `steps.txt`/`expect.txt` files and the harness takes
`--steps-file`/`--expect-file`, leaving argv ASCII-only. Evidence upload now includes those files and
the harness log tail is mirrored into the CI step output on failure.

Still BLOCKED: pinned Android pixel/motion comparison (no pinned Android screenshots/frames here), real
Windows standby/DPAPI/SMTC/device switching, physical audible acceptance, and the whole-process
native renderer/text/allocator growth.

### Current verified Skyline/native power delivery (2026-10-15)

**Current independently verified distribution:**
`f3e3a83d63d0d12c36723aee974ebf3dc2a530d1`, Windows run **38653215039 / SUCCESS**,
artifact **11936083521**. Final ZIP **308,020,756 bytes**, SHA-256
`0cc5fb610ed0dea45a323f5763ce05cdf055cbdbec9b3c12295e85a05f5ffe0a`.
Download: <https://github.com/lladlam/MeloX-Windows/actions/runs/38653215039/artifacts/11936083521>.
Linux/Windows **206 tests / 40 suites / zero failures**. Skyline all 17 keys,
four awake scopes and source reset pass 70 scenes / 342 assertions across four
JVMs. Prior 60-key lyrics replay/reload / 501 assertions still passes. Real
Tiny Giant consumer/layout/PCM replay passes with 0px paused geometry restore.
Real Windows kernel32 ownership passes six samples / four native calls / one
thread, including direct foreground gate and stale-UI/late-disposal protection.
Independent artifact digest/CRC/revision, FFmpeg/Java Sound/management, eight
pinned resources, eight static fonts/metrics, glyph/font/EQ/cache-memory gates
pass. See `parity/3f877ea/SKYLINE_SETTINGS_POWER.md`. User-machine power/idle,
549→1431MB memory, physical accounts/audio/devices and Android pixels remain
pending/blocked. Next gaps include provider-native actions and real initial
favorite-state reads; current song actions still start `liked=false`.

### Preceding verified advanced lyrics delivery (2026-10-15)

**Preceding independently verified distribution:**
`d186e0c62941d2726c2184b8399d97c861af4195`, Windows run **38592934356 / SUCCESS**,
artifact **11909196579**. Final ZIP **307,984,559 bytes**, SHA-256
`a97b4b0d89861634b12a35ea38be2b1277134a06cba1a8f59d6d9cb7b8bc8f37`.
Download: <https://github.com/lladlam/MeloX-Windows/actions/runs/38592934356/artifacts/11909196579>.
Linux/Windows **204 tests / 39 suites**, all **60 canonical lyric controls**
changed by coordinate input and reloaded in a new JVM (**501 assertions**).
Real Tiny Giant selected-consumer replay and PCM pass. Independent outer digest,
ZIP CRC, revision, native FFmpeg/Java Sound/management, seven pinned resources,
eight static fonts/metrics, 60 row-mask and 120 font cases, real EQ PCM and
cache memory audit all pass. See `parity/3f877ea/ADVANCED_LYRICS_SETTINGS.md`.
Whole-process 549→1431MB memory, pinned Android pixel/motion and physical Windows
account/audio/device behavior remain pending/blocked.

**Follow-up Skyline batch (now verified above):** all 17 Skyline settings and exact 13-key reset are
implemented at the source SkylineLyrics route. Four awake scopes replace the
legacy stored toggle, with an actual Windows same-thread display/system idle
request and direct AWT foreground/iconify gate; disposal prevents late reacquire.
Linux controls: 70 scenes / 342 assertions across four JVMs. Real Tiny Giant:
217 scenes / 162 frames / 69 assertions, paused geometry restores with 0px error
and genuine PCM passes. Linux 206 tests / 40 suites and package decoder/resource
gates pass. Native API is UNSUPPORTED on Linux. Windows run `38653215039` at exact
`f3e3a83d63d0d12c36723aee974ebf3dc2a530d1` now passes all steps including real
native power-request ownership, both settings replay batches and packaging.
Independent distribution collection passes, artifact `11936083521`; source
revision remains `f3e3a83`. See `parity/3f877ea/SKYLINE_SETTINGS_POWER.md`.

### User-accepted font follow-up (2026-10-14)

**User-accepted font package:** `797c3be4a64e3f9bef536e1cb2b26654c16f47d7`,
Windows Build **38559314976 / SUCCESS**, artifact **11891553587**. **194 tests
/ 36 suites / zero failures, errors or skips**, Windows glyph raster **60 cases
/ zero difference**, and the new independent font gate **120/120 PASSED on
Windows Server 2025**: native measure width per weight 676.06/702.75/755.21/
768.88 (was one collapsed constant 676.056), shaped paragraph vs independent
HarfBuzz within **0.0002 px** at every weight, ink ratio exactly 1.0 (no
synthetic-bold path), 8 packaged axis-frozen instance bytes + manifest
verified, frozen typefaces with zero variation axes. Final ZIP **307,918,466
bytes**, SHA-256
`cfec0f200a8f74248460e23dfe4d6777425b50daead6e2bff4c22c9268c4ae57`.
Download: <https://github.com/lladlam/MeloX-Windows/actions/runs/38559314976/artifacts/11891553587>.
Independent artifact digest, ZIP CRC, FFmpeg PE/hash, Java Sound DLL, exact
revision, six pinned resources, static fonts/manifest bytes, real single/dual
EQ PCM all verify. See `parity/3f877ea/FONT_SYNTHETIC_BOLD.md` and
`evidence/font-synthetic-bold/`.

**Windows root cause fixed in this package:** the Windows text stack applies
the variable-font `wght` axis to outlines but keeps default-axis advance
widths, so Heavy glyphs sat on light advances (the user's letter-overlap
report; Linux never reproduced it). The package now ships eight axis-frozen
static instances (`tools/generate_lanting_static.py`) baked from the pinned
variable source, each provably shaping identically to the variable font at its
Android-mapped axis; CSS 900 reuses the axis-700 instance; stale STAT is
dropped (DirectWrite would otherwise report private axis coordinates and
re-enable synthetic bold); the pinned variable font remains in the package
unchanged as the hash-verified source. The user accepted the `797c3be` font fix
on 2026-10-14 (“可以了。继续迁移其他功能”). Android pixels/motion remain separate.

**New active report:** the Windows executable uses **549 MB at startup** and
**1431 MB after playing for a while**. The unbounded general decoded-artwork
cache and separate player-image cache are being replaced by one 128-entry /
64 MiB budget. Per-row 64-paragraph history is disabled; actual heap/OS process
memory diagnostics and image-retention stress are added. See
`parity/3f877ea/WINDOWS_MEMORY.md`. Feature revision is `768445f`; Windows run
`38578024876` passed. Local/Windows 202/39 tests, no-forced-GC 2 GiB cache stress
and real twenty-cycle playback pass, but the full Linux renderer still reaches
894 MiB while artwork stays at 24 MiB. Do not label the whole symptom resolved.
Independent download of artifact `11901753358` passes all package checks;
final ZIP SHA-256 `3689043da3fa5ad9ccd90ecd13311b5db6dba74750764175b570dde9c0ecde5b`.
User memory retest is pending. On request, migration resumes with advanced
lyrics controls while retaining the unresolved full-process memory finding.

The earlier `989f33b` package passed all prior gates but was **rejected by the
user for the font symptom**; it is not accepted as resolving the report.

**Verified previous package:** `989f33bd4cb074e91ff9dc267e53b5ce380c0294`, Windows
Build **38505030167 / SUCCESS**, artifact **11867583407**. **194 tests / 36
suites / zero failures, errors or skips**, plus **60 Windows glyph cases / zero
pixel difference**. Source/JAR/app-image/final ZIP genuine native decode and six
exact pinned resources pass. Independently downloaded artifact digest, ZIP CRC,
FFmpeg PE/hash, Java Sound DLL, actual correction classes, exact revision and
generated single/dual EQ PCM all verify. Final ZIP SHA-256
`30ef69fba859ea273814b6d7efab3799f6255788da7f5a0f2c144ef40863338c`.
Download:
<https://github.com/lladlam/MeloX-Windows/actions/runs/38505030167/artifacts/11867583407>.
Final-source Tiny Giant replay passes **41 scenes / 20 assertions**, including
actual online native PCM, paused seek, physical resume, six playing lyric
frames and subsequent pause. User Windows 10 retest and Android pixels/motion
are still pending; evidence-only archival commits do not relabel the package.

Production attempt `ea18d388b5a127eea78753a0e49ad83199acc1d1` was rejected by
Windows Build `38504096447` at the enforced glyph geometry gate (1.5407% > 1%).
All 194 Windows tests and real native EQ passed; no distribution was created.
The final production revision fixes fractional mask-crop seams with integer
pixel partitions covering each complete shaped row and trailing ink overhang.
All 60 expanded Linux audit cases now have zero difference. Retain the exact
final verified package source even when an evidence-only commit advances main.

The user's genuine Windows 10 report from diagnostic package `d5489de` reached
actual URL resolution, bundled FFmpeg and an opened Java Sound default output,
then failed in PCM write. This is distinct from Windows anonymous DAMIDAMI CI
returning no URL. The report's actual track and supplied scrambled-glyph
screenshot are Tiny Giant (`netease:3334421408`), not DAMIDAMI `2755332551`.

Confirmed pause-interrupted zero/short-write errors in both normal and AutoMix
output are corrected by a shared pause-version-aware PCM writer with exact
already-processed remainder retention. Real-device zero/unaligned/closed writes
still fail and now report accepted/requested bytes, open/running/paused state,
pause version and PCM clock. Real bundled-FFmpeg regressions now total
**194 tests / 36 suites / zero failures, errors or skipped** locally, including
quick pause/resume, byte retention, paused pending-write stop/seek and unexpected
device closure. Real single/dual EQ PCM remains verified.

Glyph overlap investigation now compares **within-character/word rendering**,
not just settled lyric row gaps. Windows's rejected unlifted Heavy baseline
differs from its own shaped paragraph by up to 25.22% normalized absolute ink
difference. Glyph regions now come from one immutable Compose-shaped row mask
with the exact pinned MiLan Pro font; original glyph timing/reveal/lift/glow/
scale/blur is preserved. The expanded 60-case 100–200% density compositor
self-geometry audit passes locally with zero difference and enforces a 1%
gate in Windows CI.
Production Tiny Giant online/native FFmpeg/consumed software PCM and its
opening lyrics at 175% density replay successfully.

See `parity/3f877ea/PCM_WRITE_INTERRUPTION.md` and
`evidence/pcm-write-interruption/`. User Windows 10 rendering/audibility and
pinned Android pixels/motion remain **BLOCKED** until directly reproduced.
The advanced-lyrics batch is preserved in two stashes and restored for migration.
All 60 keys now pass a complete coordinate replay and separate-JVM persistence
check (151 + 2 scenes / 501 assertions). Real Tiny Giant consumer replay passes
173 scenes / 129 dynamic frames / 55 assertions, including translation scope,
font/size/reduced motion, real PCM and auto-hide/reveal. Windows release gates
are still pending: first feature run `38587692142` at `9e1fe56` failed the
existing AutoMix heard-position regression before lyrics replay. A deterministic
inline-resolution case reproduced starting the incoming track at 0 instead of
1200ms. Next now installs heard position before prepare; Linux 204/39 passes.
The portable fixture and explicit UTF-8 replay argument files resolve the next
two Windows gate failures. Run `38592934356` at exact
`d186e0c62941d2726c2184b8399d97c861af4195` passes all Windows steps, including
full pointer replay + separate-process reload and all package layers. Independent
download/collection passes, artifact `11909196579`; delivery provenance is
recorded above. See `parity/3f877ea/ADVANCED_LYRICS_SETTINGS.md`. Continue remaining
pinned gaps and the 17 independent upstream increments afterward.

User-reported “search works, anonymous playback fails” repair is tracked in
`parity/3f877ea/PLAYBACK_BUGFIX.md`: real song 139774 reproduced an unencoded
NBSP query failure before any audio request. Old package 47542d8 predates the
repair. URI encoding/raw-query decoding, unified native NetEase delegation,
third-party resolver wiring and actual pinned CHKSZ requests are repaired;
final acceptance lives in `evidence/playback-fix/validation.json`. Existing LX
script runtime is still empty/unaccepted, and physical audio remains separate.
Actual fixed Windows package is **0cd3883e0ae4a210c7e5e2a03fa7e4f52f0e0dd6**,
run **38383921705 / SUCCESS**, artifact **11805120983**; final ZIP SHA-256
`8d8cc6c5e7bd06b3e94cd5c3e5357d4f057d5432b5aa5adbe96edd965c34e7a4`.

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
- Settings/legal: actual first-launch legal → connection → cloud choice, immediate native NetEase login branch and historical consent preservation; four unchanged pinned legal documents, full-screen reader and real Legal controls; source-default English strings and complete Chinese cloud welcome. Privacy ten-second gate and actual joined/background-safe erase are wired; the pinned dormant Privacy page is made reachable explicitly. Pinned collapsing header/fade uses an acyclic page layer. NetEase revisions fix stale Settings/Library accounts, cancel-safe IO profiles and captured-cookie Library reads. See `SETTINGS_LEGAL.md`; root inventory/detail/model/pixel equivalence remains partial.

## Still required

Search/player/output batch now implements pinned native Songs/artwork and owned cancellation, actual profile/podcast/playlist routes, native operation IDs and queue/swipes, functional adaptive Classic, real-clock EVA/TextPV/explicit Skyline and all 18 styles, plus Bilibili per-resource offsets. Real ten-band stereo user EQ/preamp consumes single and post-mix dual PCM with canonical/legacy/reset compatibility and source controls. Local lyric/recognition/favorite persistence is corrected. See `parity/3f877ea/SEARCH_PLAYER_PLAYBACK.md`; overall parity remains PARTIAL.

1. AutoMix streaming-provider account acceptance and remaining settings/candidate/transport edge cases; physical Windows audible transitions and Android dynamic cover parity. Production dual PCM/tempo/EQ/Smart Queue/UI are now implemented and tested, but overall AutoMix remains **PARTIAL**.
2. Complete behavioral upstream-delta acceptance, including Library's complete source layout and App Shell combined backdrop/shared transitions.
3. Windows account/native DPAPI, physical speaker/device-switch/sleep, continuous audio and dual-track transitions. Current environment is Linux; do not convert controlled SourceDataLine tests into physical acceptance.
4. Current pinned Android reference screenshots/frames. Device enumeration returned zero. Artist/album and remaining pages still need same-content comparison; historical Settings SSIM is not a new complete parity result.
5. Remaining settings routes, Unified Search authenticated-provider/catalog/download/alternate-candidate acceptance, live Cloud account/chooser acceptance, podcasts/library Provider acceptance, Classic/alternate lyric shell/settings and refraction/dynamic-tone verification.
6. Desktop SongRecognitionClient is an existing empty-result stub; the new Android ID fields do not solve its missing native recognition path.
7. Independently migrate and accept the newly fetched `3f877ea..febb4c0` increment after completing the pinned batch. Preserve actual models/providers and signed-config scope; do not label pre-AutoSync frames/tests as new upstream parity.
8. Jellyfin real user-server acceptance: authenticate and reopen the exact Windows distribution, read/search real Library, inspect authenticated cover, play native universal audio, favorite and create/rename/add/duplicate-remove/move on an authorized server. Client protocol fixtures and Linux protected-file tests do not prove native Windows DPAPI, server-version compatibility or audible output. Old unpersisted-random-key ciphertext cannot be recovered; keep it until successful genuine re-login.
9. Search/player/output remaining: complete shared-element/back animation, Provider catalog/action and advanced lyrics/player controls, system keep-awake and physical platform behavior. User EQ is now a genuine single/dual PCM consumer; desktop RBJ does not prove Android device-specific hardware response equivalence. Online song 185670 returned no playable non-preview source and podcast programs returned -462/login-required without an authorized account; those remain BLOCKED.

## Execute physical Windows acceptance

Download the CI artifact for the actual new commit; extract its app-image.

```powershell
pwsh tools/windows_audio_acceptance.ps1 -AppImage C:\MeloX\MeloX-Desktop -EvidenceDirectory C:\MeloX-evidence
```

The runner verifies final packaged FFmpeg, enumerates Windows sound devices, opens the real launcher and records explicit PASS/FAIL/BLOCKED/NOT_RUN observations. Supply generated/public fixtures and a real authorized account locally. Keep personal audio and credentials out of public evidence. Publish only sanitized logs and synthetic loopback metrics.

Detailed observable-state matrix and evidence: `parity/3f877ea/`.

## Verified batch evidence

- Latest anonymous playback fix/package **0cd3883e0ae4a210c7e5e2a03fa7e4f52f0e0dd6**: Windows run **38383921705 / SUCCESS**, **174 tests / 33 suites / zero failures, errors or skipped**. Source/JAR/app-image/final ZIP native formats/required-header HTTP, real EQ and all five immutable resource bytes pass. Full downloaded artifact GitHub digest, final ZIP CRC/PE-x64 decoder/feature classes/resources and downloaded generated-EQ PCM independently verify. Artifact **11805120983**; final ZIP **264515014 bytes**, SHA-256 `8d8cc6c5e7bd06b3e94cd5c3e5357d4f057d5432b5aa5adbe96edd965c34e7a4`. Linux ordinary/unified anonymous song 139774 has actual consumed non-silent software PCM; 2 replays / 24 state scenes / 66 frames / 26 assertions. Song 185670 remains genuine error-only/no PCM (14 scenes / 39 frames / 14 assertions). Physical Windows audio/real-user DPAPI/Android pixels stay BLOCKED. See `PLAYBACK_BUGFIX.md` and `evidence/playback-fix/`.

- Previous accepted search/player Windows package **47542d8401ed626e7fff87444499729a0f3c369b**, run **38372169489**, **SUCCESS**, **166 tests / 31 suites / zero failures, errors or skipped**. Real native EQ, source/JAR/app-image/final ZIP native integrity/formats/header HTTP, and all five immutable pinned resource bytes pass at all four stages. Downloaded artifact GitHub digest, actual final ZIP PE/x64 decoder, pinned resource bytes and native EQ PCM independently verify. `windows-ci-47542d8/`; final ZIP **264503115 bytes**, SHA-256 `d79fd2e357efee6bcbc1f26f936afa89346f73e2ac4008c1fd0e316a0c0ba403`, downloadable artifact **11800221356**. Feature is a07e1be; 47542d8 fixes Windows resource line endings and adds verification. Physical audio/native real-account DPAPI and Android pixels remain BLOCKED.

- Search/player Windows feature CI **a07e1be97ea166ec886249ceaa307deb6fc87c2d**, run **38368910608**, CI SUCCESS: **166 tests / 31 suites**, genuine native single/dual EQ and source/JAR/app-image/final ZIP native format/authorized HTTP pass. Full artifact download digest verified and native EQ independently reread. Distribution resource-byte acceptance is **REJECTED**: checkout converted pinned legal/default strings to CRLF (normalized text unchanged). Explicit LF attributes and immutable source/JAR/app-image/final ZIP resource checks are added in a follow-up; archive is `windows-ci-a07e1be/`. Continue player work from `NEXT_SEARCH_PLAYER_GAPS.md`: 51 of pinned LyricsSettings' 60 distinct keys still need production Settings exposure; reference inventory is not behavior acceptance.

- Latest Linux search/player/output: **166 tests / 31 suites / zero failures, errors or skipped**, **9 production replays / 258 state scenes / 747 dynamic frames / 215 assertions**, including all 18 TextPV styles and generated EVA. Independent native single/dual EQ PCM readback, source/final-JAR decode and unchanged legal files pass. `search-player-validation.json`, `search-player-tests/`, `search-player-native-pcm/`, `search-player-equalizer-readback.json`. Final Linux JAR SHA-256 `038815dd488e74cbb125a2e79e184bb73d59e15be791c64fbce9189ea7b5cd28`. New exact feature-SHA Windows CI is pending publication; old 2181ba4 CI does not cover these changes.

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
- `evidence/windows-ci-337042a/`: exact Jellyfin run 38224003236 passed **144 tests / 24 suites** and native source/JAR/app-image/final ZIP integrity/format/required-header HTTP decode checks. Its exact-SHA downloadable `MeloX-Desktop-Windows` distribution is attached to that run; live-server/DPAPI/speaker/pixel acceptance remains separately blocked.
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
- Latest Linux Settings/legal batch: **151 tests / 27 suites / zero failures, errors or skipped**; six production replays total **56 state scenes / 81 assertions / 150 dynamic frames**. Real Local dual-PCM spectral handoff, unchanged legal-resource hashes, final JAR and native format/header decode pass. Final JAR's four legal documents and 122 default-string entries also match the pure pinned export. `settings-validation.json`, `settings-tests/`, `settings-*-ui/`, `settings-pcm-readback.json`, `settings-packaged-legal-resources.json`.
- Latest exact Windows CI: Settings/legal implementation **2181ba4f57e63ee13d3d16aac2c539bb09afab32**, run **38341304307**, **SUCCESS**, **151 tests / 27 suites / zero failures, errors or skipped**, plus source/JAR/app-image/final ZIP native integrity/format/required-header HTTP PCM. `windows-ci-2181ba4/` retains raw reports and the exact-SHA `MeloX-Desktop-Windows` artifact metadata/digests. No old CI or Android visual result is inherited; physical audio/native real-user DPAPI/pixels remain blocked.
