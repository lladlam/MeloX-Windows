#!/usr/bin/env python3
"""Production action-sheet pointer replay with generated real PCM and UTF-8 parameters.

Usage: capture_song_actions.py HOME OUTPUT FIXTURE_DIRECTORY [reload]
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output, fixtures = map(lambda p: Path(p).resolve(), sys.argv[1:4])
reload = len(sys.argv) > 4 and sys.argv[4] == 'reload'
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
if not gradle:
    raise SystemExit('Set MELOX_GRADLE')
if not reload:
    if home.exists():
        raise SystemExit('Use a new isolated home for the controls run')
    (home / '.melox').mkdir(parents=True)
    (home / '.melox/prefs_melox_app_settings.properties').write_text('onboarding_completed=true\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
steps, checks = [], []

def add(name, action, *assertions):
    steps.append(f'{name}:{action}')
    if assertions:
        checks.append(name + '=' + ','.join(assertions))

if not reload:
    add('consent', 'tap@remote-reject', 'cloud-choice:true')
add('player', 'tap@mini-player-expand', 'tag:now-playing')
add('pause', 'tap@player-play-pause', 'playing:false')
add('actions', 'tap@player-actions', 'tag:song-actions')
add('favorite-reveal', 'reveal@song-action-' + ('取消喜爱' if reload else '喜爱'))
add('favorite-read', 'wait@200', 'state:song-action-' + ('取消喜爱:liked' if reload else '喜爱:unliked'))
if not reload:
    add('favorite-write', 'tap@song-action-喜爱', 'state:song-action-取消喜爱:liked')
    add('download-unavailable', 'wait@100', 'disabled:song-action-下载歌曲')
    add('playlist-reveal', 'reveal@song-action-添加到歌单')
    add('playlist', 'tap@song-action-添加到歌单', 'state:song-actions:AddToPlaylist-idle')
    add('create', 'tap@song-action-新建歌单', 'state:song-actions:CreatePlaylist-idle')
    add('name-focus', 'tap@song-new-playlist-name')
    add('name', 'type@Replay generated list')
    add('create-and-add', 'tap@song-action-创建并添加歌曲', 'absent:song-actions', 'local-playlist-size:Replay generated list:1')
    add('reopen-playlist', 'tap@player-actions', 'tag:song-actions')
    add('sleep-reveal', 'reveal@song-action-定时关闭')
    add('sleep', 'tap@song-action-定时关闭', 'state:song-actions:Sleep-idle')
    add('set-sleep', 'tap@song-action-15 分钟后', 'absent:song-actions', 'sleep-timer:true')
    add('reopen', 'tap@player-actions', 'tag:song-actions')
    add('sleep-again', 'tap@song-action-定时关闭', 'state:song-actions:Sleep-idle')
    add('sleep-back', 'escape', 'state:song-actions:Main-idle', 'sleep-timer:true')
else:
    add('playlist-reveal', 'reveal@song-action-添加到歌单')
    add('playlist', 'tap@song-action-添加到歌单', 'tag:song-playlist-Replay generated list', 'local-playlist-size:Replay generated list:1')
    add('playlist-back', 'escape', 'state:song-actions:Main-idle')
    add('sleep-reveal', 'reveal@song-action-定时关闭')
    add('sleep', 'tap@song-action-定时关闭', 'state:song-actions:Sleep-idle', 'sleep-timer:true')
    add('cancel-sleep', 'tap@song-action-取消定时', 'absent:song-actions', 'sleep-timer:false')
    add('reopen', 'tap@player-actions', 'tag:song-actions')
add('close', 'escape', 'absent:song-actions')
add('collapse', 'escape', 'tag:settings-root')
add('listen-reveal', 'reveal@settings-open-listentogether')
add('listen', 'tap@settings-open-listentogether', 'enabled:listen-together-start')
add('listen-create-needs-account', 'tap@listen-together-start', 'text:请先登录网易云音乐', 'tag:listen-together-message')
add('listen-invalid-invitation', 'tap@listen-together-join', 'tag:listen-together-message')
add('listen-back', 'escape', 'tag:settings-root')
add('recognition-reveal', 'reveal@settings-open-recognition')
add('recognition', 'tap@settings-open-recognition', 'enabled:recognition-start', 'tag:recognition-status')
if reload:
    add('recognition-persisted', 'wait@100', 'pref-string:recognition_duration:3')
else:
    add('recognition-duration', 'tap@recognition-duration-3', 'pref-string:recognition_duration:3')
add('recognition-back', 'escape', 'tag:settings-root')
add('general-reveal', 'reveal@settings-open-general')
add('general', 'tap@settings-open-general', 'tag:settings-general')
add('font-reveal', 'reveal@setting-system_font')
if reload:
    add('font-persisted', 'wait@100', 'pref-bool:system_font:true', 'ui-font:system')
    add('font-reset', 'tap@setting-system_font', 'pref-bool:system_font:false', 'ui-font:pinned')
else:
    add('font-enable', 'tap@setting-system_font', 'pref-bool:system_font:true', 'ui-font:system')
add('general-back', 'escape', 'tag:settings-root')
steps_file, expect_file = output / 'steps.txt', output / 'expect.txt'
steps_file.write_text('\n'.join(steps) + '\n', encoding='utf-8')
expect_file.write_text('\n'.join(checks) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark',
        '--width', '427', '--height', '924', '--output', str(output), '--wait-ms', '300',
        '--startup-wait-ms', '1200', '--local-wavs', str(fixtures / 'generated-1000hz.wav'),
        '--scan-local-dir', str(fixtures), '--capture-pcm', '--steps-file', str(steps_file), '--expect-file', str(expect_file)]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1024m -XX:+UseSerialGC',
           f'-PcaptureHome={home}', f'-PcaptureArgsFile={argfile}']
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print('\n'.join((output / 'gradle.log').read_text(encoding='utf-8', errors='replace').splitlines()[-70:]), file=sys.stderr)
print('song-actions', 'reload' if reload else 'controls', result.returncode)
sys.exit(result.returncode)
