# Desktop 迁移记录

## 1. 保留当前 UI，加入桌面切换及首批源布局（1ccc123）

- 实现提交：`1ccc123830e7662e0a2e6989de237a2ed3c64dcb`，已正常推送 `main`。
- 重新 clone 的桌面真源固定 `a41ea0e1e1fa69fb543d827b853fe3aff5420951`。`source-audit.json` 记录本批采用文件与导出文案的纯 Git blob 哈希；`surface-inventory.json` 枚举 App／Features 下全部 **124 个 Swift 文件、16 个分组**，逐文件记录哈希和未完成状态，防止只迁移框架后遗漏其余 UI。
- 设置 → 通用增加“电脑端布局 / Desktop layout”。`general_desktop_layout=false` 为默认值；切换立即应用并写磁盘，独立 JVM 验证开启／关闭均能恢复。主内容和设置详情保留 composition，旧左右等分／底栏不会与桌面框架重复叠加。
- 接入固定侧栏、真实页面导航、700×54 底部播放器、258 宽歌词／队列面板和源尺寸的并列播放器；首页首批卡片使用真实首页 API，快捷动作接真实推荐／歌单／播放链。心动模式直接启动播放，其余推荐入口进入歌曲／歌单浏览；详情样式仍需迁移。
- 歌词复用生产加载和字级 Canvas，增加仅 presentation 层的桌面前景色、字号断点、段距、边距和 focus 输入；浅色 inspector 不再沿用白色文字。现有布局的默认白色输入保持原值。
- 真源两种 locale 共 **59 个 key** 已纯导出复核；按系统 locale 选择简体中文／默认英文。Windows 集成开关与入口文案单独标注。源与打包资源核验清单扩至 **21 项**，原 Android 基线保持独立。
- 显式 JDK 17、Gradle 9.5.1 顺序验证：**274 tests / 56 suites / 0 failures/errors/skipped**，桌面编译通过。四项源几何／字号断点 golden 回归覆盖 1200×768、1239×600、1620×1080 及 inspector reservation。
- 桌面七组真实输入回放全部通过：**75 scenes / 139 assertions**。包括连续切换三次、切回两次、独立重载开／关状态、真实暂停／恢复／选曲／音量、完整播放器和浅色歌词点击跳转。连续性检查对比曲目 ID、位置和 PCM output 数量，确认切换不重开输出。
- 独立读取实际 `.s16le` 字节：大小精确对应 `written_frames × 4`、非静音，220／330Hz 生成输入的真实 FFmpeg 输出谱峰符合；每个播放回放写入超过 44100 帧。输出端点是明确标注的软件 PCM 验收端，不冒充硬件听感。
- 原布局三组回归通过：**70 scenes / 95 assertions**，覆盖分屏、六位置和竖屏。两批共 **10 组 / 145 scenes / 234 assertions**。生产字形／同 shaped paragraph raster 审计通过；该桌面自比对不替代 Android 或 macOS 的固定同内容验收。
- 已人工查看深／浅首页、队列、浅色歌词 inspector 和完整播放器帧。当前素材、字体、图标、材质、hover／弹层／页面布局仍有明确差异；全部迁移边界在 `MATRIX.md`，总体 **PARTIAL**。
- 本地原始 XML、capture／semantics、UTF-8 参数、输入、日志、独立重计和选定截图在 `evidence/local/`。真实 PCM 大文件保留在 build，并记录路径／大小／SHA-256；Windows CI 同时上传原始 PCM。
- Windows [39363076242](https://github.com/lladlam/MeloX-Windows/actions/runs/39363076242) 已开始。完整结果、发行下载与独立核验尚待完成；不能把本地结果记作 Windows 发行通过。最近完整独立核验发行仍为 `14b4df0`。

## 后续优先级

1. 按 `MATRIX.md`／124 文件清单补齐全部 Desktop 页面：搜索、发现／Radio、Library 全部子页及 collection 详情、所有设置、账号／登录、私信／Together／识曲／引导／关于。
2. 对齐首页完整分区与分页、底栏 hover／时间展开、队列 drag／菜单、播放器 accessory／收藏、独立歌词／迷你窗口，补齐可追溯的同款 SF Symbols。
3. 用固定提交、相同内容、尺寸、主题和状态取得 macOS 参考帧／动态轨迹，再逐项关闭像素与动画差异。
4. Windows 原生 DPI／窗口尺寸和真实账号／硬件回放继续验收；用户 549→1431MB 长时常驻增长保持原有未闭环状态。
