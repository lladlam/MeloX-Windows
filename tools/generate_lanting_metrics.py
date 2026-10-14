#!/usr/bin/env python3
"""Independent HarfBuzz reference (pip install uharfbuzz); never a runtime dependency.

Axis mapping: pinned Android 3f877ea res/font/mi_lan_pro.xml.
Run from repository root. Retain the immutable original font; do not instantiate
or rewrite its variable outlines. This reference checks layout advances while
the Kotlin native-outline audit separately checks unintended synthetic ink.
"""
import hashlib
import json
from pathlib import Path
import uharfbuzz as hb

font_path = Path('desktop/src/main/resources/fonts/mi_lan_pro_vf.ttf')
data = font_path.read_bytes()
digest = hashlib.sha256(data).hexdigest()
assert digest == '15fc7accb0afcbb7cf3bbf776536a37398e552f082bb13af70602bf1d7ed676f'
face = hb.Face(data)
texts = ['Everything that you know will be changing', '你熟悉的日常都将重启',
         "You'll like it, you love it", 'Lookin’ so sharp like']
rows = []
for css, axis in [(100, 150), (400, 340), (700, 630), (800, 700), (900, 700)]:
    font = hb.Font(face)
    font.set_variations({'wght': axis})
    for index, text in enumerate(texts):
        buffer = hb.Buffer()
        buffer.add_str(text)
        buffer.guess_segment_properties()
        hb.shape(font, buffer)
        assert all(info.codepoint for info in buffer.glyph_infos)
        rows.append(dict(css=css, axis=axis, fixture=index, text=text,
                         advance_em=sum(p.x_advance for p in buffer.glyph_positions) / face.upem))
out = Path('desktop/src/main/resources/parity/mi-lan-pro-metrics.json')
out.write_text(json.dumps(dict(font_sha256=digest, harfbuzz=hb.version_string(),
                              pinned_android='3f877eaffe30c82eb6d24eb3647702f5bc554dd3',
                              upem=face.upem, rows=rows), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Wrote {len(rows)} independently shaped font advance references: {out}')
