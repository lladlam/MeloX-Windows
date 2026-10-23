# 0.6.5 迁移记录

## 1. 切换正式发行基线与智能队列额度（0cee9a3）

- 用户指定基于0.6.5继续迁移；核对正式release为 `v0.6.5` / `53244b9d6b21d7e3a609e8cb11876efef70f218b` / versionCode24，区别于旧 `0.6.5` / versionCode23。新增活跃基线入口和独立矩阵、原始38提交/389文件清单与真源导出哈希。
- 迁移 `smartQueueAnalysisLimit` 默认60、负数-1不限、正数10–500；5个原文选择接真实持久化。futureItems排除历史，额度按队列generation一次性应用，fallback仅等待额度内有效候选。
- 队列改为完整时间线，仅原位重排分析匹配；记录链尾并保护当前/历史，额度外条目保留原序。分析用两个worker避免对大歌单逐项创建协程；关闭AutoMix/SmartQueue取消任务，旧generation结果不能写入新队列。已有真实双解码PCM、过渡取消及失败保留回归继续运行。
- 本地相关shared回归和桌面编译成功；最终全量 **257 tests / 52 suites / 0 failures/errors/skipped**，桌面编译成功。新增真实15首生成WAV、从第3首播放且额度10的测试，实际只解析/成功分析11首，历史/额度尾部不分析，切歌不重置额度；90/140/92 BPM完整队列重排通过。成功分析数从builder只读诊断读取，URI解析集从真实resolver边界记录，未用假分析输出。
- 真实鼠标/滚轮操作额度五选项、Smart/Fixed显隐：**26 scenes / 13 assertions**；独立JVM真实偏好重载：**5 scenes / 4 assertions**，均通过。
- 原始基线、正式release/tag解析、纯Git源文件哈希在 `evidence/baseline/`；最终本地构建日志、XML汇总、新增测试原始XML及生产输入/capture在 `evidence/smartqueue-local/`。
- Windows **39238611894 / head 0cee9a32de9751e582807c88984b44d800d188ba** 已成功；完整下载两个artifacts，实际XML **257 tests / 52 suites / 0 failures/errors/skipped**，全部 **20组 / 762 scenes / 1,295 assertions** 通过，包含额度26/13与独立reload 5/4。
- 独立发行ZIP **333,098,400 bytes**，SHA-256 **`33e754b423359d1970a6d09a44db923c389e83ab5f2f0b1cc3e43581a2d011b1`**；357 ZIP条目及JAR全部CRC、精确版本戳、该提交16项legacy pinned资源、Windows PE FFmpeg（164,352,000 bytes / `af7913b7ddc324bf02b8af72fe8612b1471455ef8d9e04e45466510742abcff9`）、0.6.5目标元数据和更新类均通过。
- 玻璃光学、12组组件/4组Popup、稳定复用/资源释放、Windows原生沉浸placement报告均PASSED；仅证明各报告范围，不能替代549→1431MB用户长时RSS或Android同内容像素验收。原始结果见 `evidence/windows-ci-0cee9a3/`。发行来源为实现 `0cee9a3`，不是文档HEAD。本轮不标记整体0.6.5迁移完成。

## 2. 歌词翻译语言路由（13d275c）

- 从正式 `53244b9` 纯树迁移 `MeloXLyricPreference`、5个设置选项、TTML行内/metadata/伴唱轨道选择，以及网易/QQ/酷狗中文译文的目标语言过滤；无目标译文保留原文、罗马音和真实时间线。
- 缓存和in-flight包含有效语言；同一请求快照传到AMLL查找、强绑定、Bilibili校验链和Apple Music TTML。播放器按新请求建立独立显示状态，切换语言的首个结果可在播放30秒后正常接收，旧请求不能写入新显示状态；三种歌词渲染共用。
- 6条新增文案及所有Locale回退来自固定Git树，源审计和可复现导出工具已加入；实现版本资源核验新增至17项。独立发行验证按发行精确提交读取清单，旧发行核验仍按原有16项。
- 本地全量 **265 tests / 54 suites / 0 failures/errors/skipped**，桌面编译成功。新增8项覆盖语言/简繁/地区归一化、缺译文、metadata与伴唱、中文端点过滤、晚返回缓存隔离与升级取消。
- 生产指针输入controls **22 scenes / 22 assertions**；独立JVM从en-US换到zh-TW重载 **11 scenes / 13 assertions**，共 **33 scenes / 35 assertions** 全部通过。验证持久化tr覆盖新系统语言，切回system即时解析为zh:Traditional；650ms等待保留展开完成的菜单帧，不把过渡中截图作为静态外观验收。
- 原始本地XML、全量测试/编译日志、17项资源与历史清单回查、两次回放/语义/UTF-8参数及选定截图在 `evidence/lyric-language/local/`。回归延迟源仅存在测试中，生产链仍使用实际provider与AMLL。
- 实现 **`13d275cc4bfa600fe3c7f7b6a963fc2a1c6b21bc`** 的 Windows **39243293868** 已成功；两个artifact完整下载并通过全部CRC，实际XML重计 **265 tests / 54 suites / 0 failures/errors/skipped**。下载到的 **20组 / 762 scenes / 1,295 assertions** 全通过；新语言controls/reload步骤在CI成功，但上传清单漏了两个目录，因此不能记为22组原始回放独立通过。核验脚本最初因22组预期失败，定位遗漏后按实际20组继续检查，缺失边界另记 `independent-recount.json`。上传清单在下一实现批修复，不能补写历史原始帧。
- 独立发行ZIP **333,109,728 bytes**，SHA-256 **`defd979629d93631e6c9cf82e36442bdcff390c2b67473c2a6fe815b6920ccb0`**；357条目与JAR全CRC、精确版本戳、该提交17项pinned资源、新语言类/资源和Windows PE FFmpeg均通过。玻璃光学、组件/Popup复用释放和Windows沉浸placement报告均PASSED。证据 `evidence/windows-ci-13d275c/`；发行来源仍为实现13d275c，歌词语言Windows原始回放验收因漏传保持PARTIAL。
- 行为说明 `LYRIC_LANGUAGE.md`，真源与验证证据 `evidence/lyric-language/`。固定Android 0.6.5同内容帧与网络账号实机验收仍待完成，整体保持PARTIAL。

## 3. 分屏与独立Chrome首批、背景流光修复（8684c2e）

- 固定0.6.5纯树迁移分屏默认开关、物理左右等分、设置子页可移动composition，以及Dock/迷你播放器独立六位置、同位置联动/不同位置展开、72dp侧栏和8dp安全间距、顶部/侧边页面避让；单栏回退保留偏好。Library分屏详情和退场内容保留已接线；Local本身只有Songs入口，账号Library回放、其他详情入口和完整播放器双栏仍待完成。
- 按用户反馈修复背景流光旧版小幅移动/亮度闪动：迁移颜色源游走、两层warp、8π连续周期和固定亮度；真实分析才驱动形状，无分析恒速漂移。CPU数组计算与原生位图安装分开，安装/释放同属UI线程；双缓冲索引跨effect保留，字节数组复用。
- 10条新设置文案与右栏launcher图来自固定Git树；所有Locale无覆盖，默认回退。新增资源清单19项验证通过，旧13d275c发行仍以其精确源提交按17项验证通过。
- 最终显式JDK17全量 **270 tests / 55 suites / 0 failures/errors/skipped**，桌面编译与流光生产Canvas审计通过。三档画质均有连续帧变化，暂停/减少动态冻结，恢复继续流动；相位回卷连续、无伪节拍亮度脉动、合计36次卸载/重建通过。没有以该短跑证明用户549→1431MB常驻增长已解决。
- 分屏controls **58 scenes / 69 assertions**、独立reload **6/14**、竖屏 **6/12**，合计 **70/95** 全通过；实际生产FFmpeg/播放器向明确标注的软件验收端点写入真实PCM，硬件听感未代验。当前分屏指针回放使用环境JDK21，Windows CI固定JDK17；不隐去平台差异。
- 新布局下歌词语言controls/reload再次通过 **33 scenes / 35 assertions**；本批已完成本地回放共 **5组 / 103 scenes / 130 assertions**。另一次完整设置route回放未生成capture.json，不计通过。初次位置回放遇到目标稳定等待不足，扩大物理点击的等待预算后完整回放通过；并发Gradle编译曾发生backup冲突，最终顺序以JDK17验证成功。
- 实现已正常推main **`8684c2e`**，本地原始XML、日志、capture/语义/UTF-8参数和选定截图在 `evidence/split-layout/local/`，行为及边界见 `SPLIT_LAYOUT_AND_FLOW.md`。CI新增流光审计与3组分屏回放，同时修复歌词语言两目录漏传；本实现Windows发行尚待成功构建、完整下载后独立核验。

## 4. Dock回放适配分屏宽度（7fca71b）

- Windows **39270464456 / head 8684c2e** 失败于旧Dock回放。下载Verification-Windows后重计原始XML：**270 tests / 55 suites / 0 failures/errors/skipped**；三档流光生产Canvas审计PASSED。旧回放固定拖动248px，分屏后实际段宽约减半，跨过多个标签导致Library断言失败；剩余回放/打包被跳过，**未产生发行ZIP，不计发行验收通过**。原始失败回放、截图及Windows流光证据见 `evidence/windows-ci-8684c2e/`。
- 增加可读的横向根标签定位；`hold-drag-between` 只从semantics读取两个真实按钮的中心坐标，然后发送真实指针按下／移动／释放。深浅两主题共 **14 scenes / 18 assertions** 通过，JDK17下设置全路由回归 **82 scenes / 65 assertions** 通过。该修复未用直接调用语义动作代替拖动。
- 修复实现 **`7fca71b`** 已正常推main，待新的Windows全量构建与独立发行核验；本地回放在 `evidence/dock-adaptive-local/`。最近可独立验证的发行仍为13d275c，缺失语言原始回放的历史边界保留。

## 5. 短分屏设置逐项滚入后断言（3a79b5c）

- Windows **39272967752 / head 7fca71b** 的原始Verification-Windows已完整下载并重计：**270 tests / 55 suites / 0 failures/errors/skipped**、三档流光审计PASSED，已完整通过 **11组 / 312 scenes / 295 assertions**，包括Dock两主题、歌词语言、三个分屏回放与en-US/zh-CN设置路由。zh-TW 800×600设置路由未完成，不计入通过数；后续步骤跳过，**未生成发行ZIP**。原始输入、完整/失败capture及失败语义快照在 `evidence/windows-ci-7fca71b/`；该CI上传清单未包含回放PNG。
- 一般页新增分屏设置后，短pane中的触感反馈行在视口之外，旧回放在同一帧断言三行可用性。修复保留全部启用/禁用检查，改为逐项真实滚轮滚入可视区后检查，不修改生产偏好或语义动作。
- JDK17顺序回归四种Locale（en-US/zh-CN/zh-TW/ja-JP），各 **88 scenes / 65 assertions**；controls **74/55**、独立reload **8/18**，合计 **6组 / 434 scenes / 333 assertions** 全通过。原始参数、capture、日志及一般页检查截图见 `evidence/settings-visible-local/`。
- 修复 **`3a79b5c`** 已正常推main，新的Windows全流程及发行独立核验仍待完成；整体0.6.5迁移保持PARTIAL。

## 6. Bilibili设置回放定位与剩余回归（14b4df0）

- Windows **39277455043 / head 3a79b5c** 已通过四Locale设置全路由、controls/reload及先前Dock/歌词语言/分屏回放。完整下载Verification-Windows后独立重计 **270 tests / 55 suites / 0 failures/errors/skipped**、流光审计PASSED，完整回放 **15组 / 582 scenes / 498 assertions** 通过；Bilibili controls在点击底栏设置时失败，后续跳过，**未生成发行ZIP**。原始输入、capture、失败语义/截图与汇总在 `evidence/windows-ci-3a79b5c/`。
- Bilibili脚本仍以全宽时代的765,745坐标点击设置；分屏后改为读取 `root-tab:Settings` 的实际位置并发送物理点击。检索其余capture脚本，未再发现同类固定底栏点击或固定Dock拖动。
- JDK17顺序实跑Bilibili controls/reload **16 scenes / 21 assertions**、完整歌词controls/reload **153/501**、Skyline四模式 **70/342**、真实生成WAV歌曲操作/独立reload **68/63**，共 **10组 / 307 scenes / 927 assertions** 全通过。歌词60键、Skyline17键/四awake范围/重置保留边界、歌曲操作聚合验证均通过；仅为各报告范围，不代替已登录账号、硬件播放或Android同内容验收。
- 修复 **`14b4df0`** 已正常推main；本地原始输入、语义、capture和日志在 `evidence/remaining-replays-local/`。新的Windows全量发行及完整下载后独立核验仍待完成，最近独立核验发行仍为13d275c。

## 7. 分屏／流光发行完成独立核验（14b4df0）

- Windows **[39285046285](https://github.com/lladlam/MeloX-Windows/actions/runs/39285046285) / head `14b4df0576b67ffd59a23e6670e76eb53e952caf` / SUCCESS**。完整下载Verification-Windows **40,184,748 bytes** 与MeloX-Desktop-Windows artifact **332,570,046 bytes**，与API报告大小一致、全部条目CRC通过；artifact哈希在 `evidence/windows-ci-14b4df0/artifact-downloads.json`。
- 独立重计原始XML **270 tests / 55 suites / 0 failures/errors/skipped**；全部 **25组 / 889 scenes / 1,425 assertions** 完整通过，逐组检查场景数等于输入步数+1。新增语言controls/reload原始capture已随本次发行下载，**33/35**独立通过；三个分屏回放 **70/95**，各组真实PCM写入均超过44100帧，验收输出端点明确为软件而非硬件。历史13d275c漏传结果不回填。
- 内层发行ZIP **333,279,974 bytes**，SHA-256 **`6780a498467bced4c9a964dbfa2d22801c54c20a08a9378e1b8eb822dba3c0a0`**；357条目及应用JAR全部CRC、精确版本戳、按该提交清单核验19项源资源、新分屏/语言类和固定0.6.5目标元数据均通过。
- Windows PE FFmpeg **164,352,000 bytes / `af7913b7ddc324bf02b8af72fe8612b1471455ef8d9e04e45466510742abcff9`** 核验通过。CI在Windows执行JAR/app-image/ZIP解码器验证成功，下载后的Linux独立检查核对PE字节与哈希，不声称在Linux执行Windows解码器或完成真实硬件听感。
- Windows三档流光生产Canvas报告及连续帧已下载：每档76789–76797/76800像素变化，暂停/减少动态冻结、恢复移动、周期回卷连续、无伪分析或节拍亮度脉动、36次卸载重建均通过；High两帧已人工查看。玻璃光学、组件/Popup复用释放及Windows原生沉浸placement报告均PASSED。
- 原始输入/capture、重点分屏/语言语义/截图/日志、55份XML及核验结果在 `evidence/windows-ci-14b4df0/`；资源和解码器按精确发行提交读取。最近独立核验发行升级为 **14b4df0**，不是后续文档HEAD。整体迁移继续 **PARTIAL**：Android同内容帧、账号/硬件闭环、Library账号详情/其余路由/完整播放器双栏及用户Windows长时内存增长仍待完成。

## 8. 手机版0.6.5主动对齐与沉浸式标题栏（8f9342e）

- 固定Android正式发行仍为 `53244b9d6b21d7e3a609e8cb11876efef70f218b`。本机Android工作树HEAD和当前`v0.6.5`标签已漂移，未把移动标签内容混入基线；新增 `MOBILE_UI_AUDIT.md`、`mobile-ui-source-index.json` 记录20个手机UI区域的固定源路径、哈希和动画调用位置。
- 首页“每日推荐/热歌榜/私人雷达”不再用精简页替换首页或直接开播：真实API歌曲进入 canonical 歌曲列表详情，父页保留；返回/再次打开由真实Escape验证。未登录每日推荐显示真实错误。发现页及非网易云首页的排行卡片不再空点击，按 `RankingCapability` 全页抓取并按“音源+原始ID”去重。
- 手机版 Home/Explore/Search 的旧 `PlaylistDetailScreen` 统一委派 canonical 详情；接入封面流光背景、可读性背板、下拉隐藏式搜索栏、840×600+分屏双列；未取得真实收藏态时不显示收藏按钮，provided songs 不写入虚构歌单ID。
- 播客详情、私信会话、账户详情改为覆盖层，保留父页滚动状态；私信登录态随真实会话revision刷新并在注销时清空。
- MiniPlay/完整播放器改用0.6.5线性master + 归一化临界阻尼内容曲线；外壳/封面共用关闭尾段22dp单峰下沉、状态机到位后140ms圆角坐平、背景页0.83等距内缩和黑舞台、过渡期命中拦截及MiniPlay矩形内重新打开。`capture_mobile_v065 player` 用显式动画时间轴采样，避免软件渲染墙钟把100ms拖成478ms。
- 新增持久化 `windows_immersive_caption`（默认关闭）：“沉浸式标题栏”。开启后正文避让34dp，标题按钮透明覆盖；播放器真实流光、详情封面调色板延伸到按钮栏，普通页沿用主题底色。桌面侧栏和歌词检查器也避让标题栏；拖动、双击最大化、按钮、边缘缩放、托盘关闭保留。
- 本地顺序验证：手机首页 **13/13**、播放器 **115/22**、桌面布局 controls **29/67** + lyrics-light **12/15**、两项几何/真实PCM核验 **41/82**、标题栏设置 **25/27** + 独立JVM **1/10**、`:shared:desktopTest` **276 tests / 0 failures/errors/skipped**。本机原生标题栏审计因无正尺寸显示器为 **UNSUPPORTED**。
- 证据在 `evidence/mobile-v45/`。整体迁移仍 **PARTIAL**：真实登录账户、物理触屏、可听硬件、长时内存、Android同内容像素和完整横屏/歌词天际/多语言仍未由本轮证明；Windows CI及发行独立核验见后续补记。
- 首次Windows运行 **39724720557 / 8f9342e** 在“Desktop promoted drag and native Touch scene paths”失败：`capture_desktop_search_scroll.py` 用Windows默认cp1252读取UTF-8语义JSON，遇到真实中文语义字符即抛 `UnicodeDecodeError`，不是搜索/拖动功能失败。已把CI用到的语义/JSON读取统一显式设为UTF-8并重新推送；该失败运行未生成发行，不作为通过证据。
- 修复 **`ba86270`** 的Windows **[39729927138](https://github.com/lladlam/MeloX-Windows/actions/runs/39729927138) / SUCCESS**。完整下载 `Verification-Windows`（135,778,814 bytes API报告）与 `MeloX-Desktop-Windows`（337,269,928 bytes API报告）；独立重计原始XML **57 suites / 276 tests / 0 failures/errors/skipped**，48份capture **1,458 scenes / 2,033 assertions** 全部通过且场景数=输入步数+1；所有capture的 `physical_audible_acceptance` 均明确为BLOCKED。
- `window-chrome.json` 在Windows Server 2025以真实AWT Robot通过：无原生标题栏、物理最大化/还原/全屏还原/最小化/双击/右下角缩放/关闭，以及**沉浸式标题栏**渐变上下像素连续、沉浸状态再次最大化和还原。`immersive-window`、`glass-optics`、`display-awake`、`desktop-layout`、`lyrics-controls`、`settings-icons` 均PASSED。原生输入、下载核验与独立重计在 `evidence/windows-ci-ba86270/`。
- 独立核验内层发行ZIP **337,978,332 bytes**，SHA-256 **`6b72c72189dcdb10543b2abb10a0150f9913461abaf97aacaf345403938de443`**；357条目CRC、构建修订戳等于`ba86270`、JAR/app-image/最终ZIP解码器与固定资源检查通过。最近可独立核验的Windows发行升级为 **ba86270**。
- 整体迁移仍为 **PARTIAL**：真实网易云账号收藏/云盘/私信、物理触屏与触摸键盘、可听Windows硬件输出、长时内存、Android同内容像素，以及播放器横屏双栏/天际歌词/完整多语言仍未由这些回放证明。

## 9. 统一详情操作与竖屏封面续迁

- 分享按钮和更多入口接通真实分享文本/系统剪贴板、四种排序、详情信息与真实歌单刷新；provided songs 分享真实歌曲链接，不拼接虚构网易云歌单链接。可见排序行与实际分享的200首公开热歌榜数据独立比较通过。
- 收藏态随真实会话revision刷新，未知状态不显示旧账户收藏态；订阅请求固定Cookie、旧会话结果不回写；自己的歌单不显示取消订阅。取消异常传播，已有歌曲时错误也可见。
- 按固定源保留480ms header tween，补22dp顶部落点、竖屏封面独立上抬30dp、MiniPlay展开时目标页封面姿态与切歌回弹（AutoMix期间不重复叠加）。横屏统一时间线与双栏仍待继续。
- 修正旧手机回放的验证缺口：只传LRC参数没有Local扫描，因此旧115/22不证明真实歌词显示；现经生产Local扫描及持久化歌词库加载，必须出现真实歌词行。失败排查与源依据见 `DETAIL_AND_ARTWORK_FOLLOWUP.md`。
- 本地最终：手机首页 **17 scenes / 20 assertions**，手机播放器 **145/28**，桌面controls **29/67**、lyrics-light **12/15**；独立几何/PCM重计 **41/82**；shared **57 suites / 276 tests / 0 failures/errors/skipped**，桌面编译通过。源码/回放/选定图片证据在 `evidence/mobile-v49/`，完整本地输出保留于build目录。静态桌面截图等待1200ms，不放宽几何容差。
- Windows **39743199195 / 94e8bc9 FAILED**：album回放通过，随后真实首页歌单打开详情容器但未在默认5秒断言预算内出现内容header。下载原始Verification后独立重计 **57 suites / 276 tests / 0 failures/errors/skipped**；上传的17组回放 **238 scenes / 330 assertions**。该revision漏传collection目录，无法从产物独立判断是请求错误还是加载时序；不把它判为已修复，不生成发行。记录 `evidence/windows-ci-94e8bc9/`。后续补collection完整上传，并为真实网络详情单独设30秒断言预算，仍要求实际header加载。最近已独立核验发行仍为 **ba86270**。

## 10. 设置页互补退让与可中断回程

- 增加声明式 `MeloXPageMotion`，设置父列表布局期左退宽度1/3，详情满宽右入；两页共用一个ζ=1、k=800弹簧进度，不分别重启animateTo。分栏左列表不退让，桌面设置保持独立tab呈现。
- 关闭期间保留内容/返回处理器；特别覆盖尚未完成推入就返回，此时current/target都可能为false，仍须等回程结束才能移除。退出中Escape不穿透，关闭中真实指针点击原列表位置能接管并重开。
- 新增可选显式scene时钟，仅供离屏动画回放；默认时钟行为保持原样。独立核对两个实际页面边缘的互补关系，不只检查进度标记。
- 最终单栏 **73 scenes / 11 assertions / 48 geometry frames**、双栏 **49/8/28**、减少动态 **45/8/18**，全部通过。设置全路由 **88/65**，桌面controls **29/67**；shared测试任务成功，原始XML **57 suites / 276 tests / 0 failures/errors/skipped**。中途并发编译发生一次输出目录竞争，最终三种模式顺序编译/回放成功；未将该失败算通过。
- 源依据、范围和验证方法见 `PAGE_MOTION_FOLLOWUP.md`；证据 `evidence/page-motion-local/`。CI加入三种模式及真实Local歌词播放器回放、完整帧上传。共享封面、搜索及通用详情的接线、0.92预测返回缩放和Lamé平滑角仍待迁移；整体保持PARTIAL。Windows构建及完整发行独立核验待补记。

## 11. Windows collection失败取证补齐

- 为后续Windows失败补传collection完整截图、semantics、参数与日志；真实网络详情断言预算改为30秒，默认其它回放仍为5秒。历史94e8bc9失败原因尚不能由缺失帧独立确定，不声称已经证明修复根因。
- 本地实际公开歌单深色en-US、浅色zh-CN各 **38 scenes / 45 assertions** 全通过，包含真实首页入口、拖动/搜索、歌曲菜单、入队、歌曲详情、艺人路由与返回。源几何独立核验通过；证据 `evidence/collection-v55/`。等待新Windows构建和完整发行独立核验。

## 12. Dock与侧面歌词的MiniPlay材质统一

- 按用户要求，去掉桌面侧栏旧的单独表面色/12dp模糊，复用固定Android MiniPlay的0.52涂层、2dp模糊、24/24dp折射、高光和内阴影；右侧歌词/队列共用同一材质函数。保留磨砂设置分支。
- Dock从右侧相邻页面实时取样；经实图复查，将整页镜像改为相邻180dp压缩到28dp侧面并向内渐隐，避免倒字/封面污染导航。歌词栏沿左边缘做同样处理，正文区铺相同中性底色。页面与collection详情统一录制，排除采样者；Dock绘制置于页面录制后。
- 新增生产材质光学审计 **8 cases PASSED**，覆盖1×/2×、深浅色、液态/磨砂，验证实时侧面变化、主体材质一致与filter释放。真实首页深浅主题滚轮回放均通过：侧面96%–98%像素随页面更新，Dock正文0像素变化；独立工具按semantics实际边界复核PNG。既有玻璃光学/组件审计通过。
- 证据 `evidence/desktop-panel-glass/`；实现说明 `DESKTOP_PANEL_GLASS.md`。Windows发行核验仍待后续网络恢复后完成，本批不以离屏像素宣称Windows发行完成。

## 13. 网易云搜索请求减量、实际取消与冷却

- 用户指出主要是搜索报请求过多。定位桌面300/320ms建议词与正式搜索双路触发、分类/建议词遗漏实际HTTP取消、重复关键词无缓存及各模块无共享请求预算。
- 桌面正式搜索800ms、建议词600ms且至少2字符；显式提交立即执行。各分类与建议词补实际Call取消；内存64条/120秒搜索缓存，按会话、接口/参数与transport隔离，合并重复并发，不缓存失败/取消。
- 网易云API共享2并发、350ms发起间隔；429/-462/明确频繁消息触发至少60秒冷却并尊重Retry-After，显示剩余秒数；不自动重试。音频和封面CDN不参与限流。
- 顺带将首页fallback改成缺失时才读取，复用“全部”区域热歌，减少搜索同时发生的后台请求竞争。
- shared **58 suites / 285 tests / 0 failures/errors/skipped**，桌面编译通过。真实公开搜索13 scenes回放通过；协议层确认3次相同搜索仅1次HTTP及媒体/建议词实际Call取消。证据 `evidence/netease-search-traffic/`，说明 `NETEASE_SEARCH_TRAFFIC.md`。尚无长期风控或新Windows发行验收结论。

## 14. 完整歌词回放失败的补验

- 迟到的v58后台通知确认完整深浅歌词回放失败，不覆盖此前已通过的独立材质/侧面像素检查。排查语义证实目标行未创建时，reveal在窗口中央滚动首页；增加显式所属面板/方向与可选原子指针点击，避免截图耗时触发歌词自动跟随后再点击旧行。
- 串行v61深色、单独v62浅色各 **10 scenes / 18 assertions PASSED**，实际滚轮找行、鼠标点击跳转、恢复播放及队列/歌词切换全部通过。v61浅色被外层总超时中断，不计通过。v58类加载错误本次未复现，未将推测并行构建原因记为已证实。
- 证据 `evidence/desktop-panel-glass/full-lyrics-replay/`，详见 `DESKTOP_PANEL_GLASS.md`。本次仅修回放工具，不改生产歌词交互。

## 15. 电脑UI收藏歌曲/收藏歌单读取

- 修复独立session未刷新profile导致userId为空、静默空列表；补账号错误显示。拆除电脑收藏入口对整套snapshot的依赖，歌单/歌曲/历史独立获取，避免任一无关接口失败拖累当前页。
- 相关HTTP接入owner取消，账号revision清空旧列表与详情；保留真实ID和喜欢顺序。
- shared **59 suites / 289 tests / 0 failures/errors/skipped**，桌面编译通过。协议fixture验证独立请求及实际Call取消；未宣称私人账号收藏数量已实测。说明 `DESKTOP_LIBRARY_LOADING.md`。

## 16. MELOX-LAN v1 局域网播放迁移

- 依据 Android `docs/MELOX-LAN.md` 与 `lan/` 源码迁移发现、PIN/信任、控制、接力、PCM 投放、Together 及中继 token。
- 播放全部接真实 PlaybackCommands/AudioPlayer；投放只取 Java Sound 实际接受的 PCM；中继只代理真实 Range 音频且不泄露 Cookie。底栏与设置入口提供设备名/端口/PIN/信任列表/最近连接。
- shared **292 tests / 0 failures/errors/skipped**，桌面编译通过。新增真实 loopback socket 协议测试与真实 UDP 发现测试。未验证真实 Windows↔Android 跨机、防火墙、物理扬声器；详见 `LAN_PLAYBACK.md`。

## 17. Windows 关于页品牌与图标

- 关于页原读取 macOS 参考字符串，显示“洛汐聚合体”并复用 macOS Logo；改为 Windows 自有 `branding/melox-windows.png`（派生自 MeloX-Android 固定启动图标）与 lladlam 开发者/版权信息，首次引导、托盘、侧栏占位同步。
- 真实设置导航+鼠标点击截图验证；参考资源字节未改。详见 `WINDOWS_ABOUT_BRANDING.md`，证据 `evidence/windows-branding-about/`。

## 18. 第三方音乐源双 UI 入口与真实收藏能力

- QQ／酷狗／YouTube 首页新增独立电脑排版；电脑发现页的原生榜单不再被缺少预览歌曲过滤，统一读取真实 RankingCapability 分页后打开桌面详情，保留完整队列、筛选、随机播放与歌曲菜单。
- Spotify／YouTube 音乐库补齐歌单、收藏专辑、关注歌手；本地音乐补齐歌单和新建歌单，歌曲列表提供实际分页与桌面菜单。手机沿用固定标题、液态分段选择器、66dp 歌曲行及独立详情；电脑使用侧栏、封面网格与桌面行。
- 修复第三方音乐库登录误开网易云表单，QQ／酷狗／Bilibili／Jellyfin 未登录也保持电脑布局和对应登录入口。Spotify 可以进入已有 Client ID／OAuth 配置页，未登录账号读取遵守可空契约，401 刷新接受用户配置的 Client ID。
- 首页／发现按来源实际 feed 能力过滤，云盘／播客限定网易云；来源切换恢复各自栏目。Spotify／YouTube 会话 revision 清理旧音乐库组合与栏目任务，普通 Spotify access-token 刷新不反复重启读取。
- 本地桌面编译、61 suites／297 tests 通过，零失败；12 组生产 UI 回放共 107 scenes／139 assertions 通过，覆盖真实鼠标／滚轮／键盘、本地 50→55 首分页、新建与打开歌单、来源账号表单、QQ／酷狗真实公开首页和发现页榜单进退。最终榜单断言必须出现实际歌曲行；旧 v3 两组仅导航回放不计内容验收。补齐酷狗 KMR v2 嵌套音频 hash／毫秒时长解析，并过滤 QQ 不支持音频详情的 MV 榜。
- 回放未构造登录账号、Cookie 或在线收藏数据；私人账号实际收藏数量、真实 OAuth 成功与新 Windows 发行完整 ZIP 尚未在本批验收。失败回放及补验口径见 `PROVIDER_DUAL_UI.md`，原始通过证据 `evidence/provider-dual-ui/`。

## 19. 液态玻璃开关外观与取消回弹

- 修正 40×24dp 滑块相对 64×28dp 轨道多出的 2dp 垂直偏移；补回固定 Android 轨道采样随按压“吸起”变形，真实轨道及页面底图保持原坐标。
- 实际指针基线复现拖动中途禁用后滑块卡在中间；修复拖动态清理、按压释放、连续拖动累计及旧定位任务覆盖回弹，拒绝变更继续以外部 checked 为准。减少动态时直接定位且不拉伸。
- 桌面编译与完整 `auditGlassOptics` 通过，三档密度／深浅色／液态和磨砂共 12 组均通过滑块居中、拒绝拖动回弹、中途禁用恢复及下一次点击唯一提交断言，原生滤镜释放与弹窗回归通过。
- 详见 `LIQUID_TOGGLE_FIX.md` 及 `evidence/liquid-toggle-fix/`；尚不声称固定 Android 像素逐点一致或新 Windows 发行 ZIP 验收完成。
