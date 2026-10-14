#!/usr/bin/env python3
"""Production pointer replay for 17 Skyline keys, screen-awake scopes and reset.

Run controls, reload, reset, reload-reset in separate JVMs against one fresh home.
The controls home must be pre-onboarded without any Skyline preference values.
"""
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
mode, home, output = sys.argv[1:4]
assert mode in ('controls', 'reload', 'reset', 'reload-reset')
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
setters = (repo / 'shared/src/commonMain/kotlin/melox/settings/MeloXSettingsPreferences.kt').read_text(encoding='utf-8')
properties = dict(re.findall(r'"((?:lyrics_skyline_|player_screen_awake_mode)[^"]*)"\s*->\s*MeloXSettingsRuntime\.(\w+)', setters))
steps, expected, changed = [], {}, {}


def add(name, action, checks=()):
    steps.append(f'{name}:{action}')
    if checks:
        expected[name] = list(checks)


def checks(key, kind, value):
    value = str(value).lower() if isinstance(value, bool) else str(value)
    changed[key] = [kind, value]
    return [f'pref-{kind}:{key}:{value}', f'runtime:{properties[key]}:{value}']


def choose(key, label, kind, value, prefix=''):
    add(prefix + 'reveal-' + key, 'reveal@' + key)
    add(prefix + 'open-' + key, 'tap@' + key)
    add(prefix + 'set-' + key, 'tap@settings-option:' + label, checks(key, kind, value))


add('remote-reject', 'tap@remote-reject', ['cloud-choice:true', 'cloud-enabled:false'])
add('root-skyline', 'reveal@settings-open-skylinelyrics')
add('open', 'tap@settings-open-skylinelyrics', ['tag:skyline-settings'])
for suffix in ['enabled', 'song_info', 'keep_awake']:
    key = 'lyrics_skyline_' + suffix
    add('reveal-' + key, 'reveal@' + key)
    add('set-' + key, 'tap@' + key, checks(key, 'bool', False) + ['unchecked:' + key])
choose('lyrics_skyline_ambient_lines', '4 lines', 'int', 4)
choose('lyrics_skyline_ambient_max_characters', '2 characters', 'int', 2)
choose('lyrics_skyline_ambient_max_visible', '8 groups', 'int', 8)
for suffix, fraction, value in [
    ('current_font_size', 1, 84), ('current_max_scale', 1, 1.2), ('current_width', 0, .4),
    ('next_font_size', 1, 44), ('next_opacity', 0, .2), ('current_spacing', 1, 36),
    ('ambient_font_size', 0, 24), ('ambient_opacity', 1, 1.8), ('ambient_blur', 0, 0),
    ('ambient_max_tilt', 1, 20), ('ambient_drift', 0, 0),
]:
    key = 'lyrics_skyline_' + suffix
    add('reveal-' + key, 'reveal@' + key)
    add('set-' + key, f'slider@{key},{fraction}', checks(key, 'float', value))
audit = json.loads((repo / 'parity/3f877ea/evidence/skyline-settings-source-audit.json').read_text(encoding='utf-8'))
assert set(changed) == set(audit['canonical_keys']) and len(changed) == 17
add('back', 'escape', ['tag:settings-root'])
add('root-appearance', 'reveal@settings-open-appearance')
add('appearance', 'tap@settings-open-appearance', ['tag:settings-appearance'])
for value, label in [('Player', 'While the player is open'), ('Lyrics', 'On the lyrics page'),
                     ('Disabled', 'Off'), ('HiddenLyricsInterface', 'After the lyrics UI is hidden')]:
    choose('player_screen_awake_mode', label, 'string', value, value + '-')
add('back-appearance', 'escape', ['tag:settings-root'])

reset_values = dict(current_font_size=54, current_max_scale=1.1, next_font_size=24,
                    current_spacing=14, current_width=.64, next_opacity=.48,
                    ambient_font_size=44, ambient_opacity=1, ambient_blur=1,
                    ambient_max_tilt=8, ambient_drift=1)
reset = dict(changed)
reset.update({'lyrics_skyline_' + key: ['float', str(value)] for key, value in reset_values.items()})
reset['lyrics_skyline_ambient_max_characters'] = ['int', '4']
reset['lyrics_skyline_ambient_max_visible'] = ['int', '16']


def all_checks(values):
    return [check for key, (kind, value) in values.items()
            for check in (f'pref-{kind}:{key}:{value}', f'runtime:{properties[key]}:{value}')]


if mode == 'controls':
    settings = (home / '.melox/prefs_melox_app_settings.properties').read_text(encoding='utf-8')
    assert not any(l.startswith('lyrics_skyline_') for l in settings.splitlines()), 'Use a fresh home'
    add('reload', 'reload-settings', all_checks(changed))
elif mode == 'reset':
    steps, expected = [], {'00-settings': all_checks(changed)}
    add('root-skyline', 'reveal@settings-open-skylinelyrics')
    add('open', 'tap@settings-open-skylinelyrics', ['tag:skyline-settings'])
    add('reveal-reset', 'reveal@skyline-reset')
    add('reset', 'tap@skyline-reset', all_checks(reset))
    add('reload', 'reload-settings', all_checks(reset))
else:
    values = reset if mode == 'reload-reset' else changed
    steps, expected = [], {'00-settings': all_checks(values)}
    add('root-skyline', 'reveal@settings-open-skylinelyrics')
    add('open', 'tap@settings-open-skylinelyrics', ['tag:skyline-settings'] + all_checks(values))

args = ['--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark', '--width', '1100', '--height', '780',
        '--output', str(output), '--wait-ms', '180', '--startup-wait-ms', '1000',
        '--steps', ';'.join(steps), '--expect', ';'.join(k + '=' + ','.join(v) for k, v in expected.items())]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n', encoding='utf-8')
(output / 'control-inputs.json').write_text(json.dumps(changed, indent=2) + '\n', encoding='utf-8')
(output / 'reset-inputs.json').write_text(json.dumps(reset, indent=2) + '\n', encoding='utf-8')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process',
           '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
with (output / 'gradle.log').open('w', encoding='utf-8') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
print(mode, result.returncode)
sys.exit(result.returncode)
