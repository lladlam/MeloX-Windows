#!/usr/bin/env python3
"""Export immutable fingerprint runtime bytes from the pinned Git tree."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess

PIN = '3f877eaffe30c82eb6d24eb3647702f5bc554dd3'
ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--android', type=Path, default=ROOT.parent / 'MeloX-Android')
parser.add_argument('--verify', action='store_true')
args = parser.parse_args()
records = {}
for name in ['afp.js', 'afp.wasm.js', 'NeteaseAPI-LICENSE.txt', 'NeteaseAudioFingerprint-NOTICE.txt']:
    source = 'android/app/src/main/assets/AudioFingerprint/' + name
    data = subprocess.check_output(['git', 'show', PIN + ':' + source], cwd=args.android)
    resource = 'AudioFingerprint/' + name
    target = ROOT / 'shared/src/desktopMain/resources' / resource
    if args.verify:
        assert target.read_bytes() == data, target
    else:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    records[resource] = {'source': source, 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}
audit = {'pinned_android': PIN, 'verified_against_pure_export': True, 'resources': records}
manifest = ROOT / 'parity/3f877ea/evidence/recognition-source-audit.json'
text = json.dumps(audit, indent=2) + '\n'
if args.verify:
    assert manifest.read_text(encoding='utf-8') == text
else:
    manifest.write_text(text, encoding='utf-8')
print('PASS: 4 fingerprint runtime/license blobs match pinned Git bytes')
