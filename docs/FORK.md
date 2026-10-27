# This fork (goodwind/aerial-tv)

`main` here is upstream's `main` plus our branches, built as one app that updates itself from this
repository's releases. It is meant to be brought up to upstream again and again.

## How the fork is laid out

`main` here is a plain mirror of upstream's `main`. Every change lives in its own feature branch off
it, and the app is built from a throwaway branch `build` that merges them all.

| Branch | What | Where it stands |
|---|---|---|
| `fork-layer` | updates come from this repo's releases, `tools/fork-stamp.py`, this file | fork only |
| `thin-focus-ring` | thinner high-contrast ring on the tiles | PR #352 |
| `app-info-for-firmware-without-it` | read-only App Info for firmware without one (already inside `app-info-adb`) | PR #233 |
| `app-info-adb` | adbd access for App Info, off until the owner agrees, revoked by turning it off | PR #356 (stacked on #233) |
| `app-content-shelf` | app content shelf (Slides Out / Off / Always), tabs, asks apps to publish, big app name; its backdrop is the frosted wallpaper or the card's poster (a setting) | PR #364 |
| `personalization-tabs` | Personalize in pages: Home Screen, Wallpaper, Screensaver, System | PR #748 |
| `dvd-screensaver` | the bouncing DVD logo screensaver (the logo is drawn in code) | PR #777 |
| `dvd-logo-picture` | the DVD Video logo as a picture instead of the drawn one, on top of `dvd-screensaver` (a trademark: kept out of upstream and of releases; merge it into a private `build` only) | local only |
| `auto-wallpapers` | auto wallpapers (Bing, Wikimedia, NASA, Unsplash, own photos) | **not taken upstream** (#353 closed): fork only |

## Bringing the fork up to upstream

`git config rerere.enabled true` once: conflicts between the branches (the Personalize sheet is
touched by four of them) are settled by hand the first time and remembered after that.

```sh
git fetch origin fork
git checkout main && git merge --ff-only origin/main && git push fork main
# 1. a branch with an open PR takes upstream's main by merge (the maintainer also pushes to those
#    branches, so never rebase them); fork-only branches (fork-layer, auto-wallpapers) are rebased
for b in thin-focus-ring app-info-adb app-content-shelf personalization-tabs dvd-screensaver; do
  git checkout $b && git merge origin/main        # fix, push fork $b
done
for b in fork-layer auto-wallpapers; do git checkout $b && git rebase origin/main; done   # push --force-with-lease
# 2. build: a new branch off upstream with all of them
git checkout -b build origin/main
for b in fork-layer thin-focus-ring app-info-adb app-content-shelf personalization-tabs dvd-screensaver auto-wallpapers; do git merge $b; done
python3 tools/fork-stamp.py 1                     # N = the fork's build of this upstream version
./gradlew testStandardDebugUnitTest assembleStandardRelease assembleGuardRelease
```

Release from `build`; delete it afterwards (`git branch -D build`), the next one starts again from
upstream. Conflicts that came up every time: the strings files (keep both sides, drop repeated
keys), the `Glyph` list (union), `HomeScreen.kt` (keep upstream's version and put the shelf's lines
back in), `AerialSettings.kt` (both sides add settings), the Personalize sheet (`WallpaperSheet.kt`:
each branch's rows go on the page they belong to; App permissions on System). After merging, check
that `values-ru`, `-uk`, `-kk` and `-zh` have every key of `values` and nothing more.

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
