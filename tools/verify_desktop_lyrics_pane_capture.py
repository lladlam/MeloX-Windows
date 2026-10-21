#!/usr/bin/env python3
"""Verify the uploaded lyrics-panel viewport and real lyric/queue transitions."""
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
assert (data['width'], data['height'], data['density']) == (2414, 1356, 2)
assert len(data['scenes']) == len(data['steps']) + 1
assert all(a['passed'] for s in data['scenes'] for a in s['assertions'])
assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
assert sum(p['written_frames'] for p in data['pcm_outputs']) > 44100
panels = []
for scene in data['scenes']:
    path = root / scene['file']
    nodes = {n['tag']: n for n in json.loads(path.with_suffix('.semantics.json').read_text(encoding='utf-8')) if n.get('tag')}
    image = Image.open(path)
    assert image.size == (2414, 1356)
    for kind, width in [('Lyrics', 720), ('Queue', 516)]:
        panel = nodes.get('desktop-inspector:' + kind)
        if panel is None:
            continue
        actual = panel['bounds']
        expected = [2414 - width, 0, 2414, 1356]
        assert all(abs(a - b) <= 1 for a, b in zip(actual, expected)), (scene['file'], actual, expected)
        assert 'desktop-home' in nodes, 'Inspector must preserve the page behind the glass'
        if kind == 'Lyrics':
            assert 'player-lyrics' in nodes and any(t.startswith('lyric-line-') for t in nodes)
        panels.append(dict(file=scene['file'], kind=kind, bounds=actual,
                           surface_sample_rgb=image.convert('RGB').getpixel((2390, 100))))
assert any(p['kind'] == 'Queue' for p in panels)
assert sum(p['kind'] == 'Lyrics' for p in panels) >= 3
reference = None
if args.reference:
    raw = args.reference.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == 'e6c559e285ec4137d0c713c412607a579bc025d4449773fee93aaf3865779f74'
    assert Image.open(args.reference).size == (2414, 1356)
    reference = dict(sha256=hashlib.sha256(raw).hexdigest(), original_panel_bounds=[1694, 0, 2414, 1356],
                     boundary_uncertainty_px=1, content='Private lyrics/content are not reused in public evidence')
report = dict(status='PASSED', scope='Original viewport/boundary, real timed lyrics seek, PCM and inspector transitions; not full-image pixel parity',
              theme=data['theme'], panels=panels, reference=reference)
(root / 'lyrics-pane-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS lyrics pane: {len(panels)} panel frames; uploaded boundary, production lyrics, seek and queue transitions')
