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
parser.add_argument('--drag-scroll', action='store_true')
parser.add_argument('--song-menu', action='store_true')
parser.add_argument('--artist-route', action='store_true')
parser.add_argument('--width', type=int, default=1280)
parser.add_argument('--height', type=int, default=800)
parser.add_argument('--density', type=float, default=1)
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
    add('show-playlists', 'reveal@desktop-home-shelf:hero')
    add('open-real-playlist', 'tap-first@desktop-home-playlist:', ['tag:desktop-playlist-detail', 'tag:desktop-collection-header'])
else:
    add('loaded-album', 'wait@1500', ['tag:desktop-album-detail', 'tag:desktop-collection-header', 'tag:desktop-sidebar'])
if args.expand_description:
    add('expand-description', 'tap@desktop-collection-description-toggle', ['state:desktop-collection-description:expanded'])
    add('reveal-collapse', 'reveal@desktop-collection-description-toggle')
    add('collapse-description', 'tap@desktop-collection-description-toggle', ['state:desktop-collection-description:collapsed'])
    add('return-header', 'reveal@desktop-collection-artwork')
if args.drag_scroll:
    add('native-touch-scroll', 'touch-drag@desktop-collection-scroll,0,-240', ['tag:desktop-collection-scroll'])
    add('native-touch-return', 'touch-drag@desktop-collection-scroll,0,300', ['state:desktop-collection-scroll:0-0'])
    add('promoted-touch-scroll', 'mouse-drag@desktop-collection-scroll,0,-240', ['tag:desktop-collection-scroll'])
    add('promoted-touch-return', 'mouse-drag@desktop-collection-scroll,0,300', ['state:desktop-collection-scroll:0-0'])
if args.page == 'playlist':
    add('open-search', 'tap@desktop-collection-search-toggle', ['tag:desktop-collection-search'])
    add('focus-search', 'tap@desktop-collection-search')
    add('type-search', 'type@MeloX-no-matching-title-2026')
    add('close-search', 'tap@desktop-collection-search-toggle', ['absent:desktop-collection-search'])
add('more', 'tap@desktop-collection-actions', ['tag:desktop-collection-download'])
add('dismiss-menu', '1200,780', ['absent:desktop-collection-download'])
if args.song_menu:
    add('open-song-menu', 'tap-first@desktop-collection-more:', ['tag:desktop-song-menu', 'absent:song-actions', 'tag:desktop-song-information'])
    add('playlist-submenu', 'tap@desktop-song-add-to', ['tag:desktop-song-add-menu', 'disabled:desktop-song-create'])
    add('close-submenu', 'escape')
    add('close-song-menu', 'escape', ['absent:desktop-song-menu'])
    add('context-song-menu', 'right-click-first@desktop-collection-track:', ['tag:desktop-song-menu', 'absent:song-actions'])
    add('add-to-queue', 'tap@desktop-song-queue', ['queue-min:1', 'absent:desktop-song-menu'])
    add('song-information-menu', 'tap-first@desktop-collection-more:', ['tag:desktop-song-menu'])
    add('song-information', 'tap@desktop-song-information', ['tag:desktop-song-detail', 'tag:desktop-song-tabs', 'absent:song-actions'])
    add('song-lyrics', 'tap@desktop-song-tab:1', ['tag:desktop-song-tabs'])
    add('song-comments', 'tap@desktop-song-tab:2', ['tag:desktop-song-tabs'])
    add('song-favorite-sign-in-error', 'tap@desktop-collection-favorite', ['tag:desktop-message-dialog', 'absent:song-actions'])
    add('close-song-favorite-error', 'tap@desktop-dialog:ui.common.ok', ['absent:desktop-message-dialog', 'tag:desktop-song-detail'])
    add('close-song-information', 'tap@desktop-collection-back', ['absent:desktop-song-detail', 'tag:desktop-playlist-detail'])
    if args.artist_route:
        add('artist-menu', 'tap-first@desktop-collection-more:', ['tag:desktop-song-menu'])
        add('artist-route', 'tap@desktop-song-artist', ['any-tag:desktop-artist-detail|desktop-search', 'absent:artist-detail'])
        add('artist-search-result', 'tap-first-if-page@desktop-search,desktop-search-result:Artists:',
            ['tag:desktop-artist-detail', 'absent:artist-detail'])
        # The hero appears after the real artist request resolves. Give the
        # network response its own settled scene instead of racing the first tap.
        add('artist-loaded', 'wait@1200', ['tag:desktop-artist-hero'])
        add('reveal-artist-albums', 'touch-drag@desktop-artist-scroll,0,-160')
        # Latest Releases is real content and can be empty for an artist without
        # albums (for example a choir). Album drill-down is replayed by the
        # dedicated album page capture, so this route only asserts the real
        # artist surface and its return stack.
        add('artist-return', 'tap@desktop-artist-back', ['any-tag:desktop-playlist-detail|desktop-search', 'absent:desktop-artist-detail'])
        add('return-home', 'tap@desktop-nav:Home', ['tag:desktop-home'])
        add('return-playlist-shelf', 'reveal@desktop-home-shelf:hero')
        add('return-playlist', 'tap-first@desktop-home-playlist:', ['tag:desktop-playlist-detail'])
add('back', 'tap@desktop-collection-back', ['absent:desktop-' + args.page + '-detail', 'tag:desktop-sidebar'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(name + '=' + ','.join(values) for name, values in checks.items() if values) + '\n', encoding='utf-8')
parameters = ['--locale', args.locale, '--source', 'Netease', '--start-tab', 'Settings', '--theme', args.theme,
              '--width', str(args.width), '--height', str(args.height), '--density', str(args.density), '--output', str(output), '--wait-ms', '650',
               '--startup-wait-ms', '1800', '--assert-timeout-ms', '30000',
               '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
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
