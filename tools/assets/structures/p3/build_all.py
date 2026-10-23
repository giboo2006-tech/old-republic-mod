"""Builds every P3 (Varkhast) structure of Milestone 6: the rebuilt detention post (runtime site) and the five
new worldgen structures, with their pools, structures, sets, biome tags, site meta, anchors and loot tables.

usage: python3 tools/assets/structures/p3/build_all.py [preview_dir]
Previews (sheets per template) go into preview_dir when it is given; keep it outside the repository.
Every script is deterministic: running this twice writes byte-identical files.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import detention_post  # noqa: E402
import cliff_mausoleum  # noqa: E402
import obelisk_way  # noqa: E402
import imperial_dig  # noqa: E402
import ash_village  # noqa: E402
import hound_den  # noqa: E402

MODULES = [detention_post, cliff_mausoleum, obelisk_way, imperial_dig, ash_village, hound_den]


def main(preview_dir=None):
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
    for m in MODULES:
        print('==', m.__name__)
        m.build(preview_dir)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
