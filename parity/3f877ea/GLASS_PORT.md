# 液态玻璃移植与性能检查

基线：Android `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`。实现状态与 Android 同内容像素/动态验收分别记录。

## 真源与版本

- pinned `ui/glass/MeloXBackdropComponents.kt`：材质、普通控件、底栏面板、隐形捕获层、水珠及数值出口。
- pinned `ui/glass/publicdemo/PublicInteractiveHighlight.kt`：光斑着色器、弹簧参数、拖动观察规则。
- pinned 依赖 `io.github.kyant0:backdrop:2.0.0`；Maven Central sources JAR SHA-256 `8277e21a2dc270931a3dfb175805dac5303f541e20e01bf05d1a714e8f6c032a`。
- 上游 `effects/Lens.kt`、`effects/ColorFilter.kt`、`internal/Shaders.kt`：圆角 SDF 透镜、七采样色散、饱和度 1.5 的 vibrancy。桌面使用当前 Skia/Compose 1.8.1 实现，未引入要求 Compose 1.11 的上游二进制。
- pinned 形状为 `capsule:2.1.3` / `shapes:1.2.0`。构建只解析 sources JAR 并验证 SHA-256，移除 Android 范围注解，将 Capsule 的 Android Path/RectF 调用适配为 Compose Path/Rect；保留完整曲线算法、G2 profiles 与 Continuous/Capsule 类型。哈希、原仓库和修改说明随应用资源打包，附完整 Apache-2.0 许可证。
- Context7 月额度耗尽，API 已通过官方版本源码及本地 Skiko 0.9.4.2 类签名核对。

## 本轮修复

1. **真实折射**：原实现只平移半透明白色层，不能改变采样坐标。新增 Skia runtime image-filter 链，将真实背景振色、模糊及透镜折射/色散组合；不是用高光冒充透镜。
2. **按压单位**：原 `48px / controlHeight` 与真源 `4.dp.toPx() / controlHeight` 不同。修正材质和内容同步变换；progress 出口限为有限的 `[0,1]`，位移非有限值清零。
3. **捕获层与水珠**：面板固定 24dp 透镜；捕获层只在按压时开启 24dp×p 的透镜；水珠为 8.75dp×p / 12.25dp×p 并组合页面与实际捕获层。静息时跳过 lens，避免零除。
4. **按压光斑**：以 pinned smoothstep shader 替代线性径向渐变；焦点限制在控件内，shader 首次按压才编译。只读拖动观察器不因内层消费移动而取消高光。
5. **稳定帧资源复用**：背景 sampler 用 `drawWithCache` 按尺寸、密度、圆角和光学参数构造滤镜；位置与背景仍在每次 draw 时实时采样。单个 sampler 仅拥有一条当前链，参数变化显式释放旧链，离开组合释放 shader/滤镜；不以无限动画帧缓存换内存。
6. **完整形状与装饰**：连续圆角不再用 RoundedCornerShape 替代。Generic outline 从真实 shape 提取角半径，仍使用上游同款圆角 SDF；不是用路径有无来跳过 lens。法线 rim/环境高光、偏移阴影及内阴影替代旧渐变 rim；稳定参数只录制一次装饰层。
7. **变换层级与开关**：完整材质和内容一起按压变换，frosted 按 pinned 关闭液态变换和光斑。开关具备真实页面/轨道组合采样、5dp×p / 10dp×p 色散透镜、环境 rim、4dp 外阴影；确认/拒绝及拖动后的真实 checked 仍拥有静息外观。
8. **光斑预乘修复**：真实组件截图发现白色光斑覆盖过亮，按 Backdrop Skiko `setColorUniform` 对 RGB 乘 alpha，防止直接输出未预乘白色导致 Plus 混合饱和；组件检查加入中心过曝回归。
9. **装饰滤镜所有权**：rim/内阴影/外阴影的 blur 也使用显式 owner；透明边界为 DECAL，采样背景为 CLAMP。仅 alpha/形状变化时复用同一 blur，半径变化替换并释放，移除组件时先清除 layer 引用再释放 native chain。诊断计数仅表示这些 owned final chains，不能代表所有 Skia/GPU 内存。
10. **专用滑杆与采样坐标**：滑杆恢复 10dp×p / 14dp×p 色散透镜、组合轨道取样及 `(2/3→1, 0→1)` 轨道变换、环境 rim 和 4dp 阴影；frosted/无背景使用真实 Material Slider，支持 enabled，默认连续步长为 0。sample 使用 source-local→control-local 映射抵消按压/祖先缩放，防止背景跟控件一起被重复放大。

## 验证

- `:desktop:auditGlassOptics`：真实 Skia checkerboard raster；缓存模糊与独立模糊逐像素一致，零透镜等于无透镜，透镜/色散/vibrancy 分别确实改变背景像素，中心不产生透明采样；稳定 600 帧的链复用与所有 owned chain 释放计数。
- 微基准使用固定图片/尺寸/帧数，分别执行逐帧构造与复用；记录耗时，不将其称为生产 FPS 或 Windows 内存修复。
- 固定输入的时间差同时包含 Skia 滤镜结果复用，不是“滤镜构造单项提速”测量。可严格确认的是相同参数下 600 帧新链创建数为 0；64 owners×16 参数变化/转为无效果/重复 close 都完成链释放。
- `MeloXGlassSceneAudit` 用明确标注的生成棋盘背景包裹真实生产组件，物理指针操作覆盖按压、点击、拒绝的开关拖动、背景更新、重复挂载/销毁及密度 1/1.25/2、明暗/frosted；不向生产 app 注入假歌曲或假播放。观察常驻内存只记录数值，不对短跑平台期做断言。
- 新增专用滑杆 tap/drag/disabled 与非均匀缩放下无光学采样的逐像素对齐检查；每个 scene 销毁后 owned owner/chain 计数必须回到创建前的基线。最终结果以 `glass-scene/audit.json` 为准。
- 生产页面仍需鼠标/滚轮/键盘回放与截图检查；Android 同内容截图/帧尚未采集。不能因 shader 测试通过就标记 1:1 完成。
- 本地最终结果归档 `evidence/glass-optics-local/`：12 组真实组件检查通过；每组 scene 关闭后 owned owner 与未释放链均为 0。设置 74/55、reload 8/18，Bilibili 10/15 + reload 6/6，底栏明暗各 7/9；数字依次为 scenes/assertions。全量 shared XML 为 253 tests / 51 suites、零失败/错误/跳过。Windows 新 CI 和完整发行独立验证另记。
- Windows run **39140967234 / head 4361c98 / SUCCESS** 完成同款 optical 和 12 组组件检查；600 稳定帧新链为 0、每个 scene 释放后 owned owner/链余额归零。独立下载重新汇总为 **253 tests / 51 suites** 及 **18 组生产回放 / 731 scenes / 1,278 assertions**，全部通过。发行 ZIP **333,065,608 bytes**、SHA-256 `2bdc3803d1a37d83fa2a81f3704cb9c33d9fd0b2e2aa12436e2442d414b63297`，ZIP/JAR CRC、版本/资源和显式 Windows PE 解码器检查通过；新形状/光学类及许可证已确认打包。证据 `evidence/windows-ci-4361c98/`。这些是 Windows CI raster/组件/打包证据，仍不替代 pinned Android 或用户设备 GPU/FPS/常驻内存验收。

## 尚待补齐或验收

- 普通/环境高光、阴影及连续形状的代码迁移已落地；Android 同内容像素/动态对照仍未完成，不能把桌面 checkerboard 自检当作 pinned 像素通过。
- slider/switch/segmented/dropdown/sheet 的全部状态与原生 Popup/Dialog 坐标/组合层级仍需逐一核验。开关继续保留桌面已验证的状态所有权/拒绝回弹逻辑，其运动曲线还需 pinned 动态对照。
- Windows 原生 GPU 帧时分布、真实 DPI/resize、长时播放与 549→1431MB 报告尚未完成。

## 组件接线审计

| 组件 | 桌面消费者 / 本轮接线 | 验收边界 |
|---|---|---|
| Button / IconButton / Toolbar / TextField / Card | 共享真实 glass surface、连续形状、rim、内外阴影；Plain/无背景保留 pinned 分支 | 组件 fixture 包含实际 Button；真实页面回放覆盖导航/输入/按钮，未完成 Android 帧对照 |
| 底栏面板、隐形捕获层、水珠 | 独立材质、正确的 p 门、页面+捕获层组合采样；捕获层不烘入阴影 | 生产 tab 交互仍需同内容动态对照 |
| Toggle | 页面+轨道组合采样、环境高光、5/10dp×p 透镜；真实 checked 拒绝回弹 | fixture 覆盖按压、tap、消费移动后的拒绝 drag；桌面状态动画保留现有曲线 |
| LiquidSlider | 独立轨道录制/变换、10/14dp×p 透镜、环境高光与 enabled/frosted 分支 | 组件 fixture 验证真实 tap/drag/禁用；同内容动态帧仍待验收 |
| Segmented | 实际共享 Button/surface 和 pinned capsule 形状 | 选中切换/Popup 遮挡与 pinned 动态帧待验收 |
| Dropdown | 内容组不是玻璃；选择弹层使用真实 shared surface 和原生 Popup | 四 Locale 的路由/选择/偏好检查可验证行为，不能证明跨窗口采样坐标等价 |
| Sheet / Dialog | shared surface、pinned 非对称 sheet 形状；Dialog 内容按真源保持不透明 | 原生 Dialog 坐标/背景、dismiss/拖动及 Android 帧对照待验收 |

## Skiko 计数语义

官方 [Stats.jvm.kt（0.9.4.2）](https://github.com/JetBrains/skiko/blob/v0.9.4.2/skiko/src/jvmMain/kotlin/org/jetbrains/skia/impl/Stats.jvm.kt) 的 `allocated` 对 `onAllocated` 加一、`onDeallocated` 减一。因此它是统计开启期间尚未平衡的包装对象分配/释放计数；不是字节数，也不能单独证明某个 UI 强引用持有者或常驻内存归因。历史短跑差值不足以证明永久平台期。
