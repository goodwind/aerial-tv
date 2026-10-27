#!/usr/bin/env python3
"""Makes this fork's own build out of upstream's code: run it after every merge of upstream's main.

    python3 tools/fork-stamp.py N

- Updates come from this fork's releases (Updates.RELEASES_URL), not from upstream's site.
- The version is upstream's with the fork's build number added: upstream 1.60 (versionCode 73)
  becomes 1.60.N (versionCode 7300 + N), so it is newer than the upstream release it is made from
  and every later one of ours is newer still. Run again with a larger N for another build of the
  same upstream version. If versionName already has three parts, it is read back to upstream's.

Safe to run twice: it only changes what is not yet changed. See docs/FORK.md.
"""
import re, sys

GRADLE = 'app/build.gradle.kts'
UPDATES = 'app/src/main/java/app/orchard/tv/update/Updates.kt'
CONTROL = 'app/src/main/java/app/orchard/tv/ui/control/ControlCenter.kt'
REPO = 'goodwind/aerial-tv'


def rw(path, fn):
    s = open(path, encoding='utf8').read()
    t = fn(s)
    if t != s:
        open(path, 'w', encoding='utf8').write(t)
        print('changed', path)


def version(s, n):
    name = re.search(r'versionName = "([^"]+)"', s).group(1)
    code = int(re.search(r'versionCode = (\d+)', s).group(1))
    parts = name.split('.')
    if len(parts) >= 3:               # already ours: back to upstream's
        name, code = '.'.join(parts[:2]), code // 100
    s = re.sub(r'versionName = "[^"]+"', f'versionName = "{name}.{n}"', s, count=1)
    s = re.sub(r'versionCode = \d+', f'versionCode = {code * 100 + n}', s, count=1)
    print(f'version {name}.{n}, code {code * 100 + n}')
    return s


def updates(s):
    s = s.replace(
        '    private const val VERSION_URL = "https://aerial.qust.me/download/version.txt"',
        f'    /** This fork\'s releases ({REPO}): each carries version.txt and the APKs, see docs/FORK.md. */\n'
        f'    const val RELEASES_URL = "https://github.com/{REPO}/releases/latest"\n'
        '    private const val VERSION_URL = "$RELEASES_URL/download/version.txt"')
    s = re.sub(r'val sources = listOf\(\s*"https://aerial\.qust\.me/download/\$name",\s*"https://github\.com/[^"]+/\$name",\s*\)',
               'val sources = listOf("$RELEASES_URL/download/$name")', s)
    return s


def control(s):
    s = re.sub(r'val url = "https://aerial\.qust\.me/update/[^"\n]*"[^\n]*', 'val url = Updates.RELEASES_URL', s)
    # The address of this TV went into that page's link; the releases page needs none.
    s = re.sub(r'[ ]*val ip = PhoneServer\.lanAddress\(context\)\n(?=[ ]*val url = Updates\.RELEASES_URL)', '', s)
    return s


if __name__ == '__main__':
    n = int(sys.argv[1]) if len(sys.argv) > 1 else 1
    rw(GRADLE, lambda s: version(s, n))
    rw(UPDATES, updates)
    rw(CONTROL, control)
