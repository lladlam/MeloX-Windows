# Dock / 侧面歌词：统一 MiniPlay 液态玻璃

按用户本轮要求，桌面左侧Dock、右侧歌词/队列栏共用Android MiniPlay材质。

## 改动

- 移除桌面面板单独的24%/34%表面色、12dp模糊与半强度折射，直接复用 `meloXLiquidBottomBar` 和 `bottomGlassSurfaceColor()`：0.52表面涂层、2dp模糊、vibrancy、24/24dp边缘折射及相同高光/内阴影。磨砂设置仍走同一个MiniPlay的8dp无折射分支。
- 左Dock位于实际页面外侧，原来的同位置采样无法取到内容。现在沿Dock右边缘镜像取样右侧真实页面；把相邻180dp内容压缩到28dp侧面，再向内渐隐，不使用预制渐变、截图或假反光。实图复查后取消整页镜像，避免倒字和封面铺满导航。
- Dock布局仍在左侧，但绘制排在页面录制之后，避免Row先画Dock时读不到当帧页面。
- 右侧歌词/队列沿左边缘取相邻页面实时反射，颜色和材质与左Dock来自同一函数。两侧正文区铺同一中性底色，不透出可读的页面文字；仅侧面带承接动态反射，保持导航和歌词可读。
- 页面背景录制覆盖当前内容与collection详情，但排除Dock、歌词/队列、底部播放器等采样者。打开详情时反射详情，不停留在旧首页；保持无递归录制。

固定Android `53244b9` 依据：`ui/player/MeloXIOSMiniPlayer.kt:475–490`，`ui/glass/MeloXBackdropComponents.kt:82–142,382–456`。

## 验证

新增 `:desktop:auditDesktopGlass`：实际生产面板搭配明确标注的光学测试色块，页面只绘制在Dock右侧，Dock自身下面没有色块。页面由蓝变琥珀时检查两边真实像素实时变化，同位置对比两块材质一致，并确认销毁后filter owner释放。

1×/2×密度、深/浅主题、液态/磨砂共 **8 cases PASSED**；每例两侧超过95%的被测侧面像素随实时页面改变；正文区不被页面色块污染，相同材质像素超过95%。完整输出 `desktop/build/verification/desktop-glass/`；CI已接入该审计并上传原始PNG/JSON。

真实首页滚轮回放：深/浅主题各8个场景，独立 `verify_desktop_panel_reflection.py` 按实际semantics边界重读PNG。深色Dock/歌词侧面变化97.63%/97.52%，浅色97.93%/96.09%；两次Dock正文区均0像素变化。参数、截图、语义与原始像素报告在 `evidence/desktop-panel-glass/`。另保留真实Local歌词已显示的深色生产截图。

## 完整歌词回放补验

早先v58的深浅完整回放失败，不能按通过计数：目标`lyric-line-1`未创建时，通用reveal把滚轮发送到窗口中央，滚动的是首页；浅色另有一次`AudioFailureKind`类加载失败。

修复验证工具：reveal可显式指定所属面板和初始方向；歌词回放指向`player-lyrics`并向上滚动。可选原子鼠标点击用于找到目标后立即发送Move/Press/Release，防止中间截图耗时超过生产歌词自动跟随延迟。仍通过实际指针事件点击，没有调用semantics动作或修改生产滚动/跟随逻辑。

串行重新运行：v61深色、v62浅色各 **10 scenes / 18 assertions PASSED**，包含真实Local歌词、点击第二行跳转、恢复播放、队列/歌词切换与关闭。类加载错误未再出现；只能确认本次串行构建/运行通过，旧类加载错误原因尚未独立复现。v61浅色曾被外层300秒总超时中断，不计通过，使用单独完成的v62浅色结果。

原始完成清单、目标行点击帧及语义保留在 `evidence/desktop-panel-glass/full-lyrics-replay/`。
