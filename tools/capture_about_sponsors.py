#!/usr/bin/env python3
"""Exercise the Android sponsor list through actual settings/about pointer input."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

mode, locale, home_arg, output_arg = sys.argv[1:5]
assert mode in ('mobile', 'desktop', 'desktop-tool')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 f'general_desktop_layout={str(mode != "mobile").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
steps = [('consent', 'tap@remote-reject', 'cloud-enabled:false')]
if mode == 'mobile':
    steps += [('find-about', 'reveal@settings-open-about', ''),
              ('open-about', 'tap@settings-open-about', 'tag:settings-about'),
              ('sponsors', 'reveal@about-sponsors', 'tag:about-sponsors,tag:about-sponsor:0,tag:about-sponsor:5')]
elif mode == 'desktop':
    steps += [('developer', 'tap@desktop-settings-tab:Developer', ''),
              ('open-about', 'tap@desktop-settings-subtab:About', 'tag:settings-page:About'),
              ('sponsors', 'reveal@about-sponsors', 'tag:about-sponsors,tag:about-sponsor:0,tag:about-sponsor:5')]
else:
    steps += [('open-about', 'tap@desktop-settings-about', 'tag:desktop-about,tag:desktop-about-sponsors'),
              ('sponsors', 'tap@desktop-about-sponsors', 'tag:desktop-message-dialog'),
              ('dismiss', 'tap@desktop-dialog:ui.common.ok', 'absent:desktop-message-dialog,tag:desktop-about')]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', locale, '--source', 'Local', '--start-tab', 'Settings',
        '--width', '430' if mode == 'mobile' else '1280', '--height', '850' if mode == 'mobile' else '900',
        '--output', str(output), '--startup-wait-ms', '1200', '--wait-ms', '250',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
                             '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
                             '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                            cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
scene = next(scene for scene in data['scenes'] if scene['file'].endswith('-sponsors.png'))
nodes = json.loads((output / scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
texts = '\n'.join(node['text'] for node in nodes if node.get('text') and node['bounds'][2] > node['bounds'][0] and node['bounds'][3] > node['bounds'][1])
for amount in ('CNY 50', 'CNY 6', 'CNY 5', 'CNY 8.88', 'CNY 20', 'CNY 12.24'):
    assert amount in texts, (mode, locale, amount, texts)
for identity in ('26d40', 'aec4c', 'Bai', '中末', '361ef', 'f8b67'):
    assert identity in texts, (mode, locale, identity, texts)
assert all(assertion['passed'] for item in data['scenes'] for assertion in item.get('assertions', []))
print(f'PASS about sponsors {mode} {locale}: {len(data["scenes"])} scenes; six visible names and amounts')
