# Windows memory growth follow-up

Pinned Android acceptance base remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## User evidence (2026-10-14)

The user accepted the `797c3be` font fix (“可以了。继续迁移其他功能”). They
then reported the Windows MeloX executable at **549 MB just after startup** and
**1431 MB after playing for a while**. Duration, number of tracks/pages, Windows
memory-column definition and process breakdown have not yet been supplied.
These readings justify investigation but do not independently identify all
retained allocations or prove a particular native leak.

## Confirmed source issues and changes

- `MeloXArtwork.kt` used a process-global **unbounded** `ConcurrentHashMap` of
  decoded images. An image remained strongly reachable after its UI disappeared.
- The player backdrop had another 32-image cache, allowing the same artwork
  to remain decoded in both caches. A count-only limit does not bound bytes.
- Both now share one access-ordered cache with **128-entry / 64 MiB budget**.
  Images are conservatively charged eight bytes per pixel. Oversized images
  remain displayable but are not cached. Account-sensitive provider cache keys
  are retained. Concurrent publications reuse the existing shared instance.
- Cache and each visible composable hold separate resource leases. Eviction
  releases only the cache's lease; **visible images are never closed early**.
  The last owner closes native pixels immediately. Duplicate decodes and
  canceled IO publication also close unneeded native pixels, without forced GC.
  User cache-clear also drops decoded artwork and palette cache references.
- Each lyric row already remembers its one current shaped paragraph. Its
  additional 64-layout `TextMeasurer` LRU is disabled, avoiding history retention
  on resize/settings changes. Row-mask rendering and pinned font bytes are retained.
- Aggregate diagnostics report Java used/committed/max heap, non-heap, direct
  buffers and OS resident bytes. Windows also reports private committed bytes
  via `GetProcessMemoryInfo`. These are different measures, not interchangeable.
  Startup and 60-second samples include decoded-cache occupancy in bounded
  process logs; log export and playback-error copy include current memory.
  No track names, URLs or account credentials are added to these measurements.

## Verification boundaries

`auditArtworkMemory` is a **validation-only generated-pixel** stress of the same
production cache: 2,048 distinct 512-square RGBA images (2 GiB cumulative pixel
allocation), count/byte accounting, duplicate reuse, and post-warmup resident/live
used-heap samples without forced GC. It requires real Linux/Windows process
measurements and catches unavailable Windows bindings. Production does not force GC.

`tools/capture_playback_memory.py` runs real Tiny Giant provider/decoder playback
with twenty pointer-driven player/lyrics disposal cycles and per-scene memory
measurements. Validation PCM recording overhead is reported separately. One
track on Linux does not establish a multi-song Windows working-set plateau.

Local initial ownership fix validation: **202 tests / 39 suites / zero failures,
errors or skipped**, **60 compositor cases / zero difference**. The no-forced-GC
2,048-image stress retained the same 32-image / 64 MiB conservative cache budget;
Linux resident samples settled at **121–127 MiB** after warmup (net +5.77 MiB
from batch 2 to batch 7), rather than tracking the 2 GiB cumulative allocation.
This is image-cache lifetime evidence, not a 127 MiB promise for the full app.

The real post-lease Tiny Giant replay passed all twenty player disposal cycles,
paused lyric geometry and real non-silent consumed PCM over approximately
138 seconds. **Full-process memory remains high:** Linux resident memory was
292.47 MiB initially, 519.04 MiB after warmup and 894.09 MiB at the final
settled capture, while the artwork budget stayed at 23.65 MiB (three images).
The final Java heap was 123.94 MiB used / 228 MiB committed; non-heap was
98.92 MiB; measured Skia glyph cache was only 1.87 MiB and resource cache zero.
The validation PCM recording retained 20,483,768 bytes. Native objects are
instrumented in the replay report; these measurements **do not prove the whole
549→1431 MB user symptom is fixed**. Continue profiling native renderer/text
ownership and allocator retention, using the new Windows per-minute breakdown.
Reports are under `evidence/windows-memory/`; audio recordings stay local.

Windows run `38578024876` at exact feature source
`768445faedc17c7ca25bdeee5c8eb5d9cbfea5c2` passed 202 tests / 39 suites, the
ownership stress, raster/font gates and all packaging-layer checks. The image
stress working set settled at 123.37–125.12 MiB from batch 2 to batch 7;
private commit was 398.32–399.34 MiB. Used Java heap was 7–9 MiB while its
committed heap was 256 MiB: private commit is not the same as resident memory.
The first run's evidence upload omitted the new `artwork-memory` subdirectory.
Its eight actual printed samples and PASS line are retained/reconstructed from
the exact CI step log, explicitly labelled, rather than represented as a
downloaded audit JSON. The workflow now includes that directory for future runs.

The packaged application writes its 60-second memory samples to
`%USERPROFILE%\.melox\logs\melox-debug.log`. The prefix is `[INFO MeloXMemory]`.
The log-export API exists, but there is currently no general settings export
button; do not instruct the user to use a nonexistent UI control.

Fresh Windows CI and independently downloaded package evidence are required
before handing out a new distribution. User-machine 549→1431 MB symptom
resolution remains **PENDING** until that exact distribution is retested.
Neither memory stress nor playback establishes Android pixel/motion MATCH.

The exact `768445f` package has now been independently downloaded and verified:
artifact `11901753358`, **307,942,342 bytes**, final ZIP SHA-256
`3689043da3fa5ad9ccd90ecd13311b5db6dba74750764175b570dde9c0ecde5b`.
Outer artifact GitHub digest, ZIP CRC, Java Sound DLL, `java.management`,
Windows FFmpeg PE/hash, six pinned resources, all eight static font bytes,
metrics resource, ownership/diagnostic classes and exact revision stamp pass.
Link: <https://github.com/lladlam/MeloX-Windows/actions/runs/38578024876/artifacts/11901753358>.
Its full-source revision remains `768445faedc17c7ca25bdeee5c8eb5d9cbfea5c2`;
later evidence/migration commits do not relabel this distribution. The failed
download quota attempt was recovered after clearing only regenerable isolated
Skiko extraction caches; final success used the full digest-verified download.

The later advanced-lyrics delivery at exact
`d186e0c62941d2726c2184b8399d97c861af4195` retains these memory repairs and is
independently verified (run `38592934356`, artifact `11909196579`). Its uploaded
native audit JSON is downloaded directly, without step-log reconstruction:
warmup batch 2 resident 129,503,232 → final batch 7 131,637,248 bytes; private
commit 417,247,232 → 418,942,976 bytes. Its complete 60-key Windows UI/reload gate
also passes. Neither result resolves the user's full-player 549→1431MB report;
the exact delivery details are in `ADVANCED_LYRICS_SETTINGS.md`.

The current Skyline/native-power delivery at exact
`f3e3a83d63d0d12c36723aee974ebf3dc2a530d1` also retains these repairs and passes
independent package collection (run `38653215039`, artifact `11936083521`).
The directly downloaded no-forced-GC artwork audit again loads 2,048 images / 2GiB
cumulative pixels and retains 32 entries / 64MiB budget. Batch 2→7 resident is
128,532,480→130,310,144 bytes (+1,777,664); private commit is
415,940,608→417,558,528 bytes. These are isolated cache-stress measurements,
not evidence that whole-player renderer/text growth or the user's reported
549→1431MB symptom is resolved. See `SKYLINE_SETTINGS_POWER.md` for the exact
download, final ZIP hash and remaining physical acceptance boundaries.

## Preserved migration work

The restored advanced-lyrics batch is retained in stash:
`WIP restored advanced lyrics controls v7; paused for user 549MB startup to 1431MB memory report`.
The earlier original advanced-lyrics stash is also retained. After the user
requested continued migration, the full 60-key control replay and separate-JVM
reload passed, with real Tiny Giant consumer replay. See
`ADVANCED_LYRICS_SETTINGS.md`; Windows delivery gates and independent collection
now pass for both the advanced-lyrics and Skyline distributions. The v7
failed reveal remains historical evidence, not the current control result.
