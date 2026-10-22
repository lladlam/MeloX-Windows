#!/usr/bin/env python3
"""Check complete pointer/control and separate-process persistence evidence.

Rejects truncated captures, missing keys, failed assertions and value injection.
"""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('controls', type=Path)
parser.add_argument('reload', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
inventory = json.loads((repo / 'parity/3f877ea/evidence/search-player-next-lyrics-control-gaps.json').read_text(encoding='utf-8'))
inputs = json.loads((args.controls / 'control-inputs.json').read_text(encoding='utf-8'))
assert set(inputs) == set(inventory['pinned_setting_keys']) and len(inputs) == 60


def read_capture(directory):
    path = directory / 'capture.json'
    capture = json.loads(path.read_text(encoding='utf-8'))
    assert capture['renderer'] == 'production ImageComposeScene'
    assert not capture['frames'], 'Control audit expects one settled scene per step'
    assert len(capture['scenes']) == len(capture['steps']) + 1, 'Truncated production replay'
    for scene in capture['scenes']:
        assert all(a['passed'] for a in scene.get('assertions', [])), scene['file']
    return capture, hashlib.sha256(path.read_bytes()).hexdigest()


controls, controls_hash = read_capture(args.controls)
reload, reload_hash = read_capture(args.reload)
assert controls['home'] == reload['home']
pointer_keys = set()
for step, scene in zip(controls['steps'], controls['scenes'][1:]):
    name, action = step.split(':', 1)
    assert action in ('escape', 'reload-settings') or action.startswith(('tap@', 'reveal@', 'slider@')), action
    if not action.startswith(('tap@', 'slider@')):
        continue
    assertions = [a['expected'] for a in scene.get('assertions', [])]
    for assertion in assertions:
        if not assertion.startswith('pref-'):
            continue
        kind, key, value = assertion.split(':', 2)
        assert inputs[key] == [kind.removeprefix('pref-'), value], assertion
        assert any(a.startswith('runtime:') for a in assertions), key
        pointer_keys.add(key)
assert pointer_keys == set(inputs), 'Every key must have a pointer-triggered preference/runtime assertion'
assert controls['steps'][-2:] == ['reload:reload-settings', 'back:escape']
assert reload['steps'] == ['open:tap@settings-open-lyrics']
for scene in (controls['scenes'][-2], reload['scenes'][0], reload['scenes'][1]):
    assertions = {a['expected'] for a in scene.get('assertions', [])}
    for key, (kind, value) in inputs.items():
        assert f'pref-{kind}:{key}:{value}' in assertions, (scene['file'], key)
    assert len([a for a in assertions if a.startswith('runtime:')]) == 60
result = dict(status='PASSED', pinned_android=inventory['pinned_android'], canonical_keys=60,
              pointer_verified_keys=sorted(pointer_keys), separate_jvm_reload_verified=True,
              controls_capture_sha256=controls_hash, reload_capture_sha256=reload_hash,
              controls_scenes=len(controls['scenes']), reload_scenes=len(reload['scenes']),
              passed_assertions=sum(len(s.get('assertions', [])) for c in (controls, reload) for s in c['scenes']),
              android_pixel_motion_acceptance='BLOCKED', windows_user_acceptance='PENDING')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n')
print('PASS: all 60 keys changed by pointer input and reloaded from disk in a separate JVM')
