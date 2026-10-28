# Android 0.7.1 关于页赞助者

来源为 Android 工作区和纯 Git 内容 `9faacd8fade4861aa416b68dad3b2cffa4d230a7` 的 `SettingsScreen.kt`、默认 strings 与简中／繁中／日文 i18n。两种布局的关于设置页和独立 Desktop About 均展示六位赞助者：user_26d40（CNY 50）、user_aec4c（CNY 6）、Bai（CNY 5）、中末（CNY 8.88）、user_361ef（CNY 20）、user_f8b67（CNY 12.24）。未翻译条目按 Android 默认资源回退。

新增独立资源，没有覆盖既有 pinned 文案。`tools/audit_android_sponsors.py` 对照 Android 纯 Git 字节与工作区，验证四组文案；`evidence/android-sponsors/source.json` 保存源与资源 SHA-256。`verify_pinned_resources.py` 将新资源纳入源码、JAR、app-image、ZIP 字节校验，本地源码校验 38 项通过。

`tools/capture_about_sponsors.py` 通过真实鼠标／滚轮输入打开手机版简中、电脑版日文以及英文 Desktop About 对话框，每种 5 scenes，分别核对六个可见名称和金额；截图、指针输入与断言见 `evidence/android-sponsors/`。对应回放已加入 Windows CI，实际 Windows 构建结果另行记录。
