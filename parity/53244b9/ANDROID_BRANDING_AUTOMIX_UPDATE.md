# Android 图标、AutoMix 双封面与设置联动

## 图标

直接读取 Android 工作区 `android/app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`，原图 192×192，SHA-256 `5fe37d11ae8921300731a2b3eb325957b8ee1a8fe0d1ffe67c96f1697535bf7e`。PNG 原字节用于窗口、系统托盘、关于／引导及分栏空状态；由同一原图制作包含 16、24、32、48、64、128、256 像素的 Windows ICO，用于 Compose Windows 安装包配置及 CI 直接 jpackage 的启动器。源码哈希见 `evidence/android-branding/source.json`。

`verify_pinned_resources.py` 在源码、JAR、app-image、ZIP 验证这两项资源。新增 `verify_windows_icon.py` 解析 Windows PE 资源目录，逐尺寸比较启动器实际嵌入的 ICO 图像，而非只确认 JAR 含图片；独立发行验证也使用交付提交的图标文件。该解析器已用 MinGW 真实资源编译生成的 PE 完整通过，并能识别旧 `40325542270` 发行启动器不包含新版图标；新 jpackage PE 的 CI 验收尚待进行。

## AutoMix 背景

双布局共用的 Now Playing 将真实双 PCM 交接状态中的 outgoing／incoming 封面传入流光背景，仅在 `active && !handedOff` 期间合并。按 Android 当前逻辑对 3×3 色格及平均色做 50／50 混合；一张封面缺失／解码失败时保留另一张，均缺失则回退。加载 effect 以封面身份和 Jellyfin 会话版本为 key，**不以逐帧进度为 key**，提取两个封面并发进行。handoff 后恢复当前曲目封面。

本地 `:shared:desktopTest :desktop:compileKotlinDesktop :desktop:auditFlowingLight`：70 suites／367 tests 通过；真实 Compose 光栅审计在 Low／Balanced／High 三档使用两个生成的封面，验证混合、结束后单封面恢复、暂停／恢复、减少动态效果与资源卸载。

两种布局另用 `capture_automix_background.py` 播放独立生成的本地 WAV 和同名封面，鼠标展开真实播放器；PCM 频谱独立读回确认同一软件输出端依次存在 1 kHz、双频重叠、4 kHz。冻结动态效果后，实际截图的背景像素在双 PCM 交接时落于两张封面之间，交接后恢复新封面。双布局共 20 scenes／34 assertions，频谱、截图和生成 PCM 证据在 `evidence/android-branding/automix-background-*`。这不证明物理听音或私人线上音源。

## 设置

旧 `player_flowing_backdrop` 开关此前只改偏好、不改变播放背景。现在开关即时切换 FlowingLight／BlurredArtwork，背景选择器也反向更新开关，两项偏好原子持久化；选择自定义／歌词背景时不会显示为流光开启。开发者设置中的性能浮层接通两套布局，开启后显示 Compose 帧钟间隔统计和 JVM 堆用量；关闭后不继续采样。帧钟 FPS 不冒充 GPU 帧率或 Windows 进程总内存。

新增鼠标驱动 `tools/capture_background_controls.py` 在两种布局检查开关、背景选择、浮层显示以及独立 JVM 重载，共 46 scenes／47 assertions 本地通过；两个回放均接入 Windows CI，新发行仍待 CI 与独立下载核验。
