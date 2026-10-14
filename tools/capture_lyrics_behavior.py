#!/usr/bin/env python3
"""Pointer-driven settings changes followed by real Tiny Giant rendering/PCM.

No lyrics or player state is injected. Uses the same real provider track as the
user's font regression report. Supply a fresh isolated onboarded capture home.
Personal audio stays in that home; only sanitized frames/metrics are archived.
Usage: capture_lyrics_behavior.py HOME OUTPUT
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, out = map(lambda p: Path(p).resolve(), sys.argv[1:3])
out.mkdir(parents=True, exist_ok=True)
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
steps, expected = [], {}


def add(name, action, *checks):
    steps.append(f'{name}:{action}')
    if checks:
        expected[name] = list(checks)


def settings(prefix):
    add(prefix + '-collapse', 'tap@player-collapse', 'absent:now-playing')
    add(prefix + '-reveal-settings', 'reveal@settings-open-lyrics')
    add(prefix + '-settings', 'tap@settings-open-lyrics', 'tag:advanced-lyrics-settings')


def return_player(prefix, *checks):
    add(prefix + '-back', 'escape', 'tag:settings-root')
    add(prefix + '-player', 'tap@mini-player-expand', 'page:Lyrics', 'playing:false',
        'no-playback-error:true', *checks)


def choose(prefix, key, label, *checks):
    add(prefix + '-reveal', 'reveal@' + key)
    add(prefix + '-open', 'tap@' + key)
    add(prefix + '-set', 'tap@settings-option:' + label, *checks)


add('remote-reject', 'tap@remote-reject', 'cloud-choice:true', 'cloud-enabled:false')
add('player', 'tap@mini-player-expand', 'page:Artwork', 'playing:true', 'no-playback-error:true')
add('pause', 'tap@player-play-pause', 'playing:false')
add('seek', 'slider@player-progress,0.155', 'position-min:21000')
add('baseline', 'tap@player-show-lyrics', 'page:Lyrics', 'translation-min:2', 'word-lines-min:1')

settings('hidden')
add('hide-reveal', 'reveal@lyrics_translation')
add('hide-toggle', 'tap@lyrics_translation', 'pref-bool:lyrics_translation:false',
    'runtime:showLyricTranslation:false', 'unchecked:lyrics_translation')
return_player('hidden', 'translation-count:0')

settings('focused')
add('show-reveal', 'reveal@lyrics_translation')
add('show-toggle', 'tap@lyrics_translation', 'pref-bool:lyrics_translation:true',
    'runtime:showLyricTranslation:true', 'checked:lyrics_translation')
choose('focused-scope', 'lyrics_translation_display_mode', 'Current line only',
       'pref-string:lyrics_translation_display_mode:FocusedLine',
       'runtime:lyricTranslationDisplayMode:FocusedLine')
return_player('focused', 'translation-count:1')

settings('all')
choose('all-scope', 'lyrics_translation_display_mode', 'Every line',
       'pref-string:lyrics_translation_display_mode:AllLines')
choose('regular', 'lyrics_font_weight', 'Regular', 'runtime:lyricFontWeight:Regular')
choose('larger', 'lyrics_font_scale', '112%', 'runtime:lyricFontScale:1.12')
add('reduced-reveal', 'reveal@lyrics_reduce_motion')
add('reduced-toggle', 'tap@lyrics_reduce_motion', 'runtime:lyricReduceMotion:true')
return_player('all', 'translation-min:2')
add('resume', 'tap@player-play-pause', 'playing:true', 'no-playback-error:true')
add('advancing', 'wait@2200', 'playing:true', 'position-min:24000', 'no-playback-error:true')
add('controls-hidden', 'wait@5500', 'absent:player-controls', 'tag:lyrics-reveal-controls', 'playing:true')
add('controls-revealed', 'tap@lyrics-reveal-controls', 'tag:player-controls', 'playing:true')
add('pause-again', 'tap@player-play-pause', 'playing:false', 'no-playback-error:true')

args = ['--source', 'Netease', '--start-tab', 'Settings', '--theme', 'Dark',
        '--output', str(out), '--width', '666', '--height', '1334', '--density', '1.75',
        '--wait-ms', '650', '--startup-wait-ms', '1200', '--frames-ms', '0,160,400',
        '--play-id', '3334421408', '--capture-pcm', '--steps', ';'.join(steps),
        '--expect', ';'.join(n + '=' + ','.join(v) for n, v in expected.items())]
argfile = out / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
(out / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n')
with (out / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    raise SystemExit(result.returncode)
subprocess.run([sys.executable, 'tools/verify_online_playback_capture.py', str(out),
                '--track', 'netease:3334421408', '--output', str(out / 'pcm-readback.json')],
               cwd=repo, check=True)
subprocess.run([sys.executable, 'tools/verify_lyrics_layout.py', str(out),
                '--output', str(out / 'layout.json')], cwd=repo, check=True)
print('PASS: pointer-selected translation scope, font size/weight and reduced motion with real PCM')
