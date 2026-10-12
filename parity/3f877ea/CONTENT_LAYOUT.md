# Content layout and persisted Library navigation

Android truth: `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## Source and observed gaps

- `ui/settings/SettingsScreen.kt:2588–2737`: dedicated TabLayout route, dock/feature placements, up/down sorting, home-section visibility/order, remembered Library page/default page, launch page and full-swipe action.
- `ui/library/LibraryScreen.kt:227–241,374–389`: per-source remembered-page keys and capability/feature fallback.
- `ui/discovery/MeloXDiscoveryScreens.kt:338–346`: QuickActions is one ordered home block.
- Windows previously had nine Settings routes, feature placements on the Features page, a fixed initial Library page with no persistence, all NetEase Library pages visible regardless of module settings, and QuickActions rendered before every ordered data block.
- Two source-side gaps also exist at this pinned SHA: the configured `library_default_page` has no Library consumer, and `MeloXApp.kt:488–490` immediately rejects a hidden module page opened by a home shortcut. Windows now closes these visible-operation gaps rather than exposing controls that silently do nothing. This does not establish pixel parity with Android.

## Implementation

- `settings/MeloXContentLayout.kt` shares the actual dock/page/feature policy between settings and presentation. Settings remains reachable even when all primary tabs are hidden; Search remains available. Enabled module destinations can open from shortcuts independently of dock placement.
- `screens/TabLayoutSettings.kt`: real toggles, enabled/disabled endpoint move controls, persistent tab/home ordering, module placement and capability-filtered default Library page. Existing `melox_app_settings` names/formats are preserved.
- `screens/MeloXLibraryScreen.kt`: reads/writes `library_last_page` and `library_last_page_<source.storageValue>`; observes feature/placement changes; falls back to a visible configured page. Turning remembering off uses the default without overwriting saved provider history. Local remains Songs-only and other providers retain their existing page capabilities.
- `screens/HomeScreen.kt`: QuickActions participates in the saved section order. Optional home module links still open the real module page when its dock placement is disabled.
- Shortcut replay also uncovered focus loss when the clicked Home card was removed by the page transition. The App Shell now restores its resident Escape target on non-Search root-page changes, preserving Search's own text focus. The real cloud destination then returns to Home through the existing back hierarchy.
- `SettingsScreen.kt`: exposes launch page and full-swipe action to existing consumers. Deep-linked Messages settings opens the existing genuine Messages UI instead of claiming private messages are unavailable. Scrollable settings content reserves the existing bottom-chrome clearance.
- Library segment lens reads the current theme rather than assuming every desktop scene is dark.
- Full regression uncovered an independent Smart Queue issue: its eligible-candidate snapshot excludes analysis failures, and the disable path used that snapshot to restore the original queue. `MeloXSmartQueueBuilder.takeRemaining()` now restores **all unconsumed** entries, including failed analyses, and invalidates the old candidate generation. Smart selection/publication and disable/restoration use one mutation lock. The substantive failed-analysis restoration regression is retained alongside native PCM candidate-order tests.

## Evidence boundaries

`ContentLayoutRestorationTest` exercises real preference persistence/reinitialization, per-provider history, hidden/default page resolution, remembering-off semantics, home ordering, and shortcut reachability independent of dock placement. It does not substitute for signed-in Provider acceptance.

Production ImageComposeScene replay exercises the settings controls with pointer clicks/wheel input and captures actual dock order, toggle states, reorder behavior and return navigation. Editing the settings search uses the production field's accessibility SetText action. Capture homes are isolated. No service response or user music is synthesized into the formal application path.

`evidence/content-layout-ui-final/` records 15 state scenes / 14 passing assertions / 42 dynamic frames. The initial dock order is restored from earlier genuine UI-written preferences in the same isolated home; the replay then toggles Cloud dock/Library placement, moves Home/QuickActions, changes Library remembering/default and returns through Escape. Final labels and home grouping follow the pinned `values-zh-rCN/strings.xml` and source home-settings group rather than invented labels. Requested and actual frame times are retained separately.

`evidence/content-layout-home-shortcut/` adds 4 scenes / 7 passing assertions: physical horizontal wheel scrolling to the actual Home Cloud card, opening the genuine missing-login Cloud state without a Cloud dock tab, and Escape back to Home. The final App Shell focus fix is exercised by this replay. Source hashes for the earlier settings run and final packaged run are recorded separately in `content-layout-validation.json`.

Overall **PARTIAL**. Pinned Android same-content reference pixels/dynamic frames and authenticated NetEase/provider Library replay are still unavailable. New route availability alone is not `MATCH`, and the remaining Settings routes require their own business implementation.
