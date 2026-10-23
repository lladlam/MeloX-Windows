# 手机 Dock 选中块与 AutoMix 过渡补齐

基线：Android `53244b9d6b21d7e3a609e8cb11876efef70f218b`。

## Dock

`MeloXBottomChrome` 外层存在局部 `val maxWidth = maxWidth`，内层导航胶囊计算选中块宽度时读到了这份页面宽度；选中中心却使用胶囊实际 constraints。灰块因此过宽，边缘与标签错位。

宽度统一从胶囊 `selectionSegmentPx` 换算，保留原有玻璃、按压、弹簧和拖动。生产节点增加只读定位标签，真实指针点击、横向拖动后断言选中块中心和尺寸。

Local 两标签、网易云多标签分别覆盖 360/390/430dp，以及 1/1.25/2 倍密度。标签点击区域自身有左右各 4dp padding，几何检查在还原该内边距后比较光学选中块宽度。

## AutoMix

沿用实际 Local/provider → FFmpeg → 双解码 PCM → 输出时钟的现有播放链路。

- `transition_ui_enabled` 与 Android 一致默认关闭，设置开关即时更新 Compose runtime 并持久化。
- 过渡中的曲名、艺人、时长与进度使用真实 incoming deck；实际播放交接后恢复普通进度。
- 与 Android 一致，双路混音期间进度条不可拖动，避免显示下一首进度却 seek 到上一首。
- 封面双图动画只在封面页、动画开关已开时准入；中途去队列/歌词页再返回，不补播半途分裂动画。减少动态效果时不运行双图形变。
- 保留 28% 入场、16dp 双图间距与 620ms 交接恢复；交接时不叠加普通切歌弹跳。

## 复现与证据

`tools/capture_mobile_dock_selection.py` 使用全新隔离 home，鼠标点击及按住拖动切换标签。

`tools/capture_mobile_automix.py` 使用合法生成的 1000/4000Hz WAV、生产 Local 队列和鼠标打开播放器。四组 on/off/leave/reduced 验证开关、切页与减少动态效果；独立 `verify_captured_automix.py` 读取实际 PCM，要求同一输出中依次存在单路主曲、双频重叠和单路下一首。

`tools/capture_smartqueue_limit.py` 同时检查动画开关的即时启停与独立 JVM 持久化恢复。Windows workflow 已接入新增回放、频谱核验及证据上传。

原始本地证据见 `evidence/mobile-dock-automix/`。软件 PCM 的频谱重叠不等于物理扬声器可听验收；这些帧也不构成 Android 同内容逐像素对照完成。

已通过的生产输入回放：Dock 四组共 **28 scenes / 20 assertions**；AutoMix 四组共 **39 scenes / 73 assertions**，每组都通过独立 PCM 频谱核验。证据包含原始 capture/semantics、步骤、参数、日志、选定截图及压缩的实际输出 PCM。

补充回归全部通过：设置即时启停 **30 scenes / 24 assertions**；独立 JVM 重载 **6 scenes / 9 assertions**；原有手机播放器／歌词页恢复 **145 scenes / 28 assertions**。既有 `DesktopAutoMixIntegrationTest` **9 tests，零失败／错误／跳过**，覆盖实际双路解码、交接、取消、连续交接、下一首／上一首、软件音量和智能队列。原始 XML 与完整日志已归档。合计十一次 UI 回放 **248 scenes / 154 assertions**。
