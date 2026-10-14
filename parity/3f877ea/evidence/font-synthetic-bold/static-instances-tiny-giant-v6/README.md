# Axis-frozen static instances: real Tiny Giant replay (v6)

Source revision: `b95512586a2b30b8dbbcda42d10acc7b36a9985b`.
Track: `netease:3334421408` (Tiny Giant 小巨星), real provider/FFmpeg PCM.

- `05-lyrics.png` at position 23524 ms — the user's reported line
  "Everything that you know will be changing" (TTML 21.781–25.569 s).
- Main line (Heavy), accompaniment "(You'll like it, you love it)" and CJK
  translation "你熟悉的日常都将重启" render with healthy letter spacing;
  no touching or overlap. The grey trailing glyph is the timed word-progress
  highlight, not a defect.
- `layout.json`: `settled production lyric rows do not overlap` = PASSED.
- `pcm-readback.json`: real released PCM consumed, non-silent = PASS;
  hardware audibility BLOCKED (no physical Windows 10 listening).
- Android pixel acceptance BLOCKED (no pinned Android same-content capture).

This is Linux-hosted production rendering evidence, not Windows 10 physical
acceptance; the Windows build gate is `auditFontMetrics` on Windows Server.
