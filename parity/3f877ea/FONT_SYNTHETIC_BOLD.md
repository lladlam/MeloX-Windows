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

Final Windows independent-outline results and the exact distribution are
recorded below after verification. Until then the user's report remains open.
