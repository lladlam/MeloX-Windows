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

Windows CI 的`SM_MAXIMUMTOUCHES`为0（无触控数字化仪），系统`InjectTouchInput`在任何帧前即返回87，AWT Robot同样没有OS输入桌面。审计现记录`UNSUPPORTED`并保留两项真实错误，不再把“环境无触屏”写成PASSED；生产拖动逻辑仍由Compose场景级Touch/Mouse回放验证。有数字化仪的Windows机器上才会得到PASSED/FAILED。

新增`auditNativeTouchScroll`：Windows上使用系统`InitializeTouchInjection`／`InjectTouchInput`注入真实系统触点，经AWT／Compose进入生产`desktopPromotedDragScroll`；再用AWT Robot验证原生Mouse拖动。独立JSON记录滚动前后index／offset与Compose实际收到的pointer类型。Linux只验证96／144字节x64 ABI并报告`UNSUPPORTED`，不得算Windows输入通过；Windows系统注入通过也不等于物理触屏验收。

## 验证边界

回放覆盖实际省略号、子菜单／Escape、右键、入队、歌曲三页、歌手、专辑及返回栈，并断言无`歌曲操作`手机sheet／无Android artist页面。独立报告核对菜单尺寸、380hero／190头像、触屏实际位移。测试不伪造网易登录态；创建／写入私人歌单及真实触屏不得以未登录的离屏回放代替。

### 搜索／探索／私信

- 搜索桌面模式改为独立工具栏：332×36胶囊输入、catalog／library双scope、5类紧凑segmented picker；结果显示42pt封面track row和145–210pt自适应媒体网格，专辑／歌手／歌单／播客均走真实搜索与桌面详情。未登录library scope显示登录提示；候选词读取真实`/api/search/suggest/keyword`，320ms防抖。Windows聚合搜索在桌面模式不冒充Mac catalog结果。
- 探索桌面模式按`DesktopDiscoveryView`分为New Music 12首、Charts网格、Popular Artists圆形网格；三个数据源独立失败，单一路由失败不会清空其他真实区块。
- 私信桌面模式按`DesktopMessagesView`为270–410pt会话栏、64pt会话头、真实历史／发送、头像／时间／未读数、资源卡和新建私信联系人面板；未登录显示独立登录门。双账号与真实网易私信会话仍待实测。
- 非网易Provider音乐库不再回退手机segmented picker：有登录会话时用同一34pt留白桌面网格展示真实歌单，Local显示真实收藏歌曲；无会话显示桌面登录门。歌单进入同一Mac collection详情。
- Windows音乐服务页为独立紧凑表单，Provider真实登录／退出、加入搜索和聚合搜索开关；选择具体Provider会带上Provider标识打开对应登录。
- Provider专辑／歌手详情从搜索进入时使用桌面collection头图、220pt封面、34pt标题与桌面track row，不再显示手机top bar。
- 登录桌面模式为880×680独立面板：顶部Cancel／居中标题／direct-connection说明／进度，真实手机验证码和本机客户端Cookie两种方式。Mac使用WKWebView；Windows没有随包浏览器内核，因此这里是同等“直接连接”语义的Windows实现，不冒充WKWebView。
- 远程公告在桌面模式使用独立紧凑alert；其他手机页面仍保留原glass对话框。桌面模式的通用玻璃对话框统一改走紧凑Surface并可在小窗口内滚动，因此Windows首次启动法律同意页、连接服务页和云控选择页也使用桌面尺寸，而非手机全屏。
- 首次启动顺序先显示Windows法律同意，再显示Mac三步引导的Connect页；法律同意文本和链接仍是Android/Widows合规内容，不属于Mac源页面。

### 设置及后续页面（正在验证）

- `MeloXDesktopSettingsControls.kt` 独立实现紧凑Form、14pt checkbox、23pt picker／按钮、滑杆；设置作用域内的旧控件调用委派到该独立实现，作用域外手机分支保留。设置外框改为650pt，tab按570／480／620／620／600pt，footer为58pt；取消／确定返回主页，恢复默认经确认后只重置推荐播放器选项，保留布局偏好。
- `MeloXDesktopPodcasts.kt` 对应DesktopDiscoveryView、DesktopPodcastCategoryView和DesktopPodcastDetailView，含真实分类分页、推荐节目、收藏、30条节目分页与新旧排序；Search播客详情也分流至此。
- `MeloXDesktopTools.kt` 独立识曲700×660和一起听620×620窗口；录音／识别、房间创建／加入／结束／邀请链接使用既有真实服务。未登录不能声称房间同步、硬件麦克风已验收。
- `MeloXDesktopAbout.kt`、`MeloXDesktopOnboarding.kt` 对应About／ProjectLicenses和三步引导。Logo、两份指纹notice／license和BeatNet notice按固定Git blob导出；更新指向Windows仓库。引导的beat-effects项尚未接线，因此不能标为完整等价。
- `MeloXDesktopLibrary.kt`、`MeloXDesktopLibraryPages.kt` 独立网易收藏歌曲／历史、歌单／专辑／歌手／播客网格、下载和云盘列表。侧栏Albums／Artists接真实收藏接口，支持打开桌面详情；收藏写入／私人云盘仍需真实账户验收。其他provider音乐库仍需继续替换。

第一轮深浅20设置子页回放成功；控件回放发现滑杆3/4输入的原生thumb几何对应6.5dB，失败证据保留在`native-settings-v1-desktop-controls`。随后改用端点输入独立验证12dB持久化，结果须以最终回放记录为准。第一轮设置通过早于后续页面修改，不能视作最终冻结版全量验收。

仍须继续迁移多provider音乐库／服务页、mini窗口、消息回复／分页菜单及原生材质／动画；以上替换不声称整套Mac UI或同内容整幅像素验收完成。

当前明确的未完成项：Windows登录面板已独立，但没有Mac的WKWebView内核，只复用真实手机验证码／本机客户端Cookie导入；私人收藏、云盘、私信发送和双账号Together需要真实登录账户复测；播放器原生glass、spring和逐页同内容像素仍为PARTIAL。

本批仍有可见残差：Mac原生菜单材质／系统控件动效、歌曲别名／播客描述模型、评论点赞图标与本地化数字、系统分享窗口尚未逐项等价；Windows分享目前复制真实链接。歌手图片异步下载可能在首次截图中仍为占位，不能将这些画面用于已完成的同内容像素验收。非网易provider的歌曲信息暂使用真实搜索回退，不能计作该provider的详情页已迁移。
