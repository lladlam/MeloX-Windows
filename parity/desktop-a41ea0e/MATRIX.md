# MeloX Desktop 一比一迁移矩阵

## 固定真源与验收口径

- 仓库：<https://github.com/lladlam/melox>。
- 电脑端固定提交：`a41ea0e1e1fa69fb543d827b853fe3aff5420951`。
- 重新克隆的位置：`/mnt/ntfs_files_sda4/项目/MeloX-Desktop-Reference`。
- 这是独立的 **macOS Desktop → Windows** 迁移目标。Android 正式 `v0.6.5 / 53244b9 / versionCode 24` 的资源、矩阵和验收记录继续独立维护。
- 用户要求覆盖整套 UI；总状态为 **PARTIAL**。接入真实生产页面、源尺寸计算通过或 Windows 自身回放成功，均不等于该页面的一比一视觉／动态验收完成。
- `build/desktop-reference-a41ea0e/` 中官网下载的截图只用于布局考察；不是从该固定提交、相同账号／内容／尺寸取得的像素基准。

## 首批实现

设置 → 通用 → **电脑端布局 / Desktop layout**。偏好键 `general_desktop_layout` 默认 `false`，即时更新运行态并持久化；冷启动关键偏好读取包含该键。

切换时保留同一个主页面 composition 和可移动详情 composition；播放器／队列仍使用现有 `AudioPlayer`、`PlaybackCommands` 和生产 FFmpeg。桌面布局阻止 Android 左右等分和旧底栏重复出现。Windows 设置入口为平台接线文案，不冒充真源菜单文案。

源指标已接入：侧栏初始宽 199、外距 8；底栏 700×54、内距 9、transport 148、cluster 9、底距 10；右侧面板 258；面板出入 280ms，减少动态关闭时长；首页横向留白 52、hero 最小 200／间隔 20／4:3 竖卡、媒体最小 145／间隔 24；播放器尺寸按源 `DesktopNowPlayingLayout` 计算；歌词 viewport 字号断点 24／28／38／50／72。这些数值不涵盖原生材质、字体栅格和整页像素差。

Windows 首次以已保存桌面模式启动采用 1280×800；启用时设置原生内容最小 980×540 并提升过小窗口，关闭时释放该最小尺寸约束。原生 Windows 高 DPI／frame inset 的实机适配仍需验收。

## 全部界面追踪

| 真源界面 | Windows 状态 | 仍需完成的一比一工作 |
|---|---|---|
| `App/DesktopRootView.swift`、`Features/Sidebar` | PARTIAL：独立桌面框架、真实导航、账号／设置可达 | 原生 titlebar、安全区、材质、侧栏滚动／loading footer、账号弹层；当前账号入口接已有设置账号区 |
| `Features/Home` | PARTIAL：首页标题／卡片首批、真实首页内容与六个动作接线 | 完整分页 shelf、最近听歌／为你定制／新专辑／播客各区块；卡片元数据／封面层、详情与加载／错误样式 |
| `Features/Search` | PARTIAL：桌面标题、520 宽输入、紧凑 scope、桌面推荐卡片／分类网格／曲目密度；复用真实跨 Provider 搜索与详情跳转 | 搜索建议弹层、macOS toolbar scope、专辑／歌手／歌单完整网格和逐菜单像素／动态验收 |
| `Features/Explore` | PARTIAL：桌面 32pt 标题、宽幅 hero、自适应 145pt 媒体网格、20pt 间隔；真实分类、榜单和 Provider 数据 | macOS 固定提交的 discovery／radio 同内容帧、分类工具栏、全部加载／错误／详情状态 |
| `Features/Library` | PARTIAL：侧栏直达真实子页；歌曲／歌单已采用 34pt 留白、54pt 紧凑行、40pt 封面和 520pt 分页栏 | 专辑／歌手能力、完整网格、筛选／排序／刷新／空态、拖放及全部菜单 |
| Albums／Artists 收藏入口 | BLOCKED：可见但禁用，附后端不可用说明 | 实际收藏能力消费者与真源桌面页面；禁止空点击或填充假集合 |
| `Features/Detail`、`Features/Playlist` | PARTIAL：新增 278pt collection header、32pt 标题、桌面操作区与曲目表；歌单／专辑接真实播放、随机、收藏、下载、搜索和菜单 | artist／song／podcast 专属布局、编辑／删除／分享弹层及同内容帧验收 |
| `Features/Player/DesktopBottomPlayer.swift`、`BottomBar` | PARTIAL：源尺寸、真实播放／暂停／切歌／进度／音量、歌词／队列入口 | 元数据 hover 展开、时间／进度过渡、玻璃材质、issue／retry、禁用态与原生音量细节 |
| `Features/Player/Queue` | PARTIAL：history／up next、按稳定 entry ID 选曲、清除／移除、真实 AutoMix／autoplay；沉浸队列按参考图显示模式文字与选中状态 | drag-and-drop、fade、hover、精确字体／间距和所有菜单细节 |
| `Features/Player/Inspector`、`DesktopPlayerSidePanel.swift` | PARTIAL：258 宽面板／280ms进退、真实歌词／队列 | 材质、选项切换 crossfade、原生窗口宽度增长策略和尺寸／动画对照 |
| `Features/Player/DesktopNowPlaying*` | PARTIAL：源几何、真实播放器、歌词／队列并列、暂停封面缩放 | 顶栏 accessory／独立迷你窗口、收藏直控、信息／quality／output 菜单、全套展开收起动效 |
| `Features/Player/*Lyrics*` | PARTIAL：复用真实加载／字级渲染，接入 desktop 前景色、字号断点、左右 20、段距及 focus 输入 | 完整 macOS motion profiles、逐行焦点的精确高度求解、mask、词级 lift／spring、其他歌词样式和全部扩展窗口 |
| `Features/Settings` | PARTIAL：650pt 真源基础扩展为 Windows 自适应居中窗口；五类顶部导航、组内子页、账号与全部现有真实设置均可达 | 各控件改为 macOS 原生列式 Form 几何、独立窗口生命周期、底部按钮区、弹窗及逐页文案／像素验收 |
| `Features/Account` | PARTIAL：侧栏账户入口显示居中桌面 sheet，按截图提供私信、云盘、下载、账户设置、登录／登出和完成操作；操作连接实际页面及会话状态 | 真源 500×600 account sheet、880×680 网易 Web 登录专窗、窗口生命周期及逐状态验收 |
| `Features/Messages` | PARTIAL：286pt 联系人侧栏＋会话双栏，真实加载／发送／错误链保留 | 真源联系人头像与时间、消息方向／资源卡、回复／菜单、分页和逐状态验收 |
| `Features/ListenTogether` | PENDING：已有生产 Together 能力 | 桌面加入／邀请／会话／退出各弹窗；双账号实机回放 |
| `Features/SongRecognition` | PENDING／能力门控 | 桌面识曲弹层、真实麦克风／后端、权限／失败态及物理输入验证 |
| `Features/Onboarding`、`Features/About` | PENDING | 桌面引导／关于／隐私／协议／更新 UI、文案与窗口行为 |
| 快捷键、右键菜单、拖放、独立窗口、系统托盘／媒体控制 | PARTIAL：沿用已有 Windows 生产能力 | 对照真源逐个动作接线与物理输入验证；原生 macOS 项明确映射到 Windows 实际能力 |

## 图标与文案

`tools/export_desktop_reference.py` 直接从固定 Git blob 导出选定原文；`source-audit.json` 记录源文件及资源 SHA-256。当前导出 `en`／`zh-Hans` 两种真源资源，简体中文按系统 locale 选择，其他 locale 回退默认英文；Windows 开关／设置入口文案定义在 `MeloXDesktopStrings.kt` 的独立平台函数中。

`surface-inventory.json` 从固定 Git archive 枚举 App／Features 下全部 124 个 Swift 文件、16 个分组；未进入本批的文件继续标记 PENDING，首批接线文件为 PARTIAL，没有以目录级别的“已复用”关闭验收。

桌面 SF Symbol 子集已从 Apple 官方 SF Symbols 27 `SF-Pro.dmg` 重建，固定真源引用的 135 个名称、134 个唯一码点及全部 67 项 `MeloXSymbol` 均有非空轮廓可追溯。`square.grid.2x2`、`icloud`、`square.stack`、`music.mic`、`bubble.left.and.bubble.right` 与真实 `heart.fill` 已替换此前的相近符号；源 DMG／字体／子集哈希、构建与独立校验工具记录在 `desktop-symbols.json`、`tools/build_desktop_symbol_font.py` 和 `tools/verify_desktop_symbols.py`。

这关闭的是图标资源名称和字形差异，不等于图标在 macOS／Windows 上的像素位置、光学尺寸、层级与过渡完全一致；这些仍随各页布局和动态验收逐项核对。固定 Android 基线继续使用 `circle.grid.2x2.fill` 等原有 token 映射，不能由本桌面子集反向覆盖。

## 验证与证据

`tools/capture_desktop_layout.py` 从生产入口发送真实指针、滚轮和滑块拖动；semantics 只用于定位／断言。controls → reload → restore → reload-current 各自独立 JVM，验证偏好磁盘重载。生成 WAV 和 LRC 只存在隔离验证目录，经过生产 Local scanner／歌词存储／FFmpeg 解码；不会注入远端 provider 或用户音乐库。

切换连续性断言记录真实曲目 ID、位置及已打开 PCM 输出数量，再验证多次切换无曲目改变、无回退、无输出重建且仍播放。PCM 输出明确标注 `VALIDATION_SOFTWARE_PCM_OUTPUT`，不是硬件听感验收。浅色歌词、深浅首页和播放器另有截图。

本批本地结果：274 tests／56 suites；桌面七组 75 scenes／139 assertions；旧布局三组 70 scenes／95 assertions；全部通过。原始参数、输入、capture、semantics、源指标检查及构建日志在 `evidence/local/`；详见 `COMPLETION_LOG.md`。Windows CI 增加七组桌面回放并上传原始文件，Windows 结果仍须单独完整下载核验。

Android 正式固定版同内容静态／动态帧、Windows 用户 549→1431MB 长时增长、真实账号／硬件／DPI 实机问题继续按原矩阵跟踪。
