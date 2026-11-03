#!/usr/bin/env python3
"""Export the current Android Turkish draft without modifying the Android tree."""
import argparse
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('--verify', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
raw = (args.android / 'android/app/i18n/values-tr/strings.xml').read_bytes()
values = {}
for node in ET.fromstring(raw).findall('string'):
    value = ''.join(node.itertext()).replace("\\'", "'").replace('\\"', '"')
    value = value.replace('\\n', '\n').replace('\\t', '\t')
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    value = value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')
    values[node.attrib['name']] = value
digest = hashlib.sha256(raw).hexdigest()
output = ('# Android Turkish draft; source SHA-256 ' + digest + '\n' +
          ''.join(key + '=' + values[key] + '\n' for key in sorted(values))).encode('utf-8')
target = repo / 'desktop/src/main/resources/parity/android-current-tr.properties'
if args.verify:
    assert target.read_bytes() == output, target
else:
    target.write_bytes(output)
print(f'PASS: {len(values)} Turkish strings; Android source SHA-256 {digest}')
