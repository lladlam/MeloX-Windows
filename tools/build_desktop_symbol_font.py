#!/usr/bin/env python3
"""Rebuild the pinned SF Symbols subset used by the Desktop shell.

The manifest names every SF Symbol referenced by the fixed Desktop source and
the Windows symbol enum. It also pins the official Apple font download and the
extracted variable font, so a rebuild cannot silently change outlines.

This tool never downloads the 208 MB Apple font. Pass the extracted
`SF-Pro.ttf` from `SF-Pro.dmg`; the source hash is checked before any glyph is
copied. `--verify` regenerates the subset in memory and requires byte equality
with the committed resource.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'parity/desktop-a41ea0e/desktop-symbols.json'


def digest(data):
    return hashlib.sha256(data).hexdigest()


def build(manifest, source):
    raw = source.read_bytes()
    assert digest(raw) == manifest['source_font_sha256'], 'Unexpected SF Pro source bytes'
    font = TTFont(io.BytesIO(raw), recalcTimestamp=False)
    cmap = font.getBestCmap()
    missing = [entry['source_name'] for entry in manifest['symbols'] if entry['code'] not in cmap]
    assert not missing, 'SF Pro lacks symbol code points: ' + ', '.join(missing)
    # A code point can exist while the outline is empty. Check every glyph
    # before writing a font that would render a blank button on Windows.
    blank = []
    for entry in manifest['symbols']:
        name = cmap[entry['code']]
        glyph = font['glyf'][name] if 'glyf' in font else font['CFF2'].cff.topDictIndex[0].CharStrings[name]
        if hasattr(glyph, 'numberOfContours') and glyph.numberOfContours == 0:
            blank.append(entry['source_name'])
    assert not blank, 'Empty SF Symbol outlines: ' + ', '.join(blank)
    options = subset.Options()
    # Direct private-use code points need no layout closure. Keeping `fvar`,
    # `gvar`, `HVAR` and `STAT` preserves both Normal and Medium instances.
    options.layout_features = []
    options.name_IDs = ['*']
    options.name_legacy = True
    options.name_languages = ['*']
    options.notdef_outline = True
    options.passthrough_tables = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=[entry['code'] for entry in manifest['symbols']])
    subsetter.subset(font)
    output = io.BytesIO()
    font.save(output)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, help='extracted official SF-Pro.ttf')
    parser.add_argument('--verify', action='store_true', help='compare against the committed subset')
    parser.add_argument('--output', type=Path, help='write the generated subset here')
    args = parser.parse_args()
    manifest = json.loads(MANIFEST.read_text(encoding='utf-8'))
    assert manifest['reference_commit'] == 'a41ea0e1e1fa69fb543d827b853fe3aff5420951'
    assert manifest['source_font'] == 'SF-Pro.ttf'
    assert manifest['symbol_count'] == len(manifest['symbols'])
    target = args.output or (ROOT / manifest['subset_resource'])
    if args.verify:
        # Regeneration requires the pinned Apple source font. CI verifies the
        # committed resource and symbol coverage directly instead.
        assert args.source, '--verify requires --source for an exact rebuild'
    assert args.source, '--source is required'
    data = build(manifest, args.source)
    assert len(data) == manifest['subset_bytes']
    assert digest(data) == manifest['subset_sha256']
    if args.verify:
        assert target.read_bytes() == data, 'Committed SF Symbol subset differs from a clean rebuild'
        print(f'PASS: {len(manifest["symbols"])} SF Symbols rebuild byte-identically ({len(data)} bytes)')
        return
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(data)
    print(f'Wrote {target}: {len(manifest["symbols"])} symbols, {len(data)} bytes, sha256={digest(data)}')


if __name__ == '__main__':
    main()
