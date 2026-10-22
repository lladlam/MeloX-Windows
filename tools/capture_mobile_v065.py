#!/usr/bin/env python3
"""Production mobile Home and shared player regression; only pointer/keyboard input.

home uses public NetEase charts; player uses genuine generated WAV -> PCM + timed LRC.
Intermediate frames use the explicit scene animation clock, independent of rendering cost.
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

mode, home, output = sys.argv[1:4]
assert mode in ('home', 'player')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a new isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\nplayer_transition_duration_ms=900\n', encoding='utf-8')
steps, checks = [], {}


def add(name, action, expected=()):
    steps.append(name + ':' + action)
    if expected:
        checks[name] = list(expected)


add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
args = ['--locale', 'zh-CN', '--source', 'Netease' if mode == 'home' else 'Local', '--start-tab', 'Home' if mode == 'home' else 'Settings',
        '--theme', 'Dark', '--width', '427', '--height', '850', '--output', str(output),
        '--wait-ms', '180', '--startup-wait-ms', '1200']
if mode == 'home':
    add('loaded', 'wait@7000', ['tag:home-quick-actions', 'tag:home-action-每日推荐'])
    add('public-hot', 'touch-drag@home-quick-actions,-220,0')
    add('hot', 'tap@home-action-热歌榜')
    add('hot-loaded', 'wait@8000', ['tag:home-song-list-detail', 'absent:now-playing'])
    add('share-list', 'tap@playlist-share', ['tag:playlist-share-text', 'tag:playlist-copy-share'])
    add('close-share', 'tap@playlist-actions-close', ['absent:playlist-actions-dialog'])
    add('list-more', 'tap@playlist-more', ['tag:playlist-sort:Title', 'absent:playlist-refresh'])
    add('sort-title', 'tap@playlist-sort:Title', ['state:playlist-tracks:Title', 'absent:playlist-actions-dialog'])
    add('back', 'escape', ['tag:home-quick-actions', 'absent:home-song-list-detail'])
    add('hot-again', 'tap@home-action-热歌榜')
    add('hot-loaded-again', 'wait@8000', ['tag:home-song-list-detail'])
    add('back-again', 'escape', ['tag:home-quick-actions', 'absent:home-song-list-detail'])
    add('daily-reveal', 'touch-drag@home-quick-actions,220,0')
    add('daily-needs-session', 'tap@home-action-每日推荐')
    add('daily-error', 'wait@1000', ['tag:home-error', 'absent:home-song-list-detail', 'absent:now-playing'])
else:
    wavs = []
    for index, hz in enumerate((220, 330)):
        file = home / f'mobile-generated-{index}.wav'
        with wave.open(str(file), 'wb') as audio:
            audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
            second = b''.join(struct.pack('<h', int(4000 * math.sin(i * 2 * math.pi * hz / 16000))) for i in range(16000))
            audio.writeframes(second * 90)
        wavs.append(str(file))
    lrc = home / 'mobile-generated.lrc'
    lrc.write_text('\n'.join(f'[{i // 15:02d}:{i * 4 % 60:02d}.00]Mobile timed lyric {i + 1}' for i in range(22)) + '\n')
    args += ['--scan-local-dir', str(home), '--local-wavs', ','.join(wavs), '--local-lyrics-file', str(lrc), '--capture-pcm', '--frames-ms', '0,200,500,700,820']
    add('playing', 'wait@1500', ['playing:true', 'tag:mini-player-expand'])
    add('mini-anchor', 'remember-point@mini-player-expand')
    add('open', 'tap@mini-player-expand')
    add('opened', 'wait@1300', ['tag:player-transition-shell', 'page:Artwork', 'no-playback-error:true'])
    add('pause', 'tap@player-play-pause', ['playing:false'])
    add('lyrics', 'tap@player-show-lyrics', ['page:Lyrics', 'tag:player-lyrics', 'tag-prefix:lyric-line-'])
    add('queue', 'tap@player-show-queue', ['page:Queue'])
    add('artwork', 'tap@player-show-queue', ['page:Artwork'])
    add('close-interrupt', 'tap@player-collapse')
    add('reopen-in-flight', 'tap-saved@mini-anchor')
    add('reopened', 'wait@1300', ['tag:player-transition-shell', 'page:Artwork', 'playing:false'])
    add('close', 'tap@player-collapse')
    add('closed', 'wait@1300', ['absent:now-playing', 'tag:mini-player-shell', 'playing:false'])
    add('open-again', 'tap@mini-player-expand')
    add('opened-again', 'wait@1300', ['page:Artwork'])
    add('resume', 'tap@player-play-pause', ['playing:true', 'no-playback-error:true'])
    add('lyrics-again', 'tap@player-show-lyrics', ['page:Lyrics'])
    add('close-on-lyrics', 'tap@player-collapse')
    add('lyrics-mini', 'wait@1300', ['absent:now-playing'])
    add('reopen-lyrics', 'tap@mini-player-expand')
    add('lyrics-restored', 'wait@1300', ['page:Lyrics', 'playing:true', 'tag-prefix:lyric-line-'])
    add('final-close', 'tap@player-collapse')
    add('final-closed', 'wait@1300', ['absent:now-playing', 'playing:true'])

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(name + '=' + ','.join(values) for name, values in checks.items()) + '\n', encoding='utf-8')
args += ['--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-15000:])
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for scene in data['scenes'] for a in scene.get('assertions', []))
if mode == 'player':
    assert sum(p['written_frames'] for p in data['pcm_outputs']) > 44100
    frames = [scene for scene in data['scenes'] if '-open-frame-' in scene['file']]
    geometries = []
    for scene in frames:
        nodes = json.loads((output / scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
        shell = next((n for n in nodes if n.get('tag') == 'player-transition-shell'), None)
        if shell:
            geometries.append({'file': scene['file'], 'bounds': shell['bounds'], 'state': shell.get('state')})
    assert len({tuple(g['bounds']) for g in geometries}) >= 2, 'Shared shell did not animate through distinct bounds'
    (output / 'motion-geometries.json').write_text(json.dumps(geometries, indent=2) + '\n')
    restored = next(s for s in data['scenes'] if s['file'].endswith('-lyrics-restored.png'))
    nodes = json.loads((output / restored['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
    artwork = next(n for n in nodes if n.get('tag') == 'player-persistent-artwork')
    pose = dict(part.split('=', 1) for part in artwork['state'].split(';'))
    assert float(pose['size']) == 72 and float(pose['y']) == 22, artwork
    for scene in data['scenes']:
        if '-reopen-lyrics-frame-' not in scene['file']:
            continue
        nodes = json.loads((output / scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
        art = next(n for n in nodes if n.get('tag') == 'player-persistent-artwork')
        pose = dict(part.split('=', 1) for part in art['state'].split(';'))
        assert float(pose['size']) == 72, (scene['file'], pose)
else:
    shared = next(s for s in data['scenes'] if s['file'].endswith('-share-list.png'))
    nodes = json.loads((output / shared['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
    text = next(n for n in nodes if n.get('tag') == 'playlist-share-text')['text']
    assert 'https://music.163.com/song?id=' in str(text), text
    assert 'playlist?id=-' not in str(text), 'A synthetic recommendation ID leaked into a playlist URL'
    # Compare visible numbered rows with the songs actually offered for share;
    # a changed sort-mode flag alone does not prove the list was reordered.
    titles = [entry.splitlines()[0].rsplit(' - ', 1)[0] for entry in text.split('\n\n')[1:]]
    expected = sorted(titles, key=str.lower)
    sorted_scene = next(s for s in data['scenes'] if s['file'].endswith('-sort-title.png'))
    rows = json.loads((output / sorted_scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
    verified = 0
    for index, node in enumerate(rows[:-1]):
        if str(node.get('text', '')).isdigit() and rows[index + 1].get('bounds', [0, -1])[1] == node['bounds'][1]:
            number = int(node['text'])
            if 1 <= number <= len(expected):
                assert rows[index + 1]['text'] == expected[number - 1], (number, rows[index + 1]['text'], expected[number - 1])
                verified += 1
    assert verified >= 3, 'Too few visible sorted song rows were verified'
print(f'PASS mobile {mode}: {len(data["scenes"])} scenes / ' + str(sum(len(s.get('assertions', [])) for s in data['scenes'])) + ' assertions')
