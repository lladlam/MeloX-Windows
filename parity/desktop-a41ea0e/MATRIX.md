# MeloX Desktop 一比一迁移矩阵

## 固定真源与验收口径

**用户纠正后的硬约束：电脑模式的唯一UI基准为MeloX Mac Desktop；手机版已迁移UI仅供参考，不能作为桌面页面、弹层、菜单或动效的实现模板。可以共用真实业务／服务／持久化逻辑，不能以“功能共用”将手机UI混进桌面。此前六图回放通过不代表这个隔离约束已经全部满足。**

音乐库歌单入口此前遗漏了桌面分支，进入`MeloXLibraryPlaylistDetailScreen`（Android移植页）；已改为与Home／Search相同的桌面collection分支，并禁止桌面进入手机版split detail路由。歌曲操作现已在入口分流到独立桌面菜单，歌曲／歌手详情也新增独立Mac页面，见`DESKTOP_ONLY_SURFACES.md`。设置已使用独立桌面控件委派，逐页排列和同内容像素验收仍为 PARTIAL。

触屏需区分Compose的Touch事件与Windows AWT提升为Mouse的拖动事件；collection已为后一条链路补充按阈值开始的纵向拖动／惯性滚动，原生Touch及滚轮继续由LazyColumn处理。`--drag-scroll`独立断言两种输入均实际滚动超过100px并回到顶部，未用tag存在代替滚动结果。Windows物理触屏验证仍未完成。

用户再次反馈触屏不好用后，新增排查发现 Home、设置正文、部分工具／菜单／音乐库等滚动容器漏接 promoted-mouse 适配；Home 横向卡片与播客分类也需独立横向适配。当前补齐入口、按轴竞争、拖动全程用户输入优先级，并修正搜索媒体网格没有滚动容器的问题。场景回放与硬件触屏结论分开记录，见 `TOUCH_SCROLL_FOLLOWUP.md`。

按用户最新要求，左侧大栏／侧边歌词改为无描边液态玻璃，移除歌词打开时的 Home 卡片重排及底部播放器避让；账户 footer 与 sheet 共享真实会话资料加载，音乐服务管理接入账户页。真源依据与边界见 `SIDEBAR_ACCOUNT_GLASS.md`。

Windows 输入框的触摸键盘路由与原生 InputPane 接线见 `TOUCH_KEYBOARD.md`；关闭窗口隐藏到托盘的设置／生命周期见 `WINDOWS_TRAY.md`。这两项 Windows 集成与 Mac 原生组件的验收分别记录。

- 仓库：<https://github.com/lladlam/melox>。
- 电脑端固定提交：`a41ea0e1e1fa69fb543d827b853fe3aff5420951`。
- 重新克隆的位置：`/mnt/ntfs_files_sda4/项目/MeloX-Desktop-Reference`。
- 这是独立的 **macOS Desktop → Windows** 迁移目标。Android 正式 `v0.6.5 / 53244b9 / versionCode 24` 的资源、矩阵和验收记录继续独立维护。
- 用户要求覆盖整套 UI；总状态为 **PARTIAL**。本轮构建／回放／发行核验见 `ROUND_EVIDENCE.md`（CI `39632536833`，head `a1f6f10`）。
接入真实生产页面、源尺寸计算通过或 Windows 自身回放成功，均不等于该页面的一比一视觉／动态验收完成。
- `build/desktop-reference-a41ea0e/` 中官网下载的截图只用于布局考察；不是从该固定提交、相同账号／内容／尺寸取得的像素基准。

## 首批实现

设置 → 通用 → **电脑端布局 / Desktop layout**。偏好键 `general_desktop_layout` 默认 `false`，即时更新运行态并持久化；冷启动关键偏好读取包含该键。

切换时保留同一个主页面 composition 和可移动详情 composition；播放器／队列仍使用现有 `AudioPlayer`、`PlaybackCommands` 和生产 FFmpeg。桌面布局阻止 Android 左右等分和旧底栏重复出现。Windows 设置入口为平台接线文案，不冒充真源菜单文案。

源指标已接入：侧栏初始宽199、外距8；底栏最大700×54、内距9、transport148、cluster9、底距10；队列面板258、新图歌词阅读列360；面板出入280ms，减少动态关闭时长；首页横向留白52、普通快捷／hero最小200／间隔20，快捷为3:4竖卡、推荐hero正方形，媒体最小145／间隔24；播放器尺寸按源 `DesktopNowPlayingLayout` 计算。用户新图对inspector及沉浸队列的覆写独立记录；这些数值不涵盖原生材质、字体栅格和整页像素差。

Windows 首次以已保存桌面模式启动采用 1280×800；启用时设置原生内容最小 980×540 并提升过小窗口，关闭时释放该最小尺寸约束。原生 Windows 高 DPI／frame inset 的实机适配仍需验收。

## 全部界面追踪

| 真源界面 | Windows 状态 | 仍需完成的一比一工作 |
|---|---|---|
| `App/DesktopRootView.swift`、`Features/Sidebar` | PARTIAL：独立桌面框架、真实导航、账户sheet／设置可达；主窗口移除原生标题栏，接入软件内最小化／最大化／还原／关闭、拖动／resize，见窗口记录 | Windows原生DPI／触屏／窗口行为验收、安全区、材质、侧栏滚动／loading footer及原生阴影 |
| `Features/Home` | PARTIAL：完整顺序区块、真实新专辑API／缓存、最近播放回退、正方形推荐hero、媒体卡元数据及播放入口；分页shelf／纵滚转交、保留父页的真实歌单／专辑跳转；深浅配色和真实主题切换，见 `HOME_PARITY.md` | 固定同内容全图／动态验收、CJK字体栅格、原生toolbar／材质／阴影／snappy、歌曲独立详情和全部加载／错误视觉 |
| `Features/Search` | PARTIAL：独立332×36胶囊、catalog／library scope、5类segmented picker、42pt封面track row、惰性可滚动媒体网格、悬浮真实候选词及桌面详情 | 逐菜单像素／动态验收、聚合搜索桌面入口取舍 |
| `Features/Explore` | PARTIAL：独立New Music／Charts／Popular Artists三段、圆形歌手卡、145–205pt网格，真实Netease榜单与歌手接口 | 同内容帧、原生toolbar与逐区块加载／错误动画验收 |
| `Features/Library` | PARTIAL：网易侧栏直达独立桌面歌曲／历史、歌单／专辑／歌手／播客网格、下载和云盘；真实API、桌面track row与collection详情；深浅未登录导航回放通过 | 其他provider音乐库、登录态全量数据／分页、筛选／排序、拖放及全部菜单 |
| Albums／Artists 收藏入口 | PARTIAL：网易已启用，真实sublist接口和桌面网格／详情；其他provider仍能力门控 | 真实收藏账户验收；其他provider独立消费者与真源桌面页面 |
| `Features/Detail`、`Features/Playlist` | PARTIAL：collection内容42/34留白、278封面／34间隔、描述展开、46pt无封面行／固定列宽、专辑真实曲号／disc分组、SF Pro正文；全局album路由保留Desktop侧栏；真实播放／收藏／下载／筛选接线，详见 `COLLECTION_PARITY.md` | 原生toolbar／控件／阴影／字体栅格同内容帧；曲目菜单仍复用既有sheet；artist／song／podcast专属布局、编辑／删除／分享、分页及动态验收 |
| `Features/Player/DesktopBottomPlayer.swift`、`BottomBar` | PARTIAL：源尺寸、真实播放／暂停／切歌／进度／音量、歌词／队列入口 | 元数据 hover 展开、时间／进度过渡、玻璃材质、issue／retry、禁用态与原生音量细节 |
| `Features/Player/Queue` | PARTIAL：history／up next、按稳定 entry ID 选曲、清除／移除、真实 AutoMix／autoplay；沉浸队列按参考图显示浅色模式选中与标题，实际scrollbar和72pt底部fade | drag-and-drop、真实来源上下文、hover、精确字体／间距和所有菜单细节 |
| `Features/Player/Inspector`、`DesktopPlayerSidePanel.swift` | PARTIAL：队列258／新图歌词360宽、280ms进退与crossfade、真实page采样blur和主题透明层、真实歌词／队列 | 原生glass光学、窗口宽度增长策略和同内容尺寸／动画对照 |
| `Features/Player/DesktopNowPlaying*` | PARTIAL：修正播放1/.74封面缩放、真实收藏与独立JVM重载、8→18进度、白色音量、三态切换器，真实播放器／歌词／队列，见用户原图记录 | 顶栏 accessory／独立迷你窗口、信息／quality／output 菜单、全套展开收起原生动效 |
| `Features/Player/*Lyrics*` | PARTIAL：复用真实加载／字级渲染，接入 desktop 前景色、字号断点、左右 20、段距及 focus 输入 | 完整 macOS motion profiles、逐行焦点的精确高度求解、mask、词级 lift／spring、其他歌词样式和全部扩展窗口 |
| `Features/Settings` | PARTIAL：650pt外框、按tab高度、58pt footer；独立Form／checkbox／picker／slider／输入框／按钮／对话框，保留20页生产业务；手机分支独立 | 最终控件持久化／手机回归、独立窗口生命周期、逐页真源排列／文案与同内容像素验收 |
| `Features/Account` | PARTIAL：500×600账户sheet与原图几何；独立880×680登录面板（真实验证码／客户端Cookie）、私信／云盘／下载／设置及登出 | Mac WKWebView内核不可用，保留Windows真实登录替代；真实账户逐状态验收 |
| `Features/Messages` | PARTIAL：270–410pt会话栏、64pt会话头、头像／时间／未读、气泡方向／资源卡、真实发送与新建私信面板 | 真实双账号会话、回复／菜单、分页和逐状态像素验收 |
| `Features/ListenTogether` | PARTIAL：620×620独立桌面窗口、真实登录门控／创建／加入／成员／邀请／退出与错误确认 | 双账号实机同步、连接状态本地化／材质及逐状态视觉验收 |
| `Features/SongRecognition` | PARTIAL：700×660独立桌面窗口、真实录音／匹配服务、时长选择／停止／重试／结果 | 麦克风权限／硬件与指纹后端、动画／失败态及物理输入验证 |
| `Features/Onboarding`、`Features/About` | PARTIAL：760×650三步引导、600×320关于、许可证列表及6个文档、Windows真实更新检查；资源按固定blob导出 | 引导beat-effects、隐私／协议／云控独立桌面窗口、完整原生窗口与更新逐状态验收 |
| 快捷键、右键菜单、拖放、独立窗口、系统托盘／媒体控制 | PARTIAL：沿用已有 Windows 生产能力 | 对照真源逐个动作接线与物理输入验证；原生 macOS 项明确映射到 Windows 实际能力 |

## 图标与文案

`tools/export_desktop_reference.py` 直接从固定 Git blob 导出选定原文；`source-audit.json` 记录源文件及资源 SHA-256。当前导出 `en`／`zh-Hans` 两种真源资源，简体中文按系统 locale 选择，其他 locale 回退默认英文；Windows 开关／设置入口文案定义在 `MeloXDesktopStrings.kt` 的独立平台函数中。

`surface-inventory.json` 从固定 Git archive 枚举 App／Features 下全部 124 个 Swift 文件、16 个分组；未进入本批的文件继续标记 PENDING，首批接线文件为 PARTIAL，没有以目录级别的“已复用”关闭验收。

桌面 SF Symbol 子集已从 Apple 官方 SF Symbols 27 `SF-Pro.dmg` 重建，固定真源引用的 135 个名称、134 个唯一码点及全部 68 项 `MeloXSymbol`（含collection的`star.fill`）均有非空轮廓可追溯。`square.grid.2x2`、`icloud`、`square.stack`、`music.mic`、`bubble.left.and.bubble.right` 与真实 `heart.fill` 已替换此前的相近符号；源 DMG／字体／子集哈希、构建与独立校验工具记录在 `desktop-symbols.json`、`tools/build_desktop_symbol_font.py` 和 `tools/verify_desktop_symbols.py`。

Collection正文独立使用同一官方源字体的6个静态SF Pro face（普通Unicode字符、非图标）：wght400／500／600／700，opsz17／22／28；本轮含Home／Account的真源文案扩至105 key，两种locale均保持纯导出。6个face的构建、字节、ASCII／tnum和无变量轴核验记录在 `desktop-text-fonts.json` 与 `tools/build_desktop_text_fonts.py`，发行固定资源核验扩至28项。CJK平台fallback与CoreText／Skia像素差仍待原生同内容对照。

这关闭的是图标资源名称和字形差异，不等于图标在 macOS／Windows 上的像素位置、光学尺寸、层级与过渡完全一致；这些仍随各页布局和动态验收逐项核对。固定 Android 基线继续使用 `circle.grid.2x2.fill` 等原有 token 映射，不能由本桌面子集反向覆盖。

## 验证与证据

`tools/capture_desktop_layout.py` 从生产入口发送真实指针、滚轮和滑块拖动；semantics 只用于定位／断言。controls → reload → restore → reload-current 各自独立 JVM，验证偏好磁盘重载。生成 WAV 和 LRC 只存在隔离验证目录，经过生产 Local scanner／歌词存储／FFmpeg 解码；不会注入远端 provider 或用户音乐库。

切换连续性断言记录真实曲目 ID、位置及已打开 PCM 输出数量，再验证多次切换无曲目改变、无回退、无输出重建且仍播放。PCM 输出明确标注 `VALIDATION_SOFTWARE_PCM_OUTPUT`，不是硬件听感验收。浅色歌词、深浅首页和播放器另有截图。

首批历史本地结果保留在 `evidence/local/`；最新已完整下载核验的Windows发行为88f9c23、run39448380868，七组桌面65 scenes／120 assertions。当前fcbc877新增collection／严格冷启动的本地结果为274 tests／56 suites、49 scenes／63 assertions，原始失败与成功证据在 `evidence/collection-fcbc877/`。Windows run39516534284进行中，新结果及发行仍须独立完整下载核验；详见 `COMPLETION_LOG.md`。

Android 正式固定版同内容静态／动态帧、Windows 用户 549→1431MB 长时增长、真实账号／硬件／DPI 实机问题继续按原矩阵跟踪。
