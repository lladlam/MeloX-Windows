#!/usr/bin/env python3
"""Real pointer/Escape settings push, pop and in-flight reversal; independent two-page geometry."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

mode, home, output = sys.argv[1:4]
assert mode in ('single', 'split', 'reduced')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use an isolated fresh home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=' + str(mode == 'split').lower()
                 + '\nreduce_motion=' + str(mode == 'reduced').lower() + '\n', encoding='utf-8')
steps = [
    'consent:tap@remote-reject',
    'ready:wait@600',
    'anchor:remember-point@settings-open-general',
    'open:tap@settings-open-general',
]
if mode == 'single':
    steps += ['early-close:escape', 'early-closed:wait@700', 'open-after-cancel:tap@settings-open-general']
steps += ['settled:wait@700', 'close:escape']
if mode == 'single':
    steps += ['reverse:tap-saved@anchor', 'restored:wait@700', 'close-again:escape']
steps += ['closed:wait@700', 'open-again:tap@settings-open-general', 'settled-again:wait@700', 'last-close:escape']
if mode != 'reduced':
    steps += ['closing-escape:escape']
steps += ['last-closed:wait@700']
checks = {
    'consent': ['cloud-enabled:false'],
    'ready': ['tag:settings-open-general'],
    'settled': ['tag:settings-general'],
    'closed': ['absent:settings-general', 'tag:settings-open-general'],
    'settled-again': ['tag:settings-general'],
    'last-closed': ['absent:settings-general', 'tag:settings-open-general'],
}
if mode == 'single':
    checks['restored'] = ['tag:settings-general']
    checks['early-closed'] = ['absent:settings-general', 'tag:settings-open-general']
for name, lines in [('steps.txt', steps), ('expect.txt', [key + '=' + ','.join(value) for key, value in checks.items()])]:
    (output / name).write_text('\n'.join(lines) + '\n', encoding='utf-8')
width = 1100 if mode == 'split' else 427
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Light',
        '--width', str(width), '--height', '850', '--output', str(output), '--wait-ms', '0',
        '--startup-wait-ms', '600', '--frames-ms', '0,50,100', '--controlled-animation-clock',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(encoding='utf-8', errors='replace')[-14000:])
    raise SystemExit(result.returncode)
subprocess.run([sys.executable, str(repo / 'tools/verify_page_motion_capture.py'), mode, str(output)], check=True)
