#!/usr/bin/env python3
"""Replay AutoSync settings using real pointer/scroll input and a separate home."""
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

layout, mode, home_arg, output_arg = sys.argv[1:5]
assert layout in ('mobile', 'desktop') and mode in ('controls', 'reload')
fixtures = None
if sys.argv[5:]:
    assert len(sys.argv) == 7 and sys.argv[5] == '--fixtures' and mode == 'controls'
    fixtures = Path(sys.argv[6]).resolve()
repo = Path(__file__).resolve().parents[1]
home, output = Path(home_arg).resolve(), Path(output_arg).resolve()
prefs = home / '.melox/prefs_melox_app_settings.properties'
if mode == 'controls':
    assert not prefs.exists(), 'Use a fresh isolated home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                     f'general_desktop_layout={str(layout == "desktop").lower()}\n', encoding='utf-8')
    if fixtures is not None:
        # Seed the exact published EN pack into this test-only home so physical
        # confirmation and removal exercise real ownership without a download.
        raw = (fixtures / 'android-models.json').read_bytes()
        assert hashlib.sha256(raw).hexdigest() == '4b7b54a6f436b4f74ac6028f3876477a2d7dffe13fba557f9649577354a0f0ed'
        index = json.loads(raw)
        pack = index['languages']['en']
        selected = [spec for spec in pack['files'] if spec['role'] != 'audit-only']
        model_dir = home / '.melox/autosync/models/en'
        model_dir.mkdir(parents=True)
        for spec in selected:
            source = fixtures / spec['name']
            digest = hashlib.sha256()
            with source.open('rb') as stream:
                for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                    digest.update(chunk)
            assert source.stat().st_size == spec['bytes'] and digest.hexdigest() == spec['sha256']
            shutil.copyfile(source, model_dir / spec['name'])
        manifest = dict(pack, release_tag=index['release_tag'], installed_variant='int8',
                        installed_model=next(spec['name'] for spec in selected if spec['role'] == 'model'),
                        installed_files=[{key: spec[key] for key in ('name', 'bytes', 'sha256')} for spec in selected])
        (model_dir / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False), encoding='utf-8')
        (model_dir / 'personal-notes.txt').write_text('keep fixture', encoding='utf-8')
else:
    assert prefs.exists() and 'autosync_word_lyrics_enabled=true' in prefs.read_text(encoding='iso-8859-1')
output.mkdir(parents=True, exist_ok=True)

steps = []
def add(name, action, *checks):
    steps.append((name, action, checks))

if mode == 'controls':
    add('cloud-consent', 'tap@remote-reject', 'cloud-enabled:false')
if layout == 'desktop':
    add('developer', 'tap@desktop-settings-tab:Developer')
    add('experimental', 'tap@desktop-settings-subtab:Experimental', 'tag:settings-page:Experimental')
else:
    add('reveal-experimental', 'reveal@settings-open-experimental')
    add('experimental', 'tap@settings-open-experimental', 'tag:settings-experimental')
add('reveal-control', 'reveal@autosync-enabled')
if mode == 'controls':
    add('initially-off', 'wait@100', 'unchecked:autosync-enabled', 'runtime:autoSyncWordLyricsEnabled:false')
    add('enable', 'tap@autosync-enabled', 'checked:autosync-enabled', 'pref-bool:autosync_word_lyrics_enabled:true',
        'runtime:autoSyncWordLyricsEnabled:true', 'tag:autosync-notice')
    add('chinese-model', 'reveal@autosync-model:zh', 'tag:autosync-model:zh')
    add('english-model', 'reveal@autosync-model:en', 'tag:autosync-model:en')
    if fixtures is not None:
        add('show-delete', 'reveal@autosync-delete:en', 'tag:autosync-delete:en')
        add('confirm-delete', 'tap@autosync-delete:en', 'tag:autosync-confirm')
        add('cancel-delete', 'tap@autosync-confirm-cancel', 'absent:autosync-confirm', 'tag:autosync-delete:en')
        add('confirm-again', 'tap@autosync-delete:en', 'tag:autosync-confirm')
        add('accept-delete', 'tap@autosync-confirm-accept')
        add('deleted', 'wait@2000', 'absent:autosync-delete:en', 'tag:autosync-download:en')
    add('japanese-model', 'reveal@autosync-model:ja', 'tag:autosync-model:ja')
    add('find-control', 'reveal@autosync-enabled')
    add('disable', 'tap@autosync-enabled', 'unchecked:autosync-enabled', 'pref-bool:autosync_word_lyrics_enabled:false',
        'absent:autosync-notice')
    add('persist-enabled', 'tap@autosync-enabled', 'checked:autosync-enabled', 'pref-bool:autosync_word_lyrics_enabled:true')
else:
    add('reloaded', 'wait@200', 'checked:autosync-enabled', 'pref-bool:autosync_word_lyrics_enabled:true',
        'runtime:autoSyncWordLyricsEnabled:true', 'tag:autosync-notice')
    add('disable', 'tap@autosync-enabled', 'unchecked:autosync-enabled', 'pref-bool:autosync_word_lyrics_enabled:false',
        'runtime:autoSyncWordLyricsEnabled:false', 'absent:autosync-notice')

(output / 'steps.txt').write_text(''.join(f'{name}:{action}\n' for name, action, _ in steps), encoding='utf-8')
(output / 'expect.txt').write_text(''.join(f'{name}={",".join(checks)}\n' for name, _, checks in steps if checks), encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '1100' if layout == 'desktop' else '430',
        '--height', '850', '--output', str(output), '--wait-ms', '250', '--startup-wait-ms', '1200',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    raise SystemExit(result.returncode)
capture = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assertions = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert assertions and all(a['passed'] for a in assertions)
assert ('autosync_word_lyrics_enabled=true' in prefs.read_text(encoding='iso-8859-1')) == (mode == 'controls')
if fixtures is not None:
    assert (model_dir / 'personal-notes.txt').read_text(encoding='utf-8') == 'keep fixture'
    assert not (model_dir / 'manifest.json').exists()
    assert all(not (model_dir / spec['name']).exists() for spec in selected)
print(f'PASS AutoSync {layout}/{mode}: {len(capture["scenes"])} scenes / {len(assertions)} assertions')
