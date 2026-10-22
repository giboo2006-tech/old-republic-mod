#!/usr/bin/env python3
"""Builds every mission site of QM (docs/MILESTONE_6.md 5.4), one script after the other.

    python3 tools/assets/structures/qm/build_all.py [--preview <folder>] [--only <id part>]

Writes structure/mission/<id>.nbt, site_anchors/mission/<id>.json and site_meta/mission/<id>.json for:
  raider_camp_small(_2)  fence_camp(_2)  survey_post(_2)  skirmish_line(_2)
  patrol_ambush(_2)      smuggler_cache(_2)  ambush_rocks
Every script is deterministic (seeded random and noise only), so a rerun writes the same bytes.
Then check them: python3 tools/check_m6_structures.py --only mission/
"""
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPTS = ['raider_camp_small.py', 'fence_camp.py', 'survey_post.py', 'skirmish_line.py', 'patrol_ambush.py',
           'smuggler_cache.py', 'ambush_rocks.py']


def main():
    failed = []
    for name in SCRIPTS:
        r = subprocess.run([sys.executable, os.path.join(HERE, name)] + sys.argv[1:], cwd=HERE)
        if r.returncode:
            failed.append(name)
    if failed:
        raise SystemExit('failed: %s' % ', '.join(failed))


if __name__ == '__main__':
    main()
