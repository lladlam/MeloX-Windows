# 功能与 UI 补齐执行日志

真源固定为 Android `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`，只读取纯 Git 树。
2026-10-15 本轮起点 Windows `d3a091f30a2abe09bf76389a80588fe23f21e1da`。
逐项记录实现和验证；“实现”与 Android 同内容像素/动态验收分开记账。

## 0. 起点复核

- Windows CI `38680774924` / `d3a091f`：SUCCESS；四语言设置回放的 CJK 参数修复已通过 Windows。
- 工作区干净，原始 stash 保留。
- 历史矩阵里的“缺失设置页/51 个歌词键”已过时：设置 19 路由、60 个歌词键、17 个 Skyline 键已有实现和回放。
- 当前真实缺口：歌曲操作面板只实现少数入口，喜爱状态固定从 false 开始；源端完整面板包含下载、歌单写入、评论及回复、好友分享、动态分享、听歌排行、百科、定时关闭。

## 本轮工作清单

| 项目 | 状态 | 实现/证据 |
|---|---|---|
| 歌曲操作：真实初始喜爱状态、并发写入保护 | 本地回归/物理回放 PASS，线上待验 | NetEase/Local/Spotify/Jellyfin；未知状态不发反向写入 |
| 歌曲操作：下载/删除、添加/创建歌单、来源歌单移除 | 已实现，验证中 | NetEase 下载用原生 ID；PlaylistWrite/Sync 按来源派发 |
| 歌曲操作：评论分页、回复分页、分享联系人、动态、听歌排行、百科 | 已接真实客户端，验证中 | 错误与空数据分开；真账号写操作待授权验收 |
| 定时关闭：后台计时/取消/真实暂停 | 实现及物理设置/跨 JVM 恢复 PASS | 不依赖面板存活；退出停止 worker 但保留 deadline |
| 歌曲操作 UI：源端顺序、尺寸、SF 图标、页面切换/返回 | 本地物理回放 PASS，Android 帧待验 | controls/reload 68 scenes / 63 assertions |
| 搜索/Library/收藏详情及跨页动画差异 | 待复核 | 对照纯 pinned 源逐项列差异 |
| 桌面缺后端的设置功能 | 待复核 | 禁止仅存开关；逐项接真实消费者 |
| ListenTogether 原有空实现 | 已替换，验证中 | 应用级轮询/指令/心跳/队列状态机；设置和播放器入口接入 |
| Recognition 原有空实现 | 已替换，验证中 | Java Sound 麦克风/FFmpeg 本地 PCM → pinned JS/WASM → 真实 match API |
| Android 同内容截图/动态帧 | 缺参考证据 | 不用桌面自比对冒充验收 |
| Bilibili / Kuwo / Apple Music 原生 provider | 目录/协议已接，live 账号待验 | Kuwo 搜索/播放/歌词/歌单；Bilibili WBI 搜索/收藏夹/DASH；Apple 官方 REST 目录/资料库/歌词。Apple 全曲明确不声明 Playback 能力 |
| 播放预取 / 独立本地音频分段抽取 | 预取死代码已删除；分段抽取已接真实解码 | 删除 `MeloXMediaPrefetcher`：它会创建**空占位文件**并返回，却全仓库无调用方，属于可产生假数据的陷阱；桌面按需拉取，无 Media3 预取缓存（如将来需要，应实现真实预解码并设上限）。`LocalAudioSegmentExtractor` 已用打包 FFmpeg 输出真实 8kHz 单声道 PCM，不做静音填充 |
| 播放后 549→1431MB 持续增长 | 已定位为有界原生平台期；未复现无界增长 | 三组对照：播放 vs 暂停 +400.6 / +385.6MB（差 15MB）；含/不含歌词均 +484.9MB；15→51 场景只多 +84MB；长跑 194s 已在 ~795MB 平台；关闭播放器后静置仍 +18~53MB（JVM 堆同时被回收）|

## 步骤记录

每个已完成步骤在此追加：真源位置 → 修改文件 → 验证命令/结果 → 剩余差异。

### 1. 定时关闭后台消费者

- 真源：`MeloXPlayerUi.kt:689–707` 与播放服务的 `playback_sleep_timer_end_epoch_ms` 消费者。
- 新增 `shared/.../playback/MeloXSleepTimer.kt`：保留同一 epoch 偏好键；进程拥有计时任务，关闭弹层/换歌不会取消；替换/取消使旧任务失效；到期清空偏好并暂停真实 controller/AudioPlayer；应用启动恢复计时。
- 验证：`:shared:desktopTest --tests melox.playback.PlaybackSleepTimerTest` **2 tests PASS**。覆盖替换、取消、恢复、精确到期、只暂停一次。
- UI 入口正在并入源端歌曲操作面板；物理 15/30/45/60 分钟选择和重启证据后补。

### 2. 歌曲操作面板与原生身份

- 真源：`ui/player/MeloXSongActionsOverlay.kt`；Windows 改为源端 38dp 圆角 ModalBottomSheet、18dp 内容边距、58×4dp 拖动柄、页面 fade/scale 切换。
- 接入定时、队列、下载、歌单列表/创建/添加/移除、评论/回复游标分页、联系人发送、动态分享、排行和百科；网络失败有重试和原始错误，不以空列表冒充成功。
- 喜爱读取独立为 `FavoriteStateCapability`；NetEase 读真实 liked IDs，Local 读持久化记录，Spotify 遍历原生喜爱曲库，Jellyfin 读鉴权的 UserData。无可靠读取能力的来源明确禁用 toggle；不把 unknown 当 false。
- `MeloXDownloadStore.start` 验证来源并使用 `neteaseSongId`，修复桥接 legacy hash 被错误传到网易云 URL 解析的路径。
- `PlaylistDetailScreen` 传递真实所有者/来源歌单，写操作完成后重新加载详情。
- `LocalProvider` 接入已存在的本地歌单存储，真实创建/添加/删除/重命名/排序和持久化喜爱。
- 首轮物理回放已通过 Local 喜爱 unknown→unliked→liked、下载禁用。后续 Sleep 断言因 harness 以末尾冒号分割而失败，已把状态值改成 `Sleep-idle`，待复跑。
- 桌面分享当前使用操作系统剪贴板，提示“已复制”；这是明确的平台差异，尚非 Windows 原生分享面板。

### 3. 一起听进程级协调器

- 真源：`playback/MeloXListenTogetherCoordinator.kt`、`core/network/NeteaseListenTogetherTransport.kt` 及 `SettingsScreen.kt:2262`。
- 删除内存房间 no-op；应用级 coordinator 使用真实鉴权 room status、playback、playlist report、command report、heartbeat 协议。
- controller 显式命令事件与解码 buffering 分开；远端写入抑制回环、队列报告防抖、串行指令序号、账号/房间 generation 检查、断线 Reconnecting 与后续恢复。
- 设置及播放器一起听页接入创建/加入/成员/连接状态/邀请分享/结束；页面关闭不停止同步。
- 正在编译及跑状态机回归；双真实账号、Windows 设备上的长时同步尚未验收。

### 4. 真正的声学听歌识曲

- `SongRecognitionClient` 删除 `emptyList()` 占位实现；麦克风使用 Java Sound 8kHz/16bit/mono，文件使用已打包 FFmpeg 解码真实 PCM 后重采样；交给 pinned AFP 算法生成指纹，发送 Android 同协议的 NetEase `audio/match` 请求。User-Agent 明确标识 Desktop。
- `tools/export_recognition_resources.py` 从纯 pinned Git 导出 `afp.js`、`afp.wasm.js` 和两个授权文件，4 文件字节核对 PASS；新增 `recognition-source-audit.json`，纳入 source/JAR/image/ZIP 资源校验。
- 加入 Javet 6.0.2（Apache-2.0）及当前构建平台的 V8 JNI，应用无需外部 Node/浏览器；不在启动时初始化 V8，每次提取关闭 isolate 和 WASM heap。查阅官方 Installation/Hello Javet/API 文档；Context7 本月配额已耗尽。
- `RecognitionSettings` 接入 3/6/9 秒、录音中/停止/结果/失败状态和真实播放；系统设备/权限错误原样呈现；离页释放录音、解码和请求。
- `LocalRecognitionCoordinator` 改为真源的中段/开头/末尾三段声学识别，识别后才做元数据/封面/歌词补充；不再用文件标题搜索冒充声学识别。
- 首次测试发现 JUnit4 的表达式测试返回了异常对象而非 Unit，已修正；WASM 执行/输入相关性/释放测试正在重跑。

### 5. 本地自动验证进展

- 最新 `:shared:desktopTest :desktop:compileKotlinDesktop` **220 tests / 44 suites / 0 failures / 0 errors / 0 skipped，BUILD SUCCESSFUL**。
- 新增回归含：计时替换/取消/恢复/单次到期；Together 远端队列/进度/无回环、账号切换时丢弃过期读取、断线恢复、绕过本地 shuffle/Smart Queue；Local 喜爱/歌单跨 repository 重建与重扫描持久化；Jellyfin 鉴权喜爱读取与 missing-state 不返回 false；真实 WASM 重复性/不同信号不同输出/isolate 释放；真实 WAV→FFmpeg→PCM→WASM→match 请求协议。
- 单元测试使用的协议/网络 fixture 仅用于状态机/协议断言；不算线上账号、音乐命中或实机麦克风成功证据。
- `export_recognition_resources.py --verify` PASS；`verify_pinned_resources.py --sources` **16 resources PASS**；`git diff --check` PASS。
- 新物理回放前一轮发现 Material ModalBottomSheet 接管 Escape 焦点，应用父层 BackHandler 没收到按键；已在生产弹层添加 Escape down 消费/up 返回处理，正在重跑真实输入。
- 2026-10-15 再查 `android.device_list`：**0 devices**；同内容 Android 像素/动态证据仍 BLOCKED，见 `evidence/completion-android-reference-check.json`。

### 6. 扩大空实现核查

- 复查所有 `stub`/`no-op`/`未接入` 标记，确认还有 Bilibili、Kuwo、Apple Music 的 provider 搜索/播放空实现、Media3 预取占位、旧独立分段抽取占位，以及部分登录流程/第二播放账号槽 UI。
- 这意味着“全部缺失功能/UI 已补齐”尚不成立。当前表持续记录这些缺口，避免只验收设置路由就误标整应用完成。
  （后续：本表第 9、10 步已接通 Kuwo/Bilibili/Apple 的真实目录协议；Apple 全曲仍因缺桌面 MusicKit 而不声明 Playback。）
- Android-only 平台集成仍包括系统歌词/通知、悬浮歌词、横屏桌面歌词、系统字体/剪贴板/沉浸播放及触觉反馈消费者；须逐个接实际桌面能力后再启用。

### 7. 系统字体真实消费者与弹层返回

- 对照 pinned `ui/theme/MeloXMiSansFont.kt`：`system_font=true` 选择 `FontFamily.Default`，false 选择 pinned MiLan。
- `MeloXMaterialTheme.kt` 和 `MeloXTypography.kt` 接入同一偏好状态，应用 Material/玻璃排版立即响应，保留源端字号/字重/行高；两份 Material Typography 缓存复用，避免逐帧重建整组样式。
- General 系统字体开关已启用；新增物理开关、字体消费者断言与独立 JVM 重载/还原场景。SF 图标仍走独立 pinned 字体。
- 歌曲弹层细化源端 46dp ActionItem / 56dp grouped list 行和 grouped chevron，邀请链接按 pinned `st.music.163.com` 与 creatorId 生成。
- Local controls v4 PASS（真实喜爱写入、创建并添加歌单、定时设置/关闭弹层保留）；reload v4 已证明喜爱与本地歌单跨 JVM 持久化，但在歌单页 Escape 失败。进一步确认 ModalBottomSheet 原生 Back 接管焦点：现在关闭其自动 Back dismissal，给生产弹层显式焦点并派发页面返回，待 v5 全套复跑。
- controls v5：38 scenes 的全部断言通过（含源端页面返回/创建歌单/定时/未登录一起听/识曲时长/系统字体），但主函数结束后进程未退出。`jstack` 显示 DestroyJavaVM 等待 AWT 自动退出，timer 每秒重新唤醒 AWT；已终止该验证进程（exit 143，不记 PASS）。新增应用根节点 dispose 时停止 Together/计时 worker，保留定时偏好值；加停进程→重启恢复到期回归，重跑完整流程。

### 8. 歌曲操作完整物理输入与持久化核验

- `capture_song_actions.py` controls v6 **exit 0**，同一 home 的独立 JVM reload v6 **exit 0**；汇总 **68 scenes / 63 assertions PASS**。修复后的生产 Escape 子页返回、弹层关闭及进程退出通过。
- 验证 Local 未喜爱→写入喜爱→新进程读取已喜爱，创建歌单并真实添加→新进程仍含同一首原生曲目，15 分钟定时设置→关闭弹层仍有效→新进程恢复→点击取消，未登录 Together 返回真实登录错误及无效邀请提示，识曲 3 秒偏好重载，系统字体切换→新进程仍为系统字体→恢复 pinned。
- 回放通过鼠标/滚轮/键盘进行，无 semantics action 或直接偏好变更；UTF-8 steps/expect 原件、全量截图、capture JSON 和日志已归档到 `evidence/song-actions-local/{controls,reload}`，汇总 `song-actions-local-validation.json`。
- 两进程都解码真实生成 WAV 并输出非零 PCM；独立读取 PCM 校验帧数字节数/RMS/SHA，归档 `pcm-readback.json`。软件端点仅验证 PCM，不作物理听感成功声明。
- Windows CI 新增同一 controls/reload 与汇总门禁，上传完整新增回放证据。
- lifecycle 修正后的全量 `:shared:desktopTest` **221 tests / 44 suites / 0 failures / 0 errors / 0 skipped，BUILD SUCCESSFUL**。

### 9. 剩余原生音源：酷我与 Bilibili

- 歌曲操作功能提交 `0fca73c` 与证据提交 `38b6756` 已推送 main，Windows CI `38702385103` 运行中。
- 对照 pinned `core/provider/kuwo/KuwoProvider.kt`，移除 Search 空页/Playback Unavailable，占位改为实际 KuwoApiClient、LyricsClient、PlaylistClient 与真实账号摘要，IO 跑在后台；User-Agent 明确标识 MeloX Desktop。
- 对照 pinned `core/provider/bilibili/BilibiliProvider.kt`，迁移 WBI 搜索、cid 回查、账号/收藏夹/多 P 详情、DASH 质量选择和真实带鉴权头的音频请求；保留 bvid:cid 逻辑身份，替代关联只影响物理解析。
- Bilibili 缓存恢复源端 policies/128 条上限/6 小时 key TTL 和单例；缓存键按账号、会话修订和 cookie 摘要隔离；-403 时丢弃 key 并真正重取 nav，避免缓存的旧 nav 再导出旧 key。请求完成后检查账号未切换。
- 新增协议测试：WBI 签名/原始查询/cid、-403 重签/替代关联/质量/鉴权头、收藏夹多 P、API 错误不假装空库；酷我搜索/播放/鉴权歌单调用链。全量编译/回归正在执行。
- 识曲文件片段修正到 pinned 的最多 9 秒、中点默认起始，避免 12 秒指纹 GET 请求比源端更长。
- 全量 `:shared:desktopTest :desktop:compileKotlinDesktop` **225 tests / 46 suites / 0 failures，BUILD SUCCESSFUL**。协议测试不代表线上登录/音乐播放成功；这两类 live 证据仍缺。

### 10. Apple Music 官方 REST 目录与凭据验证

- 对照 pinned `core/provider/applemusic/AppleMusicApiClient.kt`：实现 catalog 搜索（songs/playlists/albums/artists）、charts 推荐、资料库歌单、目录/资料库歌单详情、专辑详情、艺人详情与真实 TTML/纯文本歌词。
- 新能力集合只声明实际实现的 Search/Lyrics/Library/Playlists/Albums/Artists/HomeRecommendations；**不声明 Playback**——REST preview 不能冒充全曲，`resolvePlayback` 仍返回明确 Unavailable，后续若接入桌面 MusicKit 再单独声明。
- 凭据存储改为 secure `developer_token` / `music_user_token` / `storefront`，`toString()` 只暴露 configured/authorized/storefront，不泄漏 token；登录表单从“尚未接入”改为真实调用 API 验证：目录请求成功且已授权时读取 `/v1/me/storefront` 并持久化，失败原样显示，绝不假装登录成功。
- 资源身份走白名单校验（拒绝 `/ ? # %`），防止目录 ID 拼入 URL 路径；每请求使用 Bearer + 可选 Music-User-Token + 系统 Locale，返回后检查凭据未变化。
- 歌词优先真实 TTML（`TtmlLyricsParser`，无命名空间/解析失败时回落到真实 text 字段）；Apple 的无时间文本保持单个原始行且 `pseudoTimingAllowed=false`，不编造逐字时间。
- 新增 `AppleMusicProtocolTest` 5 项：token 不进日志、目录原生 ID/preview 元数据但拒绝全曲播放、凭据验证与不完整凭据拒绝、目录身份路径校验、TTML/纯文本/回落/空歌词。
- 全量 `:shared:desktopTest :desktop:compileKotlinDesktop` **230 tests / 47 suites / 0 failures / 0 errors / 0 skipped，BUILD SUCCESSFUL**。
- 上一批 Windows CI **`38702385103` SUCCESS**（song actions/reload 回放门禁进入 CI 并通过，22m27s）。
- 多音源批次 Windows CI **`38710418775` SUCCESS**（`5b4e980`，Kuwo/Bilibili/Apple REST 与全部既有门禁通过）。
- 内存回放批次 Windows CI **`38716111866` SUCCESS**（`a0c8b79`，21m，**230 tests / 47 suites / 0 failures / 0 errors / 0 skipped**，由下载到的 Verification 产物 XML 独立汇总）。

### 16. 独立下载核验当前发行

- 新增 `tools/verify_delivery_artifact.py`：不信任 CI 自述，直接对下载到的发行 ZIP 做字节/SHA-256、逐条 CRC、JAR 内 `build-info` 版本号与 run head SHA 对齐、pinned 资源字节、打包 FFmpeg 字节与 PE x64 magic、启动 exe/JAR/`ffmpeg.exe` 齐全性校验。
- 下载方式：`gh run download`，并把 `TMPDIR` 指到主盘（`/tmp` 上一次因配额写满而失败：`disk quota exceeded`）。
- **当前独立核验通过的发行**：run **38716111866 / SUCCESS**，head **`a0c8b79a59a54c5e963a0b8c15496ac7f6de9ce0`**；发行 ZIP **320,739,222 bytes**，SHA-256 `23774c898aad1f761e1eb413bad41764c8d1e21e6ab07a2a781e470ee5651d02`；357 个 ZIP 条目 CRC 全通过；`build-info` 版本号与 run head 一致；pinned 资源与打包解码器校验 PASS；Windows 侧 230/47/0。
- 说明：`a0c8b79` 之后的 3 个提交（`ccbdd59`/`a3c16ff`/`0fee536`）与本地 `c855210` 只改工具/证据/文档，**不改应用代码**，因此该 ZIP 就是本轮全部应用改动的发行实体。
- 仍明确 BLOCKED：本机（Linux）无法执行 Windows `ffmpeg.exe`（仅做字节/PE 核验，执行由 Windows CI 的 `--run` 覆盖）、物理听感、DPAPI/设备切换、Windows 实机内存，以及 pinned Android 同内容像素/动态帧。证据：`evidence/windows-ci-a0c8b79/`（`run.json`、`windows-tests.json`、`download-verification.json`、`download-pinned-resources.json`、`download-decoder-bundle.json`）。

### 17. 删除会产生假数据的预取死代码

- 复查空实现时发现 `shared/.../playback/MeloXMediaPrefetcher.kt`：`materialize()` 会**创建空文件并返回**（注释自述“create an empty placeholder so callers don't crash”），一旦被接上就会把空文件当成音频交给分析管线，属于典型的“看起来有功能、实际是假数据”。
- 全仓库检索确认**没有任何调用方**（`MeloXMediaPrefetcher(` / `.materialize(` 零命中），因此直接删除该文件，而不是把它留在树里当陷阱。
- 删除后全量 `:shared:desktopTest :desktop:compileKotlinDesktop` **230 tests / 47 suites / 0 failures，BUILD SUCCESSFUL**。
- 桌面目前不做 Media3 式预取缓存（按需拉取）；若将来需要，应实现真实预解码并设上限/淘汰，而不是占位文件。

### 18. 剩余禁用设置的逐项盘点（本轮不改代码，只定性）

当前共 **15 行禁用控件**，按“能否在桌面落地”分两类，避免以后误把它们当已完成：

**A. 平台能力缺失，保持禁用（诚实禁用，不写开关）**
- 系统歌词 `system_lyrics_enabled`、歌词通知 `lyrics_notifications_enabled` + 5 个通知子项 + `notification-test`、HyperOS 灵动岛：Android 专属系统集成。
- 悬浮歌词 `floating_lyrics_enabled` / 副歌词模式 / 字号 / 高对比：需要桌面悬浮置顶窗口。
- 触觉反馈 `general_haptic_feedback`：桌面无振动设备。
- 音频焦点 `playback_allow_other_apps`：Android AudioFocus 的桌面等价物是 SMTC，语义不同，需单独设计而不是照抄开关。

**B. 可落地但缺真实生产端，暂不启用**
- 剪贴板链接 `general_clipboard_links`：解析器（`NeteaseClipboardLink`、一起听邀请）与消费者（播放/一起听）都已存在，缺的是桌面剪贴板轮询与确认弹窗（源端是启动后一次扫描 + `lastClipboardText` 去重）。
- 沉浸播放 `immersive_playback`：需要把播放器展开绑定到窗口全屏/无边框状态，需 Windows 实机验收。
- 记住上次歌曲 `playback_remember_last_song`：队列恢复已存在，需确认该键是否应真正控制恢复行为，避免“开关无效”。
- Bilibili 歌词/音频对齐 `bilibili_lyric_audio_alignment`：**引擎与存储都在**（`BilibiliLyricAlignment`、`BilibiliPlaybackAssociationStore`，播放解析已消费关联），但桌面**没有任何生产端**写入关联（`searchReplacementCandidates` 无调用方），因此保持禁用是对的。
- 自动更新检查 `update_auto_check`：桌面无定时器消费者。

结论：这批禁用项目前都不是“存开关但没消费者”，下一轮按 B→A 顺序逐个接真实消费者后再启用。

### 11. 本地分段抽取去占位

- `LocalAudioSegmentExtractor` 删除“返回 `FloatArray(0)`”占位，改为真实 FFmpeg PCM 解码：输入 44.1kHz 立体声，混为单声道，线性重采样到 8kHz，输出长度只由实际解码帧数决定，**不补静音凑长度**。
- 与识别客户端共用取消通道：协程取消/关闭客户端会关闭在跑的解码器并中断工作线程；`activeProcesses()` 断言解码进程已退出。
- `SongRecognitionClient.recognizeLocal` 改为调用该抽取器，固定 pinned 的 9 秒片段上限与中点起始；发送给 match 的 duration 由真实样本数四舍五入得出，不写死、不虚报。
- 新增回归：真实 3 秒 WAV → 23,000–24,100 个 8kHz 样本、含非静音信号、URI 与绝对路径两种解析结果一致、解码进程归零；全量 **230 tests / 47 suites / 0 failures**。

### 12. 播放后内存增长：定位到非 JVM 堆

- 工具：`tools/capture_playback_memory.py`（真实网易曲 `3334421408`、生产 UI、真实 FFmpeg PCM、`--skia-stats`、强制 GC 关闭）。新增 `--analyze OUTPUT` 可在不重跑回放的前提下重建汇总。
- 第一轮失败不是播放缺陷：歌词页控件在源端 5 秒 `lyrics_interface_auto_hide_ms` 后隐藏，脚本随后点击的坐标被 reveal 层消费。回放改为 `autohide`（断言 `lyrics-reveal-controls` 出现）→ `reveal`（断言 `player-controls` 恢复）→ 再点播放。
- 第二轮跑完 68 场景后停在 `position == duration == 177435ms`，即歌曲正常播完；据此把周期数收敛到 14（每轮约 7.7 秒：settle + PNG 场景证据），保证收尾的“暂停 → 关闭 → 静置 10 秒”采样能完成。
- 最终 PASS：**57 scenes / 14 player-disposal cycles**，真实 23.0MB 解码 PCM，结束时 `position=149191ms / duration=177435ms / is_playing=false`（真暂停，非曲终）。`verify_lyrics_layout` 与 `verify_online_playback_capture` 双双 PASS。
- 增长数据（同一进程、`-Xmx1g -XX:+UseSerialGC`）：常驻 **247.4 → 732.3 MB**（峰值 752.6，+484.9）；JVM heap used +78.5、heap committed 终值 122.4、非堆 +43.0 → **JVM 可解释部分终值 225.2 MB**；**未解释的原生内存终值 507.2 MB、净增 +360.0 MB**，占增长主体。
- 排除项（均有界）：Skia font cache 仅 +1.7MB、Skia resource cache 0、封面缓存 5.2MB / 64MB 预算、direct buffer 仅 +74KB、线程 22→25。
- 主要嫌疑（Skiko `Stats.allocated` 存活计数）：`Paint +647`、`RenderNode +643`、`ManagedString +489`、`Paragraph +489`、`TextStyle +523`、`Font +469`、`ImageFilter +174`、`Shader +93`、`ParagraphBuilder/ParagraphStyle +141`。基线 64/80/82（Paragraph/Paint/RenderNode）→ 收尾 553/727/725。
- 关键判别：播放器关闭后**再静置 10 秒**（`settled` 场景）常驻变化 **0.0MB**，Paragraph/Paint/RenderNode 变化 **0/0/0** → 不是“等 GC/引用清理”的假象，对象及其原生内存确实仍被持有。
- 结论边界：这只证明增长发生在 JVM 堆之外并集中在 UI 文本/绘制/渲染节点对象上；尚未指认具体持有者（候选：歌词行布局与模糊/遮罩对象在播放器关闭后仍被引用）。未在 Windows 实机复测，未继承启动内存结果作为修复证据。证据：`evidence/playback-memory-v3/`（汇总、参数、两份独立校验，不提交图片与 PCM）。

### 13. 二分：歌词页不是常驻增长主因

- 新增 `tools/capture_player_disposal_memory.py`：与 v3 完全相同的生产参数、真实曲目、PCM 端点与 14 轮播放器开合，但**全程不打开歌词页**（停留在 Artwork 页）。
- 结果（`player-disposal-memory-v1`，51 scenes / 14 cycles，`final_position=105737ms` 真暂停）：
  - 常驻 **269.9 → 754.8 MB（+484.9MB）**——与含歌词的 v3 **完全相同的 +484.9MB**。
  - JVM 可解释部分：heap committed 终值 **212.4MB**（v3 122.4）+ 非堆 +40.9 → accounted **314.4MB**；未解释原生 **440.4MB（+288.5MB）**（v3 507.2 / +360.0）。
  - Skia 活对象增长骤降：`Paragraph +64`（v3 +489）、`Paint +63`（+647）、`Font +64`（+469）、`ManagedString +64`（+489）、`TextStyle +78`（+523）、`ImageFilter +21`（+174）、`RenderNode +215`（+643）。
  - 收尾静置 10 秒：常驻 **+13.6MB**（仍在涨），同时堆使用 163→79MB 被回收、Paragraph 171→93（回到基线 64 附近）。
- 判读：
  1. **常驻总增长与是否打开歌词无关（两者都是 +484.9MB）**，所以歌词渲染不是 549→1431 这类总增长的主因。
  2. 歌曲歌词页确实存在独立滞留：关闭播放器后 `Paragraph` 仍 553（基线 64），而无歌词路径会在静置后回到 93/64 —— 这是可单独修复的文本对象/模糊滤镜滞留（原生约 +67MB）。
  3. 两轮共同的增长源仍在候选：真实播放管线、Artwork 页渲染、以及**回放自身的逐场景截图证据**。下一轮把 cycles 降到 2（场景数由 51→~14）观察增长是否随场景数线性缩放。

### 14. 场景数缩放：增长不是随截图线性增长，而是先陡后平

- `MELOX_MEMORY_CYCLES=2`（`player-disposal-memory-c2`，15 scenes / 2 cycles，Artwork 页、含播放）：常驻 **270.4 → 671.0 MB（+400.6MB）**。
- 对照：14 cycles / 51 scenes = +484.9MB；14 cycles + 歌词 / 57 scenes = +484.9MB。
  - 场景数 15 → 51（+36 张截图、+12 轮开合）只增加 **+84MB ≈ +2.3MB/场景**；
  - 若截图是主因，51/15 应约 3.4 倍（≈+1360MB），实际并非如此 → **截图证据不是线性主因**。
- c2 分段：`00→01` 拒绝远程配置 +55.7；`01→02` 打开播放器 +64.2；`02→03` 暂停 +40.8；`03→04` 恢复 **+0.8**；`04→05` 热身 7 秒真实播放 **+74.6**；此后每轮开合约 +16~29MB；末尾**播放器已关闭、静置 10 秒仍 +18.1MB**（同期 JVM 堆使用 110.6→76.2MB 被回收）。
- 结论更新：增长**先陡后平**——约 +300MB 发生在“启动 → 打开播放器 → 开始真实播放”，之后以每场景 2~3MB 缓慢逼近 700~800MB 平台；v2 长跑（194s、68 scenes）在最后约 10 轮已平（793→799MB）。
- 因此本轮**没有复现“无界增长到 1431MB”**：在本场景下它是有界饱和曲线。剩余待查：平台值本身偏高（启动 ~250MB → 平台 ~750MB），以及末尾静置期仍在增长的原生分配来自谁（候选：截图/渲染原生分配未归还操作系统的高水位、播放器模糊与遮罩层）。

### 15. 暂停对照：播放本身不是增长主因

- `MELOX_MEMORY_PAUSED=1 MELOX_MEMORY_CYCLES=2`（`player-disposal-memory-p2`，13 scenes）：常驻 **271.0 → 656.6 MB（+385.6MB）**，与同结构但持续播放的 c2（+400.6MB）**只差 15MB**。
- 逐步对齐（c2 播放 / p2 暂停，ΔMB）：拒绝远程配置 **55.8 / 53.5**、打开播放器 **64.2 / 61.9**、暂停 **40.8 / 36.4**、7 秒热身 **74.6 / 17.0**、后续每步 4~33 / 4~60、收尾关闭 **8.2 / 20.7**、**静置 10 秒 18.1 / 53.3**。
- 三个判定：
  1. **持续播放只贡献约 15MB**（41 秒播放差值），解码/混音管线不是主因。
  2. 起步几步两组几乎一致（差 <5MB）→ 主体是**应用/播放器图形初始化**，与是否出声无关。
  3. **空闲时仍在涨**（暂停组最大单步增幅出现在关闭播放器后的静置 +53.3MB，同期 JVM 堆被 GC 回收）→ 增长在原生侧，且不随活动停止而停止；但随后 v2 长跑显示最终会停在 ~795MB。
- 汇总（四组真实回放）：增长 = 启动/播放器图形初始化的约 +300MB 一次性开销 + 每场景 2~3MB + 歌词页关闭后约 67MB 的文本对象滞留，随后饱和；**播放 ≤15MB、歌词不改变总增长**。
- 仍未解决：Windows 实机上 549→1431MB 的无界复现（本环境最长 194s 未出现），以及原生分配的具体持有者/分配器。证据：`evidence/playback-memory-v3/`、`evidence/playback-memory-bisect/{no-lyrics,c2-playing,p2-paused}/`（只存汇总与参数，不提交图片/PCM）。
