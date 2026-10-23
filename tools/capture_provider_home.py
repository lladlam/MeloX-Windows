#!/usr/bin/env python3
"""Live provider feed -> native ranking detail -> back in Desktop production UI.

This is a strict online replay: unavailable services fail, with original screenshots
and logs retained. No response fixtures or credential injection are used.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

source, home, output = sys.argv[1:4]
assert source in ('QQMusic', 'Kugou', 'YouTubeMusic')
ranking_id = {'QQMusic': '62', 'Kugou': '49223'}.get(source)
repo = Path(__file__).resolve().parents[1]
home, output = Path(home).resolve(), Path(output).resolve()
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
steps = [
    ('consent', 'tap@remote-reject', ['cloud-enabled:false']),
    ('home', 'wait@8000', [f'tag:desktop-provider-home:{source}', 'absent:home-quick-actions']),
    # Prefix taps locate an actual provider-native id, then send a pointer event.
    ('ranking', f'tap@desktop-provider-home-ranking:{ranking_id}' if ranking_id else 'tap-first@desktop-provider-home-ranking:'),
    ('detail', 'wait@12000', ['tag:desktop-provider-ranking-detail', 'tag:desktop-collection-back', 'tag-prefix:desktop-collection-track:']),
    ('back', 'tap@desktop-collection-back', [f'tag:desktop-provider-home:{source}', 'absent:desktop-provider-ranking-detail']),
    ('explore', 'tap@desktop-nav:Explore', ['tag:desktop-explore']),
    ('explore-loaded', 'wait@8000'),
    ('explore-scroll', 'scroll@800,600,200'),
    ('explore-scroll-settled', 'wait@1500'),
    ('explore-ranking-reveal', f'reveal@desktop-explore-ranking:{ranking_id},desktop-explore,up' if ranking_id else 'wait@100'),
    ('explore-ranking', f'tap@desktop-explore-ranking:{ranking_id}' if ranking_id else 'tap-first@desktop-explore-ranking:'),
    ('explore-detail', 'wait@12000', ['tag:desktop-provider-ranking-detail', 'tag-prefix:desktop-collection-track:']),
    ('explore-back', 'escape', ['tag:desktop-explore', 'absent:desktop-provider-ranking-detail']),
]
(output / 'steps.txt').write_text('\n'.join(f'{s[0]}:{s[1]}' for s in steps) + '\n', encoding='utf-8')
(output / 'expect.txt').write_text('\n'.join(s[0] + '=' + ','.join(s[2]) for s in steps if len(s) > 2) + '\n', encoding='utf-8')
args = ['--locale', 'zh-CN', '--source', source, '--start-tab', 'Home', '--theme', 'Dark',
        '--width', '1280', '--height', '1000', '--output', str(output), '--wait-ms', '400', '--startup-wait-ms', '1500',
        '--steps-file', str(output / 'steps.txt'), '--expect-file', str(output / 'expect.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1', '--console=plain',
                             '-Pkotlin.compiler.execution.strategy=in-process', '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
                             '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
                            cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-14000:])
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text(encoding='utf-8'))
assert all(a['passed'] for s in data['scenes'] for a in s.get('assertions', []))
print(f'{source}/home: {len(data["scenes"])} scenes passed')
