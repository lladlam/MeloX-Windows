# Collection：固定源码差异与验收

真源固定 `a41ea0e1e1fa69fb543d827b853fe3aff5420951`。此页保持 **PARTIAL**；下表区分源码可以直接确定的参数与仍需原生同内容帧的部分。

| 对象 | 固定真源 | 迁移前差异 | 本轮实现 |
|---|---|---|---|
| 页面结构 | PlaylistDetail:27–78 / AlbumDetail:19–47；ScrollView 外为 navigationTitle，内横距42、纵距34，header→divider→track list 各隔34 | 返回工具栏随内容滚动；顶距26；额外搜索和表头 | 固定 Windows toolbar；内容42/34、两侧34 divider 间距；筛选收在工具栏 |
| Header | DesktopRouteView:65–70；278封面、continuous radius11、34间隔、底对齐、details底距6／最大620 | 仅近似实现静态header | 源结构与上限；封面采用已有固定连续圆角几何；加载／错误页不再显示伪完成header |
| 描述 | :135–161；12.5，默认3行；>120字符或换行出现展开，spacing3；240ms smooth | 无展开功能 | 真实展开／收起、可选择文本；240ms，尊重减少动态 |
| 操作区 | :164–251；spacing12；系统bordered／prominent；收藏star／star.fill；更多菜单内下载 | Material最小高度、文字星号收藏、直接下载图标 | 紧凑自绘control、SF收藏、真实下载菜单；原生控件高度／材质需参考帧确定 |
| 曲目 | DesktopTrackRow:25–108；默认无封面、minHeight46、spacing12、左右12、radius8 | 54高带38封面、radius7、额外列标题 | 默认无封面46高；移除表头；title13.5 regular／current semibold，artist11.5、间隔2 |
| 曲目列 | :49 / :82 / :88 / :131；数字24、album最大180、duration42右对齐等宽数字、menu28 | 数字38、album按weight、duration54、menu34 | 按固定列宽，album页不显示album列；专辑真实trackNumber／disc解析与分组 |
| Hover与点击 | :36–54 / :90–108；hover/current显示播放／waveform，菜单opacity1／.16，背景.045，双击行播放 | 单击整行直接播放；无替换图标 | 行双击、数字按钮单击；真实播放态waveform；160ms背景；菜单层级／原生右键菜单仍待迁移 |
| 正文字体 | SwiftUI `.system`，各指定字重 | 沿用Android兰亭，SF资源仅包含图标 | 独立固定官方SF Pro普通字符集，冻结wght／opsz；保留tnum；CJK fallback与CoreText栅格仍待对照 |
| Album全局路由 | Desktop Sidebar navigation column承载详情 | Windows全窗口overlay覆盖侧栏 | 同一可移动详情composition放入Desktop主栏；原布局仍由全窗口slot承载 |

## 证据输入

- `source-audit.json` 对本轮四份详情／曲目源文件记录固定 Git blob SHA；导出真源文案不手写翻译。
- `desktop-text-fonts.json` 记录官方源字体与每个冻结字重／光学尺寸的字节及哈希；`build_desktop_text_fonts.py --source build/SF-Pro.ttf --verify` 可重建逐字节核对。
- 小字号使用opsz17、22pt作者使用opsz22、32pt标题使用opsz28；不继承Material bodyLarge的24sp行高，使用相应SF字体自身的行度量。描述展开阈值按Unicode扩展字素簇计算，与Swift `String.count`口径相同。
- `capture_desktop_collection.py` 通过生产公共网易接口读取真实专辑或推荐歌单；隔离home只存验证偏好，不放mock provider。真实指针点击描述、筛选、更多和返回；semantics只做定位及几何检查。

## 尚需原生帧才能关闭的差异

1. 固定提交、同一公开album／playlist ID、同一实际接口返回歌曲顺序／标题／封面／描述、1280×800内容区、同主题／Locale／滚动／hover／展开状态的macOS帧。
2. 相同device pixel density；不能将Retina截图缩放后宣称逐像素相同。toolbar、安全区和窗口装饰边界需一同标注。
3. macOS系统bordered control、continuous圆角、阴影偏移与扩散、secondary动态颜色、SF实际光学尺寸／字距、CJK PingFang／系统fallback、CoreText与Skia栅格差。
4. 真源snappy 250ms／extraBounce .04的封面动画与smooth 240ms描述轨迹；当前Compose tween只实现时长，未作弹簧曲线一致验收。
5. 所有曲目菜单、分享、编辑、删除、原生右键／拖放与分页；目前歌曲更多仍连接已有真实功能sheet。
6. `Shared/DesktopArtworkView.swift` 的empty态为secondary .10＋13pt medium music.note，failure态为真源MeloXLogo／18pt inset＋quaternary .35。现有共用图片加载器仍合并loading／failure为渐变占位；这两个状态与独立加载footer尚未迁移，不能凭成功封面帧关闭验收。

缺少这些同内容原生输入时，源码参数检查和Windows回放只能证明本轮实现的结构与行为，不能作为像素级完成结论。

取得原生帧后，用现有 `tools/visual_diff.py`，显式传入 `--reference-sha a41ea0e1e1fa69fb543d827b853fe3aff5420951 --expected-sha a41ea0e1e1fa69fb543d827b853fe3aff5420951 --scene collection-<id>-<state>` 生成overlay／difference／heatmap；仅显式裁除已记录的原生窗口装饰，不使用 `--resize-desktop`。同内容／状态仍需核对接口返回及原生capture元数据，不能由图像指标推断。

## 冷启动焦点错误的具体定位

首轮真实album冷启动回放保留了 `FocusRequester is not initialized` 原始异常：`SettingsScreen.kt` 的 `LaunchedEffect(route, equalizer.enabled, lyricsStyle)` 在Desktop提前return分支中仍调用 `detailFocus.requestFocus()`，但该分支没有挂接requester的focusable节点。异常发生在AWT事件线程，后续真实album header未完成加载、模态拒绝按钮也未正常退出。

本轮为DesktopSettingsLayout挂接与旧分支相同的真实focusable容器。修复后独立冷启动首轮20场景／34断言通过，包括明确的 `playing:false` 暂停、恢复、队列选曲、音量、完整播放器与导航。新增 `cold-controls` 保留严格暂停断言，并继续加入真实本地歌词inspector／点击seek回归；任何 `Exception in thread` 会使回放失败。原始失败与后续结果分别留证，不以减少断言作为修复。
