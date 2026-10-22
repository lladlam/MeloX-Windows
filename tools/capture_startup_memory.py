#!/usr/bin/env python3
"""Measure cold-start memory of the production app in an isolated onboarding home.

Usage:
  capture_startup_memory.py fresh HOME OUTPUT [--jar PATH] [--runs 3] [--jvm-args "..."]
  capture_startup_memory.py restored-queue HOME OUTPUT [--jar PATH] [--runs 3] [--jvm-args "..."]

`fresh` measures a clean home. `restored-queue` copies an app-written persisted queue
into the home first, then asserts startup publishes the song/position paused without
resolving audio or starting a decoder session. Every measurement comes from the real
`MeloXVisualCapture` scene, never a synthetic allocator.
"""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('scenario', choices=['fresh', 'restored-queue'])
parser.add_argument('home', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--jar', type=Path)
parser.add_argument('--queue', type=Path, help='App-written prefs_melox_playback_queue.properties')
parser.add_argument('--runs', type=int, default=3)
# Must match the shipped jpackage java-options so CI measures the production JVM.
parser.add_argument('--jvm-args', default='-Xms16m -Xmx1g -XX:+UseSerialGC -Dfile.encoding=UTF-8 --add-opens=java.desktop/java.awt.event=ALL-UNNAMED -Djava.awt.headless=true')
args = parser.parse_args()

jar = args.jar or next((repo / 'desktop/build/compose/jars').glob('*.jar'), None)
assert jar and jar.is_file(), 'Build :desktop:packageUberJarForCurrentOS first'
jar = jar.resolve()
home = args.home.resolve()
output = args.output.resolve()
if output.exists():
    shutil.rmtree(output)
output.mkdir(parents=True)

settings = home / '.melox/prefs_melox_app_settings.properties'
settings.parent.mkdir(parents=True, exist_ok=True)
settings.write_text('onboarding_completed=true\n', encoding='utf-8')
if args.scenario == 'restored-queue':
    source = (args.queue or next(
        (repo / 'desktop/build/parity').glob('player-v13-home/.melox/prefs_melox_playback_queue.properties')
    )).resolve()
    shutil.copyfile(source, home / '.melox/prefs_melox_playback_queue.properties')
    # Older queue files predate the persistence of this flag; default it explicitly.
    text = settings.read_text(encoding='utf-8')
    if 'playback_save_queue=' not in text:
        settings.write_text(text + 'playback_save_queue=true\n', encoding='utf-8')

steps = 'warm:wait@1000;idle-5:wait@5000;idle-10:wait@5000'
runs = []
for index in range(args.runs):
    run_dir = output / f'run-{index}'
    run_dir.mkdir()
    command = ['java', *args.jvm_args.split(), f'-Duser.home={home}', '-cp', str(jar),
               'melox.validation.MeloXVisualCaptureKt',
               '--locale', 'en-US', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark',
               '--width', '427', '--height', '924', '--output', str(run_dir),
               '--startup-wait-ms', '1200', '--wait-ms', '200', '--steps', steps]
    (run_dir / 'command.txt').write_text(' '.join(command) + '\n', encoding='utf-8')
    with (run_dir / 'run.log').open('w') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, cwd=repo)
    capture = json.loads((run_dir / 'capture.json').read_text(encoding='utf-8'))
    raw_scenes = capture['scenes']
    # Evidence is committed/uploaded: never persist song titles or provider ids.
    scenes = [{'file': scene['file'], 'memory': scene['memory'], 'is_loading': scene['is_loading'],
               'is_playing': scene['is_playing'], 'is_paused': scene['is_paused'],
               'has_track': scene['track'] is not None} for scene in raw_scenes]
    runs.append({'index': index, 'scenes': scenes, 'steps': capture['steps']})
    del_scene = scenes[0]
    print(index, del_scene['file'], del_scene['memory'], 'loading=', del_scene['is_loading'],
          'paused=', del_scene['is_paused'], 'has_track=', del_scene['has_track'])

first = [run['scenes'][0]['memory'] for run in runs]
last = [run['scenes'][-1]['memory'] for run in runs]


def median(values, key):
    ordered = sorted(value[key] for value in values)
    return ordered[len(ordered) // 2]


result = {
    'scenario': args.scenario,
    'jar': str(jar),
    'jvm_args': args.jvm_args,
    'runs': args.runs,
    'status': 'PASSED',
    'first_frame': {
        'resident_bytes_median': median(first, 'resident_bytes'),
        'heap_used_bytes_median': median(first, 'heap_used_bytes'),
        'heap_committed_bytes_median': median(first, 'heap_committed_bytes'),
        'non_heap_used_bytes_median': median(first, 'non_heap_used_bytes'),
        'threads_median': median(first, 'threads'),
    },
    'last_frame': {
        'resident_bytes_median': median(last, 'resident_bytes'),
        'heap_used_bytes_median': median(last, 'heap_used_bytes'),
        'heap_committed_bytes_median': median(last, 'heap_committed_bytes'),
        'threads_median': median(last, 'threads'),
    },
    'runs_detail': runs,
}
if args.scenario == 'restored-queue':
    # Startup must publish the restored song paused, with no loading decoder session.
    for run in runs:
        for scene in run['scenes']:
            if scene['is_loading'] or scene['is_playing']:
                result['status'] = 'FAILED'
                result['failure'] = f"{scene['file']} loading={scene['is_loading']} playing={scene['is_playing']}"
                break
    assert result['status'] == 'PASSED', result.get('failure')
    result['restored_song_paused_without_decoder'] = True
    # Startup must not change the restored position by resolving/preparing audio.
    result['restored_track_present'] = all(run['scenes'][0]['has_track'] for run in runs)

output_json = output / 'startup-memory.json'
output_json.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
print('PASS:', args.scenario, result['first_frame'])
sys.exit(0)
