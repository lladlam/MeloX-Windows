#!/usr/bin/env python3
"""Replay real public collection pages; no mock response, account or music data.

Album starts with a persisted Desktop preference (including cold pointer input).
Playlist is opened through the real Home recommendation shelf after the physical
layout switch. Capture/semantics only locate input and measure production output.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('page', choices=['album', 'playlist'])
parser.add_argument('home', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--theme', choices=['Light', 'Dark'], default='Light')
parser.add_argument('--locale', default='en-US')
parser.add_argument('--album-id', default='34720827')
parser.add_argument('--expand-description', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
home, output = args.home.resolve(), args.output.resolve()
assert not (home / '.melox').exists(), 'Use a fresh isolated home for each replay'
(home / '.melox').mkdir(parents=True)
output.mkdir(parents=True, exist_ok=True)
(home / '.melox/prefs_melox_app_settings.properties').write_text(
    'onboarding_completed=true\ngeneral_desktop_layout=' + ('true' if args.page == 'album' else 'false') + '\n', encoding='utf-8')
steps, checks = [], {}


def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    checks[name] = list(assertions)


add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
if args.page == 'playlist':
    add('reveal-general', 'reveal@settings-open-general')
    add('general', 'tap@settings-open-general', ['tag:settings-general'])
    add('reveal-switch', 'reveal@setting-general_desktop_layout')
    add('enable-desktop', 'tap@setting-general_desktop_layout', ['tag:desktop-sidebar', 'runtime:desktopLayoutEnabled:true'])
    add('home', 'tap@desktop-nav:Home', ['tag:desktop-home'])
    add('show-playlists', 'scroll@1000,600,5')
    add('open-real-playlist', 'tap-first@desktop-home-playlist:', ['tag:desktop-playlist-detail', 'tag:desktop-collection-header'])
else:
    add('loaded-album', 'wait@1500', ['tag:desktop-album-detail', 'tag:desktop-collection-header', 'tag:desktop-sidebar'])
if args.expand_description:
    add('expand-description', 'tap@desktop-collection-description-toggle', ['state:desktop-collection-description:expanded'])
    add('reveal-collapse', 'reveal@desktop-collection-description-toggle')
    add('collapse-description', 'tap@desktop-collection-description-toggle', ['state:desktop-collection-description:collapsed'])
    add('return-header', 'reveal@desktop-collection-artwork')
if args.page == 'playlist':
    add('open-search', 'tap@desktop-collection-search-toggle', ['tag:desktop-collection-search'])
    add('focus-search', 'tap@desktop-collection-search')
    add('type-search', 'type@MeloX-no-matching-title-2026')
    add('close-search', 'tap@desktop-collection-search-toggle', ['absent:desktop-collection-search'])
add('more', 'tap@desktop-collection-actions', ['tag:desktop-collection-download'])
add('dismiss-menu', '1200,780', ['absent:desktop-collection-download'])
add('back', 'tap@desktop-collection-back', ['absent:desktop-' + args.page + '-detail', 'tag:desktop-sidebar'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(name + '=' + ','.join(values) for name, values in checks.items() if values) + '\n', encoding='utf-8')
parameters = ['--locale', args.locale, '--source', 'Netease', '--start-tab', 'Settings', '--theme', args.theme,
              '--width', '1280', '--height', '800', '--output', str(output), '--wait-ms', '650',
              '--startup-wait-ms', '1800', '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
if args.page == 'album':
    parameters += ['--album-id', args.album_id]
(output / 'arguments.txt').write_text('\n'.join(parameters) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--console=plain',
                             '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                            cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    raise SystemExit(result.returncode)
subprocess.run([sys.executable, str(repo / 'tools/verify_desktop_collection_capture.py'), str(output), '--page', args.page], check=True)
