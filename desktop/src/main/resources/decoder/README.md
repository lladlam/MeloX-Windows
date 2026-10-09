# Bundled decoder

The production app resolves these resources through `FfmpegLocator`, extracts the
OS-specific executable, and runs it off the UI thread. They are stored using Git
LFS; after cloning, run `git lfs pull` before building or running decoder tests.

| Resource | Bytes | SHA-256 |
| --- | ---: | --- |
| `ffmpeg` (Linux x64) | 172690600 | `ee930e8def21a993f4b8c680757df15054da1c8f2698da0fdf87f1f3b15bd633` |
| `ffmpeg.exe` (Windows x64) | 164352000 | `af7913b7ddc324bf02b8af72fe8612b1471455ef8d9e04e45466510742abcff9` |

Linux version: `N-126782-gdc52424419-20260923`, built with GCC 16.2.0 and
`--enable-gpl --enable-version3`. FFmpeg is developed by the FFmpeg contributors;
upstream source and licensing information: <https://ffmpeg.org/>.

`PlaybackLinkTest.bundledDecoderEmitsPcmAndIsResolvedAgain` uses the actual
production bundle and checks PCM decoding. A missing resource or an unresolved
LFS pointer fails the test. Audible output and Windows-native execution are
tracked separately in `parity/94e17c9/UI_MOTION.md`.
