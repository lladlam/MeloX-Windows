# Windows PCM write interruption and glyph overlap investigation

Pinned Android remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## Actual Windows report

The user reproduced on Windows 10 with package source `d5489de`. Its genuine
diagnostic shows successful anonymous standard URL resolution, the verified
bundled Windows FFmpeg starting, and the default Java Sound stereo S16LE /
44100 Hz line opening. Later `SourceDataLine.write` returned zero or a
non-frame-aligned count and the player classified it as Output failure. The
old generic message did not record the accepted count or control/line state.
No raw user diagnostic, private account, PID or signed source URL is published.

This differs from the separate Windows CI live-URL failure. Successful native
decoder tests or another host's upstream availability cannot establish the
cause of this physical-output report.

## Confirmed pause/short-write defect

Java SE 17's SourceDataLine.write contract explicitly permits an early, short
return when a line is stopped, flushed or closed. The production pause path
calls line.stop from the control thread. The existing output loop treated any
zero return as failure, including a legitimate interrupted write. Its partial
write loop also failed to wait for pause before retrying the remaining PCM.

References inspected after Context7 quota was exhausted:

- <https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/sound/sampled/SourceDataLine.html#write(byte%5B%5D,int,int)>
- OpenJDK 17u `DirectAudioDevice.DirectDL.write` and `implStop`:
  <https://github.com/openjdk/jdk17u/blob/master/src/java.desktop/share/classes/com/sun/media/sound/DirectAudioDevice.java>.

New regressions reproduce this through the **actual production player and
bundled FFmpeg**, with generated legal WAV data and an explicitly controlled
Java Sound contract endpoint. The baseline reports six targeted tests, **four
failures**, in both ordinary and streaming/AutoMix paths. The real-running
zero-progress negative case passes, demonstrating the test distinguishes a
legitimate pause interruption from actual device failure.

## Correction

- Share the frame-aligned PCM writer between ordinary and streaming output.
- Preserve the exact already-EQ/volume-processed buffer and accepted byte offset
  while paused; resume on the same output with no skipped or duplicated samples.
- A monotonic pause version identifies pause/resume that completed before the
  blocked write returned. Merely checking the current paused Boolean is not
  sufficient for that race.
- Serialize line start/stop with their actual owning playback controls, including
  buffered tail playback. A stopped stale writer cannot restart a paused line.
- Continue cancelling stale stop/seek/session writes rather than reporting them
  against a replacement track.
- A still-current running device making genuinely zero, invalid or unaligned
  progress remains Output failure. Record accepted/requested bytes, offset,
  open/running/paused state, pause version, frame position and buffer availability
  in the genuine error for the user's next reproduction.
- Tests verify byte-for-byte decoded PCM retention, same-output ownership and
  correct end/clock behavior after zero/short interruptions, including quick
  pause/resume. These tests do not establish physical speaker audibility.

This is a confirmed production defect matching the reported failure class. The
user's exact trigger remains **UNCONFIRMED** until their physical reproduction;
it must not be relabelled resolved based only on a controlled contract test.

## User glyph screenshot

The supplied Tiny Giant screenshot shows overlap **within words/characters**,
not merely intersecting lyric rows. Previous settled row-spacing checks could
not detect that defect. Compose measuring and independent native glyph drawing
must be compared at actual Windows DPI scales and source weights, including
the Heavy default. The original pinned MiLan Pro bytes remain unchanged.

`auditLyricRaster` exercises the real production glyph compositing path with
short generated English/CJK text at 100%, 125%, 150%, 175% and 200% density.
It compares unlifted individually drawn glyphs to that same production shaped
layout and retains actual raster frames/metrics. This is a glyph-layout
diagnostic fixture, with no fake song, provider, stream or playing state.
Android pixel/motion acceptance and the user's physical-output acceptance
remain BLOCKED until their respective direct evidence is available.

## Shaped-row glyph correction

The independent **Windows Server 2025** baseline at `9c8f6bd` confirms that
this is a real desktop discrepancy: ordinary unlifted Heavy text differed from
its own Compose-shaped paragraph by up to **25.22%** normalized absolute ink
difference. Bold matched on this Windows host. The baseline's successful Build
only means its tests and resources passed; the diagnostic raster had not yet
been an enforced gate, and its glyph geometry is rejected.

The correction measures and rasterizes a row with the same Compose paragraph
and exact pinned MiLan Pro font. Individual glyph regions come from that
immutable shaped-row mask, preserving shaping, variable weight, fallback,
wrapping and the original source positions. The existing per-glyph reveal,
lift, scale, shake, glow and soft blur still act on each region. The mask is
rebuilt only when the row layout changes and explicitly released with the
composition; no paragraph re-layout or per-glyph surface occurs on a clock tick.

After correction, the same **30 Linux cases** have maximum normalized absolute
ink difference **0.0652%**. The raster diagnostic now enforces a 1% upper bound
and runs in Windows CI. This checks the internal consistency of the desktop
compositor; it is not a comparison against Android screenshots.

Real Tiny Giant (`netease:3334421408`) was also replayed through its actual
online provider, native bundled FFmpeg, production lyrics and timed-glyph
compositor at 175% density. Physical-coordinate pause/seek into the opening
“Like in me” / “Like, like, like, yeah” rows and buffered decoder lifetime
checks passed. Consumed software-endpoint PCM is non-silent, row geometry is
retained, and actual production frames are archived. No online PCM recording
is published. The user's Windows 10 rendering and speaker output still require
their reproduction with the final package.

Additional genuine FFmpeg/player tests cover stop and seek releasing a paused
pending write without stale end/error callbacks, and unexpected device closure
still producing an Output failure with `accepted=0` / `open=false` diagnostics.

## Enforced Windows rejection and pixel-partition follow-up

Windows Build **38504096447 / ea18d38** passed all **194 tests / 36 suites**, native
FFmpeg source checks and real EQ PCM. The newly enforced glyph check correctly
rejected it: maximum normalized absolute ink difference **1.5407%**, above the
unchanged **1%** bound. No app-image or distribution ZIP was created. Its actual
test/raster evidence is retained in `windows-rejected-ea18d38/`.

Inspecting the actual Windows raster difference reveals vertical cuts at the
fractional character advances, plus missing trailing `h` ink past its advance.
Strict fractional image crops were discarding edge pixels. The follow-up tiles
each complete shaped row with nonoverlapping **integer pixel partitions**,
preserves trailing ink overhang, and retains original measured bounds for the
animation pivot, source timing and highlight math. Duplicate shaping-cluster
origins share one partition. This corrects the desktop compositor rather than
relaxing the acceptance gate.

The audit now additionally covers ligatures, a combining accent/surrogate-pair
fallback glyph, and explicit multirow text. All **60 Linux cases** reconstruct
the unlifted shaped paragraph with **zero pixel difference**. Final Windows
results and distribution integrity must be accepted independently.

The preceding actual Tiny Giant motion replay at `ea18d38` also passed: **41
state/frame scenes / 20 assertions**, including physical resume, real advancing
lyric positions **2093–8708 ms**, six playing lyric raster frames and subsequent
pause. These are actual desktop PCM-clock frames; they are not pinned Android
dynamic acceptance and do not override the later Windows raster rejection.
