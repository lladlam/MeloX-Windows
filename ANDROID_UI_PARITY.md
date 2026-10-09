# Android UI parity checklist

This is a checklist, not a claim that the UI matches. Every box stays unchecked until a side-by-side compare at the reference viewport.

Reference source is Android main `94e17c920bf554966584955afa2bd2ee92580a8b`. The independently built reference package is `com.lladlam.melox.android.parity94e17c9`, at 1280×2772 px / density 520 (3.25), fontScale 1.0. Explicit crop and density normalization are required for desktop comparison; historical `.adb_compare` captures are not acceptance for this commit.

Current numerical mapping and capture commands: [`parity/94e17c9/UI_MOTION.md`](parity/94e17c9/UI_MOTION.md). All migrated UI remains PARTIAL pending actual same-scene evidence.

Android root: `MeloX-Android/android/app/src/main/kotlin/com/lladlam/melox/ui/`
Windows root: `MeloX-Windows/desktop/src/desktopMain/kotlin/melox/ui/`

## App Shell

页面：App Shell（`MeloXApp`）
Android 源文件：`MeloXApp.kt` — `MeloXApp`
Windows 源文件：`MeloXApp.kt` — `MeloXApp`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Windows now restores the selected tab and records the production page through GraphicsLayer. Tone sampling and platform optical effects still need visual acceptance.

## Bottom Chrome

页面：Bottom Chrome（Android `MeloXBottomChrome` vs Windows `navigation/MeloXBottomChrome.kt`）
Android 源文件：`MeloXApp.kt` — `MeloXBottomChrome`
Windows 源文件：`navigation/MeloXBottomChrome.kt` — `MeloXBottomChrome`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Windows now uses Android's 57→46dp signed-spring geometry and records the hidden red tab layer for the moving droplet. GPU refraction, continuous shapes and dynamic tone sampling remain PARTIAL.

## Mini Player

页面：Mini Player（`MeloXIOSMiniPlayer` / `MeloXMiniPlayer`）
Android 源文件：`player/MeloXIOSMiniPlayer.kt` — `MeloXIOSMiniPlayer`
Windows 源文件：`screens/MeloXMiniPlayer.kt` — `MeloXMiniPlayer`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Windows measures the actual MiniPlayer width, reads reactive samples, keeps the 48dp height and artist row, and drives expansion fades independently from compact progress. Same-source swipe identity and pending incoming preview were corrected; runtime swipe/transition acceptance remains pending.

## Full Player

页面：Full Player（`MeloXIOSNowPlayingSharedHost` / `MeloXNowPlayingScreen`）
Android 源文件：`player/MeloXIOSNowPlayingSharedHost.kt` — `MeloXIOSNowPlayingSharedHost`
Windows 源文件：`screens/MeloXNowPlaying.kt` — `MeloXNowPlayingScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android switches `MeloXPlayerShell.Classic` to `MeloXClassicNowPlayingScene` and otherwise `MeloXIOSNowPlayingScene`, with `MeloXMeiMeshBackdrop`; Windows always draws `MeloXFlowingLightBackdrop` plus one `ArtworkPage` / `LyricsPage` / `QueuePage` stack and has no classic shell.

## Home

页面：Home（`MeloXHomeScreen` / `HomeScreen`）
Android 源文件：`discovery/MeloXDiscoveryScreens.kt` — `MeloXHomeScreen`
Windows 源文件：`screens/HomeScreen.kt` — `HomeScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `NeteaseHomeDataScreen` loads `LocalRecommendationStore` and opens playlist/album blocks through `DiscoveryCollectionDetail`; Windows `NeteaseHome` has no local-recommendation block and only calls `onOpenPlaylist`.

## Explore

页面：Explore（`MeloXExploreScreen` / `ExploreScreen`）
Android 源文件：`discovery/MeloXDiscoveryScreens.kt` — `MeloXExploreScreen`
Windows 源文件：`screens/ExploreScreen.kt` — `ExploreScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android keeps the 播客 category in-page (`showPodcast` on `MeloXExploreLayout`) and opens `DiscoveryCollectionDetail`; Windows `NeteaseExploreDataScreen` calls `onOpenPodcasts()` and leaves the explore page.

## Library

页面：Library（`LibraryScreen`）
Android 源文件：`library/LibraryScreen.kt` — `LibraryScreen`
Windows 源文件：`screens/MeloXLibraryScreen.kt` — `LibraryScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `LibraryScreen` takes `source`, restores `library_last_page` via `rememberLibraryPage`, and branches to `ProviderLibraryScreen` / local aggregation; Windows `LibraryScreen` is Netease-only and always starts `selectedPage` at `MeloXLibraryPage.Songs`.

## Search

页面：Search（`SearchScreen`）
Android 源文件：`search/SearchScreen.kt` — `SearchScreen`
Windows 源文件：`screens/SearchScreen.kt` — `SearchScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `SearchScreen` reads `MeloXSearchLaunchBus`, `unifiedEnabled` / `unifiedSources`, and a podcast-discovery state; Windows `SearchScreen` has none of those paths.

## Settings

页面：Settings（`ProviderSettingsHub` / `SettingsScreen`）
Android 源文件：`provider/ProviderSettingsHub.kt` — `ProviderSettingsHub`（detail pages in `settings/SettingsScreen.kt` — `SettingsScreen`）
Windows 源文件：`screens/SettingsScreen.kt` — `SettingsScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android detail routes (`EqualizerSettings`, `SkylineLyricsSettings`, `FloatingLyricsSettings`, `ListenTogetherSettings`, `RemoteConfigSettings`) live under `SettingsDetailScreen` on `MeloXPinnedListPage`; Windows `SettingsDetail` only has the shorter `GeneralSettings` / `AppearanceSettings` / `PlaybackSettings` set and the account row does not open login.

## Podcasts

页面：Podcasts（`MeloXPodcastScreen` / `PodcastsScreen`）
Android 源文件：`podcast/MeloXPodcastScreens.kt` — `MeloXPodcastScreen`
Windows 源文件：`screens/PodcastsScreen.kt` — `PodcastsScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `PodcastDetail` is hosted in `MeloXPinnedListPage` and can toggle subscribe via `onSubscriptionChanged`; Windows `PodcastDetail` is a plain column with no subscribe control and no pinned-list chrome.

## Downloads

页面：Downloads（`MeloXLibraryDownloadsPage` / `DownloadsScreen`）
Android 源文件：`library/LibraryScreen.kt` — `MeloXLibraryDownloadsPage`（tab entry is `LibraryScreen(forcedPageName = "Downloads")`）
Windows 源文件：`screens/DownloadsScreen.kt` — `DownloadsScreen`（body `screens/MeloXLibraryDownloads.kt` — `MeloXLibraryDownloadsPage`）

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android downloads stay inside `LibraryScreen` so the library segmented picker and login gate remain; Windows `DownloadsScreen` mounts `MeloXLibraryDownloadsPage` alone, with no library chrome.

## Cloud

页面：Cloud（`MeloXCloudMusicScreen` / `CloudScreen`）
Android 源文件：`cloud/MeloXCloudMusicScreen.kt` — `MeloXCloudMusicScreen`
Windows 源文件：`screens/CloudScreen.kt` — `CloudScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android paginates every `cloudSongs` page, filters with `query`, and uploads through `GetContent` plus a `MeloXGlassDialog` delete confirm; Windows loads one page, deletes inline, and has no search or upload.

## Messages

页面：Messages（`MessagesScreen`）
Android 源文件：`messages/MessagesScreen.kt` — `MessagesScreen`
Windows 源文件：`screens/MessagesScreen.kt` — `MessagesScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `MessagesScreen` opens `MessagesDetailScreen` and `MessageContactsScreen` from `privateMessageConversations`; Windows only lists contacts in `MeloXIosListRow` and the rows have no click handler.

## Login

页面：Login（`NeteaseLoginScreen` / `LoginScreen`）
Android 源文件：`account/NeteaseLoginScreen.kt` — `NeteaseLoginScreen`
Windows 源文件：`screens/LoginScreen.kt` — `LoginScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android login is a Netease phone/WebView flow (`NeteasePhoneAuthClient`, `https://music.163.com/#/login`) that writes `NeteaseSessionStore`; Windows `LoginScreen` is a multi-provider card list whose logout only flips in-memory `loggedInProviders`.

## Album detail

页面：Album detail（`MeloXUnifiedAlbumDetailScreen` / `AlbumDetailScreen`）
Android 源文件：`library/LibraryScreen.kt` — `MeloXUnifiedAlbumDetailScreen`（renders `MeloXPlaylistDetailScreen` with `albumId`）
Windows 源文件：`screens/AlbumDetailScreen.kt` — `AlbumDetailScreen`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android album detail reuses `MeloXPlaylistDetailScreen` (shared-element hero, glass circle toolbar, `MeloXDetailPalette`); Windows `AlbumDetailScreen` is a separate 48.dp row with literal `"←"` / `"⤴"` text and a 200.dp cover, and `TabContent` never assigns `selectedAlbum`.

## Playlist detail

页面：Playlist detail（`MeloXPlaylistDetailScreen` / `PlaylistDetailScreen`）
Android 源文件：`library/LibraryScreen.kt` — `MeloXPlaylistDetailScreen`（entry `MeloXUnifiedPlaylistDetailScreen`）
Windows 源文件：`screens/PlaylistDetailScreen.kt` — `PlaylistDetailScreen`（library path is `screens/MeloXLibraryPlaylistDetail.kt` — `MeloXLibraryPlaylistDetailScreen`）

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Home/Explore open `PlaylistDetailScreen`, whose toolbar is a 48.dp row of `"←"` and `"⤴"` text over a 200.dp cover; the library path uses `MeloXLibraryPlaylistDetailScreen` with `MeloXLibraryGlassCircleButton`, so the same playlist has two layouts.

## Glass sheet

页面：Glass sheet（`MeloXGlassSheet`）
Android 源文件：`glass/MeloXGlassSheet.kt` — `MeloXGlassSheet`
Windows 源文件：`glass/MeloXGlassSheet.kt` — `MeloXGlassSheet`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `meloXGlassSurface` takes `MeloXGlassMaterial.Regular` and `navigationBarsPadding`, and blur only exists when `LocalMeloXBackdrop` is a real layer; Windows drops the material argument, adds a 96.dp drag-to-dismiss, and treats `LocalMeloXBackdrop` as a `Boolean`.

## Pinned list

页面：Pinned list（`MeloXPinnedListPage`）
Android 源文件：`glass/MeloXPinnedListPage.kt` — `MeloXPinnedListPage`
Windows 源文件：`glass/MeloXPinnedListPage.kt` — `MeloXPinnedListPage`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android pins the bar with `rememberLayerBackdrop` plus the `MeloXTopFade` runtime shader and `statusBars` inset (`toolbarHeight = statusBarHeight + 62.dp`); Windows uses `meloXBackdropBlur` at a flat `62.dp` and no call site in screens uses `MeloXPinnedListPage`.

## Lyrics

页面：Lyrics（`MeloXIOSLyricsPanel` / `LyricsPage`）
Android 源文件：`player/MeloXIOSLyricsPanel.kt` — `MeloXIOSLyricsPanel`
Windows 源文件：`screens/MeloXNowPlaying.kt` — `LyricsPage`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android `MeloXAppleMusicLyricsPanel` does glyph timing, ruby text, instrumental waves, and interlude countdowns; Windows `LyricsPage` is a `LazyColumn` of 26.sp/22.sp lines with a binary syllable color.

## Queue

页面：Queue（`MeloXQueuePanel` / `QueuePage`）
Android 源文件：`player/MeloXQueuePanel.kt` — `MeloXQueuePanel`
Windows 源文件：`screens/MeloXNowPlaying.kt` — `QueuePage`

布局：
[ ] 一致

Typography：
[ ] 一致

Colors：
[ ] 一致

Icons：
[ ] 一致

Glass：
[ ] 一致

Spacing：
[ ] 一致

Animation：
[ ] 一致

Interaction：
[ ] 一致

Data：
[ ] 一致

Navigation：
[ ] 一致

状态保持：
[ ] 一致

差异：
- Android splits the queue into 历史记录 / 队列 (`MeloXQueueOrigin.Manual`) / 继续播放 under a blurred `QueueSongHeaderSurface` and `QueueModeControlsSurface`; Windows `QueuePage` is one flat song list with 已播放 / 继续播放 labels and a text "移除" button.
