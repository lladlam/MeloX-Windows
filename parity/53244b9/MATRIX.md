# Android 0.6.5 → Windows 活跃迁移矩阵

## 固定真源

- 正式发行标签 **`v0.6.5`**，精确提交 **`53244b9d6b21d7e3a609e8cb11876efef70f218b`**，Android versionCode **24**。
- GitHub 正式 release 发布于 2026-10-17T16:49:32Z；源码发布说明日期为 UTC+8 的 2026-10-18。证据 `evidence/baseline/official-release.json`。
- 旧标签 `0.6.5` 指向 `deb7308`、versionCode 23；用户此次指定 0.6.5 后，以正式 release 的 **v0.6.5 / 24** 为准。`origin/main` 的后续 README 提交不混入此基线。
- 相对旧基线 `3f877ea`：**38 commits / 389 changed files**。原始清单与纯 Git 导出 SHA-256 见 `evidence/baseline/intake.json`。Android 本地工作树中的改动不作为真源。
- Windows 起点 `d4420b1`；旧版记录继续保留在 `parity/3f877ea/`。旧测试和截图不自动升级为 0.6.5 验收。

## 迁移状态

整体 **PARTIAL**。当前包内 legacy 字符串/字体/法律资源仍有 `3f877ea` 来源；新功能逐项接入正式 0.6.5 真源。远程策略实际实现兼容号仍为 **20**，签名热更新新增 schema 与消费者完成后再变更；目标 Android versionCode 24 与已实现策略兼容号含义不同。

| 0.6.5 行为 | Windows 本轮状态 | 验证与边界 |
|---|---|---|
| 智能队列分析额度：默认60、负数不限、正数10–500；当前+后续N首，排除历史；按generation调度 | 已接入真实设置、解析和PCM分析链；两个worker限制解析/解码并发和协程分配 | Linux/Windows真实15首生成WAV/FFmpeg回归通过：从第3首开始、额度10，仅11首解析/分析；切到额度尾部不重新启动整批额度；0cee9a3发行独立核验通过 |
| 完整可见队列与原位DJ排序 | 保留历史/完整列表，按已消费链尾重排；额度尾部维持原序，禁用不重复添加；过渡中不改目标，预备目标变化取消旧mix | 真实90/140/92 BPM样本重排90→92→140通过；generation/fallback/历史与额度尾部回归通过；物理听感待验收 |
| 额度设置入口和持久化 | 真源同款5选项（不限、20/40/60/100），Smart模式显示、Fixed模式隐藏 | Linux/Windows均通过物理输入26 scenes/13 assertions、独立JVM重载5/4；该项标题/选项在源中为硬编码中文，按原文保留 |
| 分屏默认开关、list-detail、独立Dock/迷你播放器六种位置与安全区 | 首批已接入：默认开启、设置子页右栏、三种单栏回退、两个独立六位置/真实播放控制/安全区与联动规则；Library分屏接线和其余详情入口待实机补验 | 8684c2e本地270 tests/55 suites通过；开关/六位置/独立重载/竖屏70 scenes/95 assertions通过，真实PCM写入非空；播放器完整双栏及Android同内容验收仍待完成，见SPLIT_LAYOUT_AND_FLOW.md |
| 音乐库歌单打开/关闭共享封面过渡 | 分屏详情退场内容保留已接线，完整共享过渡待对照迁移/回放 | Local只有Songs入口，不能用本地生成歌单替代已登录provider Library回放；原共享封面过渡不自动升级为0.6.5验收 |
| Provider能力分发、集合分页、可写歌单权限/成员排序 | 旧功能保留，0.6.5增量待审计/迁移 | 同源原始ID及真实API约束继续生效 |
| YouTube地区/登录上下文/认证头、频道选择/刷新、账号失败不误判退出 | 0.6.5增量待迁移 | 真实账号验收待实机 |
| 统一歌词链、翻译语言立即刷新与异步覆盖保护 | 语言选择、TTML轨道、语言快照缓存/绑定/升级和播放器新请求状态已接入；完整统一链其余增量待审计 | 本地/Windows265 tests/54 suites通过；本地设置与换Locale独立JVM重载33 scenes/35 assertions通过；13d275c发行独立下载/17项资源通过，Windows新语言两组回放漏传原始输出，不能计入已独立核验的20组，见LYRIC_LANGUAGE.md |
| AutoSync ONNX/中文CIF/日文G2P与模型包 | 待迁移真实Windows推理与模型所有权 | 不用均匀字时或伪推理代替；须核对正式release模型索引 |
| 签名文案/主题/规则、受限QuickJS脚本 | 待迁移 | 现有策略读取不代表新schema和消费者已实现 |
| 液态玻璃全部组件、底栏搜索返回与命中层 | 旧版修复保留；需重新对照0.6.5后迁移剩余差异 | 历史发现记录保留，不能直接套用0.6.2同内容验收 |
| 播放器背景流光 | 已迁移正式0.6.5颜色源游走/流场扭曲、无分析恒速/真实分析驱动与稳定亮度；位图写入/释放归属修复 | Linux/Windows三档生产Canvas审计均通过；Windows8684c2e因后续Dock回放失败未出ZIP，7fca71b已修复回放坐标；不是Android像素MATCH或用户Windows长时内存修复证明 |
| 完整本地化资源与应用语言 | 待纯树逐项导出并接消费者 | 现有四Locale设置验收属于旧资源基线 |
| Android APK增量更新/系统平台功能 | 需Windows平台等价实现 | APK patch不适用于Windows发行 |
| 用户Windows549→1431MB内存、GPU/DPI/长时播放、账号/音频实机验收 | 未闭环 | 分析额度仅证明后台工作受限，不是RSS问题已修复 |
| 正式0.6.5同内容Android像素/动画参考 | 待采集 | 设备最近仍离线；旧`.parity3f877ea`包不是0.6.5参考 |

## 实现与发行记录

本轮本地结果归档及Windows发行核验另记于 `COMPLETION_LOG.md`。整体完成必须逐项核销；编译、单元测试或桌面自比对不能替代真实业务与Android同内容验收。

- 首批实现 **`0cee9a32de9751e582807c88984b44d800d188ba`**：本地全量 **257 tests / 52 suites / 0 failures/errors/skipped**，桌面编译和额度controls/reload通过。
- Windows **[39238611894](https://github.com/lladlam/MeloX-Windows/actions/runs/39238611894)** 已成功，两个artifact完整下载核验通过：257 tests/52 suites，20组回放/762 scenes/1,295 assertions全部通过；发行ZIP/JAR CRC、精确版本、16项legacy资源、Windows FFmpeg和0.6.5目标元数据核验通过。
- 最近独立核验发行现为 **`0cee9a3`**：ZIP **333,098,400 bytes**，SHA-256 **`33e754b423359d1970a6d09a44db923c389e83ab5f2f0b1cc3e43581a2d011b1`**；证据 `evidence/windows-ci-0cee9a3/`。它包含额度迁移，尚不包含下一批歌词语言实现。
- 歌词语言实现 **`13d275cc4bfa600fe3c7f7b6a963fc2a1c6b21bc`** 已推送；本地265 tests/54 suites及33 scenes/35 assertions通过。Windows **[39243293868](https://github.com/lladlam/MeloX-Windows/actions/runs/39243293868)** 运行中，成功后自动完整下载并独立核验；不预先标记发行通过。
