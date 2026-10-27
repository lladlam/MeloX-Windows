# 全页面／双 UI／功能补齐审计

## 当前检查范围与判定方式

本轮从 Windows `4c94f7e` 开始，直接读取本机 Android 工作区（含未提交修改）。固定 UI 的既有来源仍保留，功能增量跟随当前 Android；不改写历史验收结果。

`tools/audit_feature_surface.py` 索引 32 个功能域，并另存 **全部 Android UI、Windows UI 和 shared 业务源码**的路径与 SHA-256，记录见 `evidence/feature-surface/source-index.json`。按实际路由检查 Android Activity 在 Windows 上对应的覆盖层、侧栏详情或工具窗口。文件存在、名称相似、已保存偏好或编译成功均不等于功能完成；无 basename 对应也不能直接判定漏页。

当前设置字面 key 差分仅余 `player_transition_duration_ms__migrated_v2`，属于 Android 一次性迁移标记，并非用户选项。此差分只说明 key 的静态存在性；表内记录的模型推理、平台后台、渠道账号等能力缺口仍然成立。

## 本轮确定缺口与补齐

| 项目 | 检查结论与改动 | 验证范围 |
| --- | --- | --- |
| 应用语言 | Android General 有语言选择；Windows 仅随进程 Locale。新增 system／简中／繁中／英文／日文，偏好即时保存，已有 pinned 与 desktop 文案消费者观察应用语言，独立于宿主 JVM Locale | 双布局实际点击切换及独立 JVM 重载待完成；硬编码文案、桌面参考仅中英文等覆盖缺口仍存在 |
| 手机版专辑收藏 | 原 `AlbumDetailScreen` 的 onClick 为空；两套 UI 现在共用真实 setAlbumSubscribed，读取到真实收藏状态且登录后才启用，完成服务器操作后更新，账号变化重新加载 | 公共详情与私有账号写入需分别验收；不把单元测试当私人账号验证 |
| 手机版专辑下载 | 原 onClick 为空；接实际 MeloXDownloadStore 与用户音质，空列表或关闭下载时不可点 | 共享下载业务既有回归；本次专辑入口尚待回放 |
| 专辑分享／重试 | 原分享是无行为图标、加载错误缺重试；增加真实系统剪贴板专辑链接、操作状态和重试 | Windows 原生剪贴板须实机验收 |
| 隐私页入口 | Privacy 已实现，手机根设置清单缺入口；加入统一目录和搜索，电脑同组保留 | 双布局导航待回放 |
| 电脑歌词／布局设置 | 通用 LyricsGroup／LyricsToggle 和布局组仍直接绘制手机版控件；增加独立 DesktopSettingsForm、Checkbox，保留已有所有选项、tag 和偏好 | 真实指针切换及持久化待回放 |
| 自定义背景 | Android 当前 player_custom_background_path 在 Windows 完全缺失；新增图片模式、双 UI 选择／清除入口、私有目录复制、最长边 1280、有损坏／缺图回退；播放器与歌词分享图片消费者接线 | 有界导入、原图保留、错误不破坏旧图、仅清理应用自有图片回归；原生文件选择器待实机 |

## 持续补齐清单（不能标为全部完成）

| 页面／业务 | 两套 UI 当前证据或入口 | 尚需核销 |
| --- | --- | --- |
| 首页／发现 | `HomeScreen`、`ExploreScreen` 与 DesktopHome／Explore；真实 provider feed／ranking | 本地推荐、播客内容块、统一刷新、源快捷工具和同内容动画 |
| 搜索 | 手机 Search、DesktopSearch，多类型 provider 能力与真实接口；实时增量新增五源音质提示、可播优先推荐、时长与合作歌手去重，见 SEARCH_QUALITY_UPDATE.md | 网易云限流实网复验、分页／取消／菜单完整一致性、所有分类转场；其余来源提示继续核对 |
| 音乐库／歌单 | 手机 Library 与 DesktopLibrary，源身份和分页保留 | 私人收藏、自建／已喜欢权限、批量写入与成员排序实机 |
| 专辑／艺人详情 | 共享真实网络业务＋双布局详情；本轮修复手机版专辑空操作 | provider 完整目录分页、账号写入、所有分享与封面 hero |
| 歌曲详情／百科 | `MeloXSongDetail`、DesktopSongDetail 入口 | 按 Android 歌曲百科信息块逐项对照、所有源能力边界 |
| 歌曲菜单／收藏 | 手机 SongActions 与 DesktopSongMenu／PlayerFavorite；新增应用自有统一「我喜欢」、逐来源初始化／部分失败报告、全元数据持久化、跨源收藏开关与双布局独立库入口；`d547727` 的 Windows CI 与完整发行独立核验已通过，见 UNIFIED_FAVORITES_UPDATE.md | 私人账号实际导入／原生收藏写入、切账号实机、多来源完整接口验收 |
| 下载／批量下载 | MeloXDownloadStore＋手机 Downloads／电脑 library pages | 权益、失败恢复、目标目录与批量任务实机 |
| 云盘 | 手机 Cloud、电脑 library pages，真实上传接口 | 原生文件选择器、实际上传／删除与账号切换 |
| 播客 | 手机 Podcasts、DesktopPodcasts | 收藏与节目详情实机、封面共享动画 |
| 私信 | 手机 Messages、DesktopMessages，真实联系人与发信接口 | 头像／未读／分组／刷新和真实发信验收 |
| 来源选择／账号 | 两套音乐服务页、共享登录表单；汽水 QR 已有真实上游回放 | 概念版来源缺失；Apple／酷我隐藏策略与当前 Android 对照；所有真实账号验收 |
| YouTube Music | Cookie／visitor／data-sync／page 上下文导入、真实库与播放 | Android authUser／地区、自动校验、过期状态、频道枚举／切换 UI 与 backend 尚未迁移 |
| 酷狗概念版 | Windows 当前为标准酷狗 provider | **独立 KugouLite provider／账号隔离／真实双奖励接口／每日去重重试／签到页全部缺失**，不能把标准酷狗改名冒充 |
| 汽水 | `ec5753a`：真实协议、双布局、账号 QR、游标、歌词与试听识别 | 用户真实扫码确认、登录态搜索与完整播放 |
| Bilibili | `4c94f7e`：收藏的他人列表／合集、分页与单视频隔离 | 用户私人列表；WBI 播放及音质细节与最新 Android 持续对照 |
| 播放回退 | `566d099`：native → 内置串行严格完整匹配 → CHKSZ／LX 调用入口 | **LX 自定义 JS 运行时仍是空实现**：load 无操作、callAction 恒 null、close 无操作，不能将调用入口记为真实脚本执行已完成；概念版候选、真实账号／权限与网络验收仍待补齐 |
| 播放器／队列／MiniPlay | 两套独立呈现＋真实 PlaybackCommands／PCM；既有 Dock／AutoMix 证据；队列新增全 provider 元数据持久化并兼容旧字段 | MiniPlay 材质与侧反射、横竖屏同一时钟、所有页面封面 morph；Android 实时新增 AutoMix 双封面调色板合并待迁移 |
| 歌词／罗马音 | 双布局渲染、语言链、真实歌词；本轮接 JapaneseRubyAlignment 及视觉行字符范围；348 tests 与双布局 17 scenes／18 assertions 通过，见 ROMANIZATION_070_UPDATE.md | 线上同内容日文曲目、天际／EVA／TextPV 同内容动画验收 |
| 歌词样式选项 | Windows 仍保留 Apple Music／EVA／TextPV 可选 | Android 0.7 当前仅显示 Apple Music；现有用户设置兼容策略需随迁移明确核销 |
| 歌词分享 | 已有真实 PNG 导出，本轮接自定义图背景 | 长按多行、背景与排版同内容对照、原生保存对话框 |
| AutoSync 模型 | 当前没有 Windows 等价模型面板／完整推理链 | **ONNX／中文 CIF／日文 G2P、模型下载校验／存储所有权、真实推理均待实现** |
| EQ／AutoMix | 实际 PCM DSP、智能队列分析额度与既有 Linux／Windows 回归 | 物理听感、长时内存和所有硬件输出接入 |
| LAN／DLNA | 进程级会话、两套输出面板、真实协议与回环测试 | Windows↔Android 跨机、多网卡／防火墙／长时漂移、物理音箱 |
| MiPlay／USB 独占 | Android 为 Binder／usbfs 平台实现 | **Windows 对应 backend 未实现**；不能仅增加开关称支持 |
| 系统媒体／悬浮歌词 | 有设置页，未支持项显式 disabled；已跟踪 Android 统一 loader 值参数、后台完整 media ID／多源歌词更新 | Windows SMTC、悬浮歌词窗／权限与原生输入等价实现，并复用同一真实歌词链 |
| 引导／法律／关于 | 现有两套 Onboarding／About＋legal 文档页 | Android 0.7 全屏连续引导、1.3 版文本的 Windows 平台适配与品牌逐项核销；10 月 27 日新增关于页两名赞助者及统一 CNY／取消包月标记的文案尚未同步 |
| 云控／主题／规则 | 当前签名平台熔断与基础配置已实现 | 新 schema 的文案／主题／规则消费者与受限 QuickJS 脚本未完成 |
| 多语言 | 本轮补实际语言选择；已有 pinned 资源保持原文 | 全量最新资源导出、硬编码文案、土耳其语、桌面全部 Locale 覆盖 |
| Windows 平台集成 | 布局切换即时保存、托盘、标题栏、键鼠已有实现／部分原生证据 | 用户触屏／触摸键盘、DRM、麦克风、长时 RSS 及 DPAPI 实机验收 |
| CLI 模式（实时新增） | Android 新增 experimental_cli_enabled 设置、受 DUMP 权限约束的广播入口及 docs/CLI.md | Windows 运行进程的 CLI／IPC 后端、状态／队列／播放／搜索命令及双 UI 开关尚未实现；不能把 Android ADB 工具当作 Windows 控制能力 |

### 继续追查“保存了但没有消费者”的选项

额外扫描 Runtime 属性在设置定义／写入之外的引用，发现 `performanceOverlayEnabled` 缺 Windows 显示消费者；旧 `flowingBackdropEnabled` 开关与实际 background mode 选择的关联需补核。系统通知／悬浮歌词相关未消费属性对应的 UI 已 disabled，属于上表平台 backend 缺口。`downloadLyricsEnabled` 虽无属性引用，下载器实际读取 `melox_downloads.download_lyrics`，不能误报未实现；底栏禁止收缩也实际按偏好 key 读取，但启用时是否立即展开仍需回放。属性名搜索仅为线索，按真实消费者核销。

六处剩余空点击已检查上下文：App 与 GlassSheet 的弹层内容、LAN／Quality 弹层内容用于消费内部点击以避免穿透到背景关闭；播放器 controls 背板用于阻断拖动／页面底层命中。均为输入屏蔽，不是可见业务按钮，不机械删除。具体行与源码哈希在 source-index 中保留。本表持续更新，已知平台／模型缺口不会因为其他批次通过而标为完成。

## 验证记录

本地最终：**67 suites / 341 tests，零失败／错误／跳过**；桌面编译通过。自定义背景回归覆盖尺寸约束、原图不变、解码失败保留旧图、清理不删除原图或无关文件、过大输入提前拒绝。原四模式测试扩展为五模式及独立 CustomImage kind，最终以 v4 成功日志为准。

`tools/capture_feature_surface.py` 使用隔离 home 与真实指针／滚轮／键盘，双布局 controls + 独立 JVM reload 共 **98 scenes / 62 assertions**，全部通过。验证简中→日文→英文时实际 Theme 文案改变，并在系统进程 Locale 为 zh-CN 时重载保留英文；隐私页可达，歌词翻译开关真实保存，CustomImage 模式与导入入口可用。未在此回放打开原生文件选择器，也未读写私人专辑账号。

最后专辑账号边界修正：按专辑 ID 与会话 revision 创建独立 composition／协程 scope，使用固定会话 Cookie 的请求 client，切换账号时销毁旧详情与操作任务；修正后额外桌面编译通过。共享业务无需因该 UI 生命周期修正重复全量运行。

证据：`evidence/feature-surface/summary.json`、四组 UI capture／截图／semantics／压缩日志、shared-tests 及最终编译日志。Android 扩大监听新增 164 个原已存在的 XML／本地源码／i18n 文件；原共同监控文件内容变化为 0，新增监控不冒充 Android 本次刚新增代码。

设置提交 `8186c3b` 的 Windows CI `40170895379` 在共享测试阶段因 `MeloXLanProtocolTest:36` 的真实 loopback 监听端口无法绑定而失败，尚未产生可验收的新发行。原测试随机选择 49000 段，可能落入 Windows runner 保留端口区间；已改为先由操作系统选择可用 loopback 端口，真实 socket／鉴权／协议断言保留。失败日志见 `evidence/feature-surface/windows-ci-40170895379-failed.log.gz`，后续重跑结果另记。

端口修正已包含在后续 348 tests 的全量成功回归中；额外修正“错误 PIN 固定为 0000 恰好等于随机真实 PIN”的低概率不确定性后，独立重跑该套真实 loopback 协议测试 3 项全部通过。证据 `evidence/romanization-070/lan-port-selection-validation.log.gz` 与 `lan-protocol-final.xml`。

## 2026-10-27 再核查

随后 Android 又新增 `LxRuntimeDeviceRunner` instrumentation 回归源码，覆盖同会话双调用、2.3 秒延迟 HTTP 回调、驱逐后拒绝调用和新会话重建。已读取源码；未运行该设备 runner，不将其存在当作实机通过证据。

后续监听又捕获 3 个 LX 文件变化：Android 新增 `LxRuntimeThread` 和并发／清理测试，将 QuickJS 创建、执行与销毁限制在同一 owner 线程；动作超时或中断后关闭会话，取消 HTTP 并屏蔽迟到回调。追查 Windows 对应文件发现更基础的缺口：`shared/src/commonMain/kotlin/melox/provider/lxuser/LxUserRuntime.kt` 仅 8 行，`load`／`close` 为空、`callAction` 恒返回 null；`LxUserPlaybackResolver` 虽有播放／歌词／封面调用链，实际无法执行用户脚本。因此这是未迁移的真实业务后端，不只是本次线程修复未同步。CHKSZ 为独立客户端，不能用其成功证明 LX 脚本运行时可用。

直接重新读取 Android 工作区并与 `d547727` 的内容哈希快照比较，新增变化为 5 个文件：SettingsScreen、默认 strings.xml 及简中／繁中／日文 i18n。具体是关于页增加 user_361ef（CNY 20）、user_f8b67（CNY 12.24），并将原 6 元包月标记改为 CNY 6、金额统一 CNY 格式。Windows 的 PinnedAboutSettings／DesktopAbout 尚无对应赞助列表，属于新增未迁移内容。

32 域源码索引重新生成；设置 key 差分仍仅为一次性迁移标记，六处空点击仍是此前已核查的输入屏蔽。复核确认 `performanceOverlayEnabled` 目前只有 shared 定义／偏好读写，没有 desktop 显示消费者；FlowingLight 仍以单 artworkUrl 提取，未接 AutoMix 双封面调色板。KugouLite／CLI／YouTube authUser 等缺口仍保留，不能以设置 key 齐全判为功能已齐。

上一批 **`d54772721bd777d12e7d1ca6a20bff4f9f069c9e`** 的 Windows CI `40325542270` 成功，新增统一收藏回放 step 成功。完整下载独立核验 ZIP **339799993 bytes／357 entries**，源码 stamp、全条目 CRC、固定资源与 PE 解码器检查通过；SHA-256 `28d940af34a0118243a1a793825122a70db1d649af57855218aa4a429324bb45`。证据 `evidence/windows-download-40325542270/`。私人账号、跨机互联和物理听音边界仍按上表保留。
