# Windows CLI

在 MeloX 的「设置 → 测试功能尝鲜」开启 **CLI 模式**，保持 MeloX 运行（窗口可以最小化到托盘）。发行包中的 `melox_cli.py` 需要 Python 3；在 PowerShell 中运行：

```powershell
python .\melox_cli.py status
python .\melox_cli.py queue
python .\melox_cli.py pause
python .\melox_cli.py play
python .\melox_cli.py next
python .\melox_cli.py previous
python .\melox_cli.py stop
python .\melox_cli.py seek 30000
python .\melox_cli.py volume 0.5
python .\melox_cli.py shuffle on
python .\melox_cli.py repeat all
python .\melox_cli.py search "歌曲或歌手" --source netease
python .\melox_cli.py search-play "歌曲或歌手" --source local --index 0
```

`seek` 单位为毫秒，音量为 0–1，循环为 `off`、`one` 或 `all`。来源使用 MeloX 的存储 ID，如 `netease`、`qq_music`、`local` 或 `youtube_music`。搜索播放使用实时搜索结果，不保证音源提供实际播放权限。命令返回 UTF-8 JSON，成功退出码为 0，失败为 1。`stop` 停止解码并保留队列；再次 `play` 会重新解析当前歌曲。

端点仅绑定本机回环地址，默认关闭；启动后生成的随机凭据由当前 Windows 用户的 DPAPI 保护。请在运行 MeloX 的同一个 Windows 用户下运行命令。关闭 CLI 或退出 MeloX 后端点即失效。源码工作区中脚本位于 `tools/melox_cli.py`；`--home` 可以指定独立测试用户目录。
