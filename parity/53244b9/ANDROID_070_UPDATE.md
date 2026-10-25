# Android 0.7.0 新功能跟进

功能真源：本地 `MeloX-Android` 工作区，初次全面核对 HEAD `91adac7`，比较范围 `be7c33d..HEAD`（117 个变更文件）。常规 UI 参考仍为 `53244b9`；新增功能使用共享真实业务并接入手机、电脑各自现有布局。

## 新增范围

- 汽水音乐：真实抖音扫码、推荐歌单、搜索游标、歌单详情、账号歌单、歌词和播放。
- 播放顺序：当前音源 → 内置严格完整音频匹配 → 用户开启的 CHKSZ/LX，跨平台串行并跳过失败来源。
- 酷狗概念版每日首次启动自动领取畅听和概念版 VIP 两项，成功当天去重，失败 10 分钟后重试。
- Bilibili 已收藏的他人收藏夹／合集类型分流与分页，单条失效视频隔离。
- 歌词罗马音按视觉行和发音单元覆盖字符对齐，换行单元拆分。
- 首次启动全屏连续引导、1.3 版法律文本、歌词样式选择及 Android 通知栏播放模式更新。
- LAN/DLNA 已迁移并完成 Windows 发行核验，详见 `LAN_ANDROID_UPDATE.md`。MiPlay Binder 和 Android usbfs 仍有平台依赖。

## 当前新增：汽水音乐

在 `shared/.../provider/qishui/` 移植 Android 协议和字段映射，音乐源 ID `qishui`，完整保留字符串 ID；注册到浏览与播放 registry、账号管理、播放认证指纹、统一搜索和通用歌单／歌词能力。两套服务管理和账号页加入真实二维码登录，验证 `/luna/pc/me` 后才写入隔离的桌面安全偏好；退出保留设备身份。源协议 MIT 许可证随包保留。

试听保持 `Preview`，不把请求无损当作实际无损，不解密 spade 容器，不向 CDN 转发账号 Cookie，不声明收藏写入或不存在的专辑／艺人接口。搜索依上游不透明游标续页，歌单模型新增可选 hasMore 以继续游标页。

本轮 **65 suites / 321 tests 全部通过，零失败／错误／跳过**，桌面编译通过。新增 8 项汽水协议回归覆盖字符串长 ID、毫秒时长、试听、登录态实际音质/CDN Cookie 隔离、音频地址与加密容器边界、搜索游标、歌单游标和 QR 创建 Cookie/确认会话隔离。首轮有两个 JUnit 返回类型和首页可见源清单问题，修正后完整重跑通过；原先 314 tests 的失败尝试不计为成功。证据在 `evidence/android-070/qishui-full-tests/`。

手机／电脑未登录入口回放 v1 各 **5 scenes / 7 assertions** 通过，只证明音乐库登录门和账号表单可达；实际 QR 展示／刷新另跑 v2。真实用户扫码、登录态搜索及完整歌曲可听验收须另行记录，离线协议响应不能算真实账号验收。

真实上游 QR 生产输入回放 v2 通过：手机／电脑各 **9 scenes / 12 assertions**，实际进入登录、等待上游 QR 显示、鼠标点击刷新、重新显示并关闭表单。截图、只读 semantics、输入记录与压缩完整日志在 `evidence/android-070/qishui-ui-v2/`。未进行用户扫码确认或保存任何真实用户账号。

汽水提交 `ec5753a` 的 Windows CI `40164499557` 成功；完整下载发行 ZIP **339546447 bytes / 357 entries**，SHA-256 **`b492f43bb8b31c6b343080fb45337bad7e2a402778887e46f8d3ee16821de83a`**。独立检查 ZIP/JAR CRC、build-info 精确提交、固定资源与 Windows PE 解码器均通过，JSON 在 `evidence/windows-download-40164499557/`。此发行只对应汽水批次，不能当作后续回退／Bilibili／设置功能发行已通过。

匿名上游只读核查：推荐接口 HTTP 200 返回 10 个内容块；H5 示例曲目 `7079108541549643812` 返回原曲 297240ms、音频 30.047 秒，为试听。该检查没有进行用户登录或签到写入操作。

## 持续检查

`tools/check_android_lan_reference.py` 已扩展为覆盖全部 Kotlin/Java 源码与测试、资源字符串、法律文本、构建信息及 docs；除输出包外的功能改动同样纳入哈希与按内容快照。完整功能哈希单独保存在 `evidence/android-070/android-live-reference.json`。

## 下一批：播放回退顺序

对照 Android `PlaybackFallbackChain`、`ThirdPartyPlaybackResolver` 与两套 native resolver 迁移。当前音源先尝试；明确的会员／地区／版权／资源不可用结果才按用户开关进入内置跨平台严格匹配，然后尝试已配置的 CHKSZ、LX。登录缺失保持明确错误，网络异常保持其错误语义，本地和 Jellyfin 不进入外部回退。当前源返回的试听仍标为试听；跨平台候选只接受 Playable，不接受 Preview。

内置平台搜索由并行竞争改为按配置串行，跳过刚失败来源；网易云和汽水纳入候选，保留现有酷狗 `kugou` 身份。Android 的独立 `kugou_lite` 平台及其每日双签到尚未迁移，不能把标准酷狗标为概念版。源／账号／回退开关变化参与缓存指纹，跨平台 headers、实际音质及过期信息传递到真实播放请求。

新增顺序、试听排除、开关／缓存、登录错误、第三方失败隔离和网易云 native 优先回归；最终 **66 suites / 333 tests 全部通过，零失败／错误／跳过，桌面编译通过**，证据在 `evidence/android-070/fallback-full-tests/`。首轮 331 项中云控签名测试仍提供旧的三来源顺序，触发既有完整清单校验后回退内置顺序；测试数据已扩展并补测旧签名清单的回退及禁用来源保留，随后全量重跑通过。

已正常推送 `566d099`；对应 Windows CI `40165291486`，等待结果及完整发行下载独立核验。

## Bilibili 收藏夹与合集

对照实时 Android 工作区迁移收藏的他人歌单列表，合并自建列表并按资源身份去重；普通收藏夹 ID 与 `season:owner:id` 合集 ID 独立，保持只读能力。收藏列表依 Android 当前边界最多读 5 页、每页 40 项，不能声称无限分页。列表封面请求或收藏列表请求失败时保留已取得的自建信息，用户直接打开详情时仍明确返回接口错误。

合集详情使用 `/x/polymer/web-space/seasons_archives_list`，保留 mid、season_id、page_num 和实际 page_size；普通收藏夹仍使用 `/x/v3/fav/resource/list`，两种详情均传递 hasMore。按服务器已提供 cid 构建 native bvid:cid 身份，必要时展开视频分 P；单个删除／私有视频 IOException 被隔离，取消继续向上传播。新增协议回归覆盖列表分页、同号不同类型、合集详情、单视频故障与部分列表故障，执行结果待填写。真实私人账号收藏仍待用户实机验收。

首次编译发现 Android 摘要的 isEditable 字段不在桌面模型中；移植已改为沿用 Windows 现有 provider 能力机制（Bilibili 不声明 PlaylistWrite），并断言此能力边界，重新运行全量测试与编译。首次编译失败不计通过。

最终 **66 suites / 337 tests 全部通过，零失败／错误／跳过，桌面编译通过**；证据在 `evidence/android-070/bilibili-full-tests/`。期间修正测试中 nullable 分页记录的断言类型；执行回归还发现 HTTP 200 内部业务错误的解析原本处于列表容错范围之外，已将 `.data()` 纳入同一容错范围，确保收藏列表业务错误不丢弃自建歌单。最终 v5 全量验证通过，之前失败或超时尝试不计通过。
