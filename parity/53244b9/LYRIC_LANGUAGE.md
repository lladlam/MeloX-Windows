# 歌词语言迁移：正式 Android v0.6.5

真源：`53244b9d6b21d7e3a609e8cb11876efef70f218b`，纯 Git 导出及哈希见
`evidence/lyric-language/source-intake.json`。本批为统一歌词增量的语言路由部分。

## 生产链

- `lyric_language` 使用真源的 `system`、`zh-Hans`、`zh-Hant`、`en`、`tr` 五选项；偏好立即更新运行态并持久化。
- `MeloXLyricPreference` 按基础语言匹配译文，例如 `en-US` 与 `en` 共用语言缓存；中文简繁分别缓存。
- TTML 的行内、metadata 和伴唱翻译均按目标语言选择；没有目标语言保留原文和真实字时，译文为空。
- 网易、QQ、酷狗的现有端点只返回中文译文；非中文偏好不显示这些译文，原文、罗马音和时间线保留。
- 中文沿用真源的 Hans/Hant 轨道选择和 ICU 字形转换，精确选中的 AMLL 主译文保留原有适配标记语义。
- 单次请求的语言快照传到缓存、in-flight、AMLL 直查/跨源查找、绑定、Bilibili 校验链及 Apple Music TTML；异步请求不重新读取后来选择的语言。
- 播放器在歌曲、有效语言偏好或歌词源设置变化时建立新的显示状态；旧加载/升级只持有旧状态。首个新语言结果不受 30 秒 AMLL 升级窗口限制；随后自动升级保留原窗口约束。Apple Music、Eva、TextPV 共用此状态。

## 文案与交付验证

- 6 条新增文案来自固定树 `values/strings.xml`。遍历该树所有 `values*/strings.xml`，确认这些键没有本地化覆盖，因此任何 Locale 均按 Android 规则回退默认；不自行翻译。
- 可复核：`python3 tools/export_v065_lyric_language.py ../MeloX-Android --verify`。
- 新资源以 LF 固定，纳入 `verify_pinned_resources.py`，实现版本应验证 17 项资源。
- 独立发行验证新增 `--source-revision`，从发行的精确 Git 提交读取期望资源清单；旧发行仍按其本身版本的清单核验。
- 新增物理输入 controls/reload；第二个 JVM 从英文改用繁体中文 Locale，验证持久化土耳其语覆盖系统语言，再切回 system 验证实时解析为繁体中文。

## 验收边界

该批不代表完整统一歌词链、所有 0.6.5 功能或像素/动态一致性完成。网络账户实机和固定 Android 0.6.5 同内容帧仍待验收；本地单元回归中的可控延迟源仅用于竞态测试，生产数据源仍为真实 provider/AMLL。
