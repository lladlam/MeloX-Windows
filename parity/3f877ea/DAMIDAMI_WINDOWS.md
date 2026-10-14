# DAMIDAMI Windows playback and lyric report

Pinned Android remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## User report and exact target

**2026-10-14 follow-up:** the user ran diagnostic package `d5489de` and supplied
an actual Windows 10 PCM-write Output failure and a Tiny Giant glyph-overlap
screenshot. That report's actual track is `netease:3334421408`. The newer
pause-safe PCM and shaped-row glyph corrections are tracked in
[`PCM_WRITE_INTERRUPTION.md`](PCM_WRITE_INTERRUPTION.md); their production
attempt `ea18d388b5a127eea78753a0e49ad83199acc1d1` was rejected by its enforced
Windows glyph audit and is followed by a pixel-partition correction. The observations and
package below describe the earlier DAMIDAMI investigation and are not
evidence that the user's physical issue is resolved.

- Anonymous Android playback works; Windows returns to the play-button state.
- Track duration is visible. Duration metadata alone does not prove decoding or
  successful speaker initialization.
- Exact original: NetEase `2755332551`, **DAMIDAMI**, Sihan / 三Z-STUDIO / HOYO-MiX,
  album 绝区零-DAMIDAMI, 191000 ms. No account/session is injected into replay.
- The live anonymous official standard route returned a full, non-preview MP3:
  128002 bps, 48000 Hz, 3057069 bytes, `fee=0`, no trial info, host
  `m701.music.126.net`. Private/signed URLs and raw online audio are not published.
- An initial production Search replay succeeded with the real resolver, FFmpeg,
  and consumed software PCM. This did **not** reproduce the user's physical
  Windows failure and is not an acceptance of the user's reported installation.

## Actual defects found and changes

1. **Repeated FFmpeg temporary copy.** The previous locator wrote a full `.part`
   copy on every call even when a usable extraction already existed. A real
   native test failed with disk quota exceeded at that write; rejected evidence
   is retained. The locator now hashes source/legacy bytes before reuse, caches
   the verified immutable loaded resource within a process, and uses a
   content-addressed extraction when source content changes. Same-size old
   binaries cannot substitute for packaged bytes. New partial files are cleaned
   on failure; Windows executable ACLs are not treated as POSIX chmod.
2. **Identical font copied nine times in a packaged process.** Nine source
   variable-weight instances now share one extracted Android font file. Every
   required weight must load; font failure is logged instead of silently using
   a default family.
3. **Silent playback error.** The expanded player now presents genuine source,
   decoder/configuration or output errors with retry and copy-diagnostics
   actions. The mini player indicates failure. Normal playback UI is preserved.
4. **Empty desktop log export.** Platform log calls now write a sanitized bounded
   process buffer and a real rotating UTF-8 file under
   `%USERPROFILE%\.melox\logs\melox-debug.log`. It retains one previous 1 MiB
   file; log export reads genuine current-process entries. Credentials and
   signed URL paths/queries are removed before storage. Package logs include
   the exact CI checkout revision.
5. **Default-only Java Sound initialization.** Preserve stereo S16LE / 44100 Hz
   for decoder/EQ and try other genuinely supported Java Sound outputs if the
   default cannot open. Partially opened failed lines are closed, genuine
   errors retained, and the chosen output and format logged.

The cache/device changes are real tested defects/improvements. They are **not
yet a confirmed root cause or resolution of the user's physical Windows report**.

## Font provenance and lyric rendering

- `fonts/mi_lan_pro_vf.ttf` is byte-identical to the pinned Android git blob:
  19646728 bytes, SHA-256
  `15fc7accb0afcbb7cf3bbf776536a37398e552f082bb13af70602bf1d7ed676f`.
- Both Compose lyric text and native timed glyphs use the pinned CSS-to-`wght`
  mapping. The exact mapping/XML provenance is in
  `evidence/lyrics-font-source-audit.json`.
- Source and all packaging layers now verify this font alongside the existing
  five immutable legal/default-string resources. Existing hashes are retained.
- Actual DAMIDAMI lyrics are replayed at phone and desktop viewports. Without
  the user's screenshot or fresh same-content pinned Android frames, the report
  of scrambled lyrics remains unresolved; no pixel or dynamic `MATCH` is claimed.
- Fresh Android enumeration in this work returned zero connected devices.

## Verification boundaries

- Automated regressions, production replay, independent consumed PCM readback,
  Windows CI/native decode, and physical Windows speaker acceptance are separate.
- `tools/capture_damidami.py` covers real anonymous Search, unified Search,
  lyric rendering, and a genuine host output-error scene. `output-error` does
  not inject a failure; software replay is explicitly labelled.
- Manual `DAMIDAMI production playback` workflow exercises real Windows FFmpeg
  and production rendering without an account. It uploads screenshots,
  sanitized logs and PCM metrics, never raw online recordings.
- Local regressions pass: **182 tests / 36 suites / zero failures, errors or
  skips**. Packaged native MP3/FLAC/M4A and authenticated local HTTP decoding,
  real single/dual EQ PCM checks, and six exact resource hashes pass.
- Live ordinary Search and NetEase-only unified Search pass independently with
  consumed non-silent PCM. Actual word-timed lyrics load at 427×924 and
  1100×780; settled visible lyric row geometry has no overlaps in these frames.
- The headless host genuinely rejects Java Sound output. The actual output
  error is visible in the production player, retry opens the real output again,
  and the close action works. This
  validates failure reporting, not a reproduction of the user's Windows cause.
- Final test/capture/package results are recorded in `evidence/damidami/` as
  they complete. Physical Windows audio and Android pixel equivalence remain
  `BLOCKED` pending direct evidence.
- Windows live same-track run **38416523071 / d5489de failed**: genuine search
  found the original track but anonymous standard playback returned **no URL**.
  No song PCM, FFmpeg stream or speaker initialization was reached; the later
  lyric scene was not executed after the failed assertion. This negative result
  is retained in `windows-replay-d5489de-rejected/`, not counted as successful
  Windows playback. The upstream reason and whether it matches the user's
  installation remain unknown. Standalone Windows native decoder checks passed.
- Additional Linux 150% density replay (1650×1170 px, 1100×780 dp) passes real
  non-silent PCM and settled lyric row geometry. This is a DPI investigation
  frame, not a Windows hardware or Android pixel acceptance.

## Verified Windows diagnostic package

- Source: `d5489de43f2b5064500a5272eded96438c2c9875`.
- Build run: **38416512058 / SUCCESS**; **182 tests / 36 suites / zero failures,
  errors or skips** on Windows.
- Source, JAR, app-image and final ZIP each pass native decoder and exact
  pinned-resource checks, including the 19,646,728-byte Android MiLan Pro font.
  Native MP3/FLAC/M4A and authenticated local HTTP PCM decode pass at every
  layer; real single/dual EQ PCM verification passes.
- Artifact **11823734742** was independently downloaded. Its outer SHA-256
  matches GitHub's artifact digest; inner ZIP CRC, launcher PE, Java Sound DLL,
  packaged FFmpeg PE/hash, diagnostic classes, exact six resources and stamped
  source revision all verify.
- Final `MeloX-Desktop-Windows.zip`: **264542926 bytes**, SHA-256
  `2ff49d4d3aa1404c60d4484c2b3abb003a5a205733e776db732c2a3d3fb7c0da`.
- Download:
  <https://github.com/lladlam/MeloX-Windows/actions/runs/38416512058/artifacts/11823734742>.
- Detailed independent result, retained 36 test XMLs and native/resource check
  JSONs are in `evidence/damidami/windows-ci-d5489de/`. Only diagnostic/evidence
  updates follow this release source; no newer evidence-only revision is used
  to relabel the package's source.
- This is a verified diagnostic/cache/output package. Same-track Windows live
  playback remains blocked by the genuine upstream no-URL result above, and
  the user's physical playback/lyric issue remains unconfirmed pending their
  error/diagnostic and screenshot.

## Pending advanced lyrics settings

The advanced production-UI batch covering 60 persisted keys is preserved in a named local git stash,
paused for these user-reported playback and lyric rendering failures. Its first
pointer replay failed on a toggle after a selector transition; the batch is
not released or behaviorally accepted. The previous inventory remains a gap
inventory, not proof of functional or visual parity.
