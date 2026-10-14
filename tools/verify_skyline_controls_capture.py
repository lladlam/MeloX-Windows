#!/usr/bin/env python3
"""Verify all Skyline pointer keys, scope choices, reset boundaries and two new-JVM reloads."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('root', type=Path, help='Contains controls, reload, reset and reload-reset output directories')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
audit = json.loads((repo / 'parity/3f877ea/evidence/skyline-settings-source-audit.json').read_text(encoding='utf-8'))
inputs = json.loads((args.root / 'controls/control-inputs.json').read_text(encoding='utf-8'))
reset_values = json.loads((args.root / 'controls/reset-inputs.json').read_text(encoding='utf-8'))
assert set(inputs) == set(audit['canonical_keys']) | {'player_screen_awake_mode'} and len(inputs) == 18
assert set(reset_values) == set(inputs)
preserved = {'lyrics_skyline_enabled', 'lyrics_skyline_song_info', 'lyrics_skyline_keep_awake',
             'lyrics_skyline_ambient_lines', 'player_screen_awake_mode'}
assert {k for k in inputs if inputs[k] == reset_values[k]} == preserved
captures, hashes = {}, {}
for phase in ('controls', 'reload', 'reset', 'reload-reset'):
    path = args.root / phase / 'capture.json'
    c = json.loads(path.read_text(encoding='utf-8'))
    assert c['renderer'] == 'production ImageComposeScene' and not c['frames']
    assert len(c['scenes']) == len(c['steps']) + 1, (phase, 'Truncated replay')
    assert all(a['passed'] for scene in c['scenes'] for a in scene.get('assertions', [])), phase
    captures[phase], hashes[phase] = c, hashlib.sha256(path.read_bytes()).hexdigest()
assert len({c['home'] for c in captures.values()}) == 1


def assert_values(scene, values):
    assertions = {a['expected'] for a in scene.get('assertions', [])}
    assert sum(a.startswith('runtime:') for a in assertions) == len(values)
    for key, (kind, value) in values.items():
        assert f'pref-{kind}:{key}:{value}' in assertions, (scene['file'], key)


pointer_keys, scopes = set(), set()
controls = captures['controls']
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
        assert any(a.startswith('runtime:') for a in assertions), key
        if key == 'player_screen_awake_mode':
            scopes.add(value)
        if inputs[key] == [kind.removeprefix('pref-'), value]:
            pointer_keys.add(key)
assert pointer_keys == set(inputs)
assert scopes == {'Disabled', 'Player', 'Lyrics', 'HiddenLyricsInterface'}
assert controls['steps'][-1] == 'reload:reload-settings'
assert_values(controls['scenes'][-1], inputs)
for phase, values in [('reload', inputs), ('reload-reset', reset_values)]:
    c = captures[phase]
    assert c['steps'] == ['root-skyline:reveal@settings-open-skylinelyrics', 'open:tap@settings-open-skylinelyrics']
    assert_values(c['scenes'][0], values)
    assert_values(c['scenes'][-1], values)
reset = captures['reset']
assert_values(reset['scenes'][0], inputs)
assert reset['steps'][-2:] == ['reset:tap@skyline-reset', 'reload:reload-settings']
assert_values(reset['scenes'][-2], reset_values)
assert_values(reset['scenes'][-1], reset_values)
result = dict(status='PASSED', pinned_android=audit['pinned_android'], skyline_keys=17,
              screen_awake_scope_choices=sorted(scopes), pointer_verified_keys=sorted(pointer_keys),
              separately_reloaded_changed_and_reset_values=True, reset_preserved_keys=sorted(preserved),
              reset_changed_keys=13, captures_sha256=hashes,
              scene_counts={k: len(c['scenes']) for k, c in captures.items()},
              passed_assertions=sum(len(s.get('assertions', [])) for c in captures.values() for s in c['scenes']),
              android_pixel_motion_acceptance='BLOCKED', physical_windows_power_acceptance='BLOCKED')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('PASS: all 17 Skyline keys, four awake scopes, exact reset boundary and separate-JVM reloads')
