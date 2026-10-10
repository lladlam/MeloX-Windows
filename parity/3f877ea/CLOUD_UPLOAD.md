# Cloud upload — pinned Android 3f877ea

Source: `core/network/NeteaseUniversalSearchClient.uploadCloudSong` and `ui/cloud/MeloXCloudMusicScreen`.

## Implementation

- `network/NeteaseCloudUploadClient.kt`: immutable cache-directory snapshot, cancellable MD5 read, actual bundled FFmpeg metadata extraction (title/artist/album/bitrate), source metadata normalization, upload-check and NOS token allocation.
- Source LBS discovery, HTTPS NOS streaming PUT, `x-nos-token` and hexadecimal `Content-MD5`, source bucket/product/type fields. The account Cookie is not attached to NOS PUT.
- Source EAPI/WEAPI registration fallback across bitrate and raw/encoded filename variants; only then `/api/cloud/pub/v2`. A failed stage cannot report completion or publish a new song.
- `MeloXOwnedHttpCall.kt`: cancellation ownership includes response consumption; canceled sockets restore coroutine cancellation semantics rather than becoming ordinary business errors. EAPI/WEAPI ownership parameters are optional and preserve existing callers.
- `CloudScreen.kt`: actual Swing EDT file picker, upload state/real byte progress/cancel, successful publish followed by refresh, persistent existing rows on errors, retry/refresh controls. Screen cancellation closes the picker/request and removes the cache snapshot. Embedded Library cloud content uses source-style actions without a duplicate full-page heading/back action.

## Evidence levels

`CloudUploadProtocolTest` has six substantive local-server protocol integration cases. It uses actual generated WAV/FLAC files, native metadata, production EAPI/WEAPI encryption, HTTP transport and streaming request bodies; only the remote service is a protocol fixture.

Verified:

1. Exact real upload bytes and MD5, native WAV bitrate, EAPI payload integrity, publish then cloud-list refresh.
2. Unicode/escaped native FLAC metadata and filename preservation.
3. Existing remote-file path skips NOS; real encrypted WEAPI fallback registers it.
4. Rejected NOS upload never registers/publishes/reports success; cloud refresh still returns the genuine fixture state.
5. Canceling a blocked NOS response releases promptly and removes the snapshot without publishing.
6. Absent account, empty file and invalid audio produce no cloud requests.

Production offscreen UI capture uses an isolated, unauthenticated home and verifies the reachable upload entry and actual missing-login refresh/retry behavior. It does not prove desktop file selection or remote upload success.

Overall status is **PARTIAL**. Real authenticated NetEase account upload/refresh and Windows desktop chooser operation are **BLOCKED** until those environments are available. Android/Windows same-content pixels and motion remain **BLOCKED** (device enumeration still zero). No synthetic cloud account, service responses or playback state are present on the formal application path.

## Direct Windows acceptance

1. Run the final app-image for the implementation SHA; log into the real NetEase account locally.
2. Open Cloud → Upload, select generated WAV and tagged FLAC using the desktop chooser, verify real stage/byte progress and that the refreshed list contains the published item.
3. Cancel a new upload; verify that it is not reported as complete and the private cache snapshot is removed. Exercise a genuine network/upload failure and retry while preserving the current list.
4. Open the embedded Library cloud page; verify its actions and content without a duplicate full-page heading.
5. Retain only sanitized logs and aggregate generated-file metrics in public evidence. Do not upload account Cookies/tokens or personal music.
