# LX 用户音源迁移

以 Android `7c17c00` 和 lx-music-mobile 的用户 API 宿主为参考，Windows 的音乐服务页在两套布局中提供第三方音乐源协议、启停与仅会员回退设置、CHKSZ 密钥设置，以及 LX 脚本的本地文件／HTTP(S) 导入、元数据展示和移除。脚本正文保存在应用自己的目录，保留旧偏好中的源；单文件上限为 9 MB、最多 20 个源。

原来的空运行时替换为专属 owner 线程管理的 Javet V8 isolate，提供 LX 事件、异步 HTTP、定时器、buffer／crypto、音源声明与质量协商；取消和关闭会取消在途请求。独立 HTTP 客户端不共享应用账号 Cookie、认证器或缓存，跨源重定向移除敏感请求头。播放解析使用 Android 新版扁平 `musicInfo`、并发请求去重和逐质量回退，不把不同平台的歌曲 ID 当作可互换的 ID。

隔离 home 下四次实际输入回放共 **48 scenes／34 assertions**：手机和电脑版分别完成协议、启用、输入本地测试服务器脚本 URL、导入、进程重启后设置与脚本恢复、移除和关闭。截图、输入轨迹和断言保存在 `evidence/lx-update/`，CI 也执行相同的四组回放。测试服务器只提供确定的测试脚本，不伪造账号或上游音乐服务。

本地全量回归在最后一轮歌词接线和取消处理修改前为 **78 suites／411 tests、零失败**；修改后对最终源码运行的 LX／歌词定向回归为 **6 suites／27 tests、零失败**，并完成桌面编译，报告见 `evidence/lx-update/targeted-tests.json`。V8 测试覆盖延迟 HTTP 回调、异步注册、连续并发调用、跨源重定向头隔离、multipart 边界、buffer／crypto、动态代码限制及取消与 isolate 关闭；歌词测试验证协议启停和源列表对缓存的影响。V8 close 后的测试日志没有未回收 callback context 警告。

另以独立 loopback 服务返回 WAV，经实际 LX 脚本解析和生产 FFmpeg 解码得到非零 PCM。该测试证明软件解码链，不证明物理听音、任何第三方脚本的线上可用性或真实账号权限。Windows runner 构建、发行与独立下载核验结果以相应 CI／发行证据为准；长时间内存、真实第三方音源播放仍须实机验证。
