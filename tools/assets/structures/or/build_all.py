"""Builds everything OR owns among the structures: the three meteor crater templates of
oldrepublic:wilds/starfall_crater (starfall_crater.py), then their worldgen (worldgen_or.py: the start pool,
the structure, the set and the biome tag). Every build is deterministic (seeded), so a rerun rewrites the same bytes.

Run: python3 tools/assets/structures/or/build_all.py [--preview DIR]
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import common as C  # noqa: E402
import starfall_crater  # noqa: E402
import worldgen_or  # noqa: E402


def main():
    args = C.cli(__doc__)
    fits = starfall_crater.build_all(args.preview)
    print('worldgen', worldgen_or.write(fits))


if __name__ == '__main__':
    main()
