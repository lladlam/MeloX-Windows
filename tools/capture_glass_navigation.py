#!/usr/bin/env python3
"""Exercise the production dock/capture/droplet with real pointer drags over Local Library."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output = (Path(p).resolve() for p in sys.argv[1:3])
theme = sys.argv[3] if len(sys.argv) > 3 else 'Dark'
assert theme in ('Dark', 'Light')
output.mkdir(parents=True, exist_ok=True)
steps = [
    ('remote-reject', 'tap@remote-reject', ['cloud-enabled:false']),
    ('settings', '890,745', ['tag:settings-root', 'state:bottom-root-tabs:Home>Explore>Library>Settings']),
    ('drag-library', 'hold-drag@bottom-root-tabs,-248,0', ['absent:settings-root', 'tag:library-pages']),
    ('drag-settings', 'hold-drag@bottom-root-tabs,248,0', ['tag:settings-root']),
    ('repeat-library', 'hold-drag@bottom-root-tabs,-248,0', ['absent:settings-root', 'tag:library-pages']),
    ('repeat-settings', 'hold-drag@bottom-root-tabs,248,0', ['tag:settings-root']),
]
(output / 'steps.txt').write_text('\n'.join(f'{name}:{action}' for name, action, _ in steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(f'{name}=' + ','.join(checks) for name, _, checks in steps) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Local', '--start-tab', 'Settings', '--theme', theme,
        '--width', '1100', '--height', '780', '--output', str(output), '--wait-ms', '180',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
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
assert len(capture['steps']) == len(steps) and checks and all(a['passed'] for a in checks)
print(f"PASS glass dock {theme}: {len(capture['scenes'])} scenes / {len(checks)} assertions")
