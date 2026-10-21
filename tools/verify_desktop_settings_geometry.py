#!/usr/bin/env python3
"""Measure captured settings bounds against pinned DesktopSettingsView's frame/footer."""
import argparse
import json
from pathlib import Path
import re
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('reference', type=Path)
parser.add_argument('capture', type=Path)
args = parser.parse_args()
source = subprocess.check_output(['git', '-C', str(args.reference), 'show',
    'a41ea0e1e1fa69fb543d827b853fe3aff5420951:MeloXDesktop/Features/Settings/DesktopSettingsView.swift'], text=True)
assert '.frame(width: 650' in source
assert re.search(r'\.frame\(height:\s*58\)', source)
capture = json.loads((args.capture / 'capture.json').read_text())
checked = []
for scene in capture['scenes']:
    path = args.capture / (Path(scene['file']).stem + '.semantics.json')
    nodes = json.loads(path.read_text())
    panel = next((n for n in nodes if n.get('tag') == 'desktop-settings-panel'), None)
    footer = next((n for n in nodes if n.get('tag') == 'desktop-settings-footer'), None)
    if panel is None or footer is None:
        continue
    density = scene.get('density', capture.get('density', 1))
    p, f = panel['bounds'], footer['bounds']
    width, height = (p[2] - p[0]) / density, (f[3] - f[1]) / density
    assert abs(width - 650) <= 1, (scene['file'], 'panel width', width)
    assert abs(height - 58) <= 1, (scene['file'], 'footer height', height)
    checked.append(scene['file'])
assert checked, 'No measured settings scenes'
(args.capture / 'settings-geometry.json').write_text(json.dumps({'status': 'PASSED', 'width': 650, 'footer_height': 58, 'scenes': checked}, indent=2) + '\n')
print('PASS', len(checked), 'settings frames: 650pt panel, 58pt footer')
