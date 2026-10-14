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

Fresh Windows CI and independently downloaded package evidence are required
before handing out a new distribution. User-machine 549→1431 MB symptom
resolution remains **PENDING** until that exact distribution is retested.
Neither memory stress nor playback establishes Android pixel/motion MATCH.

## Preserved migration work

The restored advanced-lyrics batch is retained in stash:
`WIP restored advanced lyrics controls v7; paused for user 549MB startup to 1431MB memory report`.
The earlier original advanced-lyrics stash is also retained. The latest replay
passed setting changes through the last Apple-style slider, then failed to
reveal the style selector. It is not released as a 60-key accepted batch.
