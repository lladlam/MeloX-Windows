# Production AutoMix migration: pinned Android 3f877ea

## Source behavior → production implementation

- `playback/MeloXPlaybackService.kt` preload/update/transport/handoff → `PlaybackCommands.kt` controller maintenance, actual provider/download URL resolution, cancellable analysis, scored or configured fallback plan, preloading and queue promotion.
- `playback/MeloXAutoMixAudioAnalyzer.kt` transition scorer → `MeloXAutoMixTransitionScorer.kt` phrase, onset/novelty, contour, energy continuity and spectral crowding scoring. Full PCM analyses have source/authorization/revision-keyed caches.
- `MeloXSmartQueueBuilder.kt` → per-controller native-analysis candidate pool; bounded parallel resolution/analysis, tempo-compatible one-at-a-time visible queue growth, restoration of unconsumed candidates on disable.
- Primary/incoming ExoPlayer ownership → `AudioPlayer.kt`, `MeloXDualDecodeSession.kt`, `MeloXBufferedPcmDeck.kt`: two real FFmpeg pipes, one Java Sound output, bounded one-second PCM preload, 20ms mixing, rendered-frame clocks and metadata promotion after output consumption. Consecutive transitions retain the endpoint.
- Fade/tempo/bass exchange → `MeloXPcmMix.kt`, `MeloXAutoMixEqualizerEnvelope.kt`: actual saturating s16le mixing, source gain curves, pitch-preserving native `atempo@mix` command ramp, real RBJ 300Hz low shelf; software volume/mute works on endpoints without MASTER_GAIN.
- `MeloXAutoMixTransitionRuntime`/shared cover host → runtime StateFlow, fourth queue AutoMix control, persistent source settings and split/handoff covers (28% ease-out-back split, 16dp gap, 620ms incoming expansion). Playback progress is output-clock-driven; UI does not synthesize audio state.
- Local provider was unavailable: implemented actual local indexed search/playback and lossless URI/metadata encoding. It retains Local identity and is excluded from online resolver fallback.

## Verification and limits

`DesktopDualPcmTest` uses two generated WAV recordings, bundled native FFmpeg, independent frequency projections and real PCM hashes. It verifies overlap amplitude, primary/incoming isolation, output-consumption handoff, drain, seek=0, pause intent, invalid incoming fallback, cancellation, pitch preservation, low-shelf attenuation and clipping saturation.

`DesktopAutoMixIntegrationTest` runs the actual controller and production Local provider/resolver through AudioPlayer and FFmpeg. Only physical hardware is controlled. Cases cover two consecutive handoffs on one output, natural end at incoming duration, pause/cancel during overlap, seek=0, clear/release, next at the heard incoming position, previous, volume/mute without device gain, and real analyzed Smart Queue selection (90 → 92 BPM ahead of original 140 BPM candidate).

`DesktopStreamingAudioSessionTest` reruns the original single-deck lifecycle regressions through the production streaming/mix-capable path. Original regression coverage remains enabled.

The capture entry point can explicitly select a software PCM endpoint and generated local files. Evidence labels it `VALIDATION_SOFTWARE_PCM_OUTPUT`, saves actual written/consumed frames and signed PCM, and drives the production UI/controller/provider. `tools/verify_captured_automix.py` independently reads back the consumed PCM and requires primary, simultaneous two-frequency overlap and isolated incoming on the same endpoint.

AutoMix remains **PARTIAL** overall. Authenticated streaming-provider transition acceptance, full settings/candidate lifecycle coverage, Windows physical device changes/sleep and sustained audible transition require further acceptance. Windows physical sound and pinned Android pixel/motion comparison remain **BLOCKED**. Requested capture offsets and actual capture times are recorded separately; these Linux frames are not timing-parity proof.

## Reproduce

Generate redistributable fixtures with `python tools/generate_pcm_fixtures.py <directory>`, then run `:shared:desktopTest :desktop:desktopJar`.

Production offscreen replay uses `:desktop:captureUi` with an isolated `-PcaptureHome` and arguments `--capture-pcm --automix --local-wavs <absolute-1000hz.wav>,<absolute-4000hz.wav>`. Capture the real `mini-player-expand`, `player-show-queue` and `queue-automix` controls; wait for overlap and handoff. The exact validated invocation is archived with the evidence log.

For Windows physical output, download the artifact for the exact implementation commit and run `tools/windows_audio_acceptance.ps1` as documented in the handoff. Keep generated fixtures separate from private music and account data.
