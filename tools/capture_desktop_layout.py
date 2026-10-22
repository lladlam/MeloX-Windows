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
assert mode in ('controls', 'cold-controls', 'reference-player', 'reference-player-reload', 'reference-lyrics', 'reference-lyrics-light', 'reload', 'restore', 'reload-current', 'home-dark', 'home-light', 'lyrics-light')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
home.mkdir(parents=True, exist_ok=True)
if mode in ('reference-player', 'reference-lyrics', 'reference-lyrics-light'):
    settings = home / '.melox/prefs_melox_app_settings.properties'
    assert not settings.exists(), 'Reference player replay requires a fresh home'
    settings.parent.mkdir(parents=True, exist_ok=True)
    settings.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
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

if mode.startswith('reference-lyrics'):
    add('consent', 'tap@remote-reject', ['cloud-enabled:false', 'absent:remote-reject'])
    add('ready', 'wait@1500', ['playing:true', 'tag:desktop-home', 'tag:desktop-bottom-player'])
    add('pause-for-lyric-input', 'tap@mini-player-play-pause', ['playing:false'])
    add('lyrics-pane', 'tap@desktop-show-lyrics', ['tag:desktop-inspector:Lyrics', 'tag:player-lyrics', 'tag-prefix:lyric-line-'])
    add('reveal-lyric', 'reveal@lyric-line-1')
    add('seek-lyrics', 'tap@lyric-line-1', ['position-min:4000', 'no-playback-error:true'])
    add('resume-lyrics', 'tap@mini-player-play-pause', ['playing:true'])
    add('queue-inspector', 'tap@desktop-show-queue', ['tag:desktop-inspector:Queue', 'absent:desktop-inspector:Lyrics'])
    add('lyrics-again', 'tap@desktop-show-lyrics', ['tag:desktop-inspector:Lyrics', 'absent:desktop-inspector:Queue'])
    add('close-pane', 'tap@desktop-show-lyrics', ['absent:desktop-inspector:Lyrics', 'tag:desktop-home'])
elif mode.startswith('reference-player'):
    if mode == 'reference-player':
        add('consent', 'tap@remote-reject', ['cloud-enabled:false', 'absent:remote-reject'])
    add('ready', 'wait@1500', ['playing:true', 'runtime:desktopLayoutEnabled:true'])
    add('expanded', 'tap@mini-player-expand', ['tag:desktop-player-column', 'tag:desktop-player-favorite', 'selected:desktop-full-lyrics'])
    if mode == 'reference-player':
        add('favorite-ready', 'wait@500', ['state:desktop-player-favorite:unliked', 'local-favorite:false'])
        add('favorite', 'tap@desktop-player-favorite', ['state:desktop-player-favorite:liked', 'local-favorite:true'])
        add('paused', 'tap@mini-player-play-pause', ['playing:false'])
        add('resumed', 'tap@mini-player-play-pause', ['playing:true'])
        add('seek', 'slider@desktop-seek,0.25', ['position-min:100000', 'no-playback-error:true'])
        add('drag-seek', 'hold-drag@desktop-seek,200,0', ['position-min:300000', 'no-playback-error:true'])
        add('mute', 'tap@desktop-full-mute', ['volume:0'])
        add('unmute', 'tap@desktop-full-mute', ['volume:1'])
        add('volume', 'slider@desktop-full-volume,0.4', ['volume:0.4'])
        add('queue', 'tap@desktop-full-queue', ['selected:desktop-full-queue', 'tag:desktop-queue-track:1', 'absent:player-lyrics'])
        add('autoplay', 'tap@desktop-queue-autoplay', ['selected:desktop-queue-autoplay'])
        add('autoplay-off', 'tap@desktop-queue-autoplay', ['unselected:desktop-queue-autoplay'])
        add('automix', 'tap@desktop-queue-automix', ['selected:desktop-queue-automix'])
        add('automix-off', 'tap@desktop-queue-automix', ['unselected:desktop-queue-automix'])
        add('artwork-only', 'tap@desktop-full-queue', ['unselected:desktop-full-queue', 'unselected:desktop-full-lyrics', 'absent:desktop-queue', 'absent:player-lyrics'])
        add('lyrics', 'tap@desktop-full-lyrics', ['selected:desktop-full-lyrics', 'tag:player-lyrics'])
    else:
        add('persisted-favorite', 'wait@500', ['state:desktop-player-favorite:liked', 'local-favorite:true'])
        add('restore-favorite', 'tap@desktop-player-favorite', ['state:desktop-player-favorite:unliked', 'local-favorite:false'])
    add('collapsed', 'tap@player-collapse', ['tag:desktop-bottom-player', 'absent:desktop-player-column'])
elif mode == 'lyrics-light':
    add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
    # Pause at 0:00 before layout switching: the lyric line anchors scroll with
    # playback, so a slow desktop recomposition must not move line 1 off-screen.
    add('ready', 'wait@1500', ['playing:true', 'tag:mini-player-play-pause'])
    add('pause-before-layout', 'tap@mini-player-play-pause', ['playing:false'])
    add('reveal-general', 'reveal@settings-open-general')
    add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
    add('enable-desktop', 'tap@setting-general_desktop_layout', ['tag:desktop-sidebar', 'runtime:desktopLayoutEnabled:true'])
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
    add('reveal-general', 'reveal@settings-open-general')
    add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
    add('playback-before-switch', 'wait@100', ['playing:true', 'playback-checkpoint:switch'])
    for index in range(3):
        add('desktop-' + str(index), 'tap@setting-general_desktop_layout', ['tag:desktop-sidebar', 'tag:desktop-bottom-player',
            'tag:settings-general', 'runtime:desktopLayoutEnabled:true', 'absent:bottom-root-tabs', 'pref-bool:general_desktop_layout:true', 'playback-continuity:switch'])
        if index < 2:
            add('current-' + str(index), 'tap@setting-general_desktop_layout', ['tag:settings-general', 'tag:bottom-root-tabs',
                'tag:layout-list-detail', 'runtime:desktopLayoutEnabled:false', 'pref-bool:general_desktop_layout:false', 'playback-continuity:switch'])
elif mode == 'reload':
    add('persisted-mode', 'wait@100', ['runtime:desktopLayoutEnabled:true', 'tag:desktop-sidebar'])
    # A fresh desktop composition needs one ordinary page navigation before
    # its retained page accepts pointer input in ImageComposeScene.
    add('warm-navigation', 'tap@desktop-nav:Search', ['tag:desktop-sidebar'])
    add('return-settings', 'tap@desktop-settings', ['tag:settings-root'])
    # Start directly in the desktop settings window; General is a fixed tab.
    add('general', 'wait@100', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
    # A separate JVM restores the real queue and resumes PCM after startup.
    add('playback-ready', 'wait@1500', ['playing:true'])
elif mode == 'cold-controls':
    add('consent', 'tap@remote-reject', ['cloud-enabled:false', 'absent:remote-reject'])
    add('cold-desktop-ready', 'wait@1500', ['runtime:desktopLayoutEnabled:true', 'tag:desktop-sidebar', 'tag:settings-root', 'playing:true'])
    add('cold-lyrics', 'tap@desktop-show-lyrics', ['tag:desktop-inspector:Lyrics', 'tag:player-lyrics', 'tag-prefix:lyric-line-'])
    add('cold-seek-lyrics', 'tap@lyric-line-1', ['position-min:4000', 'no-playback-error:true'])
    add('cold-close-lyrics', 'tap@desktop-show-lyrics', ['absent:desktop-inspector:Lyrics'])
elif mode == 'restore':
    # Fresh current-layout process: record consent, enable the Desktop shell,
    # then restore the current layout. Cold persisted On is covered by `reload`.
    add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
    add('current-default', 'wait@100', ['runtime:desktopLayoutEnabled:false', 'tag:interface-current', 'tag:bottom-root-tabs'])
    add('reveal-general', 'reveal@settings-open-general')
    add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
    add('enable-desktop', 'tap@setting-general_desktop_layout', ['tag:desktop-sidebar', 'tag:settings-general',
        'runtime:desktopLayoutEnabled:true', 'pref-bool:general_desktop_layout:true'])
    add('restore-current', 'tap@setting-general_desktop_layout', ['tag:interface-current', 'tag:settings-general',
        'tag:bottom-root-tabs', 'runtime:desktopLayoutEnabled:false', 'pref-bool:general_desktop_layout:false'])
elif mode == 'reload-current':
    add('current-persisted', 'wait@100', ['runtime:desktopLayoutEnabled:false', 'tag:interface-current',
        'tag:bottom-root-tabs', 'pref-bool:general_desktop_layout:false'])
    add('reveal-general', 'reveal@settings-open-general')
    add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('check-persisted-switch', 'wait@100', ['unchecked:setting-general_desktop_layout'])
if mode in ('controls', 'cold-controls'):
    add('pause', 'tap@mini-player-play-pause', ['playing:false', 'no-playback-error:true'] if mode == 'cold-controls' else ['no-playback-error:true'])
    add('resume', 'tap@mini-player-play-pause', ['playing:true', 'no-playback-error:true'])
    add('queue', 'tap@desktop-show-queue', ['tag:desktop-inspector:Queue', 'tag:desktop-queue-track:1'])
    add('select-real-track', 'tap@desktop-queue-track:1', ['no-playback-error:true'])
    add('close-queue', 'tap@desktop-show-queue', ['absent:desktop-inspector:Queue'])
    add('volume', 'tap@desktop-volume', ['tag:desktop-volume-slider'])
    add('set-volume', 'slider@desktop-volume-slider,0.4', ['volume:0.4'])
    add('collapse-volume', 'tap@desktop-volume', ['absent:desktop-volume-slider'])
    add('expand-player', 'tap@mini-player-expand', ['tag:desktop-player-column', 'tag:desktop-player-panel', 'absent:desktop-bottom-player'])
    add('full-queue', 'tap@desktop-full-queue', ['tag:desktop-queue-autoplay', 'tag:desktop-queue-automix'])
    add('full-pause', 'tap@mini-player-play-pause', ['playing:false', 'no-playback-error:true'] if mode == 'cold-controls' else ['no-playback-error:true'])
    add('full-resume', 'tap@mini-player-play-pause', ['playing:true', 'no-playback-error:true'])
    add('collapse-player', 'tap@player-collapse', ['tag:desktop-bottom-player', 'tag:settings-general'])
    add('search', 'tap@desktop-nav:Search', ['tag:desktop-sidebar', 'absent:settings-general'])
    add('songs', 'tap@desktop-nav:Songs', ['tag:desktop-provider-library:Local', 'absent:library-pages'])
    add('settings', 'tap@desktop-settings', ['tag:settings-root'])
    add('reopen-general', 'tap@settings-open-general', ['tag:settings-general', 'checked:setting-general_desktop_layout'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items()) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Netease' if mode.startswith(('home-', 'reference-lyrics')) else 'Local',
    '--start-tab', 'Home' if mode.startswith(('home-', 'reference-lyrics')) else 'Settings', '--theme', 'Light' if mode in ('home-light', 'lyrics-light', 'reference-lyrics-light') else 'Dark',
    '--width', '2414' if mode.startswith('reference-lyrics') else '1960' if mode.startswith('reference-player') else '1280',
    '--height', '1356' if mode.startswith('reference-lyrics') else '1184' if mode.startswith('reference-player') else '800',
    '--density', '2' if mode.startswith('reference-') else '1', '--capture-pcm',
    '--output', str(output), '--wait-ms', '1200', '--startup-wait-ms', '1200',
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
assert 'Exception in thread' not in (output / 'gradle.log').read_text(encoding='utf-8', errors='replace'), 'Background UI exception invalidates replay'
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert len(data['scenes']) == len(data['steps']) + 1
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
if not mode.startswith('home-'):
    assert sum(x['written_frames'] for x in data['pcm_outputs']) > 44100
geometry = []
for scene in data['scenes']:
    nodes = json.loads((output / scene['file'].replace('.png', '.semantics.json')).read_text(encoding='utf-8'))
    # The reference side panel overlays the page; it does not reserve width.
    player_width = min(700, data['width'] / data['density'] - 207 - 48)
    for tag, expected_width, expected_height in [('desktop-sidebar', 199, None), ('desktop-bottom-player', player_width, 54),
        ('desktop-inspector:Queue', 258, None), ('desktop-inspector:Lyrics', 360, None)]:
        node = next((n for n in nodes if n.get('tag') == tag), None)
        if node:
            left, top, right, bottom = node['bounds']
            assert abs((right-left) / data['density'] - expected_width) <= 1, (scene['file'], tag, node['bounds'])
            if expected_height:
                assert abs((bottom-top) / data['density'] - expected_height) <= 1
            geometry.append({'scene': scene['file'], 'tag': tag, 'bounds': node['bounds'], 'source_metrics_match': True})
(output / 'source-geometry.json').write_text(json.dumps(geometry, indent=2) + '\n', encoding='utf-8')
print(f'PASS Desktop {mode}: {len(data["scenes"])} scenes / {sum(len(s.get("assertions", [])) for s in data["scenes"])} assertions')
