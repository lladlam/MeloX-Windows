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
