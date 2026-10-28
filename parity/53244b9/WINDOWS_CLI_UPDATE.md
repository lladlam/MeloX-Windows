# Windows CLI 迁移

参照 Android `9faacd8` 的 `MeloXCliReceiver`、`tools/melox_cli.py` 和测试功能设置，实现 Windows 运行中的真实 CLI：`status`、`queue`、`play`、`pause`、`toggle`、`next`、`previous`、`stop`、`seek`、`volume`、`shuffle`、`repeat`、`search`、`search-play`。播放器操作经 Compose 主线程调用真实控制器，搜索走真实 provider；停止解码后保留队列和位置。DLNA 的 stop 发出实际 Stop 控制，一起听场景不虚报本地 stop 成功。

端点默认关闭，仅监听 127.0.0.1 的系统分配端口。随机能力凭据在 Windows 以当前用户 DPAPI 加密写入应用目录；同目录文件锁防止多个实例抢占。禁用或退出即关闭监听、断开客户端并取消在途命令。Linux 本地验证使用仅文件所有者可读的能力文件，不当作 Windows DPAPI 验收。

`:shared:desktopTest :desktop:desktopTest` 的本地全量结果为 74 suites／389 tests 加桌面 1 项；之后补充禁用中断在途命令的定向回归，桌面测试 2 项通过。两套布局以指针开启 CLI，独立 Python 进程对扫描到的两个本地 WAV 调用 15 个实际命令；再以独立 JVM 重启并用指针关闭，共 **18 scenes／17 UI assertions**，同时捕获 FFmpeg 实际解码的软件 PCM。验证输出、截图、指针和音频摘要在 `evidence/windows-cli/`。这不证明物理听音、真实线上音源或实际 DLNA 音响。

电脑版初次回放时保留播放导致指针目标稳定性断言超时，未记为通过；调整回放在验证播放后暂停，再重跑通过。Windows CI `40630160917`（`72dd374`）已成功，包含桌面端点测试、两套布局与 Python／DPAPI 互通回放，并把 `melox_cli.py` 和说明加入发行。完整下载的发行 **340183253 bytes／359 条目**，SHA-256 `df72a79107fbddd3ca1c3a3d0f263bb0f5cc07e12a4f62bb395fc92af6e8047e`；ZIP 和 JAR CRC、固定资源、PE 解码器、启动器图标与源码版本戳均通过，CLI 两个文件与 Git 对应版本逐字节一致。证据 `evidence/windows-download-40630160917/`。
