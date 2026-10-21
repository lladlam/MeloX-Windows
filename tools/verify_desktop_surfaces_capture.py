#!/usr/bin/env python3
"""Verify the captured Desktop surface route/layout evidence without pixel claims.

Usage: verify_desktop_surfaces_capture.py CAPTURE_DIR [OUTPUT_JSON]
The capture must come from tools/capture_desktop_surfaces.py.
"""
import json
from pathlib import Path
import sys

capture_dir = Path(sys.argv[1])
output = Path(sys.argv[2]) if len(sys.argv) > 2 else capture_dir / 'surfaces-verification.json'
capture = json.loads((capture_dir / 'capture.json').read_text(encoding='utf-8'))
scenes = capture['scenes']
assert scenes, 'capture has no scenes'

required = {
    'desktop-library:Songs', 'desktop-library:Playlists', 'desktop-library:Albums',
    'desktop-library:Artists', 'desktop-library:Podcasts', 'desktop-library:History',
    'desktop-downloads', 'desktop-cloud', 'desktop-explore', 'desktop-search',
    'desktop-settings-panel', 'desktop-settings-footer', 'desktop-about',
    'desktop-project-licenses', 'desktop-account-sheet', 'desktop-login',
    'desktop-messages-login',
}
forbidden = {'library-segmented-picker', 'search-state'}
found = set()
assertions = 0
for scene in scenes:
    semantics = json.loads((capture_dir / (Path(scene['file']).stem + '.semantics.json')).read_text(encoding='utf-8'))
    tags = {node.get('tag') for node in semantics if node.get('tag')}
    found |= tags
    for entry in scene.get('assertions', []):
        assertions += 1
        assert entry['passed'], f"{scene['file']}: {entry['expected']}"
    # Settled Desktop pages may keep transient crossfade nodes only before the
    # driver's explicit wait scenes; those scenes already assert absence.
missing = required - found
assert not missing, f'missing desktop tags: {sorted(missing)}'
assert not (forbidden & found), f'phone UI leaked into settled scenes: {sorted(forbidden & found)}'
result = {'status': 'PASSED', 'scenes': len(scenes), 'assertions': assertions,
          'required_tags': sorted(required),
          'login_methods': sorted(t for t in found if str(t).startswith('desktop-login-method:'))}
output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('PASS', len(scenes), 'scenes,', assertions, 'assertions,', len(required), 'desktop surfaces')
