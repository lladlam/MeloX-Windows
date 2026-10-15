#!/usr/bin/env python3
"""Verify complete physical controls and independent-JVM reload action-sheet evidence."""
import argparse
import hashlib
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('controls', type=Path)
parser.add_argument('reload', type=Path)
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
records, homes = [], []
required = [
    {'state:song-action-取消喜爱:liked', 'local-playlist-size:Replay generated list:1', 'sleep-timer:true',
     'text:请先登录网易云音乐', 'pref-string:recognition_duration:3', 'ui-font:system'},
    {'state:song-action-取消喜爱:liked', 'local-playlist-size:Replay generated list:1', 'sleep-timer:true',
     'sleep-timer:false', 'pref-string:recognition_duration:3', 'ui-font:system', 'ui-font:pinned'},
]
for directory, expected in zip([args.controls, args.reload], required):
    path = directory / 'capture.json'
    data = path.read_bytes()
    capture = json.loads(data.decode('utf-8'))
    assertions = [check for scene in capture['scenes'] for check in scene.get('assertions', [])]
    assert assertions and all(check['passed'] for check in assertions), directory
    observed = {check['expected'] for check in assertions}
    assert expected <= observed, sorted(expected - observed)
    assert capture['renderer'] == 'production ImageComposeScene'
    assert capture['steps'] and capture['scenes']
    assert (directory / 'steps.txt').is_file() and (directory / 'expect.txt').is_file()
    assert any(output['written_frames'] > 0 and output['closed'] for output in capture['pcm_outputs']), 'No real decoded PCM'
    homes.append(capture['home'])
    records.append({'capture': directory.name, 'scenes': len(capture['scenes']), 'steps': len(capture['steps']),
                    'assertions': len(assertions), 'capture_sha256': hashlib.sha256(data).hexdigest(),
                    'pcm_written_frames': sum(output['written_frames'] for output in capture['pcm_outputs'])})
assert homes[0] == homes[1], 'Reload must read the same persisted home'
result = {'status': 'PASSED', 'captures': records,
          'android_pixel_motion_acceptance': 'BLOCKED: no same-content pinned Android frames',
          'live_recognition_hit_and_microphone': 'NOT_TESTED_BY_THIS_REPLAY',
          'two_account_listen_together': 'NOT_TESTED_BY_THIS_REPLAY', 'physical_audible_acceptance': 'BLOCKED'}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('PASS:', sum(record['scenes'] for record in records), 'scenes /', sum(record['assertions'] for record in records), 'assertions')
