#!/usr/bin/env python3
"""Check physical player replay and source/image landmark geometry, never self-diff parity."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--reference', type=Path, help='Original uploaded immersive-player.png, without resizing')
parser.add_argument('--queue-reference', type=Path)
args = parser.parse_args()
root = args.directory
data = json.loads((root / 'capture.json').read_text(encoding='utf-8'))
assert data['renderer'] == 'production ImageComposeScene'
assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
assert data['density'] == 2 and (data['width'], data['height']) == (1960, 1184)
assert data['steps'] == (root / 'steps.txt').read_text(encoding='utf-8').splitlines()
assert len(data['scenes']) == len(data['steps']) + 1
assert 'Exception in thread' not in (root / 'gradle.log').read_text(encoding='utf-8', errors='replace')
assertions = [a for s in data['scenes'] for a in s['assertions']]
assert assertions and all(a['passed'] for a in assertions)
evidence = []
for scene in data['scenes']:
    image = root / scene['file']
    raw = image.read_bytes()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n' and struct.unpack('>II', raw[16:24]) == (1960, 1184)
    nodes = json.loads(image.with_suffix('.semantics.json').read_text(encoding='utf-8'))
    tagged = {n['tag']: n for n in nodes if n.get('tag')}
    entry = dict(file=image.name, sha256=hashlib.sha256(raw).hexdigest())
    modes = []
    for tag in ['desktop-queue-autoplay', 'desktop-queue-automix']:
        mode = tagged.get(tag)
        if mode is None:
            continue
        x0, y0, x1, y1 = mode['bounds']
        if x1 <= x0 or y1 <= y0:
            continue  # Retained AnimatedContent may expose a fully clipped outgoing node.
        assert abs(y0 - 96) <= 1 and abs(y1 - y0 - 72) <= 1
        if tag.endswith('autoplay'):
            assert abs(x0 - 1062) <= 1, (image.name, mode['bounds'])
        rgb = Image.open(image).convert('RGB').getpixel((round(x0 + 22), round((y0 + y1) / 2)))
        if mode.get('selected'):
            assert min(rgb) > 210, (image.name, tag, rgb)
        modes.append(dict(tag=tag, bounds=mode['bounds'], selected=mode.get('selected'), surface_rgb=rgb))
    if modes:
        entry['queue_modes'] = modes
    artwork = tagged.get('desktop-player-artwork')
    if artwork:
        bounds = artwork['bounds']
        playing = scene['is_playing']
        # Source viewport980x592: paused214, top46, columnX85,width320.
        # Slot centre245,201; expanded scale1/.74. The uploaded image's centre
        # is248,201: preserve/report the 6px horizontal discrepancy, not a pass.
        width = 428 / .74 if playing else 428
        expected = [490 - width / 2, 402 - width / 2, 490 + width / 2, 402 + width / 2]
        if '-artwork-only.' in image.name:
            expected = [x + (980 - 490) if i % 2 == 0 else x for i, x in enumerate(expected)]
        assert all(abs(a - b) <= 2 for a, b in zip(bounds, expected)), (image.name, bounds, expected)
        progress = tagged['desktop-player-progress']['bounds']
        seek = tagged['desktop-seek']['bounds']
        assert progress[2] - progress[0] == 640 and seek[3] - seek[1] == 36
        assert all(tag in tagged for tag in ['desktop-player-favorite', 'player-actions', 'desktop-player-page-switcher'])
        entry.update(artwork=bounds, expected_artwork=expected, progress=progress, seek=seek)
        if playing and '-artwork-only.' not in image.name:
            entry['uploaded_landmark_delta_px'] = [round(a - b, 3) for a, b in zip(bounds, [207, 113, 785, 692])]
    evidence.append(entry)
assert any('artwork' in entry for entry in evidence)
reference = None
if args.reference:
    raw = args.reference.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == '89cec608c79da182e02bb7c41ac04d131e9baeb0a3a9344257a4bcc98af6b6a2'
    assert struct.unpack('>II', raw[16:24]) == (1960, 1184)
    reference = dict(file=args.reference.name, sha256=hashlib.sha256(raw).hexdigest(),
                     playing_artwork_landmark=[207, 113, 785, 692], uncertainty_px=1,
                     source_revision='unconfirmed upload; fixed source independently a41ea0e',
                     density='2 inferred from image landmarks and fixed source, no embedded density metadata')
queue_reference = None
if args.queue_reference:
    raw = args.queue_reference.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == 'd457073b0968f91634d2fbf4613bf8cbd7c953ffedc892a4ec2408129f2821a0'
    assert struct.unpack('>II', raw[16:24]) == (1960, 1184)
    modes = next(entry['queue_modes'] for entry in evidence if entry.get('queue_modes'))
    queue_reference = dict(sha256=hashlib.sha256(raw).hexdigest(), mode_row_landmark=[1063, 96, 1832, 168],
                           actual_mode_row=[*modes[0]['bounds'][:2], *modes[-1]['bounds'][2:]],
                           uncertainty_px=1, provenance='Unknown for generated local queue; do not fabricate the uploaded source album context')
report = dict(status='PASSED', scenes=len(data['scenes']), assertions=len(assertions), reference=reference, queue_reference=queue_reference, images=evidence,
              scope='Production local-media replay, original-sized frames, independent source/cover landmarks; different music/text/background, NOT full-image pixel parity')
(root / 'player-geometry.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS player: {report["scenes"]} scenes / {report["assertions"]} assertions; source geometry at uploaded image dimensions')
