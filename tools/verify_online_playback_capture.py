#!/usr/bin/env python3
"""Read real decoded PCM from a production UI replay; publish metrics, not music.

This verifies the explicitly labelled software endpoint, not speaker audibility.
"""
import argparse
import array
import hashlib
import json
import math
from pathlib import Path
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('capture', type=Path)
parser.add_argument('--track', required=True, help='Native provider identity, e.g. netease:139774')
parser.add_argument('--output', type=Path, required=True)
args = parser.parse_args()
evidence = json.loads((args.capture / 'capture.json').read_text())
assert evidence['audio_endpoint'] == 'VALIDATION_SOFTWARE_PCM_OUTPUT'
playing = [scene for scene in evidence['scenes'] if scene['track'] == args.track
           and scene['is_playing'] and scene['position_ms'] >= 500 and scene['error'] is None]
assert playing, 'No successful advancing production scene for the requested native track'
outputs = []
for endpoint in evidence['pcm_outputs']:
    assert endpoint['closed'] and not endpoint['hardware_output'], 'Output must be released and labelled software'
    data = (args.capture / endpoint['file']).read_bytes()
    assert len(data) % 4 == 0 and len(data) == endpoint['written_frames'] * 4
    assert 0 <= endpoint['consumed_frames'] <= endpoint['written_frames']
    samples = array.array('h')
    samples.frombytes(data)
    if sys.byteorder != 'little':
        samples.byteswap()
    rms = math.sqrt(sum(value * value for value in samples) / len(samples)) if samples else 0
    outputs.append(dict(endpoint, bytes=len(data), sha256=hashlib.sha256(data).hexdigest(),
                        rms_s16=rms, peak_s16=max((abs(value) for value in samples), default=0),
                        nonzero_samples=sum(value != 0 for value in samples),
                        consumed_seconds=endpoint['consumed_frames'] / 44100))
assert any(output['consumed_seconds'] >= .5 and output['rms_s16'] > 0 for output in outputs), 'No consumed non-silent PCM'
result = dict(status='PASSED', track=args.track, endpoint='VALIDATION_SOFTWARE_PCM_OUTPUT',
              pcm_format='stereo S16LE / 44100 Hz', physical_audible_acceptance='BLOCKED',
              capture_sha256=hashlib.sha256((args.capture / 'capture.json').read_bytes()).hexdigest(),
              live_audio_published=False, outputs=outputs,
              advancing_scenes=len(playing), maximum_observed_position_ms=max(scene['position_ms'] for scene in playing))
args.output.parent.mkdir(parents=True, exist_ok=True)
args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n')
print(f'PASS: {args.track}, {len(outputs)} released PCM endpoints, non-silent consumed audio; hardware audibility BLOCKED')
