# YouTube Music 登录上下文与频道迁移

## Android 实时来源

`innertube/vendor/android-b687590.tar.gz` 来自 Android `b687590869516ce68c739a6295a1b3f18410e739` 的完整 `android/innertube` git archive，生成时确认该目录无本地改动，并逐文件比较工作区；归档 SHA-256 `ce246610989e0955cd4efb2c4af494520738db1500f0bbe799437127409776e0`。再次核对 Android `9faacd8`，该目录与归档来源无差异。源代码包含 Android 的 Cookie 精确识别、`X-Goog-AuthUser`、频道 Page ID 与地区规范化；仓库保留原上游说明与许可。

## Windows 接线

登录表单增加数字 AuthUser，接受实际可签名的 `SAPISID`、`__Secure-3PAPISID` 或 `__Secure-1PAPISID`；系统语言仍不被修改，YouTube 请求使用应用语言及 Android 的地区规范化。账号/频道验证独立发起携带固定 Cookie、Visitor、DataSync、PageID、AuthUser 的请求；待验证账号不会暂时覆盖其它播放／目录请求使用的全局 InnerTube 会话。成功验证后才原子保存，网络失败保留旧账号，401／403 标记过期；账号变更／退出使之前的验证结果失效。

两套音乐服务页接入从真实 `accounts_list` 获得的频道列表、验证后切换及手动重新验证。收藏歌单、库与搜索使用带账号代次和应用语言身份的 continuation 分页；未读完的结果不报告成完整总数，重复游标作为失败而非截断成功。代次保护包括 A→B→A 切回同一账号、等待队列期间切换与结果返回后的复核；已取消的重新验证不能写入迟到的账号名。两种歌单布局读取完整分页，YouTube 歌单成员即使指向同一视频也保留，点击重复条目按实际位置选中播放队列。

## 验收边界

本地共享全量测试 74 suites／387 tests 与桌面编译通过，覆盖分页保留重复成员、定位播放队列、收藏续页及会话竞态。随后为独立账号请求禁用 HTTP 重定向与缓存，防止显式 Cookie 经跳转外泄；变更后的协议定向测试 4 项及桌面编译通过。两种布局登录表单以鼠标和键盘回放，共 30 scenes／18 assertions。证据 `evidence/youtube-session/`。协议测试的 HTTP 响应与凭据是明确标记的本地 fixture，表单回放未提交凭据；它们不代表 Google 私人账号登录、实际频道切换或 Google 服务器返回成功。InnerTube 的普通目录／写操作仍使用上游全局会话，独立候选验证并不证明所有并发写操作已完全隔离；仍需真实账号、多频道、私有收藏与退出／重登实机验证。

修改通用歌单加载与播放队列后，分别从两种布局的真实本地音乐库扫描 55 个生成 WAV、翻页、创建歌单并用指针打开详情；共 32 scenes／35 assertions 通过，证据 `collection-regression-*`。此回放验证本地源回归，不冒充 YouTube 私人歌单验收。
