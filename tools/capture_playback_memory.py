#!/usr/bin/env python3
"""Real provider playback plus repeated player/lyrics disposal; no state injection.

Uses a fresh isolated onboarded home. Software PCM endpoint is labelled and its
recording overhead is reported; this is not Windows speaker or user-machine acceptance.

Usage:
  capture_playback_memory.py HOME OUTPUT
  capture_playback_memory.py --analyze OUTPUT   # rebuild the summary from an existing replay
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]


def analyze(out: Path) -> None:
    capture = json.loads((out / 'capture.json').read_text(encoding='utf-8'))
    samples = [{k: scene[k] for k in ('file', 'captured_elapsed_ms', 'memory', 'artwork_cache', 'skia')}
               for scene in capture['scenes']]
    for sample in samples:
        assert sample['artwork_cache']['budgeted_bytes'] <= sample['artwork_cache']['budget_bytes']
    recorded = sum(endpoint['written_frames'] * 4 for endpoint in capture['pcm_outputs'])
    assert recorded > 0, 'No real decoded PCM'
    cycles = len({step.split(':', 1)[0].split('-')[1] for step in capture['steps']
                  if step.startswith('cycle-') and '-collapse:' in step})
    first, last = samples[0], samples[-1]
    peak = max(sample['memory']['resident_bytes'] for sample in samples)

    def mem(sample, key):
        return sample['memory'][key]

    def live(sample, key):
        # Skiko only records types that have been allocated at least once.
        objects = sample['skia'].get('live_objects')
        return int(objects.get(key, 0)) if isinstance(objects, dict) else 0

    def cache(sample, key):
        value = sample['skia'].get(key)
        return int(value) if isinstance(value, int) else 0

    accounted_start = mem(first, 'heap_committed_bytes') + mem(first, 'non_heap_used_bytes')
    accounted_end = mem(last, 'heap_committed_bytes') + mem(last, 'non_heap_used_bytes')
    growth = {key: live(last, key) - live(first, key)
              for key in sorted(set(first['skia'].get('live_objects', {})) | set(last['skia'].get('live_objects', {})))}
    analysis = {
        'resident_start_bytes': mem(first, 'resident_bytes'),
        'resident_peak_bytes': peak,
        'resident_end_bytes': mem(last, 'resident_bytes'),
        'resident_growth_bytes': mem(last, 'resident_bytes') - mem(first, 'resident_bytes'),
        'jvm_heap_used_growth_bytes': mem(last, 'heap_used_bytes') - mem(first, 'heap_used_bytes'),
        'jvm_heap_committed_end_bytes': mem(last, 'heap_committed_bytes'),
        'jvm_non_heap_growth_bytes': mem(last, 'non_heap_used_bytes') - mem(first, 'non_heap_used_bytes'),
        'accounted_jvm_end_bytes': accounted_end,
        'unaccounted_native_end_bytes': mem(last, 'resident_bytes') - accounted_end,
        'unaccounted_native_growth_bytes': (mem(last, 'resident_bytes') - accounted_end) -
                                           (mem(first, 'resident_bytes') - accounted_start),
        'skia_font_cache_growth_bytes': cache(last, 'font_cache_bytes') - cache(first, 'font_cache_bytes'),
        'skia_resource_cache_growth_bytes': cache(last, 'resource_cache_bytes') - cache(first, 'resource_cache_bytes'),
        'skia_live_object_growth': growth,
        'artwork_cache_budgeted_end_bytes': last['artwork_cache']['budgeted_bytes'],
        'artwork_cache_budget_bytes': last['artwork_cache']['budget_bytes'],
        'final_idle_resident_delta_bytes': (mem(samples[-1], 'resident_bytes') -
                                            mem(samples[-2], 'resident_bytes')) if len(samples) > 1 else 0,
        'final_idle_live_paragraph_delta': (live(samples[-1], 'Paragraph') -
                                            live(samples[-2], 'Paragraph')) if len(samples) > 1 else 0,
    }
    (out / 'memory-summary.json').write_text(json.dumps(dict(
        status='PASSED_REPLAY', provider='Netease', real_track='netease:3334421408',
        player_disposal_cycles=cycles, scenes=len(samples),
        final_position_ms=capture['scenes'][-1].get('position_ms'),
        final_is_playing=capture['scenes'][-1].get('is_playing'),
        track_duration_ms=capture['scenes'][-1].get('duration_ms'),
        track_ended=(capture['scenes'][-1].get('position_ms') or 0) >= (capture['scenes'][-1].get('duration_ms') or 0),
        forced_gc=False, windows_user_acceptance='PENDING', recording_bytes=recorded,
        note='Validation stores PCM and scene evidence; process memory includes that overhead. '
             'One-track replay does not prove all-song/platform plateau.',
        analysis=analysis, samples=samples), indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
    print('PASS: real Tiny Giant PCM, %d player disposal cycles, heap/resident/cache measurements' % cycles)
    print('resident %.1f -> %.1f MB (peak %.1f); JVM accounted %.1f MB; unaccounted native %.1f MB (+%.1f)' % (
        mem(first, 'resident_bytes') / 1e6, mem(last, 'resident_bytes') / 1e6, peak / 1e6, accounted_end / 1e6,
        analysis['unaccounted_native_end_bytes'] / 1e6, analysis['unaccounted_native_growth_bytes'] / 1e6))
    print('live Skia object growth: ' + ', '.join('%s%+d' % (k, v) for k, v in growth.items() if v))


if len(sys.argv) > 1 and sys.argv[1] == '--analyze':
    analyze(Path(sys.argv[2]).resolve())
    sys.exit(0)

home, out = (Path(p).resolve() for p in sys.argv[1:3])
assert not home.exists(), 'Use a new isolated home'
(home / '.melox').mkdir(parents=True)
(home / '.melox/prefs_melox_app_settings.properties').write_text('onboarding_completed=true\n')
out.mkdir(parents=True, exist_ok=True)
# The lyrics page hides its transport after the real 5 s interface auto-hide delay.
# Reveal it explicitly so the physical control click is not consumed by the reveal layer.
steps = ['remote-reject:tap@remote-reject', 'player:tap@mini-player-expand',
         'pause:tap@player-play-pause', 'seek:slider@player-progress,0.155',
         'lyrics:tap@player-show-lyrics', 'lyrics-loaded:wait@1500',
         'autohide:wait@6000', 'reveal:tap@lyrics-reveal-controls',
         'resume:tap@player-play-pause', 'warmup:wait@10000']
expect = ['remote-reject=cloud-choice:true,cloud-enabled:false',
          'player=playing:true,no-playback-error:true', 'pause=playing:false',
          'lyrics-loaded=page:Lyrics,word-lines-min:1',
          'autohide=tag:lyrics-reveal-controls', 'reveal=tag:player-controls',
          'resume=playing:true,no-playback-error:true']
# Tiny Giant is 177.435 s. Each cycle costs ~7.7 s of wall time (settle + PNG scene
# evidence), so 14 cycles plus the final idle sample finish before the track ends;
# a longer loop would fail on a genuine end-of-track rather than show memory growth.
for i in range(14):
    steps += [f'cycle-{i}-collapse:tap@player-collapse', f'cycle-{i}-open:tap@mini-player-expand',
              f'cycle-{i}-playing:wait@2000']
    expect += [f'cycle-{i}-collapse=absent:now-playing',
               f'cycle-{i}-open=page:Lyrics,playing:true,no-playback-error:true',
               f'cycle-{i}-playing=page:Lyrics,playing:true,no-playback-error:true']
steps += ['final-reveal:tap@lyrics-reveal-controls', 'pause-final:tap@player-play-pause',
          'closed:tap@player-collapse', 'settled:wait@10000']
expect += ['final-reveal=tag:player-controls', 'pause-final=playing:false,no-playback-error:true',
           'closed=absent:now-playing']
args = ['--source', 'Netease', '--start-tab', 'Search', '--theme', 'Dark', '--output', str(out),
        '--width', '666', '--height', '1334', '--density', '1.75', '--wait-ms', '650',
        '--startup-wait-ms', '1200', '--play-id', '3334421408', '--capture-pcm', '--skia-stats',
        '--steps', ';'.join(steps), '--expect', ';'.join(expect)]
argfile = out / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
(out / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n', encoding='utf-8')
with (out / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(result.returncode)
for tool, extra in [('verify_lyrics_layout.py', []), ('verify_online_playback_capture.py', ['--track', 'netease:3334421408'])]:
    subprocess.run([sys.executable, 'tools/' + tool, str(out), *extra, '--output', str(out / (tool + '.json'))],
                   cwd=repo, check=True)
analyze(out)
