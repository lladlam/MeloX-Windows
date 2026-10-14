#!/usr/bin/env python3
"""Check real paused Skyline geometry/visibility round-trip; not Android pixel acceptance."""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('capture', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
capture = json.loads((args.capture / 'capture.json').read_text(encoding='utf-8'))
assert len(capture['scenes']) == len(capture['steps']) + len(capture['frames']) + 1, 'Truncated replay'
assert all(a['passed'] for s in capture['scenes'] for a in s.get('assertions', []))
assert capture['steps'][-1] == 'final-pause:tap@mini-player-play-pause'


def nodes(stage):
    paths = list(args.capture.glob('[0-9]*-' + stage + '.semantics.json'))
    assert len(paths) == 1, (stage, paths)
    return {n['tag']: n for n in json.loads(paths[0].read_text(encoding='utf-8')) if n.get('tag')}


baseline, changed, restored = map(nodes, ('baseline', 'changed', 'restored'))
for current in (baseline, restored):
    assert current['skyline-song-info']['bounds'][2] > current['skyline-song-info']['bounds'][0]
    assert current['skyline-ambient']['bounds'][2] > current['skyline-ambient']['bounds'][0]
    assert current['skyline-next-line']['text']
assert 'skyline-song-info' not in changed and 'skyline-ambient' not in changed
lines = [n['skyline-current-line'] for n in (baseline, changed, restored)]
assert lines[0]['text'] and len({n['text'] for n in lines}) == 1, 'Pause/seek anchor changed during settings round trip'
restored_error = max(abs(a - b) for a, b in zip(lines[0]['bounds'], lines[2]['bounds']))
assert restored_error < .01, restored_error
changed_delta = max(abs(a - b) for a, b in zip(lines[0]['bounds'], lines[1]['bounds']))
assert changed_delta > 10, 'Changed settings did not affect real layout'
result = dict(status='PASSED', check='same-process real Skyline settings/layout round trip',
              scenes=len(capture['scenes']), frames=len(capture['frames']),
              passed_assertions=sum(len(s.get('assertions', [])) for s in capture['scenes']),
              unchanged_paused_lyric_text=True, changed_geometry_max_delta_px=changed_delta,
              restored_geometry_max_error_px=restored_error,
              song_info_and_ambient_removed_and_restored=True,
              android_pixel_motion_acceptance='BLOCKED', native_windows_power_acceptance='BLOCKED')
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('PASS: real paused Skyline layout changes and restores exactly; Android pixels remain BLOCKED')
