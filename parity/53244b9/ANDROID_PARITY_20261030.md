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

对照 Android 未提交工作区的 `LyricFileParser`、`LyricifyLyricsParser` 与本地扫描增量，Windows 本地 provider 新增同目录同名 LRC／KRC／QRC／TTML／Lyricify Lines 和 Syllable 的实际读取；按逐词信息优先选择，过滤常见占位语句，文件与 KRC 解压内容均有大小限制。读取发生在歌词加载时，不覆盖原有识别缓存；本地歌词禁用跨次加载的内存缓存，使新建、修改和移除 sidecar 可在下次加载时反映。安卓新增的纯文本无时间歌词与其专用显示尚未迁移。

本地 `LocalLyricsSidecarTest`、`DesktopLocalLyricsPersistenceTest`、`LyricsLoadTest` 通过，覆盖真实文件、编码路径、Lyricify 对唱／和声、压缩 KRC、同名候选与修改回退；`tools/capture_local_sidecar.py` 在 Linux 虚拟桌面使用真实鼠标打开两种布局的播放器，生成 WAV 进入软件 PCM 并显示逐词 sidecar，手机 5 场景、桌面 4 场景通过。Windows CI 与实际硬件听音仍待验证。
