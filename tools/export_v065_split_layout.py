#!/usr/bin/env python3
"""Export and verify official v0.6.5 split-layout strings/icon from the immutable Git tree."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

PIN = '53244b9d6b21d7e3a609e8cb11876efef70f218b'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('--verify', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
paths = subprocess.check_output(['git', '-C', str(args.android), 'ls-tree', '-r', '--name-only', PIN,
                                 'android/app/src/main/res'], text=True).splitlines()
sources, selected = {}, {}
keys = {'settings_split_screen', 'settings_split_screen_hint', 'settings_split_navigation_position',
        'settings_split_mini_player_position'} | {'settings_position_' + p for p in
        ['bottom_left', 'bottom_right', 'left', 'right', 'top_left', 'top_right']}
for path in paths:
    if not path.endswith('/strings.xml') or '/values' not in path:
        continue
    raw = subprocess.check_output(['git', '-C', str(args.android), 'show', PIN + ':' + path])
    sources[path] = hashlib.sha256(raw).hexdigest()
    strings = {n.attrib['name']: ''.join(n.itertext()) for n in ET.fromstring(raw).findall('string')}
    if '/values/' in path:
        selected = {key: strings[key] for key in sorted(keys)}
    else:
        assert not keys.intersection(strings), 'Localized overrides require a locale resource: ' + path
assert selected.keys() == keys
resource = 'parity/pinned-v065-split-layout.properties'
data = ('# Android v0.6.5 / ' + PIN + ' values/strings.xml.\n'
        '# No localized overrides in the pinned tree; Android default fallback.\n' +
        ''.join(key + '=' + value + '\n' for key, value in selected.items())).encode('utf-8')
icon_path = 'android/app/src/main/res/drawable-nodpi/ic_launcher_foreground.png'
icon = subprocess.check_output(['git', '-C', str(args.android), 'show', PIN + ':' + icon_path])
sources[icon_path] = hashlib.sha256(icon).hexdigest()
icon_resource = 'icons/pinned-v065-launcher-foreground.png'
manifest = {'pinned_android': PIN, 'verified_against_pure_export': True, 'source_sha256': sources,
            'resource_sha256': {resource: hashlib.sha256(data).hexdigest(), icon_resource: hashlib.sha256(icon).hexdigest()},
            'strings': selected, 'localized_overrides': [], 'fallback': 'Android default strings'}
outputs = {repo / 'desktop/src/main/resources' / resource: data,
           repo / 'desktop/src/main/resources' / icon_resource: icon,
           repo / 'parity/53244b9/evidence/split-layout/resources-source-audit.json':
               (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')}
for path, content in outputs.items():
    if args.verify:
        assert path.read_bytes() == content, path
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
print('PASS: ten split-layout strings, all locale fallback sources and exact launcher foreground')
