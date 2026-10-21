#!/usr/bin/env python3
"""Independently verify collection captures, inputs, PNGs and source geometry."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--page', choices=['album', 'playlist'], required=True)
parser.add_argument('--reference', type=Path)
args = parser.parse_args()
directory = args.directory
data = json.loads((directory / 'capture.json').read_text(encoding='utf-8'))
assert data['renderer'] == 'production ImageComposeScene'
steps = (directory / 'steps.txt').read_text(encoding='utf-8').splitlines()
assert data['steps'] == steps and len(data['scenes']) == len(steps) + 1
assert all('text@' not in step for step in steps), 'Text edits must use actual keyboard input'
assert any('tap@desktop-collection-back' in step for step in steps)
assert any('tap@desktop-collection-actions' in step for step in steps)
assertions = [a for s in data['scenes'] for a in s.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
scale = data['density']
assert scale > 0
geometries, images = [], []
measured_rows = 0
search_empty = search_restored = False
for scene in data['scenes']:
    image = directory / scene['file']
    raw = image.read_bytes()
    assert raw[:8] == b'\x89PNG\r\n\x1a\n'
    assert struct.unpack('>II', raw[16:24]) == (data['width'], data['height'])
    images.append(dict(file=scene['file'], sha256=hashlib.sha256(raw).hexdigest()))
    nodes = json.loads(image.with_suffix('.semantics.json').read_text(encoding='utf-8'))
    rows = [n for n in nodes if (n.get('tag') or '').startswith('desktop-collection-track:')]
    if '-type-search.' in scene['file']:
        assert not rows, 'Actual typed filter did not remove nonmatching rows'
        search_empty = True
    if '-close-search.' in scene['file']:
        assert rows, 'Closing the search must clear the real filter'
        search_restored = True
    artwork = next((n for n in nodes if n.get('tag') == 'desktop-collection-artwork'), None)
    if not artwork:
        continue
    x0, y0, x1, y1 = artwork['bounds']
    # Expanded descriptions can push bottom-aligned artwork out of the viewport;
    # never measure clipped bounds as full layout.
    if (y1 - y0) / scale < 277:
        continue
    assert abs((x1 - x0) / scale - 278) <= 1 and abs((y1 - y0) / scale - 278) <= 1
    root = next(n for n in nodes if n.get('tag') == f'desktop-{args.page}-detail')
    sidebar = next(n for n in nodes if n.get('tag') == 'desktop-sidebar')
    assert root['bounds'][0] >= sidebar['bounds'][2], 'Collection must stay beside the sidebar'
    assert abs((x0 - root['bounds'][0]) / scale - 42) <= 1
    details = next((n for n in nodes if n.get('tag') == 'desktop-collection-details'), None)
    assert details is not None
    assert abs((details['bounds'][0] - x1) / scale - 34) <= 1
    assert (details['bounds'][2] - details['bounds'][0]) / scale <= 621
    full_rows = [n for n in rows if abs((n['bounds'][3] - n['bounds'][1]) / scale - 46) <= 1]
    measured_rows += len(full_rows)
    for node in rows:
        assert (node['bounds'][3] - node['bounds'][1]) / scale <= 47, (scene['file'], node)
    geometries.append(dict(scene=scene['file'], artwork=artwork['bounds'], details=details['bounds'] if details else None,
                           rows=[n['bounds'] for n in full_rows]))
assert geometries and measured_rows
if args.page == 'playlist':
    assert search_empty and search_restored
log = (directory / 'gradle.log').read_text(encoding='utf-8', errors='replace')
assert 'Exception in thread' not in log, 'A background exception invalidates the replay'
reference = None
if args.reference:
    raw = args.reference.read_bytes()
    assert args.page == 'playlist' and hashlib.sha256(raw).hexdigest() == 'aac558cd59d005aeb24dbf1b2805183f2f9ab15f42d816685adb9137c123301f'
    assert struct.unpack('>II', raw[16:24]) == (data['width'], data['height']) == (1960, 1356)
    bounds = geometries[0]['artwork']
    reference = dict(sha256=hashlib.sha256(raw).hexdigest(), artwork=[498, 172, 1054, 728],
                     actual_artwork=bounds, delta_px=[a - b for a, b in zip(bounds, [498, 172, 1054, 728])],
                     scope='Original artwork landmark only; different public playlist, typography and native toolbar remain outside full-image acceptance')
report = dict(status='PASSED', page=args.page, scenes=len(data['scenes']), assertions=len(assertions), home=data['home'], reference=reference,
              source_commit='a41ea0e1e1fa69fb543d827b853fe3aff5420951', geometries=geometries, images=images,
              capture_sha256=hashlib.sha256((directory / 'capture.json').read_bytes()).hexdigest(),
              scope='Live production implementation/input/source metrics; same-content native macOS pixels pending')
(directory / 'source-geometry.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS {args.page}: {report["scenes"]} scenes / {report["assertions"]} assertions; actual input and source geometry')
