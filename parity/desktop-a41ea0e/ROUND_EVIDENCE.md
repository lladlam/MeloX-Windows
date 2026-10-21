# 本轮桌面迁移证据（2026-10-22）

## Windows CI

- 运行：<https://github.com/lladlam/MeloX-Windows/actions/runs/39632536833>，head `a1f6f109d18dda4aa763a2cfd1665c3d4cdf7543`，结论 **success**。
- 覆盖：生产构建、全部既有手机回放、桌面布局切换／真实播放器／独立 JVM 重载、专辑／歌单／歌手返回栈、Home 深浅与 Touch、播放器队列／音量、四语言设置路由、20 个桌面设置子页、内存启动、Bilibili／歌词／Skyline／歌曲操作、字形与字宽、jpackage app-image 及发行资源核验。
- 系统触屏注入：运行环境 `SM_MAXIMUMTOUCHES=0`，`InjectTouchInput` 在任何帧前即返回 87；报告记录 `UNSUPPORTED`、`max_touches=0`、原始错误与 AWT Robot 错误，**未记作 PASSED**。物理触屏仍需有数字化仪的 Windows 机器。
- 最终 ZIP：`MeloX-Desktop-Windows.zip`，337,835,368 bytes，SHA-256 `ff5958d0217f84c974b0f0bf006aabaf595329f754a204fe24819b6532439c05`，357 entries，`unzip -t` 全部通过。

## 发行独立核验

- JAR：`app/MeloX-Desktop-windows-x64-1.0.0.jar`，292,962,057 bytes。
- `parity/build-info.properties` 的 commit 为 `a1f6f109d18dda4aa763a2cfd1665c3d4cdf7543`，与 CI head 一致。
- 已验证 32 项固定资源逐字节一致；新增桌面资源另行核验：两语言导出 properties、`desktop-logo.png`、BeatNet notice、NeteaseAPI license、指纹 notice，SHA-256 与源码逐项一致。
- 解码器：`decoder/ffmpeg.exe`，164,352,000 bytes，SHA-256 `af7913b7ddc324bf02b8af72fe8612b1471455ef8d9e04e45466510742abcff9`，PE `MZ`。
- 证据文件在本地 `build/windows-delivery-check-39632536833/`（`pinned-resources.json`、`new-desktop-resources.json`、`decoder-and-layout.json`）；`build/` 不进入版本库。

## Linux 独立回放（源码 `a1f6f10`）

- `native-surfaces-v24-dark` / `v24-light`：各 48 scenes、66 assertions，17 个桌面 surface；650pt 面板／58pt footer 几何通过。
- 桌面设置：`native-settings-v24-dark` / `v24-light`（1280×900）各 95 scenes、31 assertions；控件 `13/9`，独立 JVM 重载 `1/6`。
- 手机回归：`native-phone-v24` 88 scenes、65 assertions；手机控件 `controls` 全通过。
- 非网易音乐库：`native-provider-lib-v23` 3 scenes／3 assertions，无手机 segmented picker。
- 专辑／歌单／歌手：`native-collection-artist-v3` 38 scenes／45 assertions，包含真实菜单、返回栈；本轮 CI 同路径也通过。
- `:shared:desktopTest` 与 `:desktop:packageUberJarForCurrentOS` 通过。

## 仍未完成

- 完整 Mac 原生材质／玻璃光学／spring 动效与同内容整幅像素差：**PARTIAL**。
- 独立 mini-player 窗口、消息回复／分页菜单、原生系统分享窗口：**PENDING**。
- 真实网易账户下的收藏、云盘、私信发送、双账号 Together、硬件麦克风识曲、长时内存与设备切换：**BLOCKED**，未以未登录回放代替。
- Windows 登录面板使用真实验证码／本机客户端 Cookie，不是 Mac 的 WKWebView；网易官方客户端登录数据只在 Windows 可用。
