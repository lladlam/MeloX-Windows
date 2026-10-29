# OpenJTalk frontend

This directory contains the G2P-only Open JTalk 1.11 frontend derived from the
Android `7c17c009b829c2a0c75c4316dd52d00ebf218c0a` vendored source, itself
from r9y9/open_jtalk v1.11.1. The Open JTalk and MeCab license texts and notice
are in `third_party/open_jtalk/`. The separate NAIST dictionary is obtained from
the pinned upstream release by `AutoSyncJapaneseDictionary` and has its own
`COPYING` file; it is not shipped in this source tree.

The platform-specific `config.h` and Windows MeCab memory mapping use UTF-8 to
wide-character conversion for non-ASCII dictionary paths. `text2mecab` adds a
bounded entry point used by this frontend. The C ABI returns owned UTF-8
strings that must be released with `ja_g2p_free`; each handle is single-session.

For a local Linux build, run `cmake -S native/openjtalk -B <build-dir>
-DCMAKE_BUILD_TYPE=Release`, followed by `cmake --build <build-dir>`. For a
Windows x64 cross-build use `-DCMAKE_SYSTEM_NAME=Windows`,
`-DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc` and
`-DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++`. The packaged Linux and Windows
x64 libraries are pinned via Git LFS; `AutoSyncJapaneseNative` verifies their
SHA-256 before loading. Windows cross-builds use `x86_64-w64-mingw32-strip
--strip-unneeded` for the packaged DLL. Japanese player alignment is not yet
connected.
