# Windows CI 编码失败修复与实时兼容变更核查

## CI 40172679784／40173862466

两次 Windows CI 均在新增 `capture_feature_surface.py` 解析 capture.json 时失败：Windows Python 3.12 默认 CP1252，不能解码 UTF-8 的中日文数据，抛出 UnicodeDecodeError。不是 Kotlin 编译或共享测试失败：最新失败 run 下载的真实 XML 显示 **68 suites / 350 tests，零失败／错误／跳过**；既有设置回放亦完成 434 scenes／333 assertions。

修复两个新增回放脚本全部 read_text／write_text 的显式 UTF-8，包括罗马音 steps／expect／summary／semantics，避免后续步骤碰到同类问题。新增 feature-surface／ruby 的 artifact 上传路径，保留失败时实际输入、JSON、截图和 Gradle 日志。旧 workflow 未上传本次新回放目录，不能声称已取得原失败 capture.json。

下载同一失败 run 的日文设置 capture.json，实际用 CP1252 解码复现错误，用 UTF-8 成功解析并核对 **88 scenes / 65 assertions** 全通过。对两个修改脚本进行 AST 审计，全部文本路径明确使用 UTF-8；语法与 workflow 结构检查通过。证据 `evidence/ci-utf8-fix/`。修复后的全程 Windows CI 与新发行需重新完成，不将此检查计作发行成功。

## Android 新增五处兼容修复

直接对照前一份内容哈希快照：

- XiaomiSuperIslandLyricService／MeloXPlaybackService：特殊前台服务类型调用的门控由 API 29 改 API 34。
- FloatingLyricsPip：seamless resize 仅在 API 31+ 调用。
- AudioOutputDialog：关闭时立即返回；现代 MediaRouter2 类型单独置于 API 34+ 文件，低版本走 AudioManager／系统蓝牙设置；较新 route type 常量按 API 35／36 门控。

Windows 对应生命周期已核查：`MeloXLanOutputPanel:39` 与 `MeloXLanDialog:43` 均在访问 runtime／collect 状态／创建扫描 effect 之前对 invisible 立即返回；底部桌面入口也只在 lanOpen 时创建 Dialog。附近 LAN／DLNA 扫描属于可见面板的 LaunchedEffect，关闭离开 composition 后取消，已建立的进程级互联会话继续保留。`MeloXNowPlaying` 本地输出回调仅在用户点击时调用实际系统音频设置，未在隐藏页面启动系统设置。

这些 Android API 等级修复不需要把 Android 类或版本号套入 Windows。Windows 系统媒体／悬浮歌词原生后端和完整本地设备选择能力仍按功能清单待迁移；静态生命周期检查不当作 Windows 物理设备实机验收。

## 重跑 40282057681：真实详情服务错误

`15ce39e` 的重跑在更早的 Desktop collection 步骤失败，**尚未执行新增 UTF-8／罗马音回放**，不能算该修复已获 Windows CI 通过。下载完整 Verification-Windows 后，截图与 semantics 明确显示歌曲详情实际报错 `网易云请求失败（404）：成功`，存在 `desktop-song-detail` 与重试按钮，但错误状态隐藏了所有标签页。因此不是凭标签缺失推测加载时序问题。

生产修复：歌曲菜单与首页的详情路由携带已从真实服务取得的 SearchSong；详情仅接受 neteaseSongId 匹配的初始数据。刷新详情失败时保留已有实际资料，显示错误和重试，继续独立加载歌词／相似歌曲／评论；没有可信初始数据时仍显示原有真实加载错误。不插入假账号、歌曲或播放链接，不把刷新失败隐藏为成功。

本地明暗两轮真实网络＋实际鼠标／键盘菜单回放通过，共 **68 scenes／82 assertions**，包含标签切换、未登录收藏错误对话框、返回歌单与艺人路由；这两轮未观察到原 404，故不冒称实网稳定重现错误分支。另一次含 touch drag 的完整本地回放提前在 scroll return 停于 `0-222`（期望 `0-0`），未到歌曲菜单，失败日志如实保留；不计作触摸归位通过。Windows 原始截图／语义、两轮成功输出与该失败日志见 `evidence/song-detail-ci-fix/`。新 Windows CI 和完整发行核验仍待完成。

## Windows CI 40288772016 与完整发行核验通过

生产源码 **`af4a9a4e34a5b8af1cbdda7fe8170d597642320d`** 的完整 Windows CI 已成功。完整下载 `MeloX-Desktop-Windows.zip` 后，由 `tools/verify_delivery_artifact.py` 独立检查源码 stamp、全 ZIP CRC、固定资源与 PE 解码器：**PASSED**，357 entries，339671485 bytes，SHA-256 `88b00be252873f12d0358ba14512dfb6b13ec0b1b1b91020e846d98693e800d0`。

另完整下载 Verification-Windows，逐一解析此次实际回放与 XML：共享 **69 suites／359 tests，零失败／错误／跳过**；双布局 feature-surface controls／reload **98 scenes／62 assertions**；双布局 ruby **17 scenes／18 assertions**；明暗 collection playlist（包括 Windows 实际拖动与菜单）**76 scenes／90 assertions** 全通过。该结果确认 UTF-8 脚本已在 Windows 全流程执行通过。证据 `evidence/windows-download-40288772016/`。

此成功发行包括应用语言／背景、罗马音、Passport、搜索音质与详情保留等既有批次；不包括正在迁移、尚未提交的统一收藏增量。不把 CI 与完整 ZIP 校验等同于私人账号、跨机 LAN 或物理听音验收。
