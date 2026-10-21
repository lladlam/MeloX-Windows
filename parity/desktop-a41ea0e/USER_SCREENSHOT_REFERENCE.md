# 用户原始截图：1783e775

本轮用户提供 `MeloX-Desktop-Windows (5).zip`，完整CRC验证通过，SHA-256 `a5784095a5050220808d630045ec83cbd622ce2835a64791df52635bf20985d7`。共六张原始PNG：

| 图 | 原始像素尺寸 | SHA-256 |
|---|---|---|
| account.png | 2188×1356 | e553ec08f86422bed8cdbb7c8febee1ad185ee9fc13737734b4807d9672df6bf |
| home.png | 1960×1184 | f2c2fa9fc30425513cb8a5b90569d140c520efb10f7a1413fda45aca70dd3c54 |
| immersive-player.png | 1960×1184 | 89cec608c79da182e02bb7c41ac04d131e9baeb0a3a9344257a4bcc98af6b6a2 |
| lyrics-pane.png | 2414×1356 | e6c559e285ec4137d0c713c412607a579bc025d4449773fee93aaf3865779f74 |
| playlist.png | 1960×1356 | aac558cd59d005aeb24dbf1b2805183f2f9ab15f42d816685adb9137c123301f |
| queue.png | 1960×1184 | d457073b0968f91634d2fbf4613bf8cbd7c953ffedc892a4ec2408129f2821a0 |

原图在上传目录及本地忽略目录 `build/desktop-reference-user-1783e775/`；账户／私人内容不加入公开仓库。原图没有内嵌density／色彩／版本元数据，不能仅凭文件名确认真源提交。画面中macOS标题栏、窗口圆角和控件按Windows窗口能力适配，不绘制伪系统交通灯。

## 本轮：复用第一套能力，纠正沉浸播放器

复用当前 `MeloXNowPlaying` 的真实背景、歌词加载／逐字渲染、播放器控制、图片加载、Local／provider收藏读写、歌曲操作面板、队列entry ID与PCM输出；桌面层负责布局与交互样式。

1. **封面基准错误**：固定源码 `DesktopNowPlayingArtwork.swift` 的214／308等尺寸是暂停slot；播放时scale为 `1/.74`。旧移植误用播放1／暂停.74，导致同窗下封面线性尺寸偏小26%。修正后不改变metadata位置，连续圆角11、480ms尺寸过渡；Skia shadow仍需macOS光照／偏移对照。
2. **可独立交叉验证的原图坐标**：1960×1184截图中的封面约 `[207,113,785,692]`，误差±1px。按source、980×592pt、scale2计算，播放封面宽578.378px，中心 `[490,402]`，边界 `[200.811,112.811,779.189,691.189]`。大小／纵向一致，但原图水平中心偏右6px；保留这一残差，不把source参数通过标成图片像素一致，也不从一张截图推断任意窗口都应硬移3pt。density2由大小／纵向支持，版本与窗口裁界仍需确认。
3. **Metadata**：使用SF Pro正文，自身行度量、18bold／14medium、spacing5；真源分隔符纯导出；补28圆形收藏和更多、13semibold符号、white.12背景／white.10描边。收藏仅由现有真实reader／writer返回成功后更新，错误可见；本地文件收藏用扫描库持久化。打开已有操作sheet后返回重新读取状态。
4. **进度**：移除原2pt／1.5%内缩的底栏细线；采用全宽8pt capsule，矩形进度fill、white.22底色。真实scrub时扩至18pt，320ms cubic(.25,.10,.25,1)，松开400ms cubic(.20,.80,.20,1)；时间10pt等宽数字／white.82／顶距3。真实拖动、点击、时长与seek沿用AudioPlayer。
5. **Transport**：按固定源码28／36／48／36／28宽、16／24／30／24／16字号、semibold，跟随elementScale；共享原控制的真实shuffle／repeat／切歌／暂停。
6. **切换器**：72×36底板／92×56占位、32选中圆、左右中心±18，300ms选中移动；再次点当前歌词／队列会切到居中封面页，恢复三态而非旧移植的Boolean。独立desktop页面偏好按现有“记住播放页”开关持久化。
7. **图与固定source有差异的Queue**：用户queue图模式条紧接48pt chrome，选中为浅色底／深色字并带标题。固定a41ea0e仍为38pt额外top／红色选中／纯图标。此处按用户新图覆写沉浸模式；inspector保留固定源码颜色／top规则。真实AutoMix与autoplay能力不变。用户图“来自”上下文需要真实queue provenance，目前未接入，不用当前专辑伪装来源。

## 验证边界

`capture_desktop_layout.py reference-player` 以1960×1184、density2直接生产渲染，真实本地扫描WAV／LRC、指针／拖动／键盘、不注入假播放状态；再以 `reference-player-reload` 独立JVM验证本地收藏持久化并还原。`verify_desktop_player_capture.py` 独立核对原图SHA、播放／暂停／居中封面几何、控件存在与实际PCM回放。

本地验证媒体与用户Babel曲目／歌词／背景内容不同，不能拿整幅Windows自图或缩放图替代原图逐像素验收。尚需匹配真实曲目与播放时刻、背景动态相位、窗口装饰裁界，输出原尺寸overlay／difference／heatmap；mini窗口入口、原生材质／阴影、bounce／spring与逐字歌词曲线仍未完成。

## 六图共用页面补充

- Home完整结构、真实推荐内容、两主题、分页和触屏交互见 `HOME_PARITY.md`。
- Account按固定source使用500×600 sheet、28标题／20组边距／12组间隔、42行、chevron11、完成80×30／边距16；深色底直接采样原图 `#312E2E`，组表面近 `#393636`，浅色独立配色。回放保留真实未登录状态，不借用上传图片内的身份。
- Playlist复用既有真实Collection页面；顶部返回改36圆形／52 toolbar，操作钮24高，作者和辅助操作改原图coral `#FF3B45`；278封面、42内容边距、34图文间距和46曲目行保持固定source结构。新增1960×1356／density2回放。
- Lyrics pane原图边界为x=1694至2414，宽720px；按density2提供360dp阅读面板，覆盖behind page，保留队列的固定258dp。面板从真实page backdrop采样24dp blur，再叠主题surface透明层和左侧细分隔线；歌词／队列crossfade280ms，减少动态为0。歌词活跃行定位到面板高度约1/4，与新图相适配，不再使用旧窄panel的69.734pt anchor。
- Queue新增实际LazyColumn scrollbar和底部72pt fade，原图浅色模式选中、文字、34封面／48行及真实操作沿用上文；沉浸右栏相对50%分界额外32pt前留白，模式条9／20内距、58pt header、14pt封面内距和60pt文本分隔线起点，对齐原图模式起点约x1063及首封面约x1072。继续播放的“来自”只在存在真实来源数据时才可实现，当前不以歌曲专辑伪造queue provenance。
- 软件内窗口按钮独立记录在 `WINDOW_CHROME.md`；桌面设置保持桌面布局并与第一套功能共用，见 `SETTINGS_FUNCTION_PARITY.md`。

上述实现和回放不是六张私人图片的同内容全图像素验收；逐项可核验的几何／主题／输入结果另见完成日志，差异保持可见。
