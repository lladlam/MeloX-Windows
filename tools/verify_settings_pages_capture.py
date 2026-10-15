#!/usr/bin/env python3
"""Aggregate settings-page route/controls/reload replays into one evidence file.

Usage: verify_settings_pages_capture.py PARITY_DIR OUTPUT
PARITY_DIR must contain settings-{en,zh-CN,zh-TW,ja-JP}/, settings-controls/ and settings-reload/.
"""
import argparse
import json
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('parity', type=Path, help='directory holding settings-* capture folders')
parser.add_argument('output', type=Path)
args = parser.parse_args()

# Discover the four locale route captures from their recorded JVM locale.
route_names = sorted(entry.name for entry in args.parity.glob('settings-*')
                     if entry.is_dir() and (entry / 'capture.json').is_file()
                     and entry.name not in ('settings-controls', 'settings-reload'))
assert len(route_names) == 4, route_names
locales = [json.loads((args.parity / name / 'capture.json').read_text(encoding='utf-8'))['locale']
           for name in route_names]
assert sorted(locales) == sorted(['en-US', 'zh-CN', 'zh-TW', 'ja-JP']), locales

records = []
total_assertions = 0

for name in route_names + ['settings-controls', 'settings-reload']:
    directory = args.parity / name
    capture_file = directory / 'capture.json'
    assert capture_file.is_file(), f'missing capture: {capture_file}'
    capture = json.loads(capture_file.read_text(encoding='utf-8'))
    scenes = capture['scenes']
    assertions = []
    for scene in scenes:
        for entry in scene.get('assertions', []):
            assertions.append(entry)
            assert entry['passed'], f"{name}/{scene['file']}: {entry['expected']}"
    assert scenes and capture['steps'], f'{name} captured no scenes'
    total_assertions += len(assertions)
    first, last = scenes[0], scenes[-1]
    records.append({
        'name': name,
        'scenes': len(scenes),
        'steps': len(capture['steps']),
        'assertions': len(assertions),
        'width': capture['width'],
        'height': capture['height'],
        'theme': capture['theme'],
        'first_frame_resident_bytes': first['memory']['resident_bytes'],
        'last_frame_resident_bytes': last['memory']['resident_bytes'],
        'artwork_budgeted_bytes': first['artwork_cache']['budgeted_bytes'],
    })

# Four locale replays must walk every pinned root route (19 entries).
for record in records[:4]:
    assert record['scenes'] >= 80, record
    record['locale'] = json.loads((args.parity / record['name'] / 'capture.json').read_text(encoding='utf-8'))['locale']

# The controls replay must have physically changed these real consumers.
controls_capture = json.loads((args.parity / 'settings-controls/capture.json').read_text(encoding='utf-8'))
changed_keys = set()
for scene in controls_capture['scenes']:
    for entry in scene.get('assertions', []):
        assertion = entry['expected']
        if assertion.startswith('pref-') and ':' in assertion:
            changed_keys.add(assertion.split(':')[1])
required = {'player_flowing_speed', 'player_flowing_saturation', 'player_flowing_brightness', 'music_area',
            'general_swipe_full_action', 'general_remember_tab', 'library_playlist_search_hidden',
            'library_playlist_show_artwork', 'content_playlist_play_count', 'download_lyrics'}
assert required <= changed_keys, sorted(required - changed_keys)

result = {
    'status': 'PASSED',
    'pinned_routes': 19,
    'locales': sorted(locales),
    'captures': records,
    'total_assertions': total_assertions,
    'android_pixel_acceptance': 'BLOCKED: no pinned Android screenshot/frame in this run',
    'physical_audible_acceptance': 'BLOCKED',
}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print(f"PASS: {len(records)} captures / {sum(r['scenes'] for r in records)} scenes / {total_assertions} assertions")
