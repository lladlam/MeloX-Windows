# Android 0.7 罗马音发音单元与逐视觉行对齐

## 真源与迁移

读取当前 Android 工作区的 `core/lyrics/JapaneseRubyAlignment.kt`、`LyricRomanizationAligner.kt` 与 `ui/player/MeloXIOSLyricsPanel.kt:1615–1897`。接入 Windows 两套 UI 共用的 `MeloXRubyLyricText`，不替换真实歌词加载／缓存／source identity。

- 增加假名锚点、汉字词组、拗音、促音、标点及源 reading 字符范围的对齐；仅读取已供应的罗马音，不调用假词典生成汉字读音。无法匹配时保留原完整 reading。
- 对齐搜索限制原文 256／reading 1024 字符，memo 有界；保留 timed yromalrc 和词片段。
- 用实际 Compose 段落测量得到每个视觉行的 UTF-16 范围，逐行切原文／歌词时序／罗马音，再按各单元字符 bounds 居中绘制其 reading。逐字开关两种情况下均走视觉行对齐，不再把整段注音附在整段底部。
- 注音沿用实际字号／字体粗细／透明度和 flipped／RTL 几何；按邻居中点分配区域、缩放并裁剪，避免读音碰撞。绘制复用 Compose 已 shaping 的 paragraph，不另用不一致的原生字体测量。
- 文本测量缓存为 0，remember 仅保留当前 row；原歌词 glyph mask 的 close／时钟订阅方式保留。此项不能当作长时内存验收。

## 迁移回归中发现的真源问题

`世界へ君のこと`／`sekai e kimi no koto` 的 Android 短前缀搜索可以把 `世界` 误配为 `s`，把 `へ` 配为 `sekai` 内部的 `e`，再把 `kai e kimi` 给 `君`。Windows 在搜索汉字跨度时优先使用上游 reading 已有的空格／标点边界，再尝试无分词输入的其他跨度；新增该实际反例断言。没有改写 Android 工作区。

首次本地 TTML 回放用了当前解析器不支持的 `0s` 时间写法，加载为空；fixture 已改用完整 `00:00:00.000`。这属于回放输入修正，不把失败回放计入通过。

## 同期 Android 更新继续追踪

监听捕获到统一歌词加载器新增值参数入口，播放 Service／悬浮歌词 Service 改为完整 media ID 的多音源统一加载，并补 invalid identity 回归。已检查对照前一份哈希快照；对应 Windows 系统媒体／悬浮窗口 backend 尚待接入，不能把当前应用内共用歌词链算作已迁完该后台功能。

之后又捕获 `PlaybackTrackIdentity` 改为 JVM-safe percent decoding，保留 `+` 字面值。Windows 现有 media ID 是原始 value 编解码，LAN 传输另带 source／native value；迁入 percent-encoded media ID 需要同时处理旧队列／本地 ID 兼容，列入后续身份格式审计，不对现有原始 ID 单方面解码。

## 验证

最终 **68 suites / 348 tests，零失败／错误／跳过**，桌面编译通过。7 个新增回归覆盖读音歧义、拗音／促音、标点与代理对、未匹配读音保留、视觉折行的 authored 时间切片、重复字符 offset 和有界搜索。

双布局使用真实 Local 扫描／解码／歌词持久化，真实指针进入播放器：手机 **9 scenes / 9 assertions，3 行原词／3 行注音**；电脑 **8 scenes / 9 assertions，2 行原词／2 行注音**。验证视觉行 bounds 无重叠，每一行各有独立注音，且实际 PCM 写入超过 44100 帧。截图人工检查亦确认注音跟随实际换行。

生成的 Local 文件仅用于实际扫描／解码／渲染；不代表线上来源歌词匹配、AutoSync 推理、跨机或物理可听验收。证据 `evidence/romanization-070/summary.json`、全量测试 XML、双布局截图／只读 semantics／输入／PCM 记录与压缩构建日志。CI 增加同一套双布局回放。
