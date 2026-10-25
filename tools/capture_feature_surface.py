#!/usr/bin/env python3
"""Physical input replay of new dual-layout settings and app-language persistence."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

layout, mode, home, output = sys.argv[1:5]
assert layout in ('mobile', 'desktop') and mode in ('controls', 'reload')
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
desktop = layout == 'desktop'
if mode == 'controls':
    prefs = home / '.melox/prefs_melox_app_settings.properties'
    assert not prefs.exists(), 'Use an isolated fresh home'
    prefs.parent.mkdir(parents=True, exist_ok=True)
    prefs.write_text('onboarding_completed=true\ngeneral_split_screen=false\n'
                     f'general_desktop_layout={str(desktop).lower()}\n', encoding='utf-8')
steps, expectations = [], []

def add(name, action, *checks):
    steps.append(f'{name}:{action}')
    if checks:
        expectations.append(name + '=' + ','.join(checks))

def reveal(tag):
    add('reveal-' + str(len(steps)), 'reveal@' + tag)

def route(name, group):
    if desktop:
        add('group-' + name, 'tap@desktop-settings-tab:' + group)
        reveal('desktop-settings-subtab:' + name)
        add('open-' + name, 'tap@desktop-settings-subtab:' + name, 'tag:settings-page:' + name)
    else:
        reveal('settings-open-' + name.lower())
        add('open-' + name, 'tap@settings-open-' + name.lower(), 'tag:settings-' + name.lower())

def back(name):
    if not desktop:
        add('back-' + name, 'escape', 'tag:settings-root')

if mode == 'controls':
    add('consent', 'tap@remote-reject', 'cloud-enabled:false')
    route('General', 'General')
    for tag, label in [('zh-CN', '简体中文'), ('ja-JP', '日本語'), ('en-US', 'English')]:
        reveal('application_language')
        add('language-menu-' + tag, 'tap@application_language')
        add('language-select-' + tag, 'tap@settings-option:' + label, 'pref-string:application_language:' + tag)
        reveal('theme_mode')
        add('translated-settings-' + tag, 'wait@100', 'text:' + {'zh-CN': '主题', 'ja-JP': 'テーマ', 'en-US': 'Theme'}[tag])
    back('General')
    route('Appearance', 'General')
    reveal('player_background_mode')
    add('background-menu', 'tap@player_background_mode')
    add('custom-background-mode', 'tap@settings-option:自定义图片', 'pref-string:player_background_mode:CustomImage', 'tag:custom-background-status')
    reveal('custom-background-import')
    add('custom-background-controls', 'wait@100', 'enabled:custom-background-import', 'text:尚未选择图片，暂用歌曲封面')
    back('Appearance')
    route('Lyrics', 'Playback')
    reveal('lyrics_translation')
    add('translation-off', 'tap@lyrics_translation', 'pref-bool:lyrics_translation:false', 'unchecked:lyrics_translation')
    add('translation-on', 'tap@lyrics_translation', 'pref-bool:lyrics_translation:true', 'checked:lyrics_translation')
    back('Lyrics')
    route('Privacy', 'Developer')
    reveal('privacy-content')
    add('privacy-visible', 'wait@100', 'tag:privacy-content', 'tag:privacy-algorithm')
    back('Privacy')
    route('General', 'General')
    reveal('application_language')
    add('language-retained', 'wait@100', 'text:English', 'pref-string:application_language:en-US')
else:
    expectations.append('00-settings=pref-string:application_language:en-US,pref-bool:lyrics_translation:true')
    route('General', 'General')
    reveal('application_language')
    add('language-reloaded', 'wait@100', 'text:English', 'pref-string:application_language:en-US')

(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(expectations) + '\n', encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', 'Local', '--start-tab', 'Settings', '--width', '1100' if desktop else '430',
        '--height', '850', '--output', str(output), '--wait-ms', '200', '--startup-wait-ms', '1200',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
        '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')], cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(errors='replace').splitlines()[-65:]))
    sys.exit(result.returncode)
capture = json.loads((output / 'capture.json').read_text())
checks = [a for scene in capture['scenes'] for a in scene.get('assertions', [])]
assert checks and all(a['passed'] for a in checks)
print(f'{layout}/{mode}: {len(capture["scenes"])} scenes / {len(checks)} assertions passed')
