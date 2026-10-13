# Anonymous search-to-playback repair — 2026-10-13

Pinned Android reference remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.
This is a repair of desktop playback, not a new complete parity acceptance.

## Reproduced fault

With no account, real NetEase song **139774**, “The truth that you leave”,
loads in production Search. Its metadata contains non-breaking spaces.
The old `uriForSong` interpolated title/artist directly into `melox://song/…`.
`java.net.URI` rejected the query before audio resolution:
`Illegal character in query at index 46`. Playback stopped with no PCM.
The official endpoint independently returned a full, non-preview standard source.
`evidence/playback-fix/rejected-before-uri/` retains the failed production replay.

## Repairs

- Encode each title/artist query value as UTF-8; split the raw query before
  decoding each value exactly once. Spaces, NBSP, `+`, `&`, `#`, `%`, `=` and
  emoji survive without injecting fields or losing fallback metadata. Legacy
  query flags without values no longer cause destructuring failures. Queue
  preferences keep their existing keys/format; metadata is used to rebuild URIs.
- Delegate provider/unified **native NetEase** tracks to the same NetEase
  downloaded/account/fallback chain as ordinary Songs. Delegation precedes the
  provider cache, so local-source, fallback opt-in and account changes stay live.
  Foreign resource IDs retain their provider resolver.
- Connect desktop player resolver construction to the pinned optional CHKSZ/LX
  slots and existing local third-party enabled/membership-only preferences.
  Restore the previously all-null CHKSZ client from the pure pinned source:
  real `163_music`, `qq_music`, `kugou_music`, native metadata, quality mapping,
  user-entered protected API key and nested response parsing. Source caches also
  distinguish local enable/membership/source configuration changes.
- **LX runtime itself remains an existing empty implementation and is not
  accepted as operational.** This batch restores resolver wiring, not JS
  execution. Live CHKSZ needs an authorized user API key; protocol fixtures do
  not establish live third-party playback.
- The driver can choose a real native song ID/name, isolated output directory
  and NetEase-only unified replay without overwriting historical failure evidence.

## Acceptance

`evidence/playback-fix/validation.json` records the final local checks:

- **174 tests / 33 suites / zero failures, errors or skipped**. Eight new URI,
  actual-HTTP, native-ID/download, fallback opt-in/cache and CHKSZ protocol cases.
- **2 successful production replays / 24 state scenes / 66 dynamic frames /
  26 assertions**: actual song-name Search and NetEase-only Unified Search both
  select native song 139774, resolve official full audio, advance the genuine
  output clock, pause and close. Ordinary Search also exercises long-press,
  queue/play-next and quality dialog/back. They are offscreen `ImageComposeScene`.
- Independent S16LE readback finds consumed, non-silent audio in both replays;
  all software output endpoints close. Only metrics/hashes are archived in
  `search-play-pcm.json` and `search-play-unified-pcm.json`.
- Source and final Linux uber JAR native formats/required-header HTTP, five
  immutable pinned resource bytes and genuine single/dual EQ PCM all pass.
  Final Linux JAR SHA-256:
  `a46cb3e3f07657bb30d3af0f447b1fa7c3abfde30cc262886b2d9fbdd7e78c46`.
- Initial new test setup incorrectly used an empty registry, then a provider
  outside the pinned fallback order; both rejected fixture runs remain. A
  temporary disk-quota failure is also retained; only generated Skiko caches
  in earlier isolated replay homes were removed, preserving preferences.
- Completed online replay processes initially stayed alive on idle non-daemon
  HTTP dispatcher workers. A deadline thread dump confirms `DestroyJavaVM`
  rather than a player deadlock. Validation teardown now closes the scene and
  cancels/shuts down its process's shared HTTP dispatcher; both replays exit 0.

Exact-fix-SHA Windows CI/package verification is pending publication.
Before-fix/fixture failures are retained and are not successful playback evidence.
Live-song recordings are not published: archive only output metrics/hashes,
public UI/semantics and sanitized logs. Generated PCM remains separate.

Physical Windows speakers/devices/DPAPI and same-content pinned Android
pixels/motion require their own acceptance. Searchable song **185670** still
returns no playable non-preview source; rejecting that service result is not
proof of an output-device fault. A search hit is not a guarantee of full audio.
