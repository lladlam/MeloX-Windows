#!/usr/bin/env python3
"""Physical settings input and independent reload for the real Bilibili alignment preference."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

mode, home, output = sys.argv[1:4]
assert mode in ('controls', 'reload')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
steps, expected = [], {}


def add(name, action, checks=()):
    steps.append(f'{name}:{action}')
    if checks:
        expected[name] = list(checks)


key = 'bilibili_lyric_audio_alignment'
tag = 'setting-' + key
runtime = 'bilibiliLyricAudioAlignmentEnabled'
if mode == 'controls':
    add('remote-reject', 'tap@remote-reject', ['cloud-enabled:false'])
add('settings-tab', 'tap@root-tab:Settings', ['tag:settings-root'])
add('find-experimental', 'reveal@settings-open-experimental')
add('experimental', 'tap@settings-open-experimental', ['tag:settings-experimental'])
add('find-alignment', 'reveal@' + tag)
if mode == 'controls':
    add('off-before', 'wait@100', ['enabled:' + tag, 'unchecked:' + tag, f'runtime:{runtime}:false'])
    add('on', 'tap@' + tag, ['checked:' + tag, f'pref-bool:{key}:true', f'runtime:{runtime}:true'])
    add('off', 'tap@' + tag, ['unchecked:' + tag, f'pref-bool:{key}:false', f'runtime:{runtime}:false'])
    add('on-persisted', 'tap@' + tag, ['checked:' + tag, f'pref-bool:{key}:true', f'runtime:{runtime}:true'])
else:
    add('reloaded', 'wait@100', ['enabled:' + tag, 'checked:' + tag, f'pref-bool:{key}:true', f'runtime:{runtime}:true'])

steps_file, expect_file = output / 'steps.txt', output / 'expect.txt'
steps_file.write_text('\n'.join(steps) + '\n', encoding='utf-8')
expect_file.write_text('\n'.join(name + '=' + ','.join(checks) for name, checks in expected.items()) + '\n', encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Bilibili', '--start-tab', 'Settings', '--width', '1100', '--height', '780',
        '--output', str(output), '--wait-ms', '180', '--startup-wait-ms', '1200',
        '--steps-file', str(steps_file), '--expect-file', str(expect_file)]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-60:]))
    raise SystemExit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
checks = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert checks and all(a['passed'] for a in checks)
print(f"PASS {mode}: {len(capture['scenes'])} scenes / {len(checks)} assertions")
