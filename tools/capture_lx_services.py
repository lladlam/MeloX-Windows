#!/usr/bin/env python3
"""Pointer/keyboard replay of third-party source import in an isolated home.

The temporary loopback server supplies a test script only; it does not simulate
an account or claim evidence of actual music-provider playback.
"""
import http.server
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import threading

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
    assert prefs.exists(), 'Reload requires the controls home'
output.mkdir(parents=True, exist_ok=True)

fixture = (b'/*\n * @name LX fixture\n * @version 1\n */\n'
           b"globalThis.lx.on('request', () => 'https://example.invalid/fixture.mp3');\n")
downloads = []


class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        downloads.append(self.path)
        self.send_response(200)
        self.send_header('Content-Type', 'application/javascript; charset=utf-8')
        self.send_header('Content-Length', str(len(fixture)))
        self.end_headers()
        self.wfile.write(fixture)

    def log_message(self, *_args):
        pass


server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), Handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f'http://127.0.0.1:{server.server_port}/source.js'
steps = []


def add(name, action, *checks):
    steps.append((name, action, checks))


if mode == 'controls':
    add('cloud-consent', 'tap@remote-reject', 'cloud-enabled:false')
add('reveal-services', 'reveal@settings-open-services')
add('services', 'tap@settings-open-services', 'tag:' + ('desktop-provider-services' if desktop else 'provider-current-source'))
add('reveal-lx', 'reveal@lx-enabled')
if mode == 'controls':
    add('enable', 'tap@lx-enabled', 'tag:lx-consent')
    add('terms', 'tap@lx-consent-terms', 'tag:legal-document')
    add('close-terms', 'tap@legal-document-close', 'tag:lx-consent')
    add('agree', 'tap@lx-consent-accept', 'checked:lx-enabled')
    add('membership', 'tap@lx-membership', 'checked:lx-membership')
    add('reveal-add', 'reveal@lx-add')
    add('open-import', 'tap@lx-add', 'tag:lx-import-dialog')
    add('url-focus', 'tap@lx-import-url')
    add('type-url', 'type@' + url, 'enabled:lx-import-submit')
    add('import', 'tap@lx-import-submit', 'absent:lx-import-dialog', 'text:LX fixture')
else:
    add('persisted', 'wait@250', 'checked:lx-enabled', 'checked:lx-membership')
    add('reveal-add', 'reveal@lx-add', 'text:LX fixture')
    add('remove', 'tap-first@lx-delete:')
    add('disable', 'reveal@lx-enabled')
    add('disabled', 'tap@lx-enabled', 'unchecked:lx-enabled', 'absent:lx-add')

(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={",".join(checks)}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '1100' if desktop else '430',
        '--height', '850', '--output', str(output), '--wait-ms', '250', '--startup-wait-ms', '1200',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
try:
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
    gate = (home / '.melox/prefs_melox_third_party_music_sources.properties').read_text(encoding='iso-8859-1')
    assert 'enabled=' + ('true' if mode == 'controls' else 'false') in gate
    stored = (home / '.melox/prefs_melox_lxuser_sources.properties').read_text(encoding='iso-8859-1')
    assert ('LX fixture' in stored) == (mode == 'controls')
    if mode == 'controls':
        assert downloads == ['/source.js'], downloads
        scripts = list((home / '.melox/lx-user-sources').glob('*.js'))
        assert len(scripts) == 1 and scripts[0].read_bytes() == fixture
    else:
        assert not list((home / '.melox/lx-user-sources').glob('*.js'))
    print(f'PASS LX {layout}/{mode}: {len(capture["scenes"])} scenes / {len(assertions)} assertions')
finally:
    server.shutdown()
    server.server_close()
