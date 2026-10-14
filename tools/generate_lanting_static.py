#!/usr/bin/env python3
"""Generate axis-frozen 小米兰亭 Pro instances from the pinned Android variable font.

Windows builds of the Skia text stack apply the wght axis to glyph *outlines*
but keep design (default-axis) *advance widths* for this font, producing heavy
glyphs on light advances and the reported letter overlap. Static per-weight
instances bake both outlines and advances, so every platform reads plain hmtx.

The original 19,646,728-byte pinned variable font stays in the package unchanged
and remains the hash-verified source. This tool never edits it.

Deps (generation/verification only, never runtime):
    python3 -m pip install "fonttools>=4.55" uharfbuzz
Run from the repository root:
    python3 tools/generate_lanting_static.py            # write instances
    python3 tools/generate_lanting_static.py --verify   # CI byte comparison

Faithfulness: after instancing, each output must shape (advances + GPOS kerning)
*exactly* like the variable font at the pinned axis for the shipped fixtures;
otherwise the tool fails. Two source-font quirks are normalized without
changing HarfBuzz behaviour:
  * ClassDef1/ClassDef2 entries whose class exceeds ClassCount make HarfBuzz
    skip that pair-position subtable; fontTools crashes indexing them, so the
    counts are padded with zero-kern rows (identical result: no adjustment).
  * fontTools derives frozen advances only from gvar phantom points, while this
    font encodes them in HVAR, so every glyph advance is written from HarfBuzz's
    own HVAR-aware measurement at the pinned axis.
"""
import argparse
import hashlib
import io
import json
import sys
from pathlib import Path

from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otBase
from fontTools.varLib import instancer
import uharfbuzz as hb

SOURCE = Path('desktop/src/main/resources/fonts/mi_lan_pro_vf.ttf')
MANIFEST = Path('desktop/src/main/resources/parity/mi-lan-pro-static.json')
OUT_DIR = Path('desktop/src/main/resources/fonts')
SOURCE_SHA256 = '15fc7accb0afcbb7cf3bbf776536a37398e552f082bb13af70602bf1d7ed676f'
PINNED_ANDROID = '3f877eaffe30c82eb6d24eb3647702f5bc554dd3'
# CSS weight -> private wght axis, pinned Android res/font/mi_lan_pro.xml.
AXES = [(100, 150), (200, 200), (300, 250), (400, 340),
        (500, 400), (600, 540), (700, 630), (800, 700)]
# Android XML also maps CSS 900 to axis 700; reuse the frozen 800 instance.
WEIGHT900_FILE_CSS = 800
FIXTURES = [
    'Everything that you know will be changing',
    'Like, like, like, yeah',
    '你熟悉的日常都将重启',
    'Lookin’ so sharp like',
]


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def repair_pairpos_classdefs(font: TTFont) -> list:
    """Pad Class1/Class2 record arrays so over-range classes index a zero row."""
    notes = []
    gpos = font['GPOS'].table
    for lookup_index, lookup in enumerate(gpos.LookupList.Lookup):
        if lookup.LookupType != 2:
            continue
        for sub_index, sub in enumerate(lookup.SubTable):
            if sub.Format != 2:
                continue

            def zero_class2():
                rec = type(zero_class2.template)()
                rec.Value1 = otBase.ValueRecord(sub.ValueFormat1) if sub.ValueFormat1 else None
                rec.Value2 = otBase.ValueRecord(sub.ValueFormat2) if sub.ValueFormat2 else None
                return rec
            zero_class2.template = sub.Class1Record[0].Class2Record[0]

            max1 = max(sub.ClassDef1.classDefs.values(), default=0)
            max2 = max(sub.ClassDef2.classDefs.values(), default=0)
            rows = sub.Class1Record
            if max1 + 1 > sub.Class1Count:
                for _ in range(max1 + 1 - sub.Class1Count):
                    row = type(rows[0])()
                    row.Class2Record = [zero_class2() for _ in range(sub.Class2Count)]
                    rows.append(row)
                notes.append(dict(kind='class1_pad', lookup=lookup_index, subtable=sub_index,
                                  from_=sub.Class1Count, to=max1 + 1))
                sub.Class1Count = max1 + 1
            if max2 + 1 > sub.Class2Count:
                notes.append(dict(kind='class2_pad', lookup=lookup_index, subtable=sub_index,
                                  from_=sub.Class2Count, to=max2 + 1))
                sub.Class2Count = max2 + 1
            for row in rows:
                while len(row.Class2Record) < sub.Class2Count:
                    row.Class2Record.append(zero_class2())
    return notes


def shape_advance_sum(face_blob: bytes, text: str, axis: int | None) -> int:
    face = hb.Face(face_blob)
    font = hb.Font(face)
    if axis is not None:
        font.set_variations({'wght': axis})
    buf = hb.Buffer()
    buf.add_str(text)
    buf.guess_segment_properties()
    hb.shape(font, buf)
    return sum(p.x_advance for p in buf.glyph_positions)


def build_instance(source: bytes, css: int, axis: int) -> tuple[bytes, list]:
    font = TTFont(io.BytesIO(source))
    notes = repair_pairpos_classdefs(font)
    frozen = instancer.instantiateVariableFont(font, {'wght': axis}, inplace=False, updateFontNames=False)

    variable_face = hb.Face(source)
    variable_font = hb.Font(variable_face)
    variable_font.set_variations({'wght': axis})
    order = frozen.getGlyphOrder()
    metrics = frozen['hmtx'].metrics
    updated = 0
    for gid, name in enumerate(order):
        expected = variable_font.get_glyph_h_advance(gid)
        advance, lsb = metrics[name]
        if advance != expected:
            metrics[name] = (expected, lsb)
            updated += 1
    notes.append(dict(kind='advances_written', count=updated))

    source_head = TTFont(io.BytesIO(source))['head']
    frozen.recalcTimestamp = False
    frozen['head'].created = source_head.created
    frozen['head'].modified = source_head.modified
    frozen['OS/2'].usWeightClass = css

    assert 'fvar' not in frozen and 'gvar' not in frozen and 'HVAR' not in frozen
    assert frozen['maxp'].numGlyphs == TTFont(io.BytesIO(source))['maxp'].numGlyphs

    buffer = io.BytesIO()
    frozen.save(buffer)
    data = buffer.getvalue()

    static_face = hb.Face(data)
    for text in FIXTURES:
        got = shape_advance_sum(data, text, None)
        want = shape_advance_sum(source, text, axis)
        if got != want:
            raise SystemExit(f'CSS {css} axis {axis}: shaping mismatch {got} != {want} for {text!r}')
    return data, notes


def render_manifest(source: bytes, outputs: dict[int, tuple[bytes, dict]]) -> dict:
    entries = []
    for css, axis in AXES:
        data, meta = outputs[css]
        entries.append(dict(css=css, axis=axis, file=f'fonts/mi_lan_pro_w{css}.ttf',
                            bytes=len(data), sha256=sha256(data), us_weight_class=css,
                            generation=meta))
    entries.append(dict(css=900, axis=700, file=f'fonts/mi_lan_pro_w{WEIGHT900_FILE_CSS}.ttf',
                        bytes=len(outputs[800][0]), sha256=sha256(outputs[800][0]),
                        us_weight_class=800, generation={'reuses_css': WEIGHT900_FILE_CSS}))
    return dict(
        check='axis-frozen 小米兰亭 Pro instances shaped identically to the pinned variable font at each pinned axis',
        # as_posix(): Windows Path str() would embed backslashes and break the
        # cross-host manifest comparison; resource keys are always '/'-separated.
        source=dict(file=SOURCE.as_posix(), bytes=len(source), sha256=sha256(source)),
        pinned_android=PINNED_ANDROID,
        axes=[[css, axis] for css, axis in AXES], css900=dict(reuses_css=WEIGHT900_FILE_CSS),
        fixtures=FIXTURES,
        harfbuzz=hb.version_string(),
        entries=entries,
        physical_windows_acceptance='BLOCKED',
        android_pixel_acceptance='BLOCKED',
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--verify', action='store_true',
                        help='regenerate in memory and compare with committed files/manifest')
    args = parser.parse_args()

    source = SOURCE.read_bytes()
    if sha256(source) != SOURCE_SHA256:
        raise SystemExit('pinned variable font hash mismatch')

    outputs = {}
    for css, axis in AXES:
        data, notes = build_instance(source, css, axis)
        outputs[css] = (data, notes)
        print(f'CSS {css:3d} -> wght {axis}: {len(data)} bytes, sha256 {sha256(data)[:16]}… '
              f'({notes[-1]["count"]} advances frozen)')

    manifest = render_manifest(source, outputs)

    if args.verify:
        committed = json.loads(MANIFEST.read_text(encoding='utf-8'))
        # Keep the host-regenerated manifest for post-mortem comparison.
        regenerated_path = Path('desktop/build/verification/font-static-regenerated.json')
        regenerated_path.parent.mkdir(parents=True, exist_ok=True)
        regenerated_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n',
                                    encoding='utf-8')
        # The shaping tool version is host evidence, not font content: pip may
        # resolve a newer uharfbuzz on another host. Shape equality against the
        # pinned variable font is still enforced inside build_instance() above.
        committed_hb = committed.pop('harfbuzz', None)
        regenerated_hb = manifest.pop('harfbuzz', None)
        if committed != manifest:
            only_committed = {k: committed[k] for k in committed.keys() - manifest.keys()}
            only_regenerated = {k: manifest[k] for k in manifest.keys() - committed.keys()}
            changed = {k for k in committed.keys() & manifest.keys() if committed[k] != manifest[k]}
            raise SystemExit(f'committed manifest differs from regenerated manifest; '
                             f'changed={sorted(changed)} committed-only={only_committed} '
                             f'regenerated-only={only_regenerated}')
        for css, axis in AXES:
            path = OUT_DIR / f'mi_lan_pro_w{css}.ttf'
            if not path.is_file() or sha256(path.read_bytes()) != sha256(outputs[css][0]):
                raise SystemExit(f'committed instance differs from regenerated {path}')
        print(f'VERIFIED: {len(AXES)} static instances byte-match regeneration from pinned source '
              f'(harfbuzz committed={committed_hb} host={regenerated_hb})')
        return 0

    for css, axis in AXES:
        (OUT_DIR / f'mi_lan_pro_w{css}.ttf').write_bytes(outputs[css][0])
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    total = sum(len(outputs[css][0]) for css, _ in AXES)
    print(f'Wrote {len(AXES)} instances ({total} bytes) and {MANIFEST}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
