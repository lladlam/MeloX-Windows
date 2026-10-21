# 软件内窗口操作

用户要求移除Windows顶部白色系统标题栏，最小化、最大化／还原、关闭由软件内操作。

- 主窗口使用 `undecorated=true`，软件内34dp标题区跟随应用深／浅主题；三个46dp按钮使用Windows线条图形和关闭hover红色。
- 标题空白区使用Compose的原生 `WindowDraggableArea`，双击切换最大化／还原；最小化写入同一个真实 `WindowState.isMinimized`，继续驱动原有foreground／后台行为。
- 关闭按钮和系统关闭事件共用释放真实播放器后退出的回调。
- 无边框窗口四边5dp、四角10dp提供真实AWT窗口resize；按桌面布局最小窗口约束，不拦截正文拖动。
- 沉浸Fullscreen切回普通placement前先清除Skiko fullscreen标志，避免还原按钮只有状态变更而窗口不恢复。
- 六个页面的图像回放记录客户内容区；软件caption独立于内容区。无原生Window的ImageComposeScene不绘制不能真实操作的窗口按钮。

`auditWindowChrome` 用生产caption组件和真实Window，通过AWT Robot点击最小化／最大化／还原／关闭、双击标题、拖动右下角，再读回真实窗口placement、minimized、bounds、insets；输出深／浅原生截图和JSON。当前Linux显示器尺寸为0×0，原生核验只能记录UNSUPPORTED；Windows CI遇到无有效显示器会失败，不能用离屏回放冒充Windows窗口验收。
