#!/usr/bin/env python3
"""Real pointer navigation through Desktop library and utility windows with a fresh signed-out home."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output = (Path(value).resolve() for value in sys.argv[1:3])
theme = sys.argv[3] if len(sys.argv) > 3 else 'Dark'
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
steps, expected = [], []

def add(name, action, *checks):
    steps.append(f'{name}:{action}')
    if checks:
        expected.append(name + '=' + ','.join(checks))

add('consent', 'tap@remote-reject', 'cloud-enabled:false')
for page in ['Songs', 'Playlists', 'Albums', 'Artists', 'Podcasts', 'History', 'Downloads', 'Cloud']:
    tag = {'Downloads': 'desktop-downloads', 'Cloud': 'desktop-cloud'}.get(page, 'desktop-library:' + page)
    add(page.lower(), 'tap@desktop-nav:' + page, 'tag:' + tag, 'absent:library-segmented-picker')
add('explore', 'tap@desktop-nav:Explore', 'tag:desktop-explore')
# The tab crossfade is 280ms; assert the retired Library page is gone only
# after that transition actually finishes.
add('explore-settled', 'wait@1200', 'tag:desktop-explore', 'absent:desktop-library:Songs')
add('search', 'tap@desktop-nav:Search', 'tag:desktop-search', 'absent:search-state')
add('search-focus', 'tap@search-query', 'tag:desktop-search')
add('search-type', 'type@love', 'tag:desktop-search', 'tag:search-kind-songs')
add('search-settle', 'wait@1600', 'tag:desktop-search', 'absent:search-state')
add('settings', 'tap@desktop-settings', 'tag:desktop-settings-panel')
add('about', 'tap@desktop-settings-about', 'tag:desktop-about', 'tag:desktop-about-logo')
add('licenses', 'tap@desktop-about-licenses', 'tag:desktop-project-licenses')
for index in range(6):
    add('document-' + str(index), 'tap@desktop-legal-document:' + str(index), 'tag:desktop-legal-back')
    add('document-back-' + str(index), 'tap@desktop-legal-back', 'tag:desktop-project-licenses')
add('close-licenses', 'tap@desktop-dialog:ui.common.done', 'absent:desktop-project-licenses')
add('restore-question', 'tap@settings-reset', 'tag:desktop-dialog:ui.common.cancel')
add('cancel-restore', 'tap@desktop-dialog:ui.common.cancel', 'runtime:desktopLayoutEnabled:true')
add('services', 'tap@settings-open-services', 'tag:desktop-provider-services', 'tag:desktop-service-scope:netease')
add('services-close', 'escape', 'absent:desktop-provider-services', 'tag:desktop-settings-panel')
add('settings-features', 'tap@desktop-settings-tab:Features', 'tag:desktop-settings-panel')
add('settings-messages-tab', 'tap@desktop-settings-subtab:Messages', 'tag:settings-page:Messages')
add('settings-messages-open', 'tap@settings-open-messages', 'tag:desktop-messages-login')
add('settings-messages-close', 'escape', 'absent:desktop-messages-login', 'tag:desktop-settings-panel')
add('settings-general', 'tap@desktop-settings-tab:General', 'tag:settings-general')
add('dismiss-settings', 'tap@desktop-dialog:ui.common.ok', 'tag:desktop-home')
add('account-sheet', 'tap@desktop-account', 'tag:desktop-account-sheet', 'tag:desktop-account-login')
add('login-sheet', 'tap@desktop-account-login', 'tag:desktop-login', 'tag:desktop-login-phone')
add('login-client', 'tap@desktop-login-method:ClientCookie', 'tag:desktop-login-import')
add('login-phone', 'tap@desktop-login-method:Phone', 'tag:desktop-login-send')
add('login-cancel', 'tap@desktop-login-cancel', 'absent:desktop-login')
add('login-closed', 'wait@300', 'absent:desktop-login', 'absent:desktop-account-sheet')
steps_file = output / 'steps.txt'
expect_file = output / 'expect.txt'
steps_file.write_text('\n'.join(steps) + '\n', encoding='utf-8')
expect_file.write_text('\n'.join(expected) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Netease', '--start-tab', 'Home', '--theme', theme,
        '--width', '1280', '--height', '800', '--output', str(output), '--wait-ms', '220',
        '--startup-wait-ms', '1200', '--steps-file', str(steps_file), '--expect-file', str(expect_file)]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-14000:])
raise SystemExit(result.returncode)
