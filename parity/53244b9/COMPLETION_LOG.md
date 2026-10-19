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
