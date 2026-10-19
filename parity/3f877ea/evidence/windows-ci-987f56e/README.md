# Windows 987f56e 独立核验

- Run: <https://github.com/lladlam/MeloX-Windows/actions/runs/39222447680>
- Source: `987f56e9c1c391f4ef70010c98149cd49652c919`
- 完整下载两个 artifact 后，分别验证外层 ZIP 所有条目 CRC 并解压；下载字节数与 GitHub 元数据完全一致。大小、SHA-256 及结果见 `artifact-downloads.json`。
- 在下载内容上独立运行 `tools/record_ci_evidence.py` 和 `tools/verify_delivery_artifact.py`，XML 汇总和主包结果分别见 `windows-tests.json` 与 `download-verification.json`；后者还调用 pinned 资源与显式 Windows 解码器检查。
- `production-replays.json` 从 18 个下载的 `capture.json` 重新累计 scenes/assertions 并逐条检查 passed；`replays/` 保留原始 capture 和 UTF-8 参数/输入文件。
- `glass-optics.json`、`glass-scene.json`、`glass-popup.json` 和 `immersive-window.json` 是 Windows runner 原始结果；截图保留在 `glass-scene/` / `glass-popup/`。`packaged-glass.json` 从下载的应用 JAR 重新读取更新类/形状/许可证并计算哈希。
- 这些结果不代表 pinned Android 同内容视觉验收，也不代表真实用户设备的长时播放、GPU 帧时或常驻内存问题已解决。原生 Popup/Dialog 跨窗口采样及物理播放器输入仍待实机验收。
