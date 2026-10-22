#!/usr/bin/env python3
"""Check settled production lyric row geometry, separately from Android pixel parity."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('capture', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
capture = json.loads((args.capture / 'capture.json').read_text(encoding='utf-8'))
scenes = []
for scene in capture['scenes']:
    if '-frame-' in scene['file'] or scene['page'] != 'Lyrics' or scene['is_playing']:
        continue
    path = args.capture / Path(scene['file']).with_suffix('.semantics.json')
    nodes = json.loads(path.read_text(encoding='utf-8'))
    panel = next((n for n in nodes if n.get('tag') == 'player-lyrics'), None)
    if not panel:
        continue
    left, top, right, bottom = panel['bounds']
    rows = [n for n in nodes if (n.get('tag') or '').startswith('lyric-line-') and n['bounds'][3] > top and n['bounds'][1] < bottom]
    rows.sort(key=lambda n: n['bounds'][1])
    assert len(rows) >= 2, f'Insufficient rendered lyric rows: {scene["file"]}'
    gaps = [after['bounds'][1] - before['bounds'][3] for before, after in zip(rows, rows[1:])]
    assert all(gap >= -1 for gap in gaps), f'Overlapping settled lyric rows at {scene["file"]}: {gaps}'
    assert all(n['bounds'][2] >= n['bounds'][0] and n['bounds'][3] > n['bounds'][1] for n in rows)
    scenes.append(dict(file=scene['file'], position_ms=scene['position_ms'], rows=[dict(tag=n['tag'], bounds=n['bounds']) for n in rows], minimum_row_gap_px=min(gaps), semantics_sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
assert scenes, 'No paused production lyric scene to inspect'
result = dict(status='PASSED', check='settled production lyric rows do not overlap', viewport=[capture['width'], capture['height']], scenes=scenes, android_pixel_acceptance='BLOCKED', user_scrambled_lyrics_report='UNRESOLVED_WITHOUT_USER_REFERENCE')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n')
print('PASS: paused production lyric row geometry; Android pixel acceptance BLOCKED')
