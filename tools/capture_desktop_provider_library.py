#!/usr/bin/env python3
"""Desktop library for a non-NetEase source (Local): no phone segmented picker."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output = (Path(value).resolve() for value in sys.argv[1:3])
theme = sys.argv[3] if len(sys.argv) > 3 else 'Dark'
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
steps = [
    ('consent', 'tap@remote-reject', 'cloud-enabled:false'),
    ('library', 'tap@desktop-nav:Songs', 'tag:desktop-provider-library:Local', 'absent:library-segmented-picker'),
]
steps_file = output / 'steps.txt'
expect_file = output / 'expect.txt'
steps_file.write_text('\n'.join(f'{name}:{action}' for name, action, *_ in steps) + '\n', encoding='utf-8')
expect_file.write_text('\n'.join(f'{name}={",".join(checks)}' for name, _, *checks in steps if checks) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Local', '--start-tab', 'Library', '--theme', theme,
        '--width', '1280', '--height', '800', '--output', str(output), '--wait-ms', '220',
        '--startup-wait-ms', '1200', '--steps-file', str(steps_file), '--expect-file', str(expect_file)]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-12000:])
raise SystemExit(result.returncode)
