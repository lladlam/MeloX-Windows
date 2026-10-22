#!/usr/bin/env python3
"""Verify saved pointer/key replay evidence and two-page geometry without running the app."""
import json
from pathlib import Path
import sys

mode, directory = sys.argv[1:3]
assert mode in ('single', 'split', 'reduced')
output = Path(directory)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert data['animation_clock'] == 'controlled scene time'
assert all(a['passed'] for scene in data['scenes'] for a in scene.get('assertions', []))
width = data['width'] / data['density']
geometry = []
for scene in data['scenes']:
    assert (output / scene['file']).is_file()
    nodes = json.loads((output / scene['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
    parent = next((n for n in nodes if n.get('tag') == 'settings-parent-motion'), None)
    detail = next((n for n in nodes if n.get('tag') == 'page-stack-detail'), None)
    if parent is None or detail is None:
        continue
    progress = float(detail['state'].split(';')[0].split('=')[1])
    pane_width = width / 2 if mode == 'split' else width
    base = pane_width if mode == 'split' else 0
    if progress == 0:
        # boundsInWindow is clipped; the entirely offscreen entering page is empty.
        assert detail['bounds'] == [0, 0, 0, 0], (scene['file'], detail)
    else:
        assert abs(detail['bounds'][0] / data['density'] - (base + pane_width * (1 - progress))) <= 1.1, (scene['file'], detail)
    expected_right = pane_width if mode == 'split' else width * (1 - progress / 3)
    assert abs(parent['bounds'][2] / data['density'] - expected_right) <= 1.1, (scene['file'], parent, progress)
    if mode != 'split' and progress > 0:
        # The actual page edges must be complementary, independently of the
        # progress diagnostic: old right = 2W/3 + incoming left/3.
        assert abs(parent['bounds'][2] - (2 * data['width'] / 3 + detail['bounds'][0] / 3)) <= 1.1
    geometry.append({'file': scene['file'], 'progress': progress, 'parent': parent['bounds'], 'detail': detail['bounds']})
assert geometry
if mode == 'single':
    early = next(g for g in geometry if '-early-close-frame-0.' in g['file'])
    assert 0 < early['progress'] < 1, 'An unfinished push must remain composed while reversing to closed'
    first = next(g for g in geometry if '-reverse-frame-0.' in g['file'])
    assert 0 < first['progress'] < 1, 'Reopen must reverse the existing in-flight spring'
    assert len({round(g['progress'], 3) for g in geometry if 0 < g['progress'] < 1}) >= 4
if mode == 'reduced':
    assert all(g['progress'] in (0, 1) for g in geometry)
(output / 'geometry-independent.json').write_text(json.dumps({'status': 'PASSED', 'mode': mode, 'geometry': geometry}, indent=2) + '\n', encoding='utf-8')
print(f'PASS page motion {mode}: {len(data["scenes"])} scenes / {len(geometry)} geometry frames')
