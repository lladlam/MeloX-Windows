# 应用自有统一「我喜欢」迁移

## 业务与持久化

对照 Android 当前 `MeloXUnifiedFavorites`、`MeloXTrackJson` 及 settings／library／song actions 的真实消费者，接入 Windows 共用业务层。

- 内容设置提供双布局实际开关。首次启用可选择来源，逐来源读取真实已有收藏；不选来源可创建空歌单。初始化报告每个失败来源，保留成功来源数据；取消任务不会把部分结果标为已完成。
- 网易云使用固定 Cookie 读取当前真实账号与全部 liked IDs，再分批读取详情并检查完整性；账号改变则报告失败。其他支持库／歌单的来源读取全部页，检测重复页／提前空页，并在结束后核对账号。Local 导入真实索引内的 `isFavorite` 曲目。
- 歌曲菜单、电脑播放器、歌曲详情与搜索／歌单滑动收藏操作在开关启用时写入应用自有存储；关闭时恢复既有来源收藏逻辑，已保存的应用收藏仍保留。
- 增删／排序使用同一锁进行读改写；初始化网络读取完成后重新读取当前数据再合并。持久化完成后发布 revision。沿用同曲／版本／时长规则，并在跨来源显式添加时保留已有较高音质身份。
- 完整 `MusicTrack` JSON 保存 native source／resource ID、歌手与专辑 ID、availability／搜索音质提示，以及 QQ mediaMid、酷狗 hash／albumAudioId、Bilibili cid／page、YouTube setVideoId、Jellyfin server／playlist entry、Local 文件位置等解析所需元数据。账号凭据仍由原账号存储提供。
- 播放队列也新增完整 providerTrack JSON，旧队列字段仍可读取，独立重载后保留原始资源身份与质量提示。损坏／不识别的收藏记录会阻止覆盖原始存储并报告读取错误。

## 双 UI

音乐库在来源登录门控之外显示应用自有入口，因此切换音源／退出账号仍可打开收藏。电脑用紧凑入口、桌面曲目行和菜单；手机用曲目列表与手机版歌曲操作页。独立详情提供真实播放全部、逐曲播放、菜单、上移／下移、移除与返回。

顺带补齐手机版 provider 音乐库曲目的更多按钮；路由实际进入手机版操作页。全屏歌曲操作页增加显式关闭按钮，避免只能依赖手势收起。

## 验证

- 最终串行全量 **70 suites／366 tests，零失败／错误／跳过**；桌面编译通过。新增 7 项业务回归覆盖完整元数据／队列往返、部分失败、原生身份、跨源质量保留、并发添加／排序、任务取消、损坏记录保护与旧队列兼容。
- `tools/capture_unified_favorites.py`：两个实际生成的 45 秒 WAV 经真实 Local 扫描和解码，使用鼠标／滚轮／键盘操作设置与菜单。双布局 controls＋独立 JVM reload 共 **90 scenes／46 assertions** 全通过；测试创建空收藏、添加两首、排序、真实 PCM 播放、移除、停用保留／重新启用，并在第二个 JVM 切到未登录网易云后打开收藏并再次解码原 Local 文件。
- semantics 仅定位与断言；真实输入和 PCM 记录随 capture 保存。软件 PCM 验证不算物理可听；私人账号实际收藏导入尚未验收。
- 首轮回放修正了 fixture 对 Local 24 字符 key／state 分隔符的理解；还发现并修复手机版 provider 列表菜单分发与全屏关闭入口。一次构建验证出现类加载／LAN 发现断言失败，原失败日志保留；最终串行全量通过，没有跳过失败测试。

证据 `evidence/unified-favorites/`。Windows workflow 加入两套 controls／reload 及相应失败诊断上传；该增量的新 Windows CI／完整发行需后续核验。既有成功包 `40288772016`／`af4a9a4` 不包含这次新功能。

2026-10-27 更新：`d547727` 的 Windows CI **40325542270 已成功**，其中统一收藏双布局实际输入与独立 reload step 成功；完整下载发行 ZIP 339799993 bytes／357 entries，由独立核验工具确认源码 SHA、CRC、固定资源和 PE 解码器全部通过。SHA-256 `28d940af34a0118243a1a793825122a70db1d649af57855218aa4a429324bb45`，证据 `evidence/windows-download-40325542270/`。这次验收包含统一收藏功能，仍不冒称已验私人音源收藏导入。

## 最新 Android 四文件通知

监听再次捕获 ArtworkDynamicPalette、FlowingLightBackdrop、IOSNowPlayingSharedHost／V2 的增量：AutoMix 交接前同时从 outgoing／incoming 两张真实封面提取调色板，按等面积合并 3×3 mesh；缺失封面使用另一张真实封面；提取 effect 只以封面身份为 key，避免随每帧进度重复解码。已读取内容快照差异并恢复监听，此视觉增量仍需接到 Windows 两套背景消费者，不计入本次统一收藏已迁移范围。
