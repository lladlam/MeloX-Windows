# 0.6.5 迁移记录

## 1. 切换正式发行基线与智能队列额度（0cee9a3）

- 用户指定基于0.6.5继续迁移；核对正式release为 `v0.6.5` / `53244b9d6b21d7e3a609e8cb11876efef70f218b` / versionCode24，区别于旧 `0.6.5` / versionCode23。新增活跃基线入口和独立矩阵、原始38提交/389文件清单与真源导出哈希。
- 迁移 `smartQueueAnalysisLimit` 默认60、负数-1不限、正数10–500；5个原文选择接真实持久化。futureItems排除历史，额度按队列generation一次性应用，fallback仅等待额度内有效候选。
- 队列改为完整时间线，仅原位重排分析匹配；记录链尾并保护当前/历史，额度外条目保留原序。分析用两个worker避免对大歌单逐项创建协程；关闭AutoMix/SmartQueue取消任务，旧generation结果不能写入新队列。已有真实双解码PCM、过渡取消及失败保留回归继续运行。
- 本地相关shared回归和桌面编译成功；最终全量 **257 tests / 52 suites / 0 failures/errors/skipped**，桌面编译成功。新增真实15首生成WAV、从第3首播放且额度10的测试，实际只解析/成功分析11首，历史/额度尾部不分析，切歌不重置额度；90/140/92 BPM完整队列重排通过。成功分析数从builder只读诊断读取，URI解析集从真实resolver边界记录，未用假分析输出。
- 真实鼠标/滚轮操作额度五选项、Smart/Fixed显隐：**26 scenes / 13 assertions**；独立JVM真实偏好重载：**5 scenes / 4 assertions**，均通过。
- 原始基线、正式release/tag解析、纯Git源文件哈希在 `evidence/baseline/`；最终本地构建日志、XML汇总、新增测试原始XML及生产输入/capture在 `evidence/smartqueue-local/`。
- Windows新run **39238611894 / head 0cee9a32de9751e582807c88984b44d800d188ba** 验证中，已接入新增额度controls和独立reload。成功后自动完整下载两个artifacts并重新检查XML、全部capture、发行ZIP/JAR CRC、精确版本/16项legacy pinned资源、显式Windows解码器、0.6.5目标元数据及更新类；最终结果另记。本轮不标记整体0.6.5迁移完成。

## 2. 歌词翻译语言路由

- 从正式 `53244b9` 纯树迁移 `MeloXLyricPreference`、5个设置选项、TTML行内/metadata/伴唱轨道选择，以及网易/QQ/酷狗中文译文的目标语言过滤；无目标译文保留原文、罗马音和真实时间线。
- 缓存和in-flight包含有效语言；同一请求快照传到AMLL查找、强绑定、Bilibili校验链和Apple Music TTML。播放器按新请求建立独立显示状态，切换语言的首个结果可在播放30秒后正常接收，旧请求不能写入新显示状态；三种歌词渲染共用。
- 6条新增文案及所有Locale回退来自固定Git树，源审计和可复现导出工具已加入；实现版本资源核验新增至17项。独立发行验证按发行精确提交读取清单，旧发行核验仍按原有16项。
- 本地全量 **265 tests / 54 suites / 0 failures/errors/skipped**，桌面编译成功。新增8项覆盖语言/简繁/地区归一化、缺译文、metadata与伴唱、中文端点过滤、晚返回缓存隔离与升级取消。
- 新增物理设置controls及独立JVM换Locale reload，已接入Windows CI；实际回放结果和新发行核验另记。
- 行为说明 `LYRIC_LANGUAGE.md`，真源与验证证据 `evidence/lyric-language/`。固定Android 0.6.5同内容帧与网络账号实机验收仍待完成，整体保持PARTIAL。
