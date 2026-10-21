# 电脑模式：只以 Mac Desktop UI 为基准

用户纠正：手机端 UI 只作参考。可以共用真实 API、播放、会话、数据模型及设置持久化，不能用手机版页面／控件／弹层充当桌面迁移成果。前一批六图生产回放不代表这一约束已经满足。

## 本批替换

### 音乐库 → 歌单

此前Home／Search已走桌面collection，但音乐库选中歌单仍走Android移植的`MeloXLibraryPlaylistDetailScreen`。桌面模式现改用同一Mac collection分支，排除手机split-detail分支。未登录回放不能证明私人音乐库已在登录态实测。

### 歌曲菜单

- 固定真源：`MeloXDesktop/Shared/DesktopTrackRow.swift:139–207`。
- 桌面入口统一分流到独立`MeloXDesktopSongMenu`；不会composition手机版`SongActionsContent`或`ModalBottomSheet`。
- 紧凑菜单、两条分组线，按源顺序提供下一首、添加到队列、添加到歌单子菜单、条件显示的移除、喜爱星标、下载、歌曲信息、艺人、专辑和分享；菜单266pt、项目最小26pt，边界按实际窗口裁限。
- 省略号按歌曲行的实际屏幕位置锚定；右键按实际指针位置锚定；Touch长按可打开同一菜单。Escape逐级关闭，方向键可移动焦点，Enter使用真正的clickable键盘动作。
- 添加到歌单查询真实用户拥有的歌单或provider可写歌单，空／失败／读取中为真实状态；未登录的创建入口禁用。跨页provider不只读取第一页。
- 新建歌单按源420×280独立桌面对话框，23pt操作钮、紧凑名称输入和私密checkbox；不使用手机创建歌单底部页。Mac源的创建动作只创建歌单，不暗中同时添加歌曲。
- 移除有真实确认；喜爱先读真实状态再写，未知不伪造为未收藏；下载走现有真实存储；Windows分享复制真实provider公开URL。

### 歌曲详情

- 真源：`DesktopSongDetailView.swift`。新增独立桌面歌曲route，collection header、420pt三段picker、相似歌曲／歌词／评论。
- 资料调用真实歌曲详情和相似歌曲；歌词使用真实时间戳，点击通过`playQueue(startPositionMs=…)`启动对应曲目，避免先启动异步播放后立即seek造成竞态；评论为真实用户数据，不伪造评论或账户。
- 从此处再进艺人／专辑时，桌面collection路由保留真实返回栈；侧栏导航清空返回栈。原先单一target替换会使“歌手→专辑→返回”丢失歌手页。
- header收藏先读取真实状态后写入；未登录显示独立桌面错误对话框。附加内容的加载／请求失败／成功空列表分别展示，网络失败不能冒充“暂无歌词／评论”。

### 歌手详情

- 真源：`DesktopArtistDetailView.swift`；独立`MeloXDesktopArtistDetail`在手机版artist composition之前分流。
- 380pt hero／48pt背景blur／1.38缩放／0.58透明度、190pt圆头像、34pt内距、24pt纵间隔、48pt红色播放按钮、34pt名字。
- hero下30pt间隔、34pt外边距、36pt双栏间隔；左栏最多8张新发行专辑，145–205pt自适应列、横20／纵22；右栏12首热门曲目，复用Mac桌面track row。
- follow真实写入、失败恢复；专辑播放读取真实专辑曲目；不再进入手机artist大图／手机二级网格／底部资料sheet。

### 滚动

Compose Touch原生滚动保留；为AWT可能提升为Mouse的触屏补充按触控阈值开始的纵向拖动与真实惯性。只在尚未被子控件消费的手势上接管；横向手势、文字选择、按钮、滚轮不借用手机手势实现。原生Touch和Mouse拖动回放分别验证位移超过100px和反向回到`0-0`。Windows物理触屏仍待实测。

新增`auditNativeTouchScroll`：Windows上使用系统`InitializeTouchInjection`／`InjectTouchInput`注入真实系统触点，经AWT／Compose进入生产`desktopPromotedDragScroll`；再用AWT Robot验证原生Mouse拖动。独立JSON记录滚动前后index／offset与Compose实际收到的pointer类型。Linux只验证96／144字节x64 ABI并报告`UNSUPPORTED`，不得算Windows输入通过；Windows系统注入通过也不等于物理触屏验收。

## 验证边界

回放覆盖实际省略号、子菜单／Escape、右键、入队、歌曲三页、歌手、专辑及返回栈，并断言无`歌曲操作`手机sheet／无Android artist页面。独立报告核对菜单尺寸、380hero／190头像、触屏实际位移。测试不伪造网易登录态；创建／写入私人歌单及真实触屏不得以未登录的离屏回放代替。

仍须继续替换桌面设置中沿用的手机表单控件，以及其他尚未完成的桌面列表／页面；以上替换不声称整套Mac UI或同内容整幅像素验收完成。

本批仍有可见残差：Mac原生菜单材质／系统控件动效、歌曲别名／播客描述模型、评论点赞图标与本地化数字、系统分享窗口尚未逐项等价；Windows分享目前复制真实链接。歌手图片异步下载可能在首次截图中仍为占位，不能将这些画面用于已完成的同内容像素验收。非网易provider的歌曲信息暂使用真实搜索回退，不能计作该provider的详情页已迁移。
