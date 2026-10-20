# Desktop 迁移记录

## 5. 可追溯 SF Symbols 子集与桌面图标对齐

- 固定 Desktop 真源的 135 个 SF Symbol 名称已汇总到 `parity/desktop-a41ea0e/desktop-symbols.json`，包含 App／Features 中静态与计算属性引用的图标。Apple SF Symbols 27 的 `mic`、`music.mic`、`doc.text.magnifyingglass` 等名称按官方 alias 解析；`music.mic` 使用 SF Symbols 6 的 PUA 位置 `U+10046B`，位于 `music.note` 与 `music.note.list` 之间。
- `sf_pro_subset.ttf` 由 Apple 官方 `SF-Pro.dmg` 内的变量字体重建，源 DMG SHA-256 `968ab3b8…012918`、源字体 SHA-256 `7270e159…f10e10`、子集 SHA-256 `3f592d90…d13af6`。`tools/build_desktop_symbol_font.py` 固定源哈希、134 个唯一码点、可见轮廓和逐字节可复现输出，单次重建得到 162332 bytes。
- 侧栏 `Explore` 改为 `square.grid.2x2`，`Albums` 改为 `square.stack`，`Artists` 改为 `music.mic`，`Cloud` 改为 `icloud`；账户弹层的私信入口改为 `bubble.left.and.bubble.right`。首页心动模式改用 `heart.fill`。旧 Android 设置 token 的 `circle.grid.2x2.fill` 与 `mic` 映射保留，避免破坏固定 Android 对照。
- 原枚举中重复或错误的 `heart.fill`、`cloud`、`person.fill`、`globe`、`star` 等码点已按真名字形修正；`MeloXSymbol` 现有 67 项全部在子集中有真实 ink。搜索媒体行、账户歌单行、资料库歌单行及播放列表更多菜单的文本箭头／三连点也改为同一 SF 字形；登录和 Provider 服务页返回箭头改为 `chevron.left`。
- `tools/verify_desktop_symbols.py` 在无 Apple 源字体下载的 CI 环境核对子集字节、135 个符号、67 个枚举、134 个唯一码点和非空轮廓；`source-audit.json` 与打包资源核验扩至 **22 项**。固定 Android 设置图标 18 项仍通过。
- 显式 JDK 17、Gradle 9.5.1 重新执行全量 `:shared:desktopTest --rerun-tasks`：**274 tests / 56 suites / 0 failures/errors/skipped**；桌面编译、`auditSettingsIcons` 和 `packageUberJarForCurrentOS` 通过。新子集在实际 uber jar 中的 162332 bytes 与 SHA-256 和源清单完全一致。
- 修复桌面化设置页替换旧列表后丢失的回放语义：窗口根节点保留 `settings-root`，顶部 General 入口将 `settings-open-general` 放在真实可点击节点上；`capture_desktop_layout.py` 的 reload 从固定桌面入口直接验证 General，restore 在独立新偏好目录中切换 Desktop 开关后恢复当前布局，reload-current 再打开 General 核对持久化关闭状态。本地 `controls` **29／68**、`restore` **8／14**、`reload-current` **5／6** 通过；reload 的冷启动播放控制器身份问题另见验证边界，不冒充已通过。
- 本地证据 `evidence/local/desktop-symbols.json` 记录 135／67／134 的独立核验；`desktop-icon-home-light.png` 为生产 `ImageComposeScene` 1280×800 浅色首页，真实未登录网易推荐内容，侧栏与首页卡片字形已实际渲染，`desktop-symbols-render.json` 记录截图与子集 SHA-256。
- 本批只关闭“图标资源不正确／不可追溯”差异；材质、精确字距和 macOS 同内容像素／动态验收仍未完成，总体 **PARTIAL**。

## 4. 账户弹层与沉浸队列参考迁移

- 以用户上传的 home、account、playlist、queue、immersive-player、lyrics-pane 截图为本轮参考；桌面侧栏账户入口改为居中账户弹层，提供私信、云盘、下载、账户设置、登录／登出和完成入口。
- 上述操作分别连接现有消息页、云盘页、下载页、设置页与网易云会话；退出登录清除会话，未登录状态可进入已有登录流程。
- 沉浸式队列模式选择补充 AutoPlay／AutoMix 文案，保留运行时模式状态和点击行为；队列标题及行内边距按源 15pt 指标调整。
- 修复桌面设置布局下通用设置容器缺少 `settings-general` 语义标签的问题，保证现有桌面切换回放可以继续定位设置内容。
- OpenJDK 21 与固定 Gradle 9.5.1 下 `:desktop:compileKotlinDesktop` 编译成功。此次未运行测试。

## 3. Collection 详情、账户与消息桌面化

- 新增共用 Desktop collection 详情组件：对齐真源 278×278 封面、34pt 间距、32pt 标题、22pt 作者、13pt 元数据、桌面操作按钮、搜索和曲目表列。
- 歌单与专辑在 Desktop layout 下改走该组件，并继续使用真实播放、随机播放、收藏、下载、筛选和歌曲菜单；关闭开关仍使用原移动端结构。
- 多 Provider 账户页改为居中的 700pt 桌面窗口表面，保留所有现有真实登录能力与弹窗。
- 私信改为 286pt 联系人侧栏和会话详情双栏布局，联系人选中态、消息气泡、发送与错误处理继续接真实接口。
- 使用工作区 OpenJDK 21 和固定 Gradle 9.5.1 再次编译 `:desktop:compileKotlinDesktop`，结果成功。

## 2. 桌面搜索、发现、资料库与设置页面化

- Desktop layout 开启时，搜索页采用 32pt 桌面标题、最大 520pt 搜索框、32pt scope、34pt 内容留白、桌面推荐卡片和 54pt 曲目行；原布局分支保持原尺寸。
- 发现页采用固定提交对应的 32pt 标题、宽幅 hero、自适应最小 145pt 媒体网格、20pt 横向／24pt 纵向间距及 9pt 封面圆角；继续使用真实分类和 Provider 数据。
- 资料库的歌曲与歌单页加入桌面 34pt 留白、54pt 行高、40pt 封面、紧凑字号与 520pt 分页栏；原 Android 风格在关闭 Desktop layout 后仍走原有分支。
- 设置改为居中的桌面窗口表面，提供 General、Features、Playback、Files、Advanced 五个顶部入口和组内子页；账号、布局开关及现有真实设置能力全部保留。
- 共用顶栏、分组列表和固定详情页在 Desktop layout 下统一为 34pt 页面留白、32pt 标题、52pt toolbar、10pt 分组圆角与 48／58pt 紧凑行，因此账号、登录、详情、云盘、下载等复用页面也不再显示移动端密度；关闭开关仍使用原组件指标。
- 使用工作区 OpenJDK 21 和仓库固定 Gradle 9.5.1 编译 `:desktop:compileKotlinDesktop`，结果成功。未以本次编译冒充固定 macOS 同内容截图或 Windows 实机像素验收。

## 1. 保留当前 UI，加入桌面切换及首批源布局（1ccc123）

- 实现提交：`1ccc123830e7662e0a2e6989de237a2ed3c64dcb`，已正常推送 `main`。
- 重新 clone 的桌面真源固定 `a41ea0e1e1fa69fb543d827b853fe3aff5420951`。`source-audit.json` 记录本批采用文件与导出文案的纯 Git blob 哈希；`surface-inventory.json` 枚举 App／Features 下全部 **124 个 Swift 文件、16 个分组**，逐文件记录哈希和未完成状态，防止只迁移框架后遗漏其余 UI。
- 设置 → 通用增加“电脑端布局 / Desktop layout”。`general_desktop_layout=false` 为默认值；切换立即应用并写磁盘，独立 JVM 验证开启／关闭均能恢复。主内容和设置详情保留 composition，旧左右等分／底栏不会与桌面框架重复叠加。
- 接入固定侧栏、真实页面导航、700×54 底部播放器、258 宽歌词／队列面板和源尺寸的并列播放器；首页首批卡片使用真实首页 API，快捷动作接真实推荐／歌单／播放链。心动模式直接启动播放，其余推荐入口进入歌曲／歌单浏览；详情样式仍需迁移。
- 歌词复用生产加载和字级 Canvas，增加仅 presentation 层的桌面前景色、字号断点、段距、边距和 focus 输入；浅色 inspector 不再沿用白色文字。现有布局的默认白色输入保持原值。
- 真源两种 locale 共 **59 个 key** 已纯导出复核；按系统 locale 选择简体中文／默认英文。Windows 集成开关与入口文案单独标注。源与打包资源核验清单扩至 **21 项**，原 Android 基线保持独立。
- 显式 JDK 17、Gradle 9.5.1 顺序验证：**274 tests / 56 suites / 0 failures/errors/skipped**，桌面编译通过。四项源几何／字号断点 golden 回归覆盖 1200×768、1239×600、1620×1080 及 inspector reservation。
- 桌面七组真实输入回放全部通过：**75 scenes / 139 assertions**。包括连续切换三次、切回两次、独立重载开／关状态、真实暂停／恢复／选曲／音量、完整播放器和浅色歌词点击跳转。连续性检查对比曲目 ID、位置和 PCM output 数量，确认切换不重开输出。
- 独立读取实际 `.s16le` 字节：大小精确对应 `written_frames × 4`、非静音，220／330Hz 生成输入的真实 FFmpeg 输出谱峰符合；每个播放回放写入超过 44100 帧。输出端点是明确标注的软件 PCM 验收端，不冒充硬件听感。
- 原布局三组回归通过：**70 scenes / 95 assertions**，覆盖分屏、六位置和竖屏。两批共 **10 组 / 145 scenes / 234 assertions**。生产字形／同 shaped paragraph raster 审计通过；该桌面自比对不替代 Android 或 macOS 的固定同内容验收。
- 已人工查看深／浅首页、队列、浅色歌词 inspector 和完整播放器帧。当前素材、字体、图标、材质、hover／弹层／页面布局仍有明确差异；全部迁移边界在 `MATRIX.md`，总体 **PARTIAL**。
- 本地原始 XML、capture／semantics、UTF-8 参数、输入、日志、独立重计和选定截图在 `evidence/local/`。真实 PCM 大文件保留在 build，并记录路径／大小／SHA-256；Windows CI 同时上传原始 PCM。
- Windows [39363076242](https://github.com/lladlam/MeloX-Windows/actions/runs/39363076242) 已开始。完整结果、发行下载与独立核验尚待完成；不能把本地结果记作 Windows 发行通过。最近完整独立核验发行仍为 `14b4df0`。

## 后续优先级

1. 按 `MATRIX.md`／124 文件清单补齐全部 Desktop 页面：搜索、发现／Radio、Library 全部子页及 collection 详情、所有设置、账号／登录、私信／Together／识曲／引导／关于。
2. 对齐首页完整分区与分页、底栏 hover／时间展开、队列 drag／菜单、播放器 accessory／收藏、独立歌词／迷你窗口，补齐可追溯的同款 SF Symbols。
3. 用固定提交、相同内容、尺寸、主题和状态取得 macOS 参考帧／动态轨迹，再逐项关闭像素与动画差异。
4. Windows 原生 DPI／窗口尺寸和真实账号／硬件回放继续验收；用户 549→1431MB 长时常驻增长保持原有未闭环状态。
