# Windows 系统托盘

- 设置 → 通用增加“关闭窗口时隐藏到系统托盘”，默认开启；键为 `windows_close_to_tray`，冷启动预加载、即时持久化，手机／电脑 UI 共用同一偏好。
- 开启且 AWT SystemTray 图标注册成功后，窗口关闭按钮（原生关闭与软件窗口 Chrome 均接入）只隐藏主窗口，保留应用 composition、队列和播放器，不 release 音频输出。
- 托盘图标左键点击或菜单“显示 MeloX”恢复窗口、解除最小化并请求前台焦点；菜单“退出 MeloX”释放播放器并退出进程。
- 开关关闭后移除托盘图标，关闭窗口恢复直接退出；若此时窗口隐藏，先恢复窗口。托盘不支持／注册失败时保持关闭直接退出，避免留下无法恢复的隐藏进程。
- 法律同意页的退出仍直接退出。隐藏窗口同步标记 remote-config foreground／display-awake foreground 为 false。
- 托盘使用原有固定 logo 资源，不加入新二进制。退出／开关关闭时移除系统托盘注册。

## 验证

`capture_settings_pages.py desktop-controls` 用实际指针连续 false→true→false，并断言运行态和磁盘；`desktop-reload` 另起 JVM 验证 false 偏好重载。Windows taskbar 的图标、点击唤回、关闭持续真实播放和退出进程仍需实际 Windows 交互验收，离屏设置回放不冒充原生托盘通过。

本地 `build/tray-v32` 已通过 19 scenes／18 assertions，`build/tray-reload-v32` 独立 JVM 通过 1 scene／8 assertions。重跑手机 routes 时误用了已做过云控选择的隔离 home，因 remote-reject 不再存在而失败；保留失败记录并改用全新 home 重跑，不改生产同意流程或放宽断言。

全新 home 的 `build/mobile-v33` 手机 routes 已通过；本地 Linux uber JAR 打包和 32 项固定资源逐字节检查通过。最终补充窗口 Chrome 的最新关闭回调委派，确保长期持有的窗口按钮读取当前托盘注册状态，随后 `:desktop:desktopMainClasses` 编译通过。
