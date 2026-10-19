# 0.6.5 迁移记录

## 1. 切换正式发行基线与智能队列额度（0cee9a3）

- 用户指定基于0.6.5继续迁移；核对正式release为 `v0.6.5` / `53244b9d6b21d7e3a609e8cb11876efef70f218b` / versionCode24，区别于旧 `0.6.5` / versionCode23。新增活跃基线入口和独立矩阵、原始38提交/389文件清单与真源导出哈希。
- 迁移 `smartQueueAnalysisLimit` 默认60、负数-1不限、正数10–500；5个原文选择接真实持久化。futureItems排除历史，额度按队列generation一次性应用，fallback仅等待额度内有效候选。
- 队列改为完整时间线，仅原位重排分析匹配；记录链尾并保护当前/历史，额度外条目保留原序。分析用两个worker避免对大歌单逐项创建协程；关闭AutoMix/SmartQueue取消任务，旧generation结果不能写入新队列。已有真实双解码PCM、过渡取消及失败保留回归继续运行。
- 本地相关shared回归和桌面编译成功；最终全量 **257 tests / 52 suites / 0 failures/errors/skipped**，桌面编译成功。新增真实15首生成WAV、从第3首播放且额度10的测试，实际只解析/成功分析11首，历史/额度尾部不分析，切歌不重置额度；90/140/92 BPM完整队列重排通过。成功分析数从builder只读诊断读取，URI解析集从真实resolver边界记录，未用假分析输出。
- 真实鼠标/滚轮操作额度五选项、Smart/Fixed显隐：**26 scenes / 13 assertions**；独立JVM真实偏好重载：**5 scenes / 4 assertions**，均通过。
- 原始基线、正式release/tag解析、纯Git源文件哈希在 `evidence/baseline/`；最终本地构建日志、XML汇总、新增测试原始XML及生产输入/capture在 `evidence/smartqueue-local/`。
- Windows新run **39238611894 / head 0cee9a32de9751e582807c88984b44d800d188ba** 验证中，已接入新增额度controls和独立reload。成功后自动完整下载两个artifacts并重新检查XML、全部capture、发行ZIP/JAR CRC、精确版本/16项legacy pinned资源、显式Windows解码器、0.6.5目标元数据及更新类；最终结果另记。本轮不标记整体0.6.5迁移完成。
