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
| 播放后 549→1431MB 持续增长 | 未复现、未修复；具体持有者待查 | Linux 短跑观测不足以证明永久有界平台期；播放/暂停、歌词和截图数量同时存在时长、GC 等混杂，不能将组间差值直接归因为各模块开销。历史步骤 12–15 的强因果判断由本行及步骤 25 更正 |

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
- 记住上次歌曲 `playback_remember_last_song`：**本轮已接真实消费者**，见第 20 步，已启用开关。
- Bilibili 歌词/音频对齐 `bilibili_lyric_audio_alignment`：**引擎与存储都在**（`BilibiliLyricAlignment`、`BilibiliPlaybackAssociationStore`，播放解析已消费关联），但桌面**没有任何生产端**写入关联（`searchReplacementCandidates` 无调用方），因此保持禁用是对的。
- 自动更新检查 `update_auto_check`：桌面无定时器消费者。

结论：这批禁用项目前都不是“存开关但没消费者”，下一轮按 B→A 顺序逐个接真实消费者后再启用。

### 19. 最终发行独立核验（取代第 16 步的 a0c8b79）

- 删除预取死代码后重新出包：Windows CI **`38721634269` / SUCCESS**（25m37s，head `d4fb9de70acc5849b7220de157cd8084eb965b30`）。
- 独立下载（`TMPDIR` 指向主盘）+ `tools/verify_delivery_artifact.py`：发行 ZIP **320,734,877 bytes**，SHA-256 `3499f59b2dfcdd9d0a6f982093e378a1382214b52209fd20925b0f2646e3096a`；357 条目 CRC 全通过；`build-info` 版本号 == run head SHA；pinned 资源与打包 FFmpeg 字节/PE x64 magic PASS。
- `tools/record_ci_evidence.py` 直接读取下载到的 47 份 Windows `TEST-*.xml`：**230 tests / 47 suites / 0 failures / 0 errors / 0 skipped**。
- 与上一份 `a0c8b79` 发行的唯一应用代码差异是删除空占位预取器；其余差异均为工具/证据/文档。
- 证据：`evidence/windows-ci-d4fb9de/`（`run.json`、`windows-tests.json`、`download-verification.json`、`download-pinned-resources.json`、`download-decoder-bundle.json`）。
- 仍 BLOCKED：本机执行 Windows `ffmpeg.exe`、物理听感、DPAPI/设备切换、Windows 实机内存平台值、pinned Android 同内容像素/动态帧。

### 20. 记住上次歌曲：接真实消费者并启用开关

- 真源：`ui/player/MeloXPlayerUi.kt:291`（每次状态更新时保存当前曲目）与 `:482`（仅在 `playback_save_queue=false` 且 `playback_remember_last_song=true` 时作为回退恢复单曲）。
- 桌面此前**没有**这个单曲存储，开关只是禁用行。本轮新增 `MeloXLastPlaybackStore`：保存当前曲目 + 播放位置，受同一偏好键控制；从 `MeloXPlaybackQueueStore` 抽出 `songToJson/songFromJson` 共用序列化，避免两套格式漂移。
- 接线：`publishQueue()` 保存当前曲目、周期位置更新保存位置、`restoreLastQueue()` 增加回退分支——**只有关掉队列持久化时**才恢复单曲，队列持久化开着时永远优先队列，两个开关互不越权。
- 启用设置行 `settings_remember_last_song`。
- 新增 `LastPlaybackStoreTest` 2 项：负 legacy ID/标题/歌手/时长/位置往返保存与开关关闭后不再覆盖；队列关闭→单曲回退（1 首、暂停、位置 500ms），队列开启→队列优先（2 首、index 1），两个开关都关→不恢复。
- 期间修掉一个真实回归：把 `id==0` 当无效会丢掉 provider 桥接歌曲（测试 `persistedProviderQueue...` 立刻抓到），已保留 0/负 legacy ID 的原行为。
- 全量 `:shared:desktopTest :desktop:compileKotlinDesktop` **232 tests / 48 suites / 0 failures，BUILD SUCCESSFUL**。

### 21. 最终发行独立核验（1f25501）

- Windows CI **`38732079740` / SUCCESS**（18m12s，head `1f25501fb0eb21f336d6de5426a9fe5a3ffb5383`）。
- 独立下载 + 核验：发行 ZIP **320,738,222 bytes**，SHA-256 `58f7f6006e604667811c30c9723273765046b7a286b64a358c6dd3ede8c40bd0`；357 条目 CRC 全通过；`build-info` == run head；pinned 资源与打包 FFmpeg 字节/PE PASS。
- 从下载到的 48 份 Windows `TEST-*.xml` 重新汇总：**232 tests / 48 suites / 0 failures / 0 errors / 0 skipped**（含新增 2 项 last-song 测试）。
- 相对上一份已核验发行的唯一应用代码差异：新增 `playback_remember_last_song` 真实消费者与启用的设置行。
- 证据：`evidence/windows-ci-1f25501/`。仍 BLOCKED：Windows 实机执行/听感/DPAPI/设备切换、实机内存平台值、pinned Android 同内容像素与动态帧。

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

### 22. 本机网易云音乐 Cookie 一次性导入

- 用户确认范围：**只获取 Cookie，然后走已有 API**。依据上传的官方 Windows `3.1.41.205564` / Cookies schema 14 研究交接接入一次性读取。
- 新增 `shared/src/desktopMain/kotlin/melox/account/NeteaseOfficialCookieReader.kt`：定位当前 Windows 用户的 `NetEase/CloudMusic/webapp*`；仅读官方文件，将加密 Cookies 及存在的 WAL/journal 复制到 MeloX 私有临时目录；检查复制前后文件元数据，检测到变化则提示重试。SQLite 只打开私有副本，不打开官方库。复用 `WindowsDpapi.unprotect` 解封主密钥，AES-GCM 解出根路径下未过期的 `MUSIC_U` / `__csrf`；拒绝未知 schema/加密前缀、认证标签错误、重复主会话和 Cookie 分隔符注入。主密钥缓冲区用后清零，正常/异常路径均删除私有副本，错误不附带底层秘密或原始输入。
- `LoginScreen.kt` 原未实现的网易扫码页替换为“本机客户端”页，用户点击“读取客户端登录态”触发；多 profile 时显式选择，非 Windows 明确禁用。读取结果直接交给已有 `NeteaseSessionStore.acceptAuthenticatedCookie()`，继续用已有 EAPI 账号验证和 DPAPI 会话保存；成功只向 UI 返回昵称。导入后的业务调用、退出和失效行为沿用现有链路。
- 依赖：增加 Xerial `sqlite-jdbc:3.53.4.0` 读取 SQLite 副本；既有发行模块已包含 `java.sql`。Context7 月额度已耗尽，已核对 Maven Central 版本元数据和该版本官方 `SQLiteConfig` 源码的只读打开方式。
- 新增 `NeteaseOfficialCookieReaderTest` **9 项**：真实 SQLite + AES-GCM 合成数据覆盖导入→既有 SessionStore、拒绝时不覆盖旧账号、profile 探测/选择、过期、前缀/GCM 破坏、分隔符/重复会话、DPAPI/JSON 失败脱敏、schema/非法目录，以及打开中的 WAL 提交。Windows 上 fixture 使用真实 CurrentUser DPAPI 包裹/解封测试 AES key；Linux 注入只用于测试的解封函数。原文件字节/mtime、临时副本清理和密钥清零有断言。
- 本地验证：`:shared:desktopTest :desktop:compileKotlinDesktop` **241 tests / 49 suites / 0 failures / 0 errors / 0 skipped，BUILD SUCCESSFUL**；`git diff --check` PASS。
- 验收边界：当前本地环境为 Linux，尚未用真实 Windows 网易账号完成导入和线上 API 验证；Windows CI、发行打包与独立下载核验另记。Android UI 验收基线仍为 `3f877ea`。

### 23. Cookie 导入的 Windows CI 与发行独立核验

- 功能提交 `f443fe6`，步骤记录提交 `aeafd72`；Windows run **`38858654884` / SUCCESS**，head **`aeafd724ebece906b52040a8790c066414ccb75f`**。下载的 Windows XML 重新汇总为 **241 tests / 49 suites / 0 failures / 0 errors / 0 skipped**；Cookie 导入 suite 的 9 项均通过，包含 Windows CurrentUser DPAPI 对合成密钥的真实包裹/解封。
- 完整独立下载 artifacts **`12029157838`（发行）/ `12029038255`（验证）**。发行 ZIP **332,746,834 bytes**，SHA-256 **`e0607284a65a7254891840df96a2a78067bc425c7b781db14430f42ca8b6c5f2`**。
- `tools/verify_delivery_artifact.py`：357 个 ZIP 条目 CRC、版本戳与 head SHA、16 个 pinned 资源、打包 FFmpeg 字节与 PE magic 均 PASS。额外打开下载包内应用 JAR，全部 JAR 条目 CRC PASS，确认 `NeteaseOfficialCookieReader.class`、`SQLiteConfig.class` 和 Windows x64 `sqlitejdbc.dll` 确实打包；DLL **1,014,784 bytes / PE AMD64**，SHA-256 `f16882d2eded914edd1e1d313e7c8579e040b0da090a52ea99ad7b0c5d961c45`。
- 证据：`evidence/windows-ci-aeafd72/`（run、完整测试汇总、导入 suite XML、独立下载核验、Cookie 导入打包核验）。当前独立核验发行更新为 `aeafd72`；真实官方账号导入→现有线上 API、物理 Windows 登录点击、同内容 Android 像素/动态验收仍待实机。

### 24. 前台剪贴板、邀请确认与沉浸播放消费者

- 真源：纯 pinned `MainActivity.onResume`、`MeloXApp.kt` 的链接确认框、`MeloXListenTogetherInviteActivity.kt`、`MeloXImmersivePlaybackEffect.kt`、`MeloXPlayerInsets.kt`。纠正初版“启动只扫描一次”的描述：Android 每次 resume 取消旧扫描，等首帧、延迟 100ms，检查开关并按进程内最后文本去重。
- `MeloXApp.kt` 改用生产窗口焦点/最小化状态触发扫描，并等待 onboarding 完成；仍读取真实 AWT 系统剪贴板。新增 `NeteaseClipboardIntake`，trim/去重、一起听邀请优先于 songId，邀请可从其他 music source 打开；普通歌曲/歌单仍限网易来源。关闭开关不消耗文本。
- 新增 `MeloXClipboardDialogs.kt`：普通链接使用 pinned glass 布局、按钮样式及间距；一起听直接弹源端确认框并传递 roomId/inviterId，确认才走既有真实 join API，成功后 adoptRoom。失败保留错误与重试，busy 时禁止重复加入/取消；请求期间账号变化不采纳过时结果。歌曲/歌单请求不再将异常吞成空列表，空响应及播放失败都有错误反馈，协程取消继续传播。
- 沉浸播放接入 `MeloXImmersiveWindowOwner` 与真实 Compose `WindowState`：全屏播放器打开且偏好开启时进入桌面 Fullscreen；关闭/禁用/销毁恢复原先 Floating/Maximized/Fullscreen，保留用户主动改变的 placement。设置项启用。Android 的状态栏隐藏对应桌面全屏，平台窗口表现须独立验证。
- 新增 `NeteaseClipboardIntakeTest` 3 项，覆盖前台重复扫描/取消后去重、开关与来源、含 songId 的跨来源邀请优先级。新增 `:desktop:auditImmersiveWindow`，在原生窗口读取实际 placement，验证 Floating bounds、三类原状态恢复、手动变更与销毁；Windows CI 已接入。
- 本地 `:shared:desktopTest :desktop:compileKotlinDesktop` **BUILD SUCCESSFUL**；从 XML 汇总 **244 tests / 50 suites / 0 failures / 0 errors / 0 skipped**。设置鼠标/滚轮/键盘回放 **controls 74 scenes / 55 assertions、独立进程 reload 8 scenes / 18 assertions，全 PASS**，包含沉浸开关的真实偏好、runtime 和重载；semantics 仅定位与断言。
- 本机原生窗口初测失败后确认前置环境异常：当前 rootless Xwayland 向 JDK 17 报告屏幕 **0×0**，不能据此验证全屏。audit 增加正尺寸显示检查，Linux 此环境明确记 `UNSUPPORTED`，Windows 若无可用显示则失败；正常路径额外验证全屏覆盖真实屏幕，不能只读枚举就报 PASS。Windows 原生结果待 CI。
- Context7 月额度耗尽；窗口 API 已查阅 JetBrains 官方 top-level windows 文档及当前缓存 Compose 1.8.1 类签名。真实 OS 剪贴板生命周期/播放器物理输入、真实账号加入、双账号同步、Android 同内容像素/动态帧仍待验收；不继承旧发行结果证明本次新增代码。
- 设备状态更新：2026-10-18 ADB 已出现一台在线设备，但设备现有 release/dev 均为 **0.6.5 / versionCode 24**，不是 pinned **0.6.2 / versionCode 20**，不能直接用来归档 pinned 参考帧。准备独立包名的 pinned 参考构建，保留现有应用和数据。

### 25. 内存历史结论的证据边界更正

- 保留步骤 12–15 的原始观测数字供复核；撤回其中“确定存活/持有”“播放 ≤15MB”“歌词独立滞留约 67MB”“永久有界饱和曲线”等强判断。未确认 Skiko `Stats.allocated` 的计数语义，不能直接称其为存活对象数；组间差值也不是模块开销的因果估计。
- 当前可确认的事实仅为：这些 Linux 回放在给定时长内有常驻增长，部分短跑末段趋缓，暂停和无歌词路径也能增长；用户 Windows 549→1431MB 尚未复现/修复。下一步需控制截图、运行时长、GC 等因素，确认统计语义，再查真实原生分配/持有者。

### 26. Windows 原生沉浸检查发现并修复最大化恢复问题

- Windows run `39061015821` / head `79d721af256e4b60d5794291cc8d3ed402c147d7` **FAILED**，停在 `auditImmersiveWindow`。独立下载 Verification-Windows：Floating → Fullscreen（1024×768）→ Floating（600×400）成功；Maximized → Fullscreen 后恢复超时，actual/requested 仍为 Fullscreen。此 run 没有通过发行验收。
- 核对 [Compose 1.8.1 官方 `ComposeWindow.desktop.kt`](https://github.com/JetBrains/compose-multiplatform-core/blob/v1.8.1/compose/ui/ui/src/desktopMain/kotlin/androidx/compose/ui/awt/ComposeWindow.desktop.kt)：`placement = Maximized` 只设置 `MAXIMIZED_BOTH`，不会清除独立 `isFullscreen` 标志。修复 `MeloXImmersiveWindowOwner` 使用真实 ComposeWindow，退出全屏先设 Floating 清除该标志，再应用目标 placement；原生检查保留尺寸及状态断言。功能提交 `037ed52`；本地 `:desktop:compileKotlinDesktop` **BUILD SUCCESSFUL**，Windows 复验待记录。
- pinned Android 参考 worktree 为 `../MeloX-Android-parity-3f877ea`；仅将 debug applicationIdSuffix 改为 `.parity3f877ea`，生成 BuildConfig 的 GIT_SHA 为完整 pinned SHA，版本 0.6.2 / 20。`:app:assembleDebug` 成功，独立包已安装；启动后设备处于锁屏，未采集参考截图。随后设备离线，待重连/解锁，未覆盖现有 0.6.5 应用。

### 27. Bilibili 歌词时长验证与替代音源关联生产链路

- 真源：纯 pinned `MeloXProviderLyricsLoader.kt:307–521, 967–979`。原桌面已有时长/候选评分算法及关联存储，但加载器走通用跨平台匹配，实验开关禁用，缺少生产写入路径。
- 新增 `BilibiliMatchedLyrics.kt`：正时长 + 自动歌词开启时提取最多三个标题候选，使用现有真实 QQ/网易搜索、QQ 歌词和 AMLL；网易目录仅用于 AMLL 寻址，初始阶段不调用网易歌词。主歌词时长不匹配/未知时进入校验；AMLL/QQ/网易三个独立来源的高置信度时长达成共识、当前音频明显不符且开关开启，才搜索替代 Bilibili 音源并写入真实关联。
- 替代关联保留原 bvid:cid 逻辑身份，沿用播放 resolver 在下次打开音源时读取关联；不直接打断当前播放。取消继续传播；请求完成前用户关闭自动匹配/对齐或清空关联时，生产存储不写入晚到结果。
- `MeloXLyricsLoad` 对 Bilibili 使用该专用链路、将对齐开关纳入缓存键并阻止通用迟到 AMLL 升级绕过校验；时长/元数据变化及开关变化触发 UI 歌词重载。实验设置项启用并接入 runtime/真实偏好。
- 新增八项回归：匹配主歌词不触发网易校验、三源共识保存物理关联、关闭对齐仍取歌词而不写关联、缺源/低置信/分歧拒绝替代、未知时长及取消、危险版本匹配拒绝、缓存开关变化及通用升级隔离、生产存储拒绝清空后晚到写入。功能提交 `d1e8bd2`。本地 XML 汇总 **252 tests / 51 suites / 0 failures / 0 errors / 0 skipped**。
- 中文设置物理回放 **controls 10 scenes / 15 assertions、独立进程 reload 6 scenes / 6 assertions** 通过。修正脚本直接寻找实验路由而未先点击设置 tab 的问题；semantics 只定位和断言，实际鼠标/滚轮输入与真实偏好重载。Windows 新 CI 结果另记；真实线上曲目与账号、同内容 Android 帧仍待验收。

### 28. Windows 沉浸恢复修复复验与完整发行独立下载

- Windows run **`39099205289` / SUCCESS**，head **`fdd1392dff14ad19226c7fcf96b8943e1f4d371e`**。完整下载发行和验证 artifacts；重新汇总 XML：**244 tests / 50 suites / 0 failures / 0 errors / 0 skipped**。
- `immersive-window.json` 原生检查 **PASSED**：1024×768 实际全屏；Floating 恢复 600×400，Maximized 恢复 1040×736；原 Fullscreen、手动 placement、销毁后忽略迟到请求均通过。`physical_player_input` 仍为 PENDING。
- 发行 ZIP **332,793,860 bytes**，SHA-256 **`305f6cdaf290366041913f405f7378c412a69ce48fd67ebcdd230bdbcb836908`**。独立验证 357 个 ZIP 条目及内嵌应用 JAR 全部条目 CRC、head 版本戳、16 个 pinned 资源、FFmpeg 大小/SHA/PE magic 均 PASS。
- 证据：`evidence/windows-ci-fdd1392/`。该发行包含沉浸恢复修复，不包含本轮 Bilibili 或液态玻璃新代码。

### 29. 真实液态玻璃光学、连续圆角与稳定帧复用

- 详细来源、参数、性能口径及验收边界见 `GLASS_PORT.md`。固定使用 pinned Android 对应的 Backdrop 2.0.0、Capsule 2.1.3、Shapes 1.2.0；形状源码依赖在构建时核对 SHA-256，只适配 Android Path 调用及移除静态范围注解。
- 用真实背景 SkSL lens/七采样色散替换白层假折射；补振色、按压 smoothstep 光斑、法线 rim、内外阴影，接入面板/捕获层/水珠。修复 48px 按压误差为 4dp，变换包住完整材质，frost 分支关闭液态变换。连续圆角通用路径提取实际角半径后仍可应用透镜。
- 稳定参数 sampler 复用一条有界滤镜链，动态变化替换并释放；静态阴影与 rim 只在缓存失效时录制。无效果链不创建滤镜；交互高光首次按压才编译。开关补页面/轨道组合采样、按压透镜、环境高光，保留真实 checked 状态对确认/拒绝后的回弹约束。
- 进一步将装饰 blur 纳入显式 native owner（DECAL 边界、仅 alpha 变化复用），并在组件 scene 销毁后核对 owned owner/chain 基线。专用滑杆恢复轨道组合采样和变换、10/14dp×p 透镜、环境 rim/阴影及 enabled/frosted 分支；采样采用真实相对坐标映射抵消控件/祖先缩放。
- 已通过的早期光学检查和页面回放不替代最终修改复验。新增真实组件 fixture 的物理按压/拖动、背景更新、不同密度/明暗及反复挂载销毁检查；最终结果另记。用户 Windows 549→1431MB 增长仍未复现/修复，Android 同内容帧仍缺失。
- 最终本地 `auditGlassOptics` 通过：真实 blur/零 lens 逐像素一致，lens/dispersion/vibrancy 分别改变实际背景、零尺寸 lens 跳过、600 稳定帧无新链；64×16 参数变更及重复释放检查通过。组件检查 **12 组**（density 1/1.25/2 × 明暗 × liquid/frosted），覆盖物理 button press/click、switch tap/拒绝 drag、slider tap/drag/禁用、背景更新与每组 8 次重新挂载。缩放取样的全部受检像素对齐；每组 scene 释放后 owned owner=0、created chains=released chains。
- 生产回放复验：设置 **74 scenes / 55 assertions**，独立重载 **8 scenes / 18 assertions**；Bilibili **10+6 scenes / 15+6 assertions**。明暗底栏各 **7 scenes / 9 assertions**，最终 pinned capsule 接线 v3 针对性复验同样通过。功能提交 `ae8408a`。证据 `evidence/glass-optics-local/` 含来源哈希、真实组件图、回放参数/断言/截图和准确的性能适用范围。新 Windows CI 与发行另记。

### 30. 底栏回放发现 Local Library 缺少真实聚合接口

- 初次 Local 来源底栏回放失败：脚本误用了 Bilibili 两-tab 布局的坐标，实际为 Home/Explore/Library/Settings 四 tab。拒绝该次回放结果并保留原始失败画面/断言；更正物理坐标和单段拖动距离，不以直接改导航状态绕过。
- 同时发现 Library 显示“本地音乐不可用”。桌面 `LocalProvider` 声明 Library capability，但未实现 Library 消费的 `LocalAggregationCapability`；纯 pinned Android `core/provider/local/LocalProvider.kt` 已实现该接口，并将 `aggregationTracks` 委托给真实目录 `searchSongs("", page, pageSize)`。
- 补齐相同接口接线，使用现有持久化索引/真实本地身份，未增加假 catalog 或在线回退。功能提交 `1552ba4`。新增回归通过真实 provider registry 读取空索引、三份生成 WAV 对应的持久化记录、两页结果与原文件 URI。最终 XML 汇总 **253 tests / 51 suites / 0 failures / 0 errors / 0 skipped**；Local Library 生产回放明暗各 7 scenes / 9 assertions 通过，完整 pinned Capsule 接线 v3 复验也通过。

### 31. 新 Windows CI 的编译内存失败与显式构建预算

- Windows run **`39140317064` / FAILURE**，head **`68f964bf5e566ff504b184a8304be1c7761e5e53`**。下载验证 artifact 后独立汇总 shared XML：**253 tests / 51 suites / 0 failures / 0 errors / 0 skipped**；但桌面 `compileKotlinDesktop` 在独立 Kotlin daemon 报 `java.lang.OutOfMemoryError: GC overhead limit exceeded`。后续玻璃/UI 检查未运行、发行包未生成，不能称为 Windows 通过。
- 新增根 `gradle.properties`，将普通 Gradle/CI 构建也固定为本地已使用的 **1024m / SerialGC / UTF-8 / max-workers=1 / Kotlin in-process**；不再让 Kotlin daemon 继承未显式指定的较小默认堆。配置只约束构建进程，打包应用 JVM 选项仍由 `desktop/build.gradle.kts` 定义，不能将此变更声称为播放器内存修复。
- 失败日志、run 元数据、下载 XML 汇总及失败边界保留于 `evidence/windows-ci-68f964b/`。本地不带临时内存覆盖的强制重新编译及新 Windows 结果另记。
