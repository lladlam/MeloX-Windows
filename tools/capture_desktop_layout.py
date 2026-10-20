#!/usr/bin/env python3
"""Physical layout toggle, desktop navigation/player and separate-JVM reload."""
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
assert mode in ('controls', 'reload', 'restore', 'reload-current', 'home-dark', 'home-light', 'lyrics-light')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
home.mkdir(parents=True, exist_ok=True)
fixtures = []
for index, hz in enumerate([220, 330]):
    path = home / f'generated-desktop-{index}.wav'
    if not path.exists():
        with wave.open(str(path), 'wb') as audio:
            audio.setnchannels(1)
            audio.setsampwidth(2)
            audio.setframerate(16000)
            second = b''.join(struct.pack('<h', int(4000 * math.sin(i * 2 * math.pi * hz / 16000))) for i in range(16000))
            for _ in range(420):
                audio.writeframesraw(second)
    fixtures.append(path)
lyrics = home / 'generated-desktop.lrc'
lyrics.write_text('\n'.join(f'[{i * 4 // 60:02d}:{i * 4 % 60:02d}.00]Desktop generated lyric line {i + 1}' for i in range(100)) + '\n', encoding='utf-8')
steps, checks = [], {}

def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    if assertions:
        checks[name] = list(assertions)

if mode == 'lyrics-light':
    add('consent', 'tap@remote-reject', ['cloud-enabled:false', 'absent:remote-reject'])
    add('show-real-local-lyrics', 'tap@desktop-show-lyrics', ['tag:desktop-inspector:Lyrics', 'tag:player-lyrics', 'tag-prefix:lyric-line-'])
    add('seek-by-real-pointer', 'tap@lyric-line-1', ['position-min:4000', 'no-playback-error:true'])
    add('close-lyrics', 'tap@desktop-show-lyrics', ['absent:desktop-inspector:Lyrics'])
    add('expand-light-player', 'tap@mini-player-expand', ['tag:desktop-player-column', 'tag:player-lyrics'])
elif mode.startswith('home-'):
    add('consent', 'tap@remote-reject', ['cloud-enabled:false', 'absent:remote-reject'])
    add('desktop-home', 'wait@1500', ['tag:desktop-home', 'tag:desktop-sidebar', 'runtime:desktopLayoutEnabled:true',
        'tag:desktop-home-action:daily_songs', 'tag:desktop-home-action:heart_mode', 'tag:desktop-home-action:private_roaming'])
elif mode == 'controls':
    add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
    add('current-default', 'wait@100', ['runtime:desktopLayoutEnabled:false', 'tag:interface-current', 'tag:bottom-root-tabs'])
else:
    add('persisted-mode', 'wait@100', ['runtime:desktopLayoutEnabled:' + ('false' if mode == 'reload-current' else 'true'),
        'tag:interface-current' if mode == 'reload-current' else 'tag:desktop-sidebar'])
if not mode.startswith('home-') and mode != 'lyrics-light':
    if mode in ('reload', 'restore'):
        # These modes start directly in the desktop settings window, whose
        # General entry is a fixed tab rather than a scrollable legacy list row.
        add('general', 'wait@100', ['tag:settings-general'])
    else:
        add('reveal-general', 'reveal@settings-open-general')
        add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
if mode == 'controls':
    add('playback-before-switch', 'wait@100', ['playing:true', 'playback-checkpoint:switch'])
    for index in range(3):
        add('desktop-' + str(index), 'tap@setting-general_desktop_layout', ['tag:desktop-sidebar', 'tag:desktop-bottom-player',
            'tag:settings-general', 'runtime:desktopLayoutEnabled:true', 'absent:bottom-root-tabs', 'pref-bool:general_desktop_layout:true', 'playback-continuity:switch'])
        if index < 2:
            add('current-' + str(index), 'tap@setting-general_desktop_layout', ['tag:settings-general', 'tag:bottom-root-tabs',
                'tag:layout-list-detail', 'runtime:desktopLayoutEnabled:false', 'pref-bool:general_desktop_layout:false', 'playback-continuity:switch'])
if mode in ('controls', 'reload'):
    add('pause', 'tap@mini-player-play-pause', ['play-ready:false', 'no-playback-error:true'])
    add('resume', 'tap@mini-player-play-pause', ['play-ready:true', 'no-playback-error:true'])
    add('queue', 'tap@desktop-show-queue', ['tag:desktop-inspector:Queue', 'tag:desktop-queue-track:1'])
    add('select-real-track', 'tap@desktop-queue-track:1', ['no-playback-error:true'])
    add('close-queue', 'tap@desktop-show-queue', ['absent:desktop-inspector:Queue'])
    add('volume', 'tap@desktop-volume', ['tag:desktop-volume-slider'])
    add('set-volume', 'slider@desktop-volume-slider,0.4', ['volume:0.4'])
    add('collapse-volume', 'tap@desktop-volume', ['absent:desktop-volume-slider'])
    add('expand-player', 'tap@mini-player-expand', ['tag:desktop-player-column', 'tag:desktop-player-panel', 'absent:desktop-bottom-player'])
    add('full-queue', 'tap@desktop-full-queue', ['tag:desktop-queue-autoplay', 'tag:desktop-queue-automix'])
    add('full-pause', 'tap@mini-player-play-pause', ['play-ready:false', 'no-playback-error:true'])
    add('full-resume', 'tap@mini-player-play-pause', ['play-ready:true', 'no-playback-error:true'])
    add('collapse-player', 'tap@player-collapse', ['tag:desktop-bottom-player', 'tag:settings-general'])
    add('search', 'tap@desktop-nav:Search', ['tag:desktop-sidebar', 'absent:settings-general'])
    add('songs', 'tap@desktop-nav:Songs', ['tag:library-pages'])
    add('settings', 'tap@desktop-settings', ['tag:settings-root'])
    add('reopen-general', 'tap@settings-open-general', ['tag:settings-general', 'checked:setting-general_desktop_layout'])
elif mode == 'restore':
    add('restore-current', 'tap@setting-general_desktop_layout', ['tag:interface-current', 'tag:settings-general',
        'tag:bottom-root-tabs', 'runtime:desktopLayoutEnabled:false', 'pref-bool:general_desktop_layout:false'])
elif mode == 'reload-current':
    add('current-persisted', 'wait@100', ['tag:interface-current', 'tag:bottom-root-tabs', 'unchecked:setting-general_desktop_layout'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items()) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Netease' if mode.startswith('home-') else 'Local',
    '--start-tab', 'Home' if mode.startswith('home-') else 'Settings', '--theme', 'Light' if mode in ('home-light', 'lyrics-light') else 'Dark',
    '--width', '1280', '--height', '800', '--capture-pcm',
    '--output', str(output), '--wait-ms', '650', '--startup-wait-ms', '1200',
    '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
if not mode.startswith('home-'):
    args += ['--scan-local-dir', str(home), '--local-lyrics-file', str(lyrics), '--local-wavs', ','.join(map(str, fixtures))]
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
assert len(data['scenes']) == len(data['steps']) + 1
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
if not mode.startswith('home-'):
    assert sum(x['written_frames'] for x in data['pcm_outputs']) > 44100
geometry = []
for scene in data['scenes']:
    nodes = json.loads((output / scene['file'].replace('.png', '.semantics.json')).read_text(encoding='utf-8'))
    for tag, expected_width, expected_height in [('desktop-sidebar', 199, None), ('desktop-bottom-player', 700, 54),
        ('desktop-inspector:Queue', 258, None)]:
        node = next((n for n in nodes if n.get('tag') == tag), None)
        if node:
            left, top, right, bottom = node['bounds']
            assert abs((right-left) / data['density'] - expected_width) <= 1, (scene['file'], tag, node['bounds'])
            if expected_height:
                assert abs((bottom-top) / data['density'] - expected_height) <= 1
            geometry.append({'scene': scene['file'], 'tag': tag, 'bounds': node['bounds'], 'source_metrics_match': True})
(output / 'source-geometry.json').write_text(json.dumps(geometry, indent=2) + '\n', encoding='utf-8')
print(f'PASS Desktop {mode}: {len(data["scenes"])} scenes / {sum(len(s.get("assertions", [])) for s in data["scenes"])} assertions')
