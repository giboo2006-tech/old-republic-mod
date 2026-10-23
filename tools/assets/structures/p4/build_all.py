"""Builds every P4 (Tyrvann) structure of Milestone 6: the three rebuilt planet/site templates (the verse
stone, the hymn shrine, the mire grave) and the five new worldgen structures (the silenced city with its
lots, the choir ruin, the Hask camp, the mire causeway, the downed interceptor), with their pools,
structures, sets, biome tags, site meta and loot tables.

usage: python3 tools/assets/structures/p4/build_all.py [--preview DIR] [--only TEXT]
Previews (one sheet per template) go into DIR when it is given; keep it outside the repository. --only
saves just the templates whose id contains TEXT (the worldgen files are written either way).
Every run writes the same bytes (seeded rngs and noise only).
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from common import args  # noqa: E402
import verse_stone  # noqa: E402
import hymn_shrine  # noqa: E402
import mire_grave  # noqa: E402
import silenced_city  # noqa: E402
import choir_ruin  # noqa: E402
import hask_camp  # noqa: E402
import mire_causeway  # noqa: E402
import downed_interceptor  # noqa: E402

MODULES = [verse_stone, hymn_shrine, mire_grave, silenced_city, choir_ruin, hask_camp, mire_causeway, downed_interceptor]


def main(argv=None):
    opts = args(argv)
    for m in MODULES:
        print('==', m.__name__)
        m.main(opts)


if __name__ == '__main__':
    main()
