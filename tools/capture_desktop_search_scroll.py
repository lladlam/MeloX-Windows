#!/usr/bin/env python3
"""Public catalog search regression: fixed toolbar and real multi-row grid input."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

repo = Path(__file__).resolve().parents[1]
home, output = (Path(value).resolve() for value in sys.argv[1:3])
output.mkdir(parents=True, exist_ok=True)
prefs = home / '.melox/prefs_melox_app_settings.properties'
assert not prefs.exists(), 'Use a fresh isolated home'
prefs.parent.mkdir(parents=True, exist_ok=True)
prefs.write_text('onboarding_completed=true\ngeneral_desktop_layout=true\n', encoding='utf-8')
steps = [
    'consent:tap@remote-reject',
    'search:tap@desktop-nav:Search',
    'focus:tap@search-query',
    'touch-focus:touch-tap@search-query',
    'touch-refocus:touch-tap@search-query',
    'mouse-refocus:tap@search-query',
    'query:type@love',
    'suggestions:wait@5000',
    'albums:tap@search-kind-albums',
    'loaded:wait@5000',
    'mouse-scroll:mouse-drag@desktop-search-media-scroll,0,-130',
    'touch-scroll:touch-drag@desktop-search-media-scroll,0,-130',
]
(output / 'steps.txt').write_text('\n'.join(steps) + '\n', encoding='utf-8')
args = ['--locale', 'en-US', '--source', 'Netease', '--start-tab', 'Home', '--theme', 'Dark',
        '--width', '1280', '--height', '800', '--output', str(output), '--wait-ms', '350',
        '--startup-wait-ms', '1200', '--steps-file', str(output / 'steps.txt')]
(output / 'arguments.txt').write_text('\n'.join(args) + '\n', encoding='utf-8')
gradle = os.environ.get('MELOX_GRADLE') or shutil.which('gradle')
assert gradle, 'Set MELOX_GRADLE'
with (output / 'gradle.log').open('w') as log:
    result = subprocess.run([gradle, ':desktop:captureUi', '--offline', '--no-daemon', '--max-workers=1',
        '--console=plain', '-Pkotlin.compiler.execution.strategy=in-process',
        '-Dorg.gradle.jvmargs=-Xmx1536m -XX:+UseSerialGC',
        '-PcaptureHome=' + str(home), '-PcaptureArgsFile=' + str(output / 'arguments.txt')],
        cwd=repo, stdout=log, stderr=subprocess.STDOUT)
if result.returncode:
    print((output / 'gradle.log').read_text(errors='replace')[-12000:])
    raise SystemExit(result.returncode)
data = json.loads((output / 'capture.json').read_text())
requests = {s['file'].split('-', 1)[1].removesuffix('.png'): s['touch_keyboard_requests'] for s in data['scenes']}
assert requests['focus'] == 0, requests
assert requests['touch-focus'] == 1 and requests['touch-refocus'] == 2 and requests['mouse-refocus'] == 2, requests


def nodes_at(name):
    entry = next(s for s in data['scenes'] if s['file'].split('-', 1)[1] == name + '.png')
    nodes = json.loads((output / entry['file']).with_suffix('.semantics.json').read_text())
    return {n['tag']: n for n in nodes if n.get('tag')}


before, suggestions = nodes_at('focus'), nodes_at('suggestions')
assert 'desktop-search-suggestions' in suggestions, 'Real search suggestions did not load'
assert before['search-query']['bounds'] == suggestions['search-query']['bounds'], 'Suggestions shifted the search field'
assert before['desktop-search-toolbar']['bounds'] == suggestions['desktop-search-toolbar']['bounds'], 'Suggestions resized the toolbar'
positions = {name: nodes_at(name)['desktop-search-media-scroll']['vertical_scroll']
             for name in ['loaded', 'mouse-scroll', 'touch-scroll']}
assert positions['mouse-scroll'] - positions['loaded'] > 60, positions
assert positions['touch-scroll'] - positions['mouse-scroll'] > 60, positions
report = {'renderer': data['renderer'], 'physical_touch_verified': False, 'positions': positions,
          'public_query': 'love', 'kind': 'albums', 'suggestions_overlay': True}
report['touch_keyboard_requests'] = requests
(output / 'search-scroll-report.json').write_text(json.dumps(report, indent=2) + '\n')
print('PASS real catalog search:', positions)
