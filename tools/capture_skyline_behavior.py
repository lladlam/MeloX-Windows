#!/usr/bin/env python3
"""Select production Skyline controls and observe real provider lyrics/consumed PCM."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, out = (Path(p).resolve() for p in sys.argv[1:3])
out.mkdir(parents=True, exist_ok=True)
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
steps, expected = [], {}


def add(name, action, *checks):
    steps.append(f'{name}:{action}')
    if checks:
        expected[name] = list(checks)


def settings(prefix):
    add(prefix + '-exit', 'tap@player-skyline-exit', 'absent:player-skyline')
    add(prefix + '-collapse', 'tap@player-collapse', 'absent:now-playing')
    add(prefix + '-root', 'reveal@settings-open-skylinelyrics')
    add(prefix + '-open', 'tap@settings-open-skylinelyrics', 'tag:skyline-settings')


def player(prefix, *checks):
    add(prefix + '-back', 'escape', 'tag:settings-root')
    add(prefix + '-player', 'tap@mini-player-expand', 'page:Lyrics', 'playing:false')
    add(prefix, 'tap@player-skyline-open', 'tag:player-skyline', 'tag:skyline-current-line', *checks)


add('remote-reject', 'tap@remote-reject', 'cloud-choice:true', 'cloud-enabled:false')
add('player', 'tap@mini-player-expand', 'page:Artwork', 'playing:true', 'no-playback-error:true')
add('pause', 'tap@player-play-pause', 'playing:false')
add('seek', 'slider@player-progress,0.155', 'position-min:21000')
add('lyrics', 'tap@player-show-lyrics', 'page:Lyrics', 'word-lines-min:1')
add('baseline', 'tap@player-skyline-open', 'tag:player-skyline', 'tag:skyline-song-info',
    'tag:skyline-ambient', 'tag:skyline-current-line', 'tag:skyline-next-line')
settings('changed')
add('info-reveal', 'reveal@lyrics_skyline_song_info')
add('info-off', 'tap@lyrics_skyline_song_info', 'runtime:skylineShowSongInfo:false')
add('ambient-reveal', 'reveal@lyrics_skyline_ambient_lines')
add('ambient-open', 'tap@lyrics_skyline_ambient_lines')
add('ambient-off', 'tap@settings-option:Off', 'runtime:skylineAmbientLines:0')
add('font-reveal', 'reveal@lyrics_skyline_current_font_size')
add('font-large', 'slider@lyrics_skyline_current_font_size,1', 'runtime:skylineCurrentFontSize:84')
player('changed', 'absent:skyline-song-info', 'absent:skyline-ambient', 'runtime:skylineCurrentFontSize:84')
settings('disabled')
add('disable-reveal', 'reveal@lyrics_skyline_enabled')
add('disable', 'tap@lyrics_skyline_enabled', 'runtime:skylineEnabled:false')
add('disabled-back', 'escape', 'tag:settings-root')
add('disabled-player', 'tap@mini-player-expand', 'page:Lyrics', 'absent:player-skyline-open', 'absent:player-skyline')
add('reenable-collapse', 'tap@player-collapse', 'absent:now-playing')
add('reenable-root', 'reveal@settings-open-skylinelyrics')
add('reenable-open', 'tap@settings-open-skylinelyrics', 'tag:skyline-settings')
add('reenable-reveal', 'reveal@lyrics_skyline_enabled')
add('reenable', 'tap@lyrics_skyline_enabled', 'runtime:skylineEnabled:true')
add('reset-reveal', 'reveal@skyline-reset')
add('reset', 'tap@skyline-reset', 'runtime:skylineCurrentFontSize:54', 'runtime:skylineShowSongInfo:false', 'runtime:skylineAmbientLines:0')
add('restore-info-reveal', 'reveal@lyrics_skyline_song_info')
add('restore-info', 'tap@lyrics_skyline_song_info', 'runtime:skylineShowSongInfo:true')
add('restore-ambient-reveal', 'reveal@lyrics_skyline_ambient_lines')
add('restore-ambient-open', 'tap@lyrics_skyline_ambient_lines')
add('restore-ambient', 'tap@settings-option:2 lines', 'runtime:skylineAmbientLines:2')
player('restored', 'tag:skyline-song-info', 'tag:skyline-ambient', 'runtime:skylineCurrentFontSize:54')
add('resume-exit', 'tap@player-skyline-exit', 'absent:player-skyline')
add('controls-hidden', 'wait@5500', 'absent:player-controls', 'tag:lyrics-reveal-controls')
add('controls-reveal', 'tap@lyrics-reveal-controls', 'tag:player-controls')
add('artwork', 'tap@player-show-lyrics', 'page:Artwork')
add('resume', 'tap@player-play-pause', 'playing:true', 'no-playback-error:true')
add('lyrics-again', 'tap@player-show-lyrics', 'page:Lyrics')
add('playing-skyline', 'tap@player-skyline-open', 'playing:true', 'tag:player-skyline')
add('advancing', 'wait@2200', 'playing:true', 'no-playback-error:true', 'position-min:24000')
add('final-exit', 'tap@player-skyline-exit', 'absent:player-skyline')
add('final-collapse', 'tap@player-collapse', 'absent:now-playing')
add('final-pause', 'tap@mini-player-play-pause', 'playing:false', 'no-playback-error:true')

args = ['--source', 'Netease', '--start-tab', 'Settings', '--theme', 'Dark', '--width', '1100', '--height', '780',
        '--output', str(out), '--wait-ms', '650', '--startup-wait-ms', '1200', '--frames-ms', '0,160,400',
        '--play-id', '3334421408', '--capture-pcm', '--steps', ';'.join(steps),
        '--expect', ';'.join(k + '=' + ','.join(v) for k, v in expected.items())]
argfile = out / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
with (out / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(result.returncode)
subprocess.run([sys.executable, 'tools/verify_online_playback_capture.py', str(out), '--track', 'netease:3334421408',
                '--output', str(out / 'pcm-readback.json')], cwd=repo, check=True)
subprocess.run([sys.executable, 'tools/verify_skyline_behavior_capture.py', str(out),
                '--output', str(out / 'skyline-layout.json')], cwd=repo, check=True)
print('PASS: real Skyline visibility/song info/ambient/font/reset consumers and non-silent consumed PCM')
