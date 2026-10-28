#!/usr/bin/env python3
"""Physical production Home replay, with public NetEase content and an isolated home."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('home', type=Path)
parser.add_argument('output', type=Path)
parser.add_argument('--theme', choices=['Light', 'Dark'], default='Dark')
parser.add_argument('--locale', default='zh-CN')
parser.add_argument('--width', type=int, default=1960)
parser.add_argument('--height', type=int, default=1184)
parser.add_argument('--density', type=float, default=2)
parser.add_argument('--reduce-motion', action='store_true')
parser.add_argument('--touch', action='store_true')
parser.add_argument('--promoted-drag', action='store_true')
parser.add_argument('--account', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
home, output = args.home.resolve(), args.output.resolve()
assert not (home / '.melox').exists(), 'Use a fresh isolated home'
(home / '.melox').mkdir(parents=True)
output.mkdir(parents=True, exist_ok=True)
(home / '.melox/prefs_melox_app_settings.properties').write_text(
    'onboarding_completed=true\ngeneral_desktop_layout=true\n' +
    ('reduce_motion=true\n' if args.reduce_motion else ''), encoding='utf-8')
steps, checks = [], {}


def add(name, action, assertions=()):
    steps.append(f'{name}:{action}')
    checks[name] = list(assertions)


add('consent', 'tap@remote-reject', ['cloud-enabled:false'])
add('neutral-pointer', 'hover@desktop-nav:Home')
add('home', 'wait@1500', ['tag:desktop-home', 'tag:desktop-sidebar', 'tag:desktop-home-action:daily_songs',
    'tag:desktop-home-action:heart_mode', 'tag:desktop-home-action:private_roaming', 'runtime:desktopLayoutEnabled:true'])
if args.reduce_motion:
    checks['home'].append('runtime:reduceMotion:true')
if args.account:
    add('account', 'tap@desktop-account', ['tag:desktop-account-sheet', 'tag:desktop-account-signed-out', 'tag:desktop-account-login'])
    add('account-done', 'tap@desktop-account-done', ['absent:desktop-account-sheet', 'tag:desktop-home'])
    add('account-again', 'tap@desktop-account', ['tag:desktop-account-sheet'])
    add('account-escape', 'escape', ['absent:desktop-account-sheet'])
add('shelf-hover', 'hover@desktop-home-shelf:actions', ['tag:desktop-home-page:actions:next'])
add('next-actions', 'tap@desktop-home-page:actions:next', ['tag:desktop-home-action:similar_songs', 'tag:desktop-home-page:actions:previous'])
add('previous-actions', 'tap@desktop-home-page:actions:previous', ['tag:desktop-home-action:daily_songs'])
if args.promoted_drag:
    add('mouse-horizontal', 'mouse-drag@desktop-home-shelf:actions,-240,0', ['tag:desktop-home-page:actions:previous'])
    add('mouse-page-back', 'tap@desktop-home-page:actions:previous', ['tag:desktop-home-action:daily_songs'])
    add('mouse-vertical', 'mouse-drag@desktop-home-shelf:actions,0,-130', ['tag:desktop-home'])
    add('mouse-vertical-back', 'mouse-drag@desktop-home-scroll,0,130', ['tag:desktop-home'])
if args.touch:
    add('touch-horizontal', 'touch-drag@desktop-home-shelf:actions,-320,0', ['tag:desktop-home-page:actions:previous'])
    add('touch-page-back', 'touch-tap@desktop-home-page:actions:previous', ['tag:desktop-home-action:daily_songs'])
    add('touch-vertical', 'touch-drag@desktop-home-shelf:actions,0,-160', ['tag:desktop-home'])
add('reveal-hero', 'reveal@desktop-home-shelf:hero', ['tag-prefix:desktop-home-playlist:'])
add('real-playlist', ('touch-tap-first@' if args.touch else 'tap-first@') + 'desktop-home-playlist:', ['tag:desktop-playlist-detail', 'tag:desktop-collection-header'])
if args.touch:
    add('short-edge-cancel', 'touch-edge@desktop-playlist-detail,30,0', ['tag:desktop-playlist-detail'])
    add('vertical-edge-cancel', 'touch-edge@desktop-playlist-detail,0,-100', ['tag:desktop-playlist-detail'])
    add('edge-return', 'touch-edge@desktop-playlist-detail,150,0', ['tag:desktop-home', 'absent:desktop-playlist-detail'])
    add('playlist-again', 'touch-tap-first@desktop-home-playlist:', ['tag:desktop-playlist-detail', 'tag:desktop-collection-header'])
add('return-home', 'tap@desktop-collection-back', ['tag:desktop-home', 'absent:desktop-playlist-detail', 'tag-prefix:desktop-home-playlist:'])
add('tailored', 'reveal@desktop-home-shelf:tailored', ['tag-prefix:desktop-home-media-playlist:'])
add('album-shelf', 'reveal@desktop-home-shelf:albums', ['tag-prefix:desktop-home-album:'])
add('real-album', 'tap-first@desktop-home-album:', ['tag:desktop-album-detail', 'tag:desktop-collection-header'])
add('return-from-album', 'tap@desktop-collection-back', ['tag:desktop-home', 'absent:desktop-album-detail', 'tag-prefix:desktop-home-album:'])
add('settings', 'tap@desktop-settings', ['tag:settings-root'])
add('general', 'tap@settings-open-general', ['tag:settings-general'])
add('theme', 'reveal@theme_mode')
add('theme-choice', 'tap@theme_mode')
opposite = 'Light' if args.theme == 'Dark' else 'Dark'
suffix = {'zh-CN': '-zh-rCN', 'zh-TW': '-zh-rTW', 'ja-JP': '-ja'}.get(args.locale, '')
strings = {}
for name in dict.fromkeys(['pinned-settings-pages.properties', f'pinned-settings-pages{suffix}.properties']):
    strings.update(dict(line.split('=', 1) for line in (repo / 'desktop/src/main/resources/parity' / name).read_text(encoding='utf-8').splitlines()
        if line and not line.startswith('#')))
add('switch-theme', 'tap@settings-option:' + strings['settings_general_theme_' + opposite.lower()], [f'runtime:themeMode:{opposite}'])
add('home-new-theme', 'tap@desktop-nav:Home', ['tag:desktop-home', 'tag:desktop-sidebar'])
if args.account:
    add('account-new-theme', 'tap@desktop-account', ['tag:desktop-account-sheet', 'tag:desktop-account-signed-out'])
    add('account-new-theme-done', 'tap@desktop-account-done', ['absent:desktop-account-sheet', 'tag:desktop-home'])
add('return-top', 'reveal@desktop-home-action:daily_songs', ['tag:desktop-home-action:daily_songs'])
add('top-new-theme', f'scroll@{args.width - 20},{int(args.height * .25)},-40', ['tag:desktop-home-action:daily_songs'])
add('neutral-new-theme', 'hover@desktop-nav:Home', ['tag:desktop-home'])
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(n + '=' + ','.join(v) for n, v in checks.items() if v) + '\n', encoding='utf-8')
parameters = ['--locale', args.locale, '--source', 'Netease', '--start-tab', 'Home', '--theme', args.theme,
    '--width', str(args.width), '--height', str(args.height), '--density', str(args.density), '--output', str(output),
    # Public playlist details may require several real HTTP requests. Keep the
    # header assertion, but give them the same budget as the collection replay.
    '--wait-ms', '650', '--startup-wait-ms', '1800', '--assert-timeout-ms', '30000',
    '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(parameters) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--console=plain',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-75:]))
    sys.exit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert len(data['scenes']) == len(steps) + 1
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
assert 'Exception in thread' not in (output / 'gradle.log').read_text(encoding='utf-8', errors='replace')
if args.promoted_drag:
    def node_at(name, tag):
        entry = next(s for s in data['scenes'] if s['file'].split('-', 1)[1] == name + '.png')
        nodes = json.loads((output / entry['file']).with_suffix('.semantics.json').read_text(encoding='utf-8'))
        return next(n for n in nodes if n.get('tag') == tag)

    before = node_at('previous-actions', 'desktop-home-scroll')['vertical_scroll']
    after = node_at('mouse-vertical', 'desktop-home-scroll')['vertical_scroll']
    assert after - before > 60, (before, after)
    assert node_at('mouse-horizontal', 'desktop-home-shelf:actions')['state'] != node_at('previous-actions', 'desktop-home-shelf:actions')['state']
print(f'PASS Home {args.theme}: {len(data["scenes"])} scenes / {sum(len(s.get("assertions", [])) for s in data["scenes"])} assertions')
