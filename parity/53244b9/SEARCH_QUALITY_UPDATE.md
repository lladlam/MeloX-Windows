# 实时 Android 搜索聚合增量

## 范围

基于 `15ce39e` 之后实时监听捕获的 51 文件变更，继续对照 Android 工作区与按内容哈希保存的快照（包含未提交改动）。本批实现五个来源的真实搜索卡片音质提示及共用聚合推荐逻辑。手机与电脑均由 `SearchScreen` 加载共用 unified search 业务，电脑用 `MeloXDesktopSearchScaffold` 展示同一批聚合推荐；不引入测试业务到生产。

- `MusicTrack` 与旧 UI 的 `SearchSong` 增加可空 `bestQuality`／`bestBitrate`；码率单位 bits/s。
- 网易云读取 hr／sq／h／m／l 或 quality 内的非零码率，同时覆盖 canonical、universal 搜索及 provider 映射；补图不丢音质。
- QQ 读取旧 file size、size_new 数组／对象及 size 数组，识别 master／atmos／hi-res／flac 等格式。
- 酷狗读取非零变体文件大小；酷我读取 N_MINFO／MINFO 与单格式 fallback。酷我先统一码率单位再判断等级，避免把 320000 bits/s 错判为无损。
- 汽水读取 bit_rate／bitrate／max_bitrate／br，保持字符串资源 ID。
- 上游没返回提示时保留 unknown，不以音源固定最高等级填充。搜索提示不提升原始 availability，不代替真实账号权限与播放链接解析。
- 同曲推荐先比较可播放状态，再比较音质和码率；大幅时长差异单独分组，10 秒内漂移仍合并；合作歌手顺序规范化。版本分类、native ID 与实际 PCM／播放解析链保持真实消费者。

## 验证

新增业务回归覆盖：标准可播胜会员无损、试听与可播边界、音质胜固定来源偏好、缺字段不假报最高音质、时长／版本／合作歌手去重，以及五源响应形状与码率单位。既有真实 EAPI／HTTP loopback 协议测试扩展检查补图、provider 映射和分页后音质保留。这些响应夹具仅用于协议回归，不算上游账号或实际听音验收。

本批最终本地 **69 suites／359 tests，零失败／错误／跳过**，桌面 Kotlin 编译通过。9 项新增业务回归与扩展后的真实 EAPI 协议回归 XML、最终构建压缩日志和汇总在 `evidence/search-quality/`。Windows CI 与新发行仍须完成后完整下载独立核验。

## 本次 51 文件中仍需继续迁移

Android 新增的 `MeloXUnifiedFavorites` 是独立于音源账号的应用自有「我喜欢」：初始化时逐来源读取真实收藏、报告部分失败、按同曲规则去重，并保存完整 provider 元数据，之后提供增删与排序。Windows 的现有各平台收藏入口不等同于此功能；统一收藏初始化／持久化／两套 UI 入口仍需接入。

`MeloXTrackJson` 的全元数据序列化与账号安全偏好迁移也需分别核对 Windows 的现有队列、下载、推荐、私有会话存储兼容。新增 Android API 门控／Binder／Xiaomi 后台机制须按 Windows 平台后端实现判断，不按文件数量声明迁移完成。其余 Apple／Jellyfin／本地等来源的增量提示尚需继续对照；本批不将它们列为已迁移。
