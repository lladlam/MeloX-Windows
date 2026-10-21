#!/usr/bin/env python3
"""Measure production account sheets without borrowing the uploaded user's identity."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--reference', type=Path)
args = parser.parse_args()
root = args.directory
data = json.loads((root / 'capture.json').read_text(encoding='utf-8'))
assert data['renderer'] == 'production ImageComposeScene'
assert all(a['passed'] for s in data['scenes'] for a in s['assertions'])
density = data['density']
measurements = []
for scene in data['scenes']:
    path = root / scene['file']
    nodes = {n['tag']: n for n in json.loads(path.with_suffix('.semantics.json').read_text(encoding='utf-8')) if n.get('tag')}
    panel = nodes.get('desktop-account-sheet')
    if panel is None:
        continue
    bounds = panel['bounds']
    width = min(500, data['width'] / density - 32)
    height = min(600, data['height'] / density - 32)
    assert abs((bounds[2] - bounds[0]) / density - width) < 1
    assert abs((bounds[3] - bounds[1]) / density - height) < 1
    done = nodes['desktop-account-done']['bounds']
    assert abs((done[2] - done[0]) / density - 80) < 1
    assert abs((done[3] - done[1]) / density - 30) < 1
    assert abs((bounds[2] - done[2]) / density - 16) < 1
    assert abs((bounds[3] - done[3]) / density - 16) < 1
    assert 'desktop-account-signed-out' in nodes and 'desktop-account-login' in nodes
    assert 'desktop-account-logout' not in nodes, 'Never impersonate the uploaded account'
    image = Image.open(path).convert('RGB')
    background = image.getpixel((round((bounds[0] + bounds[2]) / 2), round(bounds[1] + 12 * density)))
    assert background in [(49, 46, 46), (248, 247, 247)], background
    measurements.append(dict(file=scene['file'], sheet=bounds, done=done, surface_rgb=background))
assert len(measurements) >= 3
reference = None
if args.reference:
    raw = args.reference.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == 'e553ec08f86422bed8cdbb7c8febee1ad185ee9fc13737734b4807d9672df6bf'
    original = Image.open(args.reference).convert('RGB')
    assert original.size == (2188, 1356)
    reference = dict(sha256=hashlib.sha256(raw).hexdigest(), dimensions=list(original.size),
                     surface_rgb=original.getpixel((1100, 900)), group_rgb=original.getpixel((1100, 700)),
                     account_state='Original signed-in private identity; replay uses genuine signed-out state')
report = dict(status='PASSED', scope='Source sheet/button geometry and actual rendered surfaces; not signed-in/full-image pixel acceptance',
              measurements=measurements, reference=reference)
(root / 'account-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS account: {len(measurements)} sheets; source sizing, rendered themes and real signed-out state')
