#!/usr/bin/env python3
"""Physical production UI input for pinned lyrics controls, then real preference reload.

Usage: capture_lyrics_settings.py controls|reload ISOLATED_ONBOARDED_HOME OUTPUT
The controls home must have no pre-existing lyrics preferences. Run reload in a
second process with the same home to verify persistence independently of setters.
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
home, output = Path(home).resolve(), Path(output).resolve()
assert mode in ('controls', 'reload')
output.mkdir(parents=True, exist_ok=True)
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
setters = (repo / 'shared/src/commonMain/kotlin/melox/settings/MeloXSettingsPreferences.kt').read_text()
properties = dict(re.findall(r'"(lyrics_[^"]+)"\s*->\s*MeloXSettingsRuntime\.(\w+)', setters))
properties['lyrics_text_pv_style'] = 'textPVStyle'  # Setter also updates the style's reference speed.
steps, expectations, changed = [], {}, {}


def add(name, action, checks=()):
    steps.append(f'{name}:{action}')
    if checks:
        expectations[name] = list(checks)


def checks(key, kind, value):
    text = str(value).lower() if isinstance(value, bool) else str(value)
    changed[key] = (kind, text)
    return [f'pref-{kind}:{key}:{text}', f'runtime:{properties[key]}:{text}']


def choose(key, label, kind, value):
    add('reveal-' + key, 'reveal@' + key)
    add('open-' + key, 'tap@' + key)
    add('set-' + key, 'tap@settings-option:' + label, checks(key, kind, value))


def toggle(key, before):
    add('reveal-' + key, 'reveal@' + key)
    add('set-' + key, 'tap@' + key, checks(key, 'bool', not before) + [('unchecked:' if before else 'checked:') + key])


def slider(key, fraction, value, tag=None):
    tag = tag or key
    add('reveal-' + key, 'reveal@' + tag)
    add('set-' + key, f'slider@{tag},{fraction}', checks(key, 'float', value))


if mode == 'controls':
    # A fresh onboarded home still needs the real remote-config choice.
    # Dismiss its modal by pointer input before touching underlying settings.
    add('remote-reject', 'tap@remote-reject', ['cloud-choice:true', 'cloud-enabled:false'])
add('open', 'tap@settings-open-lyrics', ['tag:settings-lyrics', 'tag:advanced-lyrics-settings'])
choose('lyrics_rendering_quality', 'Low · saves battery', 'string', 'Low')
for key, before in [('lyrics_auto_select', True), ('lyrics_word_by_word', True), ('lyrics_pseudo_timing', False),
                    ('lyrics_tap_seek', True), ('lyrics_long_press_share', True), ('lyrics_interlude_countdown', True),
                    ('lyrics_auto_follow', True)]:
    toggle(key, before)
choose('lyrics_follow_delay_ms', '5.0 seconds', 'int', 5000)
toggle('lyrics_reduce_motion', False)
toggle('lyrics_translation', True)
choose('lyrics_translation_font_scale', '80%', 'float', .8)
choose('lyrics_translation_opacity', '40%', 'float', .4)
choose('lyrics_translation_display_mode', 'Current line only', 'string', 'FocusedLine')
toggle('lyrics_romanization', False)
choose('lyrics_romanization_display_mode', 'Every line', 'string', 'AllLines')
choose('lyrics_romanization_font_scale', '50%', 'float', .5)
choose('lyrics_romanization_opacity', '50%', 'float', .5)
choose('lyrics_advance_ms', '500 ms early', 'int', 500)
toggle('lyrics_advance_word_by_word', False)
choose('lyrics_refresh_rate', '120 FPS', 'int', 120)
choose('lyrics_font_scale', '112%', 'float', 1.12)
choose('lyrics_font_weight', 'Regular', 'string', 'Regular')
choose('lyrics_lift_mode', 'Lift by word', 'string', 'Word')
choose('lyrics_long_tone_detection', 'Detect by word', 'string', 'Word')
toggle('lyrics_glow_enabled', True)
toggle('lyrics_glow_long_tones_only', True)
choose('lyrics_glow_strength', '140%', 'float', 1.4)
choose('lyrics_long_tone_strength', 'Off', 'float', 0)
choose('lyrics_long_tone_threshold_ms', '0.3 seconds', 'int', 300)
choose('lyrics_spacing_scale', '140%', 'float', 1.4)
choose('lyrics_blur_strength', 'Off', 'float', 0)
choose('lyrics_focus_scale', '108%', 'float', 1.08)
choose('lyrics_inactive_opacity', '60%', 'float', .6)
choose('lyrics_interface_auto_hide_ms', '15 seconds', 'int', 15000)
choose('lyrics_scroll_hide_threshold_dp', '40 dp', 'int', 40)
toggle('lyrics_cascade_bounce_enabled', True)
toggle('lyrics_scale_bounce_enabled', True)
choose('lyrics_scale_bounce_duration_ms', '150 ms', 'int', 150)
choose('lyrics_focus_color_lead_ms', '-300 ms', 'int', -300)
for key, fraction, value in [
    ('lyrics_cascade_bounce', 1, .8), ('lyrics_cascade_bounce_gradient', 0, 0), ('lyrics_scale_bounce', 1, .5),
    ('lyrics_highlight_gradient_width', 1, 3), ('lyrics_highlight_gradient_reduction', 0, 0),
    ('lyrics_focus_position', 1, .8), ('lyrics_distance_blur_scale', 1, 1.5), ('lyrics_hidden_blur_scale', 1, 1.5),
    ('lyrics_dim_amount', 0, 0), ('lyrics_cascade_delay_ms', 1, 100), ('lyrics_cascade_delay_increase_ms', 1, 100),
    ('lyrics_cascade_following_delay_ms', 1, 200), ('lyrics_cascade_catch_up_ratio', 0, .5),
    ('lyrics_cascade_chase_gradient', 1, 1), ('lyrics_cascade_duration_ms', 1, 1200), ('lyrics_snap_threshold_ms', 1, 500),
]:
    slider(key, fraction, value)
choose('lyrics_style', 'Text PV', 'string', 'TextPV')
add('reveal-pv-style', 'reveal@lyrics-pv-kineticsplit')
add('pv-style', 'tap@lyrics-pv-kineticsplit', checks('lyrics_text_pv_style', 'string', 'KineticSplit'))
slider('lyrics_text_pv_motion_intensity', .75, 1.5, 'lyrics-pv-intensity')
slider('lyrics_text_pv_animation_speed', .75, 3, 'lyrics-pv-speed')
inventory = json.loads((repo / 'parity/3f877ea/evidence/search-player-next-lyrics-control-gaps.json').read_text())
assert set(changed) == set(inventory['pinned_setting_keys']), (len(changed), set(inventory['pinned_setting_keys']) - set(changed))
all_checks = [item for key, (kind, value) in changed.items()
              for item in (f'pref-{kind}:{key}:{value}', f'runtime:{properties[key]}:{value}')]
if mode == 'controls':
    settings = home / '.melox/prefs_melox_app_settings.properties'
    assert not any(line.startswith('lyrics_') for line in settings.read_text().splitlines()), 'Use a fresh isolated controls home'
    add('reload', 'reload-settings', all_checks)
    add('back', 'escape', ['tag:settings-root'])
else:
    steps = ['open:tap@settings-open-lyrics']
    expectations = {'00-settings': all_checks, 'open': ['tag:advanced-lyrics-settings'] + all_checks}
args = ['--source', 'Local', '--start-tab', 'Settings', '--theme', 'Dark', '--width', '1100', '--height', '780',
        '--output', str(output), '--wait-ms', '180', '--startup-wait-ms', '1000',
        '--steps', ';'.join(steps), '--expect', ';'.join(name + '=' + ','.join(values) for name, values in expectations.items())]
argfile = output / 'arguments.txt'
argfile.write_text('\n'.join(args) + '\n')
(output / 'control-inputs.json').write_text(json.dumps(changed, indent=2) + '\n')
command = [gradle, ':desktop:captureUi', '--no-daemon', '--max-workers=1', '--console=plain',
           '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=' + os.environ.get('MELOX_CAPTURE_JVMARGS', '-Xmx1024m -XX:+UseSerialGC'),
           '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(argfile)]
(output / 'command.txt').write_text(subprocess.list2cmdline(command) + '\n')
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run(command, cwd=repo, stdout=log, stderr=subprocess.STDOUT)
print(mode, result.returncode)
sys.exit(result.returncode)
