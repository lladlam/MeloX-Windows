#!/usr/bin/env python3
"""Exercise the live background and performance controls through pointer input."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

layout, mode, home, output = sys.argv[1:5]
assert layout in ('mobile', 'desktop') and mode in ('controls', 'reload')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
desktop = layout == 'desktop'
prefs = home / '.melox/prefs_melox_app_settings.properties'
if mode == 'controls':
    assert not prefs.exists(), 'Use an isolated fresh home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                     f'general_desktop_layout={str(desktop).lower()}\n', encoding='utf-8')
else:
    assert prefs.exists(), 'Reload requires the previous controls home'

steps, checks = [], []
def add(name, action, *expect):
    steps.append(f'{name}:{action}')
    if expect:
        checks.append(name + '=' + ','.join(expect))

def route(name, group):
    if desktop:
        add('group-' + name, 'tap@desktop-settings-tab:' + group)
        add('reveal-' + name, 'reveal@desktop-settings-subtab:' + name)
        add('open-' + name, 'tap@desktop-settings-subtab:' + name, 'tag:settings-page:' + name)
    else:
        add('reveal-' + name, 'reveal@settings-open-' + name.lower())
        add('open-' + name, 'tap@settings-open-' + name.lower(), 'tag:settings-' + name.lower())

def back(name):
    if not desktop:
        add('back-' + name, 'escape', 'tag:settings-root')

switch = 'setting-player_flowing_backdrop'
perf = 'setting-developer_performance_overlay'
if mode == 'controls':
    add('consent', 'tap@remote-reject')
    route('Appearance', 'General')
    add('reveal-flow', 'reveal@' + switch)
    add('flow-off', 'tap@' + switch, 'unchecked:' + switch,
        'pref-string:player_background_mode:BlurredArtwork', 'pref-bool:player_flowing_backdrop:false')
    add('flow-on', 'tap@' + switch, 'checked:' + switch,
        'pref-string:player_background_mode:FlowingLight', 'pref-bool:player_flowing_backdrop:true')
    add('reveal-background', 'reveal@player_background_mode')
    add('background-menu', 'tap@player_background_mode')
    add('background-custom', 'tap@settings-option:自定义图片', 'pref-string:player_background_mode:CustomImage',
        'pref-bool:player_flowing_backdrop:false')
    add('reveal-flow-custom', 'reveal@' + switch)
    add('flow-unchecked-custom', 'wait@200', 'unchecked:' + switch)
    back('Appearance')
    route('Developer', 'Developer')
    add('performance-on', 'tap@' + perf, 'checked:' + perf, 'tag:performance-overlay',
        'pref-bool:developer_performance_overlay:true')
    add('performance-samples', 'wait@800', 'state:performance-overlay:sampled')
else:
    add('reloaded', 'wait@800', 'tag:performance-overlay', 'pref-bool:developer_performance_overlay:true',
        'pref-string:player_background_mode:CustomImage', 'pref-bool:player_flowing_backdrop:false')
    route('Developer', 'Developer')
    add('performance-off', 'tap@' + perf, 'unchecked:' + perf, 'absent:performance-overlay',
        'pref-bool:developer_performance_overlay:false')

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(checks) + '\n', encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '1100' if desktop else '430',
        '--height', '850', '--output', str(output), '--wait-ms', '250', '--startup-wait-ms', '1200',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    raise SystemExit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
print(f'PASS {layout}/{mode}: {len(capture["scenes"])} scenes / {len(assertions)} assertions')
