# MeloX Windows

[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

<p align="center">
  <img src="https://raw.githubusercontent.com/lladlam/MeloX-Android/main/android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png" width="128" alt="MeloX icon" />
</p>

<p align="center">
  使用 Kotlin + Compose Multiplatform 构建的 MeloX Windows 桌面移植版
</p>

> [!IMPORTANT]
> **MeloX Windows 仍处于早期开发阶段。** 本项目从 MeloX Android 移植而来，核心业务逻辑已迁移至 Kotlin Multiplatform 共享模块，桌面端 UI 和音频后端仍在完善中。

> MeloX Windows 是非官方开源项目，与网易云音乐、小米、Apple 及其关联公司不存在隶属、合作或授权关系。

## 项目说明

MeloX Windows 基于 [lladlam/MeloX-Android](https://github.com/lladlam/MeloX-Android) 的设计、交互与业务逻辑进行跨平台移植。

项目目标是将 MeloX 的完整功能带到 Windows 桌面：

- 使用 **Kotlin + Compose Multiplatform Desktop** 重建界面与交互；
- 使用 **shared module** 复用 Android 版本的全部业务逻辑（账号、搜索、歌词、音质、播放解析等）；
- 通过 **expect/actual** 平台抽象层适配 JVM/Desktop 特性；
- 支持 Windows、macOS、Linux 三大桌面平台；
- 复刻 MeloX 风格的播放器界面、歌词显示与音乐库。

## 当前已实现

### 核心业务逻辑（共享模块）

- 网易云音乐账号登录与 Cookie 持久化；
- 登录态搜索、歌曲详情、歌词请求；
- LRC / YRC / TTML 歌词解析；
- 音质模型与播放 URL 解析；
- QQ音乐、酷狗、酷我、Bilibili、Spotify、YouTube Music 等多音乐源接入；
- Provider-neutral 下载与离线播放链路；
- 远程配置与动态开关；
- 本地推荐引擎与个性化排序。

### 桌面 UI

- 深色主题播放器界面；
- 多音乐源切换（网易云、QQ音乐、酷狗、酷我、Spotify、YouTube Music）；
- 歌曲搜索与结果展示；
- 基础播放控制（播放/暂停、上一首/下一首）。

### 平台抽象（Expect/Actual）

| 模块 | Expect 声明 | Desktop Actual |
| --- | --- | --- |
| MeloXPlatform | 时间戳、日志 | JVM System.nanoTime + stdout |
| MeloXStorage | 文件读写、设置存储 | java.io.File + java.util.Properties |
| MeloXPreferences | 键值存储（替代 SharedPreferences） | Properties 文件持久化 |
| MeloXNetwork | 网络状态检测 | java.net.InetAddress |
| MeloXCookieManager | Cookie 清理 | java.net.CookieManager |

## 技术栈

| 用途 | Windows 实现 |
| --- | --- |
| UI | Kotlin + Compose Multiplatform Desktop |
| 共享业务逻辑 | Kotlin Multiplatform (commonMain) |
| 网络请求 | OkHttp |
| 歌词解析 | 自研 LRC / YRC / TTML 解析器 |
| 异步任务 | Kotlin Coroutines |
| 音频播放 | 待接入（mpv / javax.sound） |

## iOS → Windows 平台映射

| MeloX / iOS | MeloX Windows |
| --- | --- |
| SwiftUI | Compose Multiplatform Desktop |
| NavigationStack | Compose Navigation |
| AVPlayer / AVFoundation | 待接入音频后端 |
| MPNowPlayingInfoCenter | 系统媒体控制（待实现） |
| SwiftUI Mesh / Flowing Light | Compose Canvas 动态取色背景 |
| Live Activity / Dynamic Island | 系统通知（待实现） |

## 运行环境

- JDK 17 或更高版本；
- Gradle 9.5.0；
- Windows 10/11、macOS 或 Linux。

## 本地构建

1. 克隆仓库：

   ```bash
   git clone https://github.com/lladlam/MeloX-Windows.git
   cd MeloX-Windows
   ```

2. 构建共享模块：

   ```bash
   ./gradlew :shared:build
   ```

3. 构建桌面应用：

   ```bash
   ./gradlew :desktop:run
   ```

## 项目结构

```text
.
├── shared/
│   └── src/
│       ├── commonMain/kotlin/melox/    # 共享业务逻辑（163 个 Kotlin 文件）
│       │   ├── account/                # 账号与会话管理
│       │   ├── audio/                  # 音质模型与播放 URL 解析
│       │   ├── download/               # 下载与离线播放
│       │   ├── lyrics/                 # 歌词解析（LRC/YRC/TTML）
│       │   ├── model/                  # 搜索歌曲模型
│       │   ├── music/model/            # 音乐数据模型
│       │   ├── music/provider/         # Provider 注册与管理
│       │   ├── network/                # 网络请求与 API 客户端
│       │   ├── platform/               # Expect 声明
│       │   ├── playback/               # 播放解析与播放控制
│       │   ├── provider/               # 各音乐源 Provider 实现
│       │   ├── recommendation/         # 本地推荐引擎
│       │   ├── remoteconfig/           # 远程配置
│       │   └── update/                 # 更新检查
│       └── desktopMain/kotlin/melox/   # Desktop Actual 实现
│           └── platform/               # JVM 平台适配
├── desktop/
│   └── src/main/kotlin/melox/
│       ├── Main.kt                     # 应用入口
│       └── ui/MeloXApp.kt             # 桌面 UI
├── gradle/
│   └── libs.versions.toml             # 依赖版本管理
├── settings.gradle.kts
└── README.md
```

## 开源项目与特别鸣谢

MeloX Windows 的主体代码来自 MeloX Android 的跨平台移植工作。

### 上游项目

- [lladlam/MeloX-Android](https://github.com/lladlam/MeloX-Android) — 本项目的直接来源，提供核心业务逻辑；
- [lladlam/MeloX](https://github.com/lladlam/MeloX) — MeloX 的上游项目。

### 技术依赖

- [Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) — 跨平台共享业务逻辑；
- [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/) — 跨平台 UI 框架；
- [OkHttp](https://github.com/square/okhttp) — HTTP 网络客户端；Apache License 2.0；
- [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) — 协程与异步任务；Apache License 2.0。

## 免责声明

本项目出于学习、研究与开源交流目的开发。

- MeloX Windows 不以绕过付费、版权、地区限制或网易云音乐服务限制为目标；
- 使用者应自行遵守所在地法律法规、网易云音乐服务条款以及音乐内容的版权要求；
- 项目调用的第三方服务接口可能发生变化，开发者不保证持续可用；
- 本项目按许可证所述不提供任何担保，使用本项目产生的风险由使用者自行承担。

## 许可证

MeloX Windows 主体代码按照与上游 MeloX 相同的 **GNU General Public License version 3（GPLv3）** 发布，完整条款见 [LICENSE](LICENSE)。

复制、修改或分发本项目时，请遵守 GPLv3 关于源代码提供、版权声明、修改说明以及同许可证分发等要求。
