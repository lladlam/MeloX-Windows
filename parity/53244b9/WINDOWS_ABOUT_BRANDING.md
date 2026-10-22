# 关于页品牌信息修正

用户指出 Windows “关于 MeloX”仍显示桌面参考项目原作者的图标和开发者信息。

- 原因：关于页直接读取 macOS 参考资源 `desktop-reference-*.properties` 的 `ui.desktop.about.developer/copyright`，显示“洛汐聚合体”；图片与托盘/引导也使用 `parity/desktop-logo.png`（macOS 参考 Logo）。
- 修正：新增 Windows 自有 `branding/melox-windows.png`，由本项目直接来源 MeloX-Android 固定 `53244b9` 的启动图标 `ic_launcher_foreground.png` 派生（1024×1024，Lanczos + 轻度锐化，未改图形）。关于页、首次引导和系统托盘改用该图标。
- 关于页开发者/版权改为 Windows 项目 lladlam，保留“非官方第三方网易云音乐客户端”声明；中英文分别显示。侧栏未登录占位头像由“洛”改为“M”。
- 固定的 macOS 参考资源本身未改写，仍保留字节校验；仅 Windows 展示层改用独立字符串与资源，避免破坏参考证据。

## 验证

真实桌面设置导航 + 鼠标点击“关于 MeloX”后截图：关于对话框显示红色 MeloX Windows 图标、`开发者：lladlam（MeloX Windows）`、`© 2026 lladlam。MeloX 是非官方第三方网易云音乐客户端。`；侧栏占位为 M。截图与语义在 `evidence/windows-branding-about/`。

版本号仍来自打包 `jpackage.app-version`，源码运行为 1.0.0；未改。构建/运行未涉及账号或网络数据。
