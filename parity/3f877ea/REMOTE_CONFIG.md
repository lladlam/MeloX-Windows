# Signed compatibility configuration — desktop business wiring

Pinned acceptance source: Android `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## Actual application paths

- App Shell starts `MeloXRemoteConfigRuntime` after its first composition frame and stops owned refresh work on disposal. The desktop window reports minimize/restore to suppress periodic background requests and refresh on return. Refresh stays off until the existing `melox_remote_config_consent` policy/version keys record explicit acceptance.
- The real Settings RemoteConfig route displays source, verified configuration version, key, timestamps, declared breakers, fallback order/timeout, errors and selected GitHub route. Manual check, cache clear, protocol text, explicit accept/reject and withdrawal all have working handlers. The exact pinned Chinese legal asset is bundled; the viewer identifies it as the Android public source version.
- Existing GitHub route choices and legacy preference migration remain intact. GitHub DoH now really installs `DnsOverHttps` with DNSPod `https://doh.pub/dns-query`, pinned bootstrap IPs and IPv4 behavior from the source. Benchmark and config HTTP calls retain cancellation ownership through body consumption. Endpoints and signing key remain locally pinned.
- The production request uses `MeloX-Desktop` User-Agent, not an Android application identity. `PolicyCompatibilityVersionCode = 20` explicitly denotes the pinned Android compatibility-policy baseline, **not** the Windows package version. The signed envelope's min/max apply to this baseline; the Settings UI discloses it.
- Provider playback resolution consults the effective opted-in policy before subsequent resolutions. The existing original playback session is not interrupted by a refresh. NetEase/QQ phone transport and Spotify OAuth browser launch consult the login capability gate; supported visible login controls observe the same status. Local/user-added providers do not inherit built-in breakers.
- The previously unwired production NetEase fallback now uses the existing local `cross_provider_unavailable_fallback` choice **and** the effective remote policy. Its real Settings Content switch follows the source's confirmation/explanation and retains the original key. Remote acceptance cannot enable local cross-provider search. Candidate sources are limited to the disclosed locally whitelisted order, policy-disabled providers are excluded, and cache identity includes enable state, current breakers, timeout, order and hashed provider-account identity.
- Applicable signed notices are shown through the real Glass dialog with persisted once/daily receipts. No update advertisement or other remote payload is converted into executable content.

## Network, persistence and release correctness

Transport failures remain genuine failures with last-valid/default fallback. Wrong signature, unknown schema/key, rollback and oversized responses cannot become effective policy. Unknown content-length/chunked bodies are read with a hard upper bound; coroutine cancellation closes the HTTP request rather than being swallowed by route fallback. File replacement uses atomic move where supported and preserves the previous file on ordinary failures. Clearing/withdrawing removes envelope/ETag/timestamps while retaining the anti-rollback floor.

A corrupt local envelope also drops its conditional ETag before refetch, preserving its stored rollback floor. This prevents a bad cache from being locked into an endless sequence of 304 responses that never repairs the envelope.

Withdrawal cancels pending/active requests and periodic work, clears cached configuration, resets the visible status and immediately restores built-in effective policy. Runtime stop also prevents later automatic foreground events from issuing new requests. Persistent policy and credential storage names/formats are preserved.

## UI defect repaired during production replay

The old Glass Toggle animated optimistically to the requested value even when its controlled `checked` state did not change. Rejecting remote consent or cancelling the cross-provider explanation left the actual knob green while accessibility correctly reported off. The toggle now animates to the confirmed controlled value after release; transient drag positions remain available, cancelled/small drags return to confirmed state, and its tone observes the current theme. Replay records both the controlled state and the actual rendered toggle progress, rather than accepting accessibility state alone.

## Acceptance boundary

Protocol regression uses real P-256 signatures, actual HTTP sockets, native verification and real preference/file persistence. Test-local signing keys and HTTP fixtures do not change the production pinned key or URLs. Existing playback/PCM/Cloud/DPAPI regressions run alongside it.

Final full result: **124 tests / 21 suites / 0 failures, errors or skipped**. `evidence/remote-config-tests/` retains all XML. The preceding full run encountered a Cloud rejected-upload assertion failure, whose original diagnostic did not include the actual exception. The unchanged 503 requirement passes in the final run; diagnostics now include the actual exception and reached routes if it recurs. Its raw failed build log is preserved and no unrelated root-cause fix is claimed.

Production `ImageComposeScene` replay uses isolated preferences and real controls, dialogs, pointer/wheel events and the search field's accessibility text-edit action. Linux offscreen output is not a visible Windows window or Android pixel/motion parity. Minimize/restore integration still requires actual Windows window lifecycle replay.

`evidence/remote-config-ui-dark/`: **20 state scenes / 25 passing assertions / 57 dynamic frames**, including full protocol viewer, rejection, acceptance, withdrawal, cache clear, Escape return and the independent Content fallback consent/cancel/enable/disable paths. Assertions inspect actual rendered switch progress as well as its controlled state. Requested/actual frame times are retained separately. `remote-config-validation.json` records source/asset/final Linux JAR hashes; packaged native format and authorized-HTTP decoder checks pass in `remote-config-linux-packaged-decoder.json`.

`evidence/remote-config-ui-light-live/`: **15 scenes / 14 passing assertions** against the actual fixed public endpoint through the selected GhFast route. The production pinned verifier accepts config version **6**, key `melox-remote-config-2026-01`; the actual signed once notice is displayed, acknowledged and stays suppressed after manual refresh. Cache clear restores built-in policy without changing consent; withdrawal switches off and Escape returns to Settings. The isolated home has no account credentials/private music. Public-server 304 traffic was not instrumented; that protocol behavior is proved separately by the real-socket integration test. The actual DoH resolver is bundled/wired, but this particular online replay validates GhFast, not every route's network reachability.

The route is business-wired but overall **PARTIAL**: the complete source Settings/Privacy navigation, Android's post-onboarding automatic consent presentation and pinned same-content reference frames remain outstanding. New `febb4c0` signed text overrides/AutoSync are a separately recorded, unimplemented upstream increment. No old MATCH is inherited.
