"""Builds worker H2's Chapter Five hubs: hub/saltgate and hub/holdfast (docs/MILESTONE_6.md 5.7).

    python3 tools/assets/structures/h2/build_all.py [--preview DIR]

Writes each template's .nbt, its site anchors and its site meta under src/main/resources/data/oldrepublic.
Every build is deterministic (fixed seeds), so a rerun is byte-identical. With --preview, a preview
sheet per hub goes into DIR (never into the repository).
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import holdfast  # noqa: E402
import saltgate  # noqa: E402

BUILDS = (('saltgate', saltgate.build), ('holdfast', holdfast.build))


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('--preview', help='a directory for preview sheets (PNG)')
    a = ap.parse_args(argv)
    for name, build in BUILDS:
        preview = os.path.join(a.preview, name + '.png') if a.preview else None
        build(preview=preview)


if __name__ == '__main__':
    main()
