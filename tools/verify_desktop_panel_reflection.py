#!/usr/bin/env python3
"""Verify page-scroll reflection pixels while the readable Dock body stays unchanged."""
import json
from pathlib import Path
import sys
from PIL import Image

directory = Path(sys.argv[1])
data = json.loads((directory / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
first = next(s for s in data['scenes'] if s['file'].endswith('-before.png'))
last = next(s for s in data['scenes'] if s['file'].endswith('-after.png'))
nodes = json.loads((directory / first['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
dock = next(n['bounds'] for n in nodes if n.get('tag') == 'desktop-sidebar')
lyrics = next(n['bounds'] for n in nodes if n.get('tag') == 'desktop-inspector:Lyrics')
d = data['density']
top = max(dock[1], lyrics[1]) + 100 * d
bottom = min(dock[3], lyrics[3]) - 180 * d
regions = {
    'dock_edge': (dock[2] - 27 * d, top, dock[2] - 5 * d, bottom),
    'lyrics_edge': (lyrics[0] + 2 * d, top, lyrics[0] + 24 * d, bottom),
    'dock_body': (dock[0] + 22 * d, top, dock[2] - 37 * d, bottom),
}
report = {}
with Image.open(directory / first['file']) as before, Image.open(directory / last['file']) as after:
    for key, rectangle in regions.items():
        a = before.convert('RGB').crop(tuple(round(x) for x in rectangle))
        b = after.convert('RGB').crop(tuple(round(x) for x in rectangle))
        left, right = a.tobytes(), b.tobytes()
        changed = sum(left[i:i + 3] != right[i:i + 3] for i in range(0, len(left), 3))
        report[key] = {'changed': changed, 'pixels': a.width * a.height, 'fraction': changed / (a.width * a.height)}
assert report['dock_edge']['fraction'] > .05, report
assert report['lyrics_edge']['fraction'] > .05, report
assert report['dock_body']['fraction'] < .01, report
result = {'status': 'PASSED', 'theme': data['theme'], 'regions': report,
          'scope': 'production page wheel scroll; visual reflection, not physical hardware acceptance'}
(directory / 'reflection-proof.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(json.dumps(result))
