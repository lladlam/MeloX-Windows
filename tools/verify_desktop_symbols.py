#!/usr/bin/env python3
"""Verify the committed SF Symbols subset against the fixed Desktop manifest.

This runs without Apple's 208 MB font download. It checks the committed bytes,
the expected symbol count, private-use coverage, non-empty outlines, and every
`MeloXSymbol` entry in the Windows enum. Presence of a code point alone is not
enough: a blank glyph would still render a plausible but empty control.
"""
import argparse
import hashlib
import io
import json
import re
from pathlib import Path

from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'parity/desktop-a41ea0e/desktop-symbols.json'
ENUM = ROOT / 'desktop/src/desktopMain/kotlin/melox/ui/foundation/MeloXSymbol.kt'


def glyph_has_ink(font, name):
    if 'glyf' in font:
        glyph = font['glyf'][name]
        if glyph.numberOfContours == 0:
            return False
        glyph.recalcBounds(font['glyf'])
        return glyph.xMax > glyph.xMin and glyph.yMax > glyph.yMin
    char_string = font['CFF2'].cff.topDictIndex[0].CharStrings[name]
    bounds = char_string.calcBounds(font['CFF2'].cff.topDictIndex[0])
    return bool(bounds and bounds[2] > bounds[0] and bounds[3] > bounds[1])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert manifest['reference_commit'] == 'a41ea0e1e1fa69fb543d827b853fe3aff5420951'
    assert manifest['symbol_count'] == len(manifest['symbols']) == 135
    source = ROOT / manifest['subset_resource']
    raw = source.read_bytes()
    assert len(raw) == manifest['subset_bytes']
    assert hashlib.sha256(raw).hexdigest() == manifest['subset_sha256']
    font = TTFont(io.BytesIO(raw), recalcTimestamp=False)
    cmap = font.getBestCmap()
    records = []
    for entry in manifest['symbols']:
        name = cmap.get(entry['code'])
        assert name, f"Missing U+{entry['code']:05X} for {entry['source_name']}"
        assert glyph_has_ink(font, name), f"Blank glyph for {entry['source_name']}"
        records.append({'source_name': entry['source_name'], 'resolved_name': entry['resolved_name'],
                        'codepoint': entry['codepoint'], 'glyph': name, 'resolution': entry['resolution']})
    expected_names = {entry['source_name'] for entry in manifest['symbols']}
    enum = ENUM.read_text(encoding='utf-8')
    enum_entries = re.findall(r'^\s*([A-Za-z]+)\("([^"]+)", intArrayOf\(([^)]*)\)\)', enum, re.M)
    assert enum_entries, 'No MeloXSymbol enum entries found'
    enum_records = []
    for enum_name, sf_name, values in enum_entries:
        assert sf_name in expected_names, f'MeloXSymbol.{enum_name} refers to unmapped SF Symbol {sf_name}'
        code_points = [int(value.strip(), 16) for value in values.split(',') if value.strip()]
        assert code_points and all(code in cmap for code in code_points), f'MeloXSymbol.{enum_name} has an absent code point'
        assert any(glyph_has_ink(font, cmap[code]) for code in code_points), f'MeloXSymbol.{enum_name} renders blank'
        enum_records.append({'enum': enum_name, 'sf_name': sf_name,
                             'codepoints': [f'U+{code:05X}' for code in code_points]})
    result = {
        'status': 'PASSED',
        'reference_commit': manifest['reference_commit'],
        'apple_product': manifest['apple_product'],
        'source_font_sha256': manifest['source_font_sha256'],
        'subset': manifest['subset_resource'],
        'subset_sha256': manifest['subset_sha256'],
        'subset_bytes': len(raw),
        'symbol_count': len(records),
        'enum_symbols': len(enum_records),
        'unique_codepoints': len(set(entry['code'] for entry in manifest['symbols'])),
        'aliases': [record for record in records if record['resolution'] != 'canonical'],
        'scope': 'fixed Desktop SF Symbols subset and Windows enum coverage; not native macOS pixel acceptance',
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'PASS: {len(records)} SF Symbols, {len(enum_records)} enum entries and {result["unique_codepoints"]} unique code points have visible ink')


if __name__ == '__main__':
    main()
