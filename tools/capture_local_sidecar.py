#!/usr/bin/env python3
"""Replay local WAV playback and same-name Lyricify lyrics in both layouts."""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import wave

layout, home_arg, output_arg = sys.argv[1:4]
mode = sys.argv[4] if len(sys.argv) > 4 else 'timed'
assert layout in ('mobile', 'desktop')
assert mode in ('timed', 'plain')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
wav = home / 'sidecar-song.wav'
with wave.open(str(wav), 'wb') as audio:
    audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
    second = b''.join(struct.pack('<h', int(4000 * math.sin(i * 2 * math.pi * 220 / 16000))) for i in range(16000))
    audio.writeframes(second * 45)
if mode == 'timed':
    (home / 'sidecar-song.lys').write_text('[0]Sidecar(1000,700) lyrics(1700,700)\n[0]Second(5000,800) line(5800,800)\n', encoding='utf-8')
else:
    (home / 'sidecar-song.txt').write_text(''.join(f'Sidecar plain line {i}\n' for i in range(30)), encoding='utf-8')
steps = [('consent', 'tap@remote-reject', 'cloud-enabled:false'),
         ('playing', 'wait@1500', 'playing:true,no-playback-error:true')]
lyric_checks = 'tag:player-lyrics,word-lines-min:1' if mode == 'timed' else 'tag:player-lyrics,tag:lyrics-unsynchronized'
if layout == 'mobile':
    steps += [('open', 'tap@mini-player-expand', 'tag:now-playing'),
              ('lyrics', 'tap@player-show-lyrics', lyric_checks)]
else:
    steps += [('lyrics', 'tap@desktop-show-lyrics', lyric_checks)]
if mode == 'plain':
    steps += [('scroll-plain', 'reveal@lyric-line-12,player-lyrics', 'tag:lyric-line-12'),
              ('stay-plain', 'wait@1500', 'tag:lyric-line-12,playing:true')]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '430' if layout == 'mobile' else '1280',
        '--height', '850', '--output', str(output), '--wait-ms', '300', '--startup-wait-ms', '1200',
        '--scan-local-dir', str(home), '--local-wavs', str(wav), '--capture-pcm',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(encoding='utf-8', errors='replace')[-15000:])
    raise SystemExit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for scene in capture['scenes'] for a in scene.get('assertions', []))
assert sum(p['written_frames'] for p in capture['pcm_outputs']) > 0
scene = next(scene for scene in capture['scenes'] if scene['file'].endswith('-lyrics.png'))
nodes = json.loads((output / scene['file'].replace('.png', '.semantics.json')).read_text(encoding='utf-8'))
assert any('Sidecar' in str(node.get('text') or '') for node in nodes), 'Sidecar lyric text not displayed'
if mode == 'plain':
    scrolled = next(scene for scene in capture['scenes'] if scene['file'].endswith('-stay-plain.png'))
    scrolled_nodes = json.loads((output / scrolled['file'].replace('.png', '.semantics.json')).read_text(encoding='utf-8'))
    row = next(node for node in scrolled_nodes if node.get('tag') == 'lyric-line-12')
    assert not row['clickable'], 'Untimed lyrics must not expose seek actions'
print(f'PASS local sidecar {layout}/{mode}: {len(capture["scenes"])} scenes, software PCM and lyric text')
