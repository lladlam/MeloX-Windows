#!/usr/bin/env python3
"""v0.6.5 analysis-limit choices via real pointer input and a separate-JVM reload."""
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
steps, checks = [], {}


def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    if assertions:
        checks[name] = list(assertions)


key = 'automix_smart_queue_analysis_limit'
strings = dict(line.split('=', 1) for line in (repo / 'desktop/src/main/resources/parity/pinned-settings-pages.properties')
               .read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))
if mode == 'controls':
    add('remote-reject', 'tap@remote-reject', ['cloud-enabled:false'])
else:
    checks['00-settings'] = ['smart-queue-analysis-limit:-1']
add('reveal-playback', 'reveal@settings-open-playback')
add('open-playback', 'tap@settings-open-playback', ['tag:settings-playback'])
if mode == 'controls':
    add('reveal-automix', 'reveal@setting-auto_mix')
    add('automix-on', 'tap@setting-auto_mix', ['automix-mode:Smart', 'smart-queue-analysis-limit:60'])
    # These strings are literal labels in the official release, not invented translations.
    for value, label in [(20, '前 20 首'), (40, '前 40 首'), (60, '前 60 首'), (100, '前 100 首'), (-1, '不限')]:
        add(f'reveal-{value}', f'reveal@{key}')
        add(f'menu-{value}', f'tap@{key}')
        add(f'choose-{value}', 'tap@settings-option:' + label, [f'smart-queue-analysis-limit:{value}'])
    add('reveal-mode', 'reveal@automix_mode')
    add('mode-menu', 'tap@automix_mode')
    add('fixed', 'tap@settings-option:' + strings['settings_automix_fixed'], ['automix-mode:Fixed', f'absent:{key}'])
    add('mode-menu-again', 'tap@automix_mode')
    add('smart', 'tap@settings-option:' + strings['settings_automix_smart'], ['automix-mode:Smart', 'smart-queue-analysis-limit:-1'])
else:
    add('reveal-persisted', f'reveal@{key}')
    add('persisted', 'wait@100', ['smart-queue-analysis-limit:-1', 'text:不限'])

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items()) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark',
        '--width', '1100', '--height', '780', '--output', str(output), '--wait-ms', '180',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--no-daemon', '--console=plain',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-60:]))
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in data['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
print(f'PASS v0.6.5 Smart Queue {mode}: {len(data["scenes"])} scenes / {len(assertions)} assertions')
