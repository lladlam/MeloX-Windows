# Heavy glyph overlap: independent font-outline correction

Pinned Android remains `3f877eaffe30c82eb6d24eb3647702f5bc554dd3`.

## User rejection of 989f33b

The user reports that the new Windows distribution still has the same font
problem. Their new Tiny Giant screenshot shows English letters touching and
overlapping in the main line, accompaniment and inactive line, as well as
overweight CJK translation. **989f33b is not accepted as resolving this report.**

The previous 60-case zero-difference gate was a compositor **self-consistency**
check. It reconstructed the same Compose paragraph exactly, but did not
validate that paragraph against the original font outlines. Passing it could
not establish that the source typography was correct. Those genuine results
remain archived with this explicit limitation; they must not be relabelled
Android MATCH or user acceptance.

## Reproduced cause

Pinned Android `res/font/mi_lan_pro.xml` maps **both CSS 800 and 900 to wght=700**.
Skia's cloned native font consequently reports intrinsic weight 700. Its
paragraph shaper synthesizes additional bold when requested weight >=600 and
requested minus intrinsic weight >=200. A CSS 900 request meets that condition
exactly. The already-heavy native outline is emboldened again, without a
corresponding advance increase. CSS 800 selects the identical original axis
instance without triggering synthetic bold.

The actual Compose 1.8.1 / Skiko 0.9.4.2 probe reproduces this with the pinned
font. On the main-line phrase at 34 px, CSS 900 has **22.25% excess raster ink**
relative to independently drawn native wght=700 outlines at the same glyph
positions. `FontSynthesis.None` does not prevent this paragraph-layer synthesis.
The equivalent CSS 800 request has **0.69%** ink difference on that host (native
vs paragraph raster hinting), while retaining the same shaped advances. Direct
HarfBuzz shaping independently verifies the expected advances of each axis.

Inspected source explaining the native synthesis condition:
<https://github.com/google/skia/blob/main/modules/skparagraph/src/OneLineShaper.cpp>
(the independent bundled-version probe, not that moving URL, establishes the
behavior of the version actually used). Context7 is unavailable due to quota.

## Correction

`lanTingRenderWeight` translates only the desktop render request for Black/900
to the **source-equivalent ExtraBold/800 alias**. Both select unchanged original
wght=700 outlines/metrics. The persisted `Heavy` setting, source axis mapping,
font bytes and user preferences retain their existing meanings.

The correction covers the classic main timed/static lyrics, accompaniment,
translation, romanization/ruby, and explicit Black-weight alternative lyric
styles. All other weights retain their existing rendering requests. The
pause-safe PCM writer remains the same already-tested implementation.

## Independent verification

`tools/generate_lanting_metrics.py` uses standalone HarfBuzz to derive 20
advance references from the exact original font hash, covering five source
weights and four English/CJK phrases from the reported scene. The generated
`parity/mi-lan-pro-metrics.json` is read by `auditFontMetrics`; no Python library
is added to production runtime.

The Kotlin audit verifies the original font hash, checks shaped advances
against the independent reference (0.35 px bound), and compares Heavy raster
ink against native original outlines drawn at the paragraph's positions (5%
bound). Three sizes cover 34 px main text, 59.5 px at 175% density and 23.12 px
accompaniment. It retains raw 900/All and 900/None as **negative controls**:
24 Heavy controls must reject, while all 60 corrected cases must pass.
The prior compositor self-geometry check remains a separate gate.

Real Tiny Giant provider/FFmpeg/software PCM replay is positioned near 23 s,
at the user's “Everything that you know will be changing” line. Production
screenshots include the actual main line, accompaniment and translation, plus
pause/seek/resume and genuinely advancing lyric clock. This still does not
constitute physical Windows 10 or pinned Android pixel/motion acceptance.

## Windows CI result for 73efc08 (rejected)

Run `38528660536` stopped at the new font gate: Heavy synthetic-ink controls
rejected as expected (24/24), but the advance gate failed for most corrected
cases, so **no distribution was created**.

Measured Windows Server 2025 paragraph width for fixture0 (34 px), compared
with the independent HarfBuzz reference:

| CSS weight | Windows Compose | HarfBuzz reference | error |
|---:|---:|---:|---:|
| 100 | 666.33 | 666.33 | 0.00 |
| 400 | 668.14 | 694.89 | 26.75 |
| 700 | 670.41 | 749.63 | 79.23 |
| 800/900 | 670.92 | 763.74 | 92.82 |

The Windows row raster grows in **ink** with the axis (so outlines receive the
`wght` variation) but barely grows in **advance width** (666→671 instead of
666→764). Heavy outlines therefore sit on near-light advances: exactly the
reported letter-overlap. Linux shaping grows 666→759 correctly, which is why
earlier Linux-only self-checks could not detect it. The CJK fixtures pass
because MiLan CJK advances are full-width regardless of axis.

This is why the earlier zero-difference compositor gate (both rows drawn from
the same paragraph positions) passed while the user still saw overlap: it never
compared the Windows paragraph against independent font metrics.

A native `Font.measureTextWidth` probe per axis was added to determine whether
the Windows scaler/HVAR metrics collapse or only SkParagraph does; that decides
between a static-instance font fix and a shaping-configuration fix. The probe
revision is `275bc2e`. The user's report remains open.

## Windows probe verdict (run 38534556444)

The probe isolated the defect to the **native font-metrics layer on Windows**,
not to paragraph shaping configuration. For the same frozen font bytes, the
Windows native typeface reports the correct per-axis style and outline ink, but
`Font.measureTextWidth` returns one constant value for every axis:

| CSS weight | Windows native width | Compose paragraph | HarfBuzz reference |
|---:|---:|---:|---:|
| 100 | 676.056 | 666.33 | 666.33 |
| 400 | 676.056 | 668.14 | 694.89 |
| 700 | 676.056 | 670.41 | 749.63 |
| 800/900 | 676.056 | 670.92 | 763.74 |

The Windows text stack applies the variable-font `wght` axis to glyph
**outlines** (gvar) but keeps default-axis **advance widths** (the HVAR/VVAR
advance deltas never reach the design metrics). The Linux FreeType pipeline
applies both, so Linux measured 682→770 natively and passed every gate — a
self-consistent but Windows-irrelevant result. No shaping configuration can
restore an advance delta the platform font stack discards.

## Correction: axis-frozen static instances

Because the defect is in the platform's handling of *variable* advances, the
desktop package now ships **static per-weight instances** of the pinned font
(`tools/generate_lanting_static.py`): outlines and hmtx advances are baked at
each Android-mapped axis, so every platform reads plain design metrics.

- Source of truth is unchanged: the pinned 19,646,728-byte
  `mi_lan_pro_vf.ttf` (sha256 `15fc7acc…ed676f`) stays in the package as the
  hash-verified Android-identical resource and is never edited.
- Eight instances cover the nine Android XML entries: CSS 100/200/300/400/500/
  600/700/800 at axes 150/200/250/340/400/540/630/700; CSS 900 reuses the
  axis-700 instance exactly as Android maps it. Manifest:
  `parity/mi-lan-pro-static.json`.
- Generation normalizes two source quirks without changing shaping:
  over-range GPOS ClassDef entries (HarfBuzz skips those pairs; counts are
  padded with zero-kern rows, identical result), and fontTools' reliance on gvar
  phantom points for advances (this font encodes them in HVAR, so every glyph
  advance is written from HarfBuzz's own HVAR-aware measurement).
- **Equality gate**: each instance must shape (advances + GPOS kerning)
  *exactly* equal to the pinned variable font at its pinned axis for the four
  shipped fixtures, otherwise generation fails. `--verify` re-derives all bytes
  in memory and must match the committed files (CI runs it).
- `MeloXFont` loads the static instances, asserts zero variation axes and the
  frozen OS/2 style weight per CSS entry, and keeps the persisted `Heavy`
  setting meaning unchanged. The earlier `lanTingRenderWeight` alias is removed:
  static CSS 900 carries its own entry, so no synthesis path can trigger.
- The user-facing effect: Heavy glyphs now sit on Heavy advances, matching the
  Android-variable rendering that the static instance provably equals.

`auditFontMetrics` gates all 120 cases (20 references x 3 sizes x 2 synthesis
modes): shaped paragraph advance within 0.35 px of the independent HarfBuzz
reference, and ink inside the asymmetric envelope `[0.85, 1.05]` of the
independent native static outlines — excess ink above +5% is the synthetic
bold class (measured +19..22% when it existed), while the -15% floor tolerates
the measured thin-stroke sampling bias of SkParagraph vs the linear reference
(worst observed -9.7%: CSS 100 CJK at 34 px on Linux; Windows rasterizes the
two rows identically, ratio 1.0). All 8 instance file hashes are matched
against the packaged manifest, plus frozen-typeface assertions (zero variation
axes, frozen OS/2 style weight). The old Heavy negative controls are retired
because the synthetic path they guarded no longer exists (in synthesis-All mode
the ink is now bit-identical to synthesis-None — evidence the path is gone;
a regression reintroducing it would fail the upper ink bound or the advance
gate instead).

The user's report remains open pending a fresh Windows 10 package check.

## Final Windows verification and package (797c3be)

Two follow-up Windows CI defects were caught by the new gates before any
distribution existed, both outside the font content:

1. `b955125` / `ad97786`: the cross-host static-instance verifier compared the
   whole manifest. Windows regenerated all eight instances **byte-identically**
   (every sha256 matched Linux), but `str(Path())` embeds backslashes on
   Windows and the pip-resolved HarfBuzz version string can drift. The compare
   now uses posix resource keys, ignores the tool-version field, reports both
   versions, and persists the host-regenerated manifest for post-mortems.
2. `f15fe75`: the Windows frozen-typeface assertion failed with
   `CSS 100 reports style weight 150, expected 100`. The retained STAT table
   still declared the private `wght` coordinates (150 for the Thin instance,
   **700 for the Heavy alias**); DirectWrite derives typeface weight from STAT
   while Linux reads OS/2. Besides failing the gate, CSS 900 minus STAT 700 is
   exactly the synthetic-bold threshold, so the stale STAT would have
   re-enabled the emboldening this document fixes. Frozen instances now drop
   STAT entirely; OS/2.usWeightClass (100..800) is authoritative.
   `b0eedba` then passed the full Windows pipeline; `797c3be` additionally
   pins parity JSON to LF (Windows checkout had CRLF-converted the HarfBuzz
   metrics reference in the jar; content identical, bytes not).

Final verified package: **797c3be4a64e3f9bef536e1cb2b26654c16f47d7**,
run **38559314976 / SUCCESS**, artifact **11891553587**.

Windows Server 2025 font gate (`auditFontMetrics`, 120/120 PASSED): native
`Font.measureTextWidth` now grows per weight (676.06 / 702.75 / 755.21 /
768.88 for CSS 100/400/700/800) instead of the collapsed constant 676.056, and
the shaped paragraph matches the independent HarfBuzz reference within
**0.0002 px** at every weight (CSS 900 line width restored from 670.92 to
763.74 px). Ink ratio is exactly 1.0 for all 120 cases — the synthetic-bold
path does not exist in the shipped font set. Typefaces report frozen style
weights with zero variation axes; all 8 packaged instance bytes match the
manifest; glyph raster self-geometry is 60 cases / zero difference on Windows.

Independent download verification: artifact GitHub digest, ZIP CRC, FFmpeg
PE/hash, Java Sound DLL, exact revision stamp, six pinned resources, all 8
static fonts + manifest byte-equality, real single/dual EQ PCM, and 194 tests
/ 36 suites / zero failures. Final ZIP **307,918,466 bytes**, SHA-256
`cfec0f200a8f74248460e23dfe4d6777425b50daead6e2bff4c22c9268c4ae57`:
<https://github.com/lladlam/MeloX-Windows/actions/runs/38559314976/artifacts/11891553587>.

The user's genuine Windows 10 retest and pinned Android pixel/motion acceptance
remain pending; this is Server-2025 rendering plus independent metric gates,
not physical acceptance.
