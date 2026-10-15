#!/usr/bin/env python3
"""Export settings strings and route contract from the immutable Android Git tree."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

PIN = '3f877eaffe30c82eb6d24eb3647702f5bc554dd3'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('android', type=Path)
parser.add_argument('--verify', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
sources = {}


def source(path):
    data = subprocess.check_output(['git', 'show', PIN + ':android/app/src/main/' + path], cwd=args.android)
    sources[path] = hashlib.sha256(data).hexdigest()
    return data


screen = source('kotlin/com/lladlam/melox/ui/settings/SettingsScreen.kt').decode()
keys = set(re.findall(r'R\.string\.(\w+)', screen))
# App-level strings consumed outside SettingsScreen (clipboard confirmation dialog).
# They come from the same pure pinned strings.xml trees and must not be hand-typed.
keys.update({'app_clipboard_title', 'app_clipboard_song', 'app_clipboard_playlist', 'app_confirm'})
# SF Symbol inventory used by every settings row icon.
symbols = source('kotlin/com/lladlam/melox/ui/glass/MeloXSymbol.kt').decode()
sf_name = dict(re.findall(r'^\s+(\w+)\("([^"]+)",', symbols, re.M))
action_block = symbols[symbols.index('fun MeloXActionIcon('):]
action_block = action_block[:action_block.index('\n    val semanticLabel')]
action_tokens = {}
for tokens, symbol in re.findall(r'^\s+("(?:[^"\\]|\\.)*"(?:,\s*"(?:[^"\\]|\\.)*")*)\s*->\s*MeloXSymbol\.(\w+)',
                                 action_block, re.M):
    for token in re.findall(r'"((?:[^"\\]|\\.)*)"', tokens):
        # Kotlin `when` is first-match-wins; keep the first branch for a repeated token.
        action_tokens.setdefault(token, sf_name[symbol])

resources = repo / 'desktop/src/main/resources/parity'
for name in ['pinned-settings-strings.properties', 'pinned-lyrics-strings.properties', 'pinned-skyline-strings.properties']:
    keys.update(line.split('=', 1)[0] for line in (resources / name).read_text(encoding='utf-8').splitlines() if line and not line.startswith('#'))


def android_text(node):
    value = ''.join(node.itertext()).replace("\\'", "'").replace('\\"', '"').replace('\\n', '\n').replace('\\t', '\t')
    if value.startswith('"') and value.endswith('"'):
        value = value[1:-1]
    return value


def escape(value):
    return value.replace('\\', '\\\\').replace('\n', '\\n').replace('\r', '\\r').replace('\t', '\\t')


generated = {}
default = {}
for locale in ['', 'zh-rCN', 'zh-rTW', 'ja']:
    xml = source('res/values' + ('-' + locale if locale else '') + '/strings.xml')
    values = {n.attrib['name']: android_text(n) for n in ET.fromstring(xml).findall('string')}
    if not locale:
        default = values
        assert keys <= values.keys(), keys - values.keys()
    selected = {key: values[key] for key in sorted(keys & values.keys())}
    name = 'pinned-settings-pages' + ('-' + locale if locale else '') + '.properties'
    data = ('# Pure Android ' + PIN + ' / ' + (locale or 'default') + '; generated, do not edit.\n' +
            ''.join(key + '=' + escape(value) + '\n' for key, value in selected.items())).encode()
    generated['parity/' + name] = data

root = screen[screen.index('private val SettingsSections'):screen.index('@Composable\nfun SettingsScreen')]
sections = []
for title, items in re.findall(r'SettingsSection\(R.string.(\w+), listOf\((.*?)\n    \)\)', root, re.S):
    sections.append({'title': title, 'items': [dict(route=route, subtitle=subtitle, symbol=symbol, keywords=keywords)
                    for route, subtitle, symbol, keywords in re.findall(r'SettingsItem\(SettingsRoute.(\w+), R.string.(\w+), "([^"]+)", R.string.(\w+)\)', items)]})
assert [len(s['items']) for s in sections] == [6, 8, 5]
route_tokens = sorted({item['symbol'] for section in sections for item in section['items']})
assert all(token in action_tokens for token in route_tokens), [t for t in route_tokens if t not in action_tokens]
manifest = dict(pinned_android=PIN, verified_against_pure_export=True, source_sha256=sources,
                resource_sha256={k: hashlib.sha256(v).hexdigest() for k, v in generated.items()},
                string_keys=len(keys), sections=sections,
                action_icon_tokens=action_tokens, settings_route_symbols=route_tokens)
outputs = {repo / 'desktop/src/main/resources' / k: v for k, v in generated.items()}
outputs[repo / 'parity/3f877ea/evidence/settings-pages-source-audit.json'] = (json.dumps(manifest, indent=2, ensure_ascii=False) + '\n').encode()
for path, data in outputs.items():
    if args.verify:
        assert path.read_bytes() == data, path
    else:
        path.write_bytes(data)
print('PASS: pure pinned settings export, 19 root routes / 3 sections /', len(keys), 'keys / 4 resource locales')
