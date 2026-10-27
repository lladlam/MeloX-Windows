#!/usr/bin/env python3
"""Capture real dual-PCM playback and the live two-artwork background in either layout."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

from PIL import Image
import numpy as np

layout, home_arg, output_arg = sys.argv[1:4]
assert layout in ('mobile', 'desktop')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 'player_background_mode=FlowingLight\nreduce_motion=true\ntransition_ui_enabled=false\n'
                 f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
fixtures = home / 'generated'
subprocess.run([sys.executable, str(repo / 'tools/generate_pcm_fixtures.py'), str(fixtures)], check=True)
steps = [
    ('consent', 'tap@remote-reject', 'cloud-enabled:false'),
    ('playing', 'wait@200', 'playing:true,tag:mini-player-expand'),
    ('open', 'tap@mini-player-expand', 'tag:now-playing'),
    ('before', 'wait@700', 'title:generated-1000hz,mix-active:false,no-playback-error:true'),
    ('mix-start', 'wait@0', 'mix-active:true'),
    ('mixed', 'wait@800', 'mix-active:true,no-playback-error:true'),
    ('handoff', 'wait@0', 'title:generated-4000hz,mix-active:false'),
    ('after', 'wait@800', 'title:generated-4000hz,mix-active:false,playing:true,no-playback-error:true'),
    ('pause', 'tap@' + ('mini-player-play-pause' if layout == 'desktop' else 'player-play-pause'), 'playing:false'),
]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark',
        '--width', '1100' if layout == 'desktop' else '430', '--height', '850', '--output', str(output),
        '--startup-wait-ms', '1200', '--wait-ms', '180', '--assert-timeout-ms', '25000',
        '--local-wavs', ','.join(str(fixtures / f'generated-{hz}hz.wav') for hz in (1000, 4000)),
        '--capture-pcm', '--automix', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
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
subprocess.run([sys.executable, str(repo / 'tools/verify_captured_automix.py'), str(output),
                '--output', str(output / 'pcm-verification.json')], check=True)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)

# Reduced motion freezes the mesh, so pixels in the unobstructed upper-left
# margin can independently compare the outgoing, combined, and incoming fields.
def pixels(name):
    files = list(output.glob(f'*-{name}.png'))
    assert len(files) == 1, files
    return np.asarray(Image.open(files[0]).convert('RGB'), dtype=np.float32)

before, mixed, after = [pixels(name) for name in ('before', 'mixed', 'after')]
height, width = before.shape[:2]
box = (slice(int(height * .09), int(height * .16)), slice(int(width * .02), int(width * .08)))
a, b, c = before[box], mixed[box], after[box]
distance = lambda x, y: float(np.mean(np.abs(x - y)))
span = distance(a, c)
outgoing_to_mix = distance(a, b)
mix_to_incoming = distance(b, c)
midpoint_error = distance(b, (a + c) / 2)
result = {'layout': layout, 'status': 'PASSED', 'scenes': len(capture['scenes']), 'assertions': len(assertions),
          'background_box_fraction': [0.02, 0.09, 0.08, 0.16], 'outgoing_incoming_difference': span,
          'outgoing_mix_difference': outgoing_to_mix, 'mix_incoming_difference': mix_to_incoming,
          'mixed_midpoint_error': midpoint_error, 'pcm_check': 'PASSED',
          'physical_audible_acceptance': 'BLOCKED'}
assert span > 5 and outgoing_to_mix > 2 and mix_to_incoming > 2, result
assert midpoint_error < min(outgoing_to_mix, mix_to_incoming), result
(output / 'background-verification.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result, indent=2))
