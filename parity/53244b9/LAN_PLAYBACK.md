# Windows 局域网播放（MELOX-LAN v1）迁移

依据 Android `docs/MELOX-LAN.md` 与固定参考源码（当前 Android 仓库 `2fc6a30`，其 LAN 实现同 0.6.5 的 `53244b9`）迁移，而非自拟协议。

后续互联更新改为直接读取用户本地 `MeloX-Android` 工作区（含未提交改动），见 `LAN_ANDROID_UPDATE.md` 与该批真源文件哈希。下面的首批测试数是历史记录，不代表新版发行或跨设备验收。

## 首批迁移历史（当前实现见 LAN_ANDROID_UPDATE.md）

- UDP `47830` 发现/应答（`MELOX_LAN`、v1、nonce 匹配、拒绝自身与非法来源）；TCP `47831` 起最多 10 个候选端口寻址。
- 4 位 PIN、5 次失败断开并限制来源地址、常用设备显式信任（对端不能自行加入）、最近连接（最多 10 台）、每秒状态事件、15 秒握手超时、256 KiB 行限制、最多 8 个客户端。
- `state/control/transfer/cast.start/cast.stop/together.start/together.stop/ping` 全部对接真实 `PlaybackCommands`/`AudioPlayer`。拒绝未知 action 和不支持的来源。
- 投放：只在 Java Sound 实际接受 PCM 后取样（`writePcmBlock` 的 accepted 区间），按 20ms、4 字节大端长度、signed16LE 发送；接收端校验来源 IP、长度/帧对齐，真实打开 `SourceDataLine` 播放，并遵循本机音量。没有假音频或截图级模拟。
- 接力：接收方用自己账号解析轨道（Netease/QQ/酷狗等 provider 的 `PlaybackCapability`），失败时不动当前播放。
- 同步听歌：主持人每秒推送队列/位置；访客按 400ms 阈值校正 seek/播放状态；访客控制经同一 TCP 连接回传。访客音频中继 token 32 字节、绑定来源 IP 与队列、未使用 10 秒过期、会话上限 6 小时、停止即撤销，只代理真实 HTTP Range 或本地文件，不转发 Cookie。
- 设置/入口：桌面底栏新增“局域网播放”，显示设备名、实际端口、PIN、状态、常用设备与最近连接；支持启停、改 PIN、扫描、PIN 配对、远程控制、接力、投放、同步听歌。

## 首批验证历史

- shared **292 tests / 0 failures/errors/skipped**，桌面编译通过。
- 新增 `MeloXLanProtocolTest`（loopback 真实 socket）：错误 PIN 被拒、正确 PIN `auth_ok`、state/ping 回显、control 实际改变 host、unsupported 来源 transfer 被拒；真实 UDP 发现+单播应答；投放包长度/帧解析由伪 `SourceDataLine` 接收（不是物理扬声器验收）。
- 未验证：真实跨机 Windows↔Android、防火墙提示/放行、同一局域网多网卡选择、物理扬声器与长时间同步漂移。不能把 loopback 测试写成跨设备已验收。

## 首批边界记录

- Windows 防火墙首次监听会弹窗；拒绝后仅保留手动地址连接能力（需放行 TCP/UDP）。
- 多网卡时选择第一个站点本地 IPv4；VPN/虚拟网卡已过滤，但未做逐接口 UI 选择。
- 投放固定使用 MeloX 输出格式（44.1kHz 立体声），格式变化需重开投放。
