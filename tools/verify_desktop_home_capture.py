#!/usr/bin/env python3
"""Independent Home geometry, rendered theme colours, route retention and input audit.

Original-upload landmarks/colour residuals are measurements, not a full-image parity pass.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
from PIL import Image

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
parser.add_argument('--reference', type=Path)
args = parser.parse_args()
root = args.directory
data = json.loads((root / 'capture.json').read_text(encoding='utf-8'))
assert data['renderer'] == 'production ImageComposeScene'
assert data['steps'] == (root / 'steps.txt').read_text(encoding='utf-8').splitlines()
assert len(data['scenes']) == len(data['steps']) + 1
assert 'Exception in thread' not in (root / 'gradle.log').read_text(encoding='utf-8', errors='replace')
assertions = [a for s in data['scenes'] for a in s['assertions']]
assert assertions and all(a['passed'] for a in assertions)
density = data['density']


def scene(name):
    entry = [s for s in data['scenes'] if s['file'].split('-', 1)[1] == name + '.png'][-1]
    path = root / entry['file']
    nodes = json.loads(path.with_suffix('.semantics.json').read_text(encoding='utf-8'))
    image = Image.open(path).convert('RGB')
    assert image.size == (data['width'], data['height'])
    return entry, {n['tag']: n for n in nodes if n.get('tag')}, image


entry, nodes, image = scene('home')
home = nodes['desktop-home']['bounds']
usable = (home[2] - home[0]) / density - 104
columns = max(int((usable + 20) / 220), 1)
card_width = math.floor((usable - 20 * (columns - 1)) / columns)
quick = [nodes['desktop-home-action:' + key]['bounds'] for key in ['daily_songs', 'heart_mode', 'private_roaming']]
for bounds in quick:
    assert abs((bounds[2] - bounds[0]) / density - card_width) < 1, bounds
    assert abs((bounds[3] - bounds[1]) / density - card_width * 4 / 3) < 1, bounds
assert abs(quick[0][0] - home[0] - 52 * density) < 1
assert abs(quick[1][0] - quick[0][2] - 20 * density) < 1
before, before_nodes, _ = scene('reveal-hero')
after, after_nodes, _ = scene('return-home')
hero_tag = next(t for t in before_nodes if t.startswith('desktop-home-playlist:'))
assert hero_tag in after_nodes
assert all(abs(a - b) < 2 for a, b in zip(before_nodes[hero_tag]['bounds'], after_nodes[hero_tag]['bounds'])), 'Home scroll position was not retained'
hero_bounds = before_nodes[hero_tag]['bounds']
assert abs(hero_bounds[2] - hero_bounds[0] - (hero_bounds[3] - hero_bounds[1])) < 2, 'Playlist hero must be square'
_, paged_nodes, _ = scene('next-actions')
assert not paged_nodes['desktop-home-shelf:actions']['state'].startswith('first:0;'), 'Page button did not scroll'


def theme_samples(name, theme):
    _, tagged, frame = scene(name)
    background = frame.getpixel((data['width'] - 4, int(30 * density)))
    sidebar = frame.getpixel((int(80 * density), int(24 * density)))
    selected = tagged['desktop-nav:Home']['bounds']
    red = frame.getpixel((int(selected[0] + 4 * density), int((selected[1] + selected[3]) / 2)))
    if theme == 'Dark':
        assert max(background) <= 35 and max(sidebar) <= 35, (background, sidebar)
    else:
        # Borderless regular glass inherits the light page below, rather than
        # the retired nearly-opaque white sidebar slab.
        assert min(background) >= 240 and min(sidebar) >= 240, (background, sidebar)
    assert red[0] > 160 and red[1] < 50 and red[2] < 60, red
    return dict(theme=theme, background_rgb=background, sidebar_rgb=sidebar, selected_rgb=red)


themes = [theme_samples('home', data['theme']), theme_samples('neutral-new-theme', 'Light' if data['theme'] == 'Dark' else 'Dark')]
touch = [i for i in data.get('inputs', []) if i['type'] == 'Touch']
if any('touch-' in s for s in data['steps']):
    assert len(touch) >= 7
    for name in ['touch-horizontal', 'touch-vertical', 'short-edge-cancel', 'vertical-edge-cancel', 'edge-return']:
        observed, _, _ = scene(name)
        assert observed['pointer_mode'] == 'Touch', 'Input mode must come from observed production pointer events'
reference = None
if args.reference:
    raw = args.reference.read_bytes()
    assert hashlib.sha256(raw).hexdigest() == 'f2c2fa9fc30425513cb8a5b90569d140c520efb10f7a1413fda45aca70dd3c54'
    original = Image.open(args.reference).convert('RGB')
    assert original.size == image.size == (1960, 1184)
    original_card = [518, 402, 936, 959]
    residuals = []
    if data['theme'] == 'Dark':
        # Exclude symbols, labels, artwork and the rounded edges.
        for fx, fy in [(.05, .07), (.48, .07), (.93, .07), (.05, .39), (.93, .39)]:
            dx, dy = round(fx * 418), round(fy * 557)
            reference_rgb = original.getpixel((518 + dx, 402 + dy))
            actual_rgb = image.getpixel((round(quick[0][0] + dx), round(quick[0][1] + dy)))
            residuals.append(dict(point_in_card=[dx, dy], reference_rgb=reference_rgb, actual_rgb=actual_rgb,
                delta_rgb=[a - b for a, b in zip(actual_rgb, reference_rgb)]))
    reference = dict(sha256=hashlib.sha256(raw).hexdigest(), original_size=list(original.size), original_quick_card=original_card,
        actual_quick_card=quick[0], landmark_delta_px=[round(a - b, 3) for a, b in zip(quick[0], original_card)],
        dark_gradient_samples=residuals, density='2 inferred; original has no density/colour/version metadata',
        full_image_pixel_parity='PARTIAL: different public content, source/upload wording, CJK raster, native chrome/material and player state')
report = dict(status='PASSED', scope='Physical production replay, source geometry and rendered theme colours; not full-image parity',
    scenes=len(data['scenes']), assertions=len(assertions), columns=columns, quick_card_width_dp=card_width,
    themes=themes, touch_events=len(touch), retained_home_scroll=True, reference=reference)
(root / 'home-verification.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'PASS Home: {report["scenes"]} scenes / {report["assertions"]} assertions; source geometry, both themes, retained scroll, {len(touch)} Touch inputs')
