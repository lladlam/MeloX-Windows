#!/usr/bin/env python3
"""Use real pointer and keyboard input to exercise YouTube login validation."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

layout, home_arg, output_arg = sys.argv[1:4]
assert layout in ('mobile', 'desktop')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
provider = 'desktop-service-login:youtubemusic' if layout == 'desktop' else 'provider-login-youtubemusic'
steps = [
    ('consent', 'tap@remote-reject', 'cloud-enabled:false'),
    ('services', 'tap@settings-open-services', 'tag:' + ('desktop-provider-services' if layout == 'desktop' else provider)),
    ('reveal', 'reveal@' + provider, ''),
    ('open', 'tap@' + provider, 'tag:account-form-youtubemusic,disabled:youtube-login-submit'),
    ('cookie-focus', 'tap@youtube-cookie', ''),
    ('visitor-cookie', 'type@VISITOR_INFO1_LIVE=fixture', 'disabled:youtube-login-submit'),
    ('select-cookie', 'select-all', ''),
    ('signed-cookie', 'type@__Secure-3PAPISID=fixture-only', 'enabled:youtube-login-submit'),
    ('auth-focus', 'tap@youtube-auth-user', ''),
    ('select-auth', 'select-all', ''),
    ('invalid-auth', 'type@abc', 'disabled:youtube-login-submit'),
    ('reselect-auth', 'select-all', ''),
    ('valid-auth', 'type@2', 'enabled:youtube-login-submit'),
    ('close', 'tap@account-dialog-close', 'absent:account-form-youtubemusic'),
]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings',
        '--width', '1100' if layout == 'desktop' else '430', '--height', '850', '--output', str(output),
        '--startup-wait-ms', '1200', '--wait-ms', '220', '--steps-file', str(output / 'steps.txt'),
        '--expect-file', str(output / 'expect.txt')]
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
assertions = [assertion for scene in data['scenes'] for assertion in scene.get('assertions', [])]
assert assertions and all(assertion['passed'] for assertion in assertions)
assert not any('youtube_music_session' in path.name for path in (home / '.melox').iterdir()), 'Fixture credentials must never be saved'
print(f'PASS YouTube login {layout}: {len(data["scenes"])} scenes / {len(assertions)} assertions')
