# 第三方来源与许可 / Third-party notices

本目录下的 C/C++ 源码用于日文 G2P（汉字读音 → 音素），全部为**构建期依赖**，
随 APK 一起分发的只有编译后的 `libmelox_ja_g2p.so`。

## Open JTalk（文本前端）

- 来源：https://github.com/r9y9/open_jtalk （tag `v1.11.1`）
- 许可：Modified BSD（见 `COPYING.open_jtalk`）
- 使用范围：`text2mecab/`、`mecab2njd/`、`njd/`、`njd2jpcommon/`、`jpcommon/`、
  `njd_set_*/`
- **不含** HTS Engine、HTS Voice 或任何合成（仅做 G2P，不做语音合成）

## MeCab（形态素解析）

- 随 Open JTalk 一并分发；上游 https://taku910.github.io/mecab/
- 许可：三选一 BSD / LGPL / GPL（见 `COPYING.mecab`）。本项目选择 **BSD** 条款。
- 使用范围：`mecab/src/`（词典查找与分词）

## NAIST 日语词典（**不在此目录、也不打进 APK**）

- 名称：`open_jtalk_dic_utf_8-1.11`
- 来源：https://github.com/r9y9/open_jtalk/releases/download/v1.11.1/open_jtalk_dic_utf_8-1.11.tar.gz
- 大小：下载 23,646,843 字节；解压后约 102.3 MB
- SHA-256：`fe6ba0e43542cef98339abdffd903e062008ea170b04e7e2a35da805902f382a`
- 许可：NAIST Modified BSD（见词典内 `COPYING`；应用内「测试功能尝鲜 → AutoSync 模型」
  下载前会明确提示，安装后保留该文件并可在界面查看）
- 版权与免责声明：Copyright (c) 2009, Nara Institute of Science and Technology, Japan.
  All rights reserved. 再分发须保留版权声明与免责声明，且不得以作者名义为衍生产品背书。
