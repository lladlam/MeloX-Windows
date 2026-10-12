# Unified Search — approved sources and real provider identities

Pinned source: Android `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`, `ui/search/SearchScreen.kt:287–289,370–449,1138–1177`.

## Closed business-wiring gap

The desktop Services screen persisted `unified_enabled` and `unified_sources`, but SearchScreen never read them and only searched its selected single provider. Search now observes the existing provider-selection store, calls the existing `UnifiedMusicService` with only the locally approved source set, displays source-owned aggregation recommendations and partial failures, and plays the actual recommended `MusicTrack` through `ProviderPlaybackCommands`.

The same existing persisted names/formats are retained. Store setters publish a process-local revision so returning to a retained Search page applies changes immediately. Turning the global switch off returns to the selected provider. The aggregation whitelist UI excludes sources the existing selection store cannot admit, rather than presenting a checkbox whose persisted selection is silently discarded.

The approved whitelist is also honored when it contains a **single** source. Android's pinned Search branch only consumes its whitelist when its size is greater than one; for example, a Local-only whitelist with NetEase as the current provider otherwise sends the query to NetEase. Desktop uses the one approved source in that case instead of silently ignoring the explicit scope. This intentional source-side gap correction is not a pixel-parity claim.

## Result and lifecycle handling

- Approved providers execute concurrently; a failed/unsupported provider has a source-labelled inline error while other real results remain usable. Empty results have an explicit state.
- Both the implementation interface and declared Search capability are required. An older incomplete adapter cannot turn its unimplemented empty return into a claimed successful search.
- Per-provider cooperative coroutine deadlines report the timed-out source; cancellation of an obsolete search is rethrown rather than publishing a fake provider failure. Existing single-provider/catalog/link branches also preserve cancellation. New queries show loading before debounce instead of displaying old results as if they matched the new text.
- Provider-native duplicate IDs are eliminated; a response containing another provider's resource ID is rejected/reported rather than relabelled as the requested source.
- Existing `TrackAggregation` version/recommendation rules are reused. Studio/live variants retain their native identifiers; recommendations retain provider metadata and availability. No fake provider or fixed audio analysis is introduced.
- Source labels are visible on unified rows. The real row click supplies the original resource ID/provider metadata to the existing resolver/controller. It does not replace Local or other sources with a NetEase identity.

Some older provider HTTP implementations still use blocking transports; the coroutine deadline does not establish prompt socket cancellation for every provider. Authenticated account behavior, full source-native downloads/actions/catalog surfaces, and same-content Android pixels remain **PARTIAL/BLOCKED**. Aggregate library/recommendation acceptance and automatic alternate-candidate playback are not inferred from song-search wiring.

## Verification

`UnifiedSearchLifecycleTest` uses provider callback fixtures only in the test source set to verify concurrent whitelisting/partial failure, timeout child release, obsolete-search cancellation without publication, duplicate/foreign ID handling and distinct live/studio versions. Production providers and URLs stay genuine.

Final full result: **130 tests / 22 suites / zero failures, errors or skipped**, including six Unified lifecycle/capability regressions. All XML is archived in `evidence/unified-search-tests/`.

Production UI capture imports legally generated WAV files through `LocalMediaScanner.scanAll`, searches the real persisted Local repository through the real aggregator, changes queries through the editable production field, and selects the original Local track through the genuine controller/native FFmpeg decoder. The explicit software PCM endpoint is labelled by the validation harness; it is not audible Windows hardware acceptance. The capture's source/whitelist/scanner options are explicit validation inputs, never formal application defaults or synthesized online API results.

`unified-search-local-ui/`: 6 state scenes / 11 passing assertions / 15 dynamic frames. The 44.1kHz stereo PCM readback contains 207,777 real frames, RMS 4242, selected 1000Hz amplitude 5999 and other indexed track's 2000Hz amplitude 0.014 (`unified-search-pcm-readback.json`).

`unified-search-scope-ui/`: 13 light-theme state scenes / 13 assertions. Actual Services switch toggling off/on takes effect on returning to Search; unavailable Jellyfin scope is shown inline while Local stays usable, and an unmatched query shows empty state. This replay proves the genuine unavailable-capability boundary, not Jellyfin protocol support. That adapter/login/session gap is recorded for the next provider batch.

`unified-search-validation.json` records source/fixture/final JAR hashes and the minor source-timing difference between the two captures. The final Linux package passes native decoder format/authorized HTTP checks in `unified-search-linux-packaged-decoder.json`.

`unified-search-online-ui/`: another 4 state scenes / 8 assertions with the final batch source, real NetEase + Local approved scope and the existing fixed online provider client. The generated query returns only the actual Local row without an online error; selecting it retains native Local identity and produces real software PCM. This is not proof of authenticated online results/playback, and network packets were not instrumented.
