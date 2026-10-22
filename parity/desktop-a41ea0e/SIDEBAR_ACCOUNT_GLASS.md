# Sidebar／账户／侧边歌词修正

## 固定源依据

真源 HEAD：`a41ea0e1e1fa69fb543d827b853fe3aff5420951`。

- `Features/Sidebar/DesktopSidebar.swift:141–174`：账户 footer 读取真实 library profile 的头像／昵称，打开 account sheet。
- `App/DesktopRootView.swift:234–271`：pageContent、side panel、bottom player 在同一 ZStack；side panel 为 trailing overlay（zIndex 2），bottom player 在其上（zIndex 3）。
- `Features/Player/DesktopPlayerSidePanel.swift:24–45`：macOS 26 `.glassEffect(.regular, in: .rect(cornerRadius: 0))`，旧系统 ultraThinMaterial；没有额外描边。

## Windows 实现

- 左侧 199pt 大栏移除 `.border` 和原有 72% 实色板，使用无描边 `desktopLiquidPanel`。侧栏和歌词／队列面板共用真实页面 backdrop 采样、GPU blur、lens refraction／dispersion；磨砂设置启用时关闭折射，主题色使用当前 MaterialTheme。
- 侧边歌词／队列仍为浮层；移除 Home 的 inspector 宽度预留及底部播放器右侧避让。开／关歌词不再改变卡片宽度、列数或底部播放器的位置／宽度。
- Sidebar 与 account sheet 共享 layout 持有的 `NeteaseSessionStore`，在桌面启用及 Cookie 变动时加载真实 `accountProfile()`。原 footer 的独立 store 从未调用 refreshProfile，导致已有 Cookie 时仍显示未登录占位。资料暂未返回时显示“已登录”，不捏造姓名或头像。
- 切换 UI 仅更改 `general_desktop_layout`。`netease_session / cookie_header` 仍由原 SecureSessionPreferences 管理，不迁移、不覆盖或清除；仅显式登出动作会删除登录 Cookie。
- Account sheet 增加音乐服务管理入口（未登录亦可进入）；使用现有真实 provider account manager／登录路由，增加当前音乐源 picker，通过原主程序 source callback 即时更新并持久化。

## 验证与边界

- `verify_desktop_lyrics_pane_capture.py` 比较开歌词前、歌词、队列、再次歌词及关闭后：Home scroll、每日推荐卡片、底部播放器 bounds 必须逐项一致。
- `capture_desktop_surfaces.py` 增加 account → music services → 返回 → account → 登录输入回放。
- 本地 `build/surfaces-v29` 通过 58 scenes／77 assertions：包括从账户打开音乐服务、真实切换 Local／NetEase 并重新打开菜单断言选中项、Escape 关闭 picker、返回和登录面板。回放暴露并修正了桌面 picker 没有显式 Escape 焦点处理的问题。
- 独立读取该回放隔离 home 的 `prefs_melox_music_providers.properties`，确认最后选择落盘为 `selected_source=netease`。本地 Linux uber JAR 打包通过，32 项固定资源逐字节核验通过；这不是 Windows 新发行的独立核验。
- 本地 `build/home-v30` 通过 40 scenes／56 assertions（横／纵 promoted drag、原生 Touch、真实歌单／专辑、返回栈、账户及实际主题切换）。独立 Home verifier 通过两种主题、几何和保留滚动状态。原浅色侧栏测试要求旧白色板至少 250；透明玻璃实际为 249，已按浅色页面阈值 240 检查，保留深色／选中强调色和几何断言。
- `build/lyrics-v31` 通过 11 scenes／18 assertions、6 个 panel 帧；独立检查开关歌词／队列的四个阶段，Home scroll／每日推荐卡片／底部播放器 bounds 全部逐项一致。`build/settings-v31` 95 scenes／31 assertions，650pt 外框／58pt footer 几何通过；`build/mobile-v31` 手机 routes 88 scenes／65 assertions 通过。
- 实机登录账户头像／昵称及离线／过期状态仍需要真实账户验收；此轮不使用虚构身份或私人账号截图。
- Windows 的 Skia 光学实现不是 macOS 原生 glassEffect；同内容整幅像素与物理触屏验收仍为 PARTIAL。
