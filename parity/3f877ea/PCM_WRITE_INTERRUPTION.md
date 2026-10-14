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
