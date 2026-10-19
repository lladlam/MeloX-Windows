# 正式 v0.6.5：分屏／独立 Chrome 与背景流光

真源为 `53244b9d6b21d7e3a609e8cb11876efef70f218b`，纯树文件和 SHA-256 在
`evidence/split-layout/source-intake.json`。整体迁移仍为 **PARTIAL**。

## 分屏与独立位置

- `general_split_screen` 默认开启；主界面采用宽大于高的物理窗口判据，左右按整数像素等分。窄屏、竖屏、正方形和关闭开关时回到单栏。
- 新增独立持久化 `general_split_navigation_position` 与 `general_split_mini_player_position`，两者均默认 `BottomLeft`，支持 `BottomLeft / BottomRight / Left / Right / TopLeft / TopRight`。Left/Right 是竖直侧栏，角落保持横向。
- `MeloXPositionedChrome` 先按页面分隔线划分区域，再处理 `safeDrawing` 实测安全区与8dp间距；侧栏72dp，占外缘，同侧其他控件向内避让。两个侧栏同位置时使用真源的1dp间隔。顶部与侧边分别为所属页面每个控件预留80dp。
- 位置相同才联动自动收缩；不同位置的两个控件保持展开。单栏保持旧版内联布局。侧栏迷你播放器继续调用真实播放控制器，并保留封面展开共享元素。
- 设置子页使用可移动composition迁到右栏；关闭／重开分屏时保留该子页的状态。滚轮回放按目标页面的真实横坐标命中，避免在分隔线上滚动另一栏。
- Library歌单列表／详情左右分屏和退场内容保留已接线，账号实机回放仍待完成；单栏继续使用原有共享封面过渡。Local仍只有Songs入口；本地生成WAV的回放不能替代已登录provider的Library歌单验收。首页／搜索／账户等其余详情入口及新的共享过渡尚未完成验收。
- 播放器 `840×600dp` 双栏判据单独建模，不能用主界面的横屏判据替代。完整iOS播放器双栏和所有子路由仍需后续迁移／回放。
- 10条设置文案及右栏空态launcher PNG均从固定Git树导出。遍历所有 `values*/strings.xml`，确认这些键没有本地化覆盖，按Android默认回退。复核：`python3 tools/export_v065_split_layout.py ../MeloX-Android --verify`。新发行精确资源清单为19项，历史发行按自身提交的清单核验。

## 流光修复

- 原桌面实现只在3×3固定单元附近小幅移动，且以音量／节拍乘亮度；正式0.6.5已采用全场颜色源游走、两层流场扭曲和细化纹理，亮度只受明确的用户亮度设置控制。
- 迁移固定源的颜色源频率、warp、权重、8π相位周期及280ms调色板过渡。全部时间频率为四分之一整数倍，周期回卷连续。
- 没有真实分析时只恒速漂移；新增 `MeloXAudioReactiveSample.hasAnalysis` 仅由真实分析分支设置，不把播放时钟或单独PCM能量当作已有节拍分析。已有真实PCM消费者保留。
- CPU工作线程仅生成像素数组；原生位图安装和释放在composition线程执行，避免退出页面时后台写入已关闭位图。复用字节缓冲区，双缓冲索引跨effect重启保留，静态刷新后下一帧仍发布另一缓冲区。
- `:desktop:auditFlowingLight` 绘制生产Canvas，使用明确生成的彩色封面作为渲染输入，检查三档质量的连续帧变化、暂停／恢复、减少动态、相位回卷、均色场无节拍亮度闪动和反复卸载。它没有替换生产音源或播放状态，也不代表用户Windows长时内存增长已修复。

## 回放与发行

- `tools/capture_split_layout.py`：生产设置鼠标／滚轮操作、独立Dock与迷你播放器位置、真实生成WAV经生产FFmpeg解码、暂停／恢复；独立JVM重载位置，并在竖屏验证偏好仍保留但布局回到单栏。
- CI新增该三组回放和流光光栅审计；补上此前漏传的歌词语言controls/reload目录。原 `13d275c` CI只能独立核验已下载的20组，不回填缺失的两组原始结果。
- 固定Android 0.6.5同内容截图／动态帧仍缺失；本批光栅变化仅用于判断桌面生产渲染是否更新，不作为Android像素MATCH。

Windows `39270464456` 的270项测试和三档流光审计通过，但旧Dock回放的固定248px拖动未适配分屏，剩余流程跳过、未生成发行ZIP。修复 `7fca71b` 改为读取实际根标签中心后发送物理拖动；深浅两主题14 scenes/18 assertions与JDK17完整设置路由82/65通过，新的Windows发行核验仍待完成。

随后Windows `39272967752` 已通过Dock、新歌词语言和三个分屏回放，原始完整回放重计11组/312 scenes/295 assertions；zh-TW 800×600一般页的触感反馈行移出视口，旧聚合断言失败。`3a79b5c` 改为逐项真实滚轮滚入后检查；JDK17四Locale路由和controls/reload共6组/434 scenes/333 assertions通过。本次失败仍未生成发行ZIP，后续Windows全流程待验。

最终Windows **39285046285 / head `14b4df0` / SUCCESS**，两个artifact完整下载并独立核验：270 tests/55 suites零失败，**25组/889 scenes/1,425 assertions**全部通过；三个分屏回放70/95，各组生产PCM写入超过44100帧。三档流光运动/暂停恢复/减少动态/周期回卷与36次卸载重建均通过，流光连续帧及原始回放证据在 `evidence/windows-ci-14b4df0/`。

发行ZIP **333,279,974 bytes**，SHA-256 **`6780a498467bced4c9a964dbfa2d22801c54c20a08a9378e1b8eb822dba3c0a0`**；357条目/JAR全部CRC、精确版本、19项资源和Windows PE解码器核验通过。以上为Windows本批实现及交付验收，整体0.6.5迁移仍为PARTIAL，Android同内容像素/动态和用户长时内存增长仍待闭环。
