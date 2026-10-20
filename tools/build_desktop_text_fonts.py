#!/usr/bin/env python3
"""Build frozen SF Pro text/display faces from the already pinned official font.

Keep the complete non-private-use character repertoire and OpenType shaping,
including tabular digits. Freeze optical size and weight to avoid Skia variable
outline/advance mismatches. --verify without --source checks shipped bytes/axes;
with --source it additionally requires byte-identical regeneration.
Requires fontTools and uharfbuzz, also installed by the existing font CI step.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
import uharfbuzz as hb

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / 'parity/desktop-a41ea0e/desktop-text-fonts.json'
SOURCE_SHA = '7270e1593be7b88597d8ab302e4d156a33084c85b8161e042ff6510cdaf10e10'
FACES = [(kind, optical, weight) for kind, optical, weights in
         [('text', 17, [400, 500, 600, 700]), ('subtitle', 22, [600]), ('display', 28, [700])]
         for weight in weights]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def build(raw, optical, weight):
    font = TTFont(io.BytesIO(raw), recalcTimestamp=False)
    options = subset.Options()
    options.layout_features = ['*']
    options.name_IDs = ['*']
    options.name_languages = ['*']
    options.notdef_outline = True
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(unicodes=[c for c in font.getBestCmap() if
        not (0xE000 <= c <= 0xF8FF or 0xF0000 <= c <= 0xFFFFD or 0x100000 <= c <= 0x10FFFD)])
    subsetter.subset(font)
    font = instantiateVariableFont(font, {'wght': weight, 'opsz': optical, 'wdth': 100}, inplace=True)
    output = io.BytesIO()
    font.save(output)
    return output.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path)
    parser.add_argument('--verify', action='store_true')
    args = parser.parse_args()
    assert args.verify or args.source, '--source required to build'
    raw = args.source.read_bytes() if args.source else None
    if raw is not None:
        assert sha(raw) == SOURCE_SHA
    faces = []
    for kind, optical, weight in FACES:
        path = f'desktop/src/main/resources/fonts/sf_pro_{kind}_{weight}.ttf'
        target = ROOT / path
        if raw is not None:
            generated = build(raw, optical, weight)
            if args.verify:
                assert target.read_bytes() == generated, path
            else:
                target.write_bytes(generated)
        data = target.read_bytes()
        font = TTFont(io.BytesIO(data))
        assert 'fvar' not in font and 'gvar' not in font and 'HVAR' not in font, path
        assert font['OS/2'].usWeightClass == weight, path
        assert set(range(32, 127)) <= font.getBestCmap().keys(), path
        assert 'tnum' in {r.FeatureTag for r in font['GSUB'].table.FeatureList.FeatureRecord}, path
        shaped = hb.Font(hb.Face(data))
        buffer = hb.Buffer()
        buffer.add_str('0123456789')
        buffer.guess_segment_properties()
        hb.shape(shaped, buffer, {'tnum': True})
        assert len(buffer.glyph_positions) == 10
        assert len({p.x_advance for p in buffer.glyph_positions}) == 1, 'Non-tabular duration digits: ' + path
        faces.append(dict(resource=path, optical_size=optical, weight=weight, bytes=len(data), sha256=sha(data),
                          characters=len(font.getBestCmap())))
    result = dict(source_font_sha256=SOURCE_SHA, width=100, faces=faces,
                  scope='SF Pro non-PUA text; CJK platform fallback is not macOS PingFang parity')
    if args.verify:
        assert json.loads(MANIFEST.read_text(encoding='utf-8')) == result
    else:
        MANIFEST.write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print('PASS SF Pro text: 6 frozen faces, ASCII and tabular digits, pinned bytes' +
          (', byte-identical rebuild' if args.verify and raw else ''))


if __name__ == '__main__':
    main()
