# 本地 MeloX-Android 工作区互联更新迁移

## 真源

本批直接读取用户指定的 `/mnt/ntfs_files_sda4/项目/MeloX-Android/`，包括未提交的互联改动；不是旧 `youshen2/MeloX`，也不是远端仓库快照。读取时 HEAD 为 `be7c33d15c3944bcdab6b70d8c80ffddc9c9895f`，相关 14 个文件 SHA-256 在 `evidence/lan-update/android-source-reference.json`。Android 工作区保持原样。

参考新增 `MeloXLanSession.kt`、`MeloXLanTogetherSync.kt`、`MeloXLanOutputPanel.kt`、`MeloXAudioOutputPlaybackPanel.kt` 及现有 Runtime/Client/Server/Cast 的工作区修改。常规界面迁移仍以固定 0.6.5 为准，本批只更新互联。

## 实现

- 进程级 `MeloXLanSession`：StateFlow 暴露当前设备、Local/Cast/Together 输出模式、忙状态和真实远端状态；Mutex 串行连接/切换输出操作。面板关闭及切歌不会释放控制连接，连接断开清理输出、恢复本机音频。
- 手机上的播放器“音频输出”现在打开艺术封面引导的互联面板，提供实际播放控制、进度/音量、本机输出、发现设备、PIN 配对、接力/投放/同步听歌、提供音频、主动退出和互联设置。布局采用 Android 的 360dp 面板、38dp 外圆角、96dp 封面与 22dp 设备卡片。
- 电脑底栏保留独立互联管理弹窗，接入同一进程会话。修正关闭后重开丢失连接状态、接力按钮未传当前歌曲/完整队列、PIN 重新生成不刷新显示；补实际手动地址/端口连接及从最近设备显式添加常用设备。播放设置增加入口。
- 投放只静音本机 Java Sound 写入，按真实 accepted 帧范围发送原始 PCM；不修改系统音量或用户播放器音量。停止投放/控制连接断开恢复本机音频。当前 Windows 解码统一输出 44.1kHz 立体声，无需 Android 的 Media3 动态格式重建。
- 同步听歌已继续对照迁移期间变化的 Android `docs/MELOX-LAN.md` revision 2：多设备控制连接、逐设备 relay、每轮 3 次最低 RTT ping、READY 后才确认 prepare、3 秒 deadline、200ms commit lead、带 session/generation 的状态、迟到设备不追赶、自然结束最多等待 5 秒和每代一次推进。实现与验证持续跟进本地工作区；后续源码变化检查见 `android-live-reference.json`。
- 漂移使用 ready + 3 秒 seek 稳定期 + 250ms seek 阈值、40ms deadband 和 0.97–1.03 实际 PCM 重采样速率校正；暂停/退出/新代恢复 1.0。Windows 的有界线性 PCM 重采样会同时轻微改变音高，和 Android Media3 的时间伸缩实现不同。媒体进度按速率累计媒体帧，输出 drain 仍使用真实输出帧。
- 主动退出发送 `together.stopped` 并保留控制连接，过时代不能 commit、换队列或 seek。v1 对端走显式 legacy 路径并记录日志。只有支持完整 READY 回应的生产运行时声明 `together_barrier_v2`，一般测试 Host 默认只声明 v1。

## 迁移期间新增的 DLNA（验收中）

Android 后续新增了独立 `dlna` 包，现已纳入真源检查和 Windows 迁移范围。两套输出 UI 接入真实 SSDP 发现、UPnP 描述解析、SOAP 播放/暂停/seek/音量和断开；播放状态与歌词/进度使用远端状态，保留本地解码器状态用于返回本机。

首次投放需 `GetTransportInfo` 确认 PLAYING 后才暂停本机。无凭据 HTTP 源先直连，失败回退代理；本地文件和带头部的真实源使用 IP 绑定、随机资源地址的会话代理，支持 HEAD、Range 与 DLNA Streaming headers。代理选择到渲染器的实际路由网卡，保持到退出会话，无首次十秒访问限制。未确认播放、SOAP fault、上游音频失败进入真实错误状态。

`DlnaIntegrationTest` 使用回环 SOAP 渲染器，从真实音频代理 Range 读取实际生成 WAV 的字节，检查元数据转义、确认后本机暂停、控制与音量路由、退出本机恢复，以及渲染器未确认播放时不暂停本机。这些用例的运行结果单独记录；Android 的 `DlnaDeviceRunner` 为真源设备测试入口，读取该文件不代表已经运行 Android 或真实音响。

### 并行新增的小米妙播平台依赖

监听随后捕获 `MiPlaySession.kt`、`MiPlayTransport.java`、`MiPlayProbeRunner.java` 和 `MiPlayWireRegression.java`。Android 实现通过 `com.milink.service` 的 `MiPlayAudioService` 和 `com.xiaomi.miplay.audioclient.IMiPlayClient` Binder 接口调用系统音频捕获/传输；后续修正把 Binder 负返回码转为真实错误、面板把 LAN/DLNA/妙播忙状态统一互斥。Windows 没有对应系统服务，无可迁移的 MiPlay 传输实现，不声明该能力；跨输出忙状态互斥已在 Windows 两个输出 UI 中同样实现。相关文件已纳入真源哈希跟进与本地快照，便于逐版本 diff。

后续又新增 `usb` 包、`usb_audio.cpp` 和 `UsbAudioOutputRows.kt`，属于 Android UsbManager／Linux usbfs 等时 URB 的实验性全速 UAC1 独占输出实现。其新增输入编码/声道记录用于 USB PCM16 格式准入，未改变 LAN PCM 协议；Windows 当前统一解码为 44.1kHz 双声道 PCM16。相关平台实现与测试已纳入工作区监测；当前批次未实现 Windows USB 独占后端，不能把本机 Java Sound 输出称为 UAC1 独占验收。

## 验证口径

`MeloXLanSessionTest` 使用真实 loopback TCP 控制/PCM socket，音频端点为受控 Java Sound 测试端点；检查 PIN、连接复用、完整队列接力、模式切换、断开清理、本机静音/原始 PCM tap 以及同步校正策略。

`tools/capture_lan_update.py` 使用实际生成 Local WAV、生产 UI 鼠标/键盘输入、生产解码器及显式 loopback 协议对端。对端记录请求与实际长度前缀 TCP PCM，不能作为 Android 实机或物理扬声器通过证据。脚本检查关闭重开、切歌保留投放、本机静音与恢复，以及完整队列接力和同步退出。

revision 2 另由 `MeloXLanBarrierTest` 检查真实 FFmpeg READY、commit 前无 PCM 输出、定时 commit、拒绝过时代、迟到者不追赶、多控制连接、新队列保持同数值 index 仍开启新代、5 秒结束等待上限及用户切歌取消等待。新增状态机的最终运行结果必须单独记录，不能沿用旧 16 项通过记录。

## 跟进并行 Android 开发

用户明确要求迁移期间持续检查本地 Android 的修改。`tools/check_android_lan_reference.py` 在实现/验证/提交前记录 live 工作区文件 SHA-256 与差异；`android-source-reference.json` 和 `android-source-rounds-reference.json` 是先后读取记录，不作为停止跟进的固定基线。Android 源码目录不写入、回滚或提交。

迁移中发现的 Android 后续修正已对照：自然结束加入 `completedGeneration` 和 LAZY job 避免同代重复推进；只有活动参与者才运行更新；seek 标记与实际新位置更新一起发送。旧的首轮 400ms 校正阈值已按新协议更改为 250ms。

初轮 LAN 7 项 + AutoMix 9 项已通过；`/tmp` 配额导致过一次运行失败，切换项目临时目录后重跑记录已保留。随后新增同步屏障及投放归属测试，DLNA 接入前 **21 项全部通过**，XML 在 `evidence/lan-update/rounds-before-dlna/`。DLNA 接入后 **24 项全部通过**（LAN 12＋DLNA 3＋AutoMix 9），XML 与日志在 `evidence/lan-update/dlna-unit-pass/`。

最新 **26 项全部通过**（LAN 13＋DLNA 4＋AutoMix 9），零失败／错误／跳过，桌面编译通过。覆盖真实 PCM 速率校正、队列切换保持渲染器、过时代 commit 隔离和保留独立控制连接，XML 与完整日志在 `evidence/lan-update/dlna-final-pass/`。结束等待用例已明确设置落后／追平两种前提，并使用不同合法 index 执行用户跳曲；首次 FFmpeg READY 的基础设施等待单独放宽为 120 秒，生产协议准备与结束期限保持原值。此前 26 项尝试中曾在首次 READY 等待和结束等待前提处失败，修正测试后重新执行通过。

生产输入回放 v4（对端声明 `together_barrier_v2`，真实 `together.prepare`/`together.commit`/`ping` 校时、常用信任切换）：手机 **63 scenes / 30 assertions**、电脑 **37 scenes / 12 assertions** 通过，证据在 `evidence/lan-update/ui-replay-v4/`。对端为显式 loopback 协议 fixture，不代表 Android 实机或物理扬声器。

DLNA 双布局生产回放同样通过：手机 **24 scenes / 15 assertions**、电脑 **18 scenes / 13 assertions**。真实鼠标选择经 SSDP 发现的显式回环渲染器、关闭重开输出面板、暂停/恢复、手机远端音量与返回本机；每套回放的渲染器均通过真实 HTTP Range 实际读取两次生成 WAV 音频。截图、只读 semantics、SOAP 记录、读取的音频字节、压缩本机 PCM 与 SHA-256 在 `evidence/lan-update/dlna-ui-v1/`。该渲染器是协议 fixture，不是用户物理音响。

最终收尾的 **shared 全量回归 64 suites / 312 tests，零失败／错误／跳过**，并完成桌面编译。额外验证所有参与者拒绝 prepare 后按原播放意图恢复本机、保留控制连接；对应方法 `aRejectedPrepareRestoresLocalPlaybackAndRetainsItsControlConnection` 已在全量 XML 中确认执行。完整 XML、日志及汇总在 `evidence/lan-update/full-shared-final/`。上述四次 LAN/DLNA UI 回放合计 **142 scenes / 70 assertions**。

### Windows CI 原生路径修正

上述 312 项全量通过发生于本地 Linux。Windows CI `40111100874`（`b08ce04`）实际为 **312 tests / 1 failure**：DLNA Range 取到 WAV 字节，但 MIME 断言期望 `audio/wav`、实际 `audio/mpeg`，未生成发行 ZIP。原始失败 XML 与摘要在 `evidence/lan-update/windows-ci-40111100874-failure/`。根因是把 `D:\...\generated.wav` 原生路径传入 `URI` 解析，异常后丢弃了扩展名；现保留原始路径回退，并补充 Windows 盘符／UNC、空格／中文、file URI、带查询参数 HTTP URI 回归。新 Windows 运行与发行独立核验结果另行记录。

修正后本地 **5 项 DLNA 测试全部通过，桌面编译通过**，包含原始真实 SOAP/Range/WAV 严格 MIME 断言和新增跨平台路径用例；XML 与摘要在 `evidence/lan-update/windows-path-fix/`。

后续监听捕获 `MeloXPlaybackService.kt` 与 `docs/MELOX-LAN.md`：逐快照比对确认前者调整第三方音源选项与 Android 通知栏播放模式，后者仅更新示例 appVersion 为 `0.7.0`。本次增量没有修改 LAN/DLNA 消息、屏障时序或音频格式；最新工作区哈希已更新至 `android-live-reference.json`。

修复提交 `15e2a75` 的最终本地 LAN v5 回放通过：手机 **63 scenes / 30 assertions**、电脑 **37 scenes / 12 assertions**，截图、协议与压缩 PCM 归档至 `evidence/lan-update/ui-replay-v5/`。

Windows CI **`40112156169` 成功**：64 suites / **313 tests，零失败／错误／跳过**，并通过 Windows 上的 LAN 手机 63/30、电脑 37/12，DLNA 手机 24/15、电脑 18/13 生产输入回放。对应 XML、协议/semantics 与汇总在 `evidence/lan-update/windows-ci-40112156169/`。这是 Windows runner 上的回环协议验证，跨设备与物理扬声器边界仍按前述记录。

该运行的 Windows 发行 ZIP 已完整独立下载核验通过：**338909804 bytes**，SHA-256 **`b2831627110bf01ebf6d2cd85bcb8682a29a505489f581a611a123a448a3577d`**，357 个 ZIP 条目；全部 ZIP/JAR CRC、build-info 提交 `15e2a75753530f6cb94b24acf3c84a88fc03deb6`、固定资源及 Windows PE 解码器字节检查通过。独立核验 JSON 在 `evidence/windows-download-40112156169/`，完整下载保存在 `desktop/build/verification/delivery-40112156169/artifact/MeloX-Desktop-Windows.zip`。本机未执行该 Windows 包的物理音频验收。

尚未闭环：真实跨机 Windows↔Android、多网卡/防火墙、物理音响和长时同步漂移。不要把 loopback fixture 写成手机互通已验收。
