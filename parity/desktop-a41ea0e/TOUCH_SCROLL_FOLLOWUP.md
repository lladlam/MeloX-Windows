# 触屏反馈后续与搜索 UI 补齐

## 生产代码发现

- AWT 将触屏转为鼠标事件时，Compose 原生列表不会默认处理鼠标按住拖动。原有适配只接在部分 collection 等页面，Home／设置正文／侧栏／部分音乐库、消息和工具页遗漏。
- 横向 Home shelf 和播客分类原先只接受原生 Touch 拖动及分页按钮，缺少 promoted-mouse 横向路径。
- 原适配用 `dispatchRawDelta` 绕过滚动 mutation，不能在整个拖动过程中持续排斥正在运行的滚动动画。
- 桌面搜索媒体结果使用普通 Column，结果超出视口后不能滚动。候选词列表直接参与 toolbar 高度计算，出现时会移动后续内容。

## 修正范围

- `desktopPromotedDragScroll` 支持纵／横轴，子控件已消费的输入仍归子控件；跨轴手势在阈值前退出，拖动结束消费 release。
- 达到阈值后用 channel 向持有 `MutatePriority.UserInput` 的 scroll mutation 传递位移，释放后接续惯性；取消时关闭位移流。
- `desktopVerticalScroll` 让普通桌面滚动列共享同一个 ScrollState；补齐 Home、设置、侧栏、音乐库、服务、许可、菜单、消息、工具和队列的适配。
- 搜索网格改为按自适应列数分行的 LazyColumn；真实候选词改为不抢输入焦点的 Popup，限制高度并支持滚动。

## 验证口径

- `tools/capture_desktop_drag_scroll.py` 在生产设置页发送 Mouse／Touch 双向拖动，读取语义滚动坐标，断言四次实际位移均超过 60px。
- `tools/capture_desktop_home.py --promoted-drag` 检查横向卡片位置变化、纵向父页实际滚动；与原生 Touch／真实公开内容导航一同回放。
- `tools/capture_desktop_search_scroll.py` 用公开目录真实查询 `love`，验证候选词不会移动输入框、专辑网格 Mouse／Touch 拖动均实际滚动。
- 所有输入均为离屏 production ImageComposeScene 回放。**不代表 Windows 数字化仪、物理触屏或长时内存问题已通过验收。**

Windows hosted runner 的原生注入仍需按真实支持能力报告；此前 `UNSUPPORTED` 不能用作本次用户反馈已解决的证据。

## 状态

本地 `build/touch-v28/drag-report.json` 已通过：Mouse 上／下为 0→112→0，原生 Touch 上／下为 0→112→0。`build/search-v28/search-scroll-report.json` 已通过真实目录：网格 0→112（Mouse）→224（Touch），真实候选词悬浮且 toolbar bounds 不变。

回放同时发现原有 settings-general tag 被新增 tag 覆盖，已保留原 tag；公开候选词被错误要求登录 Cookie，已与真源一致允许未登录 catalog 请求并正确传播取消。routes／overlay 回归通过，详见 `SIDEBAR_ACCOUNT_GLASS.md`；触摸键盘新增路由和原生 API 接线见 `TOUCH_KEYBOARD.md`。

独立 mini-player、非网易登录表单、消息分页／菜单及同内容整幅像素验收仍未完成。
