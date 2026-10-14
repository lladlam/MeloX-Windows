# Axis-frozen static instances: real Tiny Giant replay at the delivered revision

Source revision: `797c3be4a64e3f9bef536e1cb2b26654c16f47d7` — the exact
source of delivered Windows package run `38559314976`, artifact `11891553587`.
Track: `netease:3334421408` (Tiny Giant 小巨星), real provider/FFmpeg PCM.

- `05-lyrics.png` at position ~23.5 s — the user's reported line
  "Everything that you know will be changing" (TTML 21.781–25.569 s).
- Main line (Heavy/CSS 900), accompaniment "(You'll like it, you love it)"
  and CJK translation "你熟悉的日常都将重启" render with healthy letter
  spacing; no touching or overlap. The grey trailing glyph is the timed
  word-progress highlight, not a defect.
- `layout.json`: `settled production lyric rows do not overlap` = PASSED.
- `pcm-readback.json`: real released PCM consumed, non-silent = PASS;
  hardware audibility BLOCKED (no physical Windows 10 listening).
- Android pixel acceptance BLOCKED (no pinned Android same-content capture).

This is Linux-hosted production rendering evidence; the delivered Windows
package is additionally gated by `auditFontMetrics` on Windows Server 2025
(120/120, paragraph vs HarfBuzz within 0.0002 px, see FONT_SYNTHETIC_BOLD.md).
The album art differs between replays because cover fetches are network-loaded;
it is unrelated to the font check.
