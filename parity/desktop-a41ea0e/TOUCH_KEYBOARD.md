# Windows 触摸键盘

## 行为

全部 String BasicTextField／OutlinedTextField 入口使用输入行为委派，视觉参数继续传递给原 Compose 控件，手机／桌面分别保留自己的 UI。

- 手指 tap 释放后，等待输入框自身完成聚焦，再请求 Windows 触摸键盘。
- 识别 Compose Touch 以及 AWT MouseEvent 的真实 `causedByTouchEvent` 标志，不以“设备支持触屏”替代“这次来自手指”。打包启动参数为此开放 `java.desktop/java.awt.event` 的反射读取。
- 已聚焦输入框再次 tap 会重新请求打开；鼠标、键盘 Tab 聚焦、滚动拖动、只读与禁用输入不请求。
- 请求不消费控件事件，不拦截文本编辑，也不切换用户布局偏好。

## 原生实现

在 Swing EDT 上使用 Windows 10 1607+ 文档化的桌面互操作：

`RoInitialize → WindowsCreateString → RoGetActivationFactory(InputPane, IInputPaneInterop) → GetForWindow(HWND, IInputPane2) → TryShow`。

`IInputPaneInterop` 继承 `IInspectable`，GetForWindow 为 slot 6；IInputPane2 的 TryShow 为 slot 6，WinRT boolean 为一字节。COM／HSTRING 均在 finally 释放，平衡成功的 RoInitialize；无焦点窗口、非 Windows、headless 不调用系统 API。

不使用 Toggle（避免重复点击关闭键盘），不修改系统键盘策略。原生调用记录 HRESULT 与 accepted，不记录输入内容、Cookie 或密码。

依据：
- <https://learn.microsoft.com/en-us/windows/win32/api/inputpaneinterop/nn-inputpaneinterop-iinputpaneinterop>
- Microsoft windows-rs `Windows/UI/ViewManagement/mod.rs` 的 IInputPane2 IID／ABI 定义。

## 验证口径

`capture_desktop_search_scroll.py` 向生产搜索框发送 Mouse tap、Touch tap、已聚焦 Touch 再 tap、Mouse 再 tap，检查请求数为 0／1／2／2，同时验证实际输入、真实目录请求和网格滚动。

本地 `build/search-v31/search-scroll-report.json` 已通过上述 0／1／2／2 路由检查；随后输入 `love`、加载真实候选词／专辑和两种指针滚动均通过，滚动坐标为 0／112／224，拖动期间请求数保持 2。

离屏记录仅证明**请求路由**。Linux 不启动 Windows 键盘；本轮实际 Windows 触摸键盘显示、输入、收起后重开及物理触屏仍需实机验证，不能把请求计数当作显示通过。
