# This fork (goodwind/aerial-tv)

`main` here is upstream's `main` plus our branches, built as one app that updates itself from this
repository's releases. It is meant to be brought up to upstream again and again.

## What is on top of upstream

| Branch | What | Where it stands |
|---|---|---|
| `thin-focus-ring` | thinner high-contrast ring on the tiles | PR #352 |
| `app-info-for-firmware-without-it` | read-only App Info for firmware without one (already inside `app-info-adb`) | PR #233 |
| `app-info-adb` | adbd access for App Info, off until the owner agrees, revoked by turning it off | PR #356 (stacked on #233) |
| `app-content-shelf` | app content shelf (Slides Out / Off / Always), tabs, asks apps to publish, big app name | PR #364 |
| `auto-wallpapers-pr` | auto wallpapers (Bing, Wikimedia, NASA, Unsplash, own photos) | **not taken upstream** (#353 closed): kept only here |

On top of those, `tools/fork-stamp.py` makes it this fork's own build (below).

## Bringing the fork up to upstream

```sh
git fetch origin fork
# 1. each branch takes upstream's main: merge, fix conflicts, build; push (never rebase: upstream's
#    maintainer also pushes to the PR branches)
for b in thin-focus-ring app-info-adb app-content-shelf auto-wallpapers-pr; do
  git checkout $b && git merge origin/main      # then fix, ./gradlew testStandardDebugUnitTest assembleStandardRelease, git push fork $b
done
# 2. main takes upstream and all the branches
git checkout main && git merge origin/main
for b in thin-focus-ring app-info-adb app-content-shelf auto-wallpapers-pr; do git merge $b; done
python3 tools/fork-stamp.py 1                   # N = the fork's build of this upstream version; 2 for a second one
./gradlew testStandardDebugUnitTest assembleStandardRelease assembleGuardRelease
```

Conflicts that came up every time, and how they were settled: the strings files (keep both sides,
drop repeated keys), the `Glyph` list (union), `HomeScreen.kt` (upstream moves it a lot; keep its
version and put the shelf's lines back in), `AerialSettings.kt` (both sides add settings). After
merging, check that `values-ru`, `-uk`, `-kk` and `-zh` have every key of `values` and nothing more.

## Versions

`tools/fork-stamp.py N` turns upstream's version into the fork's: upstream 1.60 (code 73) becomes
**1.60.N** (code 7300 + N). It is newer than the upstream release it was made from, and every later
one is newer than it. The script also points updates at this repository (`Updates.RELEASES_URL`),
and can be run again after any merge.

## Updates and releases

`update/Updates.kt` reads `version.txt` from the latest release of this repository and downloads
`aerial-<flavor>.apk` from it. Every release needs three assets:

| Asset | What |
|---|---|
| `version.txt` | the version name only, e.g. `1.60.1` |
| `aerial-standard.apk` | `assembleStandardRelease` |
| `aerial-guard.apk` | `assembleGuardRelease` |

The APKs are signed with this machine's debug key (there is no `local/signing.keystore`), and the
installer accepts an update only if it is signed like the installed app, so every release must be
built with the same key.

```sh
gh release create v1.60.1 --repo goodwind/aerial-tv --title "1.60.1" --notes-file notes.md \
  version.txt aerial-standard.apk aerial-guard.apk
```

Release for a noticeable update (a feature, not a typo), as agreed.
