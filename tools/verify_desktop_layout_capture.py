#!/usr/bin/env python3
"""Independently recount Desktop captures, source geometry and actual PCM bytes."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import struct

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--prefix', default='desktop-layout-')
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--require-cold-controls', action='store_true')
args = parser.parse_args()
modes = ['controls', 'reload', 'restore', 'reload-current', 'home-dark', 'home-light', 'lyrics-light']
if args.require_cold_controls or (args.directory / (args.prefix + 'cold-controls') / 'capture.json').is_file():
    modes.append('cold-controls')
reports = []

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

for mode in modes:
    directory = args.directory / (args.prefix + mode)
    path = directory / 'capture.json'
    data = json.loads(path.read_text(encoding='utf-8'))
    assert data['renderer'] == 'production ImageComposeScene'
    assert data['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
    assert data['physical_audible_acceptance'] == 'BLOCKED'
    scenes = data['scenes']
    steps = (directory / 'steps.txt').read_text(encoding='utf-8').splitlines()
    assert data['steps'] == steps and len(scenes) == len(steps) + 1
    assertions = [a for scene in scenes for a in scene['assertions']]
    assert assertions and all(a['passed'] for a in assertions), str(path)
    dimensions = {'desktop-sidebar': (199, None), 'desktop-bottom-player': (700, 54),
                  'desktop-inspector:Queue': (258, None), 'desktop-inspector:Lyrics': (258, None)}
    geometries = []
    for scene in scenes:
        image = directory / scene['file']
        assert image.is_file()
        nodes = json.loads(image.with_suffix('.semantics.json').read_text(encoding='utf-8'))
        for node in nodes:
            if node['tag'] not in dimensions:
                continue
            width, height = dimensions[node['tag']]
            x0, y0, x1, y1 = node['bounds']
            assert abs((x1 - x0) / data['density'] - width) <= 1, (mode, scene['file'], node['tag'])
            if height is not None:
                assert abs((y1 - y0) / data['density'] - height) <= 1
            geometries.append({'scene': scene['file'], 'tag': node['tag'], 'bounds': node['bounds']})
    pcm = []
    for endpoint in data['pcm_outputs']:
        raw = directory / endpoint['file']
        frames = endpoint['written_frames']
        assert endpoint['hardware_output'] is False and endpoint['closed']
        assert raw.stat().st_size == frames * 4, (mode, raw.name, raw.stat().st_size, frames)
        if not frames:
            continue
        with raw.open('rb') as stream:
            block = stream.read(8192 * 4)
        samples = [left for left, _ in struct.iter_unpack('<hh', block)]
        assert len(samples) >= 1000, (mode, raw.name, 'insufficient spectral sample')
        rms = math.sqrt(sum(s * s for s in samples) / len(samples))
        assert rms > 20, (mode, raw.name, rms)
        powers = {}
        for hz in [110, 220, 330, 440]:
            coefficient = 2 * math.cos(2 * math.pi * hz / 44100)
            previous = previous2 = 0.0
            for sample in samples:
                current = sample + coefficient * previous - previous2
                previous2, previous = previous, current
            powers[hz] = previous2 ** 2 + previous ** 2 - coefficient * previous * previous2
        peak = max(powers, key=powers.get)
        assert peak in (220, 330), (mode, raw.name, powers)
        pcm.append({'file': raw.name, 'bytes': raw.stat().st_size, 'written_frames': frames,
                    'sha256': digest(raw), 'sample_frames': len(samples), 'rms_s16': rms,
                    'strongest_test_frequency_hz': peak, 'spectral_power': powers})
    if not mode.startswith('home-'):
        assert sum(p['written_frames'] for p in pcm) > 44100
    reports.append({'mode': mode, 'home': data['home'], 'scenes': len(scenes), 'assertions': len(assertions),
                    'capture_sha256': digest(path), 'geometries': geometries, 'pcm': pcm, 'status': 'PASSED'})
assert reports[0]['home'] == reports[1]['home'], 'controls and reload must share one persisted home'
assert reports[2]['home'] == reports[3]['home'], 'restore and reload-current must share one persisted home'
assert reports[0]['home'] != reports[2]['home'], 'Desktop-On and restored-Off replays must use independent homes'
result = {'desktop_reference': 'a41ea0e1e1fa69fb543d827b853fe3aff5420951', 'status': 'PASSED',
          'scope': 'Windows implementation replay/source geometry/real PCM; native source same-content pixel and hardware acceptance pending',
          'totals': {'replays': len(reports), 'scenes': sum(r['scenes'] for r in reports), 'assertions': sum(r['assertions'] for r in reports)},
          'replays': reports}
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({'status': result['status'], **result['totals']}))
