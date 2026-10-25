#!/usr/bin/env python3
"""Real pointer/wheel replay of source-native libraries in both production layouts.

Local scans 55 generated WAV files, pages beyond 50, creates a persistent playlist
and opens its real detail. Spotify/YouTube use a fresh signed-out home: this checks
collection destinations and account navigation, not authenticated service data.
"""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import wave

source, layout, home, output = sys.argv[1:5]
assert source in ('Local', 'Spotify', 'YouTubeMusic', 'QQMusic', 'Kugou', 'Bilibili', 'Jellyfin', 'Qishui')
assert layout in ('desktop', 'mobile')
home, output = Path(home).resolve(), Path(output).resolve()
repo = Path(__file__).resolve().parents[1]
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
desktop = layout == 'desktop'
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 f'general_desktop_layout={str(desktop).lower()}\n', encoding='utf-8')
steps, expectations = [], []


def add(name, action, *checks):
    steps.append(f'{name}:{action}')
    if checks:
        expectations.append(name + '=' + ','.join(checks))


def section(value):
    return f'tap@desktop-nav:{value}' if desktop else f'tap@library-page:{value}'


native_collections = source in ('Local', 'Spotify', 'YouTubeMusic', 'Qishui')
assert native_collections or desktop, 'Generic provider regression targets its Desktop login gate'
root = f'{layout}-provider-library:{source}' if native_collections else 'desktop-provider-library'
add('consent', 'tap@remote-reject', 'cloud-enabled:false')
root_checks = [f'tag:{root}', 'absent:library-pages' if desktop else 'tag:library-pages']
if desktop and source in ('Local', 'Spotify', 'Bilibili', 'Jellyfin'):
    root_checks += ['absent:desktop-nav:Home', 'absent:desktop-nav:Explore']
add('library', 'wait@800', *root_checks)
args = ['--locale', 'zh-CN', '--source', source, '--start-tab', 'Library', '--theme', 'Dark',
        '--width', '1280' if desktop else '427', '--height', '800' if desktop else '850',
        '--output', str(output), '--wait-ms', '400', '--startup-wait-ms', '1500']
if source == 'Local':
    audio_dir = home / 'generated-audio'
    audio_dir.mkdir()
    pcm = b''.join(struct.pack('<h', int(2000 * math.sin(i * math.tau * 220 / 16000))) for i in range(16000))
    for index in range(55):
        with wave.open(str(audio_dir / f'Generated-{index:02}.wav'), 'wb') as audio:
            audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
            audio.writeframes(pcm)
    args += ['--scan-local-dir', str(audio_dir)]
    add('songs', section('Songs'), f'tag:{root}', 'tag:provider-library-play-all')
    add('more-scroll', f'scroll@{800 if desktop else 220},400,200')
    add('more-settled', 'wait@1500', 'tag:provider-library-more')
    add('more', 'tap@provider-library-more', 'absent:provider-library-more')
    # Desktop navigation lives outside the list; phone's picker scrolls with it.
    if not desktop:
        add('picker-reveal', 'scroll@220,400,-200')
        add('picker-settled', 'wait@1500', 'tag:library-page:Playlists')
    add('playlists', section('Playlists'), 'tag:provider-library-create', 'tag:provider-library-empty')
    add('create-dialog', 'tap@provider-library-create', 'tag:provider-playlist-name')
    add('create-name', 'text@provider-playlist-name,Generated library replay')
    add('create', 'tap@provider-playlist-create-confirm')
    add('created', 'wait@600', f'tag-prefix:provider-library-card:{source}:', 'absent:provider-library-empty')
    add('detail', f'tap-first@provider-library-card:{source}:')
    add('detail-loaded', 'wait@800', 'tag:desktop-playlist-detail' if desktop else 'tag:playlist-tracks', 'text:Generated library replay')
    add('back', 'escape', f'tag:{root}', f'tag-prefix:provider-library-card:{source}:')
else:
    add('signed-out', 'wait@1200', 'tag:provider-library-login', 'absent:provider-library-empty')
    if native_collections and source != 'Qishui':
        add('albums', section('Albums'), f'tag:{root}', 'tag:provider-library-login')
        add('artists', section('Artists'), f'tag:{root}', 'tag:provider-library-login')
    provider_id = {'Spotify': 'spotify', 'YouTubeMusic': 'youtubemusic', 'QQMusic': 'qq',
                    'Kugou': 'kugou', 'Bilibili': 'bilibili', 'Jellyfin': 'jellyfin', 'Qishui': 'qishui'}[source]
    add('login', 'tap@provider-library-login', 'tag:account-screen', f'tag:account-form-{provider_id}')
    if source == 'Qishui':
        add('qr-loaded', 'wait@25000', 'tag:qishui-login-qr', 'tag:qishui-login-refresh')
        add('qr-refresh', 'tap@qishui-login-refresh', 'tag:qishui-login')
        add('qr-reloaded', 'wait@25000', 'tag:qishui-login-qr')
        add('close-login', 'tap@account-dialog-close', 'absent:account-form-qishui')

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(expectations) + '\n', encoding='utf-8')
args += ['--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
                             '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
                             '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                            cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-14000:])
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
if source == 'Local':
    for scene_name, count in (('songs', 50), ('more', 55)):
        scene = next(s for s in data['scenes'] if s['file'].endswith('-' + scene_name + '.png'))
        nodes = json.loads((output / scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
        node = next(n for n in nodes if n.get('tag') == root)
        assert f'count={count};' in node['state'], node
print(f'{source}/{layout}: {len(data["scenes"])} scenes passed')
