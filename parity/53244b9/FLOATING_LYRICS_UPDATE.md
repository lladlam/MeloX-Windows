# 悬浮歌词窗口

对照 Android `MeloXFloatingLyricsService`，增加独立置顶悬浮窗口：复用实际播放曲目、歌词加载与 AutoSync 状态，以及本地／DLNA 输出时钟；支持译文、罗马音、下一行、自动选择及隐藏，字号和对比度即时响应设置。关闭按钮会保存关闭偏好；拖动区域使用原生窗口拖动。

本地桌面测试覆盖行时间和副行选择。隔离 Linux Xvfb 会话中启动实际应用，以鼠标将 500×116 窗口从 (48,48) 拖到 (238,163)，再点击关闭，窗口消失且独立偏好写入 `floating_lyrics_enabled=false`。随后关闭 Xvfb 时应用尚未完全退出，JVM 在 libX11 中崩溃，不能将该回放视为正常退出验证；保留该次生成的崩溃记录。Linux 虚拟桌面结果不能代替 Windows 原生置顶、拖动、关闭或托盘恢复实机验收。

手机布局和桌面布局设置导航回放分别通过 89 和 97 个步骤，均通过真实坐标点击打开、关闭悬浮歌词开关，并断言选中状态与运行时状态。手机布局首次回放因隔离用户目录未预置完成引导而停在法律页面；预置引导完成状态后重跑通过。回放日志位于 `desktop/build/parity/floating-routes-mobile-retry/gradle.log` 和 `desktop/build/parity/floating-routes-desktop/gradle.log`。
