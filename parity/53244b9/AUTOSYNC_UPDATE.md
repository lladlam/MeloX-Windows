# AutoSync 逐词时间轴迁移

参考 Android `7c17c00` 的 AutoSync：设置页提供两套布局的本地生成开关及模型管理，模型索引固定到 `android-models-v0.1.0-beta.4` 并校验 SHA-256。下载在应用自有暂存目录完成，逐文件验证大小和哈希后发布；取消或下载失败保留旧模型，更新和删除不会清除目录内的无关文件。部分模型文件缺失时可以重新安装修复。模型安装与推理串行，避免 Windows 中替换正在使用的文件。

对于有行时间、没有现成逐字时间的歌词，播放器复用实际选中歌曲的播放来源及请求头，通过随包 FFmpeg 生成受时间与大小约束的本地 16 kHz 单声道 PCM，再使用 ONNX Runtime CPU 模型执行英文 CTC 或中文 CIF。结果按歌词、音频及模型身份隔离缓存，保留翻译和未成功对齐的行；没有可用模型、无法映射原文、解码或推理失败时保留原始逐行歌词。普通提供方 LRC 即使质量字段为 Fallback，也依据真实行时间标记参与生成；没有时间标记的纯文本和已有精确音节的歌词不参与。

本地以发布版英、中、日三套模型和固定合成 WAV，验证安装、真实 ONNX 推理及与独立 Python CTC／CIF 参考的数值和帧位置一致。英文和中文还通过真实桌面播放器、生产 FFmpeg、已安装模型、LRC 时间轴及缓存重载组成端到端回归；生成期间切换队列身份会丢弃旧结果并保留缓存。定向回归 13 suites／62 tests 均通过。软件 PCM 不代表物理听音。模型的逐词准确率尚未与人工真值验收，界面明确提示 beta 边界。

日文发布模型的 ONNX 契约已验证，但 Windows 播放歌词所需的 OpenJTalk 原生 G2P、NAIST 词典下载和原文字符映射尚未接入，界面明确显示不可生成日文逐词歌词。原生前端正在隔离目录验证，不能将单独模型推理视为日文完整功能。

隔离原型现已用 Windows JRE／JNA 在 Wine 中加载 Windows PE 前端，打开包含中文路径的真实 NAIST 词典，并通过 20 组 Android／Python 日文逐字符参考向量，记录见 `evidence/autosync/openjtalk-prototype/windows-wine-jna-golden.txt`。Wine 环境自动解包 JNA DLL 时崩溃，预解包后通过；这不是 Windows 原生机或生产接线验收。

Windows CI 配置了真实模型契约、双布局设置回放和新发行包的 ONNX Windows x64 DLL 字节核验；实际 Windows CI 及完整下载后的独立发行核验结果另记。

本地四组双布局真实指针／滚轮回放全部通过：controls 各 19 scenes／23 assertions，独立 JVM reload 各 6 scenes／9 assertions。截图、输入轨迹、断言和测试 XML 保存在 `evidence/autosync/`。

Windows CI `40820522890` 和 `40820485793` 在真实模型测试的英文 CTC 固定 Linux 帧断言失败，两者实际输出均为 `23, 30, 49, 88, 105`，未生成发行包。模型和 WAV 哈希验证通过，具体差异原因尚待确认。为避免把不同 CPU 的量化模型数值当作固定真值，改为在同一 runner 上单独用 Python ONNX Runtime 与 NumPy 参考实现生成对齐结果，再严格比较 JVM 结果、总分及播放器时间线；Linux 上新参考复现此前独立参考的英、日、中文结果。失败日志见 `evidence/autosync/windows-ci-40820522890/`，Windows 新结果另记。

Windows CI `40821926845` 第一次运行中，英、日、中文实际 ONNX 与同机 Python 参考一致，英文和中文真实播放器／FFmpeg／模型／歌词缓存链以及切歌丢弃旧结果均通过。随后既有原生窗口标题栏回放在首次最大化点击后超时，按原断言重跑仍在同一位置失败，故未生成发行 ZIP；两次窗口状态与模型日志见 `evidence/autosync/windows-ci-40821926845-attempt1/`。回放现先以真实鼠标点击激活窗口并确认焦点，失败时额外保留屏幕截图；各标题栏操作断言不变，修正后结果另记。

后续 `40825157161` 增加焦点记录后仍失败：AWT 报告窗口有焦点，但失败截图显示 GitHub runner 终端遮住了窗口和鼠标目标。故回放期间将测试窗口临时置顶以防遮挡，结束时恢复；所有真实鼠标操作和状态断言保留，Windows 重跑结果另记。

`40826615533` 的 Windows 原生标题栏鼠标回放已完整通过（含最大化、恢复、最小化、拖拽缩放和关闭），但后续公开推荐歌单的歌手路由断言失败，未产生发行 ZIP：首曲缺失歌手 ID，生产逻辑实际进入歌手搜索，而原回放只接受直接详情页。截图、输入步骤及窗口验收记录见 `evidence/autosync/windows-ci-40826615533/`。现对这两种真实路由分别验证，并在搜索回退时以鼠标进入实际结果，再核对歌手详情和返回路径。

本地已验证两条路由：正常公开歌单的直接详情回放 38 scenes／45 assertions 通过；对 CI 同一真实公开歌单 `465389876` 的首曲，鼠标打开搜索、点击真实歌手结果、进入详情并返回搜索页通过。证据见 `evidence/autosync/artist-route-replay/`。带触摸拖动的本地完整回放在歌手步骤之前的触摸返回断言失败，Windows CI 的触摸断言仍保留并需重新运行。

Windows CI `40834891150` 对提交 `35a35f65a4f2ed8a0e53b59b87781d1e9be46bfc` 全部通过，包括真实模型、标题栏、触摸、歌手路由、双布局回放及最终打包。完整发行 ZIP 已重新下载并独立验证：392,620,515 字节，359 个条目，SHA-256 `e44db45ed72ee5f2b2d1122b2c7052ebb8773f24fc205358689ca8fb5e58abd9`；ZIP／JAR CRC、版本戳、图标、固定资源、FFmpeg、CLI、LX V8 与三枚 ONNX Windows x64 DLL 均通过。详细记录见 `evidence/windows-download-40834891150/verification.json`；该离线核验不代表本机物理 Windows 执行或物理听音。

日文基础组件已迁入源码：NAIST 词典固定哈希下载、有界解包、损坏修复及互斥管理，OpenJTalk G2P 原生源码、JNA 包装与 Android 字符映射。真实发布词典已通过安装、完整性及移除契约；仓库内 Linux 前端通过 20 组 Android／Python 逐字符向量，Windows 交叉编译前端在 Wine 中以 Windows JRE／JNA、中文词典路径通过同组向量。词典管理及原生前端尚未接入设置和播放器，日文生成仍不可用；Wine 结果不是原生 Windows 验收。

随包前端正在接线：固定哈希的 Linux 和 Windows x64 原生库、许可文本、校验提取及独立发行包检查已加入；Linux 使用真实归档安装的词典和随包库通过同组向量。Windows JRE／Wine 组合在 `File.createTempFile` 处中断，独立最小 Java 程序也复现该环境问题，故此前直接 DLL 加载的通过结果不代表随包提取路径通过。Windows CI 新增真实发布词典、非 ASCII 路径和随包 DLL 的完整向量契约，结果另记；设置与播放器接线仍待完成。

Windows CI `40893615496` 成功，完整发行 ZIP 独立核验通过：394,991,167 字节、359 条目，SHA-256 `9daeb01c69c15525456db7a102bfb0f5f99b28e84788180cac64c8ba4108efdc`，版本戳为 `4d8de3fd661f73e7959a59c6ef56cd4cae7ecedb`，详见 `evidence/windows-download-40893615496/`。后续 CI `40896173761` 的 Windows 原生真实词典／随包 DLL 及 20 组向量契约通过，但 AutoSync 桌面设置回放第二次确认点击失败，未产出 ZIP。现场截图显示模型索引请求完成后加载行消失、按钮位置上移，原点击坐标随之失效；证据见 `evidence/autosync/windows-ci-40896173761/`。回放现在等待索引加载结束并重新定位后进行真实点击，本地桌面 controls 回放通过 20 scenes／24 assertions，Windows 重跑结果另记。

2026-10-30 日文生产接线：设置可独立下载／删除 NAIST 词典；播放器在词典完整时通过随包 OpenJTalk 读取并将音素 CTC 对齐映射回原字符。对于不能可靠归属发音的字符或模型词表不支持的音素，保留原行时间；缓存身份包括词典可用状态。日文、中文、英文真实 beta.4 模型均通过同机独立 Python ONNX 参考比较；真实播放器→生产 FFmpeg→真实词典和前端→日文 ONNX→歌词时间轴和缓存重载通过。安装前及删除词典后均确认保留原始行时间；英文切歌丢弃旧结果契约通过。桌面和手机版词典设置真实指针回放各 23 scenes／29 assertions 通过。以上为 Linux 执行，Windows 原生完整生产链及真实歌曲逐字精度仍需验收。
