# Desktop 迁移记录

## 8. 六张用户图、设置功能对齐和软件内窗口操作

- 实现提交：`ecfd6e2`，证据与完成记录独立提交后正常推送main触发Windows CI。
- 六张原始PNG的SHA／尺寸和source差异见 `USER_SCREENSHOT_REFERENCE.md`；原图与用户私人内容仅留忽略的build目录，公开证据使用真实公共推荐或明确标注的生成本地媒体。
- Home按固定源码补齐完整分区、正方形hero、真实新专辑API／缓存、分页／hover、鼠标纵滚转交、返回保留滚动和真实Touch输入；深／浅主题跟随同一设置。原图首卡 `[518,402,936,959]`，最终 `[518,406,936,963]`：横向及尺寸一致，纵向仍低4px，未将其标为完全一致。
- Account采用500×600／42行／chevron11／80×30完成按钮和原图surface采样，两主题下回放真实未登录态、完成、Escape和再次打开。真实登录后的私信／云盘／下载／设置／退出仍复用原生产会话与页面；没有借用原图账号。
- Playlist以1960×1356／density2直接回放，真实搜索／恢复／更多／返回通过；原图封面 `[498,172,1054,728]` 与最终几何相同。正文和用户图内容不同，顶部Windows搜索／标题与CJK栅格差异继续明确保留。
- 沉浸播放器修正播放封面为暂停slot的1/.74，补真实收藏／重载还原、进度scrub、白色音量和三态切换；21／35及独立JVM6／11通过。原图封面相对固定source仍有约6px横向差，不通过硬移封面伪装关闭。
- Queue按新图修正浅色选中／模式文字、横向inset和header，加入实际scrollbar／72pt fade；模式总边界最终约 `[1062,96,1832,168]`，原图约 `[1063,96,1832,168]`。继续播放的真实来源上下文仍未接入，不以歌曲专辑伪造。
- Lyrics panel按原图宽720px／左边界1694制作360dp阅读列；生产page-layer单独BlurEffect24dp后裁剪，再叠主题透明表面，避免旧采样器的偏移／模糊越界；歌词／队列280ms crossfade，减少动态为0。深浅两组均11／18通过，包含真实暂停、歌词点击seek、恢复、切队列及关闭。
- 设置保持桌面五类顶部导航／分类子页，统一为第一套布局的同一20个生产设置组件，补齐同一搜索、重置和退出逻辑；长子标签换行解决真实不可达入口。英文深色／中文浅色各91 scenes／25 assertions通过，详见 `SETTINGS_FUNCTION_PARITY.md`。
- 主窗口使用undecorated，新增软件内最小化／最大化／还原／关闭、拖动／双击及四边四角resize；关闭共用释放实际播放器后退出的回调。原生Robot审计已加入Windows CI。本机显示器0×0，记录UNSUPPORTED，未把离屏截图冒充窗口操作通过；详见 `WINDOW_CHROME.md`。
- 最终9组独立重计 **312 scenes／241 assertions**；**276 tests／57 suites／0 failures/errors/skipped**，18枚设置图标、135 SF Symbols／71枚举／134码点、105纯导出文案和源／最终uber JAR内28资源通过。独立读取最终播放器23份实际软件PCM的字节数／SHA／RMS和220／330Hz频谱均通过。
- 原始失败、成功输入／参数／capture、选定PNG／semantics、测试XML与独立报告在 `evidence/six-ui-local/`；背景色、原图几何残差与验证边界保持可追溯。总体同内容全图像素／原生材质／字体／弹簧曲线及真机触屏验收继续 **PARTIAL**，长时内存增长不由本次短回放关闭。

## 7. Collection 源结构／SF Pro 正文与冷启动焦点修复（fcbc877）

- 实现 `fcbc8771bffbf1a6cd00a3612895f8a1c99db563` 已正常推送main。固定源码对照见 `COLLECTION_PARITY.md`：移除额外滚动toolbar／列标题，内容横42／纵34、header／divider／list间34、封面278／continuous radius11、详情最大620／底距6；默认曲目无封面、minHeight46、数字24／album180／duration42／menu28、行距1；专辑隐藏album列，解析真实曲号及disc分组；接入真实描述展开／选择、收藏星号、下载菜单和曲目双击。
- Collection正文从Android兰亭切为官方SF Pro普通字符集的6个静态face：text wght400／500／600／700 opsz17、subtitle wght600 opsz22、display wght700 opsz28。保留OpenType shaping及tnum，明确阻断Material的24sp行高继承。固定官方源可逐字节复现；6个face的已冻结轴／SHA／ASCII／HarfBuzz实际等宽数字核验通过。CJK系统fallback、CoreText／Skia栅格与系统字距继续保持未验收。
- 真源 `en`／`zh-Hans` 扩至86 key，包含collection动作、metadata、质量标题；纯Git blob复核通过。Properties对leading／trailing space使用Unicode转义以完整保留原文分隔符，不手写替换翻译。源及实际uber JAR内28项资源SHA全通过；135符号／68枚举／134码点与原Android18项设置图标核验通过。
- 全局album／artist路由改用同一movable composition在Desktop主栏呈现，保留侧栏、inspector和底部播放器层级；关闭布局后仍进入原全窗口slot。侧栏导航清除旧collection路由。
- 首轮真实album冷启动保留了 `FocusRequester is not initialized` 原始异常：DesktopSettingsLayout提前return分支未挂接启动effect使用的requester，导致AWT线程异常和后续点击／加载失败。为该分支挂接真实focusable容器后，严格 `playing:false` 的暂停／完整播放器暂停恢复通过。最终新增冷启动回放 **23 scenes／40 assertions**，覆盖已保存Desktop偏好启动、歌词inspector、真实歌词点击seek、队列选曲、音量、展开／收起播放器和导航；任何后台线程异常会让回放失败。该结果限定于生产ImageComposeScene，不冒充Windows原生窗口物理输入实机验收。
- 最终真实公共网易album／playlist回放 **26 scenes／23 assertions**：album ID `34720827` 包含超长真实描述展开、滚轮找到收起、收起后返回header、更多菜单／关闭／返回；playlist从Home真实推荐卡打开，键盘筛选到空结果、关闭后恢复真实行。独立核对PNG、输入文件、assertions及278封面／42边距／34间隔／620上限／46行高。与冷启动合计 **49 scenes／63 assertions**。
- 显式JDK17顺序编译、全量 **274 tests／56 suites／0 failures/errors/skipped**、`auditSettingsIcons`、uber JAR打包通过。独立读取3份实际PCM：字节精确对应written_frames×4、非静音，前两份主峰220Hz、第三份330Hz；均为软件PCM端点，不代替硬件听感。
- 原始首轮失败、最终capture／semantics／PNG／输入参数／日志、56份测试XML和独立核验在 `evidence/collection-fcbc877/`。Windows [39516534284](https://github.com/lladlam/MeloX-Windows/actions/runs/39516534284) 已启动，新增严格cold-controls及真实collection两组；此处尚未记录Windows结果或新发行下载验收。
- 总状态继续 **PARTIAL**。源参数对齐、官方字形、Windows实现自回放均不能替代固定macOS同内容／同尺寸／同density的overlay／difference／heatmap；原生控件、阴影、弹簧曲线、加载／失败占位、全部菜单／拖放与剩余页面继续按矩阵推进。

## 6. Windows 独立下载核验（88f9c23）

- Windows [39448380868](https://github.com/lladlam/MeloX-Windows/actions/runs/39448380868) 成功，来源 `88f9c23bbb2c88499e313423aee00c3de5facda1`。完整下载 `Verification-Windows` 与 `MeloX-Desktop-Windows.zip` 后独立重计：**274 tests / 56 suites / 0 failures/errors/skipped**、桌面七组 **65 scenes / 120 assertions**、全矩阵 **32 组**，ZIP **333622322 bytes**，SHA-256 `a01219c7f8fb3fe1af2b502aa66c81089e2666a40e1fac996cc6fc2f3efd13a1`，357 entries CRC 全通过，build-info 精确匹配。
- 发行内 22 项固定资源全部 SHA 匹配，其中 `fonts/sf_pro_subset.ttf` 为 **162332 bytes / `3f592d90…d13af6`**；`desktop-symbols.json` 在下载产物中独立核对 **135 symbols / 67 enum / 134 unique code points**。原始证据在 `evidence/windows-ci-88f9c23/`。
- 仍不把 Windows CI 自比对当作固定 macOS 同内容像素／动态验收；整套 UI 仍为 **PARTIAL**，后续继续逐页、图标光学、材质、hover、拖放与所有弹窗迁移。

## 5. 可追溯 SF Symbols 子集与桌面图标对齐

- 固定 Desktop 真源的 135 个 SF Symbol 名称已汇总到 `parity/desktop-a41ea0e/desktop-symbols.json`，包含 App／Features 中静态与计算属性引用的图标。Apple SF Symbols 27 的 `mic`、`music.mic`、`doc.text.magnifyingglass` 等名称按官方 alias 解析；`music.mic` 使用 SF Symbols 6 的 PUA 位置 `U+10046B`，位于 `music.note` 与 `music.note.list` 之间。
- `sf_pro_subset.ttf` 由 Apple 官方 `SF-Pro.dmg` 内的变量字体重建，源 DMG SHA-256 `968ab3b8…012918`、源字体 SHA-256 `7270e159…f10e10`、子集 SHA-256 `3f592d90…d13af6`。`tools/build_desktop_symbol_font.py` 固定源哈希、134 个唯一码点、可见轮廓和逐字节可复现输出，单次重建得到 162332 bytes。
- 侧栏 `Explore` 改为 `square.grid.2x2`，`Albums` 改为 `square.stack`，`Artists` 改为 `music.mic`，`Cloud` 改为 `icloud`；账户弹层的私信入口改为 `bubble.left.and.bubble.right`。首页心动模式改用 `heart.fill`。旧 Android 设置 token 的 `circle.grid.2x2.fill` 与 `mic` 映射保留，避免破坏固定 Android 对照。
- 原枚举中重复或错误的 `heart.fill`、`cloud`、`person.fill`、`globe`、`star` 等码点已按真名字形修正；`MeloXSymbol` 现有 67 项全部在子集中有真实 ink。搜索媒体行、账户歌单行、资料库歌单行及播放列表更多菜单的文本箭头／三连点也改为同一 SF 字形；登录和 Provider 服务页返回箭头改为 `chevron.left`。
- `tools/verify_desktop_symbols.py` 在无 Apple 源字体下载的 CI 环境核对子集字节、135 个符号、67 个枚举、134 个唯一码点和非空轮廓；`source-audit.json` 与打包资源核验扩至 **22 项**。固定 Android 设置图标 18 项仍通过。
- 显式 JDK 17、Gradle 9.5.1 重新执行全量 `:shared:desktopTest --rerun-tasks`：**274 tests / 56 suites / 0 failures/errors/skipped**；桌面编译、`auditSettingsIcons` 和 `packageUberJarForCurrentOS` 通过。新子集在实际 uber jar 中的 162332 bytes 与 SHA-256 和源清单完全一致。
- 修复桌面化设置页替换旧列表后丢失的回放语义：窗口根节点保留 `settings-root`，顶部 General 入口将 `settings-open-general` 放在真实可点击节点上；`capture_desktop_layout.py` 的 reload 从固定桌面入口直接验证 General，restore 在独立新偏好目录中切换 Desktop 开关后恢复当前布局，reload-current 再打开 General 核对持久化关闭状态。本地 `controls` **29／68**、`reload` **7／6**、`restore` **8／14**、`reload-current` **5／6** 通过。reload 只验证独立 JVM 的已保存桌面布局、真实队列恢复和播放就绪，不重复 controls 内的冷启动页面点击；后者的全交互仍由同一 JVM 的 controls 覆盖。
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
