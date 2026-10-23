#!/usr/bin/env python3
"""Pointer-selected Dock geometry: the gray lens must match its actual tab cell."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

source, width, density, home, output = sys.argv[1:6]
assert source in ('Local', 'Netease')
width, density = int(width), float(density)
home, output = Path(home).resolve(), Path(output).resolve()
repo = Path(__file__).resolve().parents[1]
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\ngeneral_desktop_layout=false\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
steps = [('consent', 'tap@remote-reject', ['cloud-enabled:false']),
         ('settings', 'wait@1200', ['dock-selection:Settings']),
         ('library', 'tap@root-tab:Library', []),
         ('library-settled', 'wait@1200', ['dock-selection:Library']),
         ('return', 'hold-drag-between@root-tab:Library,root-tab:Settings', []),
         ('return-settled', 'wait@1200', ['tag:settings-root', 'dock-selection:Settings'])]
(output/'steps.txt').write_text('\n'.join(f'{n}:{a}' for n,a,_ in steps)+'\n', encoding='utf-8')
(output/'expect.txt').write_text('\n'.join(n+'='+','.join(c) for n,_,c in steps if c)+'\n', encoding='utf-8')
args = ['--source',source,'--start-tab','Settings','--width',str(round(width*density)),
        '--height',str(round(850*density)),'--density',str(density),'--theme','Dark',
        '--output',str(output),'--wait-ms','300','--startup-wait-ms','1500',
        '--steps-file',str(output/'steps.txt'),'--expect-file',str(output/'expect.txt')]
(output/'arguments.txt').write_text('\n'.join(args)+'\n',encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle
with (output/'gradle.log').open('w') as log:
    result = subprocess.run([gradle,':desktop:captureUi','--offline','--no-daemon','--max-workers=1','--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process','-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome='+str(home),'-PcaptureArgsFile='+str(output/'arguments.txt')], cwd=repo,stdout=log,stderr=subprocess.STDOUT)
if result.returncode:
    print((output/'gradle.log').read_text(errors='replace')[-12000:]);raise SystemExit(result.returncode)
d=json.loads((output/'capture.json').read_text());assert all(a['passed'] for s in d['scenes'] for a in s.get('assertions',[]))
print(f'PASS Dock {source}/{width}dp/{density}x: {len(d["scenes"])} scenes')
