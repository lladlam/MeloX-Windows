#!/usr/bin/env python3
"""Replay real settings navigation, localized controls and independent preference reload.

Usage: capture_settings_pages.py routes|controls|reload HOME OUTPUT [LOCALE THEME WIDTH HEIGHT]
Use fresh isolated onboarded homes. No semantic actions or direct preference setters.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
mode, home, output = sys.argv[1:4]
assert mode in ('routes', 'controls', 'reload')
locale, theme, width, height = (sys.argv[4:] or ['en-US', 'Dark', '1100', '780'])
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
suffix = {'zh-CN': '-zh-rCN', 'zh-TW': '-zh-rTW', 'ja-JP': '-ja'}.get(locale, '')
resources = repo / 'desktop/src/main/resources/parity'


def properties(path):
    return dict(line.split('=', 1) for line in path.read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))


strings = properties(resources / 'pinned-settings-pages.properties')
strings.update(properties(resources / f'pinned-settings-pages{suffix}.properties'))
steps, expected, changed = [], {}, {}


def add(name, action, checks=()):
    steps.append(f'{name}:{action}')
    if checks:
        expected[name] = list(checks)


def reveal(tag):
    add('reveal-' + str(len(steps)), 'reveal@' + tag)


def open_route(route):
    tag = 'settings-open-' + route.lower()
    reveal(tag)
    add('open-' + route.lower(), 'tap@' + tag, ['tag:settings-' + route.lower()])


def choose(tag, label, kind, value, runtime=None):
    reveal(tag)
    add('menu-' + str(len(steps)), 'tap@' + tag)
    changed[tag] = [kind, value]
    checks = [f'pref-{kind}:{tag}:{value}']
    if runtime:
        checks += [f'runtime:{runtime}:{value}']
    add('choose-' + str(len(steps)), 'tap@settings-option:' + label, checks)


def toggle(key, value, runtime=None):
    tag = 'setting-' + key
    reveal(tag)
    changed[key] = ['bool', value]
    checks = [f'pref-bool:{key}:{value}', ('checked:' if value == 'true' else 'unchecked:') + tag]
    if runtime:
        checks += [f'runtime:{runtime}:{value}']
    add('toggle-' + str(len(steps)), 'tap@' + tag, checks)


if mode != 'reload':
    add('remote-reject', 'tap@remote-reject', ['cloud-choice:true', 'cloud-enabled:false'])

if mode == 'routes':
    audit = json.loads((repo / 'parity/3f877ea/evidence/settings-pages-source-audit.json').read_text(encoding='utf-8'))
    aliases = {'PlayerAppearance': 'Appearance', 'ContentFeatures': 'Features'}
    for section in audit['sections']:
        for item in section['items']:
            route = aliases.get(item['route'], item['route'])
            if route == 'Messages':
                reveal('settings-open-messages')
                add('open-messages', 'tap@settings-open-messages')
            else:
                open_route(route)
                checks = ['text:' + strings['settings_route_' + {
                    'TabLayout': 'tabs', 'SkylineLyrics': 'skyline', 'SystemPlayback': 'system_playback',
                    'FloatingLyrics': 'floating', 'ListenTogether': 'listen_together', 'RemoteConfig': 'remote_config',
                }.get(route, route.lower())]]
                if route == 'General':
                    checks += ['disabled:setting-general_clipboard_links', 'disabled:setting-general_haptic_feedback', 'disabled:setting-system_font']
                if route == 'Recognition':
                    checks += ['disabled:recognition-start']
                if route == 'FloatingLyrics':
                    checks += ['disabled:setting-floating_lyrics_enabled']
                add('inspect-' + route.lower(), 'wait@100', checks)
            add('back-' + route.lower(), 'escape', ['tag:settings-root'])
    # Search via physical keyboard, including the localized no-match state.
    add('root-top', f'scroll@{int(width) // 2},{int(height) // 2},-10000')
    reveal('settings-search')
    add('focus-search', 'tap@settings-search')
    add('no-match', 'type@no-matching-setting-984723', ['tag:settings-search-empty'])
elif mode == 'controls':
    open_route('General')
    choose('theme_mode', strings['settings_general_theme_light'], 'string', 'Light', 'themeMode')
    choose('general_swipe_full_action', strings['settings_general_add_queue'], 'string', 'AddToQueue', 'swipeFullAction')
    toggle('general_remember_tab', 'false', 'rememberLastTab')
    add('back-general', 'escape')
    open_route('Appearance')
    for key, value, runtime in [('player_flowing_speed', 2, 'flowingLightSpeed'), ('player_flowing_saturation', 2, 'flowingLightSaturation'),
                                ('player_flowing_brightness', 1.6, 'flowingLightBrightness')]:
        reveal(key)
        changed[key] = ['float', value]
        add('set-' + key, f'slider@{key},1', [f'pref-float:{key}:{value}', f'runtime:{runtime}:{value}'])
    choose('player_background_mode', strings['settings_bg_blurred_artwork'], 'string', 'BlurredArtwork', 'playerBackgroundMode')
    add('conditional-background', 'wait@100', ['absent:player_flowing_speed'])
    toggle('library_playlist_search_hidden', 'true', 'playlistSearchBarHidden')
    toggle('library_playlist_show_artwork', 'true', 'playlistShowArtwork')
    add('back-appearance', 'escape')
    open_route('Content')
    choose('music_area', strings['settings_content_area_japan'], 'string', '日本', 'musicArea')
    toggle('content_playlist_play_count', 'false', 'showPlaylistPlayCount')
    add('back-content', 'escape')
    open_route('Playback')
    reveal('equalizer-enabled')
    add('eq-on', 'tap@equalizer-enabled', ['eq-enabled:true'])
    reveal('equalizer-preset-custom')
    add('eq-custom', 'tap@equalizer-preset-custom', ['eq-preset:Custom'])
    reveal('equalizer-band-9')
    add('eq-band-9', 'wait@200', ['tag:equalizer-band-9'])
    reveal('setting-auto_mix')
    add('automix-on', 'tap@setting-auto_mix', ['tag:automix_mode'])
    reveal('automix_mode'); add('automix-menu', 'tap@automix_mode')
    add('automix-fixed', 'tap@settings-option:' + strings['settings_automix_fixed'], ['absent:automix_transition_bars', 'automix-mode:Fixed'])
    reveal('automix_fixed_duration_ms'); add('duration-menu', 'tap@automix_fixed_duration_ms')
    label = strings['settings_seconds'].replace('%1$d', '12').replace('%d', '12')
    add('duration-12', 'tap@settings-option:' + label, ['automix-duration-ms:12000'])
    add('back-playback', 'escape')
    open_route('Storage')
    toggle('download_lyrics', 'false')
    add('download-consumer', 'wait@100', ['download-lyrics:false'])
    reveal('storage-clear-all'); add('confirm-clear', 'tap@storage-clear-all', ['tag:settings-confirmation'])
    add('cancel-clear', 'tap@settings-confirm-cancel', ['absent:settings-confirmation'])
    add('back-storage', 'escape')
    open_route('TabLayout')
    reveal('layout-tab_order')
    add('tab-order-before', 'wait@200', ['state:layout-tab_order:Home>Explore>Library>Podcasts>Downloads>Cloud>Settings'])
    reveal('layout-tab_order-Podcasts-up')
    add('tab-order-up', 'tap@layout-tab_order-Podcasts-up',
        ['state:layout-tab_order:Home>Explore>Podcasts>Library>Downloads>Cloud>Settings'])
    add('back-tabs', 'escape')
    add('reload', 'reload-settings', ['runtime:flowingLightSpeed:2.0', 'runtime:playlistSearchBarHidden:true', 'runtime:musicArea:日本'])
else:
    expected['00-settings'] = ['runtime:flowingLightSpeed:2.0', 'runtime:flowingLightSaturation:2.0', 'runtime:flowingLightBrightness:1.6',
        'runtime:playlistSearchBarHidden:true', 'runtime:playlistShowArtwork:true', 'runtime:musicArea:日本', 'eq-enabled:true', 'eq-preset:Custom',
        'pref-bool:download_lyrics:false', 'download-lyrics:false', 'automix-mode:Fixed', 'automix-duration-ms:12000']
    open_route('Appearance')
    # Fresh reload starts at the top of the page, where the background group is laid out.
    add('persisted-background', 'wait@200', ['absent:player_flowing_speed'])
    reveal('setting-library_playlist_show_artwork')
    add('persisted-library', 'wait@200', ['checked:setting-library_playlist_show_artwork'])

# Non-ASCII pinned labels never cross the process boundary as argv: Windows
# CreateProcess encodes the child command line with sun.jnu.encoding.
steps_file = output / 'steps.txt'
expect_file = output / 'expect.txt'
steps_file.write_text('\n'.join(steps) + '\n', encoding='utf-8')
expect_file.write_text('\n'.join(name + '=' + ','.join(checks) for name, checks in expected.items()) + '\n', encoding='utf-8')
args = ['--locale', locale, '--source', 'Local', '--start-tab', 'Settings', '--theme', theme, '--width', width, '--height', height,
    '--output', str(output), '--wait-ms', '180', '--startup-wait-ms', '1200',
    '--steps-file', str(steps_file), '--expect-file', str(expect_file)]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
(output / 'control-inputs.json').write_text(json.dumps(changed, indent=2) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
    '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
    '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode != 0:
    # CI only surfaces this step's stdout, so mirror the harness failure here.
    tail = (output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()
    print('\n'.join(tail[-60:]), file=sys.stderr)
print(mode, locale, theme, width, height, result.returncode)
sys.exit(result.returncode)
