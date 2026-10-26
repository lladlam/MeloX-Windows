#!/usr/bin/env python3
"""Real input and Local decoding for app-owned favorites; no provider account fixture is used."""
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import wave

layout, mode, home, output = sys.argv[1:5]
assert layout in ('mobile', 'desktop') and mode in ('controls', 'reload')
home, output = Path(home).resolve(), Path(output).resolve()
repo = Path(__file__).resolve().parents[1]
output.mkdir(parents=True, exist_ok=True)
desktop = layout == 'desktop'
prefs = home / '.melox/prefs_melox_app_settings.properties'
audio_dir = home / 'generated-audio'
if mode == 'controls':
    assert not prefs.exists(), 'Use a fresh isolated home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                     f'general_desktop_layout={str(desktop).lower()}\n', encoding='utf-8')
    audio_dir.mkdir()
    pcm = b''.join(struct.pack('<h', int(1000 * math.sin(i * math.tau * 220 / 16000))) for i in range(16000))
    for name in ('Generated-A', 'Generated-B'):
        with wave.open(str(audio_dir / (name + '.wav')), 'wb') as audio:
            audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
            for _ in range(45):
                audio.writeframes(pcm)
else:
    assert prefs.exists(), 'Reload must use the preceding control home'

def file_key(name):
    file = audio_dir / (name + '.wav')
    return hashlib.sha256(f'{file}:{file.stat().st_size}'.encode('utf-8')).hexdigest()[:24]

def legacy_id(key):
    value = -0x340d631b7bdddcdb
    for char in 'track:local:' + key:
        value = ((value ^ ord(char)) * 0x100000001b3) & ((1 << 64) - 1)
    if value >= (1 << 63):
        value -= (1 << 64)
    return -value if value > 0 else value

steps, checks = [], []
def add(name, action, *expect):
    steps.append(f'{name}:{action}')
    if expect:
        checks.append(name + '=' + ','.join(expect))

def settings(suffix):
    add('settings-' + suffix, 'tap@' + ('desktop-settings' if desktop else 'root-tab:Settings'))
    content(suffix)

def content(suffix):
    if desktop:
        add('general-' + suffix, 'tap@desktop-settings-tab:General')
        add('content-' + suffix, 'tap@desktop-settings-subtab:Content')
    else:
        add('content-reveal-' + suffix, 'reveal@settings-open-content')
        add('content-' + suffix, 'tap@settings-open-content')
    add('toggle-reveal-' + suffix, 'reveal@unified-favorites-enabled')

def library(suffix):
    if not desktop:
        add('settings-back-' + suffix, 'escape')
    add('library-' + suffix, 'tap@' + ('desktop-nav:Songs' if desktop else 'root-tab:Library'))

if mode == 'controls':
    add('consent', 'tap@remote-reject')
    content('initial')
    add('setup', 'tap@unified-favorites-enabled', 'tag:unified-favorites-create')
    add('create-empty', 'tap@unified-favorites-create')
    add('created', 'wait@600', 'tag:unified-favorites-status')
    library('initial')
    if not desktop:
        add('songs', 'tap@library-page:Songs')
    for index, title in enumerate(('Generated-A', 'Generated-B')):
        key = file_key(title)
        tag = f'desktop-collection-more:{legacy_id(key)}' if desktop else f'provider-library-song-menu:{key}'
        add(f'menu-{index}', 'tap@' + tag, 'tag:desktop-song-menu' if desktop else 'tag:song-actions')
        add(f'read-favorite-{index}', 'wait@400')
        add(f'like-{index}', 'tap@' + ('desktop-song-favorite' if desktop else 'song-action-喜爱'))
        if not desktop:
            add(f'liked-{index}', 'wait@300', 'tag:song-action-取消喜爱')
            add(f'close-menu-{index}', 'tap@song-actions-close', 'absent:song-actions')
    add('open-favorites', 'tap@unified-favorites-library', 'state:unified-favorites-detail:count=2')
    add('reorder', 'tap@unified-favorites-down:0', 'state:unified-favorites-track:0:local|' + file_key('Generated-B'))
    add('play-real-local', 'tap@unified-favorites-play')
    add('decoded', 'wait@1600', 'playing:true', 'no-playback-error:true')
    add('pause', 'tap@mini-player-play-pause', 'playing:false')
    add('remove-one', 'tap@unified-favorites-remove:0', 'state:unified-favorites-detail:count=1')
    add('close-detail', 'tap@unified-favorites-back')
    settings('disable')
    add('disable', 'tap@unified-favorites-enabled', 'absent:unified-favorites-status')
    library('disabled')
    add('hidden', 'wait@200', 'absent:unified-favorites-library')
    settings('enable')
    add('enable-existing', 'tap@unified-favorites-enabled', 'tag:unified-favorites-status', 'absent:unified-favorites-create')
    library('enabled')
add('open-persisted', 'tap@unified-favorites-library', 'state:unified-favorites-detail:count=1',
    'state:unified-favorites-track:0:local|' + file_key('Generated-A'))
if mode == 'reload':
    add('play-reloaded', 'tap@unified-favorites-play')
    add('decoded-reloaded', 'wait@1600', 'playing:true', 'no-playback-error:true')
    add('pause-reloaded', 'tap@mini-player-play-pause', 'playing:false')

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(checks) + '\n', encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local' if mode == 'controls' else 'Netease',
        '--start-tab', 'Settings' if mode == 'controls' else 'Library', '--theme', 'Dark',
        '--width', '1280' if desktop else '430', '--height', '850', '--output', str(output),
        '--wait-ms', '400', '--startup-wait-ms', '1200', '--capture-pcm',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
if mode == 'controls':
    args += ['--scan-local-dir', str(audio_dir)]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    sys.exit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
assert sum(p.get('written_frames', 0) for p in capture['pcm_outputs']) > 44100
summary = {'layout': layout, 'mode': mode, 'scenes': len(capture['scenes']), 'assertions': len(assertions),
           'real_local_pcm': True, 'physical_audible': False, 'private_account_import_verified': False}
(output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary))
