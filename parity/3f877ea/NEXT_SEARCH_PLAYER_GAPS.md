# Next search/player work after a07e1be

Pinned acceptance base remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.
These are inspected gaps, not new acceptance results.

The user accepted the `797c3be` font symptom fix and requested continued migration.
The later memory report remains open in `WINDOWS_MEMORY.md`. Advanced lyrics
controls have now passed all 60 canonical keys, separate-JVM persistence and
real Tiny Giant consumer replay; see `ADVANCED_LYRICS_SETTINGS.md`. Exact Windows
package gates remain pending. The inventory below is the historical inspected
gap, not the current production-UI key count.

## Confirmed production settings exposure gap

Pinned `ui/settings/SettingsScreen.kt:1700–1897` has **60 distinct lyrics setting
keys**. Desktop `SettingsScreen.kt` plus `AlternativeLyricsSettings.kt` exposes
**9** of those keys. **51** still lack that production UI exposure. The exact
inventory is `evidence/search-player-next-lyrics-control-gaps.json`.

Prioritize these source groups:

1. Rendering quality, automatic selection, ordinary-LRC pseudo timing, long-press
   share, interlude countdown, manual-scroll follow delay and reduce motion.
2. Translation/romanization size, opacity and focused/all-lines scope.
3. Global advance, whether it applies to authored word highlighting, refresh
   rate, font scale/weight and character/word lift mode.
4. Long-tone detection/glow/threshold/stretch, spacing and depth blur.
5. Focus scale, inactive opacity, interface auto-hide and scroll-hide threshold.
6. Cascade/scale bounce switches, timing, elasticity and gradients.
7. Highlight gradient width/reduction; focus position, distance/hidden blur and
   dim amount; cascade delays/catch-up/settle and snap threshold.

Existing runtime setters and candidate consumer paths are recorded in the JSON.
Reference scanning does not establish behavior: follow the actual shared
word-rendering settings/presentation path before calling a control functional.
Preserve canonical keys, source choices/defaults and authored timing. Verify
actual UI input, persisted reload and changed production rendering with real PCM.

## Other confirmed next checks

- Skyline's 17 source settings in `SettingsScreen.kt:1091…` now have production
  controls, exact reset and four-process pointer/persistence evidence. See
  `SKYLINE_SETTINGS_POWER.md`; fresh Windows delivery gates are pending.
- `MeloXIOSNowPlayingSharedHost.kt:130–145` keeps the Android view awake for
  explicit Skyline and Disabled/Player/Lyrics/HiddenLyricsInterface scopes.
  A real Windows same-thread display/system-idle request is now connected to
  those scopes and direct window foreground/iconify/disposal events. Native
  Windows CI ownership checks and physical Windows idle/sleep acceptance remain
  required; Linux reports unsupported rather than simulating power success.
- Complete provider-native action capabilities and favorite-state reads; the
  common overlay currently handles NetEase IDs but full source-specific actions
  still require work. Provider bridge negative IDs must remain stable.
- Complete Search shared-element/overlay/back animations and source catalog
  details; do not upgrade current ImageComposeScene evidence to Android parity.
- Test genuine authorized online playback and podcast programs. Public track
  185670 returned no playable non-preview source; programs returned -462/login
  required. Retain those blocked outcomes, including successful error/queue UI
  replay, without treating them as successful audio/program retrieval.

Physical Windows audio/native real-account DPAPI and same-content pinned Android
pixel/motion acceptance remain BLOCKED. Fresh Android enumeration on 2026-10-13
still reports zero connected devices.
