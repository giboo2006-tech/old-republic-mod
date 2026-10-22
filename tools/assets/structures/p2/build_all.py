"""Builds every P2 (Ghessar) structure of Milestone 6: the three rebuilt planet/site templates and the six new
worldgen structures, with their pools, structures, sets, biome tags, site meta and loot tables.

usage: python3 tools/assets/structures/p2/build_all.py [preview_dir]
Previews (one sheet per template) go into preview_dir when it is given; keep it outside the repository.
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import crashed_freighter  # noqa: E402
import kajidic_post  # noqa: E402
import debtor_camp  # noqa: E402
import freighter_graveyard  # noqa: E402
import caravan_waystation  # noqa: E402
import salt_works  # noqa: E402
import mesa_lookout  # noqa: E402
import salvager_squat  # noqa: E402
import escape_pod  # noqa: E402

MODULES = [crashed_freighter, kajidic_post, debtor_camp, freighter_graveyard, caravan_waystation, salt_works,
           mesa_lookout, salvager_squat, escape_pod]


def main(preview_dir=None):
    if preview_dir:
        os.makedirs(preview_dir, exist_ok=True)
    for m in MODULES:
        print('==', m.__name__)
        m.main(preview_dir)


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else None)
