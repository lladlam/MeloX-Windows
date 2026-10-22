# Mobile 0.6.5 alignment — local replay evidence (8f9342e)

Source baseline: Android `53244b9d6b21d7e3a609e8cb11876efef70f218b` (`versionCode 24`). These replays use the production `ImageComposeScene` / `MeloXApp`, not mock screens.

## Home / details

- `02-loaded.png` — mobile Home quick actions and sections.
- `05-hot-loaded.png` — public 热歌榜 loaded from the real NetEase API and opened in the canonical song-list detail; the Home page is retained underneath.
- `09-back-again.png` — second open/close cycle via Escape; verifies keyboard focus returns to the root after the detail overlay closes.
- `12-daily-error.png` — without a session, 每日推荐 reports “请先登录网易云音乐”; no mock list or fake playback is produced.
- Counts: **13 scenes / 13 assertions** (`build/mobile-home-v45/capture.json`, not copied).

## Player / mini-player

- `04-open-frame-0.png`, `04-open-frame-500.png`, `04-open-frame-820.png` — explicit animation-clock samples at 0/500/820 ms (not wall-clock sleeps). Shell bounds, corner radius and expansion progress advance frame by frame.
- `05-opened.png` — fully expanded 427×850 player.
- `07-lyrics.png`, `08-queue.png` — real lyric/queue pages while paused.
- `13-close-frame-500.png`, `13-close-frame-820.png` — 22dp single-peak sink in the final 38% of the close; bounds return exactly to the mini target.
- `14-closed.png` — real mini player after close, audio still paused.
- `motion-geometries.json` — production semantics bounds/state for the opening shell.
- Counts: **115 scenes / 22 assertions**; generated WAV→PCM wrote more than 44,100 frames (`build/mobile-player-v45/capture.json`, not copied).

## Desktop layout regression

- `layout-v45-verification.json` — targeted `controls` + `lyrics-light` verification: **41 scenes / 82 assertions PASSED**, including 700dp bottom player, 199dp sidebar and 360dp lyrics inspector geometry.
- The full Windows CI runs all layout modes, settings, packaging and native window audits.

## Boundaries

- Software PCM is not audible-hardware acceptance; offscreen pointer events are not physical touch.
- The native `auditWindowChrome` is **UNSUPPORTED** on this Linux host (no positive-size display). Windows CI must exercise the real AWT window and caption.
- Playlist/artist hero shared-element morph, landscape/dual-pane player, complete multilingual strings and third-party detail pagination remain listed as incomplete in `MOBILE_UI_AUDIT.md`.
