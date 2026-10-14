#!/usr/bin/env python3
"""Real provider playback plus repeated player/lyrics disposal; no state injection.

Uses a fresh isolated onboarded home. Software PCM endpoint is labelled and its
recording overhead is reported; this is not Windows speaker or user-machine acceptance.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, out = (Path(p).resolve() for p in sys.argv[1:3])
assert not home.exists(), 'Use a new isolated home'
(home / '.melox').mkdir(parents=True)
(home / '.melox/prefs_melox_app_settings.properties').write_text('onboarding_completed=true\n')
out.mkdir(parents=True, exist_ok=True)
steps = ['remote-reject:tap@remote-reject', 'player:tap@mini-player-expand',
         'pause:tap@player-play-pause', 'seek:slider@player-progress,0.155',
         'lyrics:tap@player-show-lyrics', 'lyrics-loaded:wait@1500',
         'resume:tap@player-play-pause', 'warmup:wait@10000']
expect = ['remote-reject=cloud-choice:true,cloud-enabled:false',
          'player=playing:true,no-playback-error:true', 'pause=playing:false',
          'lyrics-loaded=page:Lyrics,word-lines-min:1', 'resume=playing:true,no-playback-error:true']
for i in range(20):
    steps += [f'cycle-{i}-collapse:tap@player-collapse', f'cycle-{i}-open:tap@mini-player-expand',
              f'cycle-{i}-playing:wait@2000']
    expect += [f'cycle-{i}-collapse=absent:now-playing',
               f'cycle-{i}-open=page:Lyrics,playing:true,no-playback-error:true',
               f'cycle-{i}-playing=page:Lyrics,playing:true,no-playback-error:true']
steps += ['pause-final:tap@player-play-pause', 'closed:tap@player-collapse', 'settled:wait@10000']
expect += ['pause-final=playing:false,no-playback-error:true', 'closed=absent:now-playing']
args = ['--source', 'Netease', '--start-tab', 'Search', '--theme', 'Dark', '--output', str(out),
        '--width', '666', '--height', '1334', '--density', '1.75', '--wait-ms', '650',
        '--startup-wait-ms', '1200', '--play-id', '3334421408', '--capture-pcm', '--skia-stats',
        '--steps', ';'.join(steps), '--expect', ';'.join(expect)]
argfile = out / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
(out / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n')
with (out / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(result.returncode)
for tool, extra in [('verify_lyrics_layout.py', []), ('verify_online_playback_capture.py', ['--track', 'netease:3334421408'])]:
    subprocess.run([sys.executable, 'tools/' + tool, str(out), *extra, '--output', str(out / (tool + '.json'))],
                   cwd=repo, check=True)
capture = json.loads((out / 'capture.json').read_text())
samples = [{k: scene[k] for k in ('file', 'captured_elapsed_ms', 'memory', 'artwork_cache', 'skia')}
           for scene in capture['scenes']]
for sample in samples:
    assert sample['artwork_cache']['budgeted_bytes'] <= sample['artwork_cache']['budget_bytes']
recorded = sum(endpoint['written_frames'] * 4 for endpoint in capture['pcm_outputs'])
(out / 'memory-summary.json').write_text(json.dumps(dict(
    status='PASSED_REPLAY', provider='Netease', real_track='netease:3334421408', player_disposal_cycles=20,
    forced_gc=False, windows_user_acceptance='PENDING', recording_bytes=recorded,
    note='Validation stores PCM and scene evidence; process memory includes that overhead. One-track replay does not prove all-song/platform plateau.',
    samples=samples), indent=2) + '\n')
print('PASS: real Tiny Giant PCM, 20 player disposal cycles, heap/resident/cache measurements')
