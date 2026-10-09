# Android main InnerTube source archive

Source: https://github.com/lladlam/MeloX-Android

Pinned commit: `94e17c920bf554966584955afa2bd2ee92580a8b`

`android-94e17c9.tar.gz` is generated directly with `git archive` from
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
  --output="$PWD/innertube/vendor/android-94e17c9.tar.gz" \
  94e17c920bf554966584955afa2bd2ee92580a8b android/innertube
```
