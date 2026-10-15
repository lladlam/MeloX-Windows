#!/usr/bin/env python3
"""Bisect playback memory growth: real playback + player disposal without opening lyrics.

Same production flags, track and PCM endpoint as capture_playback_memory.py, so the
resident / Skia live-object growth of this run can be compared against the lyrics run.
Not speaker, Windows or pinned-Android acceptance.

Usage:
  capture_player_disposal_memory.py HOME OUTPUT
  capture_player_disposal_memory.py --analyze OUTPUT   # reuse capture_playback_memory's analyzer
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]

if len(sys.argv) > 1 and sys.argv[1] == '--analyze':
    subprocess.run([sys.executable, 'tools/capture_playback_memory.py', '--analyze', sys.argv[2]], cwd=repo, check=True)
    sys.exit(0)

home, out = (Path(p).resolve() for p in sys.argv[1:3])
paused = os.environ.get('MELOX_MEMORY_PAUSED') == '1'
assert not home.exists(), 'Use a new isolated home'
(home / '.melox').mkdir(parents=True)
(home / '.melox/prefs_melox_app_settings.properties').write_text('onboarding_completed=true\n')
out.mkdir(parents=True, exist_ok=True)
playing = 'false' if paused else 'true'
steps = ['remote-reject:tap@remote-reject', 'player:tap@mini-player-expand',
         'pause:tap@player-play-pause']
expect = ['remote-reject=cloud-choice:true,cloud-enabled:false',
          'player=playing:true,no-playback-error:true', 'pause=playing:false']
if not paused:
    steps.append('resume:tap@player-play-pause')
    expect.append('resume=playing:true,no-playback-error:true')
steps.append('warmup:wait@6000')
for i in range(int(os.environ.get('MELOX_MEMORY_CYCLES', '14'))):
    steps += [f'cycle-{i}-collapse:tap@player-collapse', f'cycle-{i}-open:tap@mini-player-expand',
              f'cycle-{i}-playing:wait@2000']
    expect += [f'cycle-{i}-collapse=absent:now-playing',
               f'cycle-{i}-open=page:Artwork,playing:{playing},no-playback-error:true',
               f'cycle-{i}-playing=page:Artwork,playing:{playing},no-playback-error:true']
steps += ['closed:tap@player-collapse', 'settled:wait@10000']
expect += ['closed=absent:now-playing']
if not paused:
    steps.insert(-2, 'pause-final:tap@player-play-pause')
    expect.insert(-1, 'pause-final=playing:false,no-playback-error:true')
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
    sys.stderr.write('\n'.join((out / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-50:]) + '\n')
    raise SystemExit(result.returncode)
subprocess.run([sys.executable, 'tools/capture_playback_memory.py', '--analyze', str(out)], cwd=repo, check=True)
