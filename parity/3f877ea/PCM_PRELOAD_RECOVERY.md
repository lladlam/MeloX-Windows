# Bounded incoming PCM scheduling recovery

Windows run `38202345808`, exact Unified Search commit `275032bc138fdad36392b1ea9e513091b4729dc9`, failed `twoDecodedFrequencyComponentsCrossfadeAndHandoffOnlyAfterRenderedFrames`: the controlled output received a fallback-length stream and never reached the expected genuine two-deck length. This run is **FAIL**, not inherited from the earlier remote-config success.

The incoming read previously used an immediate `poll()` on every 20ms block. An empty queue at that instant was treated as permanent underrun, including ordinary decoder-thread scheduling jitter. Incoming reads now have one **100ms total budget per mixed block**, measured against a monotonic deadline. The budget is shared across packets; it is not restarted for each packet. EOF and decoder errors remain distinct. Budget exhaustion still reports an actual fallback; no PCM is invented or retried indefinitely.

The real native two-tone integration test now records fallback exceptions and fails promptly with the reason and accepted-byte count, rather than spending 30 seconds waiting for a length that cannot be reached. Its overlap amplitudes, pitch, rendered-frame handoff, cancellation and invalid-input assertions remain in place.

The initial Linux full suite passed all existing **130 tests / 22 suites** after the PCM change. Its desktop UI compilation then exposed missing imports in the concurrent uncommitted Jellyfin work; that build is not labelled a complete Jellyfin success. A fresh exact-SHA Windows CI run is required. Real Windows output-buffer scheduling/audible transitions and pinned Android motion remain **BLOCKED**.

Evidence: `evidence/windows-ci-275032b-failed/TEST-melox.player.DesktopDualPcmTest.xml` and `evidence/pcm-preload-linux/TEST-melox.player.DesktopDualPcmTest.xml`.
