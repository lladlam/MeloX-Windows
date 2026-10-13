#!/usr/bin/env python3
"""Replay the reported anonymous DAMIDAMI track with the production player.

search/unified/lyrics use an explicitly labelled software PCM endpoint.
output-error uses the actual host audio system and requires a genuine output
failure; it does not inject a failure. No online PCM is published by this tool.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('mode', choices=['search', 'unified', 'lyrics', 'output-error'])
parser.add_argument('--home', type=Path, required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--width', type=int, default=427)
parser.add_argument('--height', type=int, default=924)
options = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
home, output = options.home.resolve(), options.output.resolve()
assert not home.exists(), 'Use a new isolated validation home'
(home / '.melox').mkdir(parents=True)
# Onboarding is separately accepted. This explicit capture input contains no account or audio.
(home / '.melox/prefs_melox_app_settings.properties').write_text('onboarding_completed=true\n', encoding='utf-8')
output.mkdir(parents=True, exist_ok=True)
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
args = ['--source', 'Netease', '--start-tab', 'Search', '--theme', 'Dark', '--output', str(output),
        '--width', str(options.width), '--height', str(options.height), '--wait-ms', '650',
        '--startup-wait-ms', '1200', '--frames-ms', '0,160,400']
if options.mode in ['search', 'unified']:
    tag = 'search-song-2755332551' if options.mode == 'search' else 'search-track-netease-2755332551'
    if options.mode == 'unified':
        args += ['--unified-sources', 'Netease']
    args += ['--capture-pcm']
    steps = f'query:text@search-query,DAMIDAMI;submit:tap@search-submit;result:wait@5000;play:tap@{tag};playing:wait@5000;player:tap@mini-player-expand;pause:tap@player-play-pause;lyrics:tap@player-show-lyrics;lyrics-loaded:wait@1500;collapse:tap@player-collapse'
    expect = f'result=tag:{tag},absent:search-error;playing=track:netease:2755332551,playing:true,position-min:500,no-playback-error:true;player=page:Artwork,absent:playback-error;pause=playing:false;lyrics-loaded=page:Lyrics,word-lines-min:1;collapse=absent:now-playing'
elif options.mode == 'lyrics':
    args += ['--play-id', '2755332551', '--capture-pcm']
    steps = 'player:tap@mini-player-expand;pause:tap@player-play-pause;seek:slider@player-progress,0.10;lyrics:tap@player-show-lyrics;lyrics-loaded:wait@1500;collapse:tap@player-collapse'
    expect = 'player=page:Artwork,playing:true,no-playback-error:true;pause=playing:false;seek=position-min:10000;lyrics-loaded=page:Lyrics,word-lines-min:1,playing:false,no-playback-error:true;collapse=absent:now-playing'
else:
    args += ['--play-id', '2755332551']
    steps = 'failed:wait@5000;player:tap@mini-player-expand;retry:tap@playback-error-retry;retried:wait@5000;close:tap@playback-error-close;collapse:tap@player-collapse'
    expect = 'failed=track:netease:2755332551,playing:false,no-playback-error:false,error-kind:Output;player=tag:playback-error,tag:playback-error-details;retried=tag:playback-error,playing:false,error-kind:Output;close=absent:playback-error;collapse=absent:now-playing'
# The fresh isolated app presents its genuine cloud-control choice. Decline
# through the production UI before clicking a search row behind that modal.
steps = 'remote-reject:tap@remote-reject;' + steps
expect = 'remote-reject=cloud-choice:true,cloud-enabled:false;' + expect
args += ['--steps', steps, '--expect', expect]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
(output / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n')
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
logfile = home / '.melox/logs/melox-debug.log'
if logfile.is_file():
    shutil.copyfile(logfile, output / 'melox-debug.log')
if result.returncode == 0 and options.mode == 'output-error':
    assert logfile.read_text(encoding='utf-8').count('PCM output rejected: system default;') >= 2, 'Retry must actually reopen the genuine audio output'
if result.returncode == 0 and options.mode != 'output-error':
    result = subprocess.run([shutil.which('python3') or 'python', str(repo / 'tools/verify_online_playback_capture.py'),
                             str(output), '--track', 'netease:2755332551', '--output', str(output / 'pcm-readback.json')], cwd=repo)
print(options.mode, result.returncode)
raise SystemExit(result.returncode)
