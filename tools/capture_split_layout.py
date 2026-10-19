#!/usr/bin/env python3
"""Physical settings/pane/Dock/mini-player replay and independent preferences reload."""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import wave

mode, home, output = sys.argv[1:4]
assert mode in ('controls', 'reload', 'portrait')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
home.mkdir(parents=True, exist_ok=True)
fixture = home / 'generated-split-pcm.wav'
if not fixture.exists():
    # This is real decoded PCM through the production FFmpeg/player, isolated
    # from the user's library. Never populate a production provider with fixtures.
    with wave.open(str(fixture), 'wb') as audio:
        audio.setnchannels(1)
        audio.setsampwidth(2)
        audio.setframerate(16000)
        second = b''.join(struct.pack('<h', int(4000 * math.sin(i * 2 * math.pi * 220 / 16000))) for i in range(16000))
        for _ in range(420):
            audio.writeframesraw(second)
strings = dict(line.split('=', 1) for line in (repo / 'desktop/src/main/resources/parity/pinned-v065-split-layout.properties')
               .read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))
steps, checks = [], {}


def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    if assertions:
        checks[name] = list(assertions)


def choose(name, key, suffix, value, runtime):
    add('reveal-' + name, 'reveal@' + key)
    add('menu-' + name, 'tap@' + key)
    add('choose-' + name, 'tap@settings-option:' + strings['settings_position_' + suffix],
        [f'pref-string:{key}:{value}', f'runtime:{runtime}:{value}'])


add('remote-reject', 'tap@remote-reject', ['cloud-enabled:false']) if mode == 'controls' else None
add('reveal-general', 'reveal@settings-open-general')
add('open-general', 'tap@settings-open-general', ['tag:settings-general'])
if mode == 'controls':
    add('split-default', 'wait@100', ['runtime:splitScreenEnabled:true', 'tag:layout-list-detail', 'in-half:settings-general:right'])
    add('reveal-toggle', 'reveal@setting-general_split_screen')
    add('disable', 'tap@setting-general_split_screen', ['runtime:splitScreenEnabled:false', 'tag:layout-single-pane',
        'full-tag:settings-general', 'absent:general_split_navigation_position', 'absent:general_split_mini_player_position'])
    add('enable', 'tap@setting-general_split_screen', ['runtime:splitScreenEnabled:true', 'tag:layout-list-detail', 'in-half:settings-general:right'])
    positions = [('bottom_right', 'BottomRight'), ('left', 'Left'), ('right', 'Right'),
                 ('top_left', 'TopLeft'), ('top_right', 'TopRight'), ('bottom_left', 'BottomLeft')]
    for i, (suffix, value) in enumerate(positions):
        choose('nav-' + str(i), 'general_split_navigation_position', suffix, value, 'splitNavigationPosition')
        # Different control positions exercise independent hit layers and pane reservations.
        mini_suffix, mini_value = positions[(i + 2) % len(positions)]
        choose('mini-' + str(i), 'general_split_mini_player_position', mini_suffix, mini_value, 'splitMiniPlayerPosition')
        add('placement-' + str(i), 'wait@150', ['tag:layout-list-detail', 'in-half:settings-general:right',
            'chrome-placement:positioned-navigation:' + value, 'chrome-placement:positioned-mini-player:' + mini_value])
    # Persist distinct vertical rails for the fresh JVM reload.
    choose('final-nav', 'general_split_navigation_position', 'left', 'Left', 'splitNavigationPosition')
    choose('final-mini', 'general_split_mini_player_position', 'right', 'Right', 'splitMiniPlayerPosition')
    add('pause', 'tap@mini-player-play-pause', ['play-ready:false', 'no-playback-error:true'])
    add('resume', 'tap@mini-player-play-pause', ['play-ready:true', 'no-playback-error:true'])
else:
    add('persisted', 'wait@100', ['runtime:splitScreenEnabled:true', 'runtime:splitNavigationPosition:Left',
        'runtime:splitMiniPlayerPosition:Right', 'pref-string:general_split_navigation_position:Left',
        'pref-string:general_split_mini_player_position:Right'] +
        (['tag:layout-single-pane', 'full-tag:settings-general'] if mode == 'portrait' else
         ['tag:layout-list-detail', 'in-half:settings-general:right', 'chrome-placement:positioned-navigation:Left',
          'chrome-placement:positioned-mini-player:Right']))
    add('pause', 'tap@mini-player-play-pause', ['play-ready:false', 'no-playback-error:true'])
    add('resume', 'tap@mini-player-play-pause', ['play-ready:true', 'no-playback-error:true'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items()) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark',
        '--width', '427' if mode == 'portrait' else '1100', '--height', '924' if mode == 'portrait' else '780',
        '--local-wavs', str(fixture), '--capture-pcm', '--output', str(output), '--wait-ms', '650',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--no-daemon', '--console=plain',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-65:]))
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in data['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
assert sum(endpoint['written_frames'] for endpoint in data['pcm_outputs']) > 44100, 'Production decoder wrote no real PCM'
print(f'PASS v0.6.5 split {mode}: {len(data["scenes"])} scenes / {len(assertions)} assertions')
