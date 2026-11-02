# Android 当前源码对照（2026-10-30）

Android 本地及远端 `main` 均为 `7c17c009b829c2a0c75c4316dd52d00ebf218c0a`，工作区干净；Windows 对照基线为 `b6ebfb3e169b04a708e6889c760e25cee7a7ffa4`。本记录汇总已核查的主要缺口，不以文件名或设置项存在作为功能完成证明；详细背景和实机边界见 `FEATURE_SURFACE_AUDIT.md` 与各功能更新记录。

| 范围 | Android 与 Windows 当前差异 | 下一步 |
| --- | --- | --- |
| 日文 AutoSync | Android 的 `AutoSyncCoordinator.JaEngineHandle` 用 OpenJTalk、NAIST 词典和日语 CTC 映射原文字；Windows 已有真实词典管理、随包原生库、字符映射及模型推理，基线尚未接设置和播放器 | 本轮接入设置与生产链，真实模型／词典、缓存与两套 UI 验证正在进行 |
| 系统媒体和悬浮歌词 | Android 使用系统媒体服务和悬浮歌词；基线 Windows 的相关未支持项禁用 | 本轮接入置顶歌词窗口，见 `FLOATING_LYRICS_UPDATE.md`；Windows SMTC 仍未实现，窗口待 Windows 原生验收 |
| USB／MiPlay | Android 使用其平台 USB 和 Binder 后端；Windows 无等价后端 | 按 Windows 设备 API 单独实现并以真实硬件验收 |
| 云控与规则 | Windows 已有基础签名配置与熔断；Android 的动态文字、主题和脚本能力仍未完整对齐 | 本轮接签名歌词匹配容差和网易搜索标题后缀规则；签名、边界、授权与版本回退回归通过，其余逐字段核对生产消费者和受限脚本运行时 |
| 引导、法律、关于、多语言 | Android 全屏连续引导及最新平台文案与 Windows 未逐项核销；部分 Windows 文案仍硬编码，土耳其语等不完整 | 对照当前 Android 页面和资源，分别补齐双布局及本地化 |
| YouTube、酷狗概念版和其他账号 | 基础真实业务已接；YouTube 上游普通目录／写请求并发隔离及私人账号流程未完全证明 | 用真实账号／多频道和退出重登验收，补齐会话隔离 |
| LAN／DLNA 和音频 | 实现及回环回归存在，跨机、物理设备和实际听音尚未证明；自动混音近期修正仍待真实歌曲验收 | Windows↔Android、实际渲染器和硬件输出实测 |
| 日文以外的歌词与页面 | 首页内容块、目录分页、细节动画以及翻译／罗马音的真实在线内容仍需对照；部分操作需要私人账号 | 按真实来源、两套布局和用户输入逐项验证 |

Windows CI `40989598523` 对 `b6ebfb3` 已通过，发行包独立核验见 `AUTOSYNC_UPDATE.md`。本记录的缺口包含尚未迁移及需要实机验收的项目，不将回环、合成音频或编译成功当作跨机／物理验收。

## 2026-11-01 增量

Android 本地及远端 `main` 已前进到 `0baa7c4b821c6cc7c3a9abd39e49b84bb0dc11f5`，新增自动歌词选择优先真实逐词时间。Android 工作区另有未提交的歌词格式、无时间歌词、在线搜索及本地媒体增量，应按实时源码继续核对，不视为已发布基线。Windows 本轮接入逐词优先策略，本地 `LyricsLoadTest` 已通过；AutoSync 控件回放滚轮落点修正的本地验证见 `AUTOSYNC_UPDATE.md`，Windows CI 结果仍待确认。

## 2026-11-02 本地同步歌词

对照 Android 未提交工作区的 `LyricFileParser`、`LyricifyLyricsParser` 与本地扫描增量，Windows 本地 provider 新增同目录同名 LRC／KRC／QRC／TTML／Lyricify Lines 和 Syllable 的实际读取；按逐词信息优先选择，过滤常见占位语句，文件与 KRC 解压内容均有大小限制。读取发生在歌词加载时，不覆盖原有识别缓存；本地歌词禁用跨次加载的内存缓存，使新建、修改和移除 sidecar 可在下次加载时反映。

本地 `LocalLyricsSidecarTest`、`DesktopLocalLyricsPersistenceTest`、`LyricsLoadTest` 通过，覆盖真实文件、编码路径、Lyricify 对唱／和声、压缩 KRC、同名候选与修改回退；`tools/capture_local_sidecar.py` 在 Linux 虚拟桌面使用真实鼠标打开两种布局的播放器，生成 WAV 进入软件 PCM 并显示逐词 sidecar，手机 5 场景、桌面 4 场景通过。Windows CI 与实际硬件听音仍待验证。

后续接入纯文本无时间轴歌词：模型显式区分无时间轴，歌词选择优先同步结果，过滤占位文本；时间线事件、伪逐字、AutoSync 和悬浮歌词均不将纯文本当作可同步歌词。两种播放器布局共用手动滚动视图，鼠标／滚轮回放验证播放期间位置保持、歌词行无跳转动作；Linux 下手机 7 场景、桌面 6 场景通过，并重跑同步歌词双布局。Windows CI 尚待验证。

QQ 歌曲搜索原仍使用 `client_search_cp`；对照 Android 未提交源码，现将生产 provider 接到 musicu 桌面搜索及版本化移动端回退，展开分组曲目并保留可播标记、分页和来源元数据。两个匿名公开接口的直接探测均返回 HTTP 200、10 条结果；协议夹具验证桌面成功与失败后回退，取消时不得启动回退。LX 用户脚本歌词同样接入同步格式和纯文本解析，并保留普通 LRC 翻译，真实 V8 测试使用本地脚本 fixture；这些不等于私人账号或第三方脚本线上验收。

网易歌词主接口在失败或仅返回占位语句时接公开歌词回退；回退独立禁用 CookieJar，保留取消请求的协程所有权，纯文本不生成时间轴。本地真实 loopback HTTP 回归覆盖主接口成功、服务错误、占位、无时间轴、Cookie 隔离及既有搜索协议；公开端点对歌曲 `2607641032` 的匿名直接探测返回 HTTP 200、code 200 和 LRC，未使用私人账号。

跨来源歌词匹配对与曲目歌手信息一致的 feat. 后缀进行识别，仍区分现场、混音等版本；按匹配评分依次尝试候选并过滤占位歌词，保留协程取消。`LyricsLoadTest` 覆盖已署名合作歌手、错误后缀、现场版本及首个候选为空时的回退，Linux 本地通过。

对齐 Android 下载歌词缓存：保存并恢复无时间轴标记，避免离线重载后生成错误的时间轴，同时兼容旧格式缓存；`DownloadedLyricsPersistenceTest` 本地通过。

Windows CI [41362422005](https://github.com/lladlam/MeloX-Windows/actions/runs/41362422005) 对 `6954f3b` 的 AutoSync 双布局和本地 Lyricify 双布局回放通过；后续桌面歌词面板几何验证失败，因此没有发行 ZIP。现场 `08-lyrics-again` 中面板已经打开但歌词仍为 `loading`，首次打开、歌词定位、队列切换和播放断言均通过。另一次 CI `41363239281` 对 `957b4e9` 同样在歌词面板中歌词行存在性校验失败。回放新增重新打开时等待真实歌词行的断言，保留原截图和几何检查；Linux 虚拟桌面深浅两主题各 10 场景／20 断言及原有面板校验通过。日志及现场见 `evidence/ci-41362422005-reference-lyrics/`，修正后的 Windows CI 结果需另行确认。

Windows CI [41363940211](https://github.com/lladlam/MeloX-Windows/actions/runs/41363940211) 对 `8fabb2926f176fe6a51b1e57bd94aa0cd91fc1fd` 全部通过，包含 AutoSync 与本地歌词双布局回放。发行 ZIP 已完整下载并独立核验版本戳、ZIP/JAR CRC、资源和原生库：396,426,894 字节，SHA-256 `9570282d9a17f8d82d0c5d5c47240492f9f3fe3c255b6f022e3cf062f1dc437b`，详细结果见 `evidence/ci-41363940211/verification.json`。该包不包含此提交之后的修改。

后续 Windows CI [41364285351](https://github.com/lladlam/MeloX-Windows/actions/runs/41364285351) 对 `8a9677b12cf21b2df2f2f3b2fb9d951bdd3885ad` 全部通过。其 ZIP 已完整下载并独立核验版本戳、ZIP/JAR CRC、资源及原生库：396,429,295 字节，SHA-256 `fecd7bcd4fbbb9a84212064d399b53b61f2b72a9ab433144b0f94a6b08be6bf6`；详见 `evidence/ci-41364285351/verification.json`。该包包含网易公开歌词回退，不包含后续跨来源匹配及下载歌词缓存修复。

歌词分享图片按实际字体宽度换行并根据全部选中内容计算高度，替代原先每行截取 36 字符、4096 像素静默截断；图片超出 8192 像素时明确提示减少选择。`LyricShareLayoutTest` 在 Linux 覆盖长行字符保留、60 行实际 PNG 末行绘制及超限报错；原生保存对话框与 Windows 图片结果仍需实机验证。

歌词分享图片的封面加载复用 Jellyfin 图片请求范围与会话鉴权：匹配当前服务器图片路径时带令牌，其他路径不附加令牌，受保护请求不自动跟随重定向。本地 loopback HTTP 测试验证上述行为，排版及实际 PNG 回归通过；真实私人服务器与原生保存对话框尚需实机验证。

Windows CI [41370767122](https://github.com/lladlam/MeloX-Windows/actions/runs/41370767122) 对 `48cb3bf50e5609d2faff60c3d53281865c390da2` 全部通过，包含歌词面板重开等待修正和本地歌词双布局回放。该次 ZIP 已完整下载并独立核验版本戳、ZIP/JAR CRC、资源及原生库：396,434,097 字节，SHA-256 `703da8e67cfd26cb7e00bb56b547b785153a4ee78c97ba07477a80ff2d778e5a`，详见 `evidence/ci-41370767122/verification.json`。该包早于歌词分享图片排版修正。

Windows CI [41379428240](https://github.com/lladlam/MeloX-Windows/actions/runs/41379428240) 对 `0bf689f23af4b1b31b75a8710e973858c3f63d0f` 首次因真实网易艺人详情请求超时失败；保留真实网络和原断言重跑后全部通过。ZIP 已完整下载并独立核验版本戳、ZIP/JAR CRC、资源及原生库：396,440,188 字节，SHA-256 `79bd5bf9979a5c856299d281057d8992d67c5166a2a56f217678a1136a8ff7e2`，详见 `evidence/ci-41379428240/verification.json`。该包包含歌词分享图片排版，不包含后续 Jellyfin 分享封面修正。

Windows CI [41389339721](https://github.com/lladlam/MeloX-Windows/actions/runs/41389339721) 对 `9984c06d8fda3e1dd88136f79060fdff2df2c6d3` 全部通过。ZIP 已完整下载并独立核验版本戳、ZIP/JAR CRC、资源及原生库：396,440,113 字节，SHA-256 `82b26454aa750beffabe14631db1af08db8ed51feca532de8ec81eb62df19936`，详见 `evidence/ci-41389339721/verification.json`。真实私人 Jellyfin 服务器和原生保存对话框仍需实机验证。

## Jellyfin 歌单分页

两套歌单详情使用的完整分页加载器现在保留 Jellyfin 同一曲目的多个独立成员条目，不再按曲目 ID 合并；未返回总数的服务端按实际页面长度继续读取。loopback HTTP 回归覆盖跨页重复曲目、连续页请求、令牌及按第二个成员 ID 删除；Jellyfin 协议、共享分页与汽水定向测试通过。真实私人服务器及两套 UI 的成员操作仍需实机验证。

Spotify 收藏专辑此前固定只请求第一页并关闭继续加载；现按调用方页码与大小请求真实偏移量，音乐库既有“加载更多”入口可读取后续专辑。loopback HTTP 回归覆盖两页数据、认证头、偏移量及结束状态；真实 Spotify 账号和双布局交互仍待验收，关注艺人的游标分页仍未完成。
