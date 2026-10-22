# 手机版 UI / 动画主动审计

## 真源与方法

固定正式 0.6.5 提交 `53244b9d6b21d7e3a609e8cb11876efef70f218b`，以 `git show` / `git archive` 读取，未读取 Android 工作树作为真源。本机 Android 当前分支和当前 `v0.6.5` 标签均与既定提交不同，本轮不擅自跟随移动标签更换基线。

本轮从 Windows `d46de6e` 开始。主动检查首页、发现、搜索、音乐库/详情、MiniPlay、播放器/歌词/横屏、Dock、设置、播客、私信、云盘、音源与弹层。`tools/audit_mobile_v065.py` 生成 20 个区域的两端路径、文件哈希、动画调用位置索引（`parity/53244b9/mobile-ui-source-index.json`）；索引不是自动判定一比一通过。

## 已定位差异与本轮处理

| 区域 | 0.6.5 源依据（`ui/` 下） | 发现的问题 | 处理 |
|---|---|---|---|
| 首页每日推荐 / 热歌榜 / 雷达 | `discovery/MeloXDiscoveryScreens.kt:475–536,586–628` | 每日推荐用精简列表替换整个首页；另外两项直接开播；首页及滚动状态被销毁、无返回动画 | 三项复用统一歌曲列表详情（真实 API 返回歌曲）；保留首页、覆盖层进退场；不是把虚构列表 ID 发到歌单 API |
| 多入口歌单详情 | `library/LibraryScreen.kt:1888–1966` | Home/Explore/Search 的 `PlaylistDetailScreen` 走旧简化版，Library 却走另一套 | 手机版统一委派到 canonical detail；Mac 桌面分支继续使用独立组件 |
| 详情背景 / 搜索 / 宽屏 | `library/LibraryScreen.kt:2416–2565` | 纯色背景、常驻搜索框、单列；隐藏歌单搜索设置在 canonical 页无消费者 | 接封面流光、可读性背板；64dp 下拉搜索、先收搜索再滚列表、同一手势到顶不误拉开；840×600+且开启分屏时双列 |
| 收藏态 | `library/LibraryScreen.kt:2589–2595` | 会话未知仍先显示“未收藏”，容易错误再次收藏 | 未获得真实收藏态不显示收藏按钮；provided songs 不发起虚构歌单收藏；多源歌曲不误走网易云数值 ID 写入 |
| 非网易云首页 | `discovery/MeloXDiscoveryScreens.kt:634–767` | 推荐歌单、排行点击回调为空 | 歌单路由真实详情；排行走对应 RankingCapability、hasMore 分页及源+原始ID去重，错误可见 |
| 发现页排行 | 同源 `MeloXHomeLayout` 的 collection 路由 | `ProviderRanking` 卡片同样无点击消费 | 与首页共用真实排行加载和统一歌曲列表详情，不再空点击 |
| 首页账户头像 | `discovery/MeloXDiscoveryScreens.kt:376–384` | 有头像但没有点击事件 | 点击进入真实用户资料页 |
| 通用页面进退场 | `animation/MeloXMotion.kt:37–56,446–488` | 280/220ms、淡入淡出混用，退出仅移动1/4屏 | 公共 carrier 采用0.6.5临界弹簧（ζ=1,k=800,1px阈值），双向满宽；350ms基准常量；reduce motion可瞬时完成；覆盖层注册返回处理 |
| MiniPlay / 播放器时钟 | `player/MeloXPlayerTransitionSpec.kt:77–120,187–195` | 外壳360ms旧曲线、封面300ms、内容又走线性，三个通道不同拍 | 线性master不重复缓动，几何/内容/退让共用归一k=6曲线和当前设置时长 |
| 关闭尾段 | 同文件`:265–382,516–543` | 没有22dp尾段下沉回位，中断后方向不可读 | 实时读closing intent的bounds spec；仅确定收起且目标明显变小时，线性时间后38%叠加单峰sin，下沉终点严格0 |
| 播放器背景页退让 | `animation/MeloXMotion.kt:170–204` | 原页面原地不动 | 页面x缩至0.83、y按窗口比例推导等距内缩、下沉30dp、黑舞台及0.24暗化；底栏不随之缩放 |
| 过渡命中 / 再次打开 | `player/MeloXIOSNowPlayingSharedHost.kt:112–122,473+` | 关闭动画未完不能重新打开，移动中的控件可误触 | 过渡期Initial pass拦截控件；仅实际MiniPlay命中矩形内tap可抢回关闭，不抢底栏导航；不重置transition state |
| 播放器裁剪 / 封面层级 | 同文件`:398–463`、`MeloXIOSMiniPlayer.kt:506+` | Mini内容始终胶囊裁剪；封面落入外壳层级；外壳无坐平判据 | 展开中取消mini内容胶囊裁剪；mini/host封面99/100 overlay；状态机到位后140ms坐平、关闭当帧恢复圆角 |
| 播客详情 | `podcast/MeloXPodcastScreens.kt:151–214` | 互斥if/else直接销毁列表，返回滚动丢失 | 保留首页与详情退场子树，公共覆盖层返回；封面hero仍待接线 |
| 私信会话 / 会话变动 | `messages/MessagesScreen.kt` | 互斥切页、登录态只remember一次；取消异常可能被当普通失败 | 覆盖层保留联系人页；真实会话revision刷新，注销清空旧联系人；传播取消 |
| Windows标题栏（新增平台功能） | 用户要求 | 窗口按钮独占背景行，播放器背景无法延伸至顶部 | 增加持久化“沉浸式标题栏”，页内容延伸到窗口顶部；播放器背景全高、控件避让34dp；主题/详情palette提供标题栏颜色。保留拖动、双击最大化、按钮与缩放 |

### 真源说明

0.6.5 的快捷入口本身仍为310dp渐变卡片；“每日推荐 / 热歌榜 / 私人雷达”的**目标页**调用统一歌曲列表详情。不能仅按“看起来像歌单”判断该目标页错误；本轮依据实际路由、数据和 canonical UI 改造，而不是凭名称另造页面。

## 主动发现、仍需继续迁移的差异

这些没有因本轮编译或回放通过而自动标记完成：

1. **首页/发现/搜索/播客卡片 → 详情hero共享封面**：卡片与统一详情还未全部进入同一SharedTransitionScope；搜索分类子页仍有直接条件切页。本次设置详情先接通同一声明式弹簧驱动的旧页布局期左退1/3与新页满宽推入，关闭途中可反向，双栏父页不退让；其他详情入口与hero尚待迁移。
2. **页面滑动几何**：Windows边缘返回已有跟手位移/回弹，未完整接入源的0.92缩放、平滑角和两页互补几何；Windows没有Android硬件屏幕圆角真值，本轮播放器以32dp平台fallback、圆弧shape实现，不能称Lamé平滑角一比一。
3. **播放器横竖屏/大屏**：目前仍是旧43/57横屏场景；真源有转屏统一时钟、840×600+双栏内封面居中↔歌词/队列分栏、歌词天际封面morph，尚未完整迁移。**勘误：480ms封面→header tween 本身就在固定真源 SharedHost:908–915 中，不属于应删除的旧时钟。** 本次继续迁移其22dp header落点、竖屏封面单独上抬30dp和展开时目标页姿态锁定；完整转屏仍待迁移。
4. **首页数据和操作**：本地推荐结果展示、播客数据区块、真实下拉刷新、听歌识曲浮层入口未全接入；Provider首页快捷工具尚未与Android统一。识曲设置页不能当作识曲功能入口交付。
5. **发现页**：旧网格/固定标题，缺真源折叠大标题及卡片退场占位/延长保活；分类加载取消/重试还需专门回归。
6. **搜索和Provider详情**：已具备实际搜索/多类型/详情业务，但统一公共菜单、长按/分页/集合权限需按源逐项核销；有少量选择/子页切换仍未共用源的InterruptiblePage。
7. **统一详情完整操作**：下拉搜索与canonical UI已推进；本次接通分享预览/系统剪贴板、原序/歌曲/艺人/专辑排序、详情信息与真实刷新。推荐列表分享实际歌曲链接，不拼接虚构歌单链接；自己的歌单不再显示取消订阅按钮。删除、自建/已喜欢系统歌单权限与更多账号写入仍待独立收尾。
8. **歌词/天际/TextPV**：统一歌词语言及流光此前已接入，但若干样式是Windows重绘；完整模板动效、词级切换/封面联动须同内容回放，现有本地自测不等于Android像素MATCH。
9. **私信UI**：联系人头像、分组、刷新/未读、会话内更多操作和消息展示细节仍与源不同；真实发信需账号验收。
10. **完整多语言/平台输入**：多处手机版硬编码中文仍未迁到0.6.5全量资源；现有legacy资源保持原来源，不能覆盖为改写文案后声称纯导出。物理触屏、触摸键盘、长时内存等仍须实机。

## 验证

- 设置页续迁：单栏 **73 scenes / 11 assertions / 48 geometry frames**，双栏 **49/8/28**，减少动态 **45/8/18**，独立核对两页边缘互补关系；设置全路由 **88/65**、桌面controls **29/67**、shared **276 tests / 0 failures/errors/skipped**。证据 `evidence/page-motion-local/`；本批Windows结果待补记。

- 本次续迁：Home **17 scenes / 20 assertions**；实际Local歌词播放器 **145/28**；桌面controls **29/67**、lyrics-light **12/15**；独立几何/PCM **41/82**；单元测试 **57 suites / 276 tests / 0 failures/errors/skipped**。证据 `evidence/mobile-v49/`，操作与真源依据 `DETAIL_AND_ARTWORK_FOLLOWUP.md`。本批Windows结果待补记。

- `capture_mobile_v065.py home`：**13 scenes / 13 assertions PASSED**。真实公开排行榜打开统一详情、保留首页；返回后再次打开并由Escape关闭，证明详情覆盖层的键盘焦点已归还；未登录每日推荐实际显示“请先登录网易云音乐”，不生成假歌单。
- 前轮 `capture_mobile_v065.py player`：**115 scenes / 22 assertions PASSED**。427×850、真实WAV→PCM；显式动画时间轴截取展开0/200/500/700/820ms，外壳bounds与圆角确实逐帧变化；关闭尾段在820ms仍可见下沉；关闭途中按真实MiniPlay中心重新打开；再次关闭、重开、恢复播放后PCM继续写入。**本轮勘误：此前虽传入LRC文件，但缺少Local扫描，实际未入歌词库；旧通过数只证明歌词页打开，不证明真实歌词显示。** 本轮补扫描与实际歌词行断言。软件PCM不代表可听硬件验收。
- `capture_desktop_layout.py controls`：**29 scenes / 67 assertions PASSED**；`lyrics-light`：**12 scenes / 15 assertions PASSED**。lyrics-light 在布局切换前暂停于0:00，真实点击 `lyric-line-1` 后位置前进；独立几何核验两份回放 **41 scenes / 82 assertions PASSED**，`desktop-bottom-player` 700dp、sidebar 199dp、lyrics inspector 360dp 均与源几何一致。完整CI仍会跑全部六种布局模式及cold-controls。
- `capture_settings_pages.py desktop-controls/desktop-reload`：**25 scenes / 27 assertions** 与独立JVM **1 scene / 10 assertions PASSED**。真实点击三次标题栏开关后运行时和持久化偏好均为true，另起JVM读取。
- `:desktop:auditWindowChrome`：本机无正尺寸原生显示器，编译与任务完成、结果为**UNSUPPORTED**；Windows CI必须在真实AWT窗口上执行，包含沉浸式渐变像素连续、最大化/还原与关闭。不能用 ImageComposeScene 截图代替。

- Windows **[39729927138](https://github.com/lladlam/MeloX-Windows/actions/runs/39729927138) / `ba86270` PASSED**：57 suites / 276 tests（0失败/错误/跳过），48份capture / 1,458 scenes / 2,033 assertions 全部通过；真实AWT窗口的沉浸式渐变像素连续与最大化/还原也PASSED。发行ZIP独立核验 **337,978,332 bytes / SHA-256 `6b72c72189dcdb10543b2abb10a0150f9913461abaf97aacaf345403938de443`**，证据在 `evidence/windows-ci-ba86270/`。

各项最终结果在完成后补记；离屏Touch和软件PCM不代替物理触屏/可听音频验收。
