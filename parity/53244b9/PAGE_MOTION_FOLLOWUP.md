# 设置详情页：互补位移与可中断进退场

固定源仍为 Android `53244b9d6b21d7e3a609e8cb11876efef70f218b`。

## 源依据与本批范围

- `ui/MeloXPageTransitions.kt:172–247`：父页只向左退宽度的1/3；布局期绝对坐标place，不缩放、不压暗、不裁剪，分栏列表侧不退让。
- 同文件`:250–374`：声明式Transition保留位置/速度连续性；退场期间保留内容和返回处理器，避免返回键穿透。
- `ui/animation/MeloXMotion.kt`：临界阻尼弹簧ζ=1、k=800，双向满宽push/pop。

Windows 设置页此前保留父列表，却只对详情使用 AnimatedVisibility，父列表完全静止。本批增加 `navigation/MeloXPageMotion.kt`，两页读取同一个声明式Transition进度：

```
父页 placeX = -W * p / 3
详情 placeX =  W * (1 - p)
```

两端以相同进度到位，反向不另启animateTo协程；正常关闭以及推入尚未完成就关闭，都保留原内容直到弹簧结束。特别处理 `currentState == targetState == false` 但弹簧尚在回程的情况。退出中的再次Escape由本层消耗；结束后恢复root焦点。

手机分栏仍使用原有movable composition在右侧呈现详情，左侧列表不退让；桌面设置继续走独立tab布局。减弱动态使用零时长。

本批不代表通用详情、共享封面、搜索分类、0.92预测返回缩放或Lamé平滑角已完整迁移。其余差异仍在 `MOBILE_UI_AUDIT.md`。

## 验证方法

`capture_page_motion.py` 只发送指针事件和Escape，semantics用于定位与断言；`verify_page_motion_capture.py` 可独立重读保存的帧，不运行app。

- 单栏：打开、尚未到位即返回、落定后关闭、关闭途中点击已保存的实际列表坐标重新打开、重复打开和退出中的第二次Escape。
- 双栏：左侧父页始终保持原宽度，右侧详情完整推入/退出。
- 减弱动态：只存在0/1两端，没有中间进度。
- 独立核对实际可见边缘：`父页右缘 = 2W/3 + 详情左缘/3`；不只检查进度标记。
- 整页刚在屏外时semantics的boundsInWindow按窗口裁剪为0矩形，明确核对这一状态。

新增可选 `--controlled-animation-clock`：仅供离屏回放，所有scene.render时间由同一个显式时钟推进；避免软件截图的墙钟耗时使350ms页面在首次采样前就已结束。默认回放时钟保持原行为，协程计时器仍使用实际时间。因此此工具不用于自动隐藏计时或网络延迟验收，也不能证明物理触屏/120fps硬件性能。

Windows CI新增三种模式、真实Local歌词播放器回放及完整输出上传，便于独立核对Windows上的中间帧。
