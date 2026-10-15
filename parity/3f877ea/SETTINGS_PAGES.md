# Windows 设置页对 Android pinned 真源的复刻与验证

状态：**桌面端布局/路由/文案/交互复刻已实现并本地回放通过；Android 像素与动态验收仍 BLOCKED。**

## 1. 真源与导出契约

* Android pinned commit：`3f877eaffe30c82eb6d24eb3647702f5bc554dd3`，policy compatibility `20`。
* `tools/export_settings_page_resources.py` 只从该 pinned Git 树读取（`git show`），不读取当前 Android 工作区：
  * 根路由 **19** 条 / 分组 **3** 组（顺序与源一致）；
  * 设置页文案 key **797** 个；
  * 四份资源 `pinned-settings-pages{,-zh-rCN,-zh-rTW,-ja}.properties`；
  * 同时导出 `parity/3f877ea/evidence/settings-pages-source-audit.json`，记录源文件 SHA-256、资源 SHA-256 与分组结构。
* 运行期 `pinnedString()` 按 `Locale.getDefault()` 选择：简中（含 `Hant`/TW/HK/MO → zh-rTW）、繁中、日文，其余回落英文默认资源；缺项回落默认资源；带参数时按当前 locale 格式化。
* `.gitattributes` 对新资源强制 `text eol=lf`；`tools/verify_pinned_resources.py` 已把四份资源纳入逐字节哈希校验。
* 本地核验：`python3 tools/export_settings_page_resources.py <android> --verify` → PASS（19/3/797/4）；
  `python3 tools/verify_pinned_resources.py --sources --output ...` → PASS（12 pinned resources，含 Android MiLan Pro）。

## 2. 页面结构复刻

* 根页分组与顺序 = 源：`应用`（General/Appearance/Content/Playback/Lyrics/Storage）、
  `更多`（Features/Recognition/Messages/ListenTogether/TabLayout/SystemPlayback/SkylineLyrics/FloatingLyrics）、
  `关于`（RemoteConfig/About/Legal/Developer/Experimental）。隐私不再是根入口（与源一致）。
* **所有** detail 页统一使用 `MeloXPinnedListPage`（大标题折叠 + 62dp 工具条 + 返回），与 Android `SettingsDetailScreen` 一致；
  旧页面自带的额外 20dp 横向内边距 / 10dp 顶部间距已移除，避免 double padding。
* 每个分组使用 `SettingsGlassGroup` / `MeloXIosGroupedList`，行间距 10dp 与源一致；
  源 `SettingsToggleRow` 接收 `note` 但**不渲染 subtitle**，桌面端同样只渲染标题。
* 文案不再硬编码中文：General/Appearance/Content/Playback/AutoMix/EQ/Storage/About/Remote/TabLayout
  全部改走 pinned key；TabLayout 页面标题、首页区块、顺序标题也改为 pinned key。

### 已按源重排的关键点

* **General**：主题 / 启动页 / 记住上次标签页 / 完整侧滑操作 / 剪贴板链接 / 触感反馈 / 系统字体 / 底栏不自动缩小（同一组）。
* **Appearance**：播放器外壳 → 独立毛玻璃 toggle → 背景组（3 种模式；FlowingLight 时显示 3 个滑杆 + 背景隔离 + 背景帧率）
  → 减少动态/封面动效/亮屏范围 → 沉浸式播放 → 音库搜索栏与封面两行。
* **Content**：地区值为 `全部/华语/欧美/日本/韩国`（旧实现错误使用 `日语/韩语` 已修正），其后两个开关与跨平台回退。
* **Playback**：音质组 → EQ → AutoMix（含智能队列引导、过渡模式、尾巴裁剪、置信度、固定时长、预载提前量、音量曲线、
  分析回退、节拍匹配与最大调整幅度）→ 分析缓存清理。
* **Storage**：真实用量统计（下载/网络/播放/临时/本地 + 设备总量与可回收）、清理与确认、下载歌词与自动缓存
  （阈值 + 音质）、下载列表/取消、删除全部、修复、清临时。
* **About**：版本与维护者说明、GitHub 下载源（真实测速/选择）、真实 Release 检查与真实 `commits/main` 开发版检查、
  真实日志导出（JFileChooser + `MeloXLogExporter`）、真实恢复播放器设置、许可证说明与外链。

## 3. 缺少平台后端的能力：显式禁用，绝不假成功

以下控件在源端有后端、桌面端没有对应实现。它们**保留源端布局与文案，但控件禁用**，
并在页面底部给出 `settings-platform-availability` 说明；**不会写入偏好、不会出现“能开关却不生效”**：

| 页面 | 禁用项 | 原因 |
| --- | --- | --- |
| General | 剪贴板链接、触感反馈、系统字体 | 桌面无对应 OS 适配层 |
| Appearance | 沉浸式播放 | 桌面无系统状态栏隐藏语义 |
| Playback | 系统音量/后台播放/记住最近歌曲 | 依赖 Android 音频焦点与媒体会话 |
| SystemPlayback | 系统歌词、标题格式、通知、通知模板与测试 | 依赖 Android 媒体通知 / 状态栏歌词 |
| FloatingLyrics | 悬浮歌词全组 | 依赖 Android overlay 权限 |
| Recognition | 时长选择与开始按钮 | `SongRecognitionClient.recognize()` 在 pinned 桌面仍为空实现，不伪造录音识别 |
| ListenTogether | 发起/加入/粘贴 | `MeloXListenTogetherCoordinator` 为内存 room/no-op，不伪造同步 |
| About | 自动检查更新开关 | 桌面无自动更新调度器；手动检查为真实实现 |
| Experimental | 独立播放账号开关 | 登录界面尚不接受 playback slot，避免“开启即静默失败” |
| Experimental | Bilibili 音画对齐开关 | 桌面无该消费者；关联清除为真实存储清理 |

Developer 页：pinned release 构建不含 DEBUG-only 控件，因此按源保持空内容。

## 4. 真实消费者

所有仍可操作的开关/滑杆/下拉都写入 canonical key，并由既有消费者立即生效，例如：

* FlowingLight `player_flowing_speed/saturation/brightness` → `MeloXFlowingLightBackdrop` 真实渲染参数；
* `library_playlist_search_hidden` / `library_playlist_show_artwork` → 音库列表真实行为（别名 key 同步）；
* `music_area`、`content_*`、`download_lyrics`、`downloads_auto_cache*` → 对应运行时/下载存储；
* EQ `equalizer_*` → 真实 DSP；AutoMix `automix_*` → 真实 PCM 分析与过渡；
* `experimental_lyric_strong_binding` → 真实歌词绑定存储（需开启自动选词）。

## 5. 回放证据

* `tools/capture_settings_pages.py routes|controls|reload`：
  * **routes**：4 个 locale（en-US / zh-CN / zh-TW / ja-JP）× 多窗口尺寸与明暗主题，物理鼠标逐个打开 19 个根路由并返回，
    校验 pinned 标题文本、禁用态，并用**真实键盘事件**输入无匹配搜索词校验空结果；
  * **controls**：物理鼠标/滚轮改真实偏好（主题、侧滑、背景三滑杆、背景模式与条件显隐、音库两行、地区、
    EQ 开启+Custom+第 10 段、AutoMix 模式与时长、下载歌词、清理确认/取消）；
  * **reload**：**独立 JVM** 重新读取同一 home，校验运行时与偏好确实持久化且条件控件按新状态显隐。
* `tools/verify_settings_pages_capture.py` 汇总为 `desktop/build/verification/settings-pages.json`：
  **6 captures / 395 scenes / 316 assertions**（4 locale 路由 + controls + 独立 JVM reload）。
* 旧回放适配新页头后重新执行：60-key 歌词控件 + 独立 JVM reload（PASS，全部由指针输入改变）、
  17-key Skyline 控件 + 4 种亮屏范围 + 精确重置 + 2 次独立 JVM 重载（PASS）。
* `:shared:desktopTest` **208 tests / 40 suites / 0 failures / 0 errors**（新增：恢复队列延迟准备、Local 队列元数据持久化）；
  `:desktop:auditArtworkMemory`（2048 张、预算封顶、无强制 GC）与 `:desktop:auditArtworkDecode`（48 组 codec/viewport）通过。
* 上述回放全部在**与发行包相同的 JVM 参数**（见第 6 节）下执行。
* CI 已加入：四 locale 路由回放、controls/reload、汇总校验、封面解码审计 `:desktop:auditArtworkDecode`、冷启动内存测量。

## 6. 启动内存（与播放后持续增长分开统计）

1. **恢复队列延迟准备音频**：`PlaybackCommands.restoreLastQueue()` 现在以 `prepare(resolveAudio = false)` 发布歌曲与进度并保持暂停，
   直到用户按下播放才解析音源、启动 FFmpeg/PCM 会话。`DesktopPlaybackStateTest` 断言恢复后 10 秒内 0 次解析、0 次 start、
   `isLoading=false`/`isPaused=true`，按下播放后一次性解析并保留 seek 位置；Local 队列的 `contentUri/fileKey` 现在随队列持久化，
   不再在重启后丢失。
2. **封面按实际显示尺寸解码**：`decodeMeloXArtwork()` 对 JPEG 使用 Skia codec 采样解码并缩放到 Crop 视口（64px 分桶缓存 key），
   只保留视口所需像素；同时解码并发限制为 2。`:desktop:auditArtworkDecode` 用生成图像校验 48 组 codec/viewport 组合、
   alpha 与非法输入清理。
3. 既有 128-entry / 64MiB 封面与背景共享 LRU 预算、lease 归还即释放、生产不强制 GC 保持不变。
4. **启动 JVM 参数收敛**：同一 jar、同一隔离 home、同一场景各 3 次的中位数对比（`tools/capture_startup_memory.py`）：

   | JVM 参数 | 首帧常驻 | heap used | heap committed |
   | --- | --- | --- | --- |
   | `-Xmx1g`（旧生产参数，G1） | 218.4 MB | 25.1 MB | 38.8 MB |
   | `-Xms16m -Xmx1g`（G1） | 216.8 MB | 15.7 MB | 34.6 MB |
   | `-Xms16m -Xmx1g -XX:+UseSerialGC`（**新生产参数**） | **196.3 MB** | 18.0 MB | **19.3 MB** |

   即首帧常驻 **−22.1 MB（−10%）**、堆提交 **−19.5 MB（−50%）**；三组分布不重叠
   （Serial 最大 197.5 MB < 旧参数最小 203.6 MB）。`jpackage --java-options`、
   `captureUi`（所有回放）与冷启动测量统一使用同一组参数，避免“测的不是发的”。


冷启动测量由 `tools/capture_startup_memory.py` 在隔离 home 中运行真实 `MeloXVisualCapture` 场景完成，数值见
`desktop/build/parity/startup-memory/startup-memory.json`（CI 同名产物）。**注意**：这只统计启动阶段，
不能代表播放后 549MB→1431MB 的持续增长问题——该问题仍单独定位中。

`restored-queue` 场景（把**应用自己写出的**持久化队列放回隔离 home）在新生产参数下 3 次中位数：
常驻 **210.3 MB**、堆提交 **31.4 MB**，三帧全部 `is_loading=false` / `is_paused=true` / 曲目已发布——
即恢复队列**没有**创建 FFmpeg/PCM 会话（旧实现会先解析音源并启动解码再暂停）。该场景的输出不含歌曲标题或 provider id。

## 7. 仍为 BLOCKED 的验收

* **Android 像素/动态验收 BLOCKED**：本机无 pinned Android 设备截图与动态帧，Linux/Windows 自比对不能替代；
  旧 `MATCH` 结论不继承到本次页面。
* 实机待机/睡眠、DPAPI、SMTC、设备切换、真实硬件听感、Provider 真账号验收仍缺。
* Recognition / ListenTogether 后端仍为 pinned 的空实现，不能宣称功能可用。
* 整体进程内 native renderer/text/allocator 增长尚未定位完成。
