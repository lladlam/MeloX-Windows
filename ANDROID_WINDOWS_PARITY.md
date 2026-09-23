# Android → Windows 实现级迁移矩阵

审计基准：
- Android: `MeloX-Android` main 分支（HEAD `2e3e5f6` + 工作区改动，2026-09-23 状态）
- Windows: `MeloX-Windows` main 分支（HEAD `9d40f0a`）
- 状态仅允许：MATCH / PARTIAL / MISSING / DIFFERENT / BROKEN

## 1. 总矩阵

| 模块 | Android | Windows | 状态 | 差异 | 优先级 |
| --- | --- | --- | --- | --- | --- |
| App Shell | `ui/MeloXApp.kt` 1354 行 | `ui/MeloXApp.kt` 298 行 | PARTIAL | 缺 provider 状态、settingsRouteRequest、登录/私信/onboarding/更新/剪贴板/心动模式启动、双层 backdrop、Library modal zIndex | P0 |
| Navigation | AppTab 8 tab + tab 顺序/开关设置 + BackHandler 层级 | AppTab 8 + MeloXNavState 无人使用 | PARTIAL | tab 顺序不可配置、Bilibili 特例缺失、Escape 分层返回仅 Search 有 | P0 |
| Home (Netease) | `NeteaseHomeDataScreen` + homeContent 9 个 section + quick actions 真实数据流 | `HomeScreen` 只有 provider feed + 热搜兜底 | BROKEN | 网易云首页完全未接（shared `homeContent` 已移植但 UI 未调用）；8 个 quick action 全部退化为搜索 | P0 |
| Home (Provider) | `ProviderHomeDataScreen` homeFeed + account | `HomeScreen` homeFeed | PARTIAL | account 头像按钮缺失、排行榜/推荐缺 rankingTracks 跳转 | P0 |
| Explore | Netease 12 分类 + Provider 排行 | 只有 provider homeFeed 两分类 | BROKEN | Netease 分类/精品歌单/播客入口缺失；`explorePlaylists` 已在 shared 未调用 | P0 |
| Search | 1269 行：unified、链接解析、播客发现、SwipeActionRow、launch bus | 653 行：基本搜索+合集 | PARTIAL | 缺搜索历史、unified 聚合、播客 tab、SwipeActionRow（Windows 有组件但 Search 未用）、launch bus | P0 |
| Library | 2571 行：Songs/Playlists/Albums/Artists/Cloud/History/Downloads + 详情 | `MeloXLibraryScreen` 813 行 + `MeloXLibraryPlaylistDetail` 760 行 | PARTIAL | 缺 Artists 页、Albums 页、收藏单曲页快照回写、批下载 sheet、playlist actions overlay | P0 |
| Player (Full) | `MeloXIOSNowPlayingV2` 1214 行 + CoreControls 941 行 + backdrop 390 行 | `MeloXNowPlaying` 701 行 | PARTIAL | 静态渐变背景（应 FlowingLight/Lyrics 三平面/模糊封面）；音量条为空 spacer；无音质选择；artwork 用渐变占位而非真实图 | P0 |
| Mini Player | 482 行 + reactive + swipe 换曲 | 466 行 | PARTIAL | `hasNext=false` 硬编码（swipe 换曲失效）；DancingBars 缺（无 audio reactive 接线） | P0 |
| Queue | `MeloXQueuePanel` 263 行：历史/继续/手动分区 + 长按拖拽重排 + mode 控制 | QueuePage ~60 行简单列表 | MISSING | 无历史/手动分区、无拖拽重排、无 remove、无 shuffle/repeat 控件 | P0 |
| Lyrics | 2410 行 IOSLyricsPanel + 729 AlternativePanels | LyricsPage ~70 行 | PARTIAL | 无逐字、无翻译/罗马音设置接线、无自动跟随暂停、无间奏倒计时、无点击 seek、无长按分享 | P0 |
| Playback Engine | Media3 + PlaybackService + ResolvingDataSource + QueueStore + SmartQueue + AutoMix | `AudioPlayer`(javax) + `MeloXDesktopPlayer`(空壳) | BROKEN | **`PlaybackCommands.playQueue` 不驱动 `AudioPlayer`**；`prepare()/play()` 是空实现；无 next/prev/seek/repeat/shuffle；无队列持久化 | P0 |
| Settings | 3285 行 20 个子页全部表单 | 163 行 9 路由仅 2 个可用表单 | BROKEN | General/Appearance/Content/Storage/Features/Messages 子页无表单；`MeloXSettingsRuntime`（~100 项）无 Windows 等价物，所有行为开关无法生效 | P0 |
| Provider 系统 | 11 provider + 注册表 + 切换后全 UI 联动 | 11 provider 目录已移植 + 注册表 | PARTIAL | `MeloXApp` 不读 `MusicProviderSelectionStore`，各屏各自默认；ProviderServicesScreen 有 TODO 死按钮 | P0 |
| Glass | 2818 行 11 文件（Sheet/Dialog/Dropdown/SwipeRow/PinnedList/LiquidSlider/Advanced） | 790 行 3 文件 | PARTIAL | 缺 GlassSheet/GlassDialog(真)/SettingsDropdown/PinnedListPage/LiquidSlider/AdvancedGlass(Slider+Menu)/SwipeRow(glass 包) | P1 |
| Typography | LantingPro + 6 styles + MiSans | 相同 6 styles + LantingPro | MATCH | （Android 工作区新增 MiSans 变体未对齐，P2） | P1 |
| Icons | MeloXSymbol 343 行全量 glyph 表 | MeloXSymbol 130 行 | PARTIAL | Windows 130 行仅子集（MusicNote/Home/Explore 等 ~40 个），缺 Android 全表 | P1 |
| Animation | MeloXMotion 全量 + PlayerTransitionSpec | MeloXMotion 全量（值一致） | MATCH | 动画参数一致；但部分调用点（PageLayer 用 slide 而非 Android 的 offset 数学）待核对 | P1 |
| Dynamic Backdrop | FlowingLight mesh 9 色 palette + 3 平面歌词背景 + 模糊封面 | 无（静态渐变） | MISSING | 需移植 palette 提取（3x3 160px）+ mesh 像素填充（算法在 fillFlowingMeshPixels，纯 Kotlin 可直移） | P0 |
| Persistence | SharedPreferences 全量 settings + queue store + last playback + library cache | MeloXPreferences + library cache + downloads | PARTIAL | settings runtime 缺失（见上）；last-playback 恢复缺失；queue 持久化缺失 | P0 |
| Cache | NeteaseLibraryCache + MediaCache + prefetcher | NeteaseLibraryCache 已移植；MediaCache 为空壳 | PARTIAL | 播放缓存在桌面暂无 Media3 对应物，可先记录 | P1 |
| Error/Loading/Empty | 各屏 EmptyOrLoading + error 文案 | 部分屏有 | PARTIAL | Messages/Cloud/Podcasts/Downloads 错误态缺失或不一致 | P1 |
| Login | 11 个 per-provider 登录页 + 手机验证码 + QR | `LoginScreen` 1021 行合并版 | PARTIAL | Spotify/YouTube/AppleMusic/Bilibili/Kuwo/QQ/Kugou OAuth 流程在桌面为 Cookie 粘贴为主（Android 亦以 cookie 为主，差异可接受但需逐一核对登录项文案与 QQ/Kugou 账密流程） | P1 |
| Account 页 | MeloXAccountActivity（主页/听歌排行） | 无 | MISSING | P1 |
| Messages | 599 行真实联系人/会话/发私信 | 75 行列表 | PARTIAL | 无会话详情、无发送 | P1 |
| Podcasts | `MeloXPodcastScreen` 完整订阅/分类/节目 | 185 行列表 | PARTIAL | 无分类页、无订阅管理 | P1 |
| Cloud | 622 行云盘列表/上传/删除 | 101 行列表 | PARTIAL | 无删除确认样式核对、无上传入口 | P1 |
| Downloads | Library 内嵌下载页 + BatchDownloadSheet | `MeloXLibraryDownloads` 570 行（真实）+ `DownloadsScreen`（mock） | DIFFERENT | `DownloadsScreen` 全 mock 违规，须改为 Library forcedPageName=Downloads 模式或真数据 | P0 |
| Lyrics Data | Amlldb + TTML + QRC + KRC + BindingStore + TimelineProcessor | shared 目录已同名移植 | MATCH | （Android 工作区 lyrics 改动未同步，P2） | P1 |
| Shuffle/Repeat | PlaybackModePreferences + Player repeatMode | MeloXPlaybackModePreferences 已移植但 Player 无 repeat/shuffle 实现 | BROKEN | 状态存了但播放器不消费 | P0 |
| Smart Queue / AutoMix | SmartQueueBuilder + AutoMix 全链 | 文件同名移植但无 player 钩子 | PARTIAL | 无触发点 | P2 |
| Listen Together | Coordinator + 邀请解析 + UI | 文件已移植无 UI | PARTIAL | P2 |
| Equalizer | MeloXEqualizerController | 文件已移植 | PARTIAL | 无 UI 入口 | P2 |
| Update Check | MeloXUpdateClient + 启动检查弹窗 | UpdateClient 已移植无启动检查 | PARTIAL | P1 |
| Onboarding | onboardingPage 首启引导 | 无 | MISSING | P2 |
| Remote Config | 运行时 + 通知 + 云控 consent | 文件已移植无 UI 接入 | PARTIAL | P1 |
| 心动模式启动 | startsHeartModeOnLaunch 自动起播 | 库页有手动入口 | PARTIAL | P2 |
| 剪贴板链接 | NeteaseClipboardLink 解析 → 自动搜索/详情 | NeteaseClipboardLink 已移植无 App 接线 | PARTIAL | P2 |
| Haptics | 设置开关 + 各控件 | 无（桌面无震动，可映射为无操作） | DIFFERENT | 记录为平台差异 | P3 |

## 2. 状态 counts（本表）

- MATCH: 3（Typography、Animation 参数、Lyrics 数据层）
- PARTIAL: 24
- MISSING: 4（Queue UI、Dynamic Backdrop、Account 页、Onboarding）
- DIFFERENT: 2（Downloads 页结构、Haptics）
- BROKEN: 5（Home-Netease、Explore、Playback Engine、Settings、Shuffle/Repeat）

## 3. P0 修复顺序（执行队列）

1. **Playback Engine**：`MeloXDesktopPlayer` 改为真播放器（包 `AudioPlayer`），实现 play/pause/next/prev/seek/stop/queue 指针；`PlaybackCommands.playQueue` → resolve（Netease `NeteaseSearchClient.playbackUrl` / provider `PlaybackCapability`）→ `AudioPlayer`。加 repeat/shuffle。
2. **MeloXSettingsRuntime**：在 shared 新建 `melox.settings.MeloXSettingsRuntime`（compose mutableStateOf 全量字段）+ `MeloXSettingsPreferences` 桥（`MeloXPreferences`），Android 键名 1:1。
3. **Home Netease**：`HomeScreen` 按 source 分支，接 `NeteaseLibraryClient.homeContent` + 9 section + cache + quick actions 真实数据流（`dailyRecommendedSongs/hotSongs/personalFm/radar/intelligence/similar`）。
4. **Explore Netease**：12 分类 + `explorePlaylists` + 播客入口。
5. **Provider 切换**：`MeloXApp` 读 `MusicProviderSelectionStore`，Settings hub 提供 onSourceSelected，visibleRootTabs 过滤。
6. **Player/Mini/Queue**：NowPlaying 接真实 artwork、音量条、音质徽标；Mini `hasNext` 真值 + DancingBars(能量)；Queue 分区 + 重排 + remove + mode。
7. **Dynamic Backdrop**：palette 提取（Skia `Image.makeFromEncoded` + 3x3 采样）+ `fillFlowingMeshPixels` 直移 + 歌词三平面。
8. **Search 补全**：launch bus、unified 聚合、SwipeActionRow 接入、播客 tab。
9. **Settings 全表单**：20 子页按 Android 逐个移植（桌面无通知/锁屏/悬浮窗项的，记录到矩阵）。
10. **Downloads 真数据**：删除 `DownloadsScreen` mock，改 Library forcedPageName 路径。
11. **Library 补页**：Albums/Artists 页 + 详情跳转。
12. **持久化**：last-playback 恢复、queue store、rememberLastTab。


## 4. 2026-09-23 已落地（编译通过）

- P0-1 播放引擎：`MeloXDesktopPlayer.prepare/play` 解析 `melox://song` 与 `melox://track` 并驱动 `AudioPlayer`。next/previous/seek/seekToIndex/repeat/shuffle/volume 已实现。`MeloXPlaybackQueueStore` 持久化队列。
- P0-2 `melox.settings.MeloXSettingsRuntime` + `MeloXSettingsPreferences`，键名与 Android 一致，约 140 个运行时字段。
- P0-3 Home：网易云走 `NeteaseLibraryClient.homeContent`，快捷操作调用每日推荐/热歌榜/心动模式/私人雷达/私人漫游/相似歌曲，不再用搜索冒充。
- P0-4 Explore：网易云 12 个分类 + 缓存 + 刷新，播客分类切到播客页。
- P0-6 播放器：FlowingLight 背景、真实封面、音量条、循环/随机、队列分区后 `seekToIndex`。MiniPlayer 的 `hasNext` 不再写死 false，滑动调用 next/previous。
- P0-7 `MeloXFlowingLightBackdrop.kt`：3x3 取色 + mesh 像素填充 + 歌词三平面 + 模糊封面。
- P0-10 `DownloadsScreen` 去掉 mock，转调 `MeloXLibraryDownloadsPage`。

仍未做：P0-5 Provider 切换进 App Shell、P0-8 搜索补全、P0-9 设置 20 个子页表单、P1 Glass 缺件与 Library Albums/Artists。


## 5. 第二轮审计（编译通过之后）

已补上：
- Provider 切换写入 MusicProviderSelectionStore，Home / Explore / Search 跟随当前来源。Bilibili 只保留资料库和设置。播客、下载、云盘标签由设置开关控制。
- 设置子页不再是一句说明：通用、外观、内容、播放、歌词、功能模块会写入 MeloXSettingsPreferences。
- 歌单详情和专辑详情删除 mockTracks，改为 NeteaseLibraryClient.playlistDetail 和 NeteaseCollectionDetailsClient.albumDetail。首页和发现页点击歌单会打开详情。
- 登录、私信、音乐源服务三个已有页面从设置或资料库进入，不再是无法到达的死页面。

仍未对齐，且这次没有改成“完成”：
- 资料库没有专辑页和歌手页。Android 的 MeloXLibraryPage 也只有歌曲、歌单、播客、云盘、最近播放、下载；专辑和歌手在 Android 是详情页，不是资料库分段。
- 搜索没有滑动操作、聚合搜索和搜索历史。
- 队列不能拖拽重排，也不能删除单曲。
- 歌词没有逐字、自动滚动和点击跳转。
- Glass 缺 MeloXGlassSheet、MeloXPinnedListPage、MeloXSettingsDropdown、MeloXLiquidSlider、MeloXAdvancedGlassComponents。
- ProviderServicesScreen.kt:486 仍有一个 TODO 点击。
- LoginScreen.kt 的二维码区域仍是占位。
- PlaylistDetailScreen 里的非网易云歌单如果只有桥接摘要、没有曲目接口，点击后会走网易云详情并失败。这是已知缺口。
- Linux 无图形界面，没有做 Android / Windows 截图叠加。


## 6. 继续补齐（编译通过）

- 搜索：最近 12 条搜索写入 melox_search_history，空白搜索页可点回并清除。
- 歌词：当前行自动滚到可见区域，点击一行调用 seekTo(line.timeMs)。
- 队列：MeloXDesktopPlayer.removeAt 和 move 已实现。播放页非当前行有「移除」。拖拽重排的手势还没有接到 move。
- 音乐源页「切换」调用 MusicProviderSelectionStore.setSelectedSource，不再是空 TODO。
- 非网易云歌单走 PlaylistCapability.playlistDetail。没有该能力时显示「当前不提供歌单详情」，不造假曲目。

仍缺：
- 歌词逐字高亮。数据模型 LyricSyllable 已在 shared，播放页只画整行。
- 队列拖拽排序没有手势。
- Glass 仍缺 MeloXGlassSheet、MeloXPinnedListPage、MeloXSettingsDropdown、MeloXLiquidSlider、MeloXAdvancedGlassComponents。
- 登录二维码仍是占位。
- 没有 Android / Windows 截图叠加。当前环境没有图形界面。


## 7. 缺项补齐（编译通过）

- Glass：新增 MeloXGlassSheet、MeloXGlassDialog、MeloXPinnedListPage、MeloXSettingsDropdown、MeloXLiquidSlider、MeloXGlassSlider。桌面没有 Kyant backdrop，材质走现有 meloXGlassSurface。
- 歌词：有 syllable 时间轴时按字高亮。没有逐字数据时仍显示整行，不伪造时间。
- 队列：长按后拖动越过一行高度会调用 MeloXDesktopPlayer.move。正在播放的行不能拖。
- 登录：网易云手机验证码调用 NeteasePhoneAuthClient，并写入 NeteaseSessionStore。QQ 显示真实二维码图片并轮询。酷狗显示扫码链接并轮询。Bilibili、Apple Music、Jellyfin、通用表单不再延迟后假装登录成功。网易云没有扫码接口，扫码页会说明改用手机验证码。
