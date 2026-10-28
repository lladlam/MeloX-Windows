#!/usr/bin/env python3
"""Pointer replay for concept settings and distinct service entries; never signs into a fixture account."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

layout, mode, home_arg, output_arg = sys.argv[1:5]
assert layout in ('mobile', 'desktop') and mode in ('controls', 'reload')
desktop = layout == 'desktop'
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
if mode == 'controls':
    assert not prefs.exists(), 'Use a fresh isolated home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                     f'general_desktop_layout={str(desktop).lower()}\n', encoding='utf-8')
else:
    assert prefs.exists(), 'Reload requires the prior controls home'
output.mkdir(parents=True, exist_ok=True)
steps = []


def add(name, action, *checks):
    steps.append((name, action, checks))


if mode == 'controls':
    add('consent', 'tap@remote-reject', 'cloud-enabled:false')
if desktop:
    add('features', 'tap@desktop-settings-tab:Features')
    add('reveal-settings', 'reveal@desktop-settings-subtab:KugouLiteVip')
    add('open-settings', 'tap@desktop-settings-subtab:KugouLiteVip', 'tag:settings-page:KugouLiteVip')
else:
    add('reveal-settings', 'reveal@settings-open-kugoulitevip')
    add('open-settings', 'tap@settings-open-kugoulitevip', 'tag:settings-kugoulitevip')
add('reveal-checkin', 'reveal@kugou-lite-checkin')
add('reveal-auto', 'reveal@kugou-lite-auto')
add('signed-out', 'wait@300', 'tag:kugou-lite-account', 'disabled:kugou-lite-checkin',
    ('checked:' if mode == 'controls' else 'unchecked:') + 'kugou-lite-auto')
add('auto-toggle', 'tap@kugou-lite-auto', ('unchecked:' if mode == 'controls' else 'checked:') + 'kugou-lite-auto')
if mode == 'controls':
    if not desktop:
        add('back', 'escape', 'tag:settings-root')
    else:
        add('general', 'tap@desktop-settings-tab:General', 'tag:settings-general')
    add('reveal-services', 'reveal@settings-open-services')
    add('services', 'tap@settings-open-services', 'tag:' + ('desktop-provider-services' if desktop else 'provider-login-kugou'))
    picker = 'desktop-service-current-source' if desktop else 'provider-current-source'
    standard_option = 'settings-option:Kugou Music' if desktop else 'provider-source-kugou'
    concept_option = 'settings-option:Kugou Concept' if desktop else 'provider-source-kugou_lite'
    add('source-picker', 'tap@' + picker, 'selected:' + concept_option)
    add('source-standard', 'tap@' + standard_option)
    add('source-picker-standard', 'tap@' + picker, 'selected:' + standard_option)
    add('source-concept', 'tap@' + concept_option)
    add('source-picker-concept', 'tap@' + picker, 'selected:' + concept_option)
    add('source-close', 'tap@' + concept_option)
    standard = 'desktop-service-login:kugou' if desktop else 'provider-login-kugou'
    concept = 'desktop-service-login:kugou_lite' if desktop else 'provider-login-kugou_lite'
    add('distinct-standard', 'reveal@' + standard, 'tag:' + standard)
    add('distinct-concept', 'reveal@' + concept, 'tag:' + concept)
    add('open-login', 'tap@' + concept, 'tag:account-form-kugou_lite')
    add('close-login', 'tap@account-dialog-close', 'absent:account-form-kugou_lite')
    add('back-services', 'tap@account-back', 'tag:' + ('desktop-provider-services' if desktop else 'provider-current-source'))
    add('reveal-standard-login', 'reveal@' + standard)
    add('open-standard-login', 'tap@' + standard, 'tag:account-form-kugou', 'absent:account-form-kugou_lite')
    add('close-standard-login', 'tap@account-dialog-close', 'absent:account-form-kugou')

(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={",".join(checks)}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'KugouLite', '--start-tab', 'Settings', '--width', '1100' if desktop else '430',
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
checkin = home / '.melox/prefs_melox_kugou_lite_checkin.properties'
assert 'auto_enabled=' + ('false' if mode == 'controls' else 'true') in checkin.read_text(encoding='iso-8859-1')
print(f'PASS concept {layout}/{mode}: {len(capture["scenes"])} scenes / {len(assertions)} assertions')
