#!/usr/bin/env python3
"""Offscreen pointer regression for promoted mouse and native Touch scroll paths.

Uses production settings, no accounts or network content. This is scene input
evidence, not physical Windows touchscreen acceptance.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output = (Path(value).resolve() for value in sys.argv[1:3])
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
steps = [
    'consent:tap@remote-reject',
    'settings:tap@desktop-settings',
    'before:wait@400',
    'mouse-up:mouse-drag@settings-general,0,-130',
    'mouse-down:mouse-drag@settings-general,0,130',
    'touch-up:touch-drag@settings-general,0,-130',
    'touch-down:touch-drag@settings-general,0,130',
]
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Netease', '--start-tab', 'Home', '--theme', 'Dark',
        '--width', '1280', '--height', '800', '--output', str(output), '--wait-ms', '350',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1',
        '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process',
        '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
        cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-12000:])
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))


def position(name):
    entry = next(s for s in data['scenes'] if s['file'].split('-', 1)[1] == name + '.png')
    nodes = json.loads((output / entry['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
    node = next(n for n in nodes if n.get('tag') == 'settings-general')
    return node['vertical_scroll']


positions = {name: position(name) for name in ['before', 'mouse-up', 'mouse-down', 'touch-up', 'touch-down']}
assert positions['mouse-up'] - positions['before'] > 60, positions
assert positions['mouse-up'] - positions['mouse-down'] > 60, positions
assert positions['touch-up'] - positions['mouse-down'] > 60, positions
assert positions['touch-up'] - positions['touch-down'] > 60, positions
report = {'renderer': data['renderer'], 'physical_touch_verified': False, 'positions': positions}
(output / 'drag-report.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS scene drag regression:', positions)
