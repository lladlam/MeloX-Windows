#!/usr/bin/env python3
"""Verify the lyric-language strings against the official v0.6.5 Git tree."""
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
keys = {'settings_lyrics_language' + suffix for suffix in ['', '_system', '_zh_hans', '_zh_hant', '_en', '_tr']}
for path in paths:
    if not path.endswith('/strings.xml') or '/values' not in path:
        continue
    raw = subprocess.check_output(['git', '-C', str(args.android), 'show', PIN + ':' + path])
    sources[path] = hashlib.sha256(raw).hexdigest()
    strings = {n.attrib['name']: ''.join(n.itertext()) for n in ET.fromstring(raw).findall('string')}
    if '/values/' in path:
        selected = {key: strings[key] for key in sorted(keys)}
    else:
        assert not keys.intersection(strings), 'Localized overrides now require a locale resource: ' + path
assert selected.keys() == keys
resource = 'parity/pinned-v065-lyric-language.properties'
data = ('# Android v0.6.5 / ' + PIN + ' values/strings.xml.\n'
        '# No localized overrides exist for these keys in the pinned tree; use Android\'s default fallback.\n' +
        ''.join(key + '=' + value + '\n' for key, value in selected.items())).encode('utf-8')
manifest = {'pinned_android': PIN, 'verified_against_pure_export': True, 'source_sha256': sources,
            'resource_sha256': {resource: hashlib.sha256(data).hexdigest()}, 'strings': selected,
            'localized_overrides': [], 'fallback': 'Android default strings'}
outputs = {repo / 'desktop/src/main/resources' / resource: data,
           repo / 'parity/53244b9/evidence/lyric-language/strings-source-audit.json':
               (json.dumps(manifest, ensure_ascii=False, indent=2) + '\n').encode('utf-8')}
for path, content in outputs.items():
    if args.verify:
        assert path.read_bytes() == content, path
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(content)
print('PASS: six official v0.6.5 lyric-language strings and all locale fallback sources')
