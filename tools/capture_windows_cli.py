#!/usr/bin/env python3
"""Exercise the production CLI and settings using a separate real client process.

UI actions use pointer input. Audio comes from generated WAV files and is captured
by the explicit software PCM endpoint; this does not assert physical audibility.
"""
import json
import math
import os
from pathlib import Path
import shutil
import struct
import subprocess
import sys
import time
import wave

layout, home_arg, output_arg = sys.argv[1:4]
assert layout in ('mobile', 'desktop')
keep_enabled = sys.argv[4:] == ['--keep-enabled']
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
repo = Path(__file__).resolve().parents[1]
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                 f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
music = home / 'generated-audio'
music.mkdir()
for index, frequency in enumerate((220, 330)):
    samples = b''.join(struct.pack('<h', int(4000 * math.sin(i * math.tau * frequency / 16000))) for i in range(16_000 * 16))
    with wave.open(str(music / f'Generated-{index:02}.wav'), 'wb') as audio:
        audio.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
        audio.writeframes(samples)

steps = [('consent', 'tap@remote-reject', 'cloud-enabled:false')]
if layout == 'desktop':
    steps += [('developer', 'tap@desktop-settings-tab:Developer', ''),
              ('experimental', 'tap@desktop-settings-subtab:Experimental', 'tag:settings-page:Experimental')]
else:
    steps += [('reveal-experimental', 'reveal@settings-open-experimental', ''),
              ('experimental', 'tap@settings-open-experimental', 'tag:settings-experimental')]
steps += [('enable', 'tap@setting-experimental_cli_enabled', 'checked:setting-experimental_cli_enabled,pref-bool:experimental_cli_enabled:true'),
          ('commands', 'wait@40000', 'checked:setting-experimental_cli_enabled')]
if not keep_enabled:
    steps += [('disable', 'tap@setting-experimental_cli_enabled', 'unchecked:setting-experimental_cli_enabled,pref-bool:experimental_cli_enabled:false')]
(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={checks}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--scan-local-dir', str(music),
        '--capture-pcm', '--width', '1100' if layout == 'desktop' else '430', '--height', '850',
        '--output', str(output), '--startup-wait-ms', '1200', '--wait-ms', '220',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
helper = [sys.executable, str(repo / 'tools/melox_cli.py'), '--home', str(home)]


def command(*arguments, ok=True):
    completed = subprocess.run(helper + list(arguments), cwd=repo, capture_output=True, text=True, encoding='utf-8', timeout=20)
    response = json.loads(completed.stdout)
    assert response.get('ok') is ok and (completed.returncode == 0) is ok, (arguments, response, completed.stderr)
    return response


command('status', ok=False)
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    process = subprocess.Popen([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
                                '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
                                '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                               cwd=repo, stdout=log, stderr=subprocess.STDOUT)
    try:
        endpoint = home / '.melox/cli-endpoint.json'
        deadline = time.monotonic() + 180
        while not endpoint.exists():
            assert process.poll() is None, 'Capture exited before CLI was enabled'
            assert time.monotonic() < deadline, 'CLI enable timed out'
            time.sleep(.1)
        results = {}
        results['status'] = command('status')
        assert not results['status']['playing']
        results['search'] = command('search', 'Generated', '--source', 'local')
        assert len(results['search']['tracks']) == 2
        results['search_play'] = command('search-play', 'Generated', '--source', 'local', '--index', '1')
        assert results['search_play']['title'] == results['search']['tracks'][1]['title']
        results['queue'] = command('queue')
        assert len(results['queue']['queue']) == 2
        deadline = time.monotonic() + 8
        while not any(p.stat().st_size > 0 for p in output.glob('pcm-output-*.s16le')):
            assert time.monotonic() < deadline, 'No real decoded PCM reached the software output'
            time.sleep(.1)
        results['pause'] = command('pause')
        assert not results['pause']['playWhenReady']
        results['seek'] = command('seek', '2000')
        assert results['seek']['positionMs'] == 2000
        results['volume'] = command('volume', '0.5')
        assert abs(results['volume']['volume'] - .5) < .001
        results['repeat'] = command('repeat', 'all')
        assert results['repeat']['repeat'] == 2
        results['shuffle'] = command('shuffle', 'on')
        assert results['shuffle']['shuffle']
        results['next'] = command('next')
        assert results['next']['title'] == results['search']['tracks'][0]['title']
        results['previous'] = command('previous')
        assert results['previous']['title'] == results['search']['tracks'][1]['title']
        results['stop'] = command('stop')
        assert not results['stop']['playWhenReady']
        assert len(command('queue')['queue']) == 2
        results['play'] = command('play')
        assert results['play']['playWhenReady']
        results['invalid'] = command('volume', 'nan', ok=False)
        results['final_pause'] = command('pause')
        assert not results['final_pause']['playWhenReady']
        (output / 'cli-results.json').write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding='utf-8')
        assert process.wait(timeout=120) == 0, 'Production UI capture failed'
    finally:
        if process.poll() is None:
            process.terminate()
            process.wait(timeout=15)
if process.returncode:
    raise SystemExit(process.returncode)
assert not endpoint.exists(), 'Endpoint survived application exit'
command('status', ok=False)
assert ('experimental_cli_enabled=true' in prefs.read_text(encoding='iso-8859-1')) == keep_enabled
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for scene in capture['scenes'] for a in scene.get('assertions', []))
assert any(p.stat().st_size > 0 for p in output.glob('pcm-output-*.s16le'))
print(f'PASS CLI {layout}: {len(capture["scenes"])} scenes, {len(results)} real client commands, software PCM')
