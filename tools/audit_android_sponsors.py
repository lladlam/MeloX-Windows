#!/usr/bin/env python3
"""Compare additive Windows sponsor resources to exact Android git and working-tree strings."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('--revision', required=True)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
android = args.android.resolve()
revision = subprocess.check_output(['git', 'rev-parse', args.revision], cwd=android, text=True).strip()
sources = {
    '': 'android/app/src/main/res/values/strings.xml',
    '-zh-rCN': 'android/app/i18n/values-zh-rCN/strings.xml',
    '-zh-rTW': 'android/app/i18n/values-zh-rTW/strings.xml',
    '-ja': 'android/app/i18n/values-ja/strings.xml',
}
sha = lambda data: hashlib.sha256(data).hexdigest()
records = {}
expected_keys = None
for suffix, source in sources.items():
    pure = subprocess.check_output(['git', 'show', f'{revision}:{source}'], cwd=android)
    current = (android / source).read_bytes()
    assert pure == current, f'Android working tree differs from {revision}: {source}'
    strings = {item.attrib['name']: ''.join(item.itertext()) for item in ET.fromstring(pure).findall('string')}
    source_values = {key: value for key, value in strings.items()
                     if key == 'settings_about_sponsors_title' or key == 'settings_about_sponsors_note'
                     or key.startswith('settings_about_sponsor_')}
    resource = f'parity/android-about-sponsors{suffix}.properties'
    data = (repo / 'desktop/src/main/resources' / resource).read_bytes()
    exported = dict(line.split('=', 1) for line in data.decode('utf-8').splitlines() if line and not line.startswith('#'))
    assert exported == source_values, (source, sorted(exported.keys() ^ source_values.keys()))
    if suffix == '':
        expected_keys = set(exported)
    else:
        assert set(exported) <= expected_keys
    records[resource] = {'sha256': sha(data), 'bytes': len(data), 'android_source': source,
                         'android_source_sha256': sha(pure), 'keys': len(exported)}
result = {'android_commit': revision, 'verified_against_pure_git_blob_and_working_tree': True,
          'android_fallback': 'localized missing keys use default resources', 'resources': records}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')
print(f'PASS Android sponsor resources: {len(records)} locales, {len(expected_keys)} default keys, {revision}')
