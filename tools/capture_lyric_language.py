#!/usr/bin/env python3
"""v0.6.5 language choices by physical pointer and a separate-JVM/locale reload."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

mode, home, output = sys.argv[1:4]
assert mode in ('controls', 'reload')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
steps, checks = [], {}


def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    if assertions:
        checks[name] = list(assertions)


strings = dict(line.split('=', 1) for line in (repo / 'desktop/src/main/resources/parity/pinned-v065-lyric-language.properties')
               .read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))
if mode == 'controls':
    add('remote-reject', 'tap@remote-reject', ['cloud-enabled:false', 'lyric-language:system', 'lyric-preference:en:Original'])
else:
    checks['00-settings'] = ['lyric-language:tr', 'lyric-preference:tr:Original', 'pref-string:lyric_language:tr']
add('reveal-lyrics', 'reveal@settings-open-lyrics')
add('open-lyrics', 'tap@settings-open-lyrics', ['tag:settings-lyrics'])
key = 'lyric_language'
if mode == 'controls':
    values = [('zh-Hans', 'zh_hans', 'zh:Simplified'), ('zh-Hant', 'zh_hant', 'zh:Traditional'),
              ('en', 'en', 'en:Original'), ('tr', 'tr', 'tr:Original'), ('system', 'system', 'en:Original'),
              ('tr', 'tr', 'tr:Original')]
else:
    add('reveal-persisted', f'reveal@{key}')
    add('persisted', 'wait@100', ['lyric-language:tr', 'text:Türkçe', 'text:' + strings['settings_lyrics_language']])
    # Explicit persisted Turkish overrides this JVM's Traditional-Chinese locale.
    # Returning to System must immediately resolve the new locale, without a restart.
    values = [('system', 'system', 'zh:Traditional'), ('tr', 'tr', 'tr:Original')]
for index, (value, suffix, preference) in enumerate(values):
    add(f'reveal-{index}', f'reveal@{key}')
    add(f'menu-{index}', f'tap@{key}')
    add(f'choose-{index}', 'tap@settings-option:' + strings['settings_lyrics_language_' + suffix],
        [f'lyric-language:{value}', f'pref-string:{key}:{value}', f'lyric-preference:{preference}'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items()) + '\n', encoding='utf-8')
args = ['--locale', 'en-US' if mode == 'controls' else 'zh-TW', '--source', 'Local', '--start-tab', 'Settings',
        '--theme', 'Dark', '--width', '1100', '--height', '780', '--output', str(output), '--wait-ms', '650',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--no-daemon', '--console=plain',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-60:]))
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in data['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
print(f'PASS v0.6.5 lyric language {mode}: {len(data["scenes"])} scenes / {len(assertions)} assertions')
