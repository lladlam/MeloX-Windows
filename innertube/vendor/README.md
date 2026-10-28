# Android main InnerTube source archive

Source: https://github.com/lladlam/MeloX-Android

Pinned commit: `b687590869516ce68c739a6295a1b3f18410e739`

`android-b687590.tar.gz` is generated directly with `git archive` from
`android/innertube`. Builds extract Kotlin sources into `build/generated`.
The complete archive includes the Android upstream README and attribution.
Network and model sources are unchanged. `DesktopLog` provides a JVM adapter
for the Android Timber logging API; it uses `java.util.logging`.

The Android module is vendored from Metrolist
(https://github.com/mostafaalagamy/Metrolist), licensed GPL-3.0.
Copyright remains with Metrolist and its contributors. See the README inside
the archive for the Android repository's upstream adaptations.

Regenerate from the local Android repository:

```sh
git -C ../MeloX-Android archive --format=tar.gz \
  --output="$PWD/innertube/vendor/android-b687590.tar.gz" \
  b687590869516ce68c739a6295a1b3f18410e739 android/innertube
```

The current gzip is produced with a fixed zero timestamp; its SHA-256 is
`ce246610989e0955cd4efb2c4af494520738db1500f0bbe799437127409776e0`.
