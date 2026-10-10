# Behavior-level upstream delta (94e17c9 → 3f877ea)

The exact 7 commits and SHA-256 for all 24 changed Android files are recorded in `evidence/source-lock.json`.
Paths below are relative to Android `android/app/src/main/kotlin/com/lladlam/melox/`.

| Changed source | Source behavior | Windows implementation / remaining difference |
|---|---|---|
| `core/account/SecureSessionPreferences.kt` | Recover unreadable encrypted sessions; retry key/store; memory fallback rather than startup crash | Current-user DPAPI has no app-owned Android master key. `PreferenceFile.secure` quarantines unreadable bytes, rebuilds with verified encrypted atomic write, otherwise process-only memory. Native DPAPI validation BLOCKED. |
| `core/provider/youtubemusic/YouTubeSessionStore.kt` | Route store through recovered secure preferences | Same recovered cached secure abstraction; real account session acceptance remains PARTIAL. |
| `core/model/SearchSong.kt` | Nullable album/artist IDs | Same nullable model additions, old construction/records remain compatible. |
| `core/music/provider/MeloXLegacyUiBridge.kt` | Expose album/first artist IDs in legacy UI | Bridge derives IDs from actual provider track references; routes never pass foreign numeric IDs to Netease. |
| `core/network/NeteaseSearchClient.kt` | Preserve IDs from `al/album`, `ar/artists` | Added both IDs to existing parser. |
| `core/network/NeteaseUniversalSearchClient.kt` | Preserve IDs in universal song parser | Added to both SearchSong and MusicTrack search paths; desktop search uses shared legacy bridge. |
| `core/network/NeteaseCollectionDetailsClient.kt` | Detail-song IDs survive album/artist calls | Added to detail parser, consumed by real artist/album/action paths. |
| `core/network/NeteaseSocialExtrasClient.kt` | Social/history songs carry collection IDs | Added IDs to existing parser; existing API operations retained. |
| `core/library/NeteaseLibraryClient.kt` | Library song IDs | Added to existing parser; real response duplicate-key fix also covers library transport. |
| `core/library/NeteaseHomeBlockParser.kt` | Song block first artist and full-song album IDs | Added to partial and full-song paths. |
| `core/library/NeteaseLibraryCache.kt` | Cache round-trip new IDs | Nullable JSON keys on write, positive-only nullable legacy decoding. |
| `core/download/MeloXDownloadStore.kt` | Download song persistence retains new identity | Index write/read fields added; existing audio/lyrics/artwork transfers preserved. |
| `core/recognition/SongRecognitionClient.kt` | Recognized songs expose album/artist IDs | Windows client still returns empty lists. Existing missing recognition must be implemented; not represented as migrated. |
| `playback/MeloXPlaybackQueueStore.kt` | Clear explicit empty queue; store/restore original album and artist extras | Explicit desktop clear/empty install clears durable queue, closes active audio and isolates late callbacks. Startup-empty save remains guarded because desktop restore is asynchronous. Queue IDs retained; platform-independent metadata used. |
| `playback/PlaybackCommands.kt` | Metadata extras carry original collection IDs | Real selected MusicTrack album/artist refs carry IDs; generation/seek/drain/request lifecycle preserved. |
| `ui/player/MeloXPlayerUi.kt` | Last playback and UI model retain IDs | Desktop queue persistence supplies last selected metadata; separate Android LastPlayback schema is not copied over existing Windows keys. Individual last-song-only setting still requires parity review. |
| `ui/player/MeloXIOSNowPlayingSharedHost.kt` | Pass original metadata collection identity to action song | Shared bridge and actual current queue item already supply IDs. |
| `ui/player/MeloXNowPlayingActionsSheet.kt` | Direct artist/album callbacks | Shared desktop actions now dispatch real collection requests; provider fallback launches source-aware query. |
| `ui/player/MeloXSongActionsOverlay.kt` | Direct IDs when present; search fallback otherwise | Desktop action menu supports both paths. Other action-menu layout and account write acceptance remain PARTIAL. |
| `ui/MeloXApp.kt` | Global artist/album overlays, source-sized page transitions/combined backdrop and predictive return | Collection overlay and keyboard return implemented. Search launches switch real provider context. Combined backdrop, complete resident detail hierarchy and exact predictive/shared transitions are not accepted yet. |
| `ui/collection/MeloXArtistDetailScreen.kt` | Hero, five-row hot-song columns, studio/EP/collab/live album strips, grid expansion, info/follow, nested album/back | Real API + source layout/dimensions implemented. Physical replay exposed and fixed duplicate-key parsing and modal Escape focus. Current Android pixels unavailable. |
| `ui/library/LibraryScreen.kt` | Song-action direct navigation, selectable artwork rows, hidden/reveal search layout and collection changes | Shared actions/playlist settings/filter implemented. Complete pinned Library collection/detail visuals and exact search drag/reveal remain PARTIAL. |
| `ui/settings/MeloXSettingsPreferences.kt` | Persist/recompose two playlist display switches | Same keys/default false, initialize in critical/full runtime and immediate setters. |
| `ui/settings/SettingsScreen.kt` | Expose album artwork and hidden-search preferences | Windows General settings toggles feed real row/filter behavior; full 20-route gap remains. |

## Additional actual runtime correction

Real artist `6452` response repeats `followed`, which Android accepts and desktop org.json rejects. `NeteaseResponseJson.kt` normalizes the parsed object map (last key wins, numbers/escaped strings/nested data retained); existing EAPI/WEAPI/search/library/quality/operation response parsers use it. Regression tests cover duplicate fields, large IDs, nested objects and malformed documents. The initial artist screenshot was an error-only page despite tag assertions, so it was rejected as successful content evidence.
