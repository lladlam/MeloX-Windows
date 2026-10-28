#!/usr/bin/env python3
"""Restart an isolated enabled CLI home, issue a real command and turn it off by pointer."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

home, output = (Path(value).resolve() for value in sys.argv[1:3])
repo = Path(__file__).resolve().parents[1]
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert 'experimental_cli_enabled=true' in prefs.read_text(encoding='iso-8859-1')
endpoint = home / '.melox/cli-endpoint.json'
assert not endpoint.exists()
output.mkdir(parents=True, exist_ok=True)
steps = [('reveal', 'reveal@settings-open-experimental', ''),
         ('experimental', 'tap@settings-open-experimental', 'tag:settings-experimental,checked:setting-experimental_cli_enabled'),
         ('connected', 'wait@10000', 'checked:setting-experimental_cli_enabled'),
         ('disable', 'tap@setting-experimental_cli_enabled', 'unchecked:setting-experimental_cli_enabled,pref-bool:experimental_cli_enabled:false')]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '430', '--height', '850',
        '--output', str(output), '--startup-wait-ms', '1200', '--wait-ms', '220',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
helper = [sys.executable, str(repo / 'tools/melox_cli.py'), '--home', str(home), 'status']
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
                                '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
                                '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                               cwd=repo, stdout=log, stderr=subprocess.STDOUT)
    try:
        deadline = time.monotonic() + 180
        while not endpoint.exists():
            assert process.poll() is None and time.monotonic() < deadline, 'CLI was not restored on restart'
            time.sleep(.1)
        result = subprocess.run(helper, cwd=repo, capture_output=True, text=True, encoding='utf-8', timeout=20)
        response = json.loads(result.stdout)
        assert result.returncode == 0 and response['ok']
        (output / 'cli-status.json').write_text(json.dumps(response, indent=2, ensure_ascii=False), encoding='utf-8')
        assert process.wait(timeout=120) == 0
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
assert not endpoint.exists()
assert 'experimental_cli_enabled=false' in prefs.read_text(encoding='iso-8859-1')
result = subprocess.run(helper, cwd=repo, capture_output=True, text=True, encoding='utf-8', timeout=20)
assert result.returncode != 0 and not json.loads(result.stdout)['ok']
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for scene in capture['scenes'] for a in scene.get('assertions', []))
print(f'PASS CLI independent restart: {len(capture["scenes"])} scenes')
