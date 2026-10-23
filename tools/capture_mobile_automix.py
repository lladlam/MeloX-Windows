#!/usr/bin/env python3
"""Real Local -> decoder -> dual PCM output with pointer-driven mobile player checks."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

mode, home, output = sys.argv[1:4]
assert mode in ('on', 'off', 'leave', 'reduced')
home, output = Path(home).resolve(), Path(output).resolve()
repo = Path(__file__).resolve().parents[1]
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\ngeneral_desktop_layout=false\n'
                 f'transition_ui_enabled={str(mode != "off").lower()}\n'
                 f'reduce_motion={str(mode == "reduced").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
fixtures = home / 'generated'
subprocess.run([sys.executable, str(repo/'tools/generate_pcm_fixtures.py'), str(fixtures)], check=True)
steps, checks = [], {}
def add(name, action, expected=()):
    steps.append(f'{name}:{action}')
    if expected: checks[name] = expected
add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
add('playing', 'wait@300', ['playing:true', 'tag:mini-player-expand'])
add('open', 'tap@mini-player-expand')
add('opened', 'wait@1000', ['page:Artwork', 'no-playback-error:true'])
paired = mode in ('on', 'leave')
add('mixing', 'wait@0', ['mix-active:true', 'text:generated-4000hz', 'disabled:player-progress',
    ('tag:' if paired else 'absent:')+'automix-paired-artwork'])
if mode == 'leave':
    add('queue', 'tap@player-show-queue', ['page:Queue', 'mix-active:true', 'absent:automix-paired-artwork'])
    add('artwork', 'tap@player-show-queue', ['page:Artwork', 'mix-active:true', 'absent:automix-paired-artwork'])
elif mode == 'on':
    add('split', 'wait@2200', ['mix-active:true', 'tag:automix-incoming-artwork', 'tag:automix-outgoing-artwork'])
add('handoff', 'wait@0', ['title:generated-4000hz', 'mix-active:false', 'enabled:player-progress'])
add('settled', 'wait@1000', ['absent:automix-paired-artwork', 'playing:true', 'no-playback-error:true'])
add('pause', 'tap@player-play-pause', ['playing:false'])
(output/'steps.txt').write_text('\n'.join(steps)+'\n', encoding='utf-8')
(output/'expect.txt').write_text('\n'.join(k+'='+','.join(v) for k,v in checks.items())+'\n', encoding='utf-8')
args = ['--locale','zh-CN','--source','Local','--start-tab','Settings','--theme','Dark','--width','430','--height','850',
        '--output',str(output),'--startup-wait-ms','1200','--wait-ms','180','--assert-timeout-ms','25000',
        '--local-wavs',','.join(str(fixtures/f'generated-{hz}hz.wav') for hz in (1000,4000)),
        '--capture-pcm','--automix','--steps-file',str(output/'steps.txt'),'--expect-file',str(output/'expect.txt')]
(output/'arguments.txt').write_text('\n'.join(args)+'\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle
with (output/'gradle.log').open('w') as log:
    result = subprocess.run([gradle,':desktop:captureUi','--offline','--no-daemon','--max-workers=1','--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome='+str(home),'-PcaptureArgsFile='+str(output/'arguments.txt')],cwd=repo,stdout=log,stderr=subprocess.STDOUT)
if result.returncode:
    print((output/'gradle.log').read_text(errors='replace')[-12000:]);raise SystemExit(result.returncode)
subprocess.run([sys.executable,str(repo/'tools/verify_captured_automix.py'),str(output),'--output',str(output/'pcm-verification.json')], check=True)
data = json.loads((output/'capture.json').read_text())
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
print(f'PASS AutoMix {mode}: {len(data["scenes"])} scenes + independent PCM spectrum readback')
