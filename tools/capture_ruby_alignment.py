#!/usr/bin/env python3
"""Actual Local scanning/playback and pointer navigation; generated TTML is explicitly a local render fixture."""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import wave

layout, home, output = sys.argv[1:4]
assert layout in ('mobile', 'desktop')
home, output = Path(home).resolve(), Path(output).resolve()
repo = Path(__file__).resolve().parents[1]
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\nlyrics_romanization=true\n'
                 'lyrics_romanization_display_mode=AllLines\nlyrics_word_by_word=true\n'
                 f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
audio_dir = home / 'generated-audio'
audio_dir.mkdir()
second = b''.join(struct.pack('<h', int(1000 * math.sin(i * math.tau * 220 / 16000))) for i in range(16000))
with wave.open(str(audio_dir / 'Japanese-Local-Rendering.wav'), 'wb') as audio:
    audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
    for _ in range(70):
        audio.writeframes(second)
text = '君のこと世界へ君のこと世界へ君のこと世界へ'
reading = 'kimi no koto sekai e kimi no koto sekai e kimi no koto sekai e'
ttml = home / 'generated-lyrics.ttml'
ttml.write_text(f'''<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
<p begin="00:00:00.000" end="00:01:05.000"><span begin="00:00:00.000" end="00:01:05.000">{text}</span><span ttm:role="x-roman">{reading}</span></p>
</div></body></tt>''', encoding='utf-8')
steps = ['consent:tap@remote-reject', 'songs:tap@' + ('desktop-nav:Songs' if layout == 'desktop' else 'library-page:Songs'),
         'play-local:tap@provider-library-play-all', 'decoded:wait@1800', 'player:tap@mini-player-expand']
checks = ['play-local=tag:mini-player-expand', 'decoded=playing:true,no-playback-error:true', 'player=tag:now-playing']
if layout == 'mobile':
    steps.append('lyrics:tap@player-show-lyrics')
steps += ['ruby:wait@1000', 'pause:tap@' + ('mini-player-play-pause' if layout == 'desktop' else 'player-play-pause')]
checks += ['ruby=tag:lyric-ruby,tag:lyric-ruby-row-1,tag:lyric-romanization-row', 'pause=playing:false,no-playback-error:true']
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(checks) + '\n', encoding='utf-8')
args = ['--locale', 'ja-JP', '--source', 'Local', '--start-tab', 'Library', '--theme', 'Dark',
        '--width', '1100' if layout == 'desktop' else '430', '--height', '850', '--output', str(output),
        '--wait-ms', '450', '--startup-wait-ms', '1200', '--scan-local-dir', str(audio_dir), '--local-lyrics-file', str(ttml),
        '--capture-pcm',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-70:]))
    sys.exit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
semantics = json.loads(next(output.glob('*-ruby.semantics.json')).read_text(encoding='utf-8'))
rows = sorted([node for node in semantics if (node.get('tag') or '').startswith('lyric-ruby-row-')], key=lambda n: n['bounds'][1])
assert len(rows) >= 2, 'The local Japanese paragraph must really wrap'
assert all(a['bounds'][3] <= b['bounds'][1] + 1 for a, b in zip(rows, rows[1:])), 'Visual rows must not overlap'
annotations = [n for n in semantics if n.get('tag') == 'lyric-romanization-row']
assert len(annotations) == len(rows), 'Every visual row needs its own reading row'
assert sum(p.get('written_frames', 0) for p in capture['pcm_outputs']) > 44100, 'Real Local decoding must produce PCM'
summary = {'layout': layout, 'scenes': len(capture['scenes']), 'assertions': len(assertions), 'visual_rows': len(rows),
           'reading_rows': len(annotations), 'fixture': 'generated real Local WAV + TTML, no online lyric claim', 'physical_audible': False}
(output / 'summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(summary, ensure_ascii=False))
