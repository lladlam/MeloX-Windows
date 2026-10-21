# 六图适配：本地生产回放

实现提交：`ecfd6e2`。各回放在本批工作树实现期间顺序执行；验证器／说明与原始capture分别保存，不改写capture中的原始来源字段。

这里保存生产 `ImageComposeScene` 的原始输入／参数／日志、断言、选定PNG及semantics、独立几何报告和测试XML。上传的六张私人原图、用户账号、音乐文件不在公开证据中；原始参考只按SHA和坐标记录。

- `six-ready-*`：最后一轮Home／账户、播放器／队列、独立收藏重载、歌词面板回放。
- `six-glass-lyrics*`：最终深／浅歌词面板，实际page-layer blur且严格裁剪面板边界；替代较早`六图`回放里的采样偏移版本。
- `six-final-playlist`：1960×1356／density2的真实公共网易歌单，真实键盘过滤／恢复、菜单和返回。
- `settings-equal-v2`／`settings-equal-light`：保持桌面布局的20个真实设置子页、重置入口、真实未登录状态与键盘搜索；英文深色／中文浅色。
- `intermediate-player*`：修正沉浸队列横向几何之前的中间成功结果，**不代替最终六图证据**。
- `original-failures`：最初纵滚、账户Escape、设置横向溢出、浏览源被Local回放覆盖、移动歌词目标，以及编译／验证器原始失败。保留原样，不改写为成功。
- `desktopTest`：57 suites／276 tests，failures／errors／skipped均为0。
- `verification/window-chrome.json`：本机原生显示器0×0，只能记录UNSUPPORTED。Windows CI的`auditWindowChrome`会通过真实Robot指针读回窗口最小化／最大化／还原、双击、resize和关闭回调；无有效显示器不会伪装通过。

PCM原始文件保留在忽略的 `desktop/build/parity/`；这里的manifest只记录字节数／SHA，Windows CI另上传实际PCM。生成WAV和LRC经过真实Local扫描、歌词存储、FFmpeg和软件PCM端点，未注入远端假数据／假播放。

这些结果验证Windows实现、真实输入／状态、源／原图几何和深浅主题；不声称与私人原图同账号／同曲目／同播放时刻的全图像素一致，也不代替硬件听感、Windows真机触屏、原生材质／字体／弹簧曲线或549→1431MB长时内存回归。

`summary.json` 独立重计最终9组 **312 scenes／241 assertions**；中间成功和原始失败均不加入这一总数。
