# This fork (goodwind/aerial-tv)

`main` here is upstream's `main` plus the changes below. Updates and downloads come from this
repository's releases, not from upstream's site.

## Updates

`update/Updates.kt` reads `version.txt` from the latest release of this repository
(`Updates.RELEASES_URL`) and downloads `aerial-<flavor>.apk` from it. Every release therefore needs
three assets:

| Asset | What |
|---|---|
| `version.txt` | the version name only, e.g. `1.39` |
| `aerial-standard.apk` | `assembleStandardRelease` |
| `aerial-guard.apk` | `assembleGuardRelease` |

The APKs are signed with this machine's debug key (there is no `local/signing.keystore`), and the
installer only accepts an APK signed like the installed one, so every release must be built with the
same key.

## Releasing

Bump `versionCode` and `versionName` in `app/build.gradle.kts` for a noticeable update (a new
feature, not a typo), build both flavors, then:

```sh
gh release create v1.39 --repo goodwind/aerial-tv --title "1.39" --notes-file notes.md \
  version.txt aerial-standard.apk aerial-guard.apk
```

## What is on top of upstream

Auto wallpapers (Bing, Wikimedia, NASA, Unsplash, own photos); the app-content shelf with tabs per
channel and the Up arrow; Control Center reached from the shelf or by Right at the end of the top
row; a thinner high-contrast focus ring; Russian, Ukrainian and Kazakh translations of the app and
the phone page; App Info for firmware that has none.
